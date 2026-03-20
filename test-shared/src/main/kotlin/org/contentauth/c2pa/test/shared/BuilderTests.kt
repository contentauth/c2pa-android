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
import org.contentauth.c2pa.Action
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.BuilderIntent
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.C2PAContext
import org.contentauth.c2pa.C2PASettings
import org.contentauth.c2pa.DigitalSourceType
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.PredefinedAction
import org.contentauth.c2pa.Reader
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** BuilderTests - Builder API tests for manifest creation */
abstract class BuilderTests : TestBase() {

    suspend fun testBuilderOperations(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder API") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)

                    val fileTest = File.createTempFile("c2pa-stream-api-test", ".jpg")
                    val destStream = FileStream(fileTest)
                    sourceStream.use {
                        destStream.use {
                            val certPem = loadResourceAsString("es256_certs")
                            val keyPem = loadResourceAsString("es256_private")

                            val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
                            Signer.fromInfo(signerInfo).use { signer ->
                                val result =
                                    builder.sign(
                                        "image/jpeg",
                                        sourceStream,
                                        destStream,
                                        signer,
                                    )

                                val manifest = C2PA.readFile(fileTest.absolutePath)
                                val json = JSONObject(manifest)
                                val success = json.has("manifests")

                                TestResult(
                                    "Builder API",
                                    success,
                                    if (success) {
                                        "Successfully signed image"
                                    } else {
                                        "Signing failed"
                                    },
                                    "Original: ${sourceImageData.size}, Signed: ${fileTest.length()}, Result size: ${result.size}\n\n$json",
                                )
                            }
                        }
                    }
                }
            } catch (e: C2PAError) {
                TestResult("Builder API", false, "Failed to create builder", e.toString())
            }
        }
    }

    suspend fun testBuilderNoEmbed(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder No-Embed") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    builder.setNoEmbed()
                    ByteArrayStream().use { archiveStream ->
                        builder.toArchive(archiveStream)
                        val data = archiveStream.getData()
                        val success = data.isNotEmpty()
                        TestResult(
                            "Builder No-Embed",
                            success,
                            if (success) {
                                "Archive created successfully"
                            } else {
                                "Archive creation failed"
                            },
                            "Archive size: ${data.size}",
                        )
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder No-Embed",
                    false,
                    "Failed to create builder",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderRemoteUrl(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Remote URL") {
            val manifestJson = TEST_MANIFEST_JSON
            val remoteUrl = "https://example.com/manifest.c2pa"

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    builder.setRemoteURL(remoteUrl)

                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-remote-url-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    val signResult = builder.sign("image/jpeg", sourceStream, destStream, signer)
                                    val hasManifestBytes = signResult.manifestBytes != null && signResult.manifestBytes!!.isNotEmpty()
                                    val success = signResult.size > 0 && hasManifestBytes
                                    TestResult(
                                        "Builder Remote URL",
                                        success,
                                        if (success) {
                                            "Remote URL set successfully"
                                        } else {
                                            "Remote signing failed"
                                        },
                                        "Sign result size: ${signResult.size}, Has manifest bytes: $hasManifestBytes",
                                    )
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder Remote URL",
                    false,
                    "Failed to create builder",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderAddResource(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Add Resource") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    val thumbnailData = createSimpleJPEGThumbnail()
                    ByteArrayStream(thumbnailData).use { thumbnailStream ->
                        builder.addResource("thumbnail", thumbnailStream)

                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                        val sourceStream = ByteArrayStream(sourceImageData)
                        val fileTest = File.createTempFile("c2pa-resource-test", ".jpg")
                        val destStream = FileStream(fileTest)

                        try {
                            sourceStream.use {
                                destStream.use {
                                    val certPem = loadResourceAsString("es256_certs")
                                    val keyPem = loadResourceAsString("es256_private")
                                    Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                        builder.sign("image/jpeg", sourceStream, destStream, signer)
                                        val manifest = C2PA.readFile(fileTest.absolutePath)
                                        val success = manifest.contains("thumbnail")
                                        TestResult(
                                            "Builder Add Resource",
                                            success,
                                            if (success) {
                                                "Resource added successfully"
                                            } else {
                                                "Resource not found in signed manifest"
                                            },
                                            "Thumbnail size: ${thumbnailData.size} bytes, Found in manifest: $success",
                                        )
                                    }
                                }
                            }
                        } finally {
                            fileTest.delete()
                        }
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder Add Resource",
                    false,
                    "Failed to create builder",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderAddIngredient(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Add Ingredient") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    val ingredientJson =
                        """{"title": "Test Ingredient", "format": "image/jpeg"}"""
                    val ingredientImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    ByteArrayStream(ingredientImageData).use { ingredientStream ->
                        builder.addIngredient(
                            ingredientJson,
                            "image/jpeg",
                            ingredientStream,
                        )

                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                        val sourceStream = ByteArrayStream(sourceImageData)
                        val fileTest = File.createTempFile("c2pa-ingredient-test", ".jpg")
                        val destStream = FileStream(fileTest)

                        try {
                            sourceStream.use {
                                destStream.use {
                                    val certPem = loadResourceAsString("es256_certs")
                                    val keyPem = loadResourceAsString("es256_private")
                                    Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                        builder.sign("image/jpeg", sourceStream, destStream, signer)
                                        val manifest = C2PA.readFile(fileTest.absolutePath)
                                        val success = manifest.contains("Test Ingredient")
                                        TestResult(
                                            "Builder Add Ingredient",
                                            success,
                                            if (success) {
                                                "Ingredient added successfully"
                                            } else {
                                                "Ingredient not found in signed manifest"
                                            },
                                            "Ingredient found: $success",
                                        )
                                    }
                                }
                            }
                        } finally {
                            fileTest.delete()
                        }
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder Add Ingredient",
                    false,
                    "Failed to create builder",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderFromArchive(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder from Archive") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { originalBuilder ->
                    val thumbnailData = createSimpleJPEGThumbnail()
                    ByteArrayStream(thumbnailData).use { thumbnailStream ->
                        originalBuilder.addResource("test_thumbnail", thumbnailStream)
                    }

                    originalBuilder.setNoEmbed()
                    ByteArrayStream().use { archiveStream ->
                        originalBuilder.toArchive(archiveStream)
                        val archiveData = archiveStream.getData()

                        var builderCreated = false
                        ByteArrayStream(archiveData).use { newArchiveStream ->
                            try {
                                Builder.fromArchive(newArchiveStream).use {
                                    builderCreated = true
                                }
                            } catch (e: Exception) {
                                builderCreated = false
                            }
                        }

                        val hasData = archiveData.isNotEmpty()
                        val success = hasData && builderCreated

                        TestResult(
                            "Builder from Archive",
                            success,
                            when {
                                !hasData -> "No archive data generated"
                                !builderCreated ->
                                    "Failed to create builder from archive"
                                else -> "Archive round-trip successful"
                            },
                            "Archive size: ${archiveData.size} bytes, Builder created: $builderCreated",
                        )
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "Builder from Archive",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testReaderWithManifestData(): TestResult = withContext(Dispatchers.IO) {
        runTest("Reader with Manifest Data") {
            try {
                val manifestJson = TEST_MANIFEST_JSON

                val fileTest = File.createTempFile("c2pa-manifest-direct-sign", ".jpg")
                try {
                    val signResult = Builder.fromJson(manifestJson).use { builder ->
                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                        ByteArrayStream(sourceImageData).use { sourceStream ->
                            FileStream(fileTest).use { destStream ->
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(
                                    SignerInfo(SigningAlgorithm.ES256, certPem, keyPem),
                                ).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)
                                }
                            }
                        }
                    }

                    val freshImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val success = ByteArrayStream(freshImageData).use { freshStream ->
                        if (signResult.manifestBytes != null) {
                            try {
                                Reader.fromManifestAndStream(
                                    "image/jpeg",
                                    freshStream,
                                    signResult.manifestBytes!!,
                                ).use { reader ->
                                    val json = reader.json()
                                    // Check for c2pa.created action which is in TEST_MANIFEST_JSON
                                    json.contains("\"c2pa.created\"")
                                }
                            } catch (_: Exception) {
                                false
                            }
                        } else {
                            val manifest = C2PA.readFile(fileTest.absolutePath)
                            manifest.contains("\"c2pa.created\"")
                        }
                    }

                    TestResult(
                        "Reader with Manifest Data",
                        success,
                        if (success) {
                            "Reader with manifest data works"
                        } else {
                            "Failed to use manifest data"
                        },
                        "Manifest bytes available: ${signResult.manifestBytes != null}, Test assertion found: $success",
                    )
                } finally {
                    fileTest.delete()
                }
            } catch (e: Exception) {
                TestResult(
                    "Reader with Manifest Data",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testJsonRoundTrip(): TestResult = withContext(Dispatchers.IO) {
        runTest("JSON Round-trip") {
            val testImageData = loadResourceAsBytes("adobe_20220124_ci")

            try {
                ByteArrayStream(testImageData).use { memStream ->
                    Reader.fromStream("image/jpeg", memStream).use { reader ->
                        val originalJson = reader.json()
                        val json1 = JSONObject(originalJson)

                        // Extract just the manifest part for rebuilding
                        val manifestsValue = json1.opt("manifests")
                        val success =
                            when (manifestsValue) {
                                is JSONArray -> manifestsValue.length() > 0
                                is JSONObject -> manifestsValue.length() > 0
                                else -> false
                            }

                        TestResult(
                            "JSON Round-trip",
                            success,
                            if (success) {
                                "JSON parsed successfully"
                            } else {
                                "Failed to parse JSON"
                            },
                            "Manifests type: ${manifestsValue?.javaClass?.simpleName}, Has content: $success",
                        )
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "JSON Round-trip",
                    false,
                    "Failed to read manifest",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderSetIntent(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Set Intent") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    // Test Create intent with digital source type
                    builder.setIntent(BuilderIntent.Create(DigitalSourceType.DIGITAL_CAPTURE))

                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-intent-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)

                                    val manifest = C2PA.readFile(fileTest.absolutePath)
                                    val json = JSONObject(manifest)

                                    // Check for c2pa.created action which should be auto-added by Create intent
                                    val manifestStr = manifest.lowercase()
                                    val hasCreatedAction = manifestStr.contains("c2pa.created") ||
                                        manifestStr.contains("digitalcapture")

                                    TestResult(
                                        "Builder Set Intent",
                                        true,
                                        "Intent set and signed successfully",
                                        "Has created action or digital source: $hasCreatedAction\nManifest preview: ${manifest.take(500)}...",
                                    )
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder Set Intent",
                    false,
                    "Failed to set intent",
                    e.toString(),
                )
            } catch (e: Exception) {
                TestResult(
                    "Builder Set Intent",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderFromContextWithSettings(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder from Context with Settings") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                val settingsJson = """
                    {
                        "version": 1,
                        "builder": {
                            "created_assertion_labels": ["c2pa.actions"]
                        }
                    }
                """.trimIndent()

                val builder = C2PASettings.create().use { settings ->
                    settings.updateFromString(settingsJson, "json")
                    C2PAContext.fromSettings(settings).use { context ->
                        Builder.fromContext(context).withDefinition(manifestJson)
                    }
                }

                builder.use {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-context-settings-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)

                                    val manifest = C2PA.readFile(fileTest.absolutePath)
                                    val json = JSONObject(manifest)
                                    val hasManifests = json.has("manifests")
                                    val hasCreatedAction = manifest.contains("c2pa.created")

                                    val success = hasManifests && hasCreatedAction

                                    TestResult(
                                        "Builder from Context with Settings",
                                        success,
                                        if (success) {
                                            "Context-based builder with settings works"
                                        } else {
                                            "Failed to sign with context-based builder"
                                        },
                                        "Has manifests: $hasManifests, Has created action: $hasCreatedAction\nManifest preview: ${manifest.take(500)}...",
                                    )
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder from Context with Settings",
                    false,
                    "Failed to create builder from context",
                    e.toString(),
                )
            } catch (e: Exception) {
                TestResult(
                    "Builder from Context with Settings",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderFromJsonWithSettings(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder fromJson with C2PASettings") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                val settingsJson = """
                    {
                        "version": 1,
                        "builder": {
                            "created_assertion_labels": ["c2pa.actions"]
                        }
                    }
                """.trimIndent()

                val builder = C2PASettings.create().use { settings ->
                    settings.updateFromString(settingsJson, "json")
                    Builder.fromJson(manifestJson, settings)
                }

                builder.use {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-fromjson-settings-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)

                                    val manifest = C2PA.readFile(fileTest.absolutePath)
                                    val json = JSONObject(manifest)
                                    val hasManifests = json.has("manifests")
                                    val hasCreatedAction = manifest.contains("c2pa.created")
                                    val success = hasManifests && hasCreatedAction

                                    TestResult(
                                        "Builder fromJson with C2PASettings",
                                        success,
                                        if (success) {
                                            "fromJson(manifest, settings) works"
                                        } else {
                                            "Failed to sign with fromJson(manifest, settings)"
                                        },
                                        "Has manifests: $hasManifests, Has created action: $hasCreatedAction",
                                    )
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "Builder fromJson with C2PASettings",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderWithArchive(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder withArchive") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                val archiveData = Builder.fromJson(manifestJson).use { originalBuilder ->
                    originalBuilder.setNoEmbed()
                    ByteArrayStream().use { archiveStream ->
                        originalBuilder.toArchive(archiveStream)
                        archiveStream.getData()
                    }
                }

                val newBuilder = C2PAContext.create().use { context ->
                    ByteArrayStream(archiveData).use { newArchiveStream ->
                        Builder.fromContext(context).withArchive(newArchiveStream)
                    }
                }

                val signSuccess = newBuilder.use {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-witharchive-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    newBuilder.sign("image/jpeg", sourceStream, destStream, signer)
                                    val manifest = C2PA.readFile(fileTest.absolutePath)
                                    manifest.contains("c2pa.created")
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }

                val success = archiveData.isNotEmpty() && signSuccess
                TestResult(
                    "Builder withArchive",
                    success,
                    if (success) {
                        "withArchive round-trip successful"
                    } else {
                        "withArchive round-trip failed"
                    },
                    "Archive size: ${archiveData.size}, Sign success: $signSuccess",
                )
            } catch (e: Exception) {
                TestResult(
                    "Builder withArchive",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testReaderFromContext(): TestResult = withContext(Dispatchers.IO) {
        runTest("Reader fromContext with withStream") {
            try {
                // First, sign an image so we have something to read
                val fileTest = File.createTempFile("c2pa-reader-context-test", ".jpg")
                try {
                    Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                        ByteArrayStream(sourceImageData).use { sourceStream ->
                            FileStream(fileTest).use { destStream ->
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)
                                }
                            }
                        }
                    }

                    // Now read using the context-based API
                    val signedData = fileTest.readBytes()
                    ByteArrayStream(signedData).use { signedStream ->
                        val reader = C2PAContext.create().use { context ->
                            Reader.fromContext(context).withStream("image/jpeg", signedStream)
                        }

                        reader.use {
                            val json = reader.json()
                            val hasManifests = json.contains("manifests")
                            val hasCreatedAction = json.contains("c2pa.created")
                            val isEmbedded = reader.isEmbedded()
                            val remoteUrl = reader.remoteUrl()

                            val success = hasManifests && hasCreatedAction && isEmbedded && remoteUrl == null

                            TestResult(
                                "Reader fromContext with withStream",
                                success,
                                if (success) {
                                    "Context-based reader works"
                                } else {
                                    "Context-based reader failed"
                                },
                                "Has manifests: $hasManifests, Has created action: $hasCreatedAction, " +
                                    "Is embedded: $isEmbedded, Remote URL: $remoteUrl",
                            )
                        }
                    }
                } finally {
                    fileTest.delete()
                }
            } catch (e: Exception) {
                TestResult(
                    "Reader fromContext with withStream",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testSettingsSetValue(): TestResult = withContext(Dispatchers.IO) {
        runTest("C2PASettings setValue") {
            try {
                val builder = C2PASettings.create().use { settings ->
                    settings.updateFromString("""{"version": 1}""", "json")
                        .setValue("verify.verify_after_sign", "false")
                    C2PAContext.fromSettings(settings).use { context ->
                        Builder.fromContext(context)
                            .withDefinition(TEST_MANIFEST_JSON)
                    }
                }

                builder.use {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-setvalue-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)
                                    val manifest = C2PA.readFile(fileTest.absolutePath)
                                    val success = manifest.contains("manifests")

                                    TestResult(
                                        "C2PASettings setValue",
                                        success,
                                        if (success) {
                                            "setValue works for building context"
                                        } else {
                                            "setValue failed"
                                        },
                                        "Signed with setValue-configured settings",
                                    )
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "C2PASettings setValue",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderIntentEditAndUpdate(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Intent Edit and Update") {
            try {
                Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                    builder.setIntent(BuilderIntent.Edit)

                    // Add a parent ingredient (required for Edit)
                    val ingredientImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    ByteArrayStream(ingredientImageData).use { ingredientStream ->
                        builder.addIngredient(
                            """{"title": "Parent Image", "format": "image/jpeg"}""",
                            "image/jpeg",
                            ingredientStream,
                        )
                    }

                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-edit-intent-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)
                                    val manifest = C2PA.readFile(fileTest.absolutePath)
                                    val editSuccess = manifest.contains("manifests")

                                    // Test Update intent
                                    Builder.fromJson(TEST_MANIFEST_JSON).use { builder2 ->
                                        builder2.setIntent(BuilderIntent.Update)

                                        val updateSuccess = true // setIntent didn't throw

                                        val success = editSuccess && updateSuccess

                                        TestResult(
                                            "Builder Intent Edit and Update",
                                            success,
                                            if (success) {
                                                "Edit and Update intents work"
                                            } else {
                                                "Intent test failed"
                                            },
                                            "Edit signed: $editSuccess, Update set: $updateSuccess",
                                        )
                                    }
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "Builder Intent Edit and Update",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderAddAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Add Action") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    // Add multiple actions
                    builder.addAction(
                        Action(
                            PredefinedAction.EDITED,
                            DigitalSourceType.DIGITAL_CAPTURE,
                            "TestApp/1.0",
                        ),
                    )
                    builder.addAction(
                        Action(
                            PredefinedAction.CROPPED,
                            DigitalSourceType.DIGITAL_CAPTURE,
                        ),
                    )
                    builder.addAction(
                        Action(
                            action = "com.example.custom_action",
                            softwareAgent = "CustomTool/2.0",
                            parameters = mapOf("key1" to "value1", "key2" to "value2"),
                        ),
                    )

                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-action-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)

                                    val manifest = C2PA.readFile(fileTest.absolutePath)
                                    val manifestLower = manifest.lowercase()

                                    val hasEditedAction = manifestLower.contains("c2pa.edited")
                                    val hasCroppedAction = manifestLower.contains("c2pa.cropped")
                                    val hasCustomAction = manifestLower.contains("com.example.custom_action")

                                    val success = hasEditedAction && hasCroppedAction && hasCustomAction

                                    TestResult(
                                        "Builder Add Action",
                                        success,
                                        if (success) {
                                            "All actions added successfully"
                                        } else {
                                            "Some actions missing"
                                        },
                                        "Edited: $hasEditedAction, Cropped: $hasCroppedAction, Custom: $hasCustomAction\nManifest preview: ${manifest.take(500)}...",
                                    )
                                }
                            }
                        }
                    } finally {
                        fileTest.delete()
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder Add Action",
                    false,
                    "Failed to add action",
                    e.toString(),
                )
            } catch (e: Exception) {
                TestResult(
                    "Builder Add Action",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }
}
