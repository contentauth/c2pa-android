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
import org.contentauth.c2pa.test.shared.TestBase
import java.io.File

object ResourceTestHelper {

    fun loadResourceAsBytes(resourceName: String): ByteArray {
        val sharedResource =
            TestBase.loadSharedResourceAsBytes("$resourceName.jpg")
                ?: TestBase.loadSharedResourceAsBytes("$resourceName.pem")
                ?: TestBase.loadSharedResourceAsBytes("$resourceName.key")
                ?: TestBase.loadSharedResourceAsBytes("$resourceName.toml")
                ?: TestBase.loadSharedResourceAsBytes("$resourceName.json")

        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }

    fun loadResourceAsString(resourceName: String): String {
        val sharedResource =
            TestBase.loadSharedResourceAsString("$resourceName.jpg")
                ?: TestBase.loadSharedResourceAsString("$resourceName.pem")
                ?: TestBase.loadSharedResourceAsString("$resourceName.key")
                ?: TestBase.loadSharedResourceAsString("$resourceName.toml")
                ?: TestBase.loadSharedResourceAsString("$resourceName.json")

        return sharedResource ?: throw IllegalArgumentException("Resource not found: $resourceName")
    }

    fun copyResourceToFile(context: Context, resourceName: String, fileName: String): File {
        val file = File(context.filesDir, fileName)
        val resourceBytes = loadResourceAsBytes(resourceName)
        file.writeBytes(resourceBytes)
        return file
    }
}
