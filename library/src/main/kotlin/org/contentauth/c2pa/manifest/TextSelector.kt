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
 * Represents a text fragment selector for textual regions.
 *
 * Can specify text by fragment string and/or character positions.
 *
 * @property fragment A text fragment to match.
 * @property start The starting character position (inclusive).
 * @property end The ending character position (exclusive).
 * @see TextSelectorRange
 */
@Serializable
data class TextSelector(
    val fragment: String? = null,
    val start: Int? = null,
    val end: Int? = null,
)
