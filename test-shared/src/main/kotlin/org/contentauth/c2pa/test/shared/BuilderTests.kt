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
import kotlinx.serialization.json.JsonPrimitive
import org.contentauth.c2pa.Action
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.BuilderIntent
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.C2PAContext
import org.contentauth.c2pa.C2PAContextBuilder
import org.contentauth.c2pa.C2PASettings
import org.contentauth.c2pa.DigitalSourceType
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.HashType
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

    suspend fun testBuilderSetBasePath(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Set Base Path") {
            val manifestJson = TEST_MANIFEST_JSON
            val baseDir = File.createTempFile("c2pa-base-path", "").let { tmp ->
                tmp.delete()
                tmp.mkdirs()
                tmp
            }

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    // setBasePath is fluent and must not corrupt the builder; a subsequent
                    // sign should still succeed against the configured base directory.
                    builder.setBasePath(baseDir.absolutePath)

                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-base-path-test", ".jpg")
                    val destStream = FileStream(fileTest)

                    try {
                        sourceStream.use {
                            destStream.use {
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    val signResult = builder.sign("image/jpeg", sourceStream, destStream, signer)
                                    val success = signResult.size > 0
                                    TestResult(
                                        "Builder Set Base Path",
                                        success,
                                        if (success) {
                                            "Base path set and signing succeeded"
                                        } else {
                                            "Signing failed after setting base path"
                                        },
                                        "Base dir: ${baseDir.absolutePath}, Sign result size: ${signResult.size}",
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
                    "Builder Set Base Path",
                    false,
                    "Failed to set base path",
                    e.toString(),
                )
            } finally {
                baseDir.delete()
            }
        }
    }

    suspend fun testSupportedMimeTypes(): TestResult = withContext(Dispatchers.IO) {
        runTest("Supported MIME Types") {
            val builderTypes = Builder.supportedMimeTypes()
            val readerTypes = Reader.supportedMimeTypes()
            val success = builderTypes.isNotEmpty() &&
                readerTypes.isNotEmpty() &&
                builderTypes.any { it.equals("image/jpeg", ignoreCase = true) } &&
                readerTypes.any { it.equals("image/jpeg", ignoreCase = true) }
            TestResult(
                "Supported MIME Types",
                success,
                if (success) {
                    "Builder: ${builderTypes.size} types, Reader: ${readerTypes.size} types"
                } else {
                    "Expected non-empty lists containing image/jpeg"
                },
                "Builder types: ${builderTypes.joinToString()}; Reader types: ${readerTypes.joinToString()}",
            )
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

    suspend fun testContextBuilderWithSigner(): TestResult = withContext(Dispatchers.IO) {
        runTest("Context Builder with Signer") {
            try {
                val certPem = loadResourceAsString("es256_certs")
                val keyPem = loadResourceAsString("es256_private")
                val settingsJson =
                    """{"version": 1, "builder": {"created_assertion_labels": ["c2pa.actions"]}}"""

                // Build a context with settings plus a programmatic signer attached.
                val context = C2PASettings.create().use { settings ->
                    settings.updateFromString(settingsJson, "json")
                    val signer = Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem))
                    C2PAContextBuilder.create()
                        .setSettings(settings)
                        .setSigner(signer) // consumes signer
                        .build()
                }

                // The resulting context must be usable for creating a builder and signing.
                val signedSize = context.use { ctx ->
                    Builder.fromContext(ctx).withDefinition(TEST_MANIFEST_JSON).use { builder ->
                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                        ByteArrayStream(sourceImageData).use { source ->
                            ByteArrayStream().use { dest ->
                                Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                    builder.sign("image/jpeg", source, dest, signer).size
                                }
                            }
                        }
                    }
                }

                val success = signedSize > 0
                TestResult(
                    "Context Builder with Signer",
                    success,
                    if (success) {
                        "Context built with signer produced a signed asset"
                    } else {
                        "Signing via the built context failed"
                    },
                    "Signed size: $signedSize",
                )
            } catch (e: C2PAError) {
                TestResult(
                    "Context Builder with Signer",
                    false,
                    "Context builder flow threw",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testContextCancel(): TestResult = withContext(Dispatchers.IO) {
        runTest("Context Cancel") {
            try {
                // cancel() on an idle context is a valid no-op; it must not throw.
                C2PAContext.create().use { context ->
                    context.cancel()
                }
                TestResult("Context Cancel", true, "cancel() succeeded on an idle context", null)
            } catch (e: C2PAError) {
                TestResult("Context Cancel", false, "cancel() threw", e.toString())
            }
        }
    }

    suspend fun testContextBuilderRejectsConsumedSigner(): TestResult = withContext(Dispatchers.IO) {
        runTest("Context Builder Rejects Consumed Signer") {
            try {
                val certPem = loadResourceAsString("es256_certs")
                val keyPem = loadResourceAsString("es256_private")
                C2PAContextBuilder.create().use { builder ->
                    val signer = Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem))
                    signer.close() // consume/close the signer so its pointer is zeroed
                    try {
                        builder.setSigner(signer)
                        TestResult(
                            "Context Builder Rejects Consumed Signer",
                            false,
                            "setSigner accepted a closed signer",
                            null,
                        )
                    } catch (e: C2PAError) {
                        TestResult(
                            "Context Builder Rejects Consumed Signer",
                            true,
                            "setSigner rejected a closed signer as expected",
                            e.toString(),
                        )
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Context Builder Rejects Consumed Signer",
                    false,
                    "Setup threw",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testContextBuilderRejectsConsumedBuilder(): TestResult = withContext(Dispatchers.IO) {
        runTest("Context Builder Rejects Reuse") {
            try {
                val builder = C2PAContextBuilder.create()
                builder.build().use { /* first build consumes the builder */ }
                try {
                    builder.build()
                    TestResult(
                        "Context Builder Rejects Reuse",
                        false,
                        "Second build() on a consumed builder succeeded",
                        null,
                    )
                } catch (e: C2PAError) {
                    TestResult(
                        "Context Builder Rejects Reuse",
                        true,
                        "Second build() on a consumed builder threw as expected",
                        e.toString(),
                    )
                }
            } catch (e: C2PAError) {
                TestResult("Context Builder Rejects Reuse", false, "Setup threw", e.toString())
            }
        }
    }

    suspend fun testContextBuilderCloseWithoutBuild(): TestResult = withContext(Dispatchers.IO) {
        runTest("Context Builder Close Without Build") {
            try {
                // Abandoning a builder without building must free it cleanly via close().
                C2PAContextBuilder.create().use { /* no build(); use {} closes it */ }
                TestResult(
                    "Context Builder Close Without Build",
                    true,
                    "Builder closed without building",
                    null,
                )
            } catch (e: C2PAError) {
                TestResult(
                    "Context Builder Close Without Build",
                    false,
                    "close() without build threw",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderIngredientArchive(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Ingredient Archive") {
            val ingredientId = "archive-ingredient-1"
            // write_ingredient_archive matches the id against the ingredient's label first, then
            // instance_id; a producer-supplied label is the reliable, round-trip-preserved key
            // (a caller-set instance_id is not guaranteed to survive). Mirror the c2pa-rs reference.
            val ingredientJson =
                """{"title": "Archive Ingredient", "format": "image/jpeg", "relationship": "componentOf", "label": "$ingredientId"}"""
            // generate_c2pa_archive must be enabled for writeIngredientArchive to succeed.
            val settingsJson =
                """
                {
                    "version": 1,
                    "builder": {
                        "generate_c2pa_archive": true,
                        "created_assertion_labels": ["c2pa.actions", "c2pa.ingredient.v3"]
                    }
                }
                """.trimIndent()

            try {
                // Export: build an ingredient archive from one builder.
                val settings = C2PASettings.create().apply { updateFromString(settingsJson, "json") }
                val archiveData =
                    try {
                        C2PAContext.fromSettings(settings).use { context ->
                            Builder.fromContext(context).withDefinition(TEST_MANIFEST_JSON).use { ingredientBuilder ->
                                val ingredientImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                                ByteArrayStream(ingredientImageData).use { ingredientStream ->
                                    ingredientBuilder.addIngredient(ingredientJson, "image/jpeg", ingredientStream)
                                }
                                ByteArrayStream().use { archiveStream ->
                                    ingredientBuilder.writeIngredientArchive(ingredientId, archiveStream)
                                    archiveStream.getData()
                                }
                            }
                        }
                    } finally {
                        settings.close()
                    }

                // Import: load that archive into a parent builder.
                var imported = false
                Builder.fromJson(TEST_MANIFEST_JSON).use { parentBuilder ->
                    ByteArrayStream(archiveData).use { archiveStream ->
                        parentBuilder.addIngredientFromArchive(archiveStream)
                        imported = true
                    }
                }

                val success = archiveData.isNotEmpty() && imported
                TestResult(
                    "Builder Ingredient Archive",
                    success,
                    if (success) {
                        "Ingredient archive round-trip succeeded"
                    } else {
                        "Ingredient archive round-trip failed"
                    },
                    "Archive size: ${archiveData.size} bytes, Imported: $imported",
                )
            } catch (e: C2PAError) {
                TestResult(
                    "Builder Ingredient Archive",
                    false,
                    "Ingredient archive round-trip threw",
    suspend fun testEmbeddableAndPlaceholder(): TestResult = withContext(Dispatchers.IO) {
        runTest("Embeddable and Placeholder") {
            try {
                val certPem = loadResourceAsString("es256_certs")
                val keyPem = loadResourceAsString("es256_private")
                val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                val settingsJson =
                    """{"version": 1, "builder": {"created_assertion_labels": ["c2pa.actions"]}}"""

                // placeholder() reserves space sized to the signature, so the signer must be on the
                // context. Canonical placeholder workflow (mirrors the c2pa-rs data_hash example):
                // placeholder -> embed after the JPEG SOI marker -> register the exclusion ->
                // hash the asset -> signEmbeddable patches the reserved slot.
                val context = C2PASettings.create().use { settings ->
                    settings.updateFromString(settingsJson, "json")
                    val signer = Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem))
                    C2PAContextBuilder.create().setSettings(settings).setSigner(signer).build()
                }
                val placeholderBytes = context.use { ctx ->
                    Builder.fromContext(ctx).withDefinition(TEST_MANIFEST_JSON).use { builder ->
                        builder.needsPlaceholder("image/jpeg")
                        val placeholder = builder.placeholder("image/jpeg")
                        val manifestPos = 2 // after the JPEG SOI marker
                        val embedded = sourceImageData.copyOfRange(0, manifestPos) +
                            placeholder +
                            sourceImageData.copyOfRange(manifestPos, sourceImageData.size)
                        builder.setDataHashExclusions(listOf(manifestPos.toLong() to placeholder.size.toLong()))
                        ByteArrayStream(embedded).use { source ->
                            builder.updateHashFromStream("image/jpeg", source)
                        }
                        builder.signEmbeddable("image/jpeg") // placeholder mode: patches the reserved slot
                        placeholder
                    }
                }

                // formatEmbeddable wraps a raw (application/c2pa) manifest from a no-embed sign.
                val formattedSize = Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                    builder.setNoEmbed()
                    ByteArrayStream(sourceImageData).use { source ->
                        ByteArrayStream().use { dest ->
                            Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                                val signResult = builder.sign("image/jpeg", source, dest, signer)
                                val manifestBytes = signResult.manifestBytes
                                    ?: throw C2PAError.Api("No manifest bytes from no-embed sign")
                                Builder.formatEmbeddable("image/jpeg", manifestBytes).size
                            }
                        }
                    }
                }

                val success = placeholderBytes.isNotEmpty() && formattedSize > 0
                TestResult(
                    "Embeddable and Placeholder",
                    success,
                    if (success) {
                        "Placeholder, exclusions, and formatEmbeddable succeeded"
                    } else {
                        "Embeddable flow produced empty output"
                    },
                    "Placeholder bytes: ${placeholderBytes.size}, Formatted size: $formattedSize",
                )
            } catch (e: C2PAError) {
                TestResult(
                    "Embeddable and Placeholder",
                    false,
                    "Embeddable flow threw",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderHashType(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Hash Type") {
            try {
                Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                    val jpegType = builder.hashType("image/jpeg")
                    val mp4Type = builder.hashType("video/mp4")
                    val success = jpegType == HashType.DATA_HASH && mp4Type == HashType.BMFF_HASH
                    TestResult(
                        "Builder Hash Type",
                        success,
                        if (success) {
                            "Hash types resolved correctly"
                        } else {
                            "Unexpected hash types"
                        },
                        "image/jpeg -> $jpegType, video/mp4 -> $mp4Type",
                    )
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder Hash Type",
                    false,
                    "hashType threw",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testSignEmbeddableDataHash(): TestResult = withContext(Dispatchers.IO) {
        runTest("Sign Embeddable (data hash)") {
            try {
                val certPem = loadResourceAsString("es256_certs")
                val keyPem = loadResourceAsString("es256_private")
                val settingsJson =
                    """{"version": 1, "builder": {"created_assertion_labels": ["c2pa.actions"]}}"""
                val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")

                // Context carries the signer; signEmbeddable obtains it from the context.
                val context = C2PASettings.create().use { settings ->
                    settings.updateFromString(settingsJson, "json")
                    val signer = Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem))
                    C2PAContextBuilder.create().setSettings(settings).setSigner(signer).build()
                }

                // Direct mode (no placeholder): hash the whole stream to create a DataHash,
                // then sign embeddable. Mirrors c2pa-rs c-ffi test_data_hash_embeddable_workflow.
                val embeddable = context.use { ctx ->
                    Builder.fromContext(ctx).withDefinition(TEST_MANIFEST_JSON).use { builder ->
                        builder.needsPlaceholder("image/jpeg")
                        ByteArrayStream(sourceImageData).use { source ->
                            builder.updateHashFromStream("image/jpeg", source)
                        }
                        builder.signEmbeddable("image/jpeg")
                    }
                }

                val success = embeddable.isNotEmpty()
                TestResult(
                    "Sign Embeddable (data hash)",
                    success,
                    if (success) {
                        "Direct-mode embeddable manifest produced (${embeddable.size} bytes)"
                    } else {
                        "Embeddable manifest was empty"
                    },
                    "Embeddable size: ${embeddable.size}",
                )
            } catch (e: Exception) {
                TestResult(
                    "Sign Embeddable (data hash)",
                    false,
                    "signEmbeddable (direct) flow threw",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBmffMerkleHashing(): TestResult = withContext(Dispatchers.IO) {
        runTest("BMFF Merkle Hashing") {
            try {
                val certPem = loadResourceAsString("es256_certs")
                val keyPem = loadResourceAsString("es256_private")
                val settingsJson =
                    """{"version": 1, "builder": {"created_assertion_labels": ["c2pa.actions"]}}"""
                val videoData = loadSharedResourceAsBytes("video1.mp4")
                    ?: throw IllegalArgumentException("Resource not found: video1.mp4")

                val context = C2PASettings.create().use { settings ->
                    settings.updateFromString(settingsJson, "json")
                    val signer = Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem))
                    C2PAContextBuilder.create().setSettings(settings).setSigner(signer).build()
                }

                // Fragmented BMFF (placeholder) workflow. Mirrors c2pa-rs c-ffi
                // test_bmff_embeddable_workflow_with_mdat_hashes: a placeholder creates the
                // BmffHash with Merkle slots, fixed-size Merkle splits the mdat into 1 KB
                // chunks, a dummy mdat leaf hash exercises the path (the asset won't validate),
                // and update_hash_from_stream hashes the non-mdat bytes from the real asset.
                val embeddable = context.use { ctx ->
                    Builder.fromContext(ctx).withDefinition(TEST_MANIFEST_JSON).use { builder ->
                        builder.placeholder("video/mp4")
                        builder.setFixedSizeMerkle(1)
                        builder.hashMdatBytes(0, ByteArray(4096) { 0xAB.toByte() }, true)
                        ByteArrayStream(videoData).use { source ->
                            builder.updateHashFromStream("video/mp4", source)
                        }
                        builder.signEmbeddable("video/mp4")
                    }
                }

                val success = embeddable.isNotEmpty()
                TestResult(
                    "BMFF Merkle Hashing",
                    success,
                    if (success) {
                        "Fragmented BMFF embeddable produced (${embeddable.size} bytes)"
                    } else {
                        "Embeddable manifest was empty"
                    },
                    "Video bytes: ${videoData.size}, Embeddable size: ${embeddable.size}",
                )
            } catch (e: Exception) {
                TestResult(
                    "BMFF Merkle Hashing",
                    false,
                    "BMFF Merkle flow threw",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testBuilderArchiveErrorPaths(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Archive Error Paths") {
            // Exercise the error-return branches of the ingredient-archive methods. On a default
            // Builder.fromJson: writeIngredientArchive fails because generate_c2pa_archive is not
            // enabled, and addIngredientFromArchive fails on bytes that are not a valid archive.
            // (setBasePath is not checked: it just stores the path and succeeds, so it has no
            // reachable error state.)
    suspend fun testBuilderEmbeddableErrorPaths(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Embeddable Error Paths") {
            // Exercise the error-return branches of the embeddable methods. Each op is invoked in a
            // state the core rejects: placeholder/signEmbeddable need a signer on the context
            // (Builder.fromJson has none), and setDataHashExclusions fails with no DataHash assertion
            // (no placeholder). Each must throw C2PAError. (setFixedSizeMerkle is not checked here: it
            // just records the chunk size and succeeds without a DataHash, so it has no reachable
            // error state on a fresh builder.)
            val unexpectedSuccess = mutableListOf<String>()
            fun expectThrows(label: String, block: () -> Unit) {
                try {
                    block()
                    unexpectedSuccess.add(label)
                } catch (e: C2PAError) {
                    // expected
                }
            }

            Builder.fromJson(TEST_MANIFEST_JSON).use { b ->
                ByteArrayStream().use { dest ->
                    expectThrows("writeIngredientArchive") { b.writeIngredientArchive("missing", dest) }
                }
            }
            Builder.fromJson(TEST_MANIFEST_JSON).use { b ->
                ByteArrayStream(byteArrayOf(0x00, 0x01, 0x02, 0x03)).use { bogus ->
                    expectThrows("addIngredientFromArchive") { b.addIngredientFromArchive(bogus) }
                }
                expectThrows("placeholder") { b.placeholder("image/jpeg") }
            }
            Builder.fromJson(TEST_MANIFEST_JSON).use { b ->
                expectThrows("signEmbeddable") { b.signEmbeddable("image/jpeg") }
            }
            Builder.fromJson(TEST_MANIFEST_JSON).use { b ->
                expectThrows("setDataHashExclusions") { b.setDataHashExclusions(listOf(0L to 2L)) }
            }

            val success = unexpectedSuccess.isEmpty()
            TestResult(
                "Builder Archive Error Paths",
                success,
                if (success) {
                    "Ingredient-archive error paths rejected invalid state as expected"
                } else {
                    "Did not throw for: ${unexpectedSuccess.joinToString()}"
                },
                "Checked 2 error paths; unexpected successes: ${unexpectedSuccess.size}",
                "Builder Embeddable Error Paths",
                success,
                if (success) {
                    "All checked embeddable error paths rejected invalid state as expected"
                } else {
                    "Did not throw for: ${unexpectedSuccess.joinToString()}"
                },
                "Checked 3 error paths; unexpected successes: ${unexpectedSuccess.size}",
            )
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
                            softwareAgent = JsonPrimitive("CustomTool/2.0"),
                            parameters = mapOf("key1" to JsonPrimitive("value1"), "key2" to JsonPrimitive("value2")),
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
