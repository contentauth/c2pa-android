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
 * Defines the measurement unit for spatial coordinates.
 *
 * @see Shape
 */
@Serializable
enum class UnitType {
    /** Coordinates are in pixels. */
    @SerialName("pixel")
    PIXEL,

    /** Coordinates are in percentages of the asset dimensions. */
    @SerialName("percent")
    PERCENT,
}
