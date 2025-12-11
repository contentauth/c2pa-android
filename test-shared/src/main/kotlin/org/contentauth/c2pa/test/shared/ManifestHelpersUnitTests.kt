package org.contentauth.c2pa.test.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.manifest.C2PAActions
import org.contentauth.c2pa.manifest.C2PAAssertionTypes
import org.contentauth.c2pa.manifest.C2PAFormats
import org.contentauth.c2pa.manifest.C2PARelationships
import org.contentauth.c2pa.manifest.CAWGIdentityTypes
import org.contentauth.c2pa.manifest.CAWGProviders
import org.contentauth.c2pa.manifest.DigitalSourceTypes
import org.contentauth.c2pa.manifest.IdentityProvider
import org.contentauth.c2pa.manifest.Ingredient
import org.contentauth.c2pa.manifest.ManifestHelpers
import org.contentauth.c2pa.manifest.VerifiedIdentity
import org.json.JSONObject

/**
 * Unit tests for ManifestHelpers factory methods
 * Tests all helper functions for creating manifest builders
 */
abstract class ManifestHelpersUnitTests : TestBase() {

    // ==================== createBasicImageManifest Tests ====================

    suspend fun testCreateBasicImageManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createBasicImageManifest minimal") {
            val builder = ManifestHelpers.createBasicImageManifest(title = "Test Image")
            val json = builder.build()

            val success = json.getString("title") == "Test Image" &&
                    json.getString("format") == C2PAFormats.JPEG &&
                    json.has("claim_generator_info")

            TestResult(
                "createBasicImageManifest minimal",
                success,
                if (success) "Basic image manifest created" else "Basic image manifest incorrect",
                json.toString(2)
            )
        }
    }

    suspend fun testCreateBasicImageManifestWithFormat(): TestResult = withContext(Dispatchers.IO) {
        runTest("createBasicImageManifest with format") {
            val builder = ManifestHelpers.createBasicImageManifest(
                title = "PNG Image",
                format = C2PAFormats.PNG
            )
            val json = builder.build()

            val success = json.getString("format") == C2PAFormats.PNG

            TestResult(
                "createBasicImageManifest with format",
                success,
                if (success) "Format set correctly" else "Format not set correctly"
            )
        }
    }

    suspend fun testCreateBasicImageManifestWithClaimGenerator(): TestResult = withContext(Dispatchers.IO) {
        runTest("createBasicImageManifest with claim generator") {
            val builder = ManifestHelpers.createBasicImageManifest(
                title = "Test",
                claimGeneratorName = "CustomApp",
                claimGeneratorVersion = "2.0.0"
            )
            val json = builder.build()
            val claimGenInfo = json.optJSONArray("claim_generator_info")?.optJSONObject(0)

            val success = claimGenInfo?.getString("name") == "CustomApp" &&
                    claimGenInfo?.getString("version") == "2.0.0"

            TestResult(
                "createBasicImageManifest with claim generator",
                success,
                if (success) "Claim generator set correctly" else "Claim generator not set correctly"
            )
        }
    }

    suspend fun testCreateBasicImageManifestWithTimestampAuthority(): TestResult = withContext(Dispatchers.IO) {
        runTest("createBasicImageManifest with timestamp authority") {
            val builder = ManifestHelpers.createBasicImageManifest(
                title = "Test",
                timestampAuthorityUrl = "http://timestamp.example.com"
            )
            val json = builder.build()

            val success = json.getString("ta_url") == "http://timestamp.example.com"

            TestResult(
                "createBasicImageManifest with timestamp authority",
                success,
                if (success) "Timestamp authority set correctly" else "Timestamp authority not set correctly"
            )
        }
    }

    suspend fun testCreateBasicImageManifestDefaultClaimGenerator(): TestResult = withContext(Dispatchers.IO) {
        runTest("createBasicImageManifest default claim generator") {
            val builder = ManifestHelpers.createBasicImageManifest(title = "Test")
            val json = builder.build()
            val claimGenInfo = json.optJSONArray("claim_generator_info")?.optJSONObject(0)

            val success = claimGenInfo?.getString("name") == "Android C2PA SDK" &&
                    claimGenInfo?.getString("version") == "1.0.0"

            TestResult(
                "createBasicImageManifest default claim generator",
                success,
                if (success) "Default claim generator correct" else "Default claim generator incorrect"
            )
        }
    }

    // ==================== createImageEditManifest Tests ====================

    suspend fun testCreateImageEditManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createImageEditManifest minimal") {
            val builder = ManifestHelpers.createImageEditManifest(
                title = "Edited Image",
                originalIngredientTitle = "Original.jpg"
            )
            val json = builder.build()

            val success = json.getString("title") == "Edited Image" &&
                    json.has("ingredients") &&
                    json.has("assertions")

            TestResult(
                "createImageEditManifest minimal",
                success,
                if (success) "Edit manifest created" else "Edit manifest incorrect",
                json.toString(2)
            )
        }
    }

    suspend fun testCreateImageEditManifestIngredient(): TestResult = withContext(Dispatchers.IO) {
        runTest("createImageEditManifest ingredient") {
            val builder = ManifestHelpers.createImageEditManifest(
                title = "Edited",
                originalIngredientTitle = "Original.jpg",
                originalFormat = C2PAFormats.PNG
            )
            val json = builder.build()
            val ingredients = json.optJSONArray("ingredients")
            val firstIngredient = ingredients?.optJSONObject(0)

            val success = firstIngredient?.getString("title") == "Original.jpg" &&
                    firstIngredient?.getString("format") == C2PAFormats.PNG &&
                    firstIngredient?.getString("relationship") == C2PARelationships.PARENT_OF

            TestResult(
                "createImageEditManifest ingredient",
                success,
                if (success) "Ingredient set correctly" else "Ingredient incorrect"
            )
        }
    }

    suspend fun testCreateImageEditManifestAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("createImageEditManifest action") {
            val builder = ManifestHelpers.createImageEditManifest(
                title = "Edited",
                originalIngredientTitle = "Original.jpg"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasEditAction = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        if (actions.getJSONObject(j).getString("action") == C2PAActions.EDITED) {
                            hasEditAction = true
                        }
                    }
                }
            }

            TestResult(
                "createImageEditManifest action",
                hasEditAction,
                if (hasEditAction) "Edit action present" else "Edit action missing"
            )
        }
    }

    // ==================== createPhotoManifest Tests ====================

    suspend fun testCreatePhotoManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createPhotoManifest minimal") {
            val builder = ManifestHelpers.createPhotoManifest(
                title = "Photo.jpg",
                deviceName = "Pixel 8"
            )
            val json = builder.build()

            val success = json.getString("title") == "Photo.jpg" &&
                    json.getString("format") == C2PAFormats.JPEG

            TestResult(
                "createPhotoManifest minimal",
                success,
                if (success) "Photo manifest created" else "Photo manifest incorrect"
            )
        }
    }

    suspend fun testCreatePhotoManifestWithAuthor(): TestResult = withContext(Dispatchers.IO) {
        runTest("createPhotoManifest with author") {
            val builder = ManifestHelpers.createPhotoManifest(
                title = "Photo.jpg",
                authorName = "John Photographer",
                deviceName = "Pixel 8"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasCreativeWork = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == C2PAAssertionTypes.CREATIVE_WORK) {
                    val author = assertion.getJSONObject("data").optJSONArray("author")?.optJSONObject(0)
                    if (author?.getString("name") == "John Photographer") {
                        hasCreativeWork = true
                    }
                }
            }

            TestResult(
                "createPhotoManifest with author",
                hasCreativeWork,
                if (hasCreativeWork) "Author attestation present" else "Author attestation missing"
            )
        }
    }

    suspend fun testCreatePhotoManifestWithLocation(): TestResult = withContext(Dispatchers.IO) {
        runTest("createPhotoManifest with location") {
            val location = ManifestHelpers.createLocation(37.7749, -122.4194, "San Francisco")
            val builder = ManifestHelpers.createPhotoManifest(
                title = "Photo.jpg",
                location = location,
                deviceName = "Pixel 8"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasLocation = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.assertion.metadata") {
                    val loc = assertion.getJSONObject("data").optJSONObject("location")
                    if (loc != null && loc.has("latitude")) {
                        hasLocation = true
                    }
                }
            }

            TestResult(
                "createPhotoManifest with location",
                hasLocation,
                if (hasLocation) "Location metadata present" else "Location metadata missing"
            )
        }
    }

    suspend fun testCreatePhotoManifestCreatedAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("createPhotoManifest created action") {
            val builder = ManifestHelpers.createPhotoManifest(
                title = "Photo.jpg",
                deviceName = "Pixel 8"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasCreatedWithDigitalCapture = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        val action = actions.getJSONObject(j)
                        if (action.getString("action") == C2PAActions.CREATED &&
                            action.optString("digitalSourceType") == DigitalSourceTypes.DIGITAL_CAPTURE) {
                            hasCreatedWithDigitalCapture = true
                        }
                    }
                }
            }

            TestResult(
                "createPhotoManifest created action",
                hasCreatedWithDigitalCapture,
                if (hasCreatedWithDigitalCapture) "Created action with digital capture" else "Missing created action"
            )
        }
    }

    // ==================== createVideoEditManifest Tests ====================

    suspend fun testCreateVideoEditManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createVideoEditManifest minimal") {
            val builder = ManifestHelpers.createVideoEditManifest(
                title = "Edited Video",
                originalIngredientTitle = "Original.mp4"
            )
            val json = builder.build()

            val success = json.getString("title") == "Edited Video" &&
                    json.getString("format") == C2PAFormats.MP4 &&
                    json.has("ingredients")

            TestResult(
                "createVideoEditManifest minimal",
                success,
                if (success) "Video edit manifest created" else "Video edit manifest incorrect"
            )
        }
    }

    suspend fun testCreateVideoEditManifestWithEditActions(): TestResult = withContext(Dispatchers.IO) {
        runTest("createVideoEditManifest with edit actions") {
            val editActions = listOf(C2PAActions.CROPPED, C2PAActions.RESIZED)
            val builder = ManifestHelpers.createVideoEditManifest(
                title = "Edited Video",
                originalIngredientTitle = "Original.mp4",
                editActions = editActions
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasCropped = false
            var hasResized = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        val actionName = actions.getJSONObject(j).getString("action")
                        if (actionName == C2PAActions.CROPPED) hasCropped = true
                        if (actionName == C2PAActions.RESIZED) hasResized = true
                    }
                }
            }

            val success = hasCropped && hasResized

            TestResult(
                "createVideoEditManifest with edit actions",
                success,
                if (success) "Edit actions present" else "Edit actions missing"
            )
        }
    }

    suspend fun testCreateVideoEditManifestOpenedAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("createVideoEditManifest opened action") {
            val builder = ManifestHelpers.createVideoEditManifest(
                title = "Edited",
                originalIngredientTitle = "Original.mp4"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasOpened = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        if (actions.getJSONObject(j).getString("action") == C2PAActions.OPENED) {
                            hasOpened = true
                        }
                    }
                }
            }

            TestResult(
                "createVideoEditManifest opened action",
                hasOpened,
                if (hasOpened) "Opened action present" else "Opened action missing"
            )
        }
    }

    // ==================== createCompositeManifest Tests ====================

    suspend fun testCreateCompositeManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createCompositeManifest minimal") {
            val ingredients = listOf(
                Ingredient(title = "Image 1", format = C2PAFormats.JPEG),
                Ingredient(title = "Image 2", format = C2PAFormats.PNG)
            )
            val builder = ManifestHelpers.createCompositeManifest(
                title = "Composite",
                ingredients = ingredients
            )
            val json = builder.build()

            val success = json.getString("title") == "Composite" &&
                    json.optJSONArray("ingredients")?.length() == 2

            TestResult(
                "createCompositeManifest minimal",
                success,
                if (success) "Composite manifest created" else "Composite manifest incorrect"
            )
        }
    }

    suspend fun testCreateCompositeManifestActions(): TestResult = withContext(Dispatchers.IO) {
        runTest("createCompositeManifest actions") {
            val ingredients = listOf(
                Ingredient(title = "Image 1", format = C2PAFormats.JPEG),
                Ingredient(title = "Image 2", format = C2PAFormats.PNG)
            )
            val builder = ManifestHelpers.createCompositeManifest(
                title = "Composite",
                ingredients = ingredients
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var createdCount = 0
            var placedCount = 0
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        val actionName = actions.getJSONObject(j).getString("action")
                        if (actionName == C2PAActions.CREATED) createdCount++
                        if (actionName == C2PAActions.PLACED) placedCount++
                    }
                }
            }

            // Should have 1 created action and 2 placed actions (one per ingredient)
            val success = createdCount == 1 && placedCount == 2

            TestResult(
                "createCompositeManifest actions",
                success,
                if (success) "Composite actions correct" else "Expected 1 created, 2 placed. Got $createdCount created, $placedCount placed"
            )
        }
    }

    // ==================== createScreenshotManifest Tests ====================

    suspend fun testCreateScreenshotManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createScreenshotManifest minimal") {
            val builder = ManifestHelpers.createScreenshotManifest(deviceName = "Pixel 8")
            val json = builder.build()

            val success = json.getString("title") == "Screenshot" &&
                    json.getString("format") == C2PAFormats.PNG &&
                    json.getString("producer") == "Pixel 8"

            TestResult(
                "createScreenshotManifest minimal",
                success,
                if (success) "Screenshot manifest created" else "Screenshot manifest incorrect"
            )
        }
    }

    suspend fun testCreateScreenshotManifestWithAppName(): TestResult = withContext(Dispatchers.IO) {
        runTest("createScreenshotManifest with app name") {
            val builder = ManifestHelpers.createScreenshotManifest(
                deviceName = "Pixel 8",
                appName = "Browser"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasSourceApp = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.assertion.metadata") {
                    val data = assertion.getJSONObject("data")
                    if (data.optString("source_application") == "Browser") {
                        hasSourceApp = true
                    }
                }
            }

            TestResult(
                "createScreenshotManifest with app name",
                hasSourceApp,
                if (hasSourceApp) "Source app present" else "Source app missing"
            )
        }
    }

    suspend fun testCreateScreenshotManifestAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("createScreenshotManifest action") {
            val builder = ManifestHelpers.createScreenshotManifest(deviceName = "Pixel 8")
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasScreenCapture = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        val action = actions.getJSONObject(j)
                        if (action.getString("action") == C2PAActions.CREATED &&
                            action.optString("digitalSourceType") == DigitalSourceTypes.SCREEN_CAPTURE) {
                            hasScreenCapture = true
                        }
                    }
                }
            }

            TestResult(
                "createScreenshotManifest action",
                hasScreenCapture,
                if (hasScreenCapture) "Screen capture action present" else "Screen capture action missing"
            )
        }
    }

    // ==================== createSocialMediaShareManifest Tests ====================

    suspend fun testCreateSocialMediaShareManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createSocialMediaShareManifest minimal") {
            val builder = ManifestHelpers.createSocialMediaShareManifest(
                originalTitle = "Original.jpg",
                platform = "Instagram"
            )
            val json = builder.build()

            val success = json.getString("title") == "Shared on Instagram" &&
                    json.has("ingredients")

            TestResult(
                "createSocialMediaShareManifest minimal",
                success,
                if (success) "Social share manifest created" else "Social share manifest incorrect"
            )
        }
    }

    suspend fun testCreateSocialMediaShareManifestActions(): TestResult = withContext(Dispatchers.IO) {
        runTest("createSocialMediaShareManifest actions") {
            val builder = ManifestHelpers.createSocialMediaShareManifest(
                originalTitle = "Original.jpg",
                platform = "Twitter"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasRecompressed = false
            var hasPublished = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        val action = actions.getJSONObject(j)
                        if (action.getString("action") == C2PAActions.RECOMPRESSED) hasRecompressed = true
                        if (action.getString("action") == C2PAActions.PUBLISHED) hasPublished = true
                    }
                }
            }

            val success = hasRecompressed && hasPublished

            TestResult(
                "createSocialMediaShareManifest actions",
                success,
                if (success) "Share actions present" else "Share actions missing"
            )
        }
    }

    // ==================== createFilteredImageManifest Tests ====================

    suspend fun testCreateFilteredImageManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createFilteredImageManifest minimal") {
            val builder = ManifestHelpers.createFilteredImageManifest(
                originalTitle = "Original.jpg",
                filterName = "Vintage"
            )
            val json = builder.build()

            val success = json.getString("title") == "Original.jpg (Filtered)" &&
                    json.has("ingredients")

            TestResult(
                "createFilteredImageManifest minimal",
                success,
                if (success) "Filtered image manifest created" else "Filtered image manifest incorrect"
            )
        }
    }

    suspend fun testCreateFilteredImageManifestFilterParameters(): TestResult = withContext(Dispatchers.IO) {
        runTest("createFilteredImageManifest filter parameters") {
            val builder = ManifestHelpers.createFilteredImageManifest(
                originalTitle = "Original.jpg",
                filterName = "Sepia"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasFilterParams = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == "c2pa.actions") {
                    val actions = assertion.getJSONObject("data").getJSONArray("actions")
                    for (j in 0 until actions.length()) {
                        val action = actions.getJSONObject(j)
                        if (action.getString("action") == C2PAActions.FILTERED) {
                            val params = action.optJSONObject("parameters")
                            if (params?.getString("filter_name") == "Sepia") {
                                hasFilterParams = true
                            }
                        }
                    }
                }
            }

            TestResult(
                "createFilteredImageManifest filter parameters",
                hasFilterParams,
                if (hasFilterParams) "Filter parameters present" else "Filter parameters missing"
            )
        }
    }

    // ==================== createCreatorVerifiedManifest Tests ====================

    suspend fun testCreateCreatorVerifiedManifestMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createCreatorVerifiedManifest minimal") {
            val builder = ManifestHelpers.createCreatorVerifiedManifest(title = "Verified.jpg")
            val json = builder.build()

            val success = json.getString("title") == "Verified.jpg" &&
                    json.has("assertions")

            TestResult(
                "createCreatorVerifiedManifest minimal",
                success,
                if (success) "Creator verified manifest created" else "Creator verified manifest incorrect"
            )
        }
    }

    suspend fun testCreateCreatorVerifiedManifestWithIdentities(): TestResult = withContext(Dispatchers.IO) {
        runTest("createCreatorVerifiedManifest with identities") {
            val identities = listOf(
                VerifiedIdentity(
                    type = CAWGIdentityTypes.SOCIAL_MEDIA,
                    username = "testuser",
                    uri = "https://instagram.com/testuser",
                    verifiedAt = "2024-01-01T00:00:00Z",
                    provider = IdentityProvider(CAWGProviders.INSTAGRAM, "instagram")
                )
            )
            val builder = ManifestHelpers.createCreatorVerifiedManifest(
                title = "Verified.jpg",
                creatorIdentities = identities
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasCAWGIdentity = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == C2PAAssertionTypes.CAWG_IDENTITY) {
                    val identitiesArray = assertion.getJSONObject("data").optJSONArray("verifiedIdentities")
                    if (identitiesArray?.length() == 1) {
                        hasCAWGIdentity = true
                    }
                }
            }

            TestResult(
                "createCreatorVerifiedManifest with identities",
                hasCAWGIdentity,
                if (hasCAWGIdentity) "CAWG identity present" else "CAWG identity missing"
            )
        }
    }

    suspend fun testCreateCreatorVerifiedManifestWithAuthorAndDevice(): TestResult = withContext(Dispatchers.IO) {
        runTest("createCreatorVerifiedManifest with author and device") {
            val builder = ManifestHelpers.createCreatorVerifiedManifest(
                title = "Verified.jpg",
                authorName = "Test Author",
                deviceName = "Pixel 8"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasAuthor = false
            var hasDevice = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                val label = assertion?.getString("label")
                if (label == C2PAAssertionTypes.CREATIVE_WORK) {
                    val authors = assertion.getJSONObject("data").optJSONArray("author")
                    if (authors?.optJSONObject(0)?.getString("name") == "Test Author") {
                        hasAuthor = true
                    }
                }
                if (label == "c2pa.assertion.metadata") {
                    if (assertion.getJSONObject("data").optString("device") == "Pixel 8") {
                        hasDevice = true
                    }
                }
            }

            val success = hasAuthor && hasDevice

            TestResult(
                "createCreatorVerifiedManifest with author and device",
                success,
                if (success) "Author and device present" else "Author or device missing"
            )
        }
    }

    // ==================== createSocialMediaCreatorManifest Tests ====================

    suspend fun testCreateSocialMediaCreatorManifestInstagram(): TestResult = withContext(Dispatchers.IO) {
        runTest("createSocialMediaCreatorManifest Instagram") {
            val builder = ManifestHelpers.createSocialMediaCreatorManifest(
                title = "Post.jpg",
                platform = "instagram",
                username = "creator123"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasInstagramIdentity = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == C2PAAssertionTypes.CAWG_IDENTITY) {
                    val identities = assertion.getJSONObject("data").optJSONArray("verifiedIdentities")
                    if (identities?.length() == 1) {
                        val identity = identities.getJSONObject(0)
                        if (identity.getString("uri").contains("instagram.com/creator123")) {
                            hasInstagramIdentity = true
                        }
                    }
                }
            }

            TestResult(
                "createSocialMediaCreatorManifest Instagram",
                hasInstagramIdentity,
                if (hasInstagramIdentity) "Instagram identity present" else "Instagram identity missing"
            )
        }
    }

    suspend fun testCreateSocialMediaCreatorManifestTwitter(): TestResult = withContext(Dispatchers.IO) {
        runTest("createSocialMediaCreatorManifest Twitter") {
            val builder = ManifestHelpers.createSocialMediaCreatorManifest(
                title = "Tweet.jpg",
                platform = "twitter",
                username = "tweeter123"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasTwitterIdentity = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == C2PAAssertionTypes.CAWG_IDENTITY) {
                    val identities = assertion.getJSONObject("data").optJSONArray("verifiedIdentities")
                    if (identities?.length() == 1) {
                        val identity = identities.getJSONObject(0)
                        if (identity.getString("uri").contains("twitter.com/tweeter123")) {
                            hasTwitterIdentity = true
                        }
                    }
                }
            }

            TestResult(
                "createSocialMediaCreatorManifest Twitter",
                hasTwitterIdentity,
                if (hasTwitterIdentity) "Twitter identity present" else "Twitter identity missing"
            )
        }
    }

    suspend fun testCreateSocialMediaCreatorManifestX(): TestResult = withContext(Dispatchers.IO) {
        runTest("createSocialMediaCreatorManifest X (Twitter)") {
            val builder = ManifestHelpers.createSocialMediaCreatorManifest(
                title = "Post.jpg",
                platform = "x",
                username = "xuser123"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasXIdentity = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == C2PAAssertionTypes.CAWG_IDENTITY) {
                    val identities = assertion.getJSONObject("data").optJSONArray("verifiedIdentities")
                    if (identities?.length() == 1) {
                        val identity = identities.getJSONObject(0)
                        if (identity.getString("uri").contains("twitter.com/xuser123")) {
                            hasXIdentity = true
                        }
                    }
                }
            }

            TestResult(
                "createSocialMediaCreatorManifest X (Twitter)",
                hasXIdentity,
                if (hasXIdentity) "X identity present (using Twitter)" else "X identity missing"
            )
        }
    }

    suspend fun testCreateSocialMediaCreatorManifestGitHub(): TestResult = withContext(Dispatchers.IO) {
        runTest("createSocialMediaCreatorManifest GitHub") {
            val builder = ManifestHelpers.createSocialMediaCreatorManifest(
                title = "Code.jpg",
                platform = "github",
                username = "dev123"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasGitHubIdentity = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == C2PAAssertionTypes.CAWG_IDENTITY) {
                    val identities = assertion.getJSONObject("data").optJSONArray("verifiedIdentities")
                    if (identities?.length() == 1) {
                        val identity = identities.getJSONObject(0)
                        if (identity.getString("uri").contains("github.com/dev123")) {
                            hasGitHubIdentity = true
                        }
                    }
                }
            }

            TestResult(
                "createSocialMediaCreatorManifest GitHub",
                hasGitHubIdentity,
                if (hasGitHubIdentity) "GitHub identity present" else "GitHub identity missing"
            )
        }
    }

    suspend fun testCreateSocialMediaCreatorManifestCustomPlatform(): TestResult = withContext(Dispatchers.IO) {
        runTest("createSocialMediaCreatorManifest custom platform") {
            val builder = ManifestHelpers.createSocialMediaCreatorManifest(
                title = "Post.jpg",
                platform = "mastodon",
                username = "user123"
            )
            val json = builder.build()
            val assertions = json.optJSONArray("assertions")

            var hasCustomIdentity = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val assertion = assertions?.getJSONObject(i)
                if (assertion?.getString("label") == C2PAAssertionTypes.CAWG_IDENTITY) {
                    val identities = assertion.getJSONObject("data").optJSONArray("verifiedIdentities")
                    if (identities?.length() == 1) {
                        val identity = identities.getJSONObject(0)
                        if (identity.getString("uri").contains("mastodon.com/user123")) {
                            hasCustomIdentity = true
                        }
                    }
                }
            }

            TestResult(
                "createSocialMediaCreatorManifest custom platform",
                hasCustomIdentity,
                if (hasCustomIdentity) "Custom platform identity present" else "Custom platform identity missing"
            )
        }
    }

    // ==================== Location Helper Tests ====================

    suspend fun testCreateLocation(): TestResult = withContext(Dispatchers.IO) {
        runTest("createLocation") {
            val location = ManifestHelpers.createLocation(37.7749, -122.4194, "San Francisco")

            val success = location.getString("@type") == "Place" &&
                    location.getDouble("latitude") == 37.7749 &&
                    location.getDouble("longitude") == -122.4194 &&
                    location.getString("name") == "San Francisco"

            TestResult(
                "createLocation",
                success,
                if (success) "Location created correctly" else "Location incorrect"
            )
        }
    }

    suspend fun testCreateLocationWithoutName(): TestResult = withContext(Dispatchers.IO) {
        runTest("createLocation without name") {
            val location = ManifestHelpers.createLocation(40.7128, -74.0060)

            val success = location.getString("@type") == "Place" &&
                    location.getDouble("latitude") == 40.7128 &&
                    location.getDouble("longitude") == -74.0060 &&
                    !location.has("name")

            TestResult(
                "createLocation without name",
                success,
                if (success) "Location without name correct" else "Location without name incorrect"
            )
        }
    }

    suspend fun testCreateGeoLocation(): TestResult = withContext(Dispatchers.IO) {
        runTest("createGeoLocation") {
            val geoLoc = ManifestHelpers.createGeoLocation(
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 10.5,
                accuracy = 5.0
            )

            val success = geoLoc.getString("@type") == "GeoCoordinates" &&
                    geoLoc.getDouble("latitude") == 37.7749 &&
                    geoLoc.getDouble("longitude") == -122.4194 &&
                    geoLoc.getDouble("elevation") == 10.5 &&
                    geoLoc.getDouble("accuracy") == 5.0

            TestResult(
                "createGeoLocation",
                success,
                if (success) "GeoLocation created correctly" else "GeoLocation incorrect"
            )
        }
    }

    suspend fun testCreateGeoLocationMinimal(): TestResult = withContext(Dispatchers.IO) {
        runTest("createGeoLocation minimal") {
            val geoLoc = ManifestHelpers.createGeoLocation(
                latitude = 37.7749,
                longitude = -122.4194
            )

            val success = geoLoc.getString("@type") == "GeoCoordinates" &&
                    geoLoc.getDouble("latitude") == 37.7749 &&
                    geoLoc.getDouble("longitude") == -122.4194 &&
                    !geoLoc.has("elevation") &&
                    !geoLoc.has("accuracy")

            TestResult(
                "createGeoLocation minimal",
                success,
                if (success) "Minimal GeoLocation correct" else "Minimal GeoLocation incorrect"
            )
        }
    }

    // ==================== addStandardThumbnail Tests ====================

    suspend fun testAddStandardThumbnail(): TestResult = withContext(Dispatchers.IO) {
        runTest("addStandardThumbnail") {
            val builder = ManifestHelpers.createBasicImageManifest(title = "Test")
            ManifestHelpers.addStandardThumbnail(builder)
            val json = builder.build()

            val thumb = json.optJSONObject("thumbnail")

            val success = thumb?.getString("format") == C2PAFormats.JPEG &&
                    thumb?.getString("identifier") == "thumbnail.jpg"

            TestResult(
                "addStandardThumbnail",
                success,
                if (success) "Standard thumbnail added" else "Standard thumbnail incorrect"
            )
        }
    }

    suspend fun testAddStandardThumbnailCustomIdentifier(): TestResult = withContext(Dispatchers.IO) {
        runTest("addStandardThumbnail custom identifier") {
            val builder = ManifestHelpers.createBasicImageManifest(title = "Test")
            ManifestHelpers.addStandardThumbnail(builder, thumbnailIdentifier = "custom_thumb.png", format = C2PAFormats.PNG)
            val json = builder.build()

            val thumb = json.optJSONObject("thumbnail")

            val success = thumb?.getString("format") == C2PAFormats.PNG &&
                    thumb?.getString("identifier") == "custom_thumb.png"

            TestResult(
                "addStandardThumbnail custom identifier",
                success,
                if (success) "Custom thumbnail added" else "Custom thumbnail incorrect"
            )
        }
    }
}
