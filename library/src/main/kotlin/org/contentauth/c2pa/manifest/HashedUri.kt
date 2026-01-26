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

package org.contentauth.c2pa.manifest

import kotlinx.serialization.Serializable

/**
 * A URI reference with an associated hash for integrity verification.
 *
 * @property url The URL being referenced.
 * @property hash The hash bytes for integrity verification (base64 encoded).
 * @property alg The algorithm used for hashing.
 * @see MetadataActor
 */
@Serializable
data class HashedUri(
    val url: String? = null,
    val hash: String? = null,
    val alg: String? = null,
)
