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
 * Represents an ingredient in a manifest - an external asset that was used in creating the content.
 *
 * Ingredients document the provenance chain by referencing parent assets, components,
 * or other inputs that contributed to the creation of the current asset.
 *
 * @property title The title or name of the ingredient.
 * @property format The MIME type of the ingredient (e.g., "image/jpeg").
 * @property relationship The relationship of this ingredient to the manifest.
 * @property data Reference to the ingredient's data.
 * @property thumbnail Reference to a thumbnail of the ingredient.
 * @property manifestData Reference to the ingredient's manifest data.
 * @property activeManifest The label of the active manifest in the ingredient.
 * @property hash The hash of the ingredient data.
 * @property description A human-readable description of the ingredient.
 * @property label A unique label for this ingredient.
 * @property dataTypes The data types associated with this ingredient.
 * @property validationStatus The validation status codes for this ingredient.
 * @property validationResults Detailed validation results for this ingredient.
 * @property metadata Additional metadata about the ingredient.
 * @property documentId A document identifier for the ingredient.
 * @property instanceId An instance identifier for the ingredient.
 * @property provenance A URL to the ingredient's provenance information.
 * @property informationalUri A URL with additional information about the ingredient.
 * @see ManifestDefinition
 * @see Relationship
 */
@Serializable
data class Ingredient(
    val title: String? = null,
    val format: String? = null,
    val relationship: Relationship? = null,
    val data: ResourceRef? = null,
    val thumbnail: ResourceRef? = null,
    @SerialName("manifest_data")
    val manifestData: ResourceRef? = null,
    @SerialName("active_manifest")
    val activeManifest: String? = null,
    val hash: String? = null,
    val description: String? = null,
    val label: String? = null,
    @SerialName("data_types")
    val dataTypes: List<AssetType>? = null,
    @SerialName("validation_status")
    val validationStatus: List<ValidationStatus>? = null,
    @SerialName("validation_results")
    val validationResults: ValidationResults? = null,
    val metadata: Metadata? = null,
    @SerialName("document_id")
    val documentId: String? = null,
    @SerialName("instance_id")
    val instanceId: String? = null,
    val provenance: String? = null,
    @SerialName("informational_uri")
    val informationalUri: String? = null,
) {
    companion object {
        /**
         * Creates a parent ingredient with the specified title.
         *
         * @param title The title of the parent ingredient.
         * @param format The MIME type of the ingredient.
         */
        fun parent(
            title: String,
            format: String? = null,
        ) = Ingredient(
            title = title,
            format = format,
            relationship = Relationship.PARENT_OF,
        )

        /**
         * Creates a component ingredient with the specified title.
         *
         * @param title The title of the component ingredient.
         * @param format The MIME type of the ingredient.
         */
        fun component(
            title: String,
            format: String? = null,
        ) = Ingredient(
            title = title,
            format = format,
            relationship = Relationship.COMPONENT_OF,
        )
    }
}
