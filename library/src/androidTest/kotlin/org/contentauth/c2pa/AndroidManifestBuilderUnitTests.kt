package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.ManifestBuilderUnitTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Android instrumented tests for ManifestBuilder class.
 * These tests cover all ManifestBuilder methods and data classes.
 */
@RunWith(AndroidJUnit4::class)
class AndroidManifestBuilderUnitTests : ManifestBuilderUnitTests() {

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
    fun runTestClaimGeneratorDataClass() = runBlocking {
        val result = testClaimGeneratorDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestClaimGeneratorWithoutIcon() = runBlocking {
        val result = testClaimGeneratorWithoutIcon()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestIngredientDataClass() = runBlocking {
        val result = testIngredientDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestIngredientDefaults() = runBlocking {
        val result = testIngredientDefaults()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestThumbnailDataClass() = runBlocking {
        val result = testThumbnailDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestThumbnailDefaultContentType() = runBlocking {
        val result = testThumbnailDefaultContentType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionDataClass() = runBlocking {
        val result = testActionDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionDefaults() = runBlocking {
        val result = testActionDefaults()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestSoftwareAgentDataClass() = runBlocking {
        val result = testSoftwareAgentDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestActionChangeDataClass() = runBlocking {
        val result = testActionChangeDataClass()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // ManifestBuilder Method Tests
    @Test
    fun runTestManifestBuilderChaining() = runBlocking {
        val result = testManifestBuilderChaining()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderClaimGenerator() = runBlocking {
        val result = testManifestBuilderClaimGenerator()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderClaimGeneratorWithoutIcon() = runBlocking {
        val result = testManifestBuilderClaimGeneratorWithoutIcon()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderFormat() = runBlocking {
        val result = testManifestBuilderFormat()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderTitle() = runBlocking {
        val result = testManifestBuilderTitle()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderInstanceId() = runBlocking {
        val result = testManifestBuilderInstanceId()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAutoInstanceId() = runBlocking {
        val result = testManifestBuilderAutoInstanceId()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderDocumentId() = runBlocking {
        val result = testManifestBuilderDocumentId()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderProducer() = runBlocking {
        val result = testManifestBuilderProducer()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderTimestampAuthority() = runBlocking {
        val result = testManifestBuilderTimestampAuthority()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAddIngredient() = runBlocking {
        val result = testManifestBuilderAddIngredient()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderMultipleIngredients() = runBlocking {
        val result = testManifestBuilderMultipleIngredients()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderIngredientWithThumbnail() = runBlocking {
        val result = testManifestBuilderIngredientWithThumbnail()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderIngredientWithValidationStatus() = runBlocking {
        val result = testManifestBuilderIngredientWithValidationStatus()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAddAction() = runBlocking {
        val result = testManifestBuilderAddAction()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderActionWithSoftwareAgent() = runBlocking {
        val result = testManifestBuilderActionWithSoftwareAgent()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderActionWithChanges() = runBlocking {
        val result = testManifestBuilderActionWithChanges()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderActionWithParameters() = runBlocking {
        val result = testManifestBuilderActionWithParameters()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderActionWithDigitalSourceType() = runBlocking {
        val result = testManifestBuilderActionWithDigitalSourceType()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderActionWithReason() = runBlocking {
        val result = testManifestBuilderActionWithReason()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderMultipleActions() = runBlocking {
        val result = testManifestBuilderMultipleActions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAddThumbnail() = runBlocking {
        val result = testManifestBuilderAddThumbnail()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAddAssertionWithJsonObject() = runBlocking {
        val result = testManifestBuilderAddAssertionWithJsonObject()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAddAssertionWithString() = runBlocking {
        val result = testManifestBuilderAddAssertionWithString()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAddAssertionWithNumber() = runBlocking {
        val result = testManifestBuilderAddAssertionWithNumber()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderAddAssertionWithBoolean() = runBlocking {
        val result = testManifestBuilderAddAssertionWithBoolean()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    // Build Output Tests
    @Test
    fun runTestManifestBuilderBuildClaimVersion() = runBlocking {
        val result = testManifestBuilderBuildClaimVersion()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderBuildJson() = runBlocking {
        val result = testManifestBuilderBuildJson()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderEmptyAssertions() = runBlocking {
        val result = testManifestBuilderEmptyAssertions()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderEmptyIngredients() = runBlocking {
        val result = testManifestBuilderEmptyIngredients()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderOptionalFieldsAbsent() = runBlocking {
        val result = testManifestBuilderOptionalFieldsAbsent()
        assertTrue(result.success, "Test failed: ${result.message}")
    }

    @Test
    fun runTestManifestBuilderCompleteManifest() = runBlocking {
        val result = testManifestBuilderCompleteManifest()
        assertTrue(result.success, "Test failed: ${result.message}")
    }
}
