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
 * Describes the source of assertion data.
 *
 * @property type The type of data source.
 * @property details Additional details about the data source.
 * @property actors The actors (people or entities) associated with this data source.
 * @see Metadata
 */
@Serializable
data class DataSource(
    val type: String? = null,
    val details: String? = null,
    val actors: List<MetadataActor>? = null,
)
