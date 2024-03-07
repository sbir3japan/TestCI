package net.corda.sdk.packaging

import net.corda.libs.packaging.verify.PackageType
import net.corda.libs.packaging.verify.VerifierBuilder
import net.corda.libs.packaging.verify.internal.VerifierFactory
import net.corda.sdk.packaging.signing.CertificateLoader
import net.corda.sdk.packaging.signing.SigningHelpers
import net.corda.sdk.packaging.signing.SigningParameters
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * Creates a CPI v2 from a CPB and GroupPolicy.json file.
 */
object CreateCpiV2 {
    /**
     * Filename of group policy within jar file
     */
    const val META_INF_GROUP_POLICY_JSON = "META-INF/GroupPolicy.json"

    const val MANIFEST_VERSION = "1.0"
    const val CPI_FORMAT_ATTRIBUTE = "2.0"

    val cpiFormatAttributeName = Attributes.Name("Corda-CPI-Format")
    val cpiNameAttributeName = Attributes.Name("Corda-CPI-Name")
    val cpiVersionAttributeName = Attributes.Name("Corda-CPI-Version")
    val cpiUpgradeAttributeName = Attributes.Name("Corda-CPI-Upgrade")

    /**
     * @throws IllegalArgumentException if it fails to verify Cpb V2
     */
    fun verifyIsValidCpbV2(cpbPath: Path, signingParameters: SigningParameters) {
        VerifierBuilder()
            .type(PackageType.CPB)
            .format(VerifierFactory.FORMAT_2)
            .name(cpbPath.toString())
            .inputStream(FileInputStream(cpbPath.toString()))
            .trustedCerts(
                CertificateLoader.readCertificates(
                    signingParameters.keyStoreFileName,
                    signingParameters.keyStorePass
                )
            )
            .build()
            .verify()
    }

    /**
     * Build and sign CPI file
     *
     * Creates a temporary file, copies CPB into temporary file, adds group policy then signs
     */
    fun buildAndSignCpi(
        cpbPath: Path?,
        outputFilePath: Path,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String,
        cpiUpgrade: Boolean,
        signingParameters: SigningParameters
    ) {
        val unsignedCpi = Files.createTempFile("buildCPI", null)
        try {
            // Build unsigned CPI jar
            buildUnsignedCpi(
                cpbPath,
                unsignedCpi,
                groupPolicy,
                cpiName,
                cpiVersion,
                cpiUpgrade,
            )

            // Sign CPI jar
            SigningHelpers.sign(
                unsignedCpi,
                outputFilePath,
                signingParameters,
            )
        } finally {
            // Delete temp file
            Files.deleteIfExists(unsignedCpi)
        }
    }

    /**
     * Build unsigned CPI file
     *
     * Copies CPB into new jar file and then adds group policy
     */
    private fun buildUnsignedCpi(
        cpbPath: Path?,
        unsignedCpi: Path,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String,
        cpiUpgrade: Boolean,
    ) {
        val manifest = Manifest()
        val manifestMainAttributes = manifest.mainAttributes
        manifestMainAttributes[Attributes.Name.MANIFEST_VERSION] = MANIFEST_VERSION
        manifestMainAttributes[cpiFormatAttributeName] = CPI_FORMAT_ATTRIBUTE
        manifestMainAttributes[cpiNameAttributeName] = cpiName
        manifestMainAttributes[cpiVersionAttributeName] = cpiVersion
        manifestMainAttributes[cpiUpgradeAttributeName] = cpiUpgrade.toString()

        JarOutputStream(Files.newOutputStream(unsignedCpi, StandardOpenOption.WRITE), manifest).use { cpiJar ->
            cpbPath?.let {
                // Copy the CPB contents
                cpiJar.putNextEntry(JarEntry(cpbPath.fileName.toString()))
                Files.newInputStream(cpbPath, StandardOpenOption.READ).use {
                    it.copyTo(cpiJar)
                }
            }

            // Add group policy
            addGroupPolicy(cpiJar, groupPolicy)
        }
    }

    /**
     * Adds group policy file to jar file
     *
     * Reads group policy from stdin or file depending on user choice
     */
    private fun addGroupPolicy(cpiJar: JarOutputStream, groupPolicy: String) {
        cpiJar.putNextEntry(JarEntry(META_INF_GROUP_POLICY_JSON))
        cpiJar.write(groupPolicy.toByteArray())
        cpiJar.closeEntry()
    }
}