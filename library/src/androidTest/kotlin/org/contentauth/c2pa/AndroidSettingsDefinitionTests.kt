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
import org.contentauth.c2pa.test.shared.SettingsDefinitionTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Android instrumented tests for C2PASettingsDefinition. */
@RunWith(AndroidJUnit4::class)
class AndroidSettingsDefinitionTests : SettingsDefinitionTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    @Test
    fun runTestRoundTrip() = runBlocking {
        val result = testRoundTrip()
        assertTrue(result.success, "Round Trip test failed: ${result.message}")
    }

    @Test
    fun runTestFromJson() = runBlocking {
        val result = testFromJson()
        assertTrue(result.success, "fromJson test failed: ${result.message}")
    }

    @Test
    fun runTestSettingsIntent() = runBlocking {
        val result = testSettingsIntent()
        assertTrue(result.success, "Settings Intent test failed: ${result.message}")
    }

    @Test
    fun runTestToJson() = runBlocking {
        val result = testToJson()
        assertTrue(result.success, "toJson test failed: ${result.message}")
    }

    @Test
    fun runTestSignerSettings() = runBlocking {
        val result = testSignerSettings()
        assertTrue(result.success, "Signer Settings test failed: ${result.message}")
    }

    @Test
    fun runTestCawgSigner() = runBlocking {
        val result = testCawgSigner()
        assertTrue(result.success, "CAWG Signer test failed: ${result.message}")
    }

    @Test
    fun runTestIgnoreUnknownKeys() = runBlocking {
        val result = testIgnoreUnknownKeys()
        assertTrue(result.success, "Ignore Unknown Keys test failed: ${result.message}")
    }

    @Test
    fun runTestBuilderSettings() = runBlocking {
        val result = testBuilderSettings()
        assertTrue(result.success, "Builder Settings test failed: ${result.message}")
    }

    @Test
    fun runTestFromDefinition() = runBlocking {
        val result = testFromDefinition()
        assertTrue(result.success, "fromDefinition test failed: ${result.message}")
    }

    @Test
    fun runTestUpdateFrom() = runBlocking {
        val result = testUpdateFrom()
        assertTrue(result.success, "updateFrom test failed: ${result.message}")
    }

    @Test
    fun runTestPrettyJson() = runBlocking {
        val result = testPrettyJson()
        assertTrue(result.success, "Pretty JSON test failed: ${result.message}")
    }

    @Test
    fun runTestEnumSerialization() = runBlocking {
        val result = testEnumSerialization()
        assertTrue(result.success, "Enum Serialization test failed: ${result.message}")
    }

    @Test
    fun runTestActionTemplates() = runBlocking {
        val result = testActionTemplates()
        assertTrue(result.success, "Action Templates test failed: ${result.message}")
    }
}
