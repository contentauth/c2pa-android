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
 * Defines the geometric shape type for spatial regions.
 *
 * @see Shape
 */
@Serializable
enum class ShapeType {
    /** A rectangular region defined by origin, width, and height. */
    @SerialName("rectangle")
    RECTANGLE,

    /** A circular region defined by origin (center) and radius (via width). */
    @SerialName("circle")
    CIRCLE,

    /** A polygon region defined by a list of vertices. */
    @SerialName("polygon")
    POLYGON,
}
