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
import org.contentauth.c2pa.test.shared.SettingsValidatorTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Android instrumented tests for SettingsValidator. */
@RunWith(AndroidJUnit4::class)
class AndroidSettingsValidatorTests : SettingsValidatorTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    @Test
    fun runTestValidSettings() = runBlocking {
        val result = testValidSettings()
        assertTrue(result.success, "Valid Settings test failed: ${result.message}")
    }

    @Test
    fun runTestInvalidJson() = runBlocking {
        val result = testInvalidJson()
        assertTrue(result.success, "Invalid JSON test failed: ${result.message}")
    }

    @Test
    fun runTestMissingVersion() = runBlocking {
        val result = testMissingVersion()
        assertTrue(result.success, "Missing Version test failed: ${result.message}")
    }

    @Test
    fun runTestWrongVersion() = runBlocking {
        val result = testWrongVersion()
        assertTrue(result.success, "Wrong Version test failed: ${result.message}")
    }

    @Test
    fun runTestUnknownTopLevelKeys() = runBlocking {
        val result = testUnknownTopLevelKeys()
        assertTrue(result.success, "Unknown Top-Level Keys test failed: ${result.message}")
    }

    @Test
    fun runTestTrustSection() = runBlocking {
        val result = testTrustSection()
        assertTrue(result.success, "Trust Section test failed: ${result.message}")
    }

    @Test
    fun runTestCawgTrustSection() = runBlocking {
        val result = testCawgTrustSection()
        assertTrue(result.success, "CAWG Trust Section test failed: ${result.message}")
    }

    @Test
    fun runTestCoreSection() = runBlocking {
        val result = testCoreSection()
        assertTrue(result.success, "Core Section test failed: ${result.message}")
    }

    @Test
    fun runTestVerifySection() = runBlocking {
        val result = testVerifySection()
        assertTrue(result.success, "Verify Section test failed: ${result.message}")
    }

    @Test
    fun runTestBuilderSection() = runBlocking {
        val result = testBuilderSection()
        assertTrue(result.success, "Builder Section test failed: ${result.message}")
    }

    @Test
    fun runTestThumbnailSection() = runBlocking {
        val result = testThumbnailSection()
        assertTrue(result.success, "Thumbnail Section test failed: ${result.message}")
    }

    @Test
    fun runTestActionsSection() = runBlocking {
        val result = testActionsSection()
        assertTrue(result.success, "Actions Section test failed: ${result.message}")
    }

    @Test
    fun runTestLocalSigner() = runBlocking {
        val result = testLocalSigner()
        assertTrue(result.success, "Local Signer test failed: ${result.message}")
    }

    @Test
    fun runTestRemoteSigner() = runBlocking {
        val result = testRemoteSigner()
        assertTrue(result.success, "Remote Signer test failed: ${result.message}")
    }

    @Test
    fun runTestSignerMutualExclusion() = runBlocking {
        val result = testSignerMutualExclusion()
        assertTrue(result.success, "Signer Mutual Exclusion test failed: ${result.message}")
    }

    @Test
    fun runTestValidationResultHelpers() = runBlocking {
        val result = testValidationResultHelpers()
        assertTrue(result.success, "ValidationResult Helpers test failed: ${result.message}")
    }

    @Test
    fun runTestValidateAndLog() = runBlocking {
        val result = testValidateAndLog()
        assertTrue(result.success, "Validate and Log test failed: ${result.message}")
    }

    @Test
    fun runTestIntentAsNumber() = runBlocking {
        val result = testIntentAsNumber()
        assertTrue(result.success, "Intent As Number test failed: ${result.message}")
    }
}
