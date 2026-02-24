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
 * Configuration for creating a [Signer] from PEM-encoded credentials.
 *
 * @property algorithm The [SigningAlgorithm] to use (e.g., ES256, PS256)
 * @property certificatePEM The certificate chain in PEM format
 * @property privateKeyPEM The private key in PEM format
 * @property tsaURL Optional timestamp authority URL for trusted timestamping
 *
 * @see Signer.fromInfo
 * @see Signer.fromKeys
 */
data class SignerInfo(
    val algorithm: SigningAlgorithm,
    val certificatePEM: String,
    val privateKeyPEM: String,
    val tsaURL: String? = null,
)
