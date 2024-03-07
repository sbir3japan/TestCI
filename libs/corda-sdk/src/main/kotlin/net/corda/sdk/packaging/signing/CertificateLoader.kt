package net.corda.sdk.packaging.signing

import net.corda.sdk.packaging.FileHelpers
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

object CertificateLoader {
    /**
     * Reads trusted certificates from keystore [keyStoreFileName]
     */
    fun readCertificates(keyStoreFileName: String, keyStorePass: String): Collection<X509Certificate> {
        FileHelpers.requireFileExists(keyStoreFileName)
        val keyStore = KeyStore.getInstance(File(keyStoreFileName), keyStorePass.toCharArray())
        return keyStore.aliases().asSequence()
            .filter(keyStore::isCertificateEntry)
            .map { keyStore.getCertificate(it) as X509Certificate }
            .toList()
    }
}