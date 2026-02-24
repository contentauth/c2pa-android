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

package org.contentauth.c2pa

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Represents a C2PA action that describes an operation performed on content.
 *
 * Actions are used to document the editing history of an asset, such as cropping, filtering, or
 * color adjustments. This class is used with the imperative [Builder.addAction] API.
 *
 * In C2PA v2, `softwareAgent` may be either a plain string (v1 format) or a
 * `generator-info-map` object (v2 format), represented here as [JsonElement].
 *
 * @property action The action name. Use [PredefinedAction] values or custom action strings.
 * @property digitalSourceType A URL identifying an IPTC digital source type. Use
 *   [DigitalSourceType] values or custom URLs.
 * @property softwareAgent The software or hardware used to perform the action (string or object).
 * @property parameters Additional information describing the action.
 * @see Builder.addAction
 * @see PredefinedAction
 */
data class Action(
    val action: String,
    val digitalSourceType: String? = null,
    val softwareAgent: JsonElement? = null,
    val parameters: Map<String, JsonElement>? = null,
) {
    /**
     * Creates an action using a [PredefinedAction] and [DigitalSourceType].
     *
     * @param action The predefined action type
     * @param digitalSourceType The digital source type for this action
     * @param softwareAgent The software or hardware used to perform the action
     * @param parameters Additional information describing the action
     */
    constructor(
        action: PredefinedAction,
        digitalSourceType: DigitalSourceType,
        softwareAgent: String? = null,
        parameters: Map<String, JsonElement>? = null,
    ) : this(
        action = action.value,
        digitalSourceType = digitalSourceType.toIptcUrl(),
        softwareAgent = softwareAgent?.let { JsonPrimitive(it) },
        parameters = parameters,
    )

    /**
     * Creates an action using a [PredefinedAction] without a digital source type.
     *
     * @param action The predefined action type
     * @param softwareAgent The software or hardware used to perform the action
     * @param parameters Additional information describing the action
     */
    constructor(
        action: PredefinedAction,
        softwareAgent: String? = null,
        parameters: Map<String, JsonElement>? = null,
    ) : this(
        action = action.value,
        digitalSourceType = null,
        softwareAgent = softwareAgent?.let { JsonPrimitive(it) },
        parameters = parameters,
    )

    internal fun toJson(): String = buildJsonObject {
        put("action", action)
        digitalSourceType?.let { put("digitalSourceType", it) }
        softwareAgent?.let { put("softwareAgent", it) }
        parameters?.let { params ->
            put("parameters", buildJsonObject {
                params.forEach { (key, value) -> put(key, value) }
            })
        }
    }.toString()
}
