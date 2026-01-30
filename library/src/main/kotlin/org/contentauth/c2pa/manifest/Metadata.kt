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
 * Container for assertion metadata.
 *
 * @property dataSource The source of the data in this assertion.
 * @property reference A reference identifier for this metadata.
 * @property regionOfInterest Regions of interest associated with this metadata.
 * @property reviewRatings Review ratings for this assertion.
 * @property dateTime The date/time associated with this metadata (ISO 8601 format).
 * @see RegionOfInterest
 * @see DataSource
 */
@Serializable
data class Metadata(
    @SerialName("dataSource")
    val dataSource: DataSource? = null,
    val reference: String? = null,
    @SerialName("regionOfInterest")
    val regionOfInterest: List<RegionOfInterest>? = null,
    @SerialName("reviewRatings")
    val reviewRatings: List<ReviewRating>? = null,
    @SerialName("dateTime")
    val dateTime: String? = null,
)
