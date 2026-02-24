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

/**
 * Result of validation containing errors and warnings.
 *
 * Used by [ManifestValidator] to report validation outcomes.
 *
 * @property errors Critical issues that violate spec or schema requirements.
 * @property warnings Non-critical issues that may indicate misuse or misconfiguration.
 * @see ManifestValidator
 */
data class ManifestValidationResult(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    /** Returns true if there are any errors. */
    fun hasErrors(): Boolean = errors.isNotEmpty()

    /** Returns true if there are any warnings. */
    fun hasWarnings(): Boolean = warnings.isNotEmpty()

    /** Returns true if validation passed without errors. */
    fun isValid(): Boolean = !hasErrors()
}
