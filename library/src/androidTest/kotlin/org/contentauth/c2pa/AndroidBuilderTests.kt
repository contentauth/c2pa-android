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
import org.contentauth.c2pa.test.shared.BuilderTests
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Android instrumented tests for Builder API. */
@RunWith(AndroidJUnit4::class)
class AndroidBuilderTests : BuilderTests() {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    override fun getContext(): Context = targetContext

    override fun loadResourceAsBytes(resourceName: String): ByteArray =
        ResourceTestHelper.loadResourceAsBytes(resourceName)

    override fun loadResourceAsString(resourceName: String): String =
        ResourceTestHelper.loadResourceAsString(resourceName)

    override fun copyResourceToFile(resourceName: String, fileName: String): File =
        ResourceTestHelper.copyResourceToFile(targetContext, resourceName, fileName)

    @Test
    fun runTestBuilderOperations() = runBlocking {
        val result = testBuilderOperations()
        assertTrue(result.success, "Builder Operations test failed: ${result.message}")
    }

    @Test
    fun runTestBuilderNoEmbed() = runBlocking {
        val result = testBuilderNoEmbed()
        assertTrue(result.success, "Builder No-Embed test failed: ${result.message}")
    }

    @Test
    fun runTestBuilderRemoteUrl() = runBlocking {
        val result = testBuilderRemoteUrl()
        assertTrue(result.success, "Builder Remote URL test failed: ${result.message}")
    }

    @Test
    fun runTestBuilderAddResource() = runBlocking {
        val result = testBuilderAddResource()
        assertTrue(result.success, "Builder Add Resource test failed: ${result.message}")
    }

    @Test
    fun runTestBuilderAddIngredient() = runBlocking {
        val result = testBuilderAddIngredient()
        assertTrue(result.success, "Builder Add Ingredient test failed: ${result.message}")
    }

    @Test
    fun runTestBuilderFromArchive() = runBlocking {
        val result = testBuilderFromArchive()
        assertTrue(result.success, "Builder from Archive test failed: ${result.message}")
    }

    @Test
    fun runTestReaderWithManifestData() = runBlocking {
        val result = testReaderWithManifestData()
        assertTrue(result.success, "Reader with Manifest Data test failed: ${result.message}")
    }

    @Test
    fun runTestJsonRoundTrip() = runBlocking {
        val result = testJsonRoundTrip()
        assertTrue(result.success, "JSON Round-trip test failed: ${result.message}")
    }
}
