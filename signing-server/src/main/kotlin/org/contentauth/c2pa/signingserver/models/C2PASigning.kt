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

import kotlinx.serialization.Serializable

@Serializable
data class C2PASigningRequest(val claim: String) // Base64-encoded bytes to be signed

@Serializable
data class C2PASigningResponse(val signature: String) // Base64 encoded signature

@Serializable
data class C2PAConfiguration(
    val algorithm: String,
    val timestamp_url: String,
    val signing_url: String,
    val certificate_chain: String,
)
