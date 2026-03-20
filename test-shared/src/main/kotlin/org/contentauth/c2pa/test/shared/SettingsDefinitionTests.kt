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
import org.contentauth.c2pa.C2PASettings
import org.contentauth.c2pa.settings.ActionTemplateSettings
import org.contentauth.c2pa.settings.ActionsSettings
import org.contentauth.c2pa.settings.AutoActionSettings
import org.contentauth.c2pa.settings.BuilderSettingsDefinition
import org.contentauth.c2pa.settings.C2PASettingsDefinition
import org.contentauth.c2pa.settings.ClaimGeneratorInfoSettings
import org.contentauth.c2pa.settings.CoreSettings
import org.contentauth.c2pa.settings.OcspFetchScope
import org.contentauth.c2pa.settings.SettingsIntent
import org.contentauth.c2pa.settings.SignerSettings
import org.contentauth.c2pa.settings.ThumbnailFormat
import org.contentauth.c2pa.settings.ThumbnailQuality
import org.contentauth.c2pa.settings.ThumbnailSettings
import org.contentauth.c2pa.settings.TimeStampFetchScope
import org.contentauth.c2pa.settings.TimeStampSettings
import org.contentauth.c2pa.settings.TrustSettings
import org.contentauth.c2pa.settings.VerifySettings

/**
 * Tests for [C2PASettingsDefinition] serialization and integration with [C2PASettings].
 */
abstract class SettingsDefinitionTests : TestBase() {

    companion object {
        const val VALID_CERT = "-----BEGIN CERTIFICATE-----\nMIIBkTCB+wIJAL0X" +
            "N2n8v5MHMA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMMBnRl\nc3RjYTAeFw0yMzAxMDEwMDAwMD" +
            "BaFw0yNDAxMDEwMDAwMDBaMBMxETAPBgNVBAMM\nCHRlc3RjZXJ0MFkwEwYHKoZIzj0CAQYI" +
            "KoZIzj0DAQcDQgAEZlkPH79yUjJBZnYz\nvJMhDJkSCBMkMmQ1WYaA8xb6E2L3fVJSMOhb" +
            "H8vvCAb5gC4FAq8L1RK2d0c9kBIy\nqjCF4DANBgkqhkiG9w0BAQsFAANBAHVK5kQ3TjVY1x" +
            "u4G6DXb0m+8NZUL5OOTLKJ\nR9p8k7E8Y0WJxcM8c3VUo0Dc7s/ZWKZ5RKsMbBDJyH8WF+YR" +
            "CU=\n-----END CERTIFICATE-----\n"

        const val VALID_KEY = "-----BEGIN PRIVATE KEY-----\nMIGHAgEAMBMGByqGSM49" +
            "AgEGCCqGSM49AwEHBG0wawIBAQQgTESTKEYDATA00000\n00000000000000000000000000hR" +
            "ANCAAQPaL6RkAkYkKU4+IryBSYxJM3h77sF\niMrbvbI8fG7w2Bbl9otNG/cch3DAw5rGAPV7" +
            "NWkyl3QGuV/wt0MrAPDo\n-----END PRIVATE KEY-----\n"
    }

    /** Test basic serialization round-trip. */
    suspend fun testRoundTrip(): TestResult = runTest("Settings Definition Round Trip") {
        withContext(Dispatchers.IO) {
            val definition = C2PASettingsDefinition(
                version = 1,
                verify = VerifySettings(verifyAfterSign = true),
                core = CoreSettings(merkleTreeMaxProofs = 10),
            )

            val json = definition.toJson()
            val restored = C2PASettingsDefinition.fromJson(json)

            check(restored.version == 1) { "Version mismatch: ${restored.version}" }
            check(restored.verify?.verifyAfterSign == true) { "verifyAfterSign mismatch" }
            check(restored.core?.merkleTreeMaxProofs == 10) { "merkleTreeMaxProofs mismatch" }
            check(restored.trust == null) { "trust should be null" }
            check(restored.signer == null) { "signer should be null" }

            TestResult("Settings Definition Round Trip", true, "Round-trip serialization works")
        }
    }

    /** Test fromJson with a known-good JSON string. */
    suspend fun testFromJson(): TestResult = runTest("Settings Definition fromJson") {
        withContext(Dispatchers.IO) {
            val json = """
                {
                    "version": 1,
                    "trust": {
                        "verify_trust_list": false,
                        "user_anchors": "some-anchors"
                    },
                    "verify": {
                        "verify_after_reading": true,
                        "ocsp_fetch": false
                    }
                }
            """.trimIndent()

            val definition = C2PASettingsDefinition.fromJson(json)

            check(definition.version == 1) { "Version mismatch" }
            check(definition.trust?.verifyTrustList == false) { "verifyTrustList mismatch" }
            check(definition.trust?.userAnchors == "some-anchors") { "userAnchors mismatch" }
            check(definition.verify?.verifyAfterReading == true) { "verifyAfterReading mismatch" }
            check(definition.verify?.ocspFetch == false) { "ocspFetch mismatch" }

            TestResult("Settings Definition fromJson", true, "fromJson parses correctly")
        }
    }

    /** Test SettingsIntent polymorphic serialization. */
    suspend fun testSettingsIntent(): TestResult = runTest("Settings Intent Serialization") {
        withContext(Dispatchers.IO) {
            // Test Edit
            val editDef = C2PASettingsDefinition(
                version = 1,
                builder = BuilderSettingsDefinition(intent = SettingsIntent.Edit),
            )
            val editJson = editDef.toJson()
            check("\"edit\"" in editJson) { "Edit should serialize as string 'edit'" }
            val editRestored = C2PASettingsDefinition.fromJson(editJson)
            check(editRestored.builder?.intent is SettingsIntent.Edit) { "Edit deserialization failed" }

            // Test Update
            val updateDef = C2PASettingsDefinition(
                version = 1,
                builder = BuilderSettingsDefinition(intent = SettingsIntent.Update),
            )
            val updateJson = updateDef.toJson()
            check("\"update\"" in updateJson) { "Update should serialize as string 'update'" }
            val updateRestored = C2PASettingsDefinition.fromJson(updateJson)
            check(updateRestored.builder?.intent is SettingsIntent.Update) {
                "Update deserialization failed"
            }

            // Test Create
            val createDef = C2PASettingsDefinition(
                version = 1,
                builder = BuilderSettingsDefinition(
                    intent = SettingsIntent.Create(
                        "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture",
                    ),
                ),
            )
            val createJson = createDef.toJson()
            check("\"create\"" in createJson) { "Create should have 'create' key" }
            check("digitalCapture" in createJson) { "Create should contain source type" }
            val createRestored = C2PASettingsDefinition.fromJson(createJson)
            val createIntent = createRestored.builder?.intent as? SettingsIntent.Create
            check(createIntent != null) { "Create deserialization failed" }
            check("digitalCapture" in createIntent.digitalSourceType) {
                "Digital source type mismatch"
            }

            TestResult("Settings Intent Serialization", true, "All intent variants serialize correctly")
        }
    }

    /** Test toJson output correctness for complex settings. */
    suspend fun testToJson(): TestResult = runTest("Settings Definition toJson") {
        withContext(Dispatchers.IO) {
            val definition = C2PASettingsDefinition(
                version = 1,
                trust = TrustSettings(verifyTrustList = true),
                core = CoreSettings(
                    backingStoreMemoryThresholdInMb = 256,
                    allowedNetworkHosts = listOf("*.example.com"),
                ),
                builder = BuilderSettingsDefinition(
                    thumbnail = ThumbnailSettings(
                        enabled = true,
                        format = ThumbnailFormat.JPEG,
                        quality = ThumbnailQuality.HIGH,
                        longEdge = 512,
                    ),
                    actions = ActionsSettings(
                        autoCreatedAction = AutoActionSettings(enabled = true),
                    ),
                    autoTimestampAssertion = TimeStampSettings(
                        enabled = false,
                        fetchScope = TimeStampFetchScope.ALL,
                    ),
                ),
            )

            val json = definition.toJson()

            check("\"version\":1" in json) { "Version missing from JSON" }
            check("\"verify_trust_list\":true" in json) { "verify_trust_list missing" }
            check("\"backing_store_memory_threshold_in_mb\":256" in json) {
                "backing_store threshold missing"
            }
            check("\"allowed_network_hosts\"" in json) { "allowed_network_hosts missing" }
            check("\"jpeg\"" in json) { "Thumbnail format should be lowercase" }
            check("\"high\"" in json) { "Thumbnail quality should be lowercase" }
            check("\"long_edge\":512" in json) { "long_edge missing" }
            check("\"all\"" in json) { "fetch_scope should be 'all'" }

            TestResult("Settings Definition toJson", true, "toJson produces correct output")
        }
    }

    /** Test SignerSettings serialization. */
    suspend fun testSignerSettings(): TestResult = runTest("Signer Settings Serialization") {
        withContext(Dispatchers.IO) {
            // Test Local signer
            val localDef = C2PASettingsDefinition(
                version = 1,
                signer = SignerSettings.Local(
                    alg = "es256",
                    signCert = VALID_CERT,
                    privateKey = VALID_KEY,
                    tsaUrl = "http://timestamp.example.com",
                ),
            )
            val localJson = localDef.toJson()
            check("\"local\"" in localJson) { "Local signer should have 'local' key" }
            check("\"alg\":\"es256\"" in localJson) { "Algorithm missing" }
            check("\"sign_cert\"" in localJson) { "sign_cert missing" }
            check("\"private_key\"" in localJson) { "private_key missing" }
            check("\"tsa_url\"" in localJson) { "tsa_url missing" }

            val localRestored = C2PASettingsDefinition.fromJson(localJson)
            val localSigner = localRestored.signer as? SignerSettings.Local
            check(localSigner != null) { "Local signer deserialization failed" }
            check(localSigner.alg == "es256") { "Algorithm mismatch" }
            check(localSigner.tsaUrl == "http://timestamp.example.com") { "TSA URL mismatch" }

            // Test Remote signer
            val remoteDef = C2PASettingsDefinition(
                version = 1,
                signer = SignerSettings.Remote(
                    url = "http://signer.example.com/sign",
                    alg = "ps256",
                    signCert = VALID_CERT,
                    referencedAssertions = listOf("cawg.training-mining"),
                    roles = listOf("signer"),
                ),
            )
            val remoteJson = remoteDef.toJson()
            check("\"remote\"" in remoteJson) { "Remote signer should have 'remote' key" }
            check("\"url\"" in remoteJson) { "URL missing" }

            val remoteRestored = C2PASettingsDefinition.fromJson(remoteJson)
            val remoteSigner = remoteRestored.signer as? SignerSettings.Remote
            check(remoteSigner != null) { "Remote signer deserialization failed" }
            check(remoteSigner.url == "http://signer.example.com/sign") { "URL mismatch" }
            check(remoteSigner.referencedAssertions == listOf("cawg.training-mining")) {
                "Referenced assertions mismatch"
            }
            check(remoteSigner.roles == listOf("signer")) { "Roles mismatch" }

            TestResult("Signer Settings Serialization", true, "Local and remote signers serialize correctly")
        }
    }

    /** Test CAWG signer with referenced assertions. */
    suspend fun testCawgSigner(): TestResult = runTest("CAWG Signer Settings") {
        withContext(Dispatchers.IO) {
            val settingsJson = loadSharedResourceAsString("test_settings_with_cawg_signing.json")
                ?: throw IllegalArgumentException("Resource not found: test_settings_with_cawg_signing.json")
            val definition = C2PASettingsDefinition.fromJson(settingsJson)

            check(definition.signer != null) { "signer should not be null" }
            check(definition.cawgX509Signer != null) { "cawg_x509_signer should not be null" }

            val signer = definition.signer as? SignerSettings.Local
            check(signer != null) { "signer should be Local" }
            check(signer.alg == "es256") { "signer alg mismatch" }

            val cawgSigner = definition.cawgX509Signer as? SignerSettings.Local
            check(cawgSigner != null) { "cawg_x509_signer should be Local" }
            check(cawgSigner.referencedAssertions == listOf("cawg.training-mining")) {
                "CAWG referenced assertions mismatch"
            }

            // Round-trip
            val roundTripped = C2PASettingsDefinition.fromJson(definition.toJson())
            val rtCawg = roundTripped.cawgX509Signer as? SignerSettings.Local
            check(rtCawg?.referencedAssertions == listOf("cawg.training-mining")) {
                "Round-trip CAWG assertions mismatch"
            }

            TestResult("CAWG Signer Settings", true, "CAWG signer with referenced assertions works")
        }
    }

    /** Test unknown fields are ignored during deserialization. */
    suspend fun testIgnoreUnknownKeys(): TestResult = runTest("Ignore Unknown Keys") {
        withContext(Dispatchers.IO) {
            val json = """
                {
                    "version": 1,
                    "future_field": "some_value",
                    "verify": {
                        "verify_after_sign": true,
                        "unknown_bool": false
                    }
                }
            """.trimIndent()

            val definition = C2PASettingsDefinition.fromJson(json)
            check(definition.version == 1) { "Version mismatch" }
            check(definition.verify?.verifyAfterSign == true) { "verifyAfterSign mismatch" }

            TestResult("Ignore Unknown Keys", true, "Unknown keys are ignored gracefully")
        }
    }

    /** Test builder settings with all fields. */
    suspend fun testBuilderSettings(): TestResult = runTest("Builder Settings") {
        withContext(Dispatchers.IO) {
            val definition = C2PASettingsDefinition(
                version = 1,
                builder = BuilderSettingsDefinition(
                    vendor = "test-vendor",
                    claimGeneratorInfo = ClaimGeneratorInfoSettings(
                        name = "TestApp",
                        version = "1.0.0",
                    ),
                    thumbnail = ThumbnailSettings(
                        enabled = true,
                        ignoreErrors = false,
                        longEdge = 2048,
                        format = ThumbnailFormat.PNG,
                        preferSmallestFormat = false,
                        quality = ThumbnailQuality.LOW,
                    ),
                    createdAssertionLabels = listOf("c2pa.actions"),
                    preferBoxHash = true,
                    generateC2paArchive = false,
                    certificateStatusFetch = OcspFetchScope.ACTIVE,
                ),
            )

            val json = definition.toJson()
            val restored = C2PASettingsDefinition.fromJson(json)

            check(restored.builder?.vendor == "test-vendor") { "vendor mismatch" }
            check(restored.builder?.claimGeneratorInfo?.name == "TestApp") {
                "claimGeneratorInfo.name mismatch"
            }
            check(restored.builder?.claimGeneratorInfo?.version == "1.0.0") {
                "claimGeneratorInfo.version mismatch"
            }
            check(restored.builder?.thumbnail?.format == ThumbnailFormat.PNG) {
                "thumbnail format mismatch"
            }
            check(restored.builder?.thumbnail?.quality == ThumbnailQuality.LOW) {
                "thumbnail quality mismatch"
            }
            check(restored.builder?.thumbnail?.longEdge == 2048) { "longEdge mismatch" }
            check(restored.builder?.createdAssertionLabels == listOf("c2pa.actions")) {
                "createdAssertionLabels mismatch"
            }
            check(restored.builder?.preferBoxHash == true) { "preferBoxHash mismatch" }
            check(restored.builder?.generateC2paArchive == false) { "generateC2paArchive mismatch" }
            check(restored.builder?.certificateStatusFetch == OcspFetchScope.ACTIVE) {
                "certificateStatusFetch mismatch"
            }

            TestResult("Builder Settings", true, "All builder fields serialize correctly")
        }
    }

    /** Test integration with C2PASettings.fromDefinition. */
    suspend fun testFromDefinition(): TestResult = runTest("C2PASettings.fromDefinition") {
        withContext(Dispatchers.IO) {
            val definition = C2PASettingsDefinition(
                version = 1,
                verify = VerifySettings(verifyAfterSign = true),
                builder = BuilderSettingsDefinition(
                    createdAssertionLabels = listOf("c2pa.actions"),
                ),
            )

            C2PASettings.fromDefinition(definition).use {
                // If we got here without an exception, the native settings were created
                it.setValue("verify.verify_after_sign", "true")
            }

            TestResult("C2PASettings.fromDefinition", true, "fromDefinition creates native settings")
        }
    }

    /** Test integration with C2PASettings.updateFrom. */
    suspend fun testUpdateFrom(): TestResult = runTest("C2PASettings.updateFrom") {
        withContext(Dispatchers.IO) {
            C2PASettings.create().use {
                val definition = C2PASettingsDefinition(
                    version = 1,
                    verify = VerifySettings(
                        verifyAfterReading = false,
                        ocspFetch = true,
                    ),
                )
                it.updateFrom(definition)
                // If we got here without an exception, the update succeeded
                it.setValue("verify.strict_v1_validation", "false")
            }

            TestResult("C2PASettings.updateFrom", true, "updateFrom applies definition to existing settings")
        }
    }

    /** Test pretty JSON output. */
    suspend fun testPrettyJson(): TestResult = runTest("Settings Definition Pretty JSON") {
        withContext(Dispatchers.IO) {
            val definition = C2PASettingsDefinition(
                version = 1,
                verify = VerifySettings(verifyAfterSign = true),
            )

            val prettyJson = definition.toPrettyJson()
            check("\n" in prettyJson) { "Pretty JSON should contain newlines" }
            check("  " in prettyJson || "\t" in prettyJson) { "Pretty JSON should be indented" }

            // Should still be parseable
            val restored = C2PASettingsDefinition.fromJson(prettyJson)
            check(restored.version == 1) { "Pretty JSON should round-trip" }

            TestResult("Settings Definition Pretty JSON", true, "Pretty JSON output works correctly")
        }
    }

    /** Test enum serialization formats. */
    suspend fun testEnumSerialization(): TestResult = runTest("Enum Serialization") {
        withContext(Dispatchers.IO) {
            val definition = C2PASettingsDefinition(
                version = 1,
                builder = BuilderSettingsDefinition(
                    thumbnail = ThumbnailSettings(
                        format = ThumbnailFormat.WEBP,
                        quality = ThumbnailQuality.MEDIUM,
                    ),
                    certificateStatusFetch = OcspFetchScope.ALL,
                    autoTimestampAssertion = TimeStampSettings(
                        fetchScope = TimeStampFetchScope.PARENT,
                    ),
                ),
            )

            val json = definition.toJson()
            check("\"webp\"" in json) { "ThumbnailFormat.WEBP should serialize as 'webp'" }
            check("\"medium\"" in json) { "ThumbnailQuality.MEDIUM should serialize as 'medium'" }
            check("\"parent\"" in json) { "TimeStampFetchScope.PARENT should serialize as 'parent'" }

            // Verify round-trip
            val restored = C2PASettingsDefinition.fromJson(json)
            check(restored.builder?.thumbnail?.format == ThumbnailFormat.WEBP) {
                "ThumbnailFormat round-trip failed"
            }
            check(restored.builder?.thumbnail?.quality == ThumbnailQuality.MEDIUM) {
                "ThumbnailQuality round-trip failed"
            }
            check(restored.builder?.autoTimestampAssertion?.fetchScope == TimeStampFetchScope.PARENT) {
                "TimeStampFetchScope round-trip failed"
            }

            TestResult("Enum Serialization", true, "All enums serialize as lowercase strings")
        }
    }

    /** Test ActionTemplateSettings serialization. */
    suspend fun testActionTemplates(): TestResult = runTest("Action Template Settings") {
        withContext(Dispatchers.IO) {
            val definition = C2PASettingsDefinition(
                version = 1,
                builder = BuilderSettingsDefinition(
                    actions = ActionsSettings(
                        templates = listOf(
                            ActionTemplateSettings(
                                action = "c2pa.created",
                                sourceType = "http://cv.iptc.org/newscodes/digitalsourcetype/digitalCapture",
                                description = "Photo captured by camera",
                            ),
                            ActionTemplateSettings(
                                action = "c2pa.edited",
                                softwareAgent = ClaimGeneratorInfoSettings(
                                    name = "PhotoEditor",
                                    version = "2.0",
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val json = definition.toJson()
            check("\"c2pa.created\"" in json) { "First template action missing" }
            check("\"c2pa.edited\"" in json) { "Second template action missing" }
            check("\"PhotoEditor\"" in json) { "Software agent name missing" }
            check("digitalCapture" in json) { "Source type missing" }
            check("\"Photo captured by camera\"" in json) { "Description missing" }

            // Round-trip
            val restored = C2PASettingsDefinition.fromJson(json)
            val templates = restored.builder?.actions?.templates
            check(templates != null) { "Templates should not be null" }
            check(templates.size == 2) { "Should have 2 templates, got ${templates.size}" }
            check(templates[0].action == "c2pa.created") { "First template action mismatch" }
            check(templates[0].description == "Photo captured by camera") {
                "First template description mismatch"
            }
            check(templates[1].softwareAgent?.name == "PhotoEditor") {
                "Second template software agent mismatch"
            }

            TestResult("Action Template Settings", true, "Action templates serialize correctly")
        }
    }
}
