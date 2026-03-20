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

import kotlinx.serialization.json.Json

/**
 * Centralized JSON configuration for C2PA manifests and settings.
 */
object C2PAJson {

    /**
     * Default JSON configuration for C2PA manifest serialization.
     *
     * Settings:
     * - Does not encode default values (smaller output)
     * - Ignores unknown keys (forward compatibility with newer C2PA versions)
     */
    val default: Json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    /**
     * Pretty-printed JSON configuration for debugging and display.
     */
    val pretty: Json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }
}
