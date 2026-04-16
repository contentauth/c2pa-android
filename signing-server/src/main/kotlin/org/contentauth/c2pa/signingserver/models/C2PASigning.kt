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

package org.contentauth.c2pa.signingserver.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request payload containing base64-encoded bytes to be signed. */
@Serializable
data class C2PASigningRequest(val claim: String)

/** Response payload containing the base64-encoded signature. */
@Serializable
data class C2PASigningResponse(val signature: String)

/** Server configuration returned to clients for remote signing setup. */
@Serializable
data class C2PAConfiguration(
    val algorithm: String,
    @SerialName("timestamp_url") val timestampUrl: String,
    @SerialName("signing_url") val signingUrl: String,
    @SerialName("certificate_chain") val certificateChain: String,
)
