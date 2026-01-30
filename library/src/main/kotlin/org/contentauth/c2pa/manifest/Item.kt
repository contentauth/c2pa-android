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
 * Represents an identified item within a container.
 *
 * Used for identifying specific elements within structured content.
 *
 * @property identifier The identifier scheme or type.
 * @property value The identifier value.
 * @see RegionRange
 */
@Serializable
data class Item(
    val identifier: String,
    val value: String,
)
