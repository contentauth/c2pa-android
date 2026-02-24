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

package org.contentauth.c2pa.test.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.manifest.SettingsValidator
import org.contentauth.c2pa.manifest.ValidationResult

/** Tests for SettingsValidator and ValidationResult. */
abstract class SettingsValidatorTests : TestBase() {

    companion object {
        const val VALID_PEM_CERT = "-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----"
        const val VALID_PEM_KEY = "-----BEGIN PRIVATE KEY-----\nMIIBtest\n-----END PRIVATE KEY-----"
        const val VALID_PEM_EC_KEY = "-----BEGIN EC PRIVATE KEY-----\nMIIBtest\n-----END EC PRIVATE KEY-----"
        const val VALID_PEM_RSA_KEY = "-----BEGIN RSA PRIVATE KEY-----\nMIIBtest\n-----END RSA PRIVATE KEY-----"
    }

    suspend fun testValidSettings(): TestResult = withContext(Dispatchers.IO) {
        runTest("Valid Settings") {
            val settingsJson = """{"version": 1}"""
            val result = SettingsValidator.validate(settingsJson, logWarnings = false)

            val success = result.isValid() && !result.hasErrors() && !result.hasWarnings()
            TestResult(
                "Valid Settings",
                success,
                if (success) "Minimal valid settings accepted" else "Unexpected validation failures",
                "Errors: ${result.errors}, Warnings: ${result.warnings}",
            )
        }
    }

    suspend fun testInvalidJson(): TestResult = withContext(Dispatchers.IO) {
        runTest("Invalid JSON") {
            val result = SettingsValidator.validate("not valid json {{{", logWarnings = false)

            val success = result.hasErrors() &&
                result.errors.any { it.contains("Failed to parse") }
            TestResult(
                "Invalid JSON",
                success,
                if (success) "Malformed JSON correctly rejected" else "Expected parse error",
                "Errors: ${result.errors}",
            )
        }
    }

    suspend fun testMissingVersion(): TestResult = withContext(Dispatchers.IO) {
        runTest("Missing Version") {
            val result = SettingsValidator.validate("""{}""", logWarnings = false)

            val success = result.hasErrors() &&
                result.errors.any { it.contains("version") }
            TestResult(
                "Missing Version",
                success,
                if (success) "Missing version correctly detected" else "Expected version error",
                "Errors: ${result.errors}",
            )
        }
    }

    suspend fun testWrongVersion(): TestResult = withContext(Dispatchers.IO) {
        runTest("Wrong Version") {
            val result = SettingsValidator.validate("""{"version": 2}""", logWarnings = false)

            val success = result.hasErrors() &&
                result.errors.any { it.contains("version") && it.contains("2") }
            TestResult(
                "Wrong Version",
                success,
                if (success) "Wrong version correctly rejected" else "Expected version error",
                "Errors: ${result.errors}",
            )
        }
    }

    suspend fun testUnknownTopLevelKeys(): TestResult = withContext(Dispatchers.IO) {
        runTest("Unknown Top-Level Keys") {
            val settingsJson = """{"version": 1, "unknown_section": {}, "another_unknown": true}"""
            val result = SettingsValidator.validate(settingsJson, logWarnings = false)

            val success = !result.hasErrors() &&
                result.hasWarnings() &&
                result.warnings.any { it.contains("unknown_section") } &&
                result.warnings.any { it.contains("another_unknown") }
            TestResult(
                "Unknown Top-Level Keys",
                success,
                if (success) "Unknown keys produce warnings" else "Expected warnings for unknown keys",
                "Warnings: ${result.warnings}",
            )
        }
    }

    suspend fun testTrustSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Trust Section Validation") {
            val errors = mutableListOf<String>()

            // Valid trust section with PEM certificates
            val validTrust = """{
                "version": 1,
                "trust": {
                    "trust_anchors": "$VALID_PEM_CERT"
                }
            }"""
            val validResult = SettingsValidator.validate(validTrust, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid trust section rejected: ${validResult.errors}")
            }

            // Invalid PEM format
            val invalidPem = """{
                "version": 1,
                "trust": {
                    "trust_anchors": "not a PEM certificate"
                }
            }"""
            val invalidResult = SettingsValidator.validate(invalidPem, logWarnings = false)
            if (!invalidResult.hasErrors()) {
                errors.add("Invalid PEM not detected")
            }

            // Unknown key in trust
            val unknownKey = """{
                "version": 1,
                "trust": {
                    "unknown_trust_key": true
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown trust key did not produce warning")
            }

            // user_anchors and allowed_list PEM validation
            val multiPem = """{
                "version": 1,
                "trust": {
                    "user_anchors": "$VALID_PEM_CERT",
                    "allowed_list": "not valid"
                }
            }"""
            val multiResult = SettingsValidator.validate(multiPem, logWarnings = false)
            if (!multiResult.hasErrors() || !multiResult.errors.any { it.contains("allowed_list") }) {
                errors.add("Invalid allowed_list PEM not detected")
            }

            val success = errors.isEmpty()
            TestResult(
                "Trust Section Validation",
                success,
                if (success) "Trust section validated correctly" else "Trust validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testCawgTrustSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("CAWG Trust Section Validation") {
            val errors = mutableListOf<String>()

            // Valid cawg_trust with verify_trust_list boolean
            val validCawg = """{
                "version": 1,
                "cawg_trust": {
                    "verify_trust_list": true
                }
            }"""
            val validResult = SettingsValidator.validate(validCawg, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid cawg_trust rejected: ${validResult.errors}")
            }

            // Invalid verify_trust_list type
            val invalidType = """{
                "version": 1,
                "cawg_trust": {
                    "verify_trust_list": "not_a_boolean"
                }
            }"""
            val invalidResult = SettingsValidator.validate(invalidType, logWarnings = false)
            if (!invalidResult.hasErrors()) {
                errors.add("Non-boolean verify_trust_list not detected")
            }

            val success = errors.isEmpty()
            TestResult(
                "CAWG Trust Section Validation",
                success,
                if (success) "CAWG trust section validated correctly" else "CAWG trust validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testCoreSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Core Section Validation") {
            val errors = mutableListOf<String>()

            // Valid core section
            val validCore = """{
                "version": 1,
                "core": {
                    "merkle_tree_chunk_size_in_kb": 64,
                    "merkle_tree_max_proofs": 128,
                    "backing_store_memory_threshold_in_mb": 256,
                    "decode_identity_assertions": true,
                    "allowed_network_hosts": ["example.com"]
                }
            }"""
            val validResult = SettingsValidator.validate(validCore, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid core section rejected: ${validResult.errors}")
            }

            // Invalid numeric field
            val invalidNumeric = """{
                "version": 1,
                "core": {
                    "merkle_tree_chunk_size_in_kb": "not_a_number"
                }
            }"""
            val numResult = SettingsValidator.validate(invalidNumeric, logWarnings = false)
            if (!numResult.hasErrors()) {
                errors.add("Non-numeric merkle_tree_chunk_size_in_kb not detected")
            }

            // Invalid boolean field
            val invalidBool = """{
                "version": 1,
                "core": {
                    "decode_identity_assertions": "yes"
                }
            }"""
            val boolResult = SettingsValidator.validate(invalidBool, logWarnings = false)
            if (!boolResult.hasErrors()) {
                errors.add("Non-boolean decode_identity_assertions not detected")
            }

            // Invalid array field
            val invalidArray = """{
                "version": 1,
                "core": {
                    "allowed_network_hosts": "not_an_array"
                }
            }"""
            val arrayResult = SettingsValidator.validate(invalidArray, logWarnings = false)
            if (!arrayResult.hasErrors()) {
                errors.add("Non-array allowed_network_hosts not detected")
            }

            // Unknown key produces warning
            val unknownKey = """{
                "version": 1,
                "core": {
                    "unknown_core_key": 42
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown core key did not produce warning")
            }

            val success = errors.isEmpty()
            TestResult(
                "Core Section Validation",
                success,
                if (success) "Core section validated correctly" else "Core validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testVerifySection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Verify Section Validation") {
            val errors = mutableListOf<String>()

            // Valid verify section
            val validVerify = """{
                "version": 1,
                "verify": {
                    "verify_after_reading": true,
                    "verify_after_sign": true,
                    "verify_trust": true,
                    "verify_timestamp_trust": true,
                    "ocsp_fetch": false,
                    "remote_manifest_fetch": true,
                    "skip_ingredient_conflict_resolution": false,
                    "strict_v1_validation": false
                }
            }"""
            val validResult = SettingsValidator.validate(validVerify, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid verify section rejected: ${validResult.errors}")
            }

            // Non-boolean verify field
            val invalidBool = """{
                "version": 1,
                "verify": {
                    "verify_trust": "yes"
                }
            }"""
            val boolResult = SettingsValidator.validate(invalidBool, logWarnings = false)
            if (!boolResult.hasErrors()) {
                errors.add("Non-boolean verify_trust not detected")
            }

            // Disabled verification produces warnings
            val disabledVerify = """{
                "version": 1,
                "verify": {
                    "verify_trust": false,
                    "verify_timestamp_trust": false,
                    "verify_after_sign": false
                }
            }"""
            val warnResult = SettingsValidator.validate(disabledVerify, logWarnings = false)
            if (!warnResult.hasWarnings() || warnResult.warnings.size < 3) {
                errors.add("Expected 3 warnings for disabled verification, got ${warnResult.warnings.size}")
            }

            // Unknown verify key
            val unknownKey = """{
                "version": 1,
                "verify": {
                    "unknown_verify_key": true
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown verify key did not produce warning")
            }

            val success = errors.isEmpty()
            TestResult(
                "Verify Section Validation",
                success,
                if (success) "Verify section validated correctly" else "Verify validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testBuilderSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Builder Section Validation") {
            val errors = mutableListOf<String>()

            // Valid intent as string
            val editIntent = """{
                "version": 1,
                "builder": {
                    "intent": "Edit"
                }
            }"""
            val editResult = SettingsValidator.validate(editIntent, logWarnings = false)
            if (editResult.hasErrors()) {
                errors.add("Valid Edit intent rejected: ${editResult.errors}")
            }

            // Valid intent as object
            val createIntent = """{
                "version": 1,
                "builder": {
                    "intent": {"Create": "digitalCapture"}
                }
            }"""
            val createResult = SettingsValidator.validate(createIntent, logWarnings = false)
            if (createResult.hasErrors()) {
                errors.add("Valid Create intent rejected: ${createResult.errors}")
            }

            // Invalid intent string
            val badIntent = """{
                "version": 1,
                "builder": {
                    "intent": "Delete"
                }
            }"""
            val badIntentResult = SettingsValidator.validate(badIntent, logWarnings = false)
            if (!badIntentResult.hasErrors()) {
                errors.add("Invalid intent string 'Delete' not detected")
            }

            // Invalid intent object (missing Create key)
            val badObj = """{
                "version": 1,
                "builder": {
                    "intent": {"NotCreate": "digitalCapture"}
                }
            }"""
            val badObjResult = SettingsValidator.validate(badObj, logWarnings = false)
            if (!badObjResult.hasErrors()) {
                errors.add("Intent object without Create key not detected")
            }

            // Invalid intent Create source type
            val badSource = """{
                "version": 1,
                "builder": {
                    "intent": {"Create": "invalidSourceType"}
                }
            }"""
            val badSourceResult = SettingsValidator.validate(badSource, logWarnings = false)
            if (!badSourceResult.hasErrors()) {
                errors.add("Invalid Create source type not detected")
            }

            // claim_generator_info without name
            val noName = """{
                "version": 1,
                "builder": {
                    "claim_generator_info": {"version": "1.0"}
                }
            }"""
            val noNameResult = SettingsValidator.validate(noName, logWarnings = false)
            if (!noNameResult.hasErrors()) {
                errors.add("claim_generator_info without name not detected")
            }

            // created_assertion_labels not an array
            val badLabels = """{
                "version": 1,
                "builder": {
                    "created_assertion_labels": "not_an_array"
                }
            }"""
            val labelsResult = SettingsValidator.validate(badLabels, logWarnings = false)
            if (!labelsResult.hasErrors()) {
                errors.add("Non-array created_assertion_labels not detected")
            }

            // generate_c2pa_archive not boolean
            val badArchive = """{
                "version": 1,
                "builder": {
                    "generate_c2pa_archive": "yes"
                }
            }"""
            val archiveResult = SettingsValidator.validate(badArchive, logWarnings = false)
            if (!archiveResult.hasErrors()) {
                errors.add("Non-boolean generate_c2pa_archive not detected")
            }

            // Unknown builder key
            val unknownKey = """{
                "version": 1,
                "builder": {
                    "unknown_builder_key": true
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown builder key did not produce warning")
            }

            val success = errors.isEmpty()
            TestResult(
                "Builder Section Validation",
                success,
                if (success) "Builder section validated correctly" else "Builder validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testThumbnailSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Thumbnail Section Validation") {
            val errors = mutableListOf<String>()

            // Valid thumbnail
            val validThumb = """{
                "version": 1,
                "builder": {
                    "thumbnail": {
                        "enabled": true,
                        "format": "jpeg",
                        "quality": "medium",
                        "long_edge": 1024,
                        "ignore_errors": false,
                        "prefer_smallest_format": true
                    }
                }
            }"""
            val validResult = SettingsValidator.validate(validThumb, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid thumbnail rejected: ${validResult.errors}")
            }

            // Invalid format
            val badFormat = """{
                "version": 1,
                "builder": {
                    "thumbnail": {"format": "bmp"}
                }
            }"""
            val formatResult = SettingsValidator.validate(badFormat, logWarnings = false)
            if (!formatResult.hasErrors()) {
                errors.add("Invalid thumbnail format 'bmp' not detected")
            }

            // Invalid quality
            val badQuality = """{
                "version": 1,
                "builder": {
                    "thumbnail": {"quality": "ultra"}
                }
            }"""
            val qualityResult = SettingsValidator.validate(badQuality, logWarnings = false)
            if (!qualityResult.hasErrors()) {
                errors.add("Invalid thumbnail quality 'ultra' not detected")
            }

            // Invalid long_edge type
            val badEdge = """{
                "version": 1,
                "builder": {
                    "thumbnail": {"long_edge": "big"}
                }
            }"""
            val edgeResult = SettingsValidator.validate(badEdge, logWarnings = false)
            if (!edgeResult.hasErrors()) {
                errors.add("Non-numeric long_edge not detected")
            }

            // Invalid boolean field
            val badBool = """{
                "version": 1,
                "builder": {
                    "thumbnail": {"enabled": "yes"}
                }
            }"""
            val boolResult = SettingsValidator.validate(badBool, logWarnings = false)
            if (!boolResult.hasErrors()) {
                errors.add("Non-boolean thumbnail.enabled not detected")
            }

            // Unknown thumbnail key
            val unknownKey = """{
                "version": 1,
                "builder": {
                    "thumbnail": {"unknown_thumb_key": true}
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown thumbnail key did not produce warning")
            }

            val success = errors.isEmpty()
            TestResult(
                "Thumbnail Section Validation",
                success,
                if (success) "Thumbnail section validated correctly" else "Thumbnail validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testActionsSection(): TestResult = withContext(Dispatchers.IO) {
        runTest("Actions Section Validation") {
            val errors = mutableListOf<String>()

            // Valid actions with auto actions
            val validActions = """{
                "version": 1,
                "builder": {
                    "actions": {
                        "auto_created_action": {
                            "enabled": true,
                            "source_type": "digitalCapture"
                        }
                    }
                }
            }"""
            val validResult = SettingsValidator.validate(validActions, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid actions section rejected: ${validResult.errors}")
            }

            // Invalid source_type in auto action
            val badSource = """{
                "version": 1,
                "builder": {
                    "actions": {
                        "auto_opened_action": {
                            "source_type": "invalidType"
                        }
                    }
                }
            }"""
            val sourceResult = SettingsValidator.validate(badSource, logWarnings = false)
            if (!sourceResult.hasErrors()) {
                errors.add("Invalid auto action source_type not detected")
            }

            // Invalid enabled type in auto action
            val badEnabled = """{
                "version": 1,
                "builder": {
                    "actions": {
                        "auto_placed_action": {
                            "enabled": "yes"
                        }
                    }
                }
            }"""
            val enabledResult = SettingsValidator.validate(badEnabled, logWarnings = false)
            if (!enabledResult.hasErrors()) {
                errors.add("Non-boolean auto action enabled not detected")
            }

            // Unknown key in actions section
            val unknownKey = """{
                "version": 1,
                "builder": {
                    "actions": {
                        "unknown_action_key": true
                    }
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown action key did not produce warning")
            }

            // Unknown key in auto action
            val unknownAutoKey = """{
                "version": 1,
                "builder": {
                    "actions": {
                        "auto_created_action": {
                            "unknown_auto_key": true
                        }
                    }
                }
            }"""
            val unknownAutoResult = SettingsValidator.validate(unknownAutoKey, logWarnings = false)
            if (!unknownAutoResult.hasWarnings()) {
                errors.add("Unknown auto action key did not produce warning")
            }

            val success = errors.isEmpty()
            TestResult(
                "Actions Section Validation",
                success,
                if (success) "Actions section validated correctly" else "Actions validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testLocalSigner(): TestResult = withContext(Dispatchers.IO) {
        runTest("Local Signer Validation") {
            val errors = mutableListOf<String>()

            // Valid local signer
            val validLocal = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_KEY",
                        "tsa_url": "https://timestamp.example.com"
                    }
                }
            }"""
            val validResult = SettingsValidator.validate(validLocal, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid local signer rejected: ${validResult.errors}")
            }

            // Missing required fields
            val missingFields = """{
                "version": 1,
                "signer": {
                    "local": {}
                }
            }"""
            val missingResult = SettingsValidator.validate(missingFields, logWarnings = false)
            if (!missingResult.hasErrors() || missingResult.errors.size < 3) {
                errors.add("Expected 3+ errors for missing local signer fields, got ${missingResult.errors.size}")
            }

            // Invalid algorithm
            val badAlg = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "invalid_alg",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_KEY"
                    }
                }
            }"""
            val algResult = SettingsValidator.validate(badAlg, logWarnings = false)
            if (!algResult.hasErrors()) {
                errors.add("Invalid algorithm not detected")
            }

            // Invalid certificate PEM
            val badCert = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "not a cert",
                        "private_key": "$VALID_PEM_KEY"
                    }
                }
            }"""
            val certResult = SettingsValidator.validate(badCert, logWarnings = false)
            if (!certResult.hasErrors()) {
                errors.add("Invalid certificate PEM not detected")
            }

            // Invalid private key PEM
            val badKey = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "not a key"
                    }
                }
            }"""
            val keyResult = SettingsValidator.validate(badKey, logWarnings = false)
            if (!keyResult.hasErrors()) {
                errors.add("Invalid private key PEM not detected")
            }

            // EC PRIVATE KEY format accepted
            val ecKey = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_EC_KEY"
                    }
                }
            }"""
            val ecResult = SettingsValidator.validate(ecKey, logWarnings = false)
            if (ecResult.errors.any { it.contains("private_key") }) {
                errors.add("EC PRIVATE KEY format rejected: ${ecResult.errors}")
            }

            // RSA PRIVATE KEY format accepted
            val rsaKey = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "ps256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_RSA_KEY"
                    }
                }
            }"""
            val rsaResult = SettingsValidator.validate(rsaKey, logWarnings = false)
            if (rsaResult.errors.any { it.contains("private_key") }) {
                errors.add("RSA PRIVATE KEY format rejected: ${rsaResult.errors}")
            }

            // Invalid TSA URL
            val badTsa = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_KEY",
                        "tsa_url": "ftp://not-http"
                    }
                }
            }"""
            val tsaResult = SettingsValidator.validate(badTsa, logWarnings = false)
            if (!tsaResult.hasErrors()) {
                errors.add("Invalid TSA URL (ftp) not detected")
            }

            // Unknown key in local signer
            val unknownKey = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_KEY",
                        "unknown_local_key": true
                    }
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown local signer key did not produce warning")
            }

            val success = errors.isEmpty()
            TestResult(
                "Local Signer Validation",
                success,
                if (success) "Local signer validated correctly" else "Local signer validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testRemoteSigner(): TestResult = withContext(Dispatchers.IO) {
        runTest("Remote Signer Validation") {
            val errors = mutableListOf<String>()

            // Valid remote signer
            val validRemote = """{
                "version": 1,
                "signer": {
                    "remote": {
                        "url": "https://signer.example.com/sign",
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "tsa_url": "https://timestamp.example.com"
                    }
                }
            }"""
            val validResult = SettingsValidator.validate(validRemote, logWarnings = false)
            if (validResult.hasErrors()) {
                errors.add("Valid remote signer rejected: ${validResult.errors}")
            }

            // Missing required fields
            val missingFields = """{
                "version": 1,
                "signer": {
                    "remote": {}
                }
            }"""
            val missingResult = SettingsValidator.validate(missingFields, logWarnings = false)
            if (!missingResult.hasErrors() || missingResult.errors.size < 3) {
                errors.add("Expected 3+ errors for missing remote signer fields, got ${missingResult.errors.size}")
            }

            // Invalid URL
            val badUrl = """{
                "version": 1,
                "signer": {
                    "remote": {
                        "url": "not_a_url",
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT"
                    }
                }
            }"""
            val urlResult = SettingsValidator.validate(badUrl, logWarnings = false)
            if (!urlResult.hasErrors()) {
                errors.add("Invalid URL not detected")
            }

            // Invalid algorithm
            val badAlg = """{
                "version": 1,
                "signer": {
                    "remote": {
                        "url": "https://signer.example.com",
                        "alg": "invalid",
                        "sign_cert": "$VALID_PEM_CERT"
                    }
                }
            }"""
            val algResult = SettingsValidator.validate(badAlg, logWarnings = false)
            if (!algResult.hasErrors()) {
                errors.add("Invalid remote algorithm not detected")
            }

            // Invalid certificate PEM
            val badCert = """{
                "version": 1,
                "signer": {
                    "remote": {
                        "url": "https://signer.example.com",
                        "alg": "es256",
                        "sign_cert": "not a cert"
                    }
                }
            }"""
            val certResult = SettingsValidator.validate(badCert, logWarnings = false)
            if (!certResult.hasErrors()) {
                errors.add("Invalid remote certificate PEM not detected")
            }

            // Invalid TSA URL
            val badTsa = """{
                "version": 1,
                "signer": {
                    "remote": {
                        "url": "https://signer.example.com",
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "tsa_url": "ftp://invalid"
                    }
                }
            }"""
            val tsaResult = SettingsValidator.validate(badTsa, logWarnings = false)
            if (!tsaResult.hasErrors()) {
                errors.add("Invalid remote TSA URL not detected")
            }

            // Unknown key in remote signer
            val unknownKey = """{
                "version": 1,
                "signer": {
                    "remote": {
                        "url": "https://signer.example.com",
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "unknown_remote_key": true
                    }
                }
            }"""
            val unknownResult = SettingsValidator.validate(unknownKey, logWarnings = false)
            if (!unknownResult.hasWarnings()) {
                errors.add("Unknown remote signer key did not produce warning")
            }

            val success = errors.isEmpty()
            TestResult(
                "Remote Signer Validation",
                success,
                if (success) "Remote signer validated correctly" else "Remote signer validation failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testSignerMutualExclusion(): TestResult = withContext(Dispatchers.IO) {
        runTest("Signer Mutual Exclusion") {
            val errors = mutableListOf<String>()

            // Both local and remote
            val bothSigners = """{
                "version": 1,
                "signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_KEY"
                    },
                    "remote": {
                        "url": "https://signer.example.com",
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT"
                    }
                }
            }"""
            val bothResult = SettingsValidator.validate(bothSigners, logWarnings = false)
            if (!bothResult.hasErrors() || !bothResult.errors.any { it.contains("both") }) {
                errors.add("Both local+remote signer not detected")
            }

            // Neither local nor remote
            val neitherSigner = """{
                "version": 1,
                "signer": {}
            }"""
            val neitherResult = SettingsValidator.validate(neitherSigner, logWarnings = false)
            if (!neitherResult.hasErrors() || !neitherResult.errors.any { it.contains("either") }) {
                errors.add("Missing local/remote signer not detected")
            }

            // cawg_x509_signer also validates
            val cawgSigner = """{
                "version": 1,
                "cawg_x509_signer": {
                    "local": {
                        "alg": "es256",
                        "sign_cert": "$VALID_PEM_CERT",
                        "private_key": "$VALID_PEM_KEY"
                    }
                }
            }"""
            val cawgResult = SettingsValidator.validate(cawgSigner, logWarnings = false)
            if (cawgResult.hasErrors()) {
                errors.add("Valid cawg_x509_signer rejected: ${cawgResult.errors}")
            }

            val success = errors.isEmpty()
            TestResult(
                "Signer Mutual Exclusion",
                success,
                if (success) "Signer exclusion rules validated correctly" else "Signer exclusion failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testValidationResultHelpers(): TestResult = withContext(Dispatchers.IO) {
        runTest("ValidationResult Helpers") {
            val errors = mutableListOf<String>()

            // Empty result
            val empty = ValidationResult()
            if (empty.hasErrors()) errors.add("Empty result reports hasErrors")
            if (empty.hasWarnings()) errors.add("Empty result reports hasWarnings")
            if (!empty.isValid()) errors.add("Empty result reports not valid")

            // With errors
            val withErrors = ValidationResult(errors = listOf("An error"))
            if (!withErrors.hasErrors()) errors.add("Result with errors reports no errors")
            if (withErrors.hasWarnings()) errors.add("Result with only errors reports warnings")
            if (withErrors.isValid()) errors.add("Result with errors reports valid")

            // With warnings only
            val withWarnings = ValidationResult(warnings = listOf("A warning"))
            if (withWarnings.hasErrors()) errors.add("Result with only warnings reports errors")
            if (!withWarnings.hasWarnings()) errors.add("Result with warnings reports no warnings")
            if (!withWarnings.isValid()) errors.add("Result with only warnings reports not valid")

            // With both
            val withBoth = ValidationResult(
                errors = listOf("Error"),
                warnings = listOf("Warning"),
            )
            if (!withBoth.hasErrors()) errors.add("Result with both reports no errors")
            if (!withBoth.hasWarnings()) errors.add("Result with both reports no warnings")
            if (withBoth.isValid()) errors.add("Result with both reports valid")

            val success = errors.isEmpty()
            TestResult(
                "ValidationResult Helpers",
                success,
                if (success) "All ValidationResult helpers work correctly" else "ValidationResult helper failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testValidateAndLog(): TestResult = withContext(Dispatchers.IO) {
        runTest("Validate and Log") {
            // validateAndLog should work the same as validate with logWarnings=true
            val result = SettingsValidator.validateAndLog("""{"version": 1}""")
            val success = result.isValid()
            TestResult(
                "Validate and Log",
                success,
                if (success) "validateAndLog works correctly" else "validateAndLog failed",
                "Errors: ${result.errors}, Warnings: ${result.warnings}",
            )
        }
    }

    suspend fun testIntentAsNumber(): TestResult = withContext(Dispatchers.IO) {
        runTest("Intent As Number") {
            // Intent as a number (neither string nor object)
            val result = SettingsValidator.validate(
                """{"version": 1, "builder": {"intent": 42}}""",
                logWarnings = false,
            )
            val success = result.hasErrors() &&
                result.errors.any { it.contains("intent") }
            TestResult(
                "Intent As Number",
                success,
                if (success) "Non-string/object intent correctly rejected" else "Expected intent type error",
                "Errors: ${result.errors}",
            )
        }
    }
}
