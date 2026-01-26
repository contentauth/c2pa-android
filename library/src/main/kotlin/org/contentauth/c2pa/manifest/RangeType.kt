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
 * Defines the type of range for a region.
 *
 * @see RegionRange
 */
@Serializable
enum class RangeType {
    /** A spatial region defined by geometric shapes. */
    @SerialName("spatial")
    SPATIAL,

    /** A temporal region defined by time values. */
    @SerialName("temporal")
    TEMPORAL,

    /** A frame-based region defined by frame numbers. */
    @SerialName("frame")
    FRAME,

    /** A textual region defined by text selectors. */
    @SerialName("textual")
    TEXTUAL,

    /** An identified region within a container. */
    @SerialName("identified")
    IDENTIFIED,
}
