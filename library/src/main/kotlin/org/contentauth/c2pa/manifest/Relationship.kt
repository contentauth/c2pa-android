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
 * Defines the relationship of an ingredient to its parent manifest.
 *
 * @see Ingredient
 */
@Serializable
enum class Relationship {
    /**
     * The ingredient is the parent of the manifest.
     * Used when an asset is opened and edited to create a new version.
     */
    @SerialName("parentOf")
    PARENT_OF,

    /**
     * The ingredient is a component that was placed into the manifest.
     * Used when an asset is composed from multiple sources.
     */
    @SerialName("componentOf")
    COMPONENT_OF,

    /**
     * The ingredient was used as input to produce the manifest.
     * Used when an asset is derived from or influenced by another asset.
     */
    @SerialName("inputTo")
    INPUT_TO,
}
