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
 * Represents a region of interest within an asset.
 *
 * Regions of interest can identify specific areas of content that have been
 * modified, contain specific subjects, or are otherwise notable.
 *
 * @property region The list of ranges defining this region.
 * @property description A human-readable description of the region.
 * @property name A name for the region.
 * @property identifier A unique identifier for the region.
 * @property role The role of this region (what action was performed).
 * @property type The IPTC type of content in this region.
 * @property metadata Additional metadata about the region.
 * @see RegionRange
 * @see Role
 * @see ImageRegionType
 */
@Serializable
data class RegionOfInterest(
    val region: List<RegionRange>,
    val description: String? = null,
    val name: String? = null,
    val identifier: String? = null,
    val role: Role? = null,
    @SerialName("type")
    val regionType: String? = null,
    val metadata: Metadata? = null,
) {
    /**
     * Creates a region of interest with an IPTC image region type.
     */
    constructor(
        region: List<RegionRange>,
        description: String? = null,
        name: String? = null,
        identifier: String? = null,
        role: Role? = null,
        imageRegionType: ImageRegionType,
        metadata: Metadata? = null,
    ) : this(
        region = region,
        description = description,
        name = name,
        identifier = identifier,
        role = role,
        regionType = imageRegionType.toTypeString(),
        metadata = metadata,
    )

    companion object {
        /**
         * Creates a spatial region of interest.
         *
         * @param shape The shape defining the spatial region.
         * @param role The role of the region.
         * @param description An optional description.
         */
        fun spatial(
            shape: Shape,
            role: Role? = null,
            description: String? = null,
        ) = RegionOfInterest(
            region = listOf(RegionRange.spatial(shape)),
            role = role,
            description = description,
        )

        /**
         * Creates a temporal region of interest.
         *
         * @param time The time range.
         * @param role The role of the region.
         * @param description An optional description.
         */
        fun temporal(
            time: Time,
            role: Role? = null,
            description: String? = null,
        ) = RegionOfInterest(
            region = listOf(RegionRange.temporal(time)),
            role = role,
            description = description,
        )
    }
}
