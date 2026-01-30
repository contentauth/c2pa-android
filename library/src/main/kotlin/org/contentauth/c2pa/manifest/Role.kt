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
 * Defines the role of a region within an asset.
 *
 * @see RegionOfInterest
 */
@Serializable
enum class Role {
    /** A general area of interest within the asset. */
    @SerialName("c2pa.areaOfInterest")
    AREA_OF_INTEREST,

    /** A region that has been edited or modified. */
    @SerialName("c2pa.edited")
    EDITED,

    /** A region where content was placed from another source. */
    @SerialName("c2pa.placed")
    PLACED,

    /** A region that has been cropped. */
    @SerialName("c2pa.cropped")
    CROPPED,

    /** A region that has been deleted or removed. */
    @SerialName("c2pa.deleted")
    DELETED,

    /** A region where invisible watermark was added. */
    @SerialName("c2pa.invisible")
    INVISIBLE,
}
