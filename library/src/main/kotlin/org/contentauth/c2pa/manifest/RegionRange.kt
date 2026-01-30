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
 * Represents a range within content that can be spatial, temporal, frame-based, textual, or identified.
 *
 * Only one of the range-specific properties should be set, corresponding to the [type].
 *
 * @property type The type of range (optional, can be inferred from which property is set).
 * @property frame Frame range for video/animation content.
 * @property time Temporal range for audio/video content.
 * @property shape Spatial shape for image/video regions.
 * @property text Textual range for document content.
 * @property item Identified item within a container.
 * @see RegionOfInterest
 */
@Serializable
data class RegionRange(
    val type: RangeType? = null,
    val frame: Frame? = null,
    val time: Time? = null,
    val shape: Shape? = null,
    val text: Text? = null,
    val item: Item? = null,
) {
    companion object {
        /**
         * Creates a spatial region range from a shape.
         */
        fun spatial(shape: Shape) = RegionRange(
            type = RangeType.SPATIAL,
            shape = shape,
        )

        /**
         * Creates a temporal region range from a time range.
         */
        fun temporal(time: Time) = RegionRange(
            type = RangeType.TEMPORAL,
            time = time,
        )

        /**
         * Creates a frame-based region range.
         */
        fun frame(frame: Frame) = RegionRange(
            type = RangeType.FRAME,
            frame = frame,
        )

        /**
         * Creates a textual region range.
         */
        fun textual(text: Text) = RegionRange(
            type = RangeType.TEXTUAL,
            text = text,
        )

        /**
         * Creates an identified region range.
         */
        fun identified(item: Item) = RegionRange(
            type = RangeType.IDENTIFIED,
            item = item,
        )
    }
}
