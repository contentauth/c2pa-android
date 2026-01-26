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
 * Groups validation statuses by their outcome category.
 *
 * @property success List of successful validation statuses.
 * @property failure List of failed validation statuses.
 * @property informational List of informational validation statuses.
 * @see ValidationStatus
 * @see ValidationResults
 */
@Serializable
data class StatusCodes(
    val success: List<ValidationStatus>? = null,
    val failure: List<ValidationStatus>? = null,
    val informational: List<ValidationStatus>? = null,
)
