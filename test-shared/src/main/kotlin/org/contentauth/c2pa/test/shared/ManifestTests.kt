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
import org.contentauth.c2pa.manifest.StandardAssertionLabel
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
import org.contentauth.c2pa.manifest.CawgTrainingMiningEntry
import org.contentauth.c2pa.manifest.ManifestValidator
import org.contentauth.c2pa.manifest.SettingsValidator
import kotlinx.serialization.json.JsonElement
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
                val outputFile = File.createTempFile("manifest-builder-test", ".jpg")
                try {
                    Builder.fromJson(jsonString).use { builder ->
                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                        ByteArrayStream(sourceImageData).use { sourceStream ->
                            FileStream(outputFile).use { destStream ->
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")

                                val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
                                Signer.fromInfo(signerInfo).use { signer ->
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
                                }
                            }
                        }
                    }
                } finally {
                    outputFile.delete()
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
                Builder.fromJson(jsonString).use { }

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
     * Tests CAWG training/mining assertion type.
     */
    suspend fun testCawgTrainingMiningAssertion(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWG Training Mining Assertion") {
            try {
                val cawgTrainingMining = AssertionDefinition.cawgTrainingMining(
                    listOf(
                        CawgTrainingMiningEntry(
                            use = "notAllowed",
                            constraintInfo = "No AI training permitted",
                            aiModelLearningType = "machineLearning",
                        ),
                        CawgTrainingMiningEntry(
                            use = "constrained",
                            constraintInfo = "https://example.com/terms",
                            aiMiningType = "dataAggregation",
                        ),
                    ),
                )

                val manifest = ManifestDefinition(
                    title = "CAWG Training Mining Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(cawgTrainingMining),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                val assertion = parsed.assertions.first()
                if (assertion !is AssertionDefinition.CawgTrainingMining) {
                    return@runTest TestResult(
                        "CAWG Training Mining Assertion",
                        false,
                        "Assertion is not CawgTrainingMining",
                        "Got: ${assertion::class.simpleName}",
                    )
                }

                if (assertion.entries.size != 2) {
                    return@runTest TestResult(
                        "CAWG Training Mining Assertion",
                        false,
                        "Entry count mismatch",
                        "Expected 2, got ${assertion.entries.size}",
                    )
                }

                val firstEntry = assertion.entries.first()
                if (firstEntry.use != "notAllowed" || firstEntry.aiModelLearningType != "machineLearning") {
                    return@runTest TestResult(
                        "CAWG Training Mining Assertion",
                        false,
                        "First entry data mismatch",
                        "use: ${firstEntry.use}, aiModelLearningType: ${firstEntry.aiModelLearningType}",
                    )
                }

                TestResult(
                    "CAWG Training Mining Assertion",
                    true,
                    "CAWG training/mining assertion serializes correctly",
                    "Entries: ${assertion.entries.size}",
                )
            } catch (e: Exception) {
                TestResult(
                    "CAWG Training Mining Assertion",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests ManifestValidator for basic validation.
     */
    suspend fun testManifestValidator(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Validator") {
            try {
                // Test 1: Valid manifest should pass
                val validManifest = ManifestDefinition(
                    title = "Valid Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE)),
                        ),
                    ),
                )

                val validResult = ManifestValidator.validate(validManifest)
                if (validResult.hasErrors()) {
                    return@runTest TestResult(
                        "Manifest Validator",
                        false,
                        "Valid manifest should not have errors",
                        "Errors: ${validResult.errors}",
                    )
                }

                // Test 2: Empty title should produce error
                val emptyTitleManifest = ManifestDefinition(
                    title = "",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                )

                val emptyTitleResult = ManifestValidator.validate(emptyTitleManifest)
                if (!emptyTitleResult.hasErrors()) {
                    return@runTest TestResult(
                        "Manifest Validator",
                        false,
                        "Empty title should produce an error",
                    )
                }

                // Test 3: Missing claim_generator_info should produce error
                val noClaimGenManifest = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = emptyList(),
                )

                val noClaimGenResult = ManifestValidator.validate(noClaimGenManifest)
                if (!noClaimGenResult.hasErrors()) {
                    return@runTest TestResult(
                        "Manifest Validator",
                        false,
                        "Empty claim_generator_info should produce an error",
                    )
                }

                TestResult(
                    "Manifest Validator",
                    true,
                    "ManifestValidator correctly validates manifests",
                )
            } catch (e: Exception) {
                TestResult(
                    "Manifest Validator",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests manifest with mixed assertion types.
     */
    suspend fun testMixedAssertionTypes(): TestResult = withContext(Dispatchers.IO) {
        runTest("Mixed Assertion Types") {
            try {
                val manifest = ManifestDefinition(
                    title = "Mixed Assertions Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test", version = "1.0")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion.edited(softwareAgent = "TestApp/1.0")),
                        ),
                        AssertionDefinition.exif(
                            mapOf("Make" to JsonPrimitive("TestCamera")),
                        ),
                        AssertionDefinition.cawgTrainingMining(
                            listOf(CawgTrainingMiningEntry(use = "notAllowed")),
                        ),
                    ),
                    ingredients = listOf(
                        Ingredient.parent("Original Image", "image/jpeg"),
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                if (parsed.assertions.size != 3) {
                    return@runTest TestResult(
                        "Mixed Assertion Types",
                        false,
                        "Assertions count mismatch",
                        "Expected 3, got ${parsed.assertions.size}",
                    )
                }

                if (parsed.ingredients.size != 1) {
                    return@runTest TestResult(
                        "Mixed Assertion Types",
                        false,
                        "Ingredients count mismatch",
                        "Expected 1, got ${parsed.ingredients.size}",
                    )
                }

                val hasActions = parsed.assertions.any { it is AssertionDefinition.Actions }
                val hasExif = parsed.assertions.any { it is AssertionDefinition.Exif }
                val hasCawgTraining = parsed.assertions.any { it is AssertionDefinition.CawgTrainingMining }

                if (!hasActions || !hasExif || !hasCawgTraining) {
                    return@runTest TestResult(
                        "Mixed Assertion Types",
                        false,
                        "Missing expected assertion types",
                        "Actions: $hasActions, Exif: $hasExif, CawgTrainingMining: $hasCawgTraining",
                    )
                }

                TestResult(
                    "Mixed Assertion Types",
                    true,
                    "Manifest with mixed assertion types works correctly",
                    "Assertions: ${parsed.assertions.size}, Ingredients: ${parsed.ingredients.size}",
                )
            } catch (e: Exception) {
                TestResult(
                    "Mixed Assertion Types",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests ManifestDefinition.edited factory.
     */
    suspend fun testEditedFactory(): TestResult = withContext(Dispatchers.IO) {
        runTest("Edited Factory") {
            try {
                val manifest = ManifestDefinition.edited(
                    title = "Edited Photo",
                    claimGeneratorInfo = ClaimGeneratorInfo(name = "PhotoEditor", version = "2.0"),
                    parentIngredient = Ingredient.parent("Original.jpg", "image/jpeg"),
                    editActions = listOf(
                        ActionAssertion.edited(softwareAgent = "PhotoEditor/2.0"),
                        ActionAssertion(
                            action = PredefinedAction.CROPPED,
                            softwareAgent = "PhotoEditor/2.0",
                        ),
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                // Verify title
                if (parsed.title != "Edited Photo") {
                    return@runTest TestResult(
                        "Edited Factory",
                        false,
                        "Title mismatch",
                        "Expected 'Edited Photo', got '${parsed.title}'",
                    )
                }

                // Verify ingredients
                if (parsed.ingredients.size != 1) {
                    return@runTest TestResult(
                        "Edited Factory",
                        false,
                        "Should have one ingredient",
                        "Got: ${parsed.ingredients.size}",
                    )
                }

                val ingredient = parsed.ingredients.first()
                if (ingredient.relationship != Relationship.PARENT_OF) {
                    return@runTest TestResult(
                        "Edited Factory",
                        false,
                        "Ingredient should be parent",
                        "Got: ${ingredient.relationship}",
                    )
                }

                // Verify actions
                val actionsAssertion = parsed.assertions.firstOrNull() as? AssertionDefinition.Actions
                if (actionsAssertion == null || actionsAssertion.actions.size != 2) {
                    return@runTest TestResult(
                        "Edited Factory",
                        false,
                        "Should have 2 actions",
                        "Got: ${actionsAssertion?.actions?.size ?: 0}",
                    )
                }

                TestResult(
                    "Edited Factory",
                    true,
                    "ManifestDefinition.edited works correctly",
                )
            } catch (e: Exception) {
                TestResult(
                    "Edited Factory",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests that the default Builder.fromJson correctly handles assertion labels.
     *
     * The SDK uses `created_assertion_labels` to determine which assertions are
     * "created" (attributed to the signer) vs "gathered". Labels not in that list
     * are automatically treated as gathered by the SDK.
     */
    suspend fun testAssertionsWithBuilder(): TestResult = withContext(Dispatchers.IO) {
        runTest("Assertions with Builder") {
            try {
                val manifest = ManifestDefinition(
                    title = "Builder Assertions Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "TestApp", version = "1.0")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE)),
                        ),
                        AssertionDefinition.custom(
                            label = "com.test.custom",
                            data = buildJsonObject { put("test", "value") },
                        ),
                    ),
                )

                val jsonString = manifest.toJson()
                val outputFile = File.createTempFile("assertions-builder-test", ".jpg")
                try {
                    Builder.fromJson(jsonString).use { builder ->
                        val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                        ByteArrayStream(sourceImageData).use { sourceStream ->
                            FileStream(outputFile).use { destStream ->
                                val certPem = loadResourceAsString("es256_certs")
                                val keyPem = loadResourceAsString("es256_private")
                                val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
                                Signer.fromInfo(signerInfo).use { signer ->
                                    builder.sign("image/jpeg", sourceStream, destStream, signer)

                                    val readManifest = C2PA.readFile(outputFile.absolutePath)
                                    val json = JSONObject(readManifest)

                                    if (!json.has("manifests")) {
                                        return@runTest TestResult(
                                            "Assertions with Builder",
                                            false,
                                            "Signed file has no manifests",
                                        )
                                    }

                                    TestResult(
                                        "Assertions with Builder",
                                        true,
                                        "Manifest with assertions signed successfully",
                                        "File size: ${outputFile.length()} bytes",
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    outputFile.delete()
                }
            } catch (e: Exception) {
                TestResult(
                    "Assertions with Builder",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Test that creates a signed file with a custom assertion that will be treated as gathered.
     *
     * The default Builder.fromJson automatically configures the SDK to place common assertions
     * (c2pa.actions, c2pa.thumbnail.claim, etc.) in created_assertions. Labels not in the
     * `created_assertion_labels` list are treated as gathered by the SDK.
     *
     * Note: cawg.identity assertions require a valid structure with referenced_assertions
     * that can only be constructed by the SDK during signing. To test the gathered assertion
     * mechanism, we use a custom assertion label instead.
     */
    suspend fun testCustomGatheredAssertionWithBuilder(): TestResult = withContext(Dispatchers.IO) {
        runTest("Custom Gathered Assertion with Builder") {
            try {
                val manifest = ManifestDefinition(
                    title = "Custom Gathered Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "c2pa-android", version = "1.0")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE)),
                        ),
                        AssertionDefinition.custom(
                            label = "com.test.gathered-data",
                            data = buildJsonObject {
                                put("source", "test")
                                put("verified", true)
                            },
                        ),
                    ),
                )

                val jsonString = manifest.toJson()
                val outputFile = File(getContext().cacheDir, "custom_gathered_test_output.jpg")
                Builder.fromJson(jsonString).use { builder ->
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    ByteArrayStream(sourceImageData).use { sourceStream ->
                        FileStream(outputFile).use { destStream ->
                            val certPem = loadResourceAsString("es256_certs")
                            val keyPem = loadResourceAsString("es256_private")
                            val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
                            Signer.fromInfo(signerInfo).use { signer ->
                                builder.sign("image/jpeg", sourceStream, destStream, signer)

                                val readManifest = C2PA.readFile(outputFile.absolutePath)
                                val json = JSONObject(readManifest)

                                if (!json.has("manifests")) {
                                    return@runTest TestResult(
                                        "Custom Gathered Assertion with Builder",
                                        false,
                                        "Signed file has no manifests",
                                    )
                                }

                                TestResult(
                                    "Custom Gathered Assertion with Builder",
                                    true,
                                    "Manifest with custom gathered assertion signed successfully",
                                    "File size: ${outputFile.length()} bytes",
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                TestResult(
                    "Custom Gathered Assertion with Builder",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests ManifestValidator for deprecated assertions and claim_version validation.
     */
    suspend fun testDeprecatedAssertionValidation(): TestResult = withContext(Dispatchers.IO) {
        runTest("Deprecated Assertion Validation") {
            try {
                // Test 1: Deprecated EXIF assertion should generate warning
                val manifestWithExif = ManifestDefinition(
                    title = "Test with Deprecated EXIF",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.exif(
                            mapOf("Make" to JsonPrimitive("TestCamera")),
                        ),
                    ),
                )

                val exifResult = ManifestValidator.validate(manifestWithExif)
                val hasExifWarning = exifResult.warnings.any { it.contains("stds.exif") && it.contains("deprecated") }
                if (!hasExifWarning) {
                    return@runTest TestResult(
                        "Deprecated Assertion Validation",
                        false,
                        "stds.exif should generate deprecation warning",
                        "Warnings: ${exifResult.warnings}",
                    )
                }

                // Test 2: Deprecated CreativeWork assertion should generate warning
                val manifestWithCreativeWork = ManifestDefinition(
                    title = "Test with Deprecated CreativeWork",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.creativeWork(
                            mapOf("author" to JsonPrimitive("Test Author")),
                        ),
                    ),
                )

                val cwResult = ManifestValidator.validate(manifestWithCreativeWork)
                val hasCwWarning = cwResult.warnings.any {
                    it.contains("stds.schema-org.CreativeWork") && it.contains("deprecated")
                }
                if (!hasCwWarning) {
                    return@runTest TestResult(
                        "Deprecated Assertion Validation",
                        false,
                        "stds.schema-org.CreativeWork should generate deprecation warning",
                        "Warnings: ${cwResult.warnings}",
                    )
                }

                // Test 3: Non-v2 claim_version should generate warning via JSON validation
                val v1ManifestJson = """
                {
                    "title": "V1 Manifest",
                    "claim_generator": "test/1.0",
                    "claim_version": 1,
                    "assertions": []
                }
                """.trimIndent()

                val v1Result = ManifestValidator.validateJson(v1ManifestJson, logWarnings = false)
                val hasV1Warning = v1Result.warnings.any {
                    it.contains("claim_version is 1") || it.contains("Version 1")
                }
                if (!hasV1Warning) {
                    return@runTest TestResult(
                        "Deprecated Assertion Validation",
                        false,
                        "claim_version 1 should generate warning",
                        "Warnings: ${v1Result.warnings}",
                    )
                }

                // Test 4: Valid v2 manifest should not have claim_version warning
                val v2ManifestJson = """
                {
                    "title": "V2 Manifest",
                    "claim_generator_info": [{"name": "test"}],
                    "claim_version": 2,
                    "assertions": []
                }
                """.trimIndent()

                val v2Result = ManifestValidator.validateJson(v2ManifestJson, logWarnings = false)
                val hasV2Warning = v2Result.warnings.any { it.contains("claim_version") }
                if (hasV2Warning) {
                    return@runTest TestResult(
                        "Deprecated Assertion Validation",
                        false,
                        "claim_version 2 should not generate warning",
                        "Warnings: ${v2Result.warnings}",
                    )
                }

                TestResult(
                    "Deprecated Assertion Validation",
                    true,
                    "ManifestValidator correctly identifies deprecated assertions and claim_version issues",
                    "Tested: stds.exif, stds.schema-org.CreativeWork, claim_version 1 vs 2",
                )
            } catch (e: Exception) {
                TestResult(
                    "Deprecated Assertion Validation",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests all PredefinedAction values serialize correctly.
     *
     * This test verifies that every predefined action from the C2PA 2.3 specification
     * can be serialized and deserialized correctly in an ActionAssertion.
     */
    suspend fun testAllPredefinedActions(): TestResult = withContext(Dispatchers.IO) {
        runTest("All Predefined Actions") {
            try {
                val allActions = PredefinedAction.entries

                for (predefinedAction in allActions) {
                    val manifest = ManifestDefinition(
                        title = "Action Test: ${predefinedAction.name}",
                        claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                        assertions = listOf(
                            AssertionDefinition.actions(
                                listOf(
                                    ActionAssertion(
                                        action = predefinedAction,
                                        softwareAgent = "TestApp/1.0",
                                    ),
                                ),
                            ),
                        ),
                    )

                    val jsonString = manifest.toJson()
                    val parsed = ManifestDefinition.fromJson(jsonString)

                    val actions = (parsed.assertions.first() as AssertionDefinition.Actions).actions
                    val parsedAction = actions.first().action

                    // Verify the action value matches the predefined action's value
                    if (parsedAction != predefinedAction.value) {
                        return@runTest TestResult(
                            "All Predefined Actions",
                            false,
                            "Action mismatch for ${predefinedAction.name}",
                            "Expected: ${predefinedAction.value}, Got: $parsedAction",
                        )
                    }
                }

                TestResult(
                    "All Predefined Actions",
                    true,
                    "All ${allActions.size} predefined actions serialize correctly",
                    "Actions: ${allActions.map { it.name }.joinToString(", ")}",
                )
            } catch (e: Exception) {
                TestResult(
                    "All Predefined Actions",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests redactions field serialization.
     *
     * Per C2PA spec section 6.8, assertions can be redacted from ingredients.
     * The redactions field contains a list of assertion URIs to redact.
     */
    suspend fun testRedactions(): TestResult = withContext(Dispatchers.IO) {
        runTest("Redactions") {
            try {
                val manifest = ManifestDefinition(
                    title = "Manifest with Redactions",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE)),
                        ),
                    ),
                    ingredients = listOf(
                        Ingredient.parent("Parent with redacted assertions", "image/jpeg"),
                    ),
                    redactions = listOf(
                        "self#jumbf=/c2pa/urn:uuid:example/c2pa.assertions/c2pa.actions",
                        "self#jumbf=/c2pa/urn:uuid:example/c2pa.assertions/stds.exif",
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                // Capture to local variable for smart cast
                val redactions = parsed.redactions

                // Verify redactions list is preserved
                if (redactions == null || redactions.size != 2) {
                    return@runTest TestResult(
                        "Redactions",
                        false,
                        "Redactions list not preserved",
                        "Expected 2 redactions, got: ${redactions?.size ?: 0}",
                    )
                }

                // Verify specific redaction URIs
                if (!redactions.contains("self#jumbf=/c2pa/urn:uuid:example/c2pa.assertions/c2pa.actions")) {
                    return@runTest TestResult(
                        "Redactions",
                        false,
                        "c2pa.actions redaction not found",
                    )
                }

                if (!redactions.contains("self#jumbf=/c2pa/urn:uuid:example/c2pa.assertions/stds.exif")) {
                    return@runTest TestResult(
                        "Redactions",
                        false,
                        "stds.exif redaction not found",
                    )
                }

                // Verify JSON contains redactions key
                if (!jsonString.contains("\"redactions\"")) {
                    return@runTest TestResult(
                        "Redactions",
                        false,
                        "JSON does not contain redactions key",
                    )
                }

                TestResult(
                    "Redactions",
                    true,
                    "Redactions field serializes correctly",
                    "Redacted ${redactions.size} assertion URIs",
                )
            } catch (e: Exception) {
                TestResult(
                    "Redactions",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests all Relationship values (parentOf, componentOf, inputTo) serialize correctly.
     *
     * This test verifies that all three C2PA ingredient relationships work properly
     * with both factory methods and direct enum usage.
     */
    suspend fun testAllIngredientRelationships(): TestResult = withContext(Dispatchers.IO) {
        runTest("All Ingredient Relationships") {
            try {
                val manifest = ManifestDefinition(
                    title = "Relationship Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion(action = PredefinedAction.EDITED)),
                        ),
                    ),
                    ingredients = listOf(
                        // Test parentOf via factory method
                        Ingredient.parent("Parent Image", "image/jpeg"),
                        // Test componentOf via factory method
                        Ingredient.component("Component Overlay", "image/png"),
                        // Test inputTo via factory method
                        Ingredient.inputTo("Input Reference", "image/jpeg"),
                        // Test direct enum usage for all relationships
                        Ingredient(
                            title = "Direct Parent",
                            relationship = Relationship.PARENT_OF,
                        ),
                        Ingredient(
                            title = "Direct Component",
                            relationship = Relationship.COMPONENT_OF,
                        ),
                        Ingredient(
                            title = "Direct Input",
                            relationship = Relationship.INPUT_TO,
                        ),
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                // Verify all 6 ingredients
                if (parsed.ingredients.size != 6) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "Ingredient count mismatch",
                        "Expected 6, got ${parsed.ingredients.size}",
                    )
                }

                // Verify each relationship via factory
                val parentViaFactory = parsed.ingredients.find { it.title == "Parent Image" }
                val componentViaFactory = parsed.ingredients.find { it.title == "Component Overlay" }
                val inputViaFactory = parsed.ingredients.find { it.title == "Input Reference" }

                if (parentViaFactory?.relationship != Relationship.PARENT_OF) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "parentOf factory failed",
                        "Got: ${parentViaFactory?.relationship}",
                    )
                }

                if (componentViaFactory?.relationship != Relationship.COMPONENT_OF) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "componentOf factory failed",
                        "Got: ${componentViaFactory?.relationship}",
                    )
                }

                if (inputViaFactory?.relationship != Relationship.INPUT_TO) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "inputTo factory failed",
                        "Got: ${inputViaFactory?.relationship}",
                    )
                }

                // Verify each relationship via direct enum
                val parentDirect = parsed.ingredients.find { it.title == "Direct Parent" }
                val componentDirect = parsed.ingredients.find { it.title == "Direct Component" }
                val inputDirect = parsed.ingredients.find { it.title == "Direct Input" }

                if (parentDirect?.relationship != Relationship.PARENT_OF) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "parentOf direct enum failed",
                        "Got: ${parentDirect?.relationship}",
                    )
                }

                if (componentDirect?.relationship != Relationship.COMPONENT_OF) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "componentOf direct enum failed",
                        "Got: ${componentDirect?.relationship}",
                    )
                }

                if (inputDirect?.relationship != Relationship.INPUT_TO) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "inputTo direct enum failed",
                        "Got: ${inputDirect?.relationship}",
                    )
                }

                // Verify JSON contains all relationship strings
                val hasParentOf = jsonString.contains("\"parentOf\"")
                val hasComponentOf = jsonString.contains("\"componentOf\"")
                val hasInputTo = jsonString.contains("\"inputTo\"")

                if (!hasParentOf || !hasComponentOf || !hasInputTo) {
                    return@runTest TestResult(
                        "All Ingredient Relationships",
                        false,
                        "JSON missing relationship strings",
                        "parentOf: $hasParentOf, componentOf: $hasComponentOf, inputTo: $hasInputTo",
                    )
                }

                TestResult(
                    "All Ingredient Relationships",
                    true,
                    "All 3 ingredient relationships (parentOf, componentOf, inputTo) serialize correctly",
                    "Tested via factory methods and direct enum usage",
                )
            } catch (e: Exception) {
                TestResult(
                    "All Ingredient Relationships",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    suspend fun testSettingsValidatorValid(): TestResult = withContext(Dispatchers.IO) {
        runTest("Settings Validator - Valid") {
            try {
                val validSettings = """
                    {
                        "version": 1,
                        "builder": {
                            "created_assertion_labels": ["c2pa.actions", "c2pa.thumbnail.claim"]
                        }
                    }
                """.trimIndent()

                val result = SettingsValidator.validate(validSettings, logWarnings = false)
                val success = result.isValid() && !result.hasErrors()

                TestResult(
                    "Settings Validator - Valid",
                    success,
                    if (success) {
                        "Valid settings pass validation"
                    } else {
                        "Valid settings should not have errors"
                    },
                    "Errors: ${result.errors}, Warnings: ${result.warnings}",
                )
            } catch (e: Exception) {
                TestResult(
                    "Settings Validator - Valid",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testSettingsValidatorErrors(): TestResult = withContext(Dispatchers.IO) {
        runTest("Settings Validator - Errors") {
            try {
                // Missing version
                val noVersion = """{"builder": {}}"""
                val noVersionResult = SettingsValidator.validate(noVersion, logWarnings = false)
                val missingVersionDetected = noVersionResult.hasErrors() &&
                    noVersionResult.errors.any { it.contains("version") }

                // Wrong version
                val wrongVersion = """{"version": 99}"""
                val wrongVersionResult = SettingsValidator.validate(wrongVersion, logWarnings = false)
                val wrongVersionDetected = wrongVersionResult.hasErrors() &&
                    wrongVersionResult.errors.any { it.contains("version") }

                // Invalid JSON
                val invalidJson = "not json at all"
                val invalidResult = SettingsValidator.validate(invalidJson, logWarnings = false)
                val invalidJsonDetected = invalidResult.hasErrors()

                // Unknown top-level key
                val unknownKey = """{"version": 1, "bogus_section": {}}"""
                val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
                val unknownKeyDetected = unknownResult.hasWarnings() &&
                    unknownResult.warnings.any { it.contains("bogus_section") }

                // Verify section with non-boolean
                val badVerify = """{"version": 1, "verify": {"verify_trust": "yes"}}"""
                val badVerifyResult = SettingsValidator.validate(badVerify, logWarnings = false)
                val badVerifyDetected = badVerifyResult.hasErrors() &&
                    badVerifyResult.errors.any { it.contains("verify_trust") }

                val success = missingVersionDetected && wrongVersionDetected &&
                    invalidJsonDetected && unknownKeyDetected && badVerifyDetected

                TestResult(
                    "Settings Validator - Errors",
                    success,
                    if (success) {
                        "All error cases detected"
                    } else {
                        "Some error cases not detected"
                    },
                    "Missing version: $missingVersionDetected, Wrong version: $wrongVersionDetected, " +
                        "Invalid JSON: $invalidJsonDetected, Unknown key: $unknownKeyDetected, " +
                        "Bad verify: $badVerifyDetected",
                )
            } catch (e: Exception) {
                TestResult(
                    "Settings Validator - Errors",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testSettingsValidatorBuilderSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Settings Validator - Builder Section") {
            try {
                // Valid builder with intent
                val withIntent = """
                    {
                        "version": 1,
                        "builder": {
                            "intent": {"Create": "digitalCapture"},
                            "created_assertion_labels": ["c2pa.actions"]
                        }
                    }
                """.trimIndent()
                val intentResult = SettingsValidator.validate(withIntent, logWarnings = false)
                val intentValid = intentResult.isValid()

                // Invalid intent string
                val badIntent = """
                    {
                        "version": 1,
                        "builder": {
                            "intent": "InvalidIntent"
                        }
                    }
                """.trimIndent()
                val badIntentResult = SettingsValidator.validate(badIntent, logWarnings = false)
                val badIntentDetected = badIntentResult.hasErrors() &&
                    badIntentResult.errors.any { it.contains("intent") }

                // Invalid thumbnail format
                val badThumbnail = """
                    {
                        "version": 1,
                        "builder": {
                            "thumbnail": {
                                "format": "bmp",
                                "quality": "ultra"
                            }
                        }
                    }
                """.trimIndent()
                val badThumbResult = SettingsValidator.validate(badThumbnail, logWarnings = false)
                val badFormatDetected = badThumbResult.hasErrors() &&
                    badThumbResult.errors.any { it.contains("format") }
                val badQualityDetected = badThumbResult.hasErrors() &&
                    badThumbResult.errors.any { it.contains("quality") }

                // created_assertion_labels not an array
                val badLabels = """{"version": 1, "builder": {"created_assertion_labels": "not_array"}}"""
                val badLabelsResult = SettingsValidator.validate(badLabels, logWarnings = false)
                val badLabelsDetected = badLabelsResult.hasErrors() &&
                    badLabelsResult.errors.any { it.contains("created_assertion_labels") }

                val success = intentValid && badIntentDetected && badFormatDetected &&
                    badQualityDetected && badLabelsDetected

                TestResult(
                    "Settings Validator - Builder Section",
                    success,
                    if (success) {
                        "Builder section validation works"
                    } else {
                        "Builder section validation failed"
                    },
                    "Intent valid: $intentValid, Bad intent: $badIntentDetected, " +
                        "Bad format: $badFormatDetected, Bad quality: $badQualityDetected, " +
                        "Bad labels: $badLabelsDetected",
                )
            } catch (e: Exception) {
                TestResult(
                    "Settings Validator - Builder Section",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testSettingsValidatorSignerSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Settings Validator - Signer Section") {
            try {
                // Signer with neither local nor remote
                val noSigner = """{"version": 1, "signer": {}}"""
                val noSignerResult = SettingsValidator.validate(noSigner, logWarnings = false)
                val noSignerDetected = noSignerResult.hasErrors() &&
                    noSignerResult.errors.any { it.contains("local") || it.contains("remote") }

                // Local signer missing required fields
                val badLocal = """{"version": 1, "signer": {"local": {}}}"""
                val badLocalResult = SettingsValidator.validate(badLocal, logWarnings = false)
                val missingAlg = badLocalResult.errors.any { it.contains("alg") }
                val missingCert = badLocalResult.errors.any { it.contains("sign_cert") }
                val missingKey = badLocalResult.errors.any { it.contains("private_key") }

                // Remote signer missing required fields
                val badRemote = """{"version": 1, "signer": {"remote": {}}}"""
                val badRemoteResult = SettingsValidator.validate(badRemote, logWarnings = false)
                val missingUrl = badRemoteResult.errors.any { it.contains("url") }

                // Both local and remote
                val bothSigners = """{"version": 1, "signer": {"local": {}, "remote": {}}}"""
                val bothResult = SettingsValidator.validate(bothSigners, logWarnings = false)
                val bothDetected = bothResult.errors.any { it.contains("both") }

                val success = noSignerDetected && missingAlg && missingCert && missingKey &&
                    missingUrl && bothDetected

                TestResult(
                    "Settings Validator - Signer Section",
                    success,
                    if (success) {
                        "Signer section validation works"
                    } else {
                        "Signer section validation failed"
                    },
                    "No signer: $noSignerDetected, Missing alg: $missingAlg, " +
                        "Missing cert: $missingCert, Missing key: $missingKey, " +
                        "Missing URL: $missingUrl, Both: $bothDetected",
                )
            } catch (e: Exception) {
                TestResult(
                    "Settings Validator - Signer Section",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    /**
     * Tests ManifestValidator for deprecated assertion detection.
     */
    suspend fun testManifestValidatorDeprecatedAssertions(): TestResult = withContext(Dispatchers.IO) {
        runTest("Manifest Validator - Deprecated Assertions") {
            try {
                val manifest = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.exif(
                            mapOf("Make" to JsonPrimitive("TestCamera")),
                        ),
                    ),
                )

                val result = ManifestValidator.validate(manifest)
                val hasDeprecatedWarning = result.hasWarnings() &&
                    result.warnings.any { it.contains("stds.exif") && it.contains("deprecated") }

                TestResult(
                    "Manifest Validator - Deprecated Assertions",
                    hasDeprecatedWarning,
                    if (hasDeprecatedWarning) {
                        "Deprecated assertion validation works"
                    } else {
                        "Deprecated assertion validation failed"
                    },
                    "Warnings: ${result.warnings}",
                )
            } catch (e: Exception) {
                TestResult(
                    "Manifest Validator - Deprecated Assertions",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    /**
     * Helper function to clone and compare a manifest via JSON.
     */
    suspend fun testDigitalSourceTypeFromIptcUrl(): TestResult = withContext(Dispatchers.IO) {
        runTest("DigitalSourceType fromIptcUrl") {
            try {
                var allMatch = true
                val mismatches = mutableListOf<String>()

                DigitalSourceType.entries.forEach { sourceType ->
                    val url = sourceType.toIptcUrl()
                    val parsed = DigitalSourceType.fromIptcUrl(url)
                    if (parsed != sourceType) {
                        allMatch = false
                        mismatches.add("$sourceType: toIptcUrl='$url', fromIptcUrl=$parsed")
                    }
                }

                // Also test unknown URL returns null
                val unknown = DigitalSourceType.fromIptcUrl("http://example.com/unknown")
                val unknownIsNull = unknown == null

                val success = allMatch && unknownIsNull

                TestResult(
                    "DigitalSourceType fromIptcUrl",
                    success,
                    if (success) {
                        "All ${DigitalSourceType.entries.size} source types round-trip correctly"
                    } else {
                        "Round-trip failed"
                    },
                    if (mismatches.isNotEmpty()) {
                        "Mismatches: $mismatches"
                    } else {
                        "All entries match, unknown URL returns null: $unknownIsNull"
                    },
                )
            } catch (e: Exception) {
                TestResult(
                    "DigitalSourceType fromIptcUrl",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testManifestAssertionLabels(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestDefinition assertionLabels") {
            try {
                val manifest = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion(action = PredefinedAction.CREATED)),
                        ),
                        AssertionDefinition.actions(
                            listOf(ActionAssertion(action = PredefinedAction.EDITED)),
                        ),
                        AssertionDefinition.custom("com.example.test", buildJsonObject { put("key", "value") }),
                    ),
                )

                val labels = manifest.assertionLabels()
                val hasActions = labels.contains("c2pa.actions")
                val hasCustom = labels.contains("com.example.test")
                // Two Actions assertions should deduplicate to one label
                val isDistinct = labels.size == 2

                val success = hasActions && hasCustom && isDistinct

                TestResult(
                    "ManifestDefinition assertionLabels",
                    success,
                    if (success) {
                        "assertionLabels returns distinct base labels"
                    } else {
                        "assertionLabels failed"
                    },
                    "Labels: $labels (expected 2 distinct)",
                )
            } catch (e: Exception) {
                TestResult(
                    "ManifestDefinition assertionLabels",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testManifestToPrettyJson(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestDefinition toPrettyJson") {
            try {
                val manifest = ManifestDefinition(
                    title = "Pretty Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion(action = PredefinedAction.CREATED)),
                        ),
                    ),
                )

                val compact = manifest.toJson()
                val pretty = manifest.toPrettyJson()

                // Pretty JSON should be longer (has indentation/newlines)
                val isLonger = pretty.length > compact.length
                // Pretty JSON should have newlines
                val hasNewlines = pretty.contains("\n")
                // Both should parse to equivalent manifests
                val compactParsed = ManifestDefinition.fromJson(compact)
                val prettyParsed = ManifestDefinition.fromJson(pretty)
                val equivalent = compactParsed == prettyParsed

                val success = isLonger && hasNewlines && equivalent

                TestResult(
                    "ManifestDefinition toPrettyJson",
                    success,
                    if (success) {
                        "toPrettyJson produces formatted, parseable output"
                    } else {
                        "toPrettyJson failed"
                    },
                    "Compact: ${compact.length} chars, Pretty: ${pretty.length} chars, " +
                        "Has newlines: $hasNewlines, Equivalent: $equivalent",
                )
            } catch (e: Exception) {
                TestResult(
                    "ManifestDefinition toPrettyJson",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testIptcPhotoMetadata(): TestResult = withContext(Dispatchers.IO) {
        runTest("AssertionDefinition IptcPhotoMetadata") {
            try {
                val iptcData = mapOf<String, JsonElement>(
                    "dc:creator" to JsonPrimitive("Test Author"),
                    "dc:description" to JsonPrimitive("A test image"),
                    "Iptc4xmpCore:Location" to JsonPrimitive("Test City"),
                )

                val manifest = ManifestDefinition(
                    title = "IPTC Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.IptcPhotoMetadata(data = iptcData),
                    ),
                )

                val jsonString = manifest.toJson()
                val parsed = ManifestDefinition.fromJson(jsonString)

                val iptcAssertion = parsed.assertions.firstOrNull()
                val isIptc = iptcAssertion is AssertionDefinition.IptcPhotoMetadata
                val hasCreator = isIptc && jsonString.contains("Test Author")
                val hasDescription = isIptc && jsonString.contains("A test image")
                val hasLabel = jsonString.contains("stds.iptc.photo-metadata")

                val success = isIptc && hasCreator && hasDescription && hasLabel

                TestResult(
                    "AssertionDefinition IptcPhotoMetadata",
                    success,
                    if (success) {
                        "IptcPhotoMetadata serializes and deserializes"
                    } else {
                        "IptcPhotoMetadata round-trip failed"
                    },
                    "Is IPTC: $isIptc, Has creator: $hasCreator, Has description: $hasDescription, " +
                        "Has label: $hasLabel",
                )
            } catch (e: Exception) {
                TestResult(
                    "AssertionDefinition IptcPhotoMetadata",
                    false,
                    "Exception: ${e.message}",
                    e.toString(),
                )
            }
        }
    }

    /**
     * Tests ManifestValidator custom assertion label validation and typo detection.
     */
    suspend fun testCustomAssertionLabelValidation(): TestResult = withContext(Dispatchers.IO) {
        runTest("Custom Assertion Label Validation") {
            try {
                val errors = mutableListOf<String>()

                // Custom assertion with valid namespace should NOT warn about format
                val validNamespace = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.custom(
                            "com.example.test",
                            buildJsonObject { put("key", "value") },
                        ),
                    ),
                )
                val validResult = ManifestValidator.validate(validNamespace)
                if (validResult.warnings.any { it.contains("namespaced format") }) {
                    errors.add("Namespaced label 'com.example.test' should not trigger format warning")
                }

                // Custom assertion WITHOUT namespace should warn
                val noNamespace = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.custom(
                            "test",
                            buildJsonObject { put("key", "value") },
                        ),
                    ),
                )
                val noNsResult = ManifestValidator.validate(noNamespace)
                if (!noNsResult.warnings.any { it.contains("namespaced format") }) {
                    errors.add("Non-namespaced label 'test' should trigger format warning")
                }

                // Typo: c2pa.action -> c2pa.actions
                val typo1 = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.custom(
                            "c2pa.action",
                            buildJsonObject { put("key", "value") },
                        ),
                    ),
                )
                val typo1Result = ManifestValidator.validate(typo1)
                if (!typo1Result.warnings.any { it.contains("c2pa.actions") && it.contains("typo") }) {
                    errors.add("Typo 'c2pa.action' should suggest 'c2pa.actions'")
                }

                // Typo: stds.iptc -> stds.iptc.photo-metadata
                val typo2 = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.custom(
                            "stds.iptc",
                            buildJsonObject { put("key", "value") },
                        ),
                    ),
                )
                val typo2Result = ManifestValidator.validate(typo2)
                if (!typo2Result.warnings.any { it.contains("stds.iptc.photo-metadata") }) {
                    errors.add("Typo 'stds.iptc' should suggest 'stds.iptc.photo-metadata'")
                }

                // Typo: cawg.training -> cawg.training-mining
                val typo3 = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.custom(
                            "cawg.training",
                            buildJsonObject { put("key", "value") },
                        ),
                    ),
                )
                val typo3Result = ManifestValidator.validate(typo3)
                if (!typo3Result.warnings.any { it.contains("cawg.training-mining") }) {
                    errors.add("Typo 'cawg.training' should suggest 'cawg.training-mining'")
                }

                // Standard assertion type (Actions) should NOT trigger custom label warnings
                val standard = ManifestDefinition(
                    title = "Test",
                    claimGeneratorInfo = listOf(ClaimGeneratorInfo(name = "test")),
                    assertions = listOf(
                        AssertionDefinition.actions(
                            listOf(ActionAssertion.created(DigitalSourceType.DIGITAL_CAPTURE)),
                        ),
                    ),
                )
                val standardResult = ManifestValidator.validate(standard)
                if (standardResult.warnings.any { it.contains("namespaced") || it.contains("typo") }) {
                    errors.add("Standard Actions assertion should not trigger label warnings")
                }

                // Exercise validateAndLog() path
                val logResult = ManifestValidator.validateAndLog(validNamespace)
                if (logResult.hasErrors()) {
                    errors.add("validateAndLog returned unexpected errors: ${logResult.errors}")
                }

                val success = errors.isEmpty()
                TestResult(
                    "Custom Assertion Label Validation",
                    success,
                    if (success) "All custom label validation cases pass" else "Custom label validation failures",
                    errors.joinToString("\n"),
                )
            } catch (e: Exception) {
                TestResult(
                    "Custom Assertion Label Validation",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests ImageRegionType.toTypeString() for all 16 enum values.
     */
    suspend fun testImageRegionTypeToTypeString(): TestResult = withContext(Dispatchers.IO) {
        runTest("ImageRegionType toTypeString") {
            try {
                val errors = mutableListOf<String>()
                val prefix = "http://cv.iptc.org/newscodes/imageregiontype/"

                ImageRegionType.entries.forEach { type ->
                    val url = type.toTypeString()
                    if (!url.startsWith(prefix)) {
                        errors.add("${type.name}: '$url' does not start with IPTC prefix")
                    }
                }

                // Spot-check specific mappings
                val spotChecks = mapOf(
                    ImageRegionType.HUMAN to "${prefix}human",
                    ImageRegionType.FACE to "${prefix}face",
                    ImageRegionType.BODY_PART to "${prefix}bodyPart",
                    ImageRegionType.VISIBLE_CODE to "${prefix}visibleCode",
                    ImageRegionType.GEO_FEATURE to "${prefix}geoFeature",
                )
                spotChecks.forEach { (type, expected) ->
                    val actual = type.toTypeString()
                    if (actual != expected) {
                        errors.add("${type.name}: expected '$expected', got '$actual'")
                    }
                }

                val success = errors.isEmpty()
                TestResult(
                    "ImageRegionType toTypeString",
                    success,
                    if (success) {
                        "All ${ImageRegionType.entries.size} toTypeString values are correct"
                    } else {
                        "toTypeString failures"
                    },
                    errors.joinToString("\n"),
                )
            } catch (e: Exception) {
                TestResult(
                    "ImageRegionType toTypeString",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

    /**
     * Tests StandardAssertionLabel.serialName() for all enum values.
     */
    suspend fun testStandardAssertionLabelSerialNames(): TestResult = withContext(Dispatchers.IO) {
        runTest("StandardAssertionLabel serialNames") {
            try {
                val errors = mutableListOf<String>()

                // Every serialName() should be non-empty
                StandardAssertionLabel.entries.forEach { label ->
                    val name = label.serialName()
                    if (name.isBlank()) {
                        errors.add("${label.name}: serialName() is blank")
                    }
                }

                // Spot-check critical values
                val spotChecks = mapOf(
                    StandardAssertionLabel.ACTIONS to "c2pa.actions",
                    StandardAssertionLabel.ACTIONS_V2 to "c2pa.actions.v2",
                    StandardAssertionLabel.EXIF to "stds.exif",
                    StandardAssertionLabel.CREATIVE_WORK to "stds.schema-org.CreativeWork",
                    StandardAssertionLabel.ISO_LOCATION to "stds.iso.location.v1",
                    StandardAssertionLabel.CAWG_AI_TRAINING to "cawg.training-mining",
                    StandardAssertionLabel.FONT_INFO to "font.info",
                    StandardAssertionLabel.THUMBNAIL_CLAIM to "c2pa.thumbnail.claim",
                    StandardAssertionLabel.HASH_BMFF to "c2pa.hash.bmff",
                    StandardAssertionLabel.DEPTHMAP_GDEPTH to "c2pa.depthmap.GDepth",
                    StandardAssertionLabel.TRAINING_MINING to "c2pa.training-mining",
                    StandardAssertionLabel.CAWG_METADATA to "cawg.metadata",
                )
                spotChecks.forEach { (label, expected) ->
                    val actual = label.serialName()
                    if (actual != expected) {
                        errors.add("${label.name}: expected '$expected', got '$actual'")
                    }
                }

                val success = errors.isEmpty()
                TestResult(
                    "StandardAssertionLabel serialNames",
                    success,
                    if (success) {
                        "All ${StandardAssertionLabel.entries.size} serialName values are correct"
                    } else {
                        "serialName failures"
                    },
                    errors.joinToString("\n"),
                )
            } catch (e: Exception) {
                TestResult(
                    "StandardAssertionLabel serialNames",
                    false,
                    "Error: ${e.message}",
                    e.stackTraceToString(),
                )
            }
        }
    }

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
