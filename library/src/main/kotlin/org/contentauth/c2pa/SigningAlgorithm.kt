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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supported signing algorithms for C2PA manifests.
 *
 * Each value corresponds to a COSE algorithm identifier used in the C2PA specification.
 */
@Serializable
enum class SigningAlgorithm {
    @SerialName("es256")
    ES256,

    @SerialName("es384")
    ES384,

    @SerialName("es512")
    ES512,

    @SerialName("ps256")
    PS256,

    @SerialName("ps384")
    PS384,

    @SerialName("ps512")
    PS512,

    @SerialName("ed25519")
    ED25519,
    ;

    val cValue: Int
        get() = ordinal

    val description: String
        get() = name.lowercase()
}
