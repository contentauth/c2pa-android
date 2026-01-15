package org.contentauth.c2pa.test.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.manifest.Action
import org.contentauth.c2pa.manifest.ActionChange
import org.contentauth.c2pa.manifest.C2PAActions
import org.contentauth.c2pa.manifest.C2PAFormats
import org.contentauth.c2pa.manifest.C2PARelationships
import org.contentauth.c2pa.manifest.ClaimGenerator
import org.contentauth.c2pa.manifest.DigitalSourceTypes
import org.contentauth.c2pa.manifest.Ingredient
import org.contentauth.c2pa.manifest.ManifestBuilder
import org.contentauth.c2pa.manifest.SoftwareAgent
import org.contentauth.c2pa.manifest.Thumbnail
import org.json.JSONObject

/**
 * Unit tests for ManifestBuilder class
 * Tests all builder methods and JSON output structure
 */
abstract class ManifestBuilderUnitTests : TestBase() {

    // ==================== Data Class Tests ====================

    suspend fun testClaimGeneratorDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("ClaimGenerator data class") {
            val generator = ClaimGenerator("TestApp", "1.0.0", "icon.png")

            val success = generator.name == "TestApp" &&
                    generator.version == "1.0.0" &&
                    generator.icon == "icon.png"

            TestResult(
                "ClaimGenerator data class",
                success,
                if (success) "ClaimGenerator properties correct" else "ClaimGenerator properties incorrect"
            )
        }
    }

    suspend fun testClaimGeneratorWithoutIcon(): TestResult = withContext(Dispatchers.IO) {
        runTest("ClaimGenerator without icon") {
            val generator = ClaimGenerator("TestApp", "2.0.0")

            val success = generator.name == "TestApp" &&
                    generator.version == "2.0.0" &&
                    generator.icon == null

            TestResult(
                "ClaimGenerator without icon",
                success,
                if (success) "ClaimGenerator default icon is null" else "ClaimGenerator default icon should be null"
            )
        }
    }

    suspend fun testIngredientDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("Ingredient data class") {
            val thumbnail = Thumbnail(C2PAFormats.JPEG, "thumb.jpg")
            val ingredient = Ingredient(
                title = "Test Image",
                format = C2PAFormats.JPEG,
                documentId = "test-doc-id",
                provenance = "test-provenance",
                hash = "abc123",
                relationship = C2PARelationships.PARENT_OF,
                validationStatus = listOf("valid"),
                thumbnail = thumbnail
            )

            val success = ingredient.title == "Test Image" &&
                    ingredient.format == C2PAFormats.JPEG &&
                    ingredient.documentId == "test-doc-id" &&
                    ingredient.provenance == "test-provenance" &&
                    ingredient.hash == "abc123" &&
                    ingredient.relationship == C2PARelationships.PARENT_OF &&
                    ingredient.validationStatus.contains("valid") &&
                    ingredient.thumbnail == thumbnail

            TestResult(
                "Ingredient data class",
                success,
                if (success) "Ingredient properties correct" else "Ingredient properties incorrect"
            )
        }
    }

    suspend fun testIngredientDefaults(): TestResult = withContext(Dispatchers.IO) {
        runTest("Ingredient defaults") {
            val ingredient = Ingredient(format = C2PAFormats.PNG)

            val success = ingredient.title == null &&
                    ingredient.format == C2PAFormats.PNG &&
                    ingredient.documentId == null &&
                    ingredient.provenance == null &&
                    ingredient.hash == null &&
                    ingredient.relationship == "parentOf" &&
                    ingredient.validationStatus.isEmpty() &&
                    ingredient.thumbnail == null

            TestResult(
                "Ingredient defaults",
                success,
                if (success) "Ingredient defaults correct" else "Ingredient defaults incorrect"
            )
        }
    }

    suspend fun testThumbnailDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("Thumbnail data class") {
            val thumbnail = Thumbnail(
                format = C2PAFormats.PNG,
                identifier = "thumbnail.png",
                contentType = "image/png"
            )

            val success = thumbnail.format == C2PAFormats.PNG &&
                    thumbnail.identifier == "thumbnail.png" &&
                    thumbnail.contentType == "image/png"

            TestResult(
                "Thumbnail data class",
                success,
                if (success) "Thumbnail properties correct" else "Thumbnail properties incorrect"
            )
        }
    }

    suspend fun testThumbnailDefaultContentType(): TestResult = withContext(Dispatchers.IO) {
        runTest("Thumbnail default content type") {
            val thumbnail = Thumbnail(
                format = C2PAFormats.JPEG,
                identifier = "thumb.jpg"
            )

            val success = thumbnail.contentType == "image/jpeg"

            TestResult(
                "Thumbnail default content type",
                success,
                if (success) "Default content type is image/jpeg" else "Default content type should be image/jpeg"
            )
        }
    }

    suspend fun testActionDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("Action data class") {
            val softwareAgent = SoftwareAgent("TestApp", "1.0", "Android")
            val changes = listOf(ActionChange("brightness", "Increased by 10%"))
            val params = mapOf("intensity" to 10)

            val action = Action(
                action = C2PAActions.EDITED,
                whenTimestamp = "2024-01-01T00:00:00Z",
                softwareAgent = softwareAgent,
                changes = changes,
                reason = "User edit",
                parameters = params,
                digitalSourceType = DigitalSourceTypes.HUMAN_EDITS
            )

            val success = action.action == C2PAActions.EDITED &&
                    action.whenTimestamp == "2024-01-01T00:00:00Z" &&
                    action.softwareAgent == softwareAgent &&
                    action.changes == changes &&
                    action.reason == "User edit" &&
                    action.parameters == params &&
                    action.digitalSourceType == DigitalSourceTypes.HUMAN_EDITS

            TestResult(
                "Action data class",
                success,
                if (success) "Action properties correct" else "Action properties incorrect"
            )
        }
    }

    suspend fun testActionDefaults(): TestResult = withContext(Dispatchers.IO) {
        runTest("Action defaults") {
            val action = Action(action = C2PAActions.CREATED)

            val success = action.action == C2PAActions.CREATED &&
                    action.whenTimestamp == null &&
                    action.softwareAgent == null &&
                    action.changes.isEmpty() &&
                    action.reason == null &&
                    action.parameters.isEmpty() &&
                    action.digitalSourceType == null

            TestResult(
                "Action defaults",
                success,
                if (success) "Action defaults correct" else "Action defaults incorrect"
            )
        }
    }

    suspend fun testSoftwareAgentDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("SoftwareAgent data class") {
            val agent = SoftwareAgent(
                name = "MyApp",
                version = "2.0.1",
                operatingSystem = "Android 14"
            )

            val success = agent.name == "MyApp" &&
                    agent.version == "2.0.1" &&
                    agent.operatingSystem == "Android 14"

            TestResult(
                "SoftwareAgent data class",
                success,
                if (success) "SoftwareAgent properties correct" else "SoftwareAgent properties incorrect"
            )
        }
    }

    suspend fun testActionChangeDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionChange data class") {
            val change = ActionChange(
                field = "contrast",
                description = "Decreased by 5%"
            )

            val success = change.field == "contrast" &&
                    change.description == "Decreased by 5%"

            TestResult(
                "ActionChange data class",
                success,
                if (success) "ActionChange properties correct" else "ActionChange properties incorrect"
            )
        }
    }

    // ==================== ManifestBuilder Method Tests ====================

    suspend fun testManifestBuilderChaining(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder method chaining") {
            val builder = ManifestBuilder()
                .title("Test")
                .format(C2PAFormats.JPEG)
                .claimGenerator("App", "1.0")
                .documentId("doc-id")
                .producer("Producer")
                .timestampAuthorityUrl("http://ts.example.com")

            // If chaining works, builder is returned and build() can be called
            val json = builder.build()
            val success = json.has("claim_version")

            TestResult(
                "ManifestBuilder method chaining",
                success,
                if (success) "Method chaining works correctly" else "Method chaining failed"
            )
        }
    }

    suspend fun testManifestBuilderClaimGenerator(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder claimGenerator") {
            val json = ManifestBuilder()
                .claimGenerator("TestApp", "1.0.0", "icon.png")
                .build()

            val claimGenArray = json.optJSONArray("claim_generator_info")
            val firstGen = claimGenArray?.optJSONObject(0)

            val success = firstGen?.getString("name") == "TestApp" &&
                    firstGen?.getString("version") == "1.0.0" &&
                    firstGen?.getString("icon") == "icon.png"

            TestResult(
                "ManifestBuilder claimGenerator",
                success,
                if (success) "Claim generator with icon correct" else "Claim generator output incorrect",
                json.toString(2)
            )
        }
    }

    suspend fun testManifestBuilderClaimGeneratorWithoutIcon(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder claimGenerator without icon") {
            val json = ManifestBuilder()
                .claimGenerator("TestApp", "2.0.0")
                .build()

            val claimGenArray = json.optJSONArray("claim_generator_info")
            val firstGen = claimGenArray?.optJSONObject(0)

            val success = firstGen?.getString("name") == "TestApp" &&
                    firstGen?.getString("version") == "2.0.0" &&
                    !firstGen.has("icon")

            TestResult(
                "ManifestBuilder claimGenerator without icon",
                success,
                if (success) "Claim generator without icon correct" else "Should not have icon field",
                json.toString(2)
            )
        }
    }

    suspend fun testManifestBuilderFormat(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder format") {
            val json = ManifestBuilder()
                .format(C2PAFormats.PNG)
                .build()

            val success = json.getString("format") == C2PAFormats.PNG

            TestResult(
                "ManifestBuilder format",
                success,
                if (success) "Format set correctly" else "Format not set correctly"
            )
        }
    }

    suspend fun testManifestBuilderTitle(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder title") {
            val json = ManifestBuilder()
                .title("My Test Image")
                .build()

            val success = json.getString("title") == "My Test Image"

            TestResult(
                "ManifestBuilder title",
                success,
                if (success) "Title set correctly" else "Title not set correctly"
            )
        }
    }

    suspend fun testManifestBuilderDocumentId(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder documentId") {
            val json = ManifestBuilder()
                .documentId("doc-123")
                .build()

            val success = json.getString("documentID") == "doc-123"

            TestResult(
                "ManifestBuilder documentId",
                success,
                if (success) "DocumentId set correctly" else "DocumentId not set correctly"
            )
        }
    }

    suspend fun testManifestBuilderProducer(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder producer") {
            val json = ManifestBuilder()
                .producer("Test Producer")
                .build()

            val success = json.getString("producer") == "Test Producer"

            TestResult(
                "ManifestBuilder producer",
                success,
                if (success) "Producer set correctly" else "Producer not set correctly"
            )
        }
    }

    suspend fun testManifestBuilderTimestampAuthority(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder timestampAuthorityUrl") {
            val json = ManifestBuilder()
                .timestampAuthorityUrl("http://timestamp.example.com")
                .build()

            val success = json.getString("ta_url") == "http://timestamp.example.com"

            TestResult(
                "ManifestBuilder timestampAuthorityUrl",
                success,
                if (success) "TA URL set correctly" else "TA URL not set correctly"
            )
        }
    }

    suspend fun testManifestBuilderAddIngredient(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder addIngredient") {
            val ingredient = Ingredient(
                title = "Source Image",
                format = C2PAFormats.JPEG,
                documentId = "ingredient-doc",
                relationship = C2PARelationships.PARENT_OF
            )

            val json = ManifestBuilder()
                .addIngredient(ingredient)
                .build()

            val ingredients = json.optJSONArray("ingredients")
            val firstIngredient = ingredients?.optJSONObject(0)

            val success = firstIngredient?.getString("title") == "Source Image" &&
                    firstIngredient?.getString("format") == C2PAFormats.JPEG &&
                    firstIngredient?.getString("instanceID") == "ingredient-id" &&
                    firstIngredient?.getString("documentID") == "ingredient-doc" &&
                    firstIngredient?.getString("relationship") == C2PARelationships.PARENT_OF

            TestResult(
                "ManifestBuilder addIngredient",
                success,
                if (success) "Ingredient added correctly" else "Ingredient not added correctly",
                json.toString(2)
            )
        }
    }

    suspend fun testManifestBuilderMultipleIngredients(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder multiple ingredients") {
            val json = ManifestBuilder()
                .addIngredient(Ingredient(title = "Image 1", format = C2PAFormats.JPEG))
                .addIngredient(Ingredient(title = "Image 2", format = C2PAFormats.PNG))
                .addIngredient(Ingredient(title = "Image 3", format = C2PAFormats.WEBP))
                .build()

            val ingredients = json.optJSONArray("ingredients")
            val success = ingredients?.length() == 3 &&
                    ingredients.getJSONObject(0).getString("title") == "Image 1" &&
                    ingredients.getJSONObject(1).getString("title") == "Image 2" &&
                    ingredients.getJSONObject(2).getString("title") == "Image 3"

            TestResult(
                "ManifestBuilder multiple ingredients",
                success,
                if (success) "Multiple ingredients added correctly" else "Multiple ingredients not added correctly"
            )
        }
    }

    suspend fun testManifestBuilderIngredientWithThumbnail(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder ingredient with thumbnail") {
            val thumbnail = Thumbnail(C2PAFormats.JPEG, "ingredient-thumb.jpg")
            val ingredient = Ingredient(
                title = "Source",
                format = C2PAFormats.JPEG,
                thumbnail = thumbnail
            )

            val json = ManifestBuilder()
                .addIngredient(ingredient)
                .build()

            val ingredients = json.optJSONArray("ingredients")
            val thumbJson = ingredients?.optJSONObject(0)?.optJSONObject("thumbnail")

            val success = thumbJson?.getString("format") == C2PAFormats.JPEG &&
                    thumbJson?.getString("identifier") == "ingredient-thumb.jpg"

            TestResult(
                "ManifestBuilder ingredient with thumbnail",
                success,
                if (success) "Ingredient thumbnail correct" else "Ingredient thumbnail incorrect",
                json.toString(2)
            )
        }
    }

    suspend fun testManifestBuilderIngredientWithValidationStatus(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder ingredient with validation status") {
            val ingredient = Ingredient(
                title = "Source",
                format = C2PAFormats.JPEG,
                validationStatus = listOf("valid", "signed")
            )

            val json = ManifestBuilder()
                .addIngredient(ingredient)
                .build()

            val ingredients = json.optJSONArray("ingredients")
            val statusArray = ingredients?.optJSONObject(0)?.optJSONArray("validationStatus")

            val success = statusArray?.length() == 2 &&
                    statusArray.getString(0) == "valid" &&
                    statusArray.getString(1) == "signed"

            TestResult(
                "ManifestBuilder ingredient with validation status",
                success,
                if (success) "Validation status correct" else "Validation status incorrect"
            )
        }
    }

    suspend fun testManifestBuilderAddAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder addAction") {
            val action = Action(
                action = C2PAActions.CREATED,
                whenTimestamp = "2024-01-01T12:00:00Z"
            )

            val json = ManifestBuilder()
                .addAction(action)
                .build()

            val assertions = json.optJSONArray("assertions")
            val actionsAssertion = assertions?.optJSONObject(0)

            val success = actionsAssertion?.getString("label") == "c2pa.actions" &&
                    actionsAssertion?.getJSONObject("data")
                        ?.getJSONArray("actions")
                        ?.getJSONObject(0)
                        ?.getString("action") == C2PAActions.CREATED

            TestResult(
                "ManifestBuilder addAction",
                success,
                if (success) "Action added correctly" else "Action not added correctly",
                json.toString(2)
            )
        }
    }

    suspend fun testManifestBuilderActionWithSoftwareAgent(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder action with software agent") {
            val agent = SoftwareAgent("TestApp", "1.0", "Android 14")
            val action = Action(
                action = C2PAActions.EDITED,
                whenTimestamp = "2024-01-01T12:00:00Z",
                softwareAgent = agent
            )

            val json = ManifestBuilder()
                .addAction(action)
                .build()

            val assertions = json.optJSONArray("assertions")
            val actionJson = assertions?.optJSONObject(0)
                ?.getJSONObject("data")
                ?.getJSONArray("actions")
                ?.getJSONObject(0)

            val agentJson = actionJson?.optJSONObject("softwareAgent")

            val success = agentJson?.getString("name") == "TestApp" &&
                    agentJson?.getString("version") == "1.0" &&
                    agentJson?.getString("operating_system") == "Android 14"

            TestResult(
                "ManifestBuilder action with software agent",
                success,
                if (success) "Software agent correct" else "Software agent incorrect",
                json.toString(2)
            )
        }
    }

    suspend fun testManifestBuilderActionWithChanges(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder action with changes") {
            val changes = listOf(
                ActionChange("brightness", "Increased"),
                ActionChange("contrast", "Decreased")
            )
            val action = Action(
                action = C2PAActions.EDITED,
                changes = changes
            )

            val json = ManifestBuilder()
                .addAction(action)
                .build()

            val actionJson = json.optJSONArray("assertions")
                ?.optJSONObject(0)
                ?.getJSONObject("data")
                ?.getJSONArray("actions")
                ?.getJSONObject(0)

            val changesArray = actionJson?.optJSONArray("changes")

            val success = changesArray?.length() == 2 &&
                    changesArray.getJSONObject(0).getString("field") == "brightness" &&
                    changesArray.getJSONObject(1).getString("field") == "contrast"

            TestResult(
                "ManifestBuilder action with changes",
                success,
                if (success) "Action changes correct" else "Action changes incorrect"
            )
        }
    }

    suspend fun testManifestBuilderActionWithParameters(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder action with parameters") {
            val params = mapOf(
                "filter_name" to "Vintage",
                "intensity" to 75
            )
            val action = Action(
                action = C2PAActions.FILTERED,
                parameters = params
            )

            val json = ManifestBuilder()
                .addAction(action)
                .build()

            val actionJson = json.optJSONArray("assertions")
                ?.optJSONObject(0)
                ?.getJSONObject("data")
                ?.getJSONArray("actions")
                ?.getJSONObject(0)

            val paramsJson = actionJson?.optJSONObject("parameters")

            val success = paramsJson?.getString("filter_name") == "Vintage" &&
                    paramsJson?.getInt("intensity") == 75

            TestResult(
                "ManifestBuilder action with parameters",
                success,
                if (success) "Action parameters correct" else "Action parameters incorrect"
            )
        }
    }

    suspend fun testManifestBuilderActionWithDigitalSourceType(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder action with digital source type") {
            val action = Action(
                action = C2PAActions.CREATED,
                digitalSourceType = DigitalSourceTypes.DIGITAL_CAPTURE
            )

            val json = ManifestBuilder()
                .addAction(action)
                .build()

            val actionJson = json.optJSONArray("assertions")
                ?.optJSONObject(0)
                ?.getJSONObject("data")
                ?.getJSONArray("actions")
                ?.getJSONObject(0)

            val success = actionJson?.getString("digitalSourceType") == DigitalSourceTypes.DIGITAL_CAPTURE

            TestResult(
                "ManifestBuilder action with digital source type",
                success,
                if (success) "Digital source type correct" else "Digital source type incorrect"
            )
        }
    }

    suspend fun testManifestBuilderActionWithReason(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder action with reason") {
            val action = Action(
                action = C2PAActions.RECOMPRESSED,
                reason = "Social media optimization"
            )

            val json = ManifestBuilder()
                .addAction(action)
                .build()

            val actionJson = json.optJSONArray("assertions")
                ?.optJSONObject(0)
                ?.getJSONObject("data")
                ?.getJSONArray("actions")
                ?.getJSONObject(0)

            val success = actionJson?.getString("reason") == "Social media optimization"

            TestResult(
                "ManifestBuilder action with reason",
                success,
                if (success) "Action reason correct" else "Action reason incorrect"
            )
        }
    }

    suspend fun testManifestBuilderMultipleActions(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder multiple actions") {
            val json = ManifestBuilder()
                .addAction(Action(action = C2PAActions.OPENED))
                .addAction(Action(action = C2PAActions.EDITED))
                .addAction(Action(action = C2PAActions.RESIZED))
                .build()

            val actionsArray = json.optJSONArray("assertions")
                ?.optJSONObject(0)
                ?.getJSONObject("data")
                ?.getJSONArray("actions")

            val success = actionsArray?.length() == 3 &&
                    actionsArray.getJSONObject(0).getString("action") == C2PAActions.OPENED &&
                    actionsArray.getJSONObject(1).getString("action") == C2PAActions.EDITED &&
                    actionsArray.getJSONObject(2).getString("action") == C2PAActions.RESIZED

            TestResult(
                "ManifestBuilder multiple actions",
                success,
                if (success) "Multiple actions correct" else "Multiple actions incorrect"
            )
        }
    }

    suspend fun testManifestBuilderAddThumbnail(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder addThumbnail") {
            val thumbnail = Thumbnail(C2PAFormats.JPEG, "manifest-thumb.jpg")

            val json = ManifestBuilder()
                .addThumbnail(thumbnail)
                .build()

            val thumbJson = json.optJSONObject("thumbnail")

            val success = thumbJson?.getString("format") == C2PAFormats.JPEG &&
                    thumbJson?.getString("identifier") == "manifest-thumb.jpg"

            TestResult(
                "ManifestBuilder addThumbnail",
                success,
                if (success) "Thumbnail added correctly" else "Thumbnail not added correctly"
            )
        }
    }

    suspend fun testManifestBuilderAddAssertionWithJsonObject(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder addAssertion with JSONObject") {
            val customData = JSONObject().apply {
                put("custom_field", "custom_value")
                put("number_field", 42)
            }

            val json = ManifestBuilder()
                .addAssertion("custom.assertion", customData)
                .build()

            val assertions = json.optJSONArray("assertions")
            var foundAssertion: JSONObject? = null
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "custom.assertion") {
                    foundAssertion = assertion
                    break
                }
            }

            val success = foundAssertion?.getJSONObject("data")?.getString("custom_field") == "custom_value" &&
                    foundAssertion?.getJSONObject("data")?.getInt("number_field") == 42

            TestResult(
                "ManifestBuilder addAssertion with JSONObject",
                success,
                if (success) "JSONObject assertion correct" else "JSONObject assertion incorrect"
            )
        }
    }

    suspend fun testManifestBuilderAddAssertionWithString(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder addAssertion with String") {
            val json = ManifestBuilder()
                .addAssertion("string.assertion", "test string value")
                .build()

            val assertions = json.optJSONArray("assertions")
            var foundAssertion: JSONObject? = null
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "string.assertion") {
                    foundAssertion = assertion
                    break
                }
            }

            val success = foundAssertion?.getString("data") == "test string value"

            TestResult(
                "ManifestBuilder addAssertion with String",
                success,
                if (success) "String assertion correct" else "String assertion incorrect"
            )
        }
    }

    suspend fun testManifestBuilderAddAssertionWithNumber(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder addAssertion with Number") {
            val json = ManifestBuilder()
                .addAssertion("number.assertion", 12345)
                .build()

            val assertions = json.optJSONArray("assertions")
            var foundAssertion: JSONObject? = null
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "number.assertion") {
                    foundAssertion = assertion
                    break
                }
            }

            val success = foundAssertion?.getInt("data") == 12345

            TestResult(
                "ManifestBuilder addAssertion with Number",
                success,
                if (success) "Number assertion correct" else "Number assertion incorrect"
            )
        }
    }

    suspend fun testManifestBuilderAddAssertionWithBoolean(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder addAssertion with Boolean") {
            val json = ManifestBuilder()
                .addAssertion("bool.assertion", true)
                .build()

            val assertions = json.optJSONArray("assertions")
            var foundAssertion: JSONObject? = null
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "bool.assertion") {
                    foundAssertion = assertion
                    break
                }
            }

            val success = foundAssertion?.getBoolean("data") == true

            TestResult(
                "ManifestBuilder addAssertion with Boolean",
                success,
                if (success) "Boolean assertion correct" else "Boolean assertion incorrect"
            )
        }
    }

    // ==================== Build Output Tests ====================

    suspend fun testManifestBuilderBuildClaimVersion(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder build claim_version") {
            val json = ManifestBuilder().build()

            val success = json.getInt("claim_version") == 1

            TestResult(
                "ManifestBuilder build claim_version",
                success,
                if (success) "claim_version is 1" else "claim_version should be 1"
            )
        }
    }

    suspend fun testManifestBuilderBuildJson(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder buildJson") {
            val builder = ManifestBuilder()
                .title("Test")
                .format(C2PAFormats.JPEG)

            val jsonString = builder.buildJson()

            // Verify it's valid JSON and formatted (contains newlines)
            val success = try {
                val parsed = JSONObject(jsonString)
                parsed.has("title") && jsonString.contains("\n")
            } catch (e: Exception) {
                false
            }

            TestResult(
                "ManifestBuilder buildJson",
                success,
                if (success) "buildJson produces formatted JSON" else "buildJson output incorrect"
            )
        }
    }

    suspend fun testManifestBuilderEmptyAssertions(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder empty assertions") {
            val json = ManifestBuilder()
                .title("Test")
                .build()

            // Should not have assertions key if no assertions added
            val success = !json.has("assertions")

            TestResult(
                "ManifestBuilder empty assertions",
                success,
                if (success) "No assertions array when empty" else "Should not have assertions when empty"
            )
        }
    }

    suspend fun testManifestBuilderEmptyIngredients(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder empty ingredients") {
            val json = ManifestBuilder()
                .title("Test")
                .build()

            // Should not have ingredients key if no ingredients added
            val success = !json.has("ingredients")

            TestResult(
                "ManifestBuilder empty ingredients",
                success,
                if (success) "No ingredients array when empty" else "Should not have ingredients when empty"
            )
        }
    }

    suspend fun testManifestBuilderOptionalFieldsAbsent(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder optional fields absent when not set") {
            val json = ManifestBuilder().build()

            // These fields should not be present when not set
            val success = !json.has("format") &&
                    !json.has("title") &&
                    !json.has("documentID") &&
                    !json.has("producer") &&
                    !json.has("ta_url") &&
                    !json.has("thumbnail") &&
                    !json.has("claim_generator_info")

            TestResult(
                "ManifestBuilder optional fields absent when not set",
                success,
                if (success) "Optional fields correctly absent" else "Optional fields should be absent when not set",
                json.toString(2)
            )
        }
    }

    suspend fun testManifestBuilderCompleteManifest(): TestResult = withContext(Dispatchers.IO) {
        runTest("ManifestBuilder complete manifest") {
            val agent = SoftwareAgent("TestApp", "1.0", "Android")
            val thumbnail = Thumbnail(C2PAFormats.JPEG, "thumb.jpg")
            val ingredient = Ingredient(
                title = "Source",
                format = C2PAFormats.JPEG,
                relationship = C2PARelationships.PARENT_OF
            )
            val action = Action(
                action = C2PAActions.EDITED,
                whenTimestamp = "2024-01-01T00:00:00Z",
                softwareAgent = agent
            )

            val json = ManifestBuilder()
                .title("Complete Test")
                .format(C2PAFormats.JPEG)
                .claimGenerator("TestApp", "1.0.0")
                .documentId("test-document")
                .producer("Test Producer")
                .timestampAuthorityUrl("http://ts.example.com")
                .addThumbnail(thumbnail)
                .addIngredient(ingredient)
                .addAction(action)
                .addAssertion("custom.test", JSONObject().put("test", true))
                .build()

            val success = json.getInt("claim_version") == 1 &&
                    json.getString("title") == "Complete Test" &&
                    json.getString("format") == C2PAFormats.JPEG &&
                    json.getString("instanceID") == "test-instance" &&
                    json.getString("documentID") == "test-document" &&
                    json.getString("producer") == "Test Producer" &&
                    json.getString("ta_url") == "http://ts.example.com" &&
                    json.has("thumbnail") &&
                    json.has("ingredients") &&
                    json.has("assertions") &&
                    json.has("claim_generator_info")

            TestResult(
                "ManifestBuilder complete manifest",
                success,
                if (success) "Complete manifest has all fields" else "Complete manifest missing fields",
                json.toString(2)
            )
        }
    }
}
