package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.AttestationBuilderUnitTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Android instrumented tests for AttestationBuilder and all attestation types.
 * These tests cover all attestation classes and their JSON output.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAttestationBuilderUnitTests : AttestationBuilderUnitTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    // Data Class Tests
    @Test
    fun runTestVerifiedIdentityDataClass() = runBlocking {
        val result = testVerifiedIdentityDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestIdentityProviderDataClass() = runBlocking {
        val result = testIdentityProviderDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCredentialSchemaDataClass() = runBlocking {
        val result = testCredentialSchemaDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCredentialSchemaDefaults() = runBlocking {
        val result = testCredentialSchemaDefaults()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // CreativeWorkAttestation Tests
    @Test
    fun runTestCreativeWorkAttestationType() = runBlocking {
        val result = testCreativeWorkAttestationType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkAddAuthor() = runBlocking {
        val result = testCreativeWorkAddAuthor()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkAddAuthorWithCredential() = runBlocking {
        val result = testCreativeWorkAddAuthorWithCredential()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkAddAuthorWithIdentifier() = runBlocking {
        val result = testCreativeWorkAddAuthorWithIdentifier()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkMultipleAuthors() = runBlocking {
        val result = testCreativeWorkMultipleAuthors()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkDateCreatedWithDate() = runBlocking {
        val result = testCreativeWorkDateCreatedWithDate()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkDateCreatedWithString() = runBlocking {
        val result = testCreativeWorkDateCreatedWithString()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkReviewStatus() = runBlocking {
        val result = testCreativeWorkReviewStatus()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreativeWorkEmptyOutput() = runBlocking {
        val result = testCreativeWorkEmptyOutput()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // ActionsAttestation Tests
    @Test
    fun runTestActionsAttestationType() = runBlocking {
        val result = testActionsAttestationType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddAction() = runBlocking {
        val result = testActionsAttestationAddAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddCreatedAction() = runBlocking {
        val result = testActionsAttestationAddCreatedAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddEditedAction() = runBlocking {
        val result = testActionsAttestationAddEditedAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddOpenedAction() = runBlocking {
        val result = testActionsAttestationAddOpenedAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddPlacedAction() = runBlocking {
        val result = testActionsAttestationAddPlacedAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddDrawingAction() = runBlocking {
        val result = testActionsAttestationAddDrawingAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddColorAdjustmentsAction() = runBlocking {
        val result = testActionsAttestationAddColorAdjustmentsAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationAddResizedAction() = runBlocking {
        val result = testActionsAttestationAddResizedAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionsAttestationMultipleActions() = runBlocking {
        val result = testActionsAttestationMultipleActions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // AssertionMetadataAttestation Tests
    @Test
    fun runTestAssertionMetadataType() = runBlocking {
        val result = testAssertionMetadataType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAssertionMetadataAddMetadata() = runBlocking {
        val result = testAssertionMetadataAddMetadata()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAssertionMetadataDateTime() = runBlocking {
        val result = testAssertionMetadataDateTime()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAssertionMetadataDevice() = runBlocking {
        val result = testAssertionMetadataDevice()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAssertionMetadataLocation() = runBlocking {
        val result = testAssertionMetadataLocation()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAssertionMetadataMultipleFields() = runBlocking {
        val result = testAssertionMetadataMultipleFields()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // ThumbnailAttestation Tests
    @Test
    fun runTestThumbnailAttestationType() = runBlocking {
        val result = testThumbnailAttestationType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestThumbnailAttestationFormat() = runBlocking {
        val result = testThumbnailAttestationFormat()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestThumbnailAttestationIdentifier() = runBlocking {
        val result = testThumbnailAttestationIdentifier()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestThumbnailAttestationContentType() = runBlocking {
        val result = testThumbnailAttestationContentType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestThumbnailAttestationDefaultContentType() = runBlocking {
        val result = testThumbnailAttestationDefaultContentType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // DataHashAttestation Tests
    @Test
    fun runTestDataHashAttestationType() = runBlocking {
        val result = testDataHashAttestationType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestDataHashAttestationName() = runBlocking {
        val result = testDataHashAttestationName()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestDataHashAttestationDefaultName() = runBlocking {
        val result = testDataHashAttestationDefaultName()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestDataHashAttestationPad() = runBlocking {
        val result = testDataHashAttestationPad()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestDataHashAttestationExclusions() = runBlocking {
        val result = testDataHashAttestationExclusions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // CAWGIdentityAttestation Tests
    @Test
    fun runTestCAWGIdentityType() = runBlocking {
        val result = testCAWGIdentityType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityDefaultContexts() = runBlocking {
        val result = testCAWGIdentityDefaultContexts()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityDefaultTypes() = runBlocking {
        val result = testCAWGIdentityDefaultTypes()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityDefaultIssuer() = runBlocking {
        val result = testCAWGIdentityDefaultIssuer()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityIssuer() = runBlocking {
        val result = testCAWGIdentityIssuer()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityValidFrom() = runBlocking {
        val result = testCAWGIdentityValidFrom()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityValidFromNow() = runBlocking {
        val result = testCAWGIdentityValidFromNow()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddContext() = runBlocking {
        val result = testCAWGIdentityAddContext()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddContextNoDuplicate() = runBlocking {
        val result = testCAWGIdentityAddContextNoDuplicate()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddType() = runBlocking {
        val result = testCAWGIdentityAddType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddVerifiedIdentity() = runBlocking {
        val result = testCAWGIdentityAddVerifiedIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddInstagramIdentity() = runBlocking {
        val result = testCAWGIdentityAddInstagramIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddTwitterIdentity() = runBlocking {
        val result = testCAWGIdentityAddTwitterIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddLinkedInIdentity() = runBlocking {
        val result = testCAWGIdentityAddLinkedInIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddBehanceIdentity() = runBlocking {
        val result = testCAWGIdentityAddBehanceIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddYouTubeIdentity() = runBlocking {
        val result = testCAWGIdentityAddYouTubeIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddGitHubIdentity() = runBlocking {
        val result = testCAWGIdentityAddGitHubIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityAddCredentialSchema() = runBlocking {
        val result = testCAWGIdentityAddCredentialSchema()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityDefaultCredentialSchema() = runBlocking {
        val result = testCAWGIdentityDefaultCredentialSchema()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCAWGIdentityMultipleIdentities() = runBlocking {
        val result = testCAWGIdentityMultipleIdentities()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // AttestationBuilder Tests
    @Test
    fun runTestAttestationBuilderAddCreativeWork() = runBlocking {
        val result = testAttestationBuilderAddCreativeWork()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderAddActions() = runBlocking {
        val result = testAttestationBuilderAddActions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderAddAssertionMetadata() = runBlocking {
        val result = testAttestationBuilderAddAssertionMetadata()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderAddThumbnail() = runBlocking {
        val result = testAttestationBuilderAddThumbnail()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderAddDataHash() = runBlocking {
        val result = testAttestationBuilderAddDataHash()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderAddCAWGIdentity() = runBlocking {
        val result = testAttestationBuilderAddCAWGIdentity()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderAddCustomAttestation() = runBlocking {
        val result = testAttestationBuilderAddCustomAttestation()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderBuildForManifest() = runBlocking {
        val result = testAttestationBuilderBuildForManifest()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderMultipleAttestations() = runBlocking {
        val result = testAttestationBuilderMultipleAttestations()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAttestationBuilderChaining() = runBlocking {
        val result = testAttestationBuilderChaining()
        assertTrue(result.success, "Test failed: ${result.message}")
    }
}
