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
 * Represents a temporal range for audio or video content.
 *
 * Times are specified as strings in the format defined by [type].
 * For NPT (Normal Play Time), values like "0:00:00", "1:30:00", or seconds like "90" are valid.
 *
 * @property start The start time as a string.
 * @property end The end time as a string.
 * @property type The time format type.
 * @see RegionRange
 */
@Serializable
data class Time(
    val start: String? = null,
    val end: String? = null,
    val type: TimeType? = null,
)
