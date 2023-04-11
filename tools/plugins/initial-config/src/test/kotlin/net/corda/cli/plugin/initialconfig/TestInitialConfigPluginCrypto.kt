package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.config.impl.PrivateKeyPolicy
import net.corda.crypto.config.impl.flowBusProcessor
import net.corda.crypto.config.impl.hsm
import net.corda.crypto.config.impl.hsmRegistrationBusProcessor
import net.corda.crypto.config.impl.opsBusProcessor
import net.corda.crypto.config.impl.signingService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TestInitialConfigPluginCrypto {
    @Test
    fun `Should output missing options`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        var outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-crypto-config")
        }
        println(outText)
        assertThat(outText).contains("'passphrase' must be set for CORDA type secrets.")
        assertThat(outText).contains("-l, --location=<location>")
        assertThat(outText).contains("location to write the sql output to.")
        assertThat(outText).contains("-p, --passphrase=<passphrase>")
        assertThat(outText).contains("-s, --salt=<salt>")
        assertThat(outText).contains("Salt for the encrypting secrets service.")
        assertThat(outText).contains("-wp, --wrapping-passphrase=<softHsmRootPassphrase>")
        assertThat(outText).contains("Passphrase for the SOFT HSM root wrapping key.")
        assertThat(outText).contains("-ws, --wrapping-salt=<softHsmRootSalt>")
        assertThat(outText).contains("Salt for the SOFT HSM root wrapping key.")
    }


    @Test
    fun `Should output missing options when targetting Hashicorp Vault`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        var outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-crypto-config", "-t", "VAULT")
        }
        assertThat(outText).contains("'vaultPath' must be set for VAULT type secrets.")
    }

    @Suppress("MaxLineLength")
    private val expectedPrefix =
        "insert into config (config, is_deleted, schema_version_major, schema_version_minor, section, update_actor, update_ts, version) values ('"

    @Test
    fun `Should be able to create default initial crypto configuration with defined wrapping key`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-p", "passphrase",
                "-s", "salt",
                "-wp", "master-passphrase",
                "-ws", "master-salt"
            )
        }
        println(outText)
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertThat(json).containsSubsequence("\"passphrase\":{\"configSecret\":{\"encryptedSecret\":")
        assertGeneratedJson(json) { config: Config, factory: SmartConfigFactory ->
            val key1 = factory.create(config.getConfigList("wrappingKeys")[0])
            assertEquals("master-salt", key1.getString("salt"))
            assertEquals("master-passphrase", key1.getString("passphrase"))
        }
    }

    @Test
    fun `Should be able to create default initial crypto configuration with random wrapping key`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-p", "passphrase",
                "-s", "salt"
            )
        }
        println(outText)
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertGeneratedJson(json) { it: Config, _: SmartConfigFactory ->
            val key1 = it.getObjectList("wrappingKeys")[0]
            assertThat(key1.getValue("salt").render()).contains("configSecret")
            assertThat(key1.getValue("salt").render()).contains("encryptedSecret")
            assertThat(key1.getValue("passphrase").render()).contains("configSecret")
            assertThat(key1.getValue("passphrase").render()).contains("encryptedSecret")
        }
    }

    @Test
    fun `Should be able to create vault initial crypto configuration with random wrapping key`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-t", "VAULT",
                "--vault-path", "cryptosecrets",
                "--key-salt", "salt",
                "--key-passphrase", "passphrase",
            )
        }
        println(outText)
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertGeneratedJson(json) { it: Config, _: SmartConfigFactory ->
            val key1 = it.getObjectList("wrappingKeys")[0]
            assertThat(key1.getValue("salt").render()).doesNotContain("encryptedSecret")
            assertThat(key1.getValue("passphrase").render()).doesNotContain("encryptedSecret")
            assertThat(key1.getValue("salt").render()).contains("vaultKey")
            assertThat(key1.getValue("salt").render()).contains("vaultPath")
            assertThat(key1.getValue("passphrase").render()).contains("vaultKey")
            assertThat(key1.getValue("passphrase").render()).contains("vaultPath")
        }
    }

    private fun assertGeneratedJson(json: String, wrappingKeyAssert: (Config, SmartConfigFactory) -> Unit) {
        val smartConfigFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseString(
                """
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            ),
            listOf(EncryptionSecretsServiceFactory())
        )
        val config = smartConfigFactory.create(ConfigFactory.parseString(json))
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val softWorker = config.hsm()
        assertEquals(20000L, softWorker.retry.attemptTimeoutMills)
        assertEquals(3, softWorker.retry.maxAttempts)
        assertThat(softWorker.categories).hasSize(1)
        assertEquals("*", softWorker.categories[0].category)
        assertEquals(PrivateKeyPolicy.WRAPPED, softWorker.categories[0].policy)
        assertEquals(MasterKeyPolicy.UNIQUE, softWorker.masterKeyPolicy)
        assertNull(softWorker.masterKeyAlias)
        assertEquals(-1, softWorker.capacity)
        assertThat(softWorker.supportedSchemes).hasSize(8)
        assertThat(softWorker.supportedSchemes).contains(
            "CORDA.RSA",
            "CORDA.ECDSA.SECP256R1",
            "CORDA.ECDSA.SECP256K1",
            "CORDA.EDDSA.ED25519",
            "CORDA.X25519",
            "CORDA.SM2",
            "CORDA.GOST3410.GOST3411",
            "CORDA.SPHINCS-256"
        )
        val hsmCfg = softWorker.cfg
        wrappingKeyAssert(hsmCfg, smartConfigFactory)
        val opsBusProcessor = config.opsBusProcessor()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.flowBusProcessor()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.hsmRegistrationBusProcessor()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }
}
