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

package org.contentauth.c2pa

/**
 * Predefined C2PA action types as specified in the C2PA specification.
 *
 * These actions describe common operations performed on content. Use these with [Action] to
 * document the editing history of an asset.
 *
 * @property value The C2PA action identifier string
 * @see Action
 * @see Builder.addAction
 */
enum class PredefinedAction(val value: String) {
    /**
     * (visible) Textual content was inserted into the asset, such as on a text layer or as a
     * caption.
     */
    ADDED_TEXT("c2pa.addedText"),

    /** Changes to tone, saturation, etc. */
    ADJUSTED_COLOR("c2pa.adjustedColor"),

    /** Reduced or increased playback speed of a video or audio track. */
    CHANGED_SPEED("c2pa.changedSpeed"),

    /** The format of the asset was changed. */
    CONVERTED("c2pa.converted"),

    /** The asset was first created. */
    CREATED("c2pa.created"),

    /** Areas of the asset's digital content were cropped out. */
    CROPPED("c2pa.cropped"),

    /** Areas of the asset's digital content were deleted. */
    DELETED("c2pa.deleted"),

    /** Changes using drawing tools including brushes or eraser. */
    DRAWING("c2pa.drawing"),

    /** Changes were made to audio, usually one or more tracks of a composite asset. */
    DUBBED("c2pa.dubbed"),

    /** Generalized actions that would be considered editorial transformations of the content. */
    EDITED("c2pa.edited"),

    /**
     * Modifications to asset metadata or a metadata assertion but not the asset's digital content.
     */
    EDITED_METADATA("c2pa.edited.metadata"),

    /**
     * Applied enhancements such as noise reduction, multi-band compression, or sharpening that
     * represent non-editorial transformations of the content.
     */
    ENHANCED("c2pa.enhanced"),

    /** Changes to appearance with applied filters, styles, etc. */
    FILTERED("c2pa.filtered"),

    /** An existing asset was opened and is being set as the parentOf ingredient. */
    OPENED("c2pa.opened"),

    /** Changes to the direction and position of content. */
    ORIENTATION("c2pa.orientation"),

    /** Added/Placed one or more componentOf ingredient(s) into the asset. */
    PLACED("c2pa.placed"),

    /** Asset is released to a wider audience. */
    PUBLISHED("c2pa.published"),

    /** One or more assertions were redacted. */
    REDACTED("c2pa.redacted"),

    /** A componentOf ingredient was removed. */
    REMOVED("c2pa.removed"),

    /**
     * A conversion of one packaging or container format to another. Content is repackaged without
     * transcoding. This action is considered as a non-editorial transformation of the parentOf
     * ingredient.
     */
    REPACKAGED("c2pa.repackaged"),

    /** Changes to either content dimensions, its file size or both. */
    RESIZED("c2pa.resized"),

    /**
     * A conversion of one encoding to another, including resolution scaling, bitrate adjustment and
     * encoding format change. This action is considered as a non-editorial transformation of the
     * parentOf ingredient.
     */
    TRANSCODED("c2pa.transcoded"),

    /** Changes to the language of the content. */
    TRANSLATED("c2pa.translated"),

    /** Removal of a temporal range of the content. */
    TRIMMED("c2pa.trimmed"),

    /** Something happened, but the claim_generator cannot specify what. */
    UNKNOWN("c2pa.unknown"),

    /**
     * An invisible watermark was inserted into the digital content for the purpose of creating a
     * soft binding.
     */
    WATERMARKED("c2pa.watermarked"),
}
