/* 
This file is licensed to you under the Apache License, Version 2.0
(http://www.apache.org/licenses/LICENSE-2.0) or the MIT license
(http://opensource.org/licenses/MIT), at your option.

Unless required by applicable law or agreed to in writing, this software is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS OF
ANY KIND, either express or implied. See the LICENSE-MIT and LICENSE-APACHE
files for the specific language governing permissions and limitations under
each license.
*/

package org.contentauth.c2pa

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.security.auth.x500.X500Principal

/** Certificate management for C2PA signing on Android */
object CertificateManager {

    init {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    data class CertificateConfig(
        val commonName: String,
        val organization: String? = null,
        val organizationalUnit: String? = null,
        val country: String? = null,
        val state: String? = null,
        val locality: String? = null,
        val emailAddress: String? = null,
    )

    /**
     * Creates a Certificate Signing Request (CSR) for a hardware-backed key. Uses standard
     * BouncyCastle APIs since Android allows signing operations with hardware-backed keys through
     * the KeyStore API.
     */
    @JvmStatic
    fun createCSR(keyAlias: String, config: CertificateConfig): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        // Get the private key reference (not the actual key material for hardware-backed keys)
        val privateKey =
            keyStore.getKey(keyAlias, null) as? PrivateKey
                ?: throw C2PAError.Api("Private key not found for alias: $keyAlias")

        // Get the public key
        val certificate =
            keyStore.getCertificate(keyAlias)
                ?: throw C2PAError.Api("Certificate not found for alias: $keyAlias")
        val publicKey = certificate.publicKey

        // Build X500 subject
        val subjectDN = buildX500Name(config)

        // Create CSR using BouncyCastle with the hardware-backed private key
        val csrBuilder = JcaPKCS10CertificationRequestBuilder(subjectDN, publicKey)

        // Create content signer using the hardware-backed private key
        val contentSigner = createContentSigner(privateKey)

        // Build the CSR
        val csr = csrBuilder.build(contentSigner)

        // Convert to PEM format
        return csrToPEM(csr)
    }

    /**
     * Creates a CSR for a StrongBox-backed key (Android P+). StrongBox is Android's hardware
     * security module.
     */
    @JvmStatic
    fun createStrongBoxCSR(config: StrongBoxSigner.Config, certificateConfig: CertificateConfig): String {
        // Ensure StrongBox key exists or create it
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(config.keyTag)) {
            // Create StrongBox key if it doesn't exist
            createStrongBoxKey(config)
        }

        return createCSR(config.keyTag, certificateConfig)
    }

    /** Configuration for temporary certificate generation */
    data class TempCertificateConfig(
        val subject: String = "CN=Temporary, O=C2PA, C=US",
        val serialNumber: Long = System.currentTimeMillis(),
        val validityDays: Int = 365,
    )

    /** Generate a new hardware-backed key specifically for CSR generation */
    @JvmStatic
    fun generateHardwareKey(
        keyAlias: String,
        requireStrongBox: Boolean = false,
        tempCertConfig: TempCertificateConfig = TempCertificateConfig(),
    ): KeyPair {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")

        val builder =
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .apply {
                    setDigests(KeyProperties.DIGEST_SHA256)
                    setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    setCertificateSubject(X500Principal(tempCertConfig.subject))
                    setCertificateSerialNumber(
                        java.math.BigInteger.valueOf(tempCertConfig.serialNumber),
                    )
                    setCertificateNotBefore(
                        java.util.Date(System.currentTimeMillis() - 86400000L),
                    )
                    setCertificateNotAfter(
                        java.util.Date(
                            System.currentTimeMillis() +
                                86400000L * tempCertConfig.validityDays,
                        ),
                    )

                    if (requireStrongBox) {
                        setIsStrongBoxBacked(true)
                    }

                    // For CSR generation, we don't require user authentication
                    setUserAuthenticationRequired(false)
                }

        keyPairGenerator.initialize(builder.build())
        return keyPairGenerator.generateKeyPair()
    }

    /** Check if a key is hardware-backed */
    @JvmStatic
    fun isKeyHardwareBacked(keyAlias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return false
            val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel >= KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Check if a key is StrongBox-backed (Android P+) */
    @RequiresApi(Build.VERSION_CODES.S)
    @JvmStatic
    fun isKeyStrongBoxBacked(keyAlias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return false
            val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)

            keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } catch (e: Exception) {
            false
        }
    }

    // Private helper methods

    private fun buildX500Name(config: CertificateConfig): X500Name {
        val parts = mutableListOf<String>()
        parts.add("CN=${config.commonName}")
        config.organization?.let { parts.add("O=$it") }
        config.organizationalUnit?.let { parts.add("OU=$it") }
        config.locality?.let { parts.add("L=$it") }
        config.state?.let { parts.add("ST=$it") }
        config.country?.let { parts.add("C=$it") }
        config.emailAddress?.let { parts.add("EMAILADDRESS=$it") }
        return X500Name(parts.joinToString(", "))
    }

    private fun createContentSigner(privateKey: PrivateKey): ContentSigner {
        // For EC keys, use SHA256withECDSA
        val signatureAlgorithm =
            when (privateKey.algorithm) {
                "EC" -> "SHA256withECDSA"
                "RSA" -> "SHA256withRSA"
                else ->
                    throw C2PAError.Api(
                        "Unsupported key algorithm: ${privateKey.algorithm}",
                    )
            }

        // Create a custom ContentSigner that uses Android KeyStore for signing
        return AndroidKeyStoreContentSigner(privateKey, signatureAlgorithm)
    }

    private fun csrToPEM(csr: PKCS10CertificationRequest): String {
        val writer = StringWriter()
        val pemWriter = PemWriter(writer)
        val pemObject = PemObject("CERTIFICATE REQUEST", csr.encoded)
        pemWriter.writeObject(pemObject)
        pemWriter.close()
        return writer.toString()
    }

    private fun createStrongBoxKey(
        config: StrongBoxSigner.Config,
        tempCertConfig: TempCertificateConfig =
            TempCertificateConfig(subject = "CN=StrongBox Key, O=C2PA, C=US"),
    ): PrivateKey {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")

        val builder =
            KeyGenParameterSpec.Builder(config.keyTag, config.accessControl).apply {
                setDigests(KeyProperties.DIGEST_SHA256)
                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                setCertificateSubject(X500Principal(tempCertConfig.subject))
                setCertificateSerialNumber(
                    java.math.BigInteger.valueOf(tempCertConfig.serialNumber),
                )
                setCertificateNotBefore(java.util.Date(System.currentTimeMillis() - 86400000L))
                setCertificateNotAfter(
                    java.util.Date(
                        System.currentTimeMillis() +
                            86400000L * tempCertConfig.validityDays,
                    ),
                )
                setUserAuthenticationRequired(false)
                setIsStrongBoxBacked(true)
            }

        keyPairGenerator.initialize(builder.build())
        val keyPair = keyPairGenerator.generateKeyPair()
        return keyPair.private
    }

    /** Custom ContentSigner that uses Android KeyStore for signing operations */
    private class AndroidKeyStoreContentSigner(
        private val privateKey: PrivateKey,
        private val signatureAlgorithm: String,
    ) : ContentSigner {

        private val signature = Signature.getInstance(signatureAlgorithm)
        private val outputStream = java.io.ByteArrayOutputStream()

        init {
            signature.initSign(privateKey)
        }

        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = when (signatureAlgorithm) {
            "SHA256withECDSA" ->
                AlgorithmIdentifier(
                    ASN1ObjectIdentifier("1.2.840.10045.4.3.2"), // ecdsaWithSHA256
                )
            "SHA256withRSA" ->
                AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption)
            else ->
                throw IllegalArgumentException(
                    "Unsupported algorithm: $signatureAlgorithm",
                )
        }

        override fun getOutputStream(): java.io.OutputStream = outputStream

        override fun getSignature(): ByteArray {
            val dataToSign = outputStream.toByteArray()
            signature.update(dataToSign)
            return signature.sign()
        }
    }

    /**
     * Creates a hardware-backed signer by generating a CSR, submitting it to the signing server,
     * and configuring the signer with the returned certificate chain.
     *
     * @param keyAlias The alias for the hardware key
     * @param certificateConfig Configuration for the certificate
     * @param signingServerUrl URL of the signing server
     * @param apiKey Optional API key for authentication
     * @param requireStrongBox Whether to require StrongBox (will use regular hardware keystore if
     * false)
     * @return A Signer configured with the signed certificate chain
     */
    @JvmStatic
    suspend fun createSignerWithCSR(
        keyAlias: String,
        certificateConfig: CertificateConfig,
        signingServerUrl: String = LOCAL_SERVER,
        apiKey: String? = null,
        requireStrongBox: Boolean = false,
    ): Signer {
        // Generate hardware-backed key if it doesn't exist
        if (!KeyStoreSigner.keyExists(keyAlias)) {
            generateHardwareKey(keyAlias, requireStrongBox)
        }

        // Generate CSR
        val csr = createCSR(keyAlias, certificateConfig)

        // Submit to signing server
        val metadata =
            CSRMetadata(deviceId = Build.ID, appVersion = "1.0.0", purpose = "c2pa-signing")

        val response = submitCSR(csr, metadata, signingServerUrl, apiKey).getOrThrow()

        // Create signer with the certificate chain using KeyStoreSigner
        return KeyStoreSigner.createSigner(
            algorithm = SigningAlgorithm.ES256,
            certificateChainPEM = response.certificate_chain,
            keyAlias = keyAlias,
            tsaURL = null,
        )
    }

    /**
     * Creates a StrongBox-backed signer by generating a CSR, submitting it to the signing server,
     * and configuring the signer with the returned certificate chain.
     *
     * @param algorithm The signing algorithm (only ES256 supported for StrongBox)
     * @param strongBoxConfig Configuration for the StrongBox key
     * @param certificateConfig Configuration for the certificate
     * @param tsaURL Optional timestamp authority URL
     * @param signingServerUrl URL of the signing server
     * @param apiKey Optional API key for authentication
     * @return A Signer configured with the signed certificate chain
     */
    @JvmStatic
    suspend fun createStrongBoxSignerWithCSR(
        algorithm: SigningAlgorithm,
        strongBoxConfig: StrongBoxSigner.Config,
        certificateConfig: CertificateConfig,
        tsaURL: String? = null,
        signingServerUrl: String = LOCAL_SERVER,
        apiKey: String? = null,
    ): Signer {
        require(algorithm == SigningAlgorithm.ES256) { "StrongBox only supports ES256" }

        // Generate CSR for StrongBox key
        val csr = createStrongBoxCSR(strongBoxConfig, certificateConfig)

        // Submit to signing server
        val metadata =
            CSRMetadata(
                deviceId = Build.ID,
                appVersion = "1.0.0",
                purpose = "strongbox-signing",
            )

        val response = submitCSR(csr, metadata, signingServerUrl, apiKey).getOrThrow()

        // Create signer with the certificate chain using StrongBoxSigner
        return StrongBoxSigner.createSigner(
            algorithm = algorithm,
            certificateChainPEM = response.certificate_chain,
            config = strongBoxConfig,
            tsaURL = tsaURL,
        )
    }

    // Private helper for CSR signing server communication

    @Serializable private data class CSRRequest(val csr: String, val metadata: CSRMetadata? = null)

    @Serializable
    private data class CSRMetadata(
        val deviceId: String? = null,
        val appVersion: String? = null,
        val purpose: String? = null,
    )

    @Serializable
    private data class CSRResponse(
        val certificate_id: String,
        val certificate_chain: String,
        val expires_at: String,
        val serial_number: String,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private suspend fun submitCSR(
        csr: String,
        metadata: CSRMetadata,
        signingServerUrl: String,
        apiKey: String?,
    ): Result<CSRResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$signingServerUrl/api/v1/certificates/sign")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            apiKey?.let { connection.setRequestProperty("X-API-Key", it) }

            val request = CSRRequest(csr, metadata)
            val requestJson = json.encodeToString(request)

            connection.outputStream.use { output ->
                output.write(requestJson.toByteArray())
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val csrResponse = json.decodeFromString<CSRResponse>(response)
                Result.success(csrResponse)
            } else {
                val error =
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "HTTP ${connection.responseCode}"
                connection.disconnect()
                Result.failure(C2PAError.Api("CSR signing failed: $error"))
            }
        } catch (e: Exception) {
            Result.failure(C2PAError.Api("CSR signing request failed: ${e.message}"))
        }
    }

    private const val LOCAL_SERVER = "http://localhost:8080"
}
