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

package org.contentauth.c2pa.test.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.CertificateManager
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.KeyStoreSigner
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.StrongBoxSigner
import org.contentauth.c2pa.derToRawSignature
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** SignerTests - Signing and signer-related tests */
abstract class SignerTests : TestBase() {

    companion object {
        // Use 10.0.2.2 for Android emulator to access host's localhost
        private const val EMULATOR_SERVER_URL = "http://10.0.2.2:8080"

        private fun getServerUrl(): String = System.getenv("SIGNING_SERVER_URL") ?: EMULATOR_SERVER_URL
    }

    private suspend fun isSigningServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getServerUrl()}/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testSignerWithCallback(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer with Callback") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                val fileTest = File.createTempFile("c2pa-callback-signer", ".jpg")
                try {
                    Builder.fromJson(manifestJson).use { builder ->
                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")

                        val certPem = loadResourceAsString("es256_certs")
                        val keyPem = loadResourceAsString("es256_private")

                        var signCallCount = 0

                        Signer.withCallback(SigningAlgorithm.ES256, certPem, null) { data ->
                            signCallCount++
                            SigningHelper.signWithPEMKey(data, keyPem, "ES256")
                        }.use { callbackSigner ->
                            ByteArrayStream(sourceImageData).use { sourceStream ->
                                FileStream(fileTest).use { destStream ->
                                    val reserveSize = callbackSigner.reserveSize()
                                    val result =
                                        builder.sign(
                                            "image/jpeg",
                                            sourceStream,
                                            destStream,
                                            callbackSigner,
                                        )
                                    val signSucceeded = result.size > 0

                                    val (manifest, signatureVerified) =
                                        if (signSucceeded) {
                                            try {
                                                val readManifest =
                                                    C2PA.readFile(fileTest.absolutePath)
                                                val isValid =
                                                    readManifest.isNotEmpty() &&
                                                        readManifest.contains("manifests")
                                                if (isValid) {
                                                    Pair(readManifest, true)
                                                } else {
                                                    Pair(null, false)
                                                }
                                            } catch (e: Exception) {
                                                Pair(null, false)
                                            }
                                        } else {
                                            Pair(null, false)
                                        }

                                    val success =
                                        signCallCount > 0 &&
                                            reserveSize > 0 &&
                                            signSucceeded &&
                                            signatureVerified

                                    TestResult(
                                        "Signer with Callback",
                                        success,
                                        if (success) {
                                            "Callback signer created and used successfully"
                                        } else {
                                            "Callback signer test failed"
                                        },
                                        buildString {
                                            append("Callback invoked: $signCallCount time(s)\n")
                                            append("Reserve size: $reserveSize bytes\n")
                                            append("Signing succeeded: $signSucceeded\n")
                                            append("Signature verified: $signatureVerified")
                                            if (manifest != null && manifest.length > 100) {
                                                append("\nManifest size: ${manifest.length} chars")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    fileTest.delete()
                }
            } catch (e: Exception) {
                TestResult(
                    "Signer with Callback",
                    false,
                    "Test failed with exception",
                    "${e.javaClass.simpleName}: ${e.message}\n${e.stackTrace.take(3).joinToString("\n")}",
                )
            }
        }
    }

    suspend fun testHardwareSignerCreation(): TestResult = withContext(Dispatchers.IO) {
        runTest("Hardware Signer Creation") {
            val hasStrongBox =
                getContext()
                    .packageManager
                    .hasSystemFeature(
                        android.content.pm.PackageManager
                            .FEATURE_STRONGBOX_KEYSTORE,
                    )

            var genInHw = false
            try {
                val keyAlias = "test_hw_key_${System.currentTimeMillis()}"
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)

                val keyGenSpec =
                    android.security.keystore.KeyGenParameterSpec.Builder(
                        keyAlias,
                        android.security.keystore.KeyProperties
                            .PURPOSE_SIGN,
                    )
                        .apply {
                            setAlgorithmParameterSpec(
                                java.security.spec.ECGenParameterSpec(
                                    "secp256r1",
                                ),
                            )
                            setDigests(
                                android.security.keystore.KeyProperties
                                    .DIGEST_SHA256,
                            )
                            if (hasStrongBox) {
                                setIsStrongBoxBacked(true)
                            }
                        }
                        .build()

                val keyPairGen =
                    java.security.KeyPairGenerator.getInstance(
                        android.security.keystore.KeyProperties.KEY_ALGORITHM_EC,
                        "AndroidKeyStore",
                    )
                keyPairGen.initialize(keyGenSpec)
                keyPairGen.generateKeyPair()

                val key = keyStore.getKey(keyAlias, null) as? java.security.PrivateKey
                if (key != null &&
                    android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.S
                ) {
                    try {
                        val factory =
                            java.security.KeyFactory.getInstance(
                                key.algorithm,
                                "AndroidKeyStore",
                            )
                        val keyInfo =
                            factory.getKeySpec(
                                key,
                                android.security.keystore.KeyInfo::class.java,
                            )
                        @Suppress("DEPRECATION")
                        genInHw = keyInfo.isInsideSecureHardware
                    } catch (_: Exception) {
                        genInHw = hasStrongBox
                    }
                }

                keyStore.deleteEntry(keyAlias)
            } catch (_: Exception) {
                // Hardware key generation failed
            }

            val success = genInHw || !hasStrongBox
            TestResult(
                "Hardware Signer Creation",
                success,
                if (genInHw) {
                    "Generated key in hardware"
                } else if (!hasStrongBox) {
                    "No StrongBox available"
                } else {
                    "Failed to use hardware"
                },
                "StrongBox available: $hasStrongBox, Generated in HW: $genInHw",
            )
        }
    }

    suspend fun testStrongBoxSignerCreation(): TestResult = withContext(Dispatchers.IO) {
        runTest("StrongBox Signer Creation") {
            val hasStrongBox =
                getContext()
                    .packageManager
                    .hasSystemFeature(
                        android.content.pm.PackageManager
                            .FEATURE_STRONGBOX_KEYSTORE,
                    )

            var strongBoxKeyCreated = false
            if (hasStrongBox) {
                try {
                    val keyAlias = "test_strongbox_key_${System.currentTimeMillis()}"
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)

                    val keyGenSpec =
                        android.security.keystore.KeyGenParameterSpec.Builder(
                            keyAlias,
                            android.security.keystore.KeyProperties
                                .PURPOSE_SIGN,
                        )
                            .apply {
                                setAlgorithmParameterSpec(
                                    java.security.spec.ECGenParameterSpec(
                                        "secp256r1",
                                    ),
                                )
                                setDigests(
                                    android.security.keystore.KeyProperties
                                        .DIGEST_SHA256,
                                )
                                setIsStrongBoxBacked(true)
                            }
                            .build()

                    val keyPairGen =
                        java.security.KeyPairGenerator.getInstance(
                            android.security.keystore.KeyProperties
                                .KEY_ALGORITHM_EC,
                            "AndroidKeyStore",
                        )
                    keyPairGen.initialize(keyGenSpec)
                    keyPairGen.generateKeyPair()

                    strongBoxKeyCreated = keyStore.containsAlias(keyAlias)

                    keyStore.deleteEntry(keyAlias)
                } catch (_: Exception) {
                    // StrongBox key generation failed
                }
            }

            val success = strongBoxKeyCreated || !hasStrongBox
            TestResult(
                "StrongBox Signer Creation",
                success,
                if (strongBoxKeyCreated) {
                    "StrongBox key created"
                } else if (!hasStrongBox) {
                    "StrongBox not available"
                } else {
                    "StrongBox key creation failed"
                },
                "Has StrongBox: $hasStrongBox, Key created: $strongBoxKeyCreated",
            )
        }
    }

    suspend fun testSigningAlgorithms(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signing Algorithm Tests") {
            val algorithms = SigningAlgorithm.entries.map { it.name.lowercase() }
            val resultPerAlg = mutableListOf<String>()

            algorithms.forEach { alg ->
                try {
                    val manifestJson = TEST_MANIFEST_JSON
                    val fileTest = File.createTempFile("c2pa-algorithm-$alg", ".jpg")

                    try {
                        Builder.fromJson(manifestJson).use { builder ->
                            val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")

                            val certPem = loadResourceAsString("${alg}_certs")
                            val keyPem = loadResourceAsString("${alg}_private")
                            val algorithm =
                                SigningAlgorithm.entries.find {
                                    it.name.equals(alg, ignoreCase = true)
                                }
                                    ?: throw IllegalArgumentException(
                                        "Unsupported algorithm: $alg",
                                    )
                            val signerInfo = SignerInfo(algorithm, certPem, keyPem)

                            Signer.fromInfo(signerInfo).use { signer ->
                                ByteArrayStream(sourceImageData).use { sourceStream ->
                                    FileStream(fileTest).use { destStream ->
                                        builder.sign("image/jpeg", sourceStream, destStream, signer)
                                        val manifest = C2PA.readFile(fileTest.absolutePath)
                                        val ok = manifest.isNotEmpty() && manifest.contains("manifests")
                                        resultPerAlg.add("$alg:${if (ok) "ok" else "fail"}")
                                    }
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                } catch (e: Exception) {
                    resultPerAlg.add("$alg:fail(${e.message?.take(50)})")
                }
            }

            val success = resultPerAlg.all { it.contains(":ok") }
            TestResult(
                "Signing Algorithm Tests",
                success,
                if (success) "All algorithms passed" else "Some algorithms failed",
                resultPerAlg.joinToString(", "),
            )
        }
    }

    suspend fun testSignerReserveSize(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer Reserve Size") {
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")
            val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)

            Signer.fromInfo(signerInfo).use { signer ->
                val reserveSize = signer.reserveSize()
                val success = reserveSize > 0
                TestResult(
                    "Signer Reserve Size",
                    success,
                    if (success) {
                        "Signer reserve size obtained"
                    } else {
                        "Invalid reserve size"
                    },
                    "Reserve size: $reserveSize bytes",
                )
            }
        }
    }

    suspend fun testSignFile(): TestResult = withContext(Dispatchers.IO) {
        runTest("Sign File") {
            val sourceFile =
                copyResourceToFile("pexels_asadphoto_457882", "source_signfile.jpg")
            val destFile = File(getContext().cacheDir, "dest_signfile.jpg")

            try {
                val certPem = loadResourceAsString("es256_certs")
                val keyPem = loadResourceAsString("es256_private")
                val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)

                val manifestJson = TEST_MANIFEST_JSON

                C2PA.signFile(
                    sourceFile.absolutePath,
                    destFile.absolutePath,
                    manifestJson,
                    signerInfo,
                )

                val fileExists = destFile.exists() && destFile.length() > 0
                val manifest = if (fileExists) C2PA.readFile(destFile.absolutePath) else ""
                val hasManifest = manifest.isNotEmpty() && manifest.contains("manifests")
                val success = fileExists && hasManifest

                TestResult(
                    "Sign File",
                    success,
                    if (success) {
                        "File signed successfully with valid manifest"
                    } else {
                        "Failed to sign file or manifest invalid"
                    },
                    "Dest size: ${destFile.length()}, Has manifest: $hasManifest",
                )
            } finally {
                sourceFile.delete()
                destFile.delete()
            }
        }
    }

    suspend fun testAlgorithmCoverage(): TestResult = withContext(Dispatchers.IO) {
        runTest("Algorithm Coverage") {
            val testedAlgorithms = mutableListOf<String>()
            val supportedAlgorithms = mutableListOf<SigningAlgorithm>()

            for (alg in SigningAlgorithm.values()) {
                testedAlgorithms.add("${alg.name}: ${alg.description}")

                // Test that we can at least create the enum value
                when (alg) {
                    SigningAlgorithm.ES256,
                    SigningAlgorithm.ES384,
                    SigningAlgorithm.ES512,
                    SigningAlgorithm.PS256,
                    SigningAlgorithm.PS384,
                    SigningAlgorithm.PS512,
                    -> supportedAlgorithms.add(alg)
                    SigningAlgorithm.ED25519 -> supportedAlgorithms.add(alg)
                }
            }

            val success =
                testedAlgorithms.size == SigningAlgorithm.values().size &&
                    supportedAlgorithms.size >= 6

            TestResult(
                "Algorithm Coverage",
                success,
                if (success) "All algorithms covered" else "Some algorithms missing",
                "Tested: ${testedAlgorithms.size}, Supported: ${supportedAlgorithms.size}\n" +
                    testedAlgorithms.joinToString("\n"),
            )
        }
    }

    /**
     * Simple signing helper for Android C2PA callback signers Moved from SigningHelper.kt since
     * it's only used by SignerTests
     */
    private object SigningHelper {

        /** Sign data using an existing private key in PEM format */
        fun signWithPEMKey(data: ByteArray, pemPrivateKey: String, algorithm: String = "ES256"): ByteArray {
            val privateKeyStr =
                pemPrivateKey
                    .replace("-----BEGIN EC PRIVATE KEY-----", "")
                    .replace("-----END EC PRIVATE KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim()

            val keyBytes = Base64.getDecoder().decode(privateKeyStr)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKey = keyFactory.generatePrivate(keySpec)
            val (hashAlgorithm, componentSize) =
                when (algorithm.uppercase()) {
                    "ES256" -> Pair("SHA256withECDSA", 32)
                    "ES384" -> Pair("SHA384withECDSA", 48)
                    "ES512" -> Pair("SHA512withECDSA", 66) // P-521 uses 66 bytes
                    else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
                }

            val signature = Signature.getInstance(hashAlgorithm)
            signature.initSign(privateKey)
            signature.update(data)
            val derSignature = signature.sign()

            return derToRawSignature(derSignature, componentSize)
        }
    }

    suspend fun testKeyStoreSignerIntegration(): TestResult = withContext(Dispatchers.IO) {
        if (!isSigningServerAvailable()) {
            return@withContext TestResult(
                "KeyStore Signer Integration",
                true,
                "SKIPPED: Signing server not available",
                status = TestStatus.SKIPPED,
            )
        }

        runTest("KeyStore Signer Integration") {
            val keyAlias = "test_keystore_signing_${System.currentTimeMillis()}"
            val signingServerUrl = getServerUrl()

            try {
                // Generate a hardware-backed key and get a real certificate from signing
                // server
                CertificateManager.createSignerWithCSR(
                    keyAlias = keyAlias,
                    certificateConfig =
                    CertificateManager.CertificateConfig(
                        commonName = "Test KeyStore Signer",
                        organization = "C2PA Test Suite",
                        organizationalUnit = "Testing",
                        country = "US",
                        state = "CA",
                        locality = "San Francisco",
                    ),
                    signingServerUrl = signingServerUrl,
                    requireStrongBox = false,
                ).use { signer ->
                    // Test signing with the valid certificate
                    val manifestJson = TEST_MANIFEST_JSON

                    Builder.fromJson(manifestJson).use { builder ->
                        val sourceData = loadResourceAsBytes("pexels_asadphoto_457882")
                        ByteArrayStream(sourceData).use { sourceStream ->
                            ByteArrayStream().use { destStream ->
                                val result =
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)

                                val signSucceeded = result.size > 0
                                val destData = destStream.getData()
                                val hasData = destData.isNotEmpty()

                                // Verify the signed data has a manifest
                                val manifestVerified =
                                    if (hasData) {
                                        try {
                                            val tempFile =
                                                File.createTempFile(
                                                    "keystore_verify",
                                                    ".jpg",
                                                )
                                            tempFile.writeBytes(destData)
                                            val manifest = C2PA.readFile(tempFile.absolutePath)
                                            tempFile.delete()
                                            manifest.isNotEmpty() &&
                                                manifest.contains("manifests")
                                        } catch (e: Exception) {
                                            false
                                        }
                                    } else {
                                        false
                                    }

                                val success = signSucceeded && hasData && manifestVerified

                                TestResult(
                                    "KeyStore Signer Integration",
                                    success,
                                    if (success) {
                                        "KeyStoreSigner successfully signed and verified with CSR certificate"
                                    } else {
                                        "KeyStoreSigner failed"
                                    },
                                    "Signed: $signSucceeded, Has data: $hasData (${destData.size} bytes), Verified: $manifestVerified",
                                )
                            }
                        }
                    }
                }
            } finally {
                // Cleanup
                KeyStoreSigner.deleteKey(keyAlias)
            }
        }
    }

    suspend fun testStrongBoxSignerIntegration(): TestResult = withContext(Dispatchers.IO) {
        if (!isSigningServerAvailable()) {
            return@withContext TestResult(
                "StrongBox Signer Integration",
                true,
                "SKIPPED: Signing server not available",
                status = TestStatus.SKIPPED,
            )
        }

        runTest("StrongBox Signer Integration") {
            val hasStrongBox = StrongBoxSigner.isAvailable(getContext())

            if (!hasStrongBox) {
                return@runTest TestResult(
                    "StrongBox Signer Integration",
                    true,
                    "StrongBox not available on this device (expected)",
                    "Device does not support StrongBox",
                )
            }

            val keyTag = "test_strongbox_signing_${System.currentTimeMillis()}"
            val signingServerUrl = getServerUrl()

            try {
                // Generate a StrongBox-backed key and get a real certificate from signing
                // server
                CertificateManager.createStrongBoxSignerWithCSR(
                    algorithm = SigningAlgorithm.ES256,
                    strongBoxConfig =
                    StrongBoxSigner.Config(
                        keyTag = keyTag,
                        requireUserAuthentication = false,
                    ),
                    certificateConfig =
                    CertificateManager.CertificateConfig(
                        commonName = "Test StrongBox Signer",
                        organization = "C2PA Test Suite",
                        organizationalUnit = "Testing",
                        country = "US",
                        state = "CA",
                        locality = "San Francisco",
                    ),
                    signingServerUrl = signingServerUrl,
                ).use { signer ->
                    // Test signing with the valid certificate
                    val manifestJson = TEST_MANIFEST_JSON

                    Builder.fromJson(manifestJson).use { builder ->
                        val sourceData = loadResourceAsBytes("pexels_asadphoto_457882")
                        ByteArrayStream(sourceData).use { sourceStream ->
                            ByteArrayStream().use { destStream ->
                                val result =
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)

                                val signSucceeded = result.size > 0
                                val destData = destStream.getData()
                                val hasData = destData.isNotEmpty()

                                // Verify key is actually in StrongBox (API 31+)
                                val isStrongBoxBacked =
                                    if (android.os.Build.VERSION.SDK_INT >=
                                        android.os.Build.VERSION_CODES.S
                                    ) {
                                        StrongBoxSigner.isKeyStrongBoxBacked(keyTag)
                                    } else {
                                        true // Can't verify on older versions, assume success
                                    }

                                // Verify the signed data has a manifest
                                val manifestVerified =
                                    if (hasData) {
                                        try {
                                            val tempFile =
                                                File.createTempFile(
                                                    "strongbox_verify",
                                                    ".jpg",
                                                )
                                            tempFile.writeBytes(destData)
                                            val manifest = C2PA.readFile(tempFile.absolutePath)
                                            tempFile.delete()
                                            manifest.isNotEmpty() &&
                                                manifest.contains("manifests")
                                        } catch (e: Exception) {
                                            false
                                        }
                                    } else {
                                        false
                                    }

                                val success =
                                    signSucceeded &&
                                        hasData &&
                                        isStrongBoxBacked &&
                                        manifestVerified

                                TestResult(
                                    "StrongBox Signer Integration",
                                    success,
                                    if (success) {
                                        "StrongBoxSigner successfully signed and verified with CSR certificate"
                                    } else {
                                        "StrongBoxSigner failed"
                                    },
                                    "Signed: $signSucceeded, Has data: $hasData (${destData.size} bytes), " +
                                        "StrongBox backed: $isStrongBoxBacked, Verified: $manifestVerified",
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "StrongBox Signer Integration",
                    false,
                    "StrongBox test failed with exception",
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            } finally {
                // Cleanup
                StrongBoxSigner.deleteKey(keyTag)
            }
        }
    }

    suspend fun testKeyStoreSignerKeyManagement(): TestResult = withContext(Dispatchers.IO) {
        runTest("KeyStore Signer Key Management") {
            val keyAlias = "test_key_mgmt_${System.currentTimeMillis()}"

            try {
                // Test key doesn't exist initially
                val initiallyExists = KeyStoreSigner.keyExists(keyAlias)

                // Generate key
                CertificateManager.generateHardwareKey(keyAlias, requireStrongBox = false)
                val existsAfterCreate = KeyStoreSigner.keyExists(keyAlias)

                // Check if hardware-backed
                val isHardwareBacked = KeyStoreSigner.isKeyHardwareBacked(keyAlias)

                // Delete key
                val deleted = KeyStoreSigner.deleteKey(keyAlias)
                val existsAfterDelete = KeyStoreSigner.keyExists(keyAlias)

                val success =
                    !initiallyExists &&
                        existsAfterCreate &&
                        deleted &&
                        !existsAfterDelete

                TestResult(
                    "KeyStore Signer Key Management",
                    success,
                    if (success) {
                        "Key lifecycle management works correctly"
                    } else {
                        "Key management has issues"
                    },
                    "Initial: $initiallyExists, After create: $existsAfterCreate, " +
                        "Hardware backed: $isHardwareBacked, Deleted: $deleted, " +
                        "After delete: $existsAfterDelete",
                )
            } catch (e: Exception) {
                TestResult(
                    "KeyStore Signer Key Management",
                    false,
                    "Key management test failed",
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }
    }

    suspend fun testStrongBoxAvailability(): TestResult = withContext(Dispatchers.IO) {
        runTest("StrongBox Availability Check") {
            val isAvailable = StrongBoxSigner.isAvailable(getContext())
            val hasFeature =
                getContext()
                    .packageManager
                    .hasSystemFeature(
                        android.content.pm.PackageManager
                            .FEATURE_STRONGBOX_KEYSTORE,
                    )

            // The two should match
            val consistent = isAvailable == hasFeature

            TestResult(
                "StrongBox Availability Check",
                consistent,
                if (isAvailable) {
                    "StrongBox is available on this device"
                } else {
                    "StrongBox is not available (normal for many devices)"
                },
                "Available: $isAvailable, Has feature: $hasFeature, Consistent: $consistent",
            )
        }
    }

    suspend fun testSignerFromSettingsToml(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer From Settings (TOML)") {
            try {
                val settingsToml = loadSharedResourceAsString("test_settings_with_cawg_signing.toml")
                    ?: throw IllegalArgumentException("Resource not found: test_settings_with_cawg_signing.toml")

                Signer.fromSettingsToml(settingsToml).use { signer ->
                    // Load test image
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")

                    // Create manifest
                    val manifestJson = TEST_MANIFEST_JSON

                    Builder.fromJson(manifestJson).use { builder ->
                        val destFile = File.createTempFile("cawg_toml_test", ".jpg")
                        try {
                            ByteArrayStream(sourceImageData).use { sourceStream ->
                                FileStream(destFile).use { destStream ->
                                    val result = builder.sign(
                                        "image/jpeg",
                                        sourceStream,
                                        destStream,
                                        signer,
                                    )

                                    val signSucceeded = result.size > 0

                                    // Verify the signed image contains a valid manifest
                                    val manifestResult = if (signSucceeded && destFile.exists()) {
                                        try {
                                            val manifest = C2PA.readFile(destFile.absolutePath)
                                            manifest
                                        } catch (e: Exception) {
                                            ""
                                        }
                                    } else {
                                        ""
                                    }

                                    val hasManifest = manifestResult.isNotEmpty() &&
                                        manifestResult.contains("manifests")

                                    // Check for CAWG assertions in the manifest
                                    val hasCawgContent = manifestResult.lowercase().let {
                                        it.contains("cawg") || it.contains("training-mining")
                                    }

                                    val success = signSucceeded && hasManifest

                                    TestResult(
                                        "Signer From Settings (TOML)",
                                        success,
                                        if (success) {
                                            if (hasCawgContent) {
                                                "Signed with CAWG signer - found CAWG content"
                                            } else {
                                                "Signed successfully (CAWG assertions may require SDK update)"
                                            }
                                        } else {
                                            "Signing failed"
                                        },
                                        "Signed: $signSucceeded, Has manifest: $hasManifest, Has CAWG: $hasCawgContent",
                                    )
                                }
                            }
                        } finally {
                            destFile.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "Signer From Settings (TOML)",
                    false,
                    "Test failed with exception",
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }
    }

    suspend fun testSignerFromSettingsJson(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer From Settings (JSON)") {
            try {
                val settingsJson = loadSharedResourceAsString("test_settings_with_cawg_signing.json")
                    ?: throw IllegalArgumentException("Resource not found: test_settings_with_cawg_signing.json")

                Signer.fromSettingsJson(settingsJson).use { signer ->
                    // Load test image
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")

                    // Create manifest
                    val manifestJson = TEST_MANIFEST_JSON

                    Builder.fromJson(manifestJson).use { builder ->
                        val destFile = File.createTempFile("cawg_json_test", ".jpg")
                        try {
                            ByteArrayStream(sourceImageData).use { sourceStream ->
                                FileStream(destFile).use { destStream ->
                                    val result = builder.sign(
                                        "image/jpeg",
                                        sourceStream,
                                        destStream,
                                        signer,
                                    )

                                    val signSucceeded = result.size > 0

                                    // Verify the signed image contains a valid manifest
                                    val manifestResult = if (signSucceeded && destFile.exists()) {
                                        try {
                                            val manifest = C2PA.readFile(destFile.absolutePath)
                                            manifest
                                        } catch (e: Exception) {
                                            ""
                                        }
                                    } else {
                                        ""
                                    }

                                    val hasManifest = manifestResult.isNotEmpty() &&
                                        manifestResult.contains("manifests")

                                    // Check for CAWG assertions in the manifest
                                    val hasCawgContent = manifestResult.lowercase().let {
                                        it.contains("cawg") || it.contains("training-mining")
                                    }

                                    val success = signSucceeded && hasManifest

                                    TestResult(
                                        "Signer From Settings (JSON)",
                                        success,
                                        if (success) {
                                            if (hasCawgContent) {
                                                "Signed with CAWG signer - found CAWG content"
                                            } else {
                                                "Signed successfully (CAWG assertions may require SDK update)"
                                            }
                                        } else {
                                            "Signing failed"
                                        },
                                        "Signed: $signSucceeded, Has manifest: $hasManifest, Has CAWG: $hasCawgContent",
                                    )
                                }
                            }
                        } finally {
                            destFile.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "Signer From Settings (JSON)",
                    false,
                    "Test failed with exception",
                    "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }
    }

    /**
     * Assert that the reader JSON contains a well-formed `cawg.identity` assertion: the
     * assertion is present in the manifest, its `signer_payload.referenced_assertions`
     * array is non-empty, and the active-manifest validation results include the
     * `cawg.identity.well-formed` success code. The exact labels that c2pa-rs emits
     * under `referenced_assertions` are implementation-defined and not asserted here.
     */
    protected fun assertCawgIdentityInManifest(manifestJson: String): String {
        val root = Json.parseToJsonElement(manifestJson).jsonObject
        val manifests = root["manifests"]?.jsonObject
            ?: throw AssertionError("Reader JSON has no 'manifests' object. JSON: $manifestJson")

        val cawgAssertions = manifests.values.flatMap { manifestEl ->
            val assertions = manifestEl.jsonObject["assertions"]?.jsonArray ?: JsonArray(emptyList())
            assertions.mapNotNull { it as? JsonObject }
                .filter { it["label"]?.jsonPrimitive?.contentOrNull == "cawg.identity" }
        }
        if (cawgAssertions.isEmpty()) {
            throw AssertionError("Expected a cawg.identity assertion. JSON: $manifestJson")
        }
        val foundRefs = cawgAssertions.flatMap { extractReferencedAssertionLabels(it) }
        if (foundRefs.isEmpty()) {
            throw AssertionError(
                "cawg.identity assertion has no referenced_assertions entries. JSON: $manifestJson",
            )
        }

        val successCodes = root["validation_results"]?.jsonObject
            ?.get("activeManifest")?.jsonObject
            ?.get("success")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull }
            ?: emptyList()
        if ("cawg.identity.well-formed" !in successCodes) {
            throw AssertionError(
                "validation_results missing cawg.identity.well-formed success code. JSON: $manifestJson",
            )
        }

        return "cawg.identity well-formed; refs=${foundRefs.joinToString(",")}"
    }

    /**
     * Extract the assertion labels referenced by a `cawg.identity` assertion. The c2pa-rs
     * reader emits these under `data.signer_payload.referenced_assertions[*].url` as JUMBF
     * URIs like `self#jumbf=c2pa.assertions/c2pa.actions`; the label is the last path segment.
     */
    private fun extractReferencedAssertionLabels(cawgAssertion: JsonObject): List<String> {
        val payload = cawgAssertion["data"]?.jsonObject?.get("signer_payload")?.jsonObject
            ?: return emptyList()
        val refs = payload["referenced_assertions"]?.jsonArray ?: return emptyList()
        return refs.mapNotNull { ref ->
            val obj = ref as? JsonObject ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            url.substringAfterLast('/')
        }
    }

    private fun pemSignCallback(
        privateKeyPem: String,
        algorithm: SigningAlgorithm = SigningAlgorithm.ES256,
    ): (ByteArray) -> ByteArray = { data ->
        SigningHelper.signWithPEMKey(data, privateKeyPem, algorithm.name)
    }

    /**
     * Sign the test image into a temp file with [signer] (closing it), read the result
     * back, and assert it carries a well-formed `cawg.identity` assertion. Returns the
     * detail string produced by [assertCawgIdentityInManifest].
     */
    private fun signAndVerifyCawgManifest(signer: Signer, tempPrefix: String): String {
        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
        val destFile = File.createTempFile(tempPrefix, ".jpg")
        return try {
            signer.use { s ->
                Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                    ByteArrayStream(sourceImageData).use { src ->
                        FileStream(destFile).use { dst ->
                            builder.sign("image/jpeg", src, dst, s)
                        }
                    }
                }
            }
            assertCawgIdentityInManifest(C2PA.readFile(destFile.absolutePath))
        } finally {
            destFile.delete()
        }
    }

    suspend fun testCawgCombinedPemSigner(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWG Combined Signer (PEM + PEM)") {
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")

            val c2paSigner = Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256)
            val identitySigner = Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256)

            val combined = Signer.withCawgIdentity(
                c2pa = c2paSigner,
                identity = identitySigner,
                referencedAssertions = listOf("c2pa.actions"),
            )

            val detail = signAndVerifyCawgManifest(combined, "cawg_combined_pem")
            TestResult(
                "CAWG Combined Signer (PEM + PEM)",
                true,
                "Combined PEM signing succeeded",
                detail,
            )
        }
    }

    suspend fun testCawgCombinedCallbackSigner(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWG Combined Signer (Callback + Callback)") {
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")

            val pemSign = pemSignCallback(keyPem)

            val c2paSigner = Signer.withCallback(
                SigningAlgorithm.ES256,
                certPem,
                sign = pemSign,
            )
            val identitySigner = Signer.withCallback(
                SigningAlgorithm.ES256,
                certPem,
                sign = pemSign,
            )

            val combined = Signer.withCawgIdentity(
                c2pa = c2paSigner,
                identity = identitySigner,
                referencedAssertions = listOf("c2pa.actions"),
            )

            val detail = signAndVerifyCawgManifest(combined, "cawg_combined_callback")
            TestResult(
                "CAWG Combined Signer (Callback + Callback)",
                true,
                "Combined callback signing succeeded",
                detail,
            )
        }
    }

    suspend fun testCawgConsumesInputSigners(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWG Combined Signer Consumes Inputs") {
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")

            val c2paSigner = Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256)
            val identitySigner = Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256)

            val combined = Signer.withCawgIdentity(
                c2pa = c2paSigner,
                identity = identitySigner,
                referencedAssertions = listOf("c2pa.actions"),
            )
            try {
                c2paSigner.close()
                identitySigner.close()
                combined.close()
                combined.close()
                TestResult(
                    "CAWG Combined Signer Consumes Inputs",
                    true,
                    "Inputs safely closeable after combine",
                    "no crash",
                )
            } catch (e: Exception) {
                combined.close()
                TestResult(
                    "CAWG Combined Signer Consumes Inputs",
                    false,
                    "Closing consumed inputs crashed: ${e.message}",
                    e::class.simpleName ?: "",
                )
            }
        }
    }

    suspend fun testCawgCombinedStrongBoxIdentity(): TestResult = withContext(Dispatchers.IO) {
        val signingServerAvailable = isSigningServerAvailable()
        if (!signingServerAvailable) {
            return@withContext TestResult(
                "CAWG Combined Signer (StrongBox Identity)",
                true,
                "SKIPPED: Signing server not available",
                status = TestStatus.SKIPPED,
            )
        }

        runTest("CAWG Combined Signer (StrongBox Identity)") {
            val hasStrongBox = StrongBoxSigner.isAvailable(getContext())
            if (!hasStrongBox) {
                return@runTest TestResult(
                    "CAWG Combined Signer (StrongBox Identity)",
                    true,
                    "StrongBox not available on this device (expected)",
                    "Device does not support StrongBox",
                )
            }

            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")

            val c2paSigner = Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256)

            val identityKeyTag = "cawg_identity_strongbox_${System.currentTimeMillis()}"
            val identitySigner = CertificateManager.createStrongBoxSignerWithCSR(
                algorithm = SigningAlgorithm.ES256,
                strongBoxConfig = StrongBoxSigner.Config(
                    keyTag = identityKeyTag,
                    requireUserAuthentication = false,
                ),
                certificateConfig = CertificateManager.CertificateConfig(
                    commonName = "CAWG StrongBox Identity",
                    organization = "C2PA Test Suite",
                    organizationalUnit = "Testing",
                    country = "US",
                    state = "CA",
                    locality = "San Francisco",
                ),
                signingServerUrl = getServerUrl(),
            )

            val combined = Signer.withCawgIdentity(
                c2pa = c2paSigner,
                identity = identitySigner,
                referencedAssertions = listOf("c2pa.actions"),
            )

            try {
                val detail = signAndVerifyCawgManifest(combined, "cawg_combined_strongbox")
                TestResult(
                    "CAWG Combined Signer (StrongBox Identity)",
                    true,
                    "Combined signing with StrongBox identity succeeded",
                    detail,
                )
            } finally {
                StrongBoxSigner.deleteKey(identityKeyTag)
            }
        }
    }

    suspend fun testCawgRejectsClosedSigner(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWG Combined Signer Rejects Closed Input") {
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")

            val c2paSigner = Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256)
            val identitySigner = Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256)
            identitySigner.close()

            try {
                Signer.withCawgIdentity(
                    c2pa = c2paSigner,
                    identity = identitySigner,
                    referencedAssertions = listOf("c2pa.actions"),
                ).close()
                TestResult(
                    "CAWG Combined Signer Rejects Closed Input",
                    false,
                    "Expected C2PAError but call succeeded",
                    "no exception",
                )
            } catch (e: C2PAError) {
                TestResult(
                    "CAWG Combined Signer Rejects Closed Input",
                    true,
                    "Threw C2PAError as expected",
                    e.message ?: "",
                )
            } finally {
                c2paSigner.close()
            }
        }
    }

    suspend fun testCawgRejectsInvalidInputs(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWG Combined Signer Rejects Invalid Inputs") {
            val testName = "CAWG Combined Signer Rejects Invalid Inputs"
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")

            Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256).use { c2paSigner ->
                Signer.fromKeys(certPem, keyPem, SigningAlgorithm.ES256).use { identitySigner ->
                    val failures = mutableListOf<String>()

                    try {
                        Signer.withCawgIdentity(
                            c2pa = c2paSigner,
                            identity = c2paSigner,
                            referencedAssertions = listOf("c2pa.actions"),
                        ).close()
                        failures.add("same-instance signers were accepted")
                    } catch (e: C2PAError) {
                        // Expected: aliasing the same signer would consume it twice.
                    }

                    try {
                        Signer.withCawgIdentity(
                            c2pa = c2paSigner,
                            identity = identitySigner,
                            referencedAssertions = List(256) { "c2pa.actions" },
                        ).close()
                        failures.add("256-entry referencedAssertions list was accepted")
                    } catch (e: C2PAError) {
                        // Expected: the FFI's per-array limit allows at most 255 entries.
                    }

                    // Rejected calls must not have consumed the inputs. Only probe when
                    // both rejections threw; otherwise the inputs may be consumed and
                    // the probe would pass a freed signer to the FFI.
                    if (failures.isEmpty()) {
                        try {
                            if (c2paSigner.reserveSize() <= 0) {
                                failures.add("c2pa signer unusable after rejected calls")
                            }
                            if (identitySigner.reserveSize() <= 0) {
                                failures.add("identity signer unusable after rejected calls")
                            }
                        } catch (e: C2PAError) {
                            failures.add("input signer consumed by a rejected call: ${e.message}")
                        }
                    }

                    if (failures.isEmpty()) {
                        TestResult(
                            testName,
                            true,
                            "Invalid inputs rejected without consuming signers",
                            "same-instance and oversized list both rejected",
                        )
                    } else {
                        TestResult(testName, false, failures.joinToString("; "), "")
                    }
                }
            }
        }
    }
}
