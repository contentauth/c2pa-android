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
package org.contentauth.c2pa.test.shared

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Base class for all test suites. Provides common functionality for loading resources and running
 * tests.
 */
abstract class TestBase {

    enum class TestStatus {
        PASSED,
        FAILED,
        SKIPPED,
    }

    data class TestResult(
        val name: String,
        val success: Boolean,
        val message: String,
        val details: String? = null,
        val status: TestStatus = if (success) TestStatus.PASSED else TestStatus.FAILED,
    )

    companion object {
        const val TEST_MANIFEST_JSON =
            """{
            "claim_generator": "test_app/1.0",
            "assertions": [{"label": "c2pa.test", "data": {"test": true}}]
        }"""

        /** Load a test resource from the classpath (test-shared module resources). */
        fun loadSharedResourceAsBytes(resourceName: String): ByteArray? = try {
            TestBase::class.java.classLoader?.getResourceAsStream(resourceName)?.use {
                it.readBytes()
            }
        } catch (e: Exception) {
            null
        }

        fun loadSharedResourceAsString(resourceName: String): String? = try {
            TestBase::class.java.classLoader?.getResourceAsStream(resourceName)?.use {
                it.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Abstract methods to be implemented by subclasses
    protected abstract fun getContext(): Context
    protected abstract fun loadResourceAsBytes(resourceName: String): ByteArray
    protected abstract fun loadResourceAsString(resourceName: String): String
    protected abstract fun copyResourceToFile(resourceName: String, fileName: String): File

    /** Helper function to run a test with error handling */
    protected suspend fun <T> runTest(name: String, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            throw Exception("Test '$name' failed: ${e.message}", e)
        }
    }

    /** Helper function to create a simple JPEG thumbnail for testing */
    protected open fun createSimpleJPEGThumbnail(): ByteArray = byteArrayOf(
        0xFF.toByte(),
        0xD8.toByte(),
        0xFF.toByte(),
        0xE0.toByte(),
        0x00,
        0x10,
        0x4A,
        0x46,
        0x49,
        0x46,
        0x00,
        0x01,
        0x01,
        0x01,
        0x00,
        0x48,
        0x00,
        0x48,
        0x00,
        0x00,
        0xFF.toByte(),
        0xD9.toByte(),
    )
}
