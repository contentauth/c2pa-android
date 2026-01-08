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
import org.contentauth.c2pa.test.shared.CoreTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Android instrumented tests for core C2PA functionality. */
@RunWith(AndroidJUnit4::class)
class AndroidCoreTests : CoreTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    @Test
    fun runTestLibraryVersion() = runBlocking {
        val result = testLibraryVersion()
        assertTrue(result.success, "Library Version test failed: ${result.message}")
    }

    @Test
    fun runTestErrorHandling() = runBlocking {
        val result = testErrorHandling()
        assertTrue(result.success, "Error Handling test failed: ${result.message}")
    }

    @Test
    fun runTestReadManifestFromTestImage() = runBlocking {
        val result = testReadManifestFromTestImage()
        assertTrue(result.success, "Read Manifest from Test Image failed: ${result.message}")
    }

    @Test
    fun runTestReadIngredient() = runBlocking {
        val result = testReadIngredient()
        assertTrue(result.success, "Read Ingredient test failed: ${result.message}")
    }

    @Test
    fun runTestInvalidFileHandling() = runBlocking {
        val result = testInvalidFileHandling()
        assertTrue(result.success, "Invalid File Handling test failed: ${result.message}")
    }

    @Test
    fun runTestResourceReading() = runBlocking {
        val result = testResourceReading()
        assertTrue(result.success, "Resource Reading test failed: ${result.message}")
    }

    @Test
    fun runTestLoadSettings() = runBlocking {
        val result = testLoadSettings()
        assertTrue(result.success, "Load Settings test failed: ${result.message}")
    }

    @Test
    fun runTestInvalidInputs() = runBlocking {
        val result = testInvalidInputs()
        assertTrue(result.success, "Invalid Inputs test failed: ${result.message}")
    }

    @Test
    fun runTestErrorEnumCoverage() = runBlocking {
        val result = testErrorEnumCoverage()
        assertTrue(result.success, "Error Enum Coverage test failed: ${result.message}")
    }
}
