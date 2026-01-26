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
 * Describes the type and version of an asset.
 *
 * @property type The asset type identifier or MIME type.
 * @property version The optional version of the asset type.
 * @see ResourceRef
 * @see Ingredient
 */
@Serializable
data class AssetType(
    val type: String,
    val version: String? = null,
)
