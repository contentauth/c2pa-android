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
 * Labels marked "c2pa-rs" are recognized natively by the underlying c2pa-rs SDK.
 * Labels marked "spec-only" are defined in the C2PA or CAWG specifications but not
 * currently implemented as named constants in c2pa-rs; they can still be used as
 * custom assertions and will be passed through by the SDK.
 *
 * @see AssertionDefinition
 */
@Serializable
enum class StandardAssertionLabel {
    /** Actions performed on the asset (deprecated, use ACTIONS_V2). [c2pa-rs] */
    @SerialName("c2pa.actions")
    ACTIONS,

    /** Actions performed on the asset (version 2). [c2pa-rs] */
    @SerialName("c2pa.actions.v2")
    ACTIONS_V2,

    /** Assertion metadata. [c2pa-rs] */
    @SerialName("c2pa.assertion.metadata")
    ASSERTION_METADATA,

    /** Asset reference assertion. [c2pa-rs] */
    @SerialName("c2pa.asset-ref")
    ASSET_REF,

    /** Asset type assertion. [c2pa-rs] */
    @SerialName("c2pa.asset-type")
    ASSET_TYPE,

    /** Asset type assertion (version 2). [spec-only, not in c2pa-rs] */
    @SerialName("c2pa.asset-type.v2")
    ASSET_TYPE_V2,

    /** Certificate status assertion. [c2pa-rs] */
    @SerialName("c2pa.certificate-status")
    CERTIFICATE_STATUS,

    /** Cloud data assertion. [c2pa-rs] */
    @SerialName("c2pa.cloud-data")
    CLOUD_DATA,

    /** Base depthmap assertion. [c2pa-rs] */
    @SerialName("c2pa.depthmap")
    DEPTHMAP,

    /** GDepth depthmap assertion. [c2pa-rs] */
    @SerialName("c2pa.depthmap.GDepth")
    DEPTHMAP_GDEPTH,

    /** Embedded data assertion. [c2pa-rs] */
    @SerialName("c2pa.embedded-data")
    EMBEDDED_DATA,

    /** Hash data for the asset. [c2pa-rs] */
    @SerialName("c2pa.hash.data")
    HASH_DATA,

    /** Box hash data. [c2pa-rs] */
    @SerialName("c2pa.hash.boxes")
    HASH_BOXES,

    /** BMFF hash data (base label, auto-versioned by SDK). [c2pa-rs] */
    @SerialName("c2pa.hash.bmff")
    HASH_BMFF,

    /** Collection hash data. [c2pa-rs] */
    @SerialName("c2pa.hash.collection.data")
    HASH_COLLECTION,

    /** Icon assertion. [c2pa-rs] */
    @SerialName("c2pa.icon")
    ICON,

    /** Ingredient assertion (base label, auto-versioned by SDK). [c2pa-rs] */
    @SerialName("c2pa.ingredient")
    INGREDIENT,

    /** JSON-LD metadata assertion. [c2pa-rs] */
    @SerialName("c2pa.metadata")
    METADATA,

    /** Soft binding assertion. [c2pa-rs] */
    @SerialName("c2pa.soft-binding")
    SOFT_BINDING,

    /** Thumbnail claim assertion. [c2pa-rs] */
    @SerialName("c2pa.thumbnail.claim")
    THUMBNAIL_CLAIM,

    /** Ingredient thumbnail assertion. [c2pa-rs] */
    @SerialName("c2pa.thumbnail.ingredient")
    THUMBNAIL_INGREDIENT,

    /** Time-stamp assertion. [c2pa-rs] */
    @SerialName("c2pa.time-stamp")
    TIME_STAMP,

    /** Training/Mining assertion. [spec-only, not in c2pa-rs] */
    @SerialName("c2pa.training-mining")
    TRAINING_MINING,

    /** Font information assertion. [spec-only, not in c2pa-rs] */
    @SerialName("font.info")
    FONT_INFO,

    /** EXIF metadata assertion (deprecated). [c2pa-rs] */
    @SerialName("stds.exif")
    EXIF,

    /** Schema.org Creative Work assertion (deprecated). [c2pa-rs] */
    @SerialName("stds.schema-org.CreativeWork")
    CREATIVE_WORK,

    /** Schema.org Claim Review assertion. [c2pa-rs] */
    @SerialName("stds.schema-org.ClaimReview")
    CLAIM_REVIEW,

    /** IPTC photo metadata assertion (deprecated). [c2pa-rs] */
    @SerialName("stds.iptc.photo-metadata")
    IPTC_PHOTO_METADATA,

    /** ISO location assertion. [spec-only, not in c2pa-rs] */
    @SerialName("stds.iso.location.v1")
    ISO_LOCATION,

    /** CAWG metadata assertion. [c2pa-rs] */
    @SerialName("cawg.metadata")
    CAWG_METADATA,

    /** CAWG training and data mining assertion. [spec-only, not in c2pa-rs] */
    @SerialName("cawg.training-mining")
    CAWG_AI_TRAINING,
    ;

    /** Returns the serialized label string for this assertion type. */
    fun serialName(): String = when (this) {
        ACTIONS -> "c2pa.actions"
        ACTIONS_V2 -> "c2pa.actions.v2"
        ASSERTION_METADATA -> "c2pa.assertion.metadata"
        ASSET_REF -> "c2pa.asset-ref"
        ASSET_TYPE -> "c2pa.asset-type"
        ASSET_TYPE_V2 -> "c2pa.asset-type.v2"
        CERTIFICATE_STATUS -> "c2pa.certificate-status"
        CLOUD_DATA -> "c2pa.cloud-data"
        DEPTHMAP -> "c2pa.depthmap"
        DEPTHMAP_GDEPTH -> "c2pa.depthmap.GDepth"
        EMBEDDED_DATA -> "c2pa.embedded-data"
        HASH_DATA -> "c2pa.hash.data"
        HASH_BOXES -> "c2pa.hash.boxes"
        HASH_BMFF -> "c2pa.hash.bmff"
        HASH_COLLECTION -> "c2pa.hash.collection.data"
        ICON -> "c2pa.icon"
        INGREDIENT -> "c2pa.ingredient"
        METADATA -> "c2pa.metadata"
        SOFT_BINDING -> "c2pa.soft-binding"
        THUMBNAIL_CLAIM -> "c2pa.thumbnail.claim"
        THUMBNAIL_INGREDIENT -> "c2pa.thumbnail.ingredient"
        TIME_STAMP -> "c2pa.time-stamp"
        TRAINING_MINING -> "c2pa.training-mining"
        FONT_INFO -> "font.info"
        EXIF -> "stds.exif"
        CREATIVE_WORK -> "stds.schema-org.CreativeWork"
        CLAIM_REVIEW -> "stds.schema-org.ClaimReview"
        IPTC_PHOTO_METADATA -> "stds.iptc.photo-metadata"
        ISO_LOCATION -> "stds.iso.location.v1"
        CAWG_METADATA -> "cawg.metadata"
        CAWG_AI_TRAINING -> "cawg.training-mining"
    }
}
