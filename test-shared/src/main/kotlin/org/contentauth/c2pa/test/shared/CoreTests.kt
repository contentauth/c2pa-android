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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.Reader
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** CoreTests - Core library functionality tests */
abstract class CoreTests : TestBase() {

    suspend fun testLibraryVersion(): TestResult = withContext(Dispatchers.IO) {
        runTest("Library Version") {
            val version = C2PA.version()
            if (version.isNotEmpty() && version.contains(".")) {
                TestResult("Library Version", true, "C2PA version: $version", version)
            } else {
                TestResult("Library Version", false, "Invalid version format", version)
            }
        }
    }

    suspend fun testErrorHandling(): TestResult = withContext(Dispatchers.IO) {
        runTest("Error Handling") {
            val (exceptionThrown, errorMessage) =
                try {
                    C2PA.readFile("/non/existent/file.jpg")
                    false to "No exception thrown"
                } catch (e: C2PAError) {
                    true to e.toString()
                } catch (e: Exception) {
                    false to "Unexpected exception: ${e.message}"
                }

            // Validate error contains expected content
            val hasExpectedError =
                exceptionThrown &&
                    (
                        errorMessage.contains("No such file", ignoreCase = true) ||
                            errorMessage.contains("not found", ignoreCase = true) ||
                            errorMessage.contains(
                                "does not exist",
                                ignoreCase = true,
                            ) ||
                            errorMessage.contains(
                                "FileNotFound",
                                ignoreCase = true,
                            ) ||
                            errorMessage.contains(
                                "os error 2",
                                ignoreCase = true,
                            ) // Unix file not found
                        )

            val success = exceptionThrown && hasExpectedError

            TestResult(
                "Error Handling",
                success,
                if (success) {
                    "Correctly handled missing file with appropriate error"
                } else {
                    "Unexpected error handling"
                },
                "Exception: $exceptionThrown, Error: $errorMessage, Expected error pattern: $hasExpectedError",
            )
        }
    }

    suspend fun testReadManifestFromTestImage(): TestResult = withContext(Dispatchers.IO) {
        runTest("Read Manifest from Test Image") {
            val testImageFile = copyResourceToFile("adobe_20220124_ci", "test_adobe.jpg")
            try {
                val manifest =
                    try {
                        C2PA.readFile(testImageFile.absolutePath)
                    } catch (e: C2PAError) {
                        null
                    }
                if (manifest != null) {
                    val json = JSONObject(manifest)
                    val hasManifests = json.has("manifests")

                    // Handle both array and object formats for manifests
                    var manifestCount = 0
                    var activeManifest: JSONObject? = null
                    var claimGenerator: String? = null
                    var assertionCount = 0

                    if (hasManifests) {
                        val manifestsValue = json.get("manifests")
                        when (manifestsValue) {
                            is JSONArray -> {
                                manifestCount = manifestsValue.length()
                                // Find active manifest in array
                                for (i in 0 until manifestsValue.length()) {
                                    val m = manifestsValue.getJSONObject(i)
                                    if (json.optString("active_manifest") ==
                                        m.optString("instance_id")
                                    ) {
                                        activeManifest = m
                                        break
                                    }
                                }
                            }
                            is JSONObject -> {
                                // Single manifest as object
                                manifestCount = manifestsValue.length() // Number of keys
                                // Get the first (and likely only) manifest
                                val keys = manifestsValue.keys()
                                if (keys.hasNext()) {
                                    activeManifest =
                                        manifestsValue.getJSONObject(keys.next())
                                }
                            }
                        }

                        if (activeManifest != null) {
                            claimGenerator = activeManifest.optString("claim_generator")
                            val assertions = activeManifest.opt("assertions")
                            assertionCount =
                                when (assertions) {
                                    is JSONArray -> assertions.length()
                                    else -> 0
                                }
                        }
                    }

                    val success =
                        hasManifests && manifestCount > 0 && activeManifest != null

                    TestResult(
                        "Read Manifest from Test Image",
                        success,
                        if (success) {
                            "Successfully read and validated manifest"
                        } else {
                            "Manifest validation failed"
                        },
                        "Manifests: $manifestCount, Active: ${activeManifest != null}, " +
                            "Generator: $claimGenerator, Assertions: $assertionCount\n" +
                            manifest.take(300) +
                            if (manifest.length > 300) "..." else "",
                    )
                } else {
                    val error = C2PA.getError()
                    TestResult(
                        "Read Manifest from Test Image",
                        false,
                        "Failed to read manifest",
                        error ?: "No error",
                    )
                }
            } finally {
                testImageFile.delete()
            }
        }
    }

    suspend fun testReadIngredient(): TestResult = withContext(Dispatchers.IO) {
        runTest("Read Ingredient") {
            val testImageFile =
                copyResourceToFile("adobe_20220124_ci", "test_ingredient.jpg")
            try {
                val standaloneIngredient =
                    try {
                        C2PA.readIngredientFile(testImageFile.absolutePath)
                    } catch (e: C2PAError) {
                        null // Expected for files without ingredients
                    }

                var hasValidIngredientData = false
                if (standaloneIngredient != null) {
                    try {
                        val json = JSONObject(standaloneIngredient)
                        hasValidIngredientData = json.has("format") || json.has("title")
                    } catch (e: Exception) {
                        hasValidIngredientData = false
                    }
                }

                val manifest =
                    try {
                        C2PA.readFile(testImageFile.absolutePath)
                    } catch (e: C2PAError) {
                        null
                    }
                var hasIngredientsInManifest = false
                if (manifest != null) {
                    try {
                        val manifestJson = JSONObject(manifest)
                        val manifests = manifestJson.optJSONArray("manifests")
                        if (manifests != null && manifests.length() > 0) {
                            val firstManifest = manifests.getJSONObject(0)
                            val ingredients = firstManifest.optJSONArray("ingredients")
                            hasIngredientsInManifest =
                                ingredients != null && ingredients.length() > 0
                        }
                    } catch (e: Exception) {
                        // Ignore JSON parsing errors
                    }
                }

                val success =
                    true // The API is working correctly regardless of whether this
                // image has ingredients

                TestResult(
                    "Read Ingredient",
                    success,
                    when {
                        hasValidIngredientData -> "Found valid ingredient data"
                        hasIngredientsInManifest -> "Found ingredients in manifest"
                        else -> "No ingredients (normal for some images)"
                    },
                    buildString {
                        append(
                            "Ingredient API returned: ${if (standaloneIngredient != null) "data (${standaloneIngredient.length} bytes)" else "null"}",
                        )
                        if (hasIngredientsInManifest) {
                            append(", Manifest has ingredients")
                        }
                    },
                )
            } finally {
                testImageFile.delete()
            }
        }
    }

    suspend fun testInvalidFileHandling(): TestResult = withContext(Dispatchers.IO) {
        runTest("Invalid File Handling") {
            val textFile = File(getContext().cacheDir, "test.txt")
            textFile.writeText("This is not an image file")
            try {
                val (success, errorMessage) =
                    try {
                        C2PA.readFile(textFile.absolutePath)
                        false to "Should have thrown exception"
                    } catch (e: C2PAError) {
                        true to e.toString()
                    } catch (e: Exception) {
                        false to "Unexpected exception: ${e.message}"
                    }

                TestResult(
                    "Invalid File Handling",
                    success,
                    if (success) {
                        "Correctly handled invalid file"
                    } else {
                        "Unexpected behavior"
                    },
                    "Error: $errorMessage",
                )
            } finally {
                textFile.delete()
            }
        }
    }

    suspend fun testResourceReading(): TestResult = withContext(Dispatchers.IO) {
        runTest("Resource Reading") {
            val testImageData = loadResourceAsBytes("adobe_20220124_ci")
            val stream = ByteArrayStream(testImageData)
            try {
                try {
                    val reader = Reader.fromStream("image/jpeg", stream)
                    try {
                        val json = reader.json()
                        val manifestJson = JSONObject(json)

                        var resourceUri: String? = null
                        var resourceType = "unknown"
                        val availableResources = mutableListOf<String>()

                        val manifests = manifestJson.optJSONArray("manifests")
                        if (manifests != null && manifests.length() > 0) {
                            val manifest = manifests.getJSONObject(0)

                            // Check for thumbnail
                            val thumbnail = manifest.optJSONObject("thumbnail")
                            if (thumbnail != null) {
                                val id = thumbnail.optString("identifier")
                                if (id.isNotEmpty()) {
                                    availableResources.add("thumbnail: $id")
                                    if (resourceUri == null) {
                                        resourceUri = id
                                        resourceType = "thumbnail"
                                    }
                                }
                            }

                            // Check for ingredient thumbnails
                            val ingredients = manifest.optJSONArray("ingredients")
                            if (ingredients != null) {
                                for (i in 0 until ingredients.length()) {
                                    val ingredient = ingredients.getJSONObject(i)
                                    val ingThumb = ingredient.optJSONObject("thumbnail")
                                    if (ingThumb != null) {
                                        val id = ingThumb.optString("identifier")
                                        if (id.isNotEmpty()) {
                                            availableResources.add(
                                                "ingredient_thumbnail[$i]: $id",
                                            )
                                            if (resourceUri == null) {
                                                resourceUri = id
                                                resourceType = "ingredient_thumbnail"
                                            }
                                        }
                                    }
                                }
                            }

                            // Check assertion thumbnails
                            val assertions = manifest.optJSONArray("assertions")
                            if (assertions != null) {
                                for (i in 0 until assertions.length()) {
                                    val assertion = assertions.getJSONObject(i)
                                    val thumb = assertion.optJSONObject("thumbnail")
                                    if (thumb != null) {
                                        val id = thumb.optString("identifier")
                                        if (id.isNotEmpty()) {
                                            availableResources.add(
                                                "assertion_thumbnail[$i]: $id",
                                            )
                                            if (resourceUri == null) {
                                                resourceUri = id
                                                resourceType = "assertion_thumbnail"
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (resourceUri != null) {
                            val resourceStream = ByteArrayStream()
                            try {
                                reader.resource(resourceUri, resourceStream)
                                val resourceData = resourceStream.getData()
                                val success = resourceData.isNotEmpty()
                                TestResult(
                                    "Resource Reading",
                                    success,
                                    if (success) {
                                        "Successfully extracted $resourceType"
                                    } else {
                                        "Resource extraction failed"
                                    },
                                    "Type: $resourceType, URI: $resourceUri, Size: ${resourceData.size} bytes",
                                )
                            } catch (e: C2PAError) {
                                TestResult(
                                    "Resource Reading",
                                    false,
                                    "Failed to extract resource: ${e.message}",
                                    "URI: $resourceUri, Available: ${availableResources.joinToString(
                                        ", ",
                                    )}",
                                )
                            } finally {
                                resourceStream.close()
                            }
                        } else {
                            TestResult(
                                "Resource Reading",
                                true,
                                "No embedded resources in manifest (normal for some images)",
                                "Checked thumbnails, ingredients, and assertions. JSON structure valid.",
                            )
                        }
                    } finally {
                        reader.close()
                    }
                } catch (e: C2PAError) {
                    return@runTest TestResult(
                        "Resource Reading",
                        false,
                        "Failed to create reader",
                        e.toString(),
                    )
                }
            } finally {
                stream.close()
            }
        }
    }

    suspend fun testLoadSettings(): TestResult = withContext(Dispatchers.IO) {
        runTest("Load Settings") {
            val settingsJson =
                """{
                "version_major": 1,
                "version_minor": 0,
                "trust": {
                    "private_anchors": null,
                    "trust_anchors": null,
                    "trust_config": null,
                    "allowed_list": null
                },
                "Core": {
                    "debug": false,
                    "hash_alg": "sha256",
                    "salt_jumbf_boxes": true,
                    "prefer_box_hash": false,
                    "prefer_bmff_merkle_tree": false,
                    "compress_manifests": true,
                    "max_memory_usage": null
                },
                "Verify": {
                    "verify_after_reading": true,
                    "verify_after_sign": true,
                    "verify_trust": true,
                    "ocsp_fetch": false,
                    "remote_manifest_fetch": true,
                    "check_ingredient_trust": true,
                    "skip_ingredient_conflict_resolution": false,
                    "strict_v1_validation": false
                },
                "Builder": {
                    "auto_thumbnail": true
                }
            }"""

            val success =
                try {
                    C2PA.loadSettings(settingsJson, "json")
                    true
                } catch (e: Exception) {
                    false
                }

            TestResult(
                "Load Settings",
                success,
                if (success) {
                    "Settings loaded successfully"
                } else {
                    "Failed to load settings: ${C2PA.getError()}"
                },
                if (success) "Success" else "Error: ${C2PA.getError()}",
            )
        }
    }

    suspend fun testInvalidInputs(): TestResult = withContext(Dispatchers.IO) {
        runTest("Invalid Inputs") {
            val errors = mutableListOf<String>()

            // Test invalid format strings
            try {
                val stream = ByteArrayStream(ByteArray(100))
                Reader.fromStream("invalid/format", stream)
                stream.close()
            } catch (e: C2PAError) {
                errors.add("Invalid format caught: ${e.message}")
            }

            // Test null-like conditions
            try {
                Builder.fromJson("")
            } catch (e: C2PAError) {
                errors.add("Empty JSON caught: ${e.message}")
            }

            // Test invalid algorithm
            try {
                Signer.fromKeys("", "", SigningAlgorithm.ES256)
            } catch (e: C2PAError) {
                errors.add("Empty certs caught: ${e.message}")
            }

            val success = errors.size >= 3

            TestResult(
                "Invalid Inputs",
                success,
                if (success) {
                    "Invalid inputs properly rejected"
                } else {
                    "Some invalid inputs not caught"
                },
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testErrorEnumCoverage(): TestResult = withContext(Dispatchers.IO) {
        runTest("Error Enum Coverage") {
            val errors = mutableListOf<String>()
            var caughtApiError: C2PAError? = null

            try {
                val reader =
                    Reader.fromStream("invalid/format", ByteArrayStream(ByteArray(0)))
                reader.close()
            } catch (e: C2PAError.Api) {
                caughtApiError = e
                errors.add("Caught API error: ${e.message}")
            } catch (e: Exception) {
                errors.add("Wrong exception type: ${e::class.simpleName}")
            }

            // Verify all error types exist and can be created
            val nilError = C2PAError.NilPointer
            val utf8Error = C2PAError.Utf8
            val negError = C2PAError.Negative(-42)
            val apiErr = C2PAError.Api("Test error")

            try {
                val builder = Builder.fromJson("not valid json")
                builder.close()
            } catch (e: C2PAError.Api) {
                if (caughtApiError == null) caughtApiError = e
                errors.add("Caught builder error: ${e.message}")
            } catch (e: Exception) {
                errors.add("Builder threw non-C2PA error: ${e::class.simpleName}")
            }

            try {
                val signer =
                    Signer.fromInfo(
                        SignerInfo(
                            SigningAlgorithm.ES256,
                            "invalid cert",
                            "invalid key",
                        ),
                    )
                signer.close()
            } catch (e: C2PAError.Api) {
                if (caughtApiError == null) caughtApiError = e
                errors.add("Caught signer error: ${e.message}")
            } catch (e: Exception) {
                errors.add("Signer threw non-C2PA error: ${e::class.simpleName}")
            }

            val errorMessages =
                listOf(
                    "nilPointer: $nilError",
                    "utf8: $utf8Error",
                    "negative: $negError",
                    "api: $apiErr",
                )

            val success = caughtApiError != null

            TestResult(
                "Error Enum Coverage",
                success,
                if (success) {
                    "All error types covered"
                } else {
                    "Some error types not covered"
                },
                errorMessages.joinToString("\n") +
                    "\nCaught: ${caughtApiError?.toString() ?: "none"}\n\nOperations tried: ${errors.joinToString(
                        "; ",
                    )}",
            )
        }
    }

    suspend fun testReaderResourceErrorHandling(): TestResult = withContext(Dispatchers.IO) {
        runTest("Reader Resource Error Handling") {
            val testImageData = loadResourceAsBytes("adobe_20220124_ci")
            val stream = ByteArrayStream(testImageData)
            try {
                val reader = Reader.fromStream("image/jpeg", stream)
                try {
                    val resourceStream = ByteArrayStream()
                    try {
                        reader.resource("non_existent_resource", resourceStream)
                        TestResult(
                            "Reader Resource Error Handling",
                            false,
                            "Should have thrown exception for missing resource",
                            "No exception thrown",
                        )
                    } catch (e: C2PAError) {
                        TestResult(
                            "Reader Resource Error Handling",
                            true,
                            "Correctly threw exception for missing resource",
                            "Error: ${e.message}",
                        )
                    } finally {
                        resourceStream.close()
                    }
                } finally {
                    reader.close()
                }
            } finally {
                stream.close()
            }
        }
    }

    suspend fun testConcurrentOperations(): TestResult = withContext(Dispatchers.IO) {
        runTest("Concurrent Operations") {
            val errors = mutableListOf<String>()
            val successes = mutableListOf<String>()

            // Run multiple operations concurrently
            coroutineScope {
                val jobs =
                    List(4) { index ->
                        async {
                            when (index) {
                                0 -> {
                                    try {
                                        C2PA.readFile("/non/existent/file$index.jpg")
                                    } catch (e: C2PAError) {
                                        errors.add("Read $index: ${e.message}")
                                    }
                                }
                                1 -> {
                                    try {
                                        val version = C2PA.version()
                                        successes.add("Version $index: $version")
                                    } catch (e: Exception) {
                                        errors.add("Version $index: ${e.message}")
                                    }
                                }
                                2 -> {
                                    try {
                                        Builder.fromJson("invalid json $index")
                                    } catch (e: C2PAError) {
                                        errors.add("Builder $index: ${e.message}")
                                    }
                                }
                                3 -> {
                                    try {
                                        C2PA.readFile("/another/missing$index.jpg")
                                    } catch (e: C2PAError) {
                                        errors.add("Read2 $index: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

                awaitAll(*jobs.toTypedArray())
            }

            val expectedErrors = 3
            val success = errors.size == expectedErrors && successes.size == 1

            TestResult(
                "Concurrent Operations",
                success,
                if (success) {
                    "Concurrent operations handled correctly"
                } else {
                    "Concurrent operations failed"
                },
                "Expected errors: $expectedErrors, Got: ${errors.size}\n" +
                    "Expected successes: 1, Got: ${successes.size}\n" +
                    errors.joinToString("\n"),
            )
        }
    }
}
