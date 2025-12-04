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
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.FileStream
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
                val builder = Builder.fromJson(manifestJson)
                try {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)

                    val fileTest = File.createTempFile("c2pa-stream-api-test", ".jpg")
                    val destStream = FileStream(fileTest)
                    try {
                        val certPem = loadResourceAsString("es256_certs")
                        val keyPem = loadResourceAsString("es256_private")

                        val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
                        val signer = Signer.fromInfo(signerInfo)

                        try {
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
                        } finally {
                            signer.close()
                        }
                    } finally {
                        sourceStream.close()
                        destStream.close()
                    }
                } finally {
                    builder.close()
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
                val builder = Builder.fromJson(manifestJson)
                try {
                    builder.setNoEmbed()
                    val archiveStream = ByteArrayStream()
                    try {
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
                    } finally {
                        archiveStream.close()
                    }
                } finally {
                    builder.close()
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

            try {
                val builder = Builder.fromJson(manifestJson)
                try {
                    builder.setRemoteURL("https://example.com/manifest.c2pa")
                    builder.setNoEmbed()
                    val archive = ByteArrayStream()
                    try {
                        builder.toArchive(archive)
                        val archiveData = archive.getData()
                        val archiveStr = String(archiveData)
                        val success =
                            archiveStr.contains("https://example.com/manifest.c2pa")
                        TestResult(
                            "Builder Remote URL",
                            success,
                            if (success) {
                                "Remote URL set successfully"
                            } else {
                                "Remote URL not found in archive"
                            },
                            "Archive contains URL: $success",
                        )
                    } finally {
                        archive.close()
                    }
                } finally {
                    builder.close()
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
                val builder = Builder.fromJson(manifestJson)
                try {
                    val thumbnailData = createSimpleJPEGThumbnail()
                    val thumbnailStream = ByteArrayStream(thumbnailData)
                    try {
                        builder.addResource("thumbnail", thumbnailStream)
                        builder.setNoEmbed()
                        val archive = ByteArrayStream()
                        try {
                            builder.toArchive(archive)
                            val archiveStr = String(archive.getData())
                            val success = archiveStr.contains("thumbnail")
                            TestResult(
                                "Builder Add Resource",
                                success,
                                if (success) {
                                    "Resource added successfully"
                                } else {
                                    "Resource not found in archive"
                                },
                                "Thumbnail size: ${thumbnailData.size} bytes, Found in archive: $success",
                            )
                        } finally {
                            archive.close()
                        }
                    } finally {
                        thumbnailStream.close()
                    }
                } finally {
                    builder.close()
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
                val builder = Builder.fromJson(manifestJson)
                try {
                    val ingredientJson =
                        """{"title": "Test Ingredient", "format": "image/jpeg"}"""
                    val ingredientImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val ingredientStream = ByteArrayStream(ingredientImageData)
                    try {
                        builder.addIngredient(
                            ingredientJson,
                            "image/jpeg",
                            ingredientStream,
                        )
                        builder.setNoEmbed()
                        val archive = ByteArrayStream()
                        try {
                            builder.toArchive(archive)
                            val archiveStr = String(archive.getData())
                            val success =
                                archiveStr.contains("\"title\":\"Test Ingredient\"")
                            TestResult(
                                "Builder Add Ingredient",
                                success,
                                if (success) {
                                    "Ingredient added successfully"
                                } else {
                                    "Ingredient not found in archive"
                                },
                                "Ingredient found: $success",
                            )
                        } finally {
                            archive.close()
                        }
                    } finally {
                        ingredientStream.close()
                    }
                } finally {
                    builder.close()
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
                val originalBuilder = Builder.fromJson(manifestJson)
                try {
                    val thumbnailData = createSimpleJPEGThumbnail()
                    val thumbnailStream = ByteArrayStream(thumbnailData)
                    originalBuilder.addResource("test_thumbnail", thumbnailStream)
                    thumbnailStream.close()

                    originalBuilder.setNoEmbed()
                    val archiveStream = ByteArrayStream()
                    try {
                        originalBuilder.toArchive(archiveStream)
                        val archiveData = archiveStream.getData()

                        val newArchiveStream = ByteArrayStream(archiveData)

                        var builderCreated = false
                        try {
                            val newBuilder = Builder.fromArchive(newArchiveStream)
                            builderCreated = true
                            newBuilder.close()
                        } catch (e: Exception) {
                            builderCreated = false
                        }

                        newArchiveStream.close()

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
                    } finally {
                        archiveStream.close()
                    }
                } finally {
                    originalBuilder.close()
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

                val builder = Builder.fromJson(manifestJson)
                try {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)
                    val fileTest = File.createTempFile("c2pa-manifest-direct-sign", ".jpg")
                    val destStream = FileStream(fileTest)

                    val certPem = loadResourceAsString("es256_certs")
                    val keyPem = loadResourceAsString("es256_private")
                    val signer =
                        Signer.fromInfo(
                            SignerInfo(SigningAlgorithm.ES256, certPem, keyPem),
                        )

                    val signResult =
                        builder.sign("image/jpeg", sourceStream, destStream, signer)

                    sourceStream.close()
                    destStream.close()
                    signer.close()

                    val freshImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val freshStream = ByteArrayStream(freshImageData)

                    val success =
                        if (signResult.manifestBytes != null) {
                            try {
                                val reader =
                                    Reader.fromManifestAndStream(
                                        "image/jpeg",
                                        freshStream,
                                        signResult.manifestBytes!!,
                                    )
                                try {
                                    val json = reader.json()
                                    json.contains("\"c2pa.test\"")
                                } finally {
                                    reader.close()
                                }
                            } catch (_: Exception) {
                                false
                            }
                        } else {
                            val manifest = C2PA.readFile(fileTest.absolutePath)
                            manifest.contains("\"c2pa.test\"")
                        }

                    freshStream.close()
                    fileTest.delete()

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
                    builder.close()
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
            val memStream = ByteArrayStream(testImageData)

            try {
                val reader = Reader.fromStream("image/jpeg", memStream)
                try {
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
                } finally {
                    reader.close()
                }
            } catch (e: C2PAError) {
                TestResult(
                    "JSON Round-trip",
                    false,
                    "Failed to read manifest",
                    e.toString(),
                )
            } finally {
                memStream.close()
            }
        }
    }
}
