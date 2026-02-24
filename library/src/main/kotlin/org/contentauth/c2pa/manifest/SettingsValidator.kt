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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.contentauth.c2pa.C2PAJson
import org.contentauth.c2pa.SigningAlgorithm

/**
 * Validates C2PA settings JSON/TOML for schema compliance and provides warnings for common issues.
 *
 * This validator checks settings against the C2PA settings schema documented at:
 * https://opensource.contentauthenticity.org/docs/rust-sdk/docs/context-settings
 *
 * ## Usage
 *
 * ```kotlin
 * val settingsJson = """{"version": 1, "verify": {"verify_trust": false}}"""
 * val result = SettingsValidator.validate(settingsJson)
 * if (result.hasErrors()) {
 *     result.errors.forEach { println("Error: $it") }
 * }
 * if (result.hasWarnings()) {
 *     result.warnings.forEach { println("Warning: $it") }
 * }
 * ```
 */
object SettingsValidator {

    private const val TAG = "C2PA"

    /**
     * Currently supported settings format version.
     */
    const val SUPPORTED_VERSION = 1

    /**
     * Valid signing algorithms for C2PA, derived from [SigningAlgorithm] enum values.
     */
    val VALID_ALGORITHMS: Set<String> = SigningAlgorithm.entries.map { it.description }.toSet()

    /**
     * Valid thumbnail formats.
     */
    val VALID_THUMBNAIL_FORMATS: Set<String> = setOf("jpeg", "png", "webp")

    /**
     * Valid thumbnail quality settings.
     */
    val VALID_THUMBNAIL_QUALITIES: Set<String> = setOf("low", "medium", "high")

    /**
     * Valid intent string values (non-object form).
     */
    val VALID_INTENT_STRINGS: Set<String> = setOf("Edit", "Update")

    /**
     * Valid digital source types for actions.
     */
    val VALID_SOURCE_TYPES: Set<String> = setOf(
        "empty",
        "digitalCapture",
        "negativeFilm",
        "positiveFilm",
        "print",
        "minorHumanEdits",
        "compositeCapture",
        "algorithmicallyEnhanced",
        "dataDrivenMedia",
        "digitalArt",
        "compositeWithTrainedAlgorithmicMedia",
        "compositeSynthetic",
        "trainedAlgorithmicMedia",
        "algorithmicMedia",
        "virtualRecording",
        "composite",
        "softwareRendered",
        "generatedByAI",
    )

    /**
     * Known top-level settings sections.
     */
    val KNOWN_TOP_LEVEL_KEYS: Set<String> = setOf(
        "version",
        "trust",
        "cawg_trust",
        "core",
        "verify",
        "builder",
        "signer",
        "cawg_x509_signer",
    )

    /**
     * Validates a settings JSON string.
     *
     * @param settingsJson The settings JSON string to validate.
     * @param logWarnings If true (default), warnings are logged to the Android console.
     * @return A ValidationResult with any errors or warnings found.
     */
    fun validate(settingsJson: String, logWarnings: Boolean = true): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            val jsonObject = C2PAJson.default.parseToJsonElement(settingsJson).jsonObject
            validateSettingsObject(jsonObject, errors, warnings)
        } catch (e: Exception) {
            errors.add("Failed to parse settings JSON: ${e.message}")
        }

        if (logWarnings) {
            logValidationResults(errors, warnings)
        }

        return ValidationResult(errors, warnings)
    }

    /**
     * Validates a parsed settings JSON object.
     */
    private fun validateSettingsObject(
        settings: JsonObject,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        // Check for unknown top-level keys
        settings.keys.forEach { key ->
            if (key !in KNOWN_TOP_LEVEL_KEYS) {
                warnings.add("Unknown top-level key: '$key'")
            }
        }

        // Validate version
        validateVersion(settings, errors)

        // Validate each section
        settings["trust"]?.jsonObject?.let { validateTrustSection(it, "trust", errors, warnings) }
        settings["cawg_trust"]?.jsonObject?.let { validateCawgTrustSection(it, errors, warnings) }
        settings["core"]?.jsonObject?.let { validateCoreSection(it, errors, warnings) }
        settings["verify"]?.jsonObject?.let { validateVerifySection(it, errors, warnings) }
        settings["builder"]?.jsonObject?.let { validateBuilderSection(it, errors, warnings) }
        settings["signer"]?.jsonObject?.let { validateSignerSection(it, "signer", errors, warnings) }
        settings["cawg_x509_signer"]?.jsonObject?.let {
            validateSignerSection(it, "cawg_x509_signer", errors, warnings)
        }
    }

    /**
     * Validates the version field.
     */
    private fun validateVersion(settings: JsonObject, errors: MutableList<String>) {
        val version = settings["version"]?.jsonPrimitive?.intOrNull
        if (version == null) {
            errors.add("'version' is required and must be an integer")
        } else if (version != SUPPORTED_VERSION) {
            errors.add("'version' must be $SUPPORTED_VERSION, got $version")
        }
    }

    /**
     * Validates trust section (shared structure for trust and cawg_trust).
     */
    private fun validateTrustSection(
        trust: JsonObject,
        sectionName: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf("user_anchors", "trust_anchors", "trust_config", "allowed_list", "verify_trust_list")

        trust.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in $sectionName: '$key'")
            }
        }

        // Validate PEM format for certificate fields
        listOf("user_anchors", "trust_anchors", "allowed_list").forEach { field ->
            trust[field]?.jsonPrimitive?.content?.let { pemString ->
                if (!isValidPEM(pemString, "CERTIFICATE")) {
                    errors.add("$sectionName.$field must be valid PEM-formatted certificate(s)")
                }
            }
        }
    }

    /**
     * Validates cawg_trust section.
     */
    private fun validateCawgTrustSection(
        cawgTrust: JsonObject,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        validateTrustSection(cawgTrust, "cawg_trust", errors, warnings)

        // verify_trust_list specific to cawg_trust
        cawgTrust["verify_trust_list"]?.let { element ->
            if (element.jsonPrimitive.booleanOrNull == null) {
                errors.add("cawg_trust.verify_trust_list must be a boolean")
            }
        }
    }

    /**
     * Validates core section.
     */
    private fun validateCoreSection(
        core: JsonObject,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf(
            "merkle_tree_chunk_size_in_kb",
            "merkle_tree_max_proofs",
            "backing_store_memory_threshold_in_mb",
            "decode_identity_assertions",
            "allowed_network_hosts",
        )

        core.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in core: '$key'")
            }
        }

        // Validate numeric fields
        listOf(
            "merkle_tree_chunk_size_in_kb",
            "merkle_tree_max_proofs",
            "backing_store_memory_threshold_in_mb",
        ).forEach { field ->
            core[field]?.let { element ->
                if (element.jsonPrimitive.intOrNull == null) {
                    errors.add("core.$field must be a number")
                }
            }
        }

        // Validate boolean field
        core["decode_identity_assertions"]?.let { element ->
            if (element.jsonPrimitive.booleanOrNull == null) {
                errors.add("core.decode_identity_assertions must be a boolean")
            }
        }

        // Validate allowed_network_hosts is an array
        core["allowed_network_hosts"]?.let { element ->
            try {
                element.jsonArray
            } catch (e: Exception) {
                errors.add("core.allowed_network_hosts must be an array")
            }
        }
    }

    /**
     * Validates verify section.
     */
    private fun validateVerifySection(
        verify: JsonObject,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf(
            "verify_after_reading",
            "verify_after_sign",
            "verify_trust",
            "verify_timestamp_trust",
            "ocsp_fetch",
            "remote_manifest_fetch",
            "skip_ingredient_conflict_resolution",
            "strict_v1_validation",
        )

        verify.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in verify: '$key'")
            }
        }

        // All verify fields are booleans
        validKeys.forEach { field ->
            verify[field]?.let { element ->
                if (element.jsonPrimitive.booleanOrNull == null) {
                    errors.add("verify.$field must be a boolean")
                }
            }
        }

        // Warn about disabling verification
        listOf("verify_trust", "verify_timestamp_trust", "verify_after_sign").forEach { field ->
            verify[field]?.jsonPrimitive?.booleanOrNull?.let { value ->
                if (!value) {
                    warnings.add(
                        "verify.$field is set to false. This may result in verification behavior " +
                            "that is not fully compliant with the C2PA specification.",
                    )
                }
            }
        }
    }

    /**
     * Validates builder section.
     */
    private fun validateBuilderSection(
        builder: JsonObject,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf(
            "claim_generator_info",
            "certificate_status_fetch",
            "certificate_status_should_override",
            "intent",
            "created_assertion_labels",
            "generate_c2pa_archive",
            "actions",
            "thumbnail",
        )

        builder.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in builder: '$key'")
            }
        }

        // Validate intent
        builder["intent"]?.let { validateIntent(it, errors) }

        // Validate thumbnail
        builder["thumbnail"]?.jsonObject?.let { validateThumbnailSection(it, errors, warnings) }

        // Validate actions
        builder["actions"]?.jsonObject?.let { validateActionsSection(it, errors, warnings) }

        // Validate claim_generator_info
        builder["claim_generator_info"]?.jsonObject?.let { info ->
            if (info["name"] == null) {
                errors.add("builder.claim_generator_info.name is required when claim_generator_info is specified")
            }
        }

        // Validate created_assertion_labels is an array
        builder["created_assertion_labels"]?.let { element ->
            try {
                element.jsonArray
            } catch (e: Exception) {
                errors.add("builder.created_assertion_labels must be an array")
            }
        }

        // Validate boolean fields
        builder["generate_c2pa_archive"]?.let { element ->
            if (element.jsonPrimitive.booleanOrNull == null) {
                errors.add("builder.generate_c2pa_archive must be a boolean")
            }
        }
    }

    /**
     * Validates intent value.
     */
    private fun validateIntent(intent: JsonElement, errors: MutableList<String>) {
        when {
            intent is JsonPrimitive && intent.isString -> {
                val intentString = intent.content
                if (intentString !in VALID_INTENT_STRINGS) {
                    errors.add(
                        "builder.intent string must be one of: ${VALID_INTENT_STRINGS.joinToString()}, " +
                            "got '$intentString'",
                    )
                }
            }
            intent is JsonObject -> {
                // Should be {"Create": "sourceType"}
                val createValue = intent["Create"]?.jsonPrimitive?.content
                if (createValue == null) {
                    errors.add("builder.intent object must have 'Create' key with source type value")
                } else if (createValue !in VALID_SOURCE_TYPES) {
                    errors.add(
                        "builder.intent Create source type must be one of: ${VALID_SOURCE_TYPES.joinToString()}, " +
                            "got '$createValue'",
                    )
                }
            }
            else -> {
                errors.add("builder.intent must be a string ('Edit', 'Update') or object ({\"Create\": \"sourceType\"})")
            }
        }
    }

    /**
     * Validates thumbnail section.
     */
    private fun validateThumbnailSection(
        thumbnail: JsonObject,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf("enabled", "ignore_errors", "long_edge", "format", "prefer_smallest_format", "quality")

        thumbnail.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in builder.thumbnail: '$key'")
            }
        }

        // Validate format
        thumbnail["format"]?.jsonPrimitive?.content?.let { format ->
            if (format !in VALID_THUMBNAIL_FORMATS) {
                errors.add(
                    "builder.thumbnail.format must be one of: ${VALID_THUMBNAIL_FORMATS.joinToString()}, " +
                        "got '$format'",
                )
            }
        }

        // Validate quality
        thumbnail["quality"]?.jsonPrimitive?.content?.let { quality ->
            if (quality !in VALID_THUMBNAIL_QUALITIES) {
                errors.add(
                    "builder.thumbnail.quality must be one of: ${VALID_THUMBNAIL_QUALITIES.joinToString()}, " +
                        "got '$quality'",
                )
            }
        }

        // Validate long_edge is a number
        thumbnail["long_edge"]?.let { element ->
            if (element.jsonPrimitive.intOrNull == null) {
                errors.add("builder.thumbnail.long_edge must be a number")
            }
        }

        // Validate boolean fields
        listOf("enabled", "ignore_errors", "prefer_smallest_format").forEach { field ->
            thumbnail[field]?.let { element ->
                if (element.jsonPrimitive.booleanOrNull == null) {
                    errors.add("builder.thumbnail.$field must be a boolean")
                }
            }
        }
    }

    /**
     * Validates actions section.
     */
    private fun validateActionsSection(
        actions: JsonObject,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf(
            "all_actions_included",
            "templates",
            "actions",
            "auto_created_action",
            "auto_opened_action",
            "auto_placed_action",
        )

        actions.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in builder.actions: '$key'")
            }
        }

        // Validate auto action sections
        listOf("auto_created_action", "auto_opened_action", "auto_placed_action").forEach { actionType ->
            actions[actionType]?.jsonObject?.let { autoAction ->
                validateAutoAction(autoAction, "builder.actions.$actionType", errors, warnings)
            }
        }
    }

    /**
     * Validates auto action configuration.
     */
    private fun validateAutoAction(
        autoAction: JsonObject,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf("enabled", "source_type")

        autoAction.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in $path: '$key'")
            }
        }

        // Validate enabled is boolean
        autoAction["enabled"]?.let { element ->
            if (element.jsonPrimitive.booleanOrNull == null) {
                errors.add("$path.enabled must be a boolean")
            }
        }

        // Validate source_type
        autoAction["source_type"]?.jsonPrimitive?.content?.let { sourceType ->
            if (sourceType !in VALID_SOURCE_TYPES) {
                errors.add(
                    "$path.source_type must be one of: ${VALID_SOURCE_TYPES.joinToString()}, " +
                        "got '$sourceType'",
                )
            }
        }
    }

    /**
     * Validates signer section (local or remote).
     */
    private fun validateSignerSection(
        signer: JsonObject,
        sectionName: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val hasLocal = signer["local"] != null
        val hasRemote = signer["remote"] != null

        if (hasLocal && hasRemote) {
            errors.add("$sectionName cannot have both 'local' and 'remote' configurations")
        }

        if (!hasLocal && !hasRemote) {
            errors.add("$sectionName must have either 'local' or 'remote' configuration")
        }

        signer["local"]?.jsonObject?.let { local ->
            validateLocalSigner(local, "$sectionName.local", errors, warnings)
        }

        signer["remote"]?.jsonObject?.let { remote ->
            validateRemoteSigner(remote, "$sectionName.remote", errors, warnings)
        }
    }

    /**
     * Validates local signer configuration.
     */
    private fun validateLocalSigner(
        local: JsonObject,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf("alg", "sign_cert", "private_key", "tsa_url")

        local.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in $path: '$key'")
            }
        }

        // Required fields
        if (local["alg"] == null) {
            errors.add("$path.alg is required")
        }
        if (local["sign_cert"] == null) {
            errors.add("$path.sign_cert is required")
        }
        if (local["private_key"] == null) {
            errors.add("$path.private_key is required")
        }

        // Validate algorithm
        local["alg"]?.jsonPrimitive?.content?.let { alg ->
            if (alg.lowercase() !in VALID_ALGORITHMS) {
                errors.add(
                    "$path.alg must be one of: ${VALID_ALGORITHMS.joinToString()}, " +
                        "got '$alg'",
                )
            }
        }

        // Validate PEM formats
        local["sign_cert"]?.jsonPrimitive?.content?.let { cert ->
            if (!isValidPEM(cert, "CERTIFICATE")) {
                errors.add("$path.sign_cert must be valid PEM-formatted certificate(s)")
            }
        }

        local["private_key"]?.jsonPrimitive?.content?.let { key ->
            if (!isValidPEM(key, "PRIVATE KEY") && !isValidPEM(key, "RSA PRIVATE KEY") &&
                !isValidPEM(key, "EC PRIVATE KEY")
            ) {
                errors.add("$path.private_key must be valid PEM-formatted private key")
            }
        }

        // Validate TSA URL
        local["tsa_url"]?.jsonPrimitive?.content?.let { url ->
            if (!isValidUrl(url)) {
                errors.add("$path.tsa_url must be a valid URL")
            }
        }
    }

    /**
     * Validates remote signer configuration.
     */
    private fun validateRemoteSigner(
        remote: JsonObject,
        path: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val validKeys = setOf("url", "alg", "sign_cert", "tsa_url")

        remote.keys.forEach { key ->
            if (key !in validKeys) {
                warnings.add("Unknown key in $path: '$key'")
            }
        }

        // Required fields
        if (remote["url"] == null) {
            errors.add("$path.url is required")
        }
        if (remote["alg"] == null) {
            errors.add("$path.alg is required")
        }
        if (remote["sign_cert"] == null) {
            errors.add("$path.sign_cert is required")
        }

        // Validate URL
        remote["url"]?.jsonPrimitive?.content?.let { url ->
            if (!isValidUrl(url)) {
                errors.add("$path.url must be a valid URL")
            }
        }

        // Validate algorithm
        remote["alg"]?.jsonPrimitive?.content?.let { alg ->
            if (alg.lowercase() !in VALID_ALGORITHMS) {
                errors.add(
                    "$path.alg must be one of: ${VALID_ALGORITHMS.joinToString()}, " +
                        "got '$alg'",
                )
            }
        }

        // Validate PEM format for certificate
        remote["sign_cert"]?.jsonPrimitive?.content?.let { cert ->
            if (!isValidPEM(cert, "CERTIFICATE")) {
                errors.add("$path.sign_cert must be valid PEM-formatted certificate(s)")
            }
        }

        // Validate TSA URL
        remote["tsa_url"]?.jsonPrimitive?.content?.let { url ->
            if (!isValidUrl(url)) {
                errors.add("$path.tsa_url must be a valid URL")
            }
        }
    }

    /**
     * Checks if a string is valid PEM format.
     */
    private fun isValidPEM(pemString: String, expectedType: String): Boolean {
        val beginMarker = "-----BEGIN $expectedType-----"
        val endMarker = "-----END $expectedType-----"
        return pemString.contains(beginMarker) && pemString.contains(endMarker)
    }

    /**
     * Checks if a string is a valid URL.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsed = java.net.URL(url)
            parsed.protocol in listOf("http", "https")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Logs validation results to the Android console.
     */
    private fun logValidationResults(errors: List<String>, warnings: List<String>) {
        errors.forEach { error ->
            Log.e(TAG, "Settings validation error: $error")
        }
        warnings.forEach { warning ->
            Log.w(TAG, "Settings validation warning: $warning")
        }
    }

    /**
     * Validates settings and logs warnings.
     *
     * Convenience method that validates and logs in one call.
     *
     * @param settingsJson The settings JSON string to validate.
     * @return A ValidationResult with any errors or warnings found.
     */
    fun validateAndLog(settingsJson: String): ValidationResult = validate(settingsJson, logWarnings = true)
}
