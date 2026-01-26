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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Validation results for an ingredient delta.
 *
 * @property ingredientAssertionUri The URI of the ingredient assertion being validated.
 * @property validationDeltas The validation status codes for this ingredient delta.
 * @see ValidationResults
 */
@Serializable
data class IngredientDeltaValidationResult(
    @SerialName("ingredient_assertion_uri")
    val ingredientAssertionUri: String,
    @SerialName("validation_deltas")
    val validationDeltas: StatusCodes,
)
