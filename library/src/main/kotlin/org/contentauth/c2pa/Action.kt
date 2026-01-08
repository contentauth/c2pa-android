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

import org.json.JSONObject

/**
 * Represents a C2PA action that describes an operation performed on content.
 *
 * Actions are used to document the editing history of an asset, such as cropping, filtering, or
 * color adjustments.
 *
 * @property action The action name. Use [PredefinedAction] values or custom action strings.
 * @property digitalSourceType A URL identifying an IPTC digital source type. Use
 *   [DigitalSourceType] values or custom URLs.
 * @property softwareAgent The software or hardware used to perform the action.
 * @property parameters Additional information describing the action.
 * @see Builder.addAction
 * @see PredefinedAction
 */
data class Action(
    val action: String,
    val digitalSourceType: String? = null,
    val softwareAgent: String? = null,
    val parameters: Map<String, String>? = null,
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
        parameters: Map<String, String>? = null,
    ) : this(
        action = action.value,
        digitalSourceType = digitalSourceType.toIptcUrl(),
        softwareAgent = softwareAgent,
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
        parameters: Map<String, String>? = null,
    ) : this(
        action = action.value,
        digitalSourceType = null,
        softwareAgent = softwareAgent,
        parameters = parameters,
    )

    internal fun toJson(): String {
        val json = JSONObject()
        json.put("action", action)
        digitalSourceType?.let { json.put("digitalSourceType", it) }
        softwareAgent?.let { json.put("softwareAgent", it) }
        parameters?.let { params ->
            val paramsJson = JSONObject()
            params.forEach { (key, value) -> paramsJson.put(key, value) }
            json.put("parameters", paramsJson)
        }
        return json.toString()
    }
}

private fun DigitalSourceType.toIptcUrl(): String =
    when (this) {
        DigitalSourceType.EMPTY -> "http://c2pa.org/digitalsourcetype/empty"
        DigitalSourceType.TRAINED_ALGORITHMIC_DATA -> "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicData"
        DigitalSourceType.DIGITAL_CAPTURE -> "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture"
        DigitalSourceType.COMPUTATIONAL_CAPTURE -> "http://cv.iptc.org/newscodes/digitalsourcetype/computationalCapture"
        DigitalSourceType.NEGATIVE_FILM -> "http://cv.iptc.org/newscodes/digitalsourcetype/negativeFilm"
        DigitalSourceType.POSITIVE_FILM -> "http://cv.iptc.org/newscodes/digitalsourcetype/positiveFilm"
        DigitalSourceType.PRINT -> "http://cv.iptc.org/newscodes/digitalsourcetype/print"
        DigitalSourceType.HUMAN_EDITS -> "http://cv.iptc.org/newscodes/digitalsourcetype/humanEdits"
        DigitalSourceType.COMPOSITE_WITH_TRAINED_ALGORITHMIC_MEDIA -> "http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia"
        DigitalSourceType.ALGORITHMICALLY_ENHANCED -> "http://cv.iptc.org/newscodes/digitalsourcetype/algorithmicallyEnhanced"
        DigitalSourceType.DIGITAL_CREATION -> "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCreation"
        DigitalSourceType.DATA_DRIVEN_MEDIA -> "http://cv.iptc.org/newscodes/digitalsourcetype/dataDrivenMedia"
        DigitalSourceType.TRAINED_ALGORITHMIC_MEDIA -> "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"
        DigitalSourceType.ALGORITHMIC_MEDIA -> "http://cv.iptc.org/newscodes/digitalsourcetype/algorithmicMedia"
        DigitalSourceType.SCREEN_CAPTURE -> "http://cv.iptc.org/newscodes/digitalsourcetype/screenCapture"
        DigitalSourceType.VIRTUAL_RECORDING -> "http://cv.iptc.org/newscodes/digitalsourcetype/virtualRecording"
        DigitalSourceType.COMPOSITE -> "http://cv.iptc.org/newscodes/digitalsourcetype/composite"
        DigitalSourceType.COMPOSITE_CAPTURE -> "http://cv.iptc.org/newscodes/digitalsourcetype/compositeCapture"
        DigitalSourceType.COMPOSITE_SYNTHETIC -> "http://cv.iptc.org/newscodes/digitalsourcetype/compositeSynthetic"
    }
