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
 * Base type for URI or embedded resource references.
 *
 * Provides common properties for referencing external URIs or embedded resources
 * with optional algorithm specification.
 *
 * @property alg The algorithm used for hashing (e.g., "sha256").
 * @see ResourceRef
 * @see HashedUri
 */
@Serializable
data class UriOrResource(
    val alg: String? = null,
)
