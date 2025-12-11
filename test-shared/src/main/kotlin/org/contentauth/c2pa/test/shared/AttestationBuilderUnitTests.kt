package org.contentauth.c2pa.test.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.manifest.Action
import org.contentauth.c2pa.manifest.ActionChange
import org.contentauth.c2pa.manifest.ActionsAttestation
import org.contentauth.c2pa.manifest.AssertionMetadataAttestation
import org.contentauth.c2pa.manifest.AttestationBuilder
import org.contentauth.c2pa.manifest.C2PAActions
import org.contentauth.c2pa.manifest.C2PAAssertionTypes
import org.contentauth.c2pa.manifest.C2PAFormats
import org.contentauth.c2pa.manifest.CAWGIdentityAttestation
import org.contentauth.c2pa.manifest.CAWGIdentityTypes
import org.contentauth.c2pa.manifest.CAWGProviders
import org.contentauth.c2pa.manifest.CreativeWorkAttestation
import org.contentauth.c2pa.manifest.CredentialSchema
import org.contentauth.c2pa.manifest.DataHashAttestation
import org.contentauth.c2pa.manifest.IdentityProvider
import org.contentauth.c2pa.manifest.ManifestBuilder
import org.contentauth.c2pa.manifest.SoftwareAgent
import org.contentauth.c2pa.manifest.ThumbnailAttestation
import org.contentauth.c2pa.manifest.VerifiedIdentity
import org.json.JSONObject
import java.util.Date

/**
 * Unit tests for AttestationBuilder class and all attestation types
 * Tests all attestation types and JSON output structure
 */
abstract class AttestationBuilderUnitTests : TestBase() {

    // ==================== Data Class Tests ====================

    suspend fun testVerifiedIdentityDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("VerifiedIdentity data class") {
            val provider = IdentityProvider("https://instagram.com", "instagram")
            val identity = VerifiedIdentity(
                type = CAWGIdentityTypes.SOCIAL_MEDIA,
                username = "testuser",
                uri = "https://instagram.com/testuser",
                verifiedAt = "2024-01-01T00:00:00Z",
                provider = provider
            )

            val success = identity.type == CAWGIdentityTypes.SOCIAL_MEDIA &&
                    identity.username == "testuser" &&
                    identity.uri == "https://instagram.com/testuser" &&
                    identity.verifiedAt == "2024-01-01T00:00:00Z" &&
                    identity.provider.id == "https://instagram.com" &&
                    identity.provider.name == "instagram"

            TestResult(
                "VerifiedIdentity data class",
                success,
                if (success) "VerifiedIdentity properties correct" else "VerifiedIdentity properties incorrect"
            )
        }
    }

    suspend fun testIdentityProviderDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("IdentityProvider data class") {
            val provider = IdentityProvider(
                id = "https://github.com",
                name = "github"
            )

            val success = provider.id == "https://github.com" &&
                    provider.name == "github"

            TestResult(
                "IdentityProvider data class",
                success,
                if (success) "IdentityProvider properties correct" else "IdentityProvider properties incorrect"
            )
        }
    }

    suspend fun testCredentialSchemaDataClass(): TestResult = withContext(Dispatchers.IO) {
        runTest("CredentialSchema data class") {
            val schema = CredentialSchema(
                id = "https://example.com/schema/",
                type = "CustomSchema"
            )

            val success = schema.id == "https://example.com/schema/" &&
                    schema.type == "CustomSchema"

            TestResult(
                "CredentialSchema data class",
                success,
                if (success) "CredentialSchema properties correct" else "CredentialSchema properties incorrect"
            )
        }
    }

    suspend fun testCredentialSchemaDefaults(): TestResult = withContext(Dispatchers.IO) {
        runTest("CredentialSchema defaults") {
            val schema = CredentialSchema()

            val success = schema.id == "https://cawg.io/identity/1.1/ica/schema/" &&
                    schema.type == "JSONSchema"

            TestResult(
                "CredentialSchema defaults",
                success,
                if (success) "CredentialSchema defaults correct" else "CredentialSchema defaults incorrect"
            )
        }
    }

    // ==================== CreativeWorkAttestation Tests ====================

    suspend fun testCreativeWorkAttestationType(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation type") {
            val attestation = CreativeWorkAttestation()

            val success = attestation.type == C2PAAssertionTypes.CREATIVE_WORK

            TestResult(
                "CreativeWorkAttestation type",
                success,
                if (success) "Type is c2pa.creative_work" else "Type should be c2pa.creative_work"
            )
        }
    }

    suspend fun testCreativeWorkAddAuthor(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation addAuthor") {
            val attestation = CreativeWorkAttestation()
                .addAuthor("John Doe")

            val json = attestation.toJsonObject()
            val authors = json.optJSONArray("author")
            val firstAuthor = authors?.optJSONObject(0)

            val success = firstAuthor?.getString("name") == "John Doe"

            TestResult(
                "CreativeWorkAttestation addAuthor",
                success,
                if (success) "Author added correctly" else "Author not added correctly",
                json.toString(2)
            )
        }
    }

    suspend fun testCreativeWorkAddAuthorWithCredential(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation addAuthor with credential") {
            val attestation = CreativeWorkAttestation()
                .addAuthor("Jane Smith", credential = "Professional Photographer")

            val json = attestation.toJsonObject()
            val authors = json.optJSONArray("author")
            val firstAuthor = authors?.optJSONObject(0)

            val success = firstAuthor?.getString("name") == "Jane Smith" &&
                    firstAuthor?.getString("credential") == "Professional Photographer"

            TestResult(
                "CreativeWorkAttestation addAuthor with credential",
                success,
                if (success) "Author with credential correct" else "Author with credential incorrect"
            )
        }
    }

    suspend fun testCreativeWorkAddAuthorWithIdentifier(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation addAuthor with identifier") {
            val attestation = CreativeWorkAttestation()
                .addAuthor("Test User", identifier = "https://example.com/users/test")

            val json = attestation.toJsonObject()
            val authors = json.optJSONArray("author")
            val firstAuthor = authors?.optJSONObject(0)

            val success = firstAuthor?.getString("name") == "Test User" &&
                    firstAuthor?.getString("@id") == "https://example.com/users/test"

            TestResult(
                "CreativeWorkAttestation addAuthor with identifier",
                success,
                if (success) "Author with identifier correct" else "Author with identifier incorrect"
            )
        }
    }

    suspend fun testCreativeWorkMultipleAuthors(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation multiple authors") {
            val attestation = CreativeWorkAttestation()
                .addAuthor("Author 1")
                .addAuthor("Author 2", credential = "Editor")
                .addAuthor("Author 3", identifier = "https://example.com/3")

            val json = attestation.toJsonObject()
            val authors = json.optJSONArray("author")

            val success = authors?.length() == 3 &&
                    authors.getJSONObject(0).getString("name") == "Author 1" &&
                    authors.getJSONObject(1).getString("name") == "Author 2" &&
                    authors.getJSONObject(2).getString("name") == "Author 3"

            TestResult(
                "CreativeWorkAttestation multiple authors",
                success,
                if (success) "Multiple authors correct" else "Multiple authors incorrect"
            )
        }
    }

    suspend fun testCreativeWorkDateCreatedWithDate(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation dateCreated with Date") {
            val date = Date(1704067200000L) // 2024-01-01T00:00:00Z
            val attestation = CreativeWorkAttestation()
                .dateCreated(date)

            val json = attestation.toJsonObject()
            val dateCreated = json.optString("dateCreated")

            // Should be ISO 8601 format
            val success = dateCreated.contains("2024-01-01") && dateCreated.endsWith("Z")

            TestResult(
                "CreativeWorkAttestation dateCreated with Date",
                success,
                if (success) "Date formatted correctly" else "Date format incorrect",
                "dateCreated: $dateCreated"
            )
        }
    }

    suspend fun testCreativeWorkDateCreatedWithString(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation dateCreated with String") {
            val attestation = CreativeWorkAttestation()
                .dateCreated("2024-06-15T10:30:00Z")

            val json = attestation.toJsonObject()

            val success = json.getString("dateCreated") == "2024-06-15T10:30:00Z"

            TestResult(
                "CreativeWorkAttestation dateCreated with String",
                success,
                if (success) "Date string set correctly" else "Date string not set correctly"
            )
        }
    }

    suspend fun testCreativeWorkReviewStatus(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation reviewStatus") {
            val attestation = CreativeWorkAttestation()
                .reviewStatus("approved")

            val json = attestation.toJsonObject()

            val success = json.getString("reviewStatus") == "approved"

            TestResult(
                "CreativeWorkAttestation reviewStatus",
                success,
                if (success) "Review status set correctly" else "Review status not set correctly"
            )
        }
    }

    suspend fun testCreativeWorkEmptyOutput(): TestResult = withContext(Dispatchers.IO) {
        runTest("CreativeWorkAttestation empty output") {
            val attestation = CreativeWorkAttestation()
            val json = attestation.toJsonObject()

            // Empty attestation should produce empty JSON object
            val success = !json.has("author") && !json.has("dateCreated") && !json.has("reviewStatus")

            TestResult(
                "CreativeWorkAttestation empty output",
                success,
                if (success) "Empty attestation produces empty JSON" else "Empty attestation should have no fields"
            )
        }
    }

    // ==================== ActionsAttestation Tests ====================

    suspend fun testActionsAttestationType(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation type") {
            val attestation = ActionsAttestation()

            val success = attestation.type == "c2pa.actions"

            TestResult(
                "ActionsAttestation type",
                success,
                if (success) "Type is c2pa.actions" else "Type should be c2pa.actions"
            )
        }
    }

    suspend fun testActionsAttestationAddAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addAction") {
            val action = Action(action = C2PAActions.CREATED)
            val attestation = ActionsAttestation()
                .addAction(action)

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)

            val success = firstAction?.getString("action") == C2PAActions.CREATED

            TestResult(
                "ActionsAttestation addAction",
                success,
                if (success) "Action added correctly" else "Action not added correctly"
            )
        }
    }

    suspend fun testActionsAttestationAddCreatedAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addCreatedAction") {
            val agent = SoftwareAgent("TestApp", "1.0", "Android")
            val attestation = ActionsAttestation()
                .addCreatedAction(softwareAgent = agent, whenTimestamp = "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)

            val success = firstAction?.getString("action") == C2PAActions.CREATED &&
                    firstAction?.getString("when") == "2024-01-01T00:00:00Z"

            TestResult(
                "ActionsAttestation addCreatedAction",
                success,
                if (success) "Created action correct" else "Created action incorrect",
                json.toString(2)
            )
        }
    }

    suspend fun testActionsAttestationAddEditedAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addEditedAction") {
            val changes = listOf(ActionChange("brightness", "Increased"))
            val attestation = ActionsAttestation()
                .addEditedAction(changes = changes)

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)
            val changesArray = firstAction?.optJSONArray("changes")

            val success = firstAction?.getString("action") == C2PAActions.EDITED &&
                    changesArray?.getJSONObject(0)?.getString("field") == "brightness"

            TestResult(
                "ActionsAttestation addEditedAction",
                success,
                if (success) "Edited action correct" else "Edited action incorrect"
            )
        }
    }

    suspend fun testActionsAttestationAddOpenedAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addOpenedAction") {
            val attestation = ActionsAttestation()
                .addOpenedAction(whenTimestamp = "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)

            val success = firstAction?.getString("action") == C2PAActions.OPENED

            TestResult(
                "ActionsAttestation addOpenedAction",
                success,
                if (success) "Opened action correct" else "Opened action incorrect"
            )
        }
    }

    suspend fun testActionsAttestationAddPlacedAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addPlacedAction") {
            val attestation = ActionsAttestation()
                .addPlacedAction()

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)

            val success = firstAction?.getString("action") == C2PAActions.PLACED

            TestResult(
                "ActionsAttestation addPlacedAction",
                success,
                if (success) "Placed action correct" else "Placed action incorrect"
            )
        }
    }

    suspend fun testActionsAttestationAddDrawingAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addDrawingAction") {
            val attestation = ActionsAttestation()
                .addDrawingAction()

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)

            val success = firstAction?.getString("action") == C2PAActions.DRAWING

            TestResult(
                "ActionsAttestation addDrawingAction",
                success,
                if (success) "Drawing action correct" else "Drawing action incorrect"
            )
        }
    }

    suspend fun testActionsAttestationAddColorAdjustmentsAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addColorAdjustmentsAction") {
            val params = mapOf("brightness" to 10, "contrast" to 5)
            val attestation = ActionsAttestation()
                .addColorAdjustmentsAction(parameters = params)

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)
            val paramsJson = firstAction?.optJSONObject("parameters")

            val success = firstAction?.getString("action") == C2PAActions.COLOR_ADJUSTMENTS &&
                    paramsJson?.getInt("brightness") == 10 &&
                    paramsJson?.getInt("contrast") == 5

            TestResult(
                "ActionsAttestation addColorAdjustmentsAction",
                success,
                if (success) "Color adjustments action correct" else "Color adjustments action incorrect"
            )
        }
    }

    suspend fun testActionsAttestationAddResizedAction(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation addResizedAction") {
            val attestation = ActionsAttestation()
                .addResizedAction()

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")
            val firstAction = actions?.optJSONObject(0)

            val success = firstAction?.getString("action") == C2PAActions.RESIZED

            TestResult(
                "ActionsAttestation addResizedAction",
                success,
                if (success) "Resized action correct" else "Resized action incorrect"
            )
        }
    }

    suspend fun testActionsAttestationMultipleActions(): TestResult = withContext(Dispatchers.IO) {
        runTest("ActionsAttestation multiple actions") {
            val attestation = ActionsAttestation()
                .addOpenedAction()
                .addEditedAction()
                .addResizedAction()

            val json = attestation.toJsonObject()
            val actions = json.optJSONArray("actions")

            val success = actions?.length() == 3 &&
                    actions.getJSONObject(0).getString("action") == C2PAActions.OPENED &&
                    actions.getJSONObject(1).getString("action") == C2PAActions.EDITED &&
                    actions.getJSONObject(2).getString("action") == C2PAActions.RESIZED

            TestResult(
                "ActionsAttestation multiple actions",
                success,
                if (success) "Multiple actions correct" else "Multiple actions incorrect"
            )
        }
    }

    // ==================== AssertionMetadataAttestation Tests ====================

    suspend fun testAssertionMetadataType(): TestResult = withContext(Dispatchers.IO) {
        runTest("AssertionMetadataAttestation type") {
            val attestation = AssertionMetadataAttestation()

            val success = attestation.type == "c2pa.assertion.metadata"

            TestResult(
                "AssertionMetadataAttestation type",
                success,
                if (success) "Type is c2pa.assertion.metadata" else "Type should be c2pa.assertion.metadata"
            )
        }
    }

    suspend fun testAssertionMetadataAddMetadata(): TestResult = withContext(Dispatchers.IO) {
        runTest("AssertionMetadataAttestation addMetadata") {
            val attestation = AssertionMetadataAttestation()
                .addMetadata("custom_key", "custom_value")

            val json = attestation.toJsonObject()

            val success = json.getString("custom_key") == "custom_value"

            TestResult(
                "AssertionMetadataAttestation addMetadata",
                success,
                if (success) "Metadata added correctly" else "Metadata not added correctly"
            )
        }
    }

    suspend fun testAssertionMetadataDateTime(): TestResult = withContext(Dispatchers.IO) {
        runTest("AssertionMetadataAttestation dateTime") {
            val attestation = AssertionMetadataAttestation()
                .dateTime("2024-01-01T12:00:00Z")

            val json = attestation.toJsonObject()

            val success = json.getString("dateTime") == "2024-01-01T12:00:00Z"

            TestResult(
                "AssertionMetadataAttestation dateTime",
                success,
                if (success) "DateTime set correctly" else "DateTime not set correctly"
            )
        }
    }

    suspend fun testAssertionMetadataDevice(): TestResult = withContext(Dispatchers.IO) {
        runTest("AssertionMetadataAttestation device") {
            val attestation = AssertionMetadataAttestation()
                .device("Google Pixel 8")

            val json = attestation.toJsonObject()

            val success = json.getString("device") == "Google Pixel 8"

            TestResult(
                "AssertionMetadataAttestation device",
                success,
                if (success) "Device set correctly" else "Device not set correctly"
            )
        }
    }

    suspend fun testAssertionMetadataLocation(): TestResult = withContext(Dispatchers.IO) {
        runTest("AssertionMetadataAttestation location") {
            val locationJson = JSONObject().apply {
                put("@type", "Place")
                put("latitude", 37.7749)
                put("longitude", -122.4194)
            }
            val attestation = AssertionMetadataAttestation()
                .location(locationJson)

            val json = attestation.toJsonObject()
            val location = json.optJSONObject("location")

            val success = location?.getString("@type") == "Place" &&
                    location?.getDouble("latitude") == 37.7749 &&
                    location?.getDouble("longitude") == -122.4194

            TestResult(
                "AssertionMetadataAttestation location",
                success,
                if (success) "Location set correctly" else "Location not set correctly"
            )
        }
    }

    suspend fun testAssertionMetadataMultipleFields(): TestResult = withContext(Dispatchers.IO) {
        runTest("AssertionMetadataAttestation multiple fields") {
            val attestation = AssertionMetadataAttestation()
                .dateTime("2024-01-01T12:00:00Z")
                .device("Test Device")
                .addMetadata("custom1", "value1")
                .addMetadata("custom2", 42)
                .addMetadata("custom3", true)

            val json = attestation.toJsonObject()

            val success = json.getString("dateTime") == "2024-01-01T12:00:00Z" &&
                    json.getString("device") == "Test Device" &&
                    json.getString("custom1") == "value1" &&
                    json.getInt("custom2") == 42 &&
                    json.getBoolean("custom3") == true

            TestResult(
                "AssertionMetadataAttestation multiple fields",
                success,
                if (success) "Multiple fields correct" else "Multiple fields incorrect"
            )
        }
    }

    // ==================== ThumbnailAttestation Tests ====================

    suspend fun testThumbnailAttestationType(): TestResult = withContext(Dispatchers.IO) {
        runTest("ThumbnailAttestation type") {
            val attestation = ThumbnailAttestation()

            val success = attestation.type == "c2pa.thumbnail"

            TestResult(
                "ThumbnailAttestation type",
                success,
                if (success) "Type is c2pa.thumbnail" else "Type should be c2pa.thumbnail"
            )
        }
    }

    suspend fun testThumbnailAttestationFormat(): TestResult = withContext(Dispatchers.IO) {
        runTest("ThumbnailAttestation format") {
            val attestation = ThumbnailAttestation()
                .format(C2PAFormats.PNG)

            val json = attestation.toJsonObject()

            val success = json.getString("format") == C2PAFormats.PNG

            TestResult(
                "ThumbnailAttestation format",
                success,
                if (success) "Format set correctly" else "Format not set correctly"
            )
        }
    }

    suspend fun testThumbnailAttestationIdentifier(): TestResult = withContext(Dispatchers.IO) {
        runTest("ThumbnailAttestation identifier") {
            val attestation = ThumbnailAttestation()
                .identifier("thumbnail.jpg")

            val json = attestation.toJsonObject()

            val success = json.getString("identifier") == "thumbnail.jpg"

            TestResult(
                "ThumbnailAttestation identifier",
                success,
                if (success) "Identifier set correctly" else "Identifier not set correctly"
            )
        }
    }

    suspend fun testThumbnailAttestationContentType(): TestResult = withContext(Dispatchers.IO) {
        runTest("ThumbnailAttestation contentType") {
            val attestation = ThumbnailAttestation()
                .contentType("image/png")

            val json = attestation.toJsonObject()

            val success = json.getString("contentType") == "image/png"

            TestResult(
                "ThumbnailAttestation contentType",
                success,
                if (success) "ContentType set correctly" else "ContentType not set correctly"
            )
        }
    }

    suspend fun testThumbnailAttestationDefaultContentType(): TestResult = withContext(Dispatchers.IO) {
        runTest("ThumbnailAttestation default contentType") {
            val attestation = ThumbnailAttestation()
            val json = attestation.toJsonObject()

            val success = json.getString("contentType") == "image/jpeg"

            TestResult(
                "ThumbnailAttestation default contentType",
                success,
                if (success) "Default contentType is image/jpeg" else "Default contentType should be image/jpeg"
            )
        }
    }

    // ==================== DataHashAttestation Tests ====================

    suspend fun testDataHashAttestationType(): TestResult = withContext(Dispatchers.IO) {
        runTest("DataHashAttestation type") {
            val attestation = DataHashAttestation()

            val success = attestation.type == "c2pa.data_hash"

            TestResult(
                "DataHashAttestation type",
                success,
                if (success) "Type is c2pa.data_hash" else "Type should be c2pa.data_hash"
            )
        }
    }

    suspend fun testDataHashAttestationName(): TestResult = withContext(Dispatchers.IO) {
        runTest("DataHashAttestation name") {
            val attestation = DataHashAttestation()
                .name("custom hash name")

            val json = attestation.toJsonObject()

            val success = json.getString("name") == "custom hash name"

            TestResult(
                "DataHashAttestation name",
                success,
                if (success) "Name set correctly" else "Name not set correctly"
            )
        }
    }

    suspend fun testDataHashAttestationDefaultName(): TestResult = withContext(Dispatchers.IO) {
        runTest("DataHashAttestation default name") {
            val attestation = DataHashAttestation()
            val json = attestation.toJsonObject()

            val success = json.getString("name") == "jumbf manifest"

            TestResult(
                "DataHashAttestation default name",
                success,
                if (success) "Default name is 'jumbf manifest'" else "Default name should be 'jumbf manifest'"
            )
        }
    }

    suspend fun testDataHashAttestationPad(): TestResult = withContext(Dispatchers.IO) {
        runTest("DataHashAttestation pad") {
            val attestation = DataHashAttestation()
                .pad(1024)

            val json = attestation.toJsonObject()

            val success = json.getInt("pad") == 1024

            TestResult(
                "DataHashAttestation pad",
                success,
                if (success) "Pad set correctly" else "Pad not set correctly"
            )
        }
    }

    suspend fun testDataHashAttestationExclusions(): TestResult = withContext(Dispatchers.IO) {
        runTest("DataHashAttestation exclusions") {
            val exclusions = listOf(
                mapOf("start" to 0, "length" to 100),
                mapOf("start" to 200, "length" to 50)
            )
            val attestation = DataHashAttestation()
                .exclusions(exclusions)

            val json = attestation.toJsonObject()
            val exclusionsArray = json.optJSONArray("exclusions")

            val success = exclusionsArray?.length() == 2 &&
                    exclusionsArray.getJSONObject(0).getInt("start") == 0 &&
                    exclusionsArray.getJSONObject(0).getInt("length") == 100 &&
                    exclusionsArray.getJSONObject(1).getInt("start") == 200

            TestResult(
                "DataHashAttestation exclusions",
                success,
                if (success) "Exclusions set correctly" else "Exclusions not set correctly"
            )
        }
    }

    // ==================== CAWGIdentityAttestation Tests ====================

    suspend fun testCAWGIdentityType(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation type") {
            val attestation = CAWGIdentityAttestation()

            val success = attestation.type == C2PAAssertionTypes.CAWG_IDENTITY

            TestResult(
                "CAWGIdentityAttestation type",
                success,
                if (success) "Type is cawg.identity" else "Type should be cawg.identity"
            )
        }
    }

    suspend fun testCAWGIdentityDefaultContexts(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation default contexts") {
            val attestation = CAWGIdentityAttestation()
            val json = attestation.toJsonObject()
            val contexts = json.optJSONArray("@context")

            val success = contexts?.length() == 2 &&
                    contexts.getString(0) == "https://www.w3.org/ns/credentials/v2" &&
                    contexts.getString(1) == "https://cawg.io/identity/1.1/ica/context/"

            TestResult(
                "CAWGIdentityAttestation default contexts",
                success,
                if (success) "Default contexts correct" else "Default contexts incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityDefaultTypes(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation default types") {
            val attestation = CAWGIdentityAttestation()
            val json = attestation.toJsonObject()
            val types = json.optJSONArray("type")

            val success = types?.length() == 2 &&
                    types.getString(0) == "VerifiableCredential" &&
                    types.getString(1) == "IdentityClaimsAggregationCredential"

            TestResult(
                "CAWGIdentityAttestation default types",
                success,
                if (success) "Default types correct" else "Default types incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityDefaultIssuer(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation default issuer") {
            val attestation = CAWGIdentityAttestation()
            val json = attestation.toJsonObject()

            val success = json.getString("issuer") == "did:web:connected-identities.identity.adobe.com"

            TestResult(
                "CAWGIdentityAttestation default issuer",
                success,
                if (success) "Default issuer correct" else "Default issuer incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityIssuer(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation custom issuer") {
            val attestation = CAWGIdentityAttestation()
                .issuer("did:web:custom.issuer.com")

            val json = attestation.toJsonObject()

            val success = json.getString("issuer") == "did:web:custom.issuer.com"

            TestResult(
                "CAWGIdentityAttestation custom issuer",
                success,
                if (success) "Custom issuer correct" else "Custom issuer incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityValidFrom(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation validFrom") {
            val attestation = CAWGIdentityAttestation()
                .validFrom("2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()

            val success = json.getString("validFrom") == "2024-01-01T00:00:00Z"

            TestResult(
                "CAWGIdentityAttestation validFrom",
                success,
                if (success) "validFrom set correctly" else "validFrom not set correctly"
            )
        }
    }

    suspend fun testCAWGIdentityValidFromNow(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation validFromNow") {
            val attestation = CAWGIdentityAttestation()
                .validFromNow()

            val json = attestation.toJsonObject()
            val validFrom = json.optString("validFrom")

            // Should be ISO 8601 format and contain current year
            val success = validFrom.isNotEmpty() && validFrom.endsWith("Z")

            TestResult(
                "CAWGIdentityAttestation validFromNow",
                success,
                if (success) "validFromNow generates timestamp" else "validFromNow should generate timestamp",
                "validFrom: $validFrom"
            )
        }
    }

    suspend fun testCAWGIdentityAddContext(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addContext") {
            val attestation = CAWGIdentityAttestation()
                .addContext("https://custom.context.com/")

            val json = attestation.toJsonObject()
            val contexts = json.optJSONArray("@context")

            val success = contexts?.length() == 3 &&
                    contexts.getString(2) == "https://custom.context.com/"

            TestResult(
                "CAWGIdentityAttestation addContext",
                success,
                if (success) "Context added correctly" else "Context not added correctly"
            )
        }
    }

    suspend fun testCAWGIdentityAddContextNoDuplicate(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addContext no duplicate") {
            val attestation = CAWGIdentityAttestation()
                .addContext("https://www.w3.org/ns/credentials/v2") // Already exists

            val json = attestation.toJsonObject()
            val contexts = json.optJSONArray("@context")

            val success = contexts?.length() == 2 // Should not add duplicate

            TestResult(
                "CAWGIdentityAttestation addContext no duplicate",
                success,
                if (success) "Duplicate context not added" else "Should not add duplicate context"
            )
        }
    }

    suspend fun testCAWGIdentityAddType(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addType") {
            val attestation = CAWGIdentityAttestation()
                .addType("CustomCredentialType")

            val json = attestation.toJsonObject()
            val types = json.optJSONArray("type")

            val success = types?.length() == 3 &&
                    types.getString(2) == "CustomCredentialType"

            TestResult(
                "CAWGIdentityAttestation addType",
                success,
                if (success) "Type added correctly" else "Type not added correctly"
            )
        }
    }

    suspend fun testCAWGIdentityAddVerifiedIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addVerifiedIdentity") {
            val attestation = CAWGIdentityAttestation()
                .addVerifiedIdentity(
                    type = CAWGIdentityTypes.SOCIAL_MEDIA,
                    username = "testuser",
                    uri = "https://example.com/testuser",
                    verifiedAt = "2024-01-01T00:00:00Z",
                    providerId = "https://example.com",
                    providerName = "example"
                )

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")
            val firstIdentity = identities?.optJSONObject(0)

            val success = firstIdentity?.getString("type") == CAWGIdentityTypes.SOCIAL_MEDIA &&
                    firstIdentity?.getString("username") == "testuser" &&
                    firstIdentity?.getString("uri") == "https://example.com/testuser" &&
                    firstIdentity?.getString("verifiedAt") == "2024-01-01T00:00:00Z" &&
                    firstIdentity?.getJSONObject("provider")?.getString("id") == "https://example.com" &&
                    firstIdentity?.getJSONObject("provider")?.getString("name") == "example"

            TestResult(
                "CAWGIdentityAttestation addVerifiedIdentity",
                success,
                if (success) "Verified identity correct" else "Verified identity incorrect",
                json.toString(2)
            )
        }
    }

    suspend fun testCAWGIdentityAddInstagramIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addInstagramIdentity") {
            val attestation = CAWGIdentityAttestation()
                .addInstagramIdentity("instauser", "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")
            val firstIdentity = identities?.optJSONObject(0)

            val success = firstIdentity?.getString("type") == CAWGIdentityTypes.SOCIAL_MEDIA &&
                    firstIdentity?.getString("username") == "instauser" &&
                    firstIdentity?.getString("uri") == "https://www.instagram.com/instauser" &&
                    firstIdentity?.getJSONObject("provider")?.getString("id") == CAWGProviders.INSTAGRAM

            TestResult(
                "CAWGIdentityAttestation addInstagramIdentity",
                success,
                if (success) "Instagram identity correct" else "Instagram identity incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityAddTwitterIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addTwitterIdentity") {
            val attestation = CAWGIdentityAttestation()
                .addTwitterIdentity("twitteruser", "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")
            val firstIdentity = identities?.optJSONObject(0)

            val success = firstIdentity?.getString("uri") == "https://twitter.com/twitteruser" &&
                    firstIdentity?.getJSONObject("provider")?.getString("id") == CAWGProviders.TWITTER

            TestResult(
                "CAWGIdentityAttestation addTwitterIdentity",
                success,
                if (success) "Twitter identity correct" else "Twitter identity incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityAddLinkedInIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addLinkedInIdentity") {
            val attestation = CAWGIdentityAttestation()
                .addLinkedInIdentity("John Doe", "https://linkedin.com/in/johndoe", "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")
            val firstIdentity = identities?.optJSONObject(0)

            val success = firstIdentity?.getString("username") == "John Doe" &&
                    firstIdentity?.getString("uri") == "https://linkedin.com/in/johndoe" &&
                    firstIdentity?.getJSONObject("provider")?.getString("id") == CAWGProviders.LINKEDIN

            TestResult(
                "CAWGIdentityAttestation addLinkedInIdentity",
                success,
                if (success) "LinkedIn identity correct" else "LinkedIn identity incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityAddBehanceIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addBehanceIdentity") {
            val attestation = CAWGIdentityAttestation()
                .addBehanceIdentity("behanceuser", "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")
            val firstIdentity = identities?.optJSONObject(0)

            val success = firstIdentity?.getString("uri") == "https://www.behance.net/behanceuser" &&
                    firstIdentity?.getJSONObject("provider")?.getString("id") == CAWGProviders.BEHANCE

            TestResult(
                "CAWGIdentityAttestation addBehanceIdentity",
                success,
                if (success) "Behance identity correct" else "Behance identity incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityAddYouTubeIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addYouTubeIdentity") {
            val attestation = CAWGIdentityAttestation()
                .addYouTubeIdentity("My Channel", "https://youtube.com/c/mychannel", "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")
            val firstIdentity = identities?.optJSONObject(0)

            val success = firstIdentity?.getString("username") == "My Channel" &&
                    firstIdentity?.getString("uri") == "https://youtube.com/c/mychannel" &&
                    firstIdentity?.getJSONObject("provider")?.getString("id") == CAWGProviders.YOUTUBE

            TestResult(
                "CAWGIdentityAttestation addYouTubeIdentity",
                success,
                if (success) "YouTube identity correct" else "YouTube identity incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityAddGitHubIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addGitHubIdentity") {
            val attestation = CAWGIdentityAttestation()
                .addGitHubIdentity("githubuser", "2024-01-01T00:00:00Z")

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")
            val firstIdentity = identities?.optJSONObject(0)

            val success = firstIdentity?.getString("uri") == "https://github.com/githubuser" &&
                    firstIdentity?.getJSONObject("provider")?.getString("id") == CAWGProviders.GITHUB

            TestResult(
                "CAWGIdentityAttestation addGitHubIdentity",
                success,
                if (success) "GitHub identity correct" else "GitHub identity incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityAddCredentialSchema(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation addCredentialSchema") {
            val attestation = CAWGIdentityAttestation()
                .addCredentialSchema("https://custom.schema.com/", "CustomSchema")

            val json = attestation.toJsonObject()
            val schemas = json.optJSONArray("credentialSchema")

            // Should have 2 schemas: default + custom
            val success = schemas?.length() == 2 &&
                    schemas.getJSONObject(1).getString("id") == "https://custom.schema.com/" &&
                    schemas.getJSONObject(1).getString("type") == "CustomSchema"

            TestResult(
                "CAWGIdentityAttestation addCredentialSchema",
                success,
                if (success) "Credential schema added correctly" else "Credential schema not added correctly"
            )
        }
    }

    suspend fun testCAWGIdentityDefaultCredentialSchema(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation default credential schema") {
            val attestation = CAWGIdentityAttestation()
            val json = attestation.toJsonObject()
            val schemas = json.optJSONArray("credentialSchema")

            val success = schemas?.length() == 1 &&
                    schemas.getJSONObject(0).getString("id") == "https://cawg.io/identity/1.1/ica/schema/" &&
                    schemas.getJSONObject(0).getString("type") == "JSONSchema"

            TestResult(
                "CAWGIdentityAttestation default credential schema",
                success,
                if (success) "Default credential schema correct" else "Default credential schema incorrect"
            )
        }
    }

    suspend fun testCAWGIdentityMultipleIdentities(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWGIdentityAttestation multiple identities") {
            val attestation = CAWGIdentityAttestation()
                .addInstagramIdentity("insta", "2024-01-01T00:00:00Z")
                .addTwitterIdentity("twitter", "2024-01-02T00:00:00Z")
                .addGitHubIdentity("github", "2024-01-03T00:00:00Z")

            val json = attestation.toJsonObject()
            val identities = json.optJSONArray("verifiedIdentities")

            val success = identities?.length() == 3

            TestResult(
                "CAWGIdentityAttestation multiple identities",
                success,
                if (success) "Multiple identities correct" else "Multiple identities incorrect"
            )
        }
    }

    // ==================== AttestationBuilder Tests ====================

    suspend fun testAttestationBuilderAddCreativeWork(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder addCreativeWork") {
            val builder = AttestationBuilder()
                .addCreativeWork {
                    addAuthor("Test Author")
                    dateCreated("2024-01-01T00:00:00Z")
                }

            val result = builder.build()

            val success = result.containsKey(C2PAAssertionTypes.CREATIVE_WORK) &&
                    result[C2PAAssertionTypes.CREATIVE_WORK]?.optJSONArray("author")?.length() == 1

            TestResult(
                "AttestationBuilder addCreativeWork",
                success,
                if (success) "CreativeWork attestation added" else "CreativeWork attestation not added"
            )
        }
    }

    suspend fun testAttestationBuilderAddActions(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder addActions") {
            val builder = AttestationBuilder()
                .addActions {
                    addCreatedAction()
                    addEditedAction()
                }

            val result = builder.build()

            val success = result.containsKey("c2pa.actions") &&
                    result["c2pa.actions"]?.optJSONArray("actions")?.length() == 2

            TestResult(
                "AttestationBuilder addActions",
                success,
                if (success) "Actions attestation added" else "Actions attestation not added"
            )
        }
    }

    suspend fun testAttestationBuilderAddAssertionMetadata(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder addAssertionMetadata") {
            val builder = AttestationBuilder()
                .addAssertionMetadata {
                    dateTime("2024-01-01T00:00:00Z")
                    device("Test Device")
                }

            val result = builder.build()

            val success = result.containsKey("c2pa.assertion.metadata") &&
                    result["c2pa.assertion.metadata"]?.getString("device") == "Test Device"

            TestResult(
                "AttestationBuilder addAssertionMetadata",
                success,
                if (success) "AssertionMetadata added" else "AssertionMetadata not added"
            )
        }
    }

    suspend fun testAttestationBuilderAddThumbnail(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder addThumbnail") {
            val builder = AttestationBuilder()
                .addThumbnail {
                    format(C2PAFormats.JPEG)
                    identifier("thumb.jpg")
                }

            val result = builder.build()

            val success = result.containsKey("c2pa.thumbnail") &&
                    result["c2pa.thumbnail"]?.getString("format") == C2PAFormats.JPEG

            TestResult(
                "AttestationBuilder addThumbnail",
                success,
                if (success) "Thumbnail attestation added" else "Thumbnail attestation not added"
            )
        }
    }

    suspend fun testAttestationBuilderAddDataHash(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder addDataHash") {
            val builder = AttestationBuilder()
                .addDataHash {
                    name("test hash")
                    pad(512)
                }

            val result = builder.build()

            val success = result.containsKey("c2pa.data_hash") &&
                    result["c2pa.data_hash"]?.getString("name") == "test hash"

            TestResult(
                "AttestationBuilder addDataHash",
                success,
                if (success) "DataHash attestation added" else "DataHash attestation not added"
            )
        }
    }

    suspend fun testAttestationBuilderAddCAWGIdentity(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder addCAWGIdentity") {
            val builder = AttestationBuilder()
                .addCAWGIdentity {
                    validFromNow()
                    addInstagramIdentity("testuser", "2024-01-01T00:00:00Z")
                }

            val result = builder.build()

            val success = result.containsKey(C2PAAssertionTypes.CAWG_IDENTITY) &&
                    result[C2PAAssertionTypes.CAWG_IDENTITY]?.optJSONArray("verifiedIdentities")?.length() == 1

            TestResult(
                "AttestationBuilder addCAWGIdentity",
                success,
                if (success) "CAWGIdentity attestation added" else "CAWGIdentity attestation not added"
            )
        }
    }

    suspend fun testAttestationBuilderAddCustomAttestation(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder addCustomAttestation") {
            val customData = JSONObject().apply {
                put("custom_field", "custom_value")
            }
            val builder = AttestationBuilder()
                .addCustomAttestation("custom.type", customData)

            val result = builder.build()

            val success = result.containsKey("custom.type") &&
                    result["custom.type"]?.getString("custom_field") == "custom_value"

            TestResult(
                "AttestationBuilder addCustomAttestation",
                success,
                if (success) "Custom attestation added" else "Custom attestation not added"
            )
        }
    }

    suspend fun testAttestationBuilderBuildForManifest(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder buildForManifest") {
            val manifestBuilder = ManifestBuilder()
                .title("Test")
                .format(C2PAFormats.JPEG)

            AttestationBuilder()
                .addCreativeWork {
                    addAuthor("Test Author")
                }
                .addAssertionMetadata {
                    device("Test Device")
                }
                .buildForManifest(manifestBuilder)

            val json = manifestBuilder.build()
            val assertions = json.optJSONArray("assertions")

            // Should have creative work and assertion metadata
            var hasCreativeWork = false
            var hasMetadata = false
            for (i in 0 until (assertions?.length() ?: 0)) {
                val label = assertions?.getJSONObject(i)?.getString("label")
                if (label == C2PAAssertionTypes.CREATIVE_WORK) hasCreativeWork = true
                if (label == "c2pa.assertion.metadata") hasMetadata = true
            }

            val success = hasCreativeWork && hasMetadata

            TestResult(
                "AttestationBuilder buildForManifest",
                success,
                if (success) "Attestations added to manifest" else "Attestations not added to manifest",
                json.toString(2)
            )
        }
    }

    suspend fun testAttestationBuilderMultipleAttestations(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder multiple attestations") {
            val builder = AttestationBuilder()
                .addCreativeWork { addAuthor("Author") }
                .addActions { addCreatedAction() }
                .addAssertionMetadata { device("Device") }
                .addCAWGIdentity { addInstagramIdentity("user", "2024-01-01T00:00:00Z") }

            val result = builder.build()

            val success = result.size == 4 &&
                    result.containsKey(C2PAAssertionTypes.CREATIVE_WORK) &&
                    result.containsKey("c2pa.actions") &&
                    result.containsKey("c2pa.assertion.metadata") &&
                    result.containsKey(C2PAAssertionTypes.CAWG_IDENTITY)

            TestResult(
                "AttestationBuilder multiple attestations",
                success,
                if (success) "All attestations present" else "Missing attestations"
            )
        }
    }

    suspend fun testAttestationBuilderChaining(): TestResult = withContext(Dispatchers.IO) {
        runTest("AttestationBuilder method chaining") {
            val builder = AttestationBuilder()
                .addCreativeWork { addAuthor("A") }
                .addActions { addCreatedAction() }

            // If chaining works correctly, build() should succeed
            val result = builder.build()
            val success = result.isNotEmpty()

            TestResult(
                "AttestationBuilder method chaining",
                success,
                if (success) "Method chaining works" else "Method chaining broken"
            )
        }
    }
}
