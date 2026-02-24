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

package org.contentauth.c2pa

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.contentauth.c2pa.test.shared.ManifestTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Android instrumented tests for Manifest definition types. */
@RunWith(AndroidJUnit4::class)
class AndroidManifestTests : ManifestTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    @Test
    fun runTestMinimal() = runBlocking {
        val result = testMinimal()
        assertTrue(result.success, "Manifest Minimal test failed: ${result.message}")
    }

    @Test
    fun runTestCreated() = runBlocking {
        val result = testCreated()
        assertTrue(result.success, "Manifest Created test failed: ${result.message}")
    }

    @Test
    fun runTestEnumRendering() = runBlocking {
        val result = testEnumRendering()
        assertTrue(result.success, "Enum Rendering test failed: ${result.message}")
    }

    @Test
    fun runTestRegionOfInterest() = runBlocking {
        val result = testRegionOfInterest()
        assertTrue(result.success, "Region of Interest test failed: ${result.message}")
    }

    @Test
    fun runTestResourceRef() = runBlocking {
        val result = testResourceRef()
        assertTrue(result.success, "ResourceRef test failed: ${result.message}")
    }

    @Test
    fun runTestHashedUri() = runBlocking {
        val result = testHashedUri()
        assertTrue(result.success, "HashedUri test failed: ${result.message}")
    }

    @Test
    fun runTestUriOrResource() = runBlocking {
        val result = testUriOrResource()
        assertTrue(result.success, "UriOrResource test failed: ${result.message}")
    }

    @Test
    fun runTestMassInit() = runBlocking {
        val result = testMassInit()
        assertTrue(result.success, "Mass Init test failed: ${result.message}")
    }

    @Test
    fun runTestManifestToJson() = runBlocking {
        val result = testManifestToJson()
        assertTrue(result.success, "Manifest to JSON test failed: ${result.message}")
    }

    @Test
    fun runTestShapeFactoryMethods() = runBlocking {
        val result = testShapeFactoryMethods()
        assertTrue(result.success, "Shape Factory Methods test failed: ${result.message}")
    }

    @Test
    fun runTestManifestWithBuilder() = runBlocking {
        val result = testManifestWithBuilder()
        assertTrue(result.success, "Manifest with Builder test failed: ${result.message}")
    }

    @Test
    fun runTestAllAssertionTypes() = runBlocking {
        val result = testAllAssertionTypes()
        assertTrue(result.success, "All Assertion Types test failed: ${result.message}")
    }

    @Test
    fun runTestIngredients() = runBlocking {
        val result = testIngredients()
        assertTrue(result.success, "Ingredients test failed: ${result.message}")
    }

    @Test
    fun runTestActionWithRegions() = runBlocking {
        val result = testActionWithRegions()
        assertTrue(result.success, "Action with Regions test failed: ${result.message}")
    }

    @Test
    fun runTestMalformedJson() = runBlocking {
        val result = testMalformedJson()
        assertTrue(result.success, "Malformed JSON test failed: ${result.message}")
    }

    @Test
    fun runTestSpecialCharacters() = runBlocking {
        val result = testSpecialCharacters()
        assertTrue(result.success, "Special Characters test failed: ${result.message}")
    }

    @Test
    fun runTestCreatedFactory() = runBlocking {
        val result = testCreatedFactory()
        assertTrue(result.success, "Created Factory test failed: ${result.message}")
    }

    @Test
    fun runTestAllValidationStatusCodes() = runBlocking {
        val result = testAllValidationStatusCodes()
        assertTrue(result.success, "All Validation Status Codes test failed: ${result.message}")
    }

    @Test
    fun runTestAllDigitalSourceTypes() = runBlocking {
        val result = testAllDigitalSourceTypes()
        assertTrue(result.success, "All Digital Source Types test failed: ${result.message}")
    }

    @Test
    fun runTestManifestValidator() = runBlocking {
        val result = testManifestValidator()
        assertTrue(result.success, "Manifest Validator test failed: ${result.message}")
    }

    @Test
    fun runTestMixedAssertionTypes() = runBlocking {
        val result = testMixedAssertionTypes()
        assertTrue(result.success, "Mixed Assertion Types test failed: ${result.message}")
    }

    @Test
    fun runTestDeprecatedAssertionValidation() = runBlocking {
        val result = testDeprecatedAssertionValidation()
        assertTrue(result.success, "Deprecated Assertion Validation test failed: ${result.message}")
    }

    @Test
    fun runTestAllPredefinedActions() = runBlocking {
        val result = testAllPredefinedActions()
        assertTrue(result.success, "All Predefined Actions test failed: ${result.message}")
    }

    @Test
    fun runTestAllIngredientRelationships() = runBlocking {
        val result = testAllIngredientRelationships()
        assertTrue(result.success, "All Ingredient Relationships test failed: ${result.message}")
    }

    @Test
    fun runTestRedactions() = runBlocking {
        val result = testRedactions()
        assertTrue(result.success, "Redactions test failed: ${result.message}")
    }


    @Test
    fun runTestCawgTrainingMiningAssertion() = runBlocking {
        val result = testCawgTrainingMiningAssertion()
        assertTrue(result.success, "CAWG Training Mining Assertion test failed: ${result.message}")
    }

    @Test
    fun runTestEditedFactory() = runBlocking {
        val result = testEditedFactory()
        assertTrue(result.success, "Edited Factory test failed: ${result.message}")
    }

    @Test
    fun runTestAssertionsWithBuilder() = runBlocking {
        val result = testAssertionsWithBuilder()
        assertTrue(result.success, "Assertions with Builder test failed: ${result.message}")
    }

    @Test
    fun runTestCustomGatheredAssertionWithBuilder() = runBlocking {
        val result = testCustomGatheredAssertionWithBuilder()
        assertTrue(result.success, "Custom Gathered Assertion with Builder test failed: ${result.message}")
    }

    @Test
    fun runTestManifestValidatorDeprecatedAssertions() = runBlocking {
        val result = testManifestValidatorDeprecatedAssertions()
        assertTrue(result.success, "Manifest Validator Deprecated Assertions test failed: ${result.message}")
    }

    @Test
    fun runTestDigitalSourceTypeFromIptcUrl() = runBlocking {
        val result = testDigitalSourceTypeFromIptcUrl()
        assertTrue(result.success, "DigitalSourceType fromIptcUrl test failed: ${result.message}")
    }

    @Test
    fun runTestManifestAssertionLabels() = runBlocking {
        val result = testManifestAssertionLabels()
        assertTrue(result.success, "ManifestDefinition assertionLabels test failed: ${result.message}")
    }

    @Test
    fun runTestManifestToPrettyJson() = runBlocking {
        val result = testManifestToPrettyJson()
        assertTrue(result.success, "ManifestDefinition toPrettyJson test failed: ${result.message}")
    }

    @Test
    fun runTestIptcPhotoMetadata() = runBlocking {
        val result = testIptcPhotoMetadata()
        assertTrue(result.success, "IptcPhotoMetadata test failed: ${result.message}")
    }

    @Test
    fun runTestCustomAssertionLabelValidation() = runBlocking {
        val result = testCustomAssertionLabelValidation()
        assertTrue(result.success, "Custom Assertion Label Validation test failed: ${result.message}")
    }

    @Test
    fun runTestImageRegionTypeToTypeString() = runBlocking {
        val result = testImageRegionTypeToTypeString()
        assertTrue(result.success, "ImageRegionType toTypeString test failed: ${result.message}")
    }

    @Test
    fun runTestStandardAssertionLabelSerialNames() = runBlocking {
        val result = testStandardAssertionLabelSerialNames()
        assertTrue(result.success, "StandardAssertionLabel serialNames test failed: ${result.message}")
    }
}
