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
import kotlinx.serialization.Serializable

@Serializable
data class CertificateSigningRequest(
    val csr: String, // PEM-encoded CSR
)

@Serializable
data class SignedCertificateSigningResponse(
    val certificate_id: String,
    val certificate_chain: String, // PEM-encoded certificate chain
    @Contextual val expires_at: Instant,
    val serial_number: String,
)
