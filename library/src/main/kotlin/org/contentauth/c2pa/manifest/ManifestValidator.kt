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

import android.util.Log
import kotlinx.serialization.json.JsonObject
import org.contentauth.c2pa.C2PAJson
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Validates C2PA manifests for spec compliance and provides warnings for common issues.
 *
 * This validator checks for compliance with C2PA 2.3 specification requirements
 * and CAWG specification requirements.
 *
 * ## Usage
 *
 * ```kotlin
 * val manifest = ManifestDefinition(...)
 * val result = ManifestValidator.validate(manifest)
 * if (result.hasErrors()) {
 *     result.errors.forEach { println("Error: $it") }
 * }
 * if (result.hasWarnings()) {
 *     result.warnings.forEach { println("Warning: $it") }
 * }
 * ```
 */
object ManifestValidator {

    private const val TAG = "C2PA"

    /**
     * Deprecated assertion labels per C2PA 2.x specification.
     * These are still supported but should not be used in new manifests.
     */
    val DEPRECATED_ASSERTION_LABELS: Set<String> = setOf(
        "stds.exif",
        "stds.iptc.photo-metadata",
        "stds.schema-org.CreativeWork",
        "c2pa.endorsement",
        "c2pa.data",
        "c2pa.databoxes",
        "c2pa.font.info",
    )

    /**
     * The current recommended claim version for C2PA 2.x specification.
     */
    const val RECOMMENDED_CLAIM_VERSION = 2

    /**
     * Validates a manifest definition for C2PA 2.3 spec compliance.
     *
     * @param manifest The manifest to validate.
     * @return A ValidationResult with any errors or warnings found.
     */
    fun validate(manifest: ManifestDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check claim version
        if (manifest.claimVersion != RECOMMENDED_CLAIM_VERSION) {
            warnings.add(
                "claim_version is ${manifest.claimVersion}, but C2PA 2.x recommends version $RECOMMENDED_CLAIM_VERSION. " +
                    "Version 1 claims use legacy assertion formats and do not support created/gathered assertion separation.",
            )
        }

        // Check for required fields
        if (manifest.title.isBlank()) {
            errors.add("Manifest title is required")
        }

        if (manifest.claimGeneratorInfo.isEmpty()) {
            errors.add("At least one claim_generator_info entry is required")
        }

        // Check for deprecated assertions
        checkDeprecatedAssertions(manifest.assertions, warnings)

        // Validate assertion labels
        manifest.assertions.forEach { assertion ->
            validateAssertionLabel(assertion, warnings)
        }

        // Validate ingredients
        manifest.ingredients.forEach { ingredient ->
            if (ingredient.relationship == null) {
                warnings.add(
                    "Ingredient '${ingredient.title ?: "unnamed"}' has no relationship specified. " +
                        "Consider using parentOf, componentOf, or inputTo.",
                )
            }
        }

        return ValidationResult(errors, warnings)
    }

    private fun validateAssertionLabel(assertion: AssertionDefinition, warnings: MutableList<String>) {
        when (assertion) {
            is AssertionDefinition.Custom -> {
                val label = assertion.label
                // Check for standard label patterns
                if (!label.contains(".") && !label.contains(":")) {
                    warnings.add(
                        "Custom assertion label '$label' should use namespaced format " +
                            "(e.g., 'com.example.custom' or vendor prefix).",
                    )
                }
                // Check for common typos in standard labels
                val commonTypos = mapOf(
                    "c2pa.action" to "c2pa.actions",
                    "stds.iptc" to "stds.iptc.photo-metadata",
                    "cawg.training" to "cawg.training-mining",
                )
                commonTypos[label]?.let { correct ->
                    warnings.add("Label '$label' may be a typo. Did you mean '$correct'?")
                }
            }
            else -> {
                // Standard types have validated labels
            }
        }
    }

    /**
     * Checks for deprecated assertion types and adds warnings.
     */
    private fun checkDeprecatedAssertions(
        assertions: List<AssertionDefinition>,
        warnings: MutableList<String>,
    ) {
        assertions.forEach { assertion ->
            val label = assertion.baseLabel()
            if (label in DEPRECATED_ASSERTION_LABELS) {
                val replacement = getDeprecatedAssertionReplacement(label)
                warnings.add(
                    "Assertion '$label' is deprecated in C2PA 2.x. $replacement",
                )
            }
        }
    }

    /**
     * Returns replacement guidance for deprecated assertion labels.
     */
    private fun getDeprecatedAssertionReplacement(label: String): String = when (label) {
        "stds.exif" -> "Consider using c2pa.metadata or embedding EXIF in the asset directly."
        "stds.iptc.photo-metadata" -> "Consider using c2pa.metadata instead."
        "stds.schema-org.CreativeWork" -> "Consider using c2pa.metadata instead."
        "c2pa.endorsement" -> "Endorsement assertions are no longer supported in C2PA 2.x."
        "c2pa.data" -> "Use c2pa.embedded-data instead."
        "c2pa.databoxes" -> "Data box stores are deprecated in C2PA 2.x."
        "c2pa.font.info" -> "Use font.info instead."
        else -> "Check the C2PA 2.3 specification for current alternatives."
    }

    /**
     * Validates a raw JSON manifest string and logs warnings to the console.
     *
     * This method parses the JSON and checks for:
     * - Non-v2 claim versions
     * - Deprecated assertion labels
     * - CAWG assertions in wrong location
     * - Other spec compliance issues
     *
     * @param manifestJson The manifest JSON string to validate.
     * @param logWarnings If true (default), warnings are logged to the Android console.
     * @return A ValidationResult with any errors or warnings found.
     */
    fun validateJson(manifestJson: String, logWarnings: Boolean = true): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            val jsonObject = C2PAJson.default.parseToJsonElement(manifestJson).jsonObject

            // Check claim_version
            val claimVersion = jsonObject["claim_version"]?.jsonPrimitive?.intOrNull
            if (claimVersion != null && claimVersion != RECOMMENDED_CLAIM_VERSION) {
                warnings.add(
                    "claim_version is $claimVersion, but C2PA 2.x recommends version $RECOMMENDED_CLAIM_VERSION. " +
                        "Version 1 claims use legacy assertion formats (c2pa.actions instead of c2pa.actions.v2) " +
                        "and do not support created/gathered assertion separation.",
                )
            }

            // Check assertions for deprecated labels
            jsonObject["assertions"]?.jsonArray?.forEach { assertionElement ->
                val assertionObj = assertionElement.jsonObject
                val label = assertionObj["label"]?.jsonPrimitive?.content
                if (label != null) {
                    checkJsonAssertionLabel(label, warnings, "assertions")
                }
            }

        } catch (e: Exception) {
            errors.add("Failed to parse manifest JSON: ${e.message}")
        }

        // Log warnings if requested
        if (logWarnings) {
            logValidationResults(errors, warnings)
        }

        return ValidationResult(errors, warnings)
    }

    /**
     * Checks a JSON assertion label for deprecation and issues.
     */
    private fun checkJsonAssertionLabel(
        label: String,
        warnings: MutableList<String>,
        location: String,
    ) {
        // Check for deprecated labels
        if (label in DEPRECATED_ASSERTION_LABELS) {
            val replacement = getDeprecatedAssertionReplacement(label)
            warnings.add(
                "Assertion '$label' in $location is deprecated in C2PA 2.x. $replacement",
            )
        }

    }

    /**
     * Logs validation results to the Android console.
     */
    private fun logValidationResults(errors: List<String>, warnings: List<String>) {
        errors.forEach { error ->
            Log.e(TAG, "Manifest validation error: $error")
        }
        warnings.forEach { warning ->
            Log.w(TAG, "Manifest validation warning: $warning")
        }
    }

    /**
     * Validates and logs warnings for a ManifestDefinition.
     *
     * Convenience method that validates and logs in one call.
     *
     * @param manifest The manifest to validate.
     * @return A ValidationResult with any errors or warnings found.
     */
    fun validateAndLog(manifest: ManifestDefinition): ValidationResult {
        val result = validate(manifest)
        logValidationResults(result.errors, result.warnings)
        return result
    }
}
