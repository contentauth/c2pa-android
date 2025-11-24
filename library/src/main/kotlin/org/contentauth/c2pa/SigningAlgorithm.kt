/* 
This file is licensed to you under the Apache License, Version 2.0
(http://www.apache.org/licenses/LICENSE-2.0) or the MIT license
(http://opensource.org/licenses/MIT), at your option.

Unless required by applicable law or agreed to in writing, this software is
distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS OF
ANY KIND, either express or implied. See the LICENSE-MIT and LICENSE-APACHE
files for the specific language governing permissions and limitations under
each license.
*/
package org.contentauth.c2pa

/**
 * Signing algorithms
 */
enum class SigningAlgorithm {
    ES256,
    ES384,
    ES512,
    PS256,
    PS384,
    PS512,
    ED25519,
    ;

    val cValue: Int
        get() = ordinal

    val description: String
        get() = name.lowercase()
}
