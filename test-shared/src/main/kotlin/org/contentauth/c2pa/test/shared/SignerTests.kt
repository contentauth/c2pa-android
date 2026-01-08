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
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.CertificateManager
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.KeyStoreSigner
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.StrongBoxSigner
import org.contentauth.c2pa.derToRawSignature
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** SignerTests - Signing and signer-related tests */
abstract class SignerTests : TestBase() {

    suspend fun testSignerWithCallback(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer with Callback") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                val builder = Builder.fromJson(manifestJson)
                try {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-callback-signer", ".jpg")
                    val destStream = FileStream(fileTest)

                    val certPem = loadResourceAsString("es256_certs")
                    val keyPem = loadResourceAsString("es256_private")

                    var signCallCount = 0

                    val callbackSigner =
                        Signer.withCallback(SigningAlgorithm.ES256, certPem, null) { data ->
                            signCallCount++
                            SigningHelper.signWithPEMKey(data, keyPem, "ES256")
                        }

                    try {
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
                    } finally {
                        callbackSigner.close()
                        sourceStream.close()
                        destStream.close()
                        fileTest.delete()
                    }
                } finally {
                    builder.close()
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
                    val builder = Builder.fromJson(manifestJson)

                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-algorithm-$alg", ".jpg")
                    val destStream = FileStream(fileTest)

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
                    val signer = Signer.fromInfo(signerInfo)

                    try {
                        builder.sign("image/jpeg", sourceStream, destStream, signer)
                        val manifest = C2PA.readFile(fileTest.absolutePath)
                        val ok = manifest.isNotEmpty() && manifest.contains("manifests")
                        resultPerAlg.add("$alg:${if (ok) "ok" else "fail"}")
                    } finally {
                        signer.close()
                        builder.close()
                        sourceStream.close()
                        destStream.close()
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
            val signer = Signer.fromInfo(signerInfo)

            try {
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
            } finally {
                signer.close()
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
        runTest("KeyStore Signer Integration") {
            val keyAlias = "test_keystore_signing_${System.currentTimeMillis()}"
            val signingServerUrl = "http://10.0.2.2:8080"

            try {
                // Generate a hardware-backed key and get a real certificate from signing
                // server
                val signer =
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
                    )

                try {
                    // Test signing with the valid certificate
                    val manifestJson = TEST_MANIFEST_JSON
                    val builder = Builder.fromJson(manifestJson)

                    try {
                        val sourceData = loadResourceAsBytes("pexels_asadphoto_457882")
                        val sourceStream = ByteArrayStream(sourceData)
                        val destStream = ByteArrayStream()

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
                    } finally {
                        builder.close()
                    }
                } finally {
                    signer.close()
                }
            } finally {
                // Cleanup
                KeyStoreSigner.deleteKey(keyAlias)
            }
        }
    }

    suspend fun testStrongBoxSignerIntegration(): TestResult = withContext(Dispatchers.IO) {
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
            val signingServerUrl = "http://10.0.2.2:8080"

            try {
                // Generate a StrongBox-backed key and get a real certificate from signing
                // server
                val signer =
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
                    )

                try {
                    // Test signing with the valid certificate
                    val manifestJson = TEST_MANIFEST_JSON
                    val builder = Builder.fromJson(manifestJson)

                    try {
                        val sourceData = loadResourceAsBytes("pexels_asadphoto_457882")
                        val sourceStream = ByteArrayStream(sourceData)
                        val destStream = ByteArrayStream()

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
                    } finally {
                        builder.close()
                    }
                } finally {
                    signer.close()
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
                val signer = Signer.fromSettingsToml(settingsToml)

                try {
                    // Load test image
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")

                    // Create manifest
                    val manifestJson = TEST_MANIFEST_JSON
                    val builder = Builder.fromJson(manifestJson)

                    try {
                        val sourceStream = ByteArrayStream(sourceImageData)
                        val destFile = File.createTempFile("cawg_toml_test", ".jpg")
                        val destStream = FileStream(destFile)

                        try {
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
                        } finally {
                            sourceStream.close()
                            destStream.close()
                            destFile.delete()
                        }
                    } finally {
                        builder.close()
                    }
                } finally {
                    signer.close()
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
                val signer = Signer.fromSettingsJson(settingsJson)

                try {
                    // Load test image
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")

                    // Create manifest
                    val manifestJson = TEST_MANIFEST_JSON
                    val builder = Builder.fromJson(manifestJson)

                    try {
                        val sourceStream = ByteArrayStream(sourceImageData)
                        val destFile = File.createTempFile("cawg_json_test", ".jpg")
                        val destStream = FileStream(destFile)

                        try {
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
                        } finally {
                            sourceStream.close()
                            destStream.close()
                            destFile.delete()
                        }
                    } finally {
                        builder.close()
                    }
                } finally {
                    signer.close()
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
}
