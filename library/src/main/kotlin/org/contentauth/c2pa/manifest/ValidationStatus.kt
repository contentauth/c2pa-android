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
 * Represents the validation status of a manifest or ingredient.
 *
 * The status code is kept as the string c2pa-rs reported, because the set of codes is
 * open-ended. Use [statusCode] to work with the codes this library lists.
 *
 * @property code The validation status code, such as `claimSignature.validated`.
 * @property explanation A human-readable explanation of the status.
 * @property success Whether this status indicates success.
 * @property url An optional URL with more information about this status.
 * @see ValidationStatusCode
 * @see Ingredient
 */
@Serializable
data class ValidationStatus(
    val code: String,
    val explanation: String? = null,
    val success: Boolean? = null,
    val url: String? = null,
) {

    /** The [ValidationStatusCode] for [code], or `null` if this library does not list the code. */
    val statusCode: ValidationStatusCode?
        get() = ValidationStatusCode.fromCode(code)

    /**
     * Creates a validation status from a listed [ValidationStatusCode].
     */
    constructor(
        code: ValidationStatusCode,
        explanation: String? = null,
        success: Boolean? = null,
        url: String? = null,
    ) : this(
        code = code.code,
        explanation = explanation,
        success = success,
        url = url,
    )
}
