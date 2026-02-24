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

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request payload containing a PEM-encoded certificate signing request. */
@Serializable
data class CertificateSigningRequest(
    val csr: String,
)

/** Response payload for a signed certificate, including the full chain. */
@Serializable
data class SignedCertificateSigningResponse(
    @SerialName("certificate_id") val certificateId: String,
    @SerialName("certificate_chain") val certificateChain: String,
    @SerialName("expires_at") @Contextual val expiresAt: Instant,
    @SerialName("serial_number") val serialNumber: String,
)
