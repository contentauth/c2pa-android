package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.ManifestHelpersUnitTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Android instrumented tests for ManifestHelpers factory methods.
 * These tests cover all helper functions for creating manifest builders.
 */
@RunWith(AndroidJUnit4::class)
class AndroidManifestHelpersUnitTests : ManifestHelpersUnitTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    // createBasicImageManifest Tests
    @Test
    fun runTestCreateBasicImageManifestMinimal() = runBlocking {
        val result = testCreateBasicImageManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateBasicImageManifestWithFormat() = runBlocking {
        val result = testCreateBasicImageManifestWithFormat()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateBasicImageManifestWithClaimGenerator() = runBlocking {
        val result = testCreateBasicImageManifestWithClaimGenerator()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateBasicImageManifestWithTimestampAuthority() = runBlocking {
        val result = testCreateBasicImageManifestWithTimestampAuthority()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateBasicImageManifestDefaultClaimGenerator() = runBlocking {
        val result = testCreateBasicImageManifestDefaultClaimGenerator()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createImageEditManifest Tests
    @Test
    fun runTestCreateImageEditManifestMinimal() = runBlocking {
        val result = testCreateImageEditManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateImageEditManifestIngredient() = runBlocking {
        val result = testCreateImageEditManifestIngredient()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateImageEditManifestAction() = runBlocking {
        val result = testCreateImageEditManifestAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createPhotoManifest Tests
    @Test
    fun runTestCreatePhotoManifestMinimal() = runBlocking {
        val result = testCreatePhotoManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreatePhotoManifestWithAuthor() = runBlocking {
        val result = testCreatePhotoManifestWithAuthor()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreatePhotoManifestWithLocation() = runBlocking {
        val result = testCreatePhotoManifestWithLocation()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreatePhotoManifestCreatedAction() = runBlocking {
        val result = testCreatePhotoManifestCreatedAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createVideoEditManifest Tests
    @Test
    fun runTestCreateVideoEditManifestMinimal() = runBlocking {
        val result = testCreateVideoEditManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateVideoEditManifestWithEditActions() = runBlocking {
        val result = testCreateVideoEditManifestWithEditActions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateVideoEditManifestOpenedAction() = runBlocking {
        val result = testCreateVideoEditManifestOpenedAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createCompositeManifest Tests
    @Test
    fun runTestCreateCompositeManifestMinimal() = runBlocking {
        val result = testCreateCompositeManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateCompositeManifestActions() = runBlocking {
        val result = testCreateCompositeManifestActions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createScreenshotManifest Tests
    @Test
    fun runTestCreateScreenshotManifestMinimal() = runBlocking {
        val result = testCreateScreenshotManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateScreenshotManifestWithAppName() = runBlocking {
        val result = testCreateScreenshotManifestWithAppName()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateScreenshotManifestAction() = runBlocking {
        val result = testCreateScreenshotManifestAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createSocialMediaShareManifest Tests
    @Test
    fun runTestCreateSocialMediaShareManifestMinimal() = runBlocking {
        val result = testCreateSocialMediaShareManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateSocialMediaShareManifestActions() = runBlocking {
        val result = testCreateSocialMediaShareManifestActions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createFilteredImageManifest Tests
    @Test
    fun runTestCreateFilteredImageManifestMinimal() = runBlocking {
        val result = testCreateFilteredImageManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateFilteredImageManifestFilterParameters() = runBlocking {
        val result = testCreateFilteredImageManifestFilterParameters()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createCreatorVerifiedManifest Tests
    @Test
    fun runTestCreateCreatorVerifiedManifestMinimal() = runBlocking {
        val result = testCreateCreatorVerifiedManifestMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateCreatorVerifiedManifestWithIdentities() = runBlocking {
        val result = testCreateCreatorVerifiedManifestWithIdentities()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateCreatorVerifiedManifestWithAuthorAndDevice() = runBlocking {
        val result = testCreateCreatorVerifiedManifestWithAuthorAndDevice()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // createSocialMediaCreatorManifest Tests
    @Test
    fun runTestCreateSocialMediaCreatorManifestInstagram() = runBlocking {
        val result = testCreateSocialMediaCreatorManifestInstagram()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateSocialMediaCreatorManifestTwitter() = runBlocking {
        val result = testCreateSocialMediaCreatorManifestTwitter()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateSocialMediaCreatorManifestX() = runBlocking {
        val result = testCreateSocialMediaCreatorManifestX()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateSocialMediaCreatorManifestGitHub() = runBlocking {
        val result = testCreateSocialMediaCreatorManifestGitHub()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateSocialMediaCreatorManifestCustomPlatform() = runBlocking {
        val result = testCreateSocialMediaCreatorManifestCustomPlatform()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // Location Helper Tests
    @Test
    fun runTestCreateLocation() = runBlocking {
        val result = testCreateLocation()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateLocationWithoutName() = runBlocking {
        val result = testCreateLocationWithoutName()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateGeoLocation() = runBlocking {
        val result = testCreateGeoLocation()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestCreateGeoLocationMinimal() = runBlocking {
        val result = testCreateGeoLocationMinimal()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // addStandardThumbnail Tests
    @Test
    fun runTestAddStandardThumbnail() = runBlocking {
        val result = testAddStandardThumbnail()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestAddStandardThumbnailCustomIdentifier() = runBlocking {
        val result = testAddStandardThumbnailCustomIdentifier()
        assertTrue(result.success, "Test failed: ${result.message}")
    }
}
