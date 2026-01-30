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
 * Represents an actor (person or entity) associated with metadata.
 *
 * @property identifier A unique identifier for the actor.
 * @property credentials A list of credential references for the actor.
 * @see DataSource
 */
@Serializable
data class MetadataActor(
    val identifier: String? = null,
    val credentials: List<HashedUri>? = null,
)
