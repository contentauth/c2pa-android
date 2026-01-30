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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reference to a resource, either embedded or external.
 *
 * @property format The MIME type of the resource (e.g., "image/jpeg").
 * @property identifier A unique identifier for the resource.
 * @property hash The hash bytes of the resource.
 * @property dataTypes The data types associated with this resource.
 * @property alg The algorithm used for hashing.
 * @see ManifestDefinition
 * @see Ingredient
 */
@Serializable
data class ResourceRef(
    val format: String? = null,
    val identifier: String? = null,
    val hash: String? = null,
    @SerialName("data_types")
    val dataTypes: List<AssetType>? = null,
    val alg: String? = null,
)
