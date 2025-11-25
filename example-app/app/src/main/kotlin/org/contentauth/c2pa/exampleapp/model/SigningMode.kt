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

package org.contentauth.c2pa.exampleapp.model

enum class SigningMode(val displayName: String, val description: String, val requiresConfiguration: Boolean = false) {
    DEFAULT(
        displayName = "Default",
        description = "Use the included test certificate for signing",
        requiresConfiguration = false,
    ),
    KEYSTORE(
        displayName = "Android Keystore",
        description = "Generate and store keys in Android Keystore",
        requiresConfiguration = true,
    ),
    HARDWARE(
        displayName = "Hardware Security",
        description = "Use hardware-backed keys with StrongBox or TEE",
        requiresConfiguration = true,
    ),
    CUSTOM(
        displayName = "Custom",
        description = "Upload your own certificate and private key",
        requiresConfiguration = true,
    ),
    REMOTE(
        displayName = "Remote",
        description = "Use a remote signing service",
        requiresConfiguration = true,
    ),
    ;

    companion object {
        fun fromString(value: String): SigningMode = entries.find { it.name == value } ?: DEFAULT
    }
}
