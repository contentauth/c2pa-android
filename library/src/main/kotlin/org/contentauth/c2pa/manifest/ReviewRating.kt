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
 * Represents a review rating for an assertion.
 *
 * @property code A code identifying the rating type.
 * @property explanation A human-readable explanation of the rating.
 * @property value The numeric rating value.
 * @see Metadata
 */
@Serializable
data class ReviewRating(
    val code: String? = null,
    val explanation: String? = null,
    val value: Int? = null,
)
