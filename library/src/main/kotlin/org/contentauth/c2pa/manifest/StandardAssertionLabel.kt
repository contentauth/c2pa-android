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
 * Standard C2PA assertion labels as defined in the C2PA specification.
 *
 * These labels identify the type of assertion in a manifest.
 *
 * @see AssertionDefinition
 */
@Serializable
enum class StandardAssertionLabel {
    /** Actions performed on the asset. */
    @SerialName("c2pa.actions")
    ACTIONS,

    /** Actions performed on the asset (version 2). */
    @SerialName("c2pa.actions.v2")
    ACTIONS_V2,

    /** Hash data for the asset. */
    @SerialName("c2pa.hash.data")
    HASH_DATA,

    /** Box hash data. */
    @SerialName("c2pa.hash.boxes")
    HASH_BOXES,

    /** BMFF v2 hash data. */
    @SerialName("c2pa.hash.bmff.v2")
    HASH_BMFF_V2,

    /** Collection hash data. */
    @SerialName("c2pa.hash.collection")
    HASH_COLLECTION,

    /** Soft binding assertion. */
    @SerialName("c2pa.soft-binding")
    SOFT_BINDING,

    /** Cloud data assertion. */
    @SerialName("c2pa.cloud-data")
    CLOUD_DATA,

    /** Thumbnail claim assertion. */
    @SerialName("c2pa.thumbnail.claim")
    THUMBNAIL_CLAIM,

    /** Ingredient thumbnail assertion. */
    @SerialName("c2pa.thumbnail.ingredient")
    THUMBNAIL_INGREDIENT,

    /** Depthmap assertion. */
    @SerialName("c2pa.depthmap")
    DEPTHMAP,

    /** Training/Mining assertion. */
    @SerialName("c2pa.training-mining")
    TRAINING_MINING,

    /** EXIF metadata assertion. */
    @SerialName("stds.exif")
    EXIF,

    /** Schema.org Creative Work assertion. */
    @SerialName("stds.schema-org.CreativeWork")
    CREATIVE_WORK,

    /** IPTC photo metadata assertion. */
    @SerialName("stds.iptc.photo-metadata")
    IPTC_PHOTO_METADATA,

    /** ISO location assertion. */
    @SerialName("stds.iso.location.v1")
    ISO_LOCATION,

    /** CAWG identity assertion. */
    @SerialName("cawg.identity")
    CAWG_IDENTITY,

    /** CAWG AI training and data mining assertion. */
    @SerialName("cawg.ai_training_and_data_mining")
    CAWG_AI_TRAINING,
}
