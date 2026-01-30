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
import org.contentauth.c2pa.DigitalSourceType
import org.contentauth.c2pa.PredefinedAction
import org.contentauth.c2pa.manifest.ActionAssertion
import org.contentauth.c2pa.manifest.AssetType
import org.contentauth.c2pa.manifest.AssertionDefinition
import org.contentauth.c2pa.manifest.ClaimGeneratorInfo
import org.contentauth.c2pa.manifest.Coordinate
import org.contentauth.c2pa.manifest.DataSource
import org.contentauth.c2pa.manifest.Frame
import org.contentauth.c2pa.manifest.HashedUri
import org.contentauth.c2pa.manifest.ImageRegionType
import org.contentauth.c2pa.manifest.Ingredient
import org.contentauth.c2pa.manifest.IngredientDeltaValidationResult
import org.contentauth.c2pa.manifest.Item
import org.contentauth.c2pa.manifest.ManifestDefinition
import org.contentauth.c2pa.manifest.Metadata
import org.contentauth.c2pa.manifest.MetadataActor
import org.contentauth.c2pa.manifest.RangeType
import org.contentauth.c2pa.manifest.RegionOfInterest
import org.contentauth.c2pa.manifest.RegionRange
import org.contentauth.c2pa.manifest.ResourceRef
import org.contentauth.c2pa.manifest.ReviewRating
import org.contentauth.c2pa.manifest.Shape
import org.contentauth.c2pa.manifest.ShapeType
import org.contentauth.c2pa.manifest.StatusCodes
import org.contentauth.c2pa.manifest.Text
import org.contentauth.c2pa.manifest.TextSelector
import org.contentauth.c2pa.manifest.TextSelectorRange
import org.contentauth.c2pa.manifest.Time
import org.contentauth.c2pa.manifest.UnitType
import org.contentauth.c2pa.manifest.UriOrResource
import org.contentauth.c2pa.manifest.ValidationResults
import org.contentauth.c2pa.manifest.ValidationStatus
import org.contentauth.c2pa.manifest.ValidationStatusCode
import org.contentauth.c2pa.manifest.Relationship
import org.contentauth.c2pa.manifest.TrainingMiningEntry
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.File

abstract class ManifestTests : TestBase() {

    /**
     * Tests minimal ManifestDefinition creation.
     */
    suspend fun testMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Minimal") {
            val manifest = ManifestDefinition(
                title = "test",
                claimGeneratorInfo = listOf(
                    ClaimGeneratorInfo(name = "test_app"),
                ),
            )

            if (manifest.title != "test") {
                return@runTest TestResult(
                    "Manifest Minimal",
                    false,
                    "title != test",
                    "Got: ${manifest.title}",
                )
            }

            if (manifest.claimGeneratorInfo.isEmpty()) {
                return@runTest TestResult(
                    "Manifest Minimal",
                    false,
                    "claimGeneratorInfo is empty",
                )
            }

            // Test JSON round-trip
            cloneAndCompare(manifest, "Manifest Minimal")
        }
    }

    /**
     * Tests ManifestDefinition with actions assertion.
     */
    suspend fun testCreated(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Created") {
            val manifest = ManifestDefinition(
                title = "test",
                claimGeneratorInfo = listOf(
                    ClaimGeneratorInfo(
                        name = "test_app",
                        version = "1.0.0",
                        operatingSystem = ClaimGeneratorInfo.operatingSystem,
                    ),
                ),
                assertions = listOf(
                    AssertionDefinition.actions(
                        listOf(
                            ActionAssertion(
                                action = PredefinedAction.CREATED,
                                digitalSourceType = DigitalSourceType.DIGITAL_CAPTURE,
                            ),
                        ),
                    ),
                ),
            )

            val assertion = manifest.assertions.firstOrNull()
            if (assertion == null) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "manifest.assertions.first == null",
                )
            }

            if (assertion !is AssertionDefinition.Actions) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "manifest.assertions.first is not Actions",
                    "Got: ${assertion::class.simpleName}",
                )
            }

            val action = assertion.actions.firstOrNull()
            if (action == null) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "actions.first == null",
                )
            }

            if (action.action != "c2pa.created") {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "action.action != c2pa.created",
                    "Got: ${action.action}",
                )
            }

            val expectedSourceType = "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture"
            if (action.digitalSourceType != expectedSourceType) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "action.digitalSourceType incorrect",
                    "Expected: $expectedSourceType, Got: ${action.digitalSourceType}",
                )
            }

            val info = manifest.claimGeneratorInfo.firstOrNull()
            if (info == null) {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "claimGeneratorInfo.first == null",
                )
            }

            if (info.name != "test_app") {
                return@runTest TestResult(
                    "Manifest Created",
                    false,
                    "claimGeneratorInfo.name != test_app",
                    "Got: ${info.name}",
                )
            }

            // Test JSON round-trip
            cloneAndCompare(manifest, "Manifest Created")
        }
    }

    /**
     * Tests Shape creation and equality.
     */
    suspend fun testEnumRendering(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Enum Rendering") {
            val shape = Shape(
                type = ShapeType.RECTANGLE,
                origin = Coordinate(10.0, 10.0),
                width = 80.0,
                height = 80.0,
                unit = UnitType.PERCENT,
            )

            // Create another identical shape
            val shape2 = Shape(
                type = ShapeType.RECTANGLE,
                origin = Coordinate(10.0, 10.0),
                width = 80.0,
                height = 80.0,
                unit = UnitType.PERCENT,
            )

            if (shape == shape2) {
                TestResult(
                    "Manifest Enum Rendering",
                    true,
                    "Shape enums rendered as expected",
                    "Shape type: ${shape.type}, unit: ${shape.unit}",
                )
            } else {
                TestResult(
                    "Manifest Enum Rendering",
                    false,
                    "Shapes unexpectedly unequal",
                    "shape: $shape, shape2: $shape2",
                )
            }
        }
    }

    /**
     * Tests RegionOfInterest structure equality.
     */
    suspend fun testRegionOfInterest(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest RegionOfInterest") {
            val rr = RegionRange(type = RangeType.FRAME)

            val roi1 = RegionOfInterest(
                region = listOf(rr),
                imageRegionType = ImageRegionType.ANIMAL,
            )
            val roi2 = RegionOfInterest(
                region = listOf(rr),
                imageRegionType = ImageRegionType.ANIMAL,
            )

            if (roi1 == roi2) {
                TestResult(
                    "Manifest RegionOfInterest",
                    true,
                    "RegionOfInterests equal",
                )
            } else {
                TestResult(
                    "Manifest RegionOfInterest",
                    false,
                    "RegionOfInterests unexpectedly unequal",
                    "roi1: $roi1, roi2: $roi2",
                )
            }
        }
    }

    /**
     * Tests ResourceRef equality.
     */
    suspend fun testResourceRef(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest ResourceRef") {
            val r1 = ResourceRef(format = "application/octet-stream", identifier = "")
            val r2 = ResourceRef(format = "application/octet-stream", identifier = "")

            if (r1 == r2) {
                TestResult(
                    "Manifest ResourceRef",
                    true,
                    "ResourceRefs equal",
                    "ResourceRef: $r1",
                )
            } else {
                TestResult(
                    "Manifest ResourceRef",
                    false,
                    "ResourceRefs unexpectedly unequal",
                    "r1: $r1, r2: $r2",
                )
            }
        }
    }

    /**
     * Tests HashedUri equality.
     */
    suspend fun testHashedUri(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest HashedUri") {
            val hu1 = HashedUri(hash = "", url = "foo")
            val hu2 = HashedUri(hash = "", url = "foo")

            if (hu1 == hu2) {
                TestResult(
                    "Manifest HashedUri",
                    true,
                    "HashedUris equal",
                    "HashedUri: $hu1",
                )
            } else {
                TestResult(
                    "Manifest HashedUri",
                    false,
                    "HashedUris unexpectedly unequal",
                    "hu1: $hu1, hu2: $hu2",
                )
            }
        }
    }

    /**
     * Tests UriOrResource equality.
     */
    suspend fun testUriOrResource(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest UriOrResource") {
            val uor1 = UriOrResource(alg = "foo")
            val uor2 = UriOrResource(alg = "foo")

            if (uor1 == uor2) {
                TestResult(
                    "Manifest UriOrResource",
                    true,
                    "UriOrResources equal",
                )
            } else {
                TestResult(
                    "Manifest UriOrResource",
                    false,
                    "UriOrResources unexpectedly unequal",
                    "uor1: $uor1, uor2: $uor2",
                )
            }
        }
    }

    /**
     * Tests bulk initialization of various manifest-related types.
     */
    suspend fun testMassInit(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Mass Init") {
            try {
                // Test all type initializations
                val ingredient = Ingredient()
                val statusCodes = StatusCodes(failure = emptyList(), informational = emptyList(), success = emptyList())
                val metadata = Metadata()
                val validationStatus = ValidationStatus(code = ValidationStatusCode.ALGORITHM_UNSUPPORTED)
                val time = Time()
                val textSelector = TextSelector(fragment = "")
                val reviewRating = ReviewRating(explanation = "", value = 1)
                val dataSource = DataSource(type = "")
                val metadataActor = MetadataActor()
                val validationResults = ValidationResults()
                val ingredientDelta = IngredientDeltaValidationResult(
                    ingredientAssertionUri = "",
                    validationDeltas = statusCodes,
                )
                val item = Item(identifier = "track_id", value = "2")
                val assetType = AssetType(type = "")
                val frame = Frame()
                val textSelectorRange = TextSelectorRange(selector = textSelector)
                val text = Text(selectors = listOf(textSelectorRange))

                // Verify all objects initialized
                val allInitialized = listOf(
                    ingredient,
                    statusCodes,
                    metadata,
                    validationStatus,
                    time,
                    textSelector,
                    reviewRating,
                    dataSource,
                    metadataActor,
                    validationResults,
                    ingredientDelta,
                    item,
                    assetType,
                    frame,
                    textSelectorRange,
                    text,
                ).all { it != null }

                if (allInitialized) {
                    TestResult(
                        "Manifest Mass Init",
                        true,
                        "All objects initialize correctly",
                        "16 types successfully initialized",
                    )
                } else {
                    TestResult(
                        "Manifest Mass Init",
                        false,
                        "Some objects failed to initialize",
                    )
                }
            } catch (e: Exception) {
                TestResult(
                    "Manifest Mass Init",
                    false,
                    "Error during initialization: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests ManifestDefinition.toJson() produces valid JSON for Builder.
     */
    suspend fun testManifestToJson(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest to JSON") {
            val manifest = ManifestDefinition(
                title = "Test Photo",
                claimGeneratorInfo = listOf(
                    ClaimGeneratorInfo(
                        name = "TestApp",
                        version = "1.0",
                    ),
                ),
                assertions = listOf(
                    AssertionDefinition.actions(
                        listOf(
                            ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE),
                        ),
                    ),
                ),
            )

            try {
                val jsonString = manifest.toJson()

                // Verify it's valid JSON by parsing it
                val parsed = ManifestDefinition.fromJson(jsonString)

                if (parsed.title == manifest.title &&
                    parsed.claimGeneratorInfo.size == manifest.claimGeneratorInfo.size
                ) {
                    TestResult(
                        "Manifest to JSON",
                        true,
                        "ManifestDefinition.toJson() produces valid JSON",
                        "JSON: ${jsonString.take(200)}...",
                    )
                } else {
                    TestResult(
                        "Manifest to JSON",
                        false,
                        "Parsed manifest does not match original",
                        "Original title: ${manifest.title}, Parsed title: ${parsed.title}",
                    )
                }
            } catch (e: Exception) {
                TestResult(
                    "Manifest to JSON",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests Shape factory methods.
     */
    suspend fun testShapeFactoryMethods(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Shape Factory") {
            try {
                val rectangle = Shape.rectangle(
                    x = 10.0,
                    y = 20.0,
                    width = 100.0,
                    height = 50.0,
                    unit = UnitType.PIXEL,
                )

                val circle = Shape.circle(
                    centerX = 50.0,
                    centerY = 50.0,
                    diameter = 40.0,
                    unit = UnitType.PERCENT,
                )

                val polygon = Shape.polygon(
                    vertices = listOf(
                        Coordinate(0.0, 0.0),
                        Coordinate(100.0, 0.0),
                        Coordinate(50.0, 100.0),
                    ),
                )

                val allValid = rectangle.type == ShapeType.RECTANGLE &&
                    circle.type == ShapeType.CIRCLE &&
                    polygon.type == ShapeType.POLYGON &&
                    rectangle.origin?.x == 10.0 &&
                    circle.origin?.x == 50.0 &&
                    polygon.vertices?.size == 3

                if (allValid) {
                    TestResult(
                        "Manifest Shape Factory",
                        true,
                        "Shape factory methods work correctly",
                        "Rectangle: $rectangle, Circle: $circle, Polygon vertices: ${polygon.vertices?.size}",
                    )
                } else {
                    TestResult(
                        "Manifest Shape Factory",
                        false,
                        "Shape factory methods produced invalid shapes",
                    )
                }
            } catch (e: Exception) {
                TestResult(
                    "Manifest Shape Factory",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests that ManifestDefinition.toJson() produces JSON that Builder accepts.
     * This is the critical integration test - if this fails, the manifest types are broken.
     */
    suspend fun testManifestWithBuilder(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest with Builder") {
            try {
                val manifest = ManifestDefinition(
                    title = "Builder Integration Test",
                    claimGeneratorInfo = listOf(
                        ClaimGeneratorInfo(
                            name = "C2PA Android Test",
                            version = "1.0.0",
                        ),
                    ),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(
                                ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE),
                            ),
                        ),
                    ),
                )

                val jsonString = manifest.toJson()

                // Try to create a Builder from our manifest JSON
                val builder = Builder.fromJson(jsonString)
                try {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = ByteArrayStream(sourceImageData)

                    val outputFile = File.createTempFile("manifest-builder-test", ".jpg")
                    val destStream = FileStream(outputFile)
                    try {
                        val certPem = loadResourceAsString("es256_certs")
                        val keyPem = loadResourceAsString("es256_private")

                        val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
                        val signer = Signer.fromInfo(signerInfo)

                        try {
                            builder.sign("image/jpeg", sourceStream, destStream, signer)

                            // Read back and verify
                            val readManifest = C2PA.readFile(outputFile.absolutePath)
                            val json = JSONObject(readManifest)

                            if (!json.has("manifests")) {
                                return@runTest TestResult(
                                    "Manifest with Builder",
                                    false,
                                    "Signed file has no manifests",
                                )
                            }

                            // Verify our title made it through
                            val manifests = json.getJSONObject("manifests")
                            val keys = manifests.keys()
                            if (!keys.hasNext()) {
                                return@runTest TestResult(
                                    "Manifest with Builder",
                                    false,
                                    "No manifest entries found",
                                )
                            }

                            val firstManifest = manifests.getJSONObject(keys.next())
                            val title = firstManifest.optString("title", "")

                            if (title != "Builder Integration Test") {
                                return@runTest TestResult(
                                    "Manifest with Builder",
                                    false,
                                    "Title mismatch",
                                    "Expected: 'Builder Integration Test', Got: '$title'",
                                )
                            }

                            TestResult(
                                "Manifest with Builder",
                                true,
                                "ManifestDefinition successfully used with Builder",
                                "Signed file: ${outputFile.length()} bytes",
                            )
                        } finally {
                            signer.close()
                        }
                    } finally {
                        sourceStream.close()
                        destStream.close()
                        outputFile.delete()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: Exception) {
                TestResult(
                    "Manifest with Builder",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests all assertion types serialize and deserialize correctly.
     */
    suspend fun testAllAssertionTypes(): TestResult = withContext(Dispatchers.IO) {
        runTest("All Assertion Types") {
            try {
                val manifest = ManifestDefinition(
                    title = "Multi-Assertion Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        // Actions assertion
                        AssertionDefinition.actions(
                            listOf(
                                ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE),
                                ActionAssertion.edited(softwareAgent = "PhotoEditor 2.0"),
                            ),
                        ),
                        // CreativeWork assertion
                        AssertionDefinition.creativeWork(
                            mapOf(
                                "@context" to JsonPrimitive("https://schema.org"),
                                "@type" to JsonPrimitive("Photograph"),
                                "author" to buildJsonObject {
                                    put("@type", "Person")
                                    put("name", "Test Author")
                                },
                            ),
                        ),
                        // Training/Mining assertion
                        AssertionDefinition.trainingMining(
                            listOf(
                                TrainingMiningEntry(
                                    use = "notAllowed",
                                    constraintInfo = "No AI training permitted",
                                ),
                            ),
                        ),
                        // Custom assertion
                        AssertionDefinition.custom(
                            label = "com.example.custom",
                            data = buildJsonObject {
                                put("customField", "customValue")
                                put("version", 1)
                            },
                        ),
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                // Verify all assertions survived the round-trip
                if (parsed.assertions.size != 4) {
                    return@runTest TestResult(
                        "All Assertion Types",
                        false,
                        "Assertion count mismatch",
                        "Expected 4, got ${parsed.assertions.size}",
                    )
                }

                // Check each type
                val hasActions = parsed.assertions.any { it is AssertionDefinition.Actions }
                val hasCreativeWork = parsed.assertions.any { it is AssertionDefinition.CreativeWork }
                val hasTrainingMining = parsed.assertions.any { it is AssertionDefinition.TrainingMining }
                val hasCustom = parsed.assertions.any { it is AssertionDefinition.Custom }

                if (!hasActions || !hasCreativeWork || !hasTrainingMining || !hasCustom) {
                    return@runTest TestResult(
                        "All Assertion Types",
                        false,
                        "Missing assertion types after round-trip",
                        "Actions: $hasActions, CreativeWork: $hasCreativeWork, " +
                            "TrainingMining: $hasTrainingMining, Custom: $hasCustom",
                    )
                }

                // Verify custom assertion data
                val custom = parsed.assertions.filterIsInstance<AssertionDefinition.Custom>().first()
                if (custom.label != "com.example.custom") {
                    return@runTest TestResult(
                        "All Assertion Types",
                        false,
                        "Custom label mismatch",
                        "Expected 'com.example.custom', got '${custom.label}'",
                    )
                }

                TestResult(
                    "All Assertion Types",
                    true,
                    "All assertion types serialize correctly",
                    "JSON length: ${jsonString.length}",
                )
            } catch (e: Exception) {
                TestResult(
                    "All Assertion Types",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests ingredient handling with different relationships.
     */
    suspend fun testIngredients(): TestResult = withContext(Dispatchers.IO) {
        runTest("Ingredients") {
            try {
                val manifest = ManifestDefinition(
                    title = "Composite Image",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion(action = PredefinedAction.PLACED)),
                        ),
                    ),
                    ingredients = listOf(
                        Ingredient.parent(
                            title = "Background Image",
                            format = "image/jpeg",
                        ),
                        Ingredient.component(
                            title = "Overlay Graphic",
                            format = "image/png",
                        ),
                        Ingredient(
                            title = "Full Ingredient",
                            format = "image/jpeg",
                            relationship = Relationship.PARENT_OF,
                            description = "The original source image",
                            documentId = "doc-12345",
                            instanceId = "instance-67890",
                            provenance = "https://example.com/provenance",
                            validationStatus = listOf(
                                ValidationStatus(code = ValidationStatusCode.CLAIM_SIGNATURE_VALIDATED),
                            ),
                        ),
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                if (parsed.ingredients.size != 3) {
                    return@runTest TestResult(
                        "Ingredients",
                        false,
                        "Ingredient count mismatch",
                        "Expected 3, got ${parsed.ingredients.size}",
                    )
                }

                // Verify relationships
                val parent = parsed.ingredients.find { it.title == "Background Image" }
                val component = parsed.ingredients.find { it.title == "Overlay Graphic" }
                val full = parsed.ingredients.find { it.title == "Full Ingredient" }

                if (parent?.relationship != Relationship.PARENT_OF) {
                    return@runTest TestResult(
                        "Ingredients",
                        false,
                        "Parent relationship incorrect",
                        "Got: ${parent?.relationship}",
                    )
                }

                if (component?.relationship != Relationship.COMPONENT_OF) {
                    return@runTest TestResult(
                        "Ingredients",
                        false,
                        "Component relationship incorrect",
                        "Got: ${component?.relationship}",
                    )
                }

                // Verify full ingredient fields
                if (full?.documentId != "doc-12345" || full.instanceId != "instance-67890") {
                    return@runTest TestResult(
                        "Ingredients",
                        false,
                        "Full ingredient fields missing",
                        "documentId: ${full?.documentId}, instanceId: ${full?.instanceId}",
                    )
                }

                if (full?.validationStatus?.firstOrNull()?.code != ValidationStatusCode.CLAIM_SIGNATURE_VALIDATED) {
                    return@runTest TestResult(
                        "Ingredients",
                        false,
                        "Validation status not preserved",
                    )
                }

                TestResult(
                    "Ingredients",
                    true,
                    "Ingredients serialize correctly with all relationships",
                )
            } catch (e: Exception) {
                TestResult(
                    "Ingredients",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests action assertions with regions of interest (changes).
     */
    suspend fun testActionWithRegions(): TestResult = withContext(Dispatchers.IO) {
        runTest("Action with Regions") {
            try {
                val manifest = ManifestDefinition(
                    title = "Edited Image",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(
                                ActionAssertion(
                                    action = PredefinedAction.EDITED,
                                    softwareAgent = "PhotoEditor 3.0",
                                    reason = "Color correction applied",
                                    changes = listOf(
                                        RegionOfInterest(
                                            region = listOf(
                                                RegionRange(
                                                    type = RangeType.SPATIAL,
                                                    shape = Shape.rectangle(
                                                        x = 10.0,
                                                        y = 10.0,
                                                        width = 80.0,
                                                        height = 80.0,
                                                        unit = UnitType.PERCENT,
                                                    ),
                                                ),
                                            ),
                                            imageRegionType = ImageRegionType.FACE,
                                            description = "Face region was edited",
                                        ),
                                    ),
                                ),
                                ActionAssertion(
                                    action = PredefinedAction.REMOVED,
                                    reason = "Background removed",
                                    changes = listOf(
                                        RegionOfInterest(
                                            region = listOf(
                                                RegionRange(
                                                    type = RangeType.SPATIAL,
                                                    shape = Shape.polygon(
                                                        vertices = listOf(
                                                            Coordinate(0.0, 0.0),
                                                            Coordinate(100.0, 0.0),
                                                            Coordinate(100.0, 100.0),
                                                            Coordinate(0.0, 100.0),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                val actions = (parsed.assertions.first() as AssertionDefinition.Actions).actions
                if (actions.size != 2) {
                    return@runTest TestResult(
                        "Action with Regions",
                        false,
                        "Action count mismatch",
                        "Expected 2, got ${actions.size}",
                    )
                }

                // Verify first action's region
                val editAction = actions.find { it.action == "c2pa.edited" }
                val changes = editAction?.changes
                if (changes.isNullOrEmpty()) {
                    return@runTest TestResult(
                        "Action with Regions",
                        false,
                        "Edit action has no changes",
                    )
                }

                val region = changes.first().region?.first()
                val shape = region?.shape
                if (shape?.type != ShapeType.RECTANGLE) {
                    return@runTest TestResult(
                        "Action with Regions",
                        false,
                        "Shape type mismatch",
                        "Expected RECTANGLE, got ${shape?.type}",
                    )
                }

                if (shape.width != 80.0 || shape.height != 80.0) {
                    return@runTest TestResult(
                        "Action with Regions",
                        false,
                        "Shape dimensions incorrect",
                        "width: ${shape.width}, height: ${shape.height}",
                    )
                }

                // Verify polygon action
                val removeAction = actions.find { it.action == "c2pa.removed" }
                val polygonShape = removeAction?.changes?.first()?.region?.first()?.shape
                if (polygonShape?.type != ShapeType.POLYGON) {
                    return@runTest TestResult(
                        "Action with Regions",
                        false,
                        "Polygon shape type mismatch",
                        "Expected POLYGON, got ${polygonShape?.type}",
                    )
                }

                if (polygonShape.vertices?.size != 4) {
                    return@runTest TestResult(
                        "Action with Regions",
                        false,
                        "Polygon vertex count incorrect",
                        "Expected 4, got ${polygonShape.vertices?.size}",
                    )
                }

                TestResult(
                    "Action with Regions",
                    true,
                    "Actions with complex regions serialize correctly",
                )
            } catch (e: Exception) {
                TestResult(
                    "Action with Regions",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests malformed JSON handling - ensures graceful error handling.
     */
    suspend fun testMalformedJson(): TestResult = withContext(Dispatchers.IO) {
        runTest("Malformed JSON") {
            val testCases = listOf(
                "" to "empty string",
                "{" to "incomplete JSON",
                "{\"title\": \"test\"}" to "missing claimGeneratorInfo",
                "not json at all" to "invalid JSON syntax",
                "{\"title\": null, \"claim_generator_info\": []}" to "null required field",
            )

            for ((json, description) in testCases) {
                try {
                    ManifestDefinition.fromJson(json)
                    return@runTest TestResult(
                        "Malformed JSON",
                        false,
                        "Should have thrown for: $description",
                        "JSON: $json",
                    )
                } catch (e: Exception) {
                    // Expected - continue to next test case
                }
            }

            TestResult(
                "Malformed JSON",
                true,
                "All malformed JSON cases throw exceptions",
                "Tested ${testCases.size} cases",
            )
        }
    }

    /**
     * Tests special characters in string fields survive serialization.
     */
    suspend fun testSpecialCharacters(): TestResult = withContext(Dispatchers.IO) {
        runTest("Special Characters") {
            try {
                val specialStrings = listOf(
                    "Hello \"World\"", // Quotes
                    "Line1\nLine2", // Newline
                    "Tab\there", // Tab
                    "Unicode: \u00E9\u00E8\u00EA", // Accented chars
                    "Emoji: \uD83D\uDCF7", // Camera emoji
                    "Path: C:\\Users\\test", // Backslashes
                    "<script>alert('xss')</script>", // HTML-like
                    "日本語テスト", // Japanese
                )

                for (special in specialStrings) {
                    val manifest = ManifestDefinition(
                        title = special,
                        claimGeneratorInfo = listOf(
                            ClaimGeneratorInfo(name = special),
                        ),
                    )

                    val jsonString = manifest.toJson()
                    val parsed = ManifestDefinition.fromJson(jsonString)

                    if (parsed.title != special) {
                        return@runTest TestResult(
                            "Special Characters",
                            false,
                            "Title mismatch for special string",
                            "Expected: $special, Got: ${parsed.title}",
                        )
                    }

                    if (parsed.claimGeneratorInfo.first().name != special) {
                        return@runTest TestResult(
                            "Special Characters",
                            false,
                            "ClaimGeneratorInfo name mismatch",
                            "Expected: $special, Got: ${parsed.claimGeneratorInfo.first().name}",
                        )
                    }
                }

                TestResult(
                    "Special Characters",
                    true,
                    "All special characters serialize correctly",
                    "Tested ${specialStrings.size} strings",
                )
            } catch (e: Exception) {
                TestResult(
                    "Special Characters",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests the ManifestDefinition.created() convenience factory.
     */
    suspend fun testCreatedFactory(): TestResult = withContext(Dispatchers.IO) {
        runTest("Created Factory") {
            try {
                val manifest = ManifestDefinition.created(
                    title = "New Photo",
                    claimGeneratorInfo = ClaimGeneratorInfo(
                        name = "Camera App",
                        version = "2.0",
                    ),
                    digitalSourceType = DigitalSourceType.DIGITAL_CAPTURE,
                )

                if (manifest.title != "New Photo") {
                    return@runTest TestResult(
                        "Created Factory",
                        false,
                        "Title mismatch",
                    )
                }

                if (manifest.assertions.size != 1) {
                    return@runTest TestResult(
                        "Created Factory",
                        false,
                        "Should have exactly 1 assertion",
                    )
                }

                val assertion = manifest.assertions.first() as? AssertionDefinition.Actions
                if (assertion == null) {
                    return@runTest TestResult(
                        "Created Factory",
                        false,
                        "Assertion is not Actions type",
                    )
                }

                val action = assertion.actions.first()
                if (action.action != "c2pa.created") {
                    return@runTest TestResult(
                        "Created Factory",
                        false,
                        "Action is not c2pa.created",
                        "Got: ${action.action}",
                    )
                }

                val expectedSourceType = "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture"
                if (action.digitalSourceType != expectedSourceType) {
                    return@runTest TestResult(
                        "Created Factory",
                        false,
                        "Digital source type mismatch",
                        "Expected: $expectedSourceType, Got: ${action.digitalSourceType}",
                    )
                }

                // Verify it works with Builder
                val jsonString = manifest.toJson()
                val builder = Builder.fromJson(jsonString)
                builder.close()

                TestResult(
                    "Created Factory",
                    true,
                    "ManifestDefinition.created() works correctly",
                )
            } catch (e: Exception) {
                TestResult(
                    "Created Factory",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests all ValidationStatusCode values serialize correctly.
     */
    suspend fun testAllValidationStatusCodes(): TestResult = withContext(Dispatchers.IO) {
        runTest("All Validation Status Codes") {
            try {
                val allCodes = ValidationStatusCode.entries

                for (code in allCodes) {
                    val status = ValidationStatus(code = code, explanation = "Test for $code")
                    val ingredient = Ingredient(
                        title = "Test",
                        validationStatus = listOf(status),
                    )

                    val manifest = ManifestDefinition(
                        title = "Status Test",
                        claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                        ingredients = listOf(ingredient),
                    )

                    val jsonString = manifest.toJson()
                    val parsed = ManifestDefinition.fromJson(jsonString)

                    val parsedCode = parsed.ingredients.first().validationStatus?.first()?.code
                    if (parsedCode != code) {
                        return@runTest TestResult(
                            "All Validation Status Codes",
                            false,
                            "Code mismatch for $code",
                            "Got: $parsedCode",
                        )
                    }
                }

                TestResult(
                    "All Validation Status Codes",
                    true,
                    "All ${allCodes.size} validation status codes serialize correctly",
                )
            } catch (e: Exception) {
                TestResult(
                    "All Validation Status Codes",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests all DigitalSourceType values serialize correctly in actions.
     */
    suspend fun testAllDigitalSourceTypes(): TestResult = withContext(Dispatchers.IO) {
        runTest("All Digital Source Types") {
            try {
                val allTypes = DigitalSourceType.entries

                for (sourceType in allTypes) {
                    val manifest = ManifestDefinition(
                        title = "Source Type Test",
                        claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                        assertions = listOf(
                            AssertionDefinition.actions(
                                listOf(ActionAssertion.created(sourceType)),
                            ),
                        ),
                    )

                    val jsonString = manifest.toJson()
                    val parsed = ManifestDefinition.fromJson(jsonString)

                    val actions = (parsed.assertions.first() as AssertionDefinition.Actions).actions
                    val parsedSourceType = actions.first().digitalSourceType

                    // Verify it's a valid IPTC URL
                    if (parsedSourceType == null || !parsedSourceType.contains("digitalsourcetype")) {
                        return@runTest TestResult(
                            "All Digital Source Types",
                            false,
                            "Invalid source type URL for $sourceType",
                            "Got: $parsedSourceType",
                        )
                    }
                }

                TestResult(
                    "All Digital Source Types",
                    true,
                    "All ${allTypes.size} digital source types serialize correctly",
                )
            } catch (e: Exception) {
                TestResult(
                    "All Digital Source Types",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Helper function to clone and compare a manifest via JSON.
     */
    private fun cloneAndCompare(manifest: ManifestDefinition, testName: String): TestResult {
        return try {
            val jsonString = manifest.toJson()
            val m2 = ManifestDefinition.fromJson(jsonString)

            if (manifest == m2) {
                TestResult(
                    testName,
                    true,
                    "Manifest rendered as expected",
                    "JSON: ${jsonString.take(200)}${if (jsonString.length > 200) "..." else ""}",
                )
            } else {
                TestResult(
                    testName,
                    false,
                    "Broken compiled manifest",
                    "Original: ${manifest.toJson()}\nDecoded: ${m2.toJson()}",
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName,
                false,
                "Error during clone and compare: ${e.message}",
                e.stackTraceToString(),
            )
        }
    }
}
