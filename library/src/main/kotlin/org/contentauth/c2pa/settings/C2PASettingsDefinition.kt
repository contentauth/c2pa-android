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

package org.contentauth.c2pa.settings

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.contentauth.c2pa.C2PAJson
import org.contentauth.c2pa.manifest.ResourceRef

/**
 * Typed representation of C2PA settings, matching the Rust `Settings` struct in c2pa-rs.
 *
 * Provides compile-time type safety for constructing settings JSON. The native C2PA library
 * handles actual validation when the JSON is passed to [org.contentauth.c2pa.C2PASettings.updateFromString].
 *
 * Generated from the c2pa-rs JSON Schema (`library/schemas/c2pa-settings.schema.json`).
 *
 * ## Usage
 *
 * ```kotlin
 * val definition = C2PASettingsDefinition(
 *     version = 1,
 *     verify = VerifySettings(verifyAfterSign = true),
 * )
 * val settings = C2PASettings.fromDefinition(definition)
 * ```
 *
 * @property version Configuration version (must be 1).
 * @property trust Settings for configuring the C2PA trust lists.
 * @property cawgTrust Settings for configuring the CAWG trust lists.
 * @property core Settings for configuring core features.
 * @property verify Settings for configuring verification.
 * @property builder Settings for configuring the builder.
 * @property signer Settings for configuring the base C2PA signer.
 * @property cawgX509Signer Settings for configuring the CAWG x509 signer.
 * @see org.contentauth.c2pa.C2PASettings
 * @since 1.0.0
 */
@Serializable
data class C2PASettingsDefinition(
    val version: Int? = null,
    val trust: TrustSettings? = null,
    @SerialName("cawg_trust")
    val cawgTrust: TrustSettings? = null,
    val core: CoreSettings? = null,
    val verify: VerifySettings? = null,
    val builder: BuilderSettingsDefinition? = null,
    val signer: SignerSettings? = null,
    @SerialName("cawg_x509_signer")
    val cawgX509Signer: SignerSettings? = null,
) {

    companion object {
        /**
         * Deserializes a [C2PASettingsDefinition] from a JSON string.
         *
         * @param jsonString The JSON string to parse.
         * @return The deserialized settings definition.
         */
        fun fromJson(jsonString: String): C2PASettingsDefinition =
            C2PAJson.default.decodeFromString(serializer(), jsonString)
    }

    /** Serializes this settings definition to a compact JSON string. */
    fun toJson(): String = C2PAJson.default.encodeToString(serializer(), this)

    /** Serializes this settings definition to a pretty-printed JSON string. */
    fun toPrettyJson(): String = C2PAJson.pretty.encodeToString(serializer(), this)
}

/**
 * Trust list configuration.
 *
 * @property verifyTrustList Whether to verify trust lists.
 * @property userAnchors User-defined trust anchors (PEM format).
 * @property trustAnchors Trust anchors (PEM format).
 * @property trustConfig Trust configuration.
 * @property allowedList Allowed list configuration.
 */
@Serializable
data class TrustSettings(
    @SerialName("verify_trust_list")
    val verifyTrustList: Boolean? = null,
    @SerialName("user_anchors")
    val userAnchors: String? = null,
    @SerialName("trust_anchors")
    val trustAnchors: String? = null,
    @SerialName("trust_config")
    val trustConfig: String? = null,
    @SerialName("allowed_list")
    val allowedList: String? = null,
)

/**
 * Core settings for configuring fundamental behavior.
 *
 * @property merkleTreeChunkSizeInKb Chunk size for Merkle tree computation in KB.
 * @property merkleTreeMaxProofs Maximum number of Merkle tree proofs.
 * @property backingStoreMemoryThresholdInMb Memory threshold before spilling to disk in MB.
 * @property decodeIdentityAssertions Whether to decode identity assertions.
 * @property allowedNetworkHosts Allowed network host patterns for fetching remote resources.
 */
@Serializable
data class CoreSettings(
    @SerialName("merkle_tree_chunk_size_in_kb")
    val merkleTreeChunkSizeInKb: Int? = null,
    @SerialName("merkle_tree_max_proofs")
    val merkleTreeMaxProofs: Int? = null,
    @SerialName("backing_store_memory_threshold_in_mb")
    val backingStoreMemoryThresholdInMb: Int? = null,
    @SerialName("decode_identity_assertions")
    val decodeIdentityAssertions: Boolean? = null,
    @SerialName("allowed_network_hosts")
    val allowedNetworkHosts: List<String>? = null,
)

/**
 * Verification settings.
 *
 * @property verifyAfterReading Whether to verify manifests after reading.
 * @property verifyAfterSign Whether to verify manifests after signing.
 * @property verifyTrust Whether to verify trust.
 * @property verifyTimestampTrust Whether to verify timestamp trust.
 * @property ocspFetch Whether to fetch OCSP responses.
 * @property remoteManifestFetch Whether to fetch remote manifests.
 * @property skipIngredientConflictResolution Whether to skip ingredient conflict resolution.
 * @property strictV1Validation Whether to use strict V1 validation.
 */
@Serializable
data class VerifySettings(
    @SerialName("verify_after_reading")
    val verifyAfterReading: Boolean? = null,
    @SerialName("verify_after_sign")
    val verifyAfterSign: Boolean? = null,
    @SerialName("verify_trust")
    val verifyTrust: Boolean? = null,
    @SerialName("verify_timestamp_trust")
    val verifyTimestampTrust: Boolean? = null,
    @SerialName("ocsp_fetch")
    val ocspFetch: Boolean? = null,
    @SerialName("remote_manifest_fetch")
    val remoteManifestFetch: Boolean? = null,
    @SerialName("skip_ingredient_conflict_resolution")
    val skipIngredientConflictResolution: Boolean? = null,
    @SerialName("strict_v1_validation")
    val strictV1Validation: Boolean? = null,
)

/**
 * Builder settings for configuring manifest creation.
 *
 * @property vendor Vendor identifier.
 * @property claimGeneratorInfo Claim generator information.
 * @property thumbnail Thumbnail generation settings.
 * @property actions Actions settings.
 * @property certificateStatusFetch OCSP fetch scope for certificate status.
 * @property certificateStatusShouldOverride Whether certificate status should override.
 * @property intent Default builder intent.
 * @property createdAssertionLabels Labels for assertions marked as created.
 * @property preferBoxHash Whether to prefer box hash.
 * @property generateC2paArchive Whether to generate a C2PA archive.
 * @property autoTimestampAssertion Automatic timestamp assertion settings.
 */
@Serializable
data class BuilderSettingsDefinition(
    val vendor: String? = null,
    @SerialName("claim_generator_info")
    val claimGeneratorInfo: ClaimGeneratorInfoSettings? = null,
    val thumbnail: ThumbnailSettings? = null,
    val actions: ActionsSettings? = null,
    @SerialName("certificate_status_fetch")
    val certificateStatusFetch: OcspFetchScope? = null,
    @SerialName("certificate_status_should_override")
    val certificateStatusShouldOverride: Boolean? = null,
    val intent: SettingsIntent? = null,
    @SerialName("created_assertion_labels")
    val createdAssertionLabels: List<String>? = null,
    @SerialName("prefer_box_hash")
    val preferBoxHash: Boolean? = null,
    @SerialName("generate_c2pa_archive")
    val generateC2paArchive: Boolean? = null,
    @SerialName("auto_timestamp_assertion")
    val autoTimestampAssertion: TimeStampSettings? = null,
)

/**
 * Claim generator information for settings.
 *
 * @property name Name of the claim generator.
 * @property version Version of the claim generator.
 * @property icon Reference to an icon resource.
 * @property operatingSystem Operating system identifier (null for auto-detection).
 * @property other Additional key-value pairs flattened into the JSON output.
 */
@Serializable
data class ClaimGeneratorInfoSettings(
    val name: String,
    val version: String? = null,
    val icon: ResourceRef? = null,
    @SerialName("operating_system")
    val operatingSystem: String? = null,
    val other: Map<String, JsonElement>? = null,
)

/**
 * Thumbnail generation settings.
 *
 * @property enabled Whether to generate thumbnails.
 * @property ignoreErrors Whether to ignore thumbnail generation errors.
 * @property longEdge Maximum long edge size in pixels.
 * @property format Thumbnail image format.
 * @property preferSmallestFormat Whether to prefer the smallest format.
 * @property quality Thumbnail image quality.
 */
@Serializable
data class ThumbnailSettings(
    val enabled: Boolean? = null,
    @SerialName("ignore_errors")
    val ignoreErrors: Boolean? = null,
    @SerialName("long_edge")
    val longEdge: Int? = null,
    val format: ThumbnailFormat? = null,
    @SerialName("prefer_smallest_format")
    val preferSmallestFormat: Boolean? = null,
    val quality: ThumbnailQuality? = null,
)

/** Thumbnail image format. */
@Serializable
enum class ThumbnailFormat {
    @SerialName("png")
    PNG,

    @SerialName("jpeg")
    JPEG,

    @SerialName("gif")
    GIF,

    @SerialName("webp")
    WEBP,

    @SerialName("tiff")
    TIFF,
    ;
}

/** Thumbnail image quality. */
@Serializable
enum class ThumbnailQuality {
    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,
    ;
}

/**
 * Actions settings for configuring automatic action generation.
 *
 * @property allActionsIncluded Whether all actions are included.
 * @property templates Action templates added to the Actions assertion.
 * @property actions Action definitions (excluded from the JSON schema due to CBOR dependency; use [JsonElement]).
 * @property autoCreatedAction Auto-generated created action settings.
 * @property autoOpenedAction Auto-generated opened action settings.
 * @property autoPlacedAction Auto-generated placed action settings.
 */
@Serializable
data class ActionsSettings(
    @SerialName("all_actions_included")
    val allActionsIncluded: Boolean? = null,
    val templates: List<ActionTemplateSettings>? = null,
    val actions: JsonElement? = null,
    @SerialName("auto_created_action")
    val autoCreatedAction: AutoActionSettings? = null,
    @SerialName("auto_opened_action")
    val autoOpenedAction: AutoActionSettings? = null,
    @SerialName("auto_placed_action")
    val autoPlacedAction: AutoActionSettings? = null,
)

/**
 * Settings for an action template.
 *
 * @property action The label associated with this action (e.g., "c2pa.created").
 * @property softwareAgent The software agent that performed the action.
 * @property softwareAgentIndex 0-based index into the softwareAgents array.
 * @property sourceType One of the defined URI values at `https://cv.iptc.org/newscodes/digitalsourcetype/`.
 * @property icon Reference to an icon resource.
 * @property description Description of the template.
 * @property templateParameters Additional parameters for the template.
 */
@Serializable
data class ActionTemplateSettings(
    val action: String,
    @SerialName("software_agent")
    val softwareAgent: ClaimGeneratorInfoSettings? = null,
    @SerialName("software_agent_index")
    val softwareAgentIndex: Int? = null,
    @SerialName("source_type")
    val sourceType: String? = null,
    val icon: ResourceRef? = null,
    val description: String? = null,
    @SerialName("template_parameters")
    val templateParameters: JsonObject? = null,
)

/**
 * Settings for automatic action generation.
 *
 * @property enabled Whether this auto action is enabled.
 * @property sourceType Digital source type for the action.
 */
@Serializable
data class AutoActionSettings(
    val enabled: Boolean,
    @SerialName("source_type")
    val sourceType: String? = null,
)

/**
 * Timestamp assertion settings.
 *
 * @property enabled Whether to auto-generate a timestamp assertion.
 * @property skipExisting Whether to skip fetching timestamps for manifests that already have one.
 * @property fetchScope Which manifests to fetch timestamps for.
 */
@Serializable
data class TimeStampSettings(
    val enabled: Boolean? = null,
    @SerialName("skip_existing")
    val skipExisting: Boolean? = null,
    @SerialName("fetch_scope")
    val fetchScope: TimeStampFetchScope? = null,
)

/** Scope of manifests to fetch timestamps for. */
@Serializable
enum class TimeStampFetchScope {
    @SerialName("parent")
    PARENT,

    @SerialName("all")
    ALL,
    ;
}

/** Scope of manifests to fetch OCSP responses for. */
@Serializable
enum class OcspFetchScope {
    @SerialName("all")
    ALL,

    @SerialName("active")
    ACTIVE,
    ;
}

/**
 * Builder intent for settings, matching the Rust `BuilderIntent` enum.
 *
 * Serialized as:
 * - `"edit"` for [Edit]
 * - `"update"` for [Update]
 * - `{"create": "<digital_source_type_url>"}` for [Create]
 */
@Serializable(with = SettingsIntentSerializer::class)
sealed class SettingsIntent {

    /**
     * A new digital creation with the specified digital source type URL.
     *
     * @property digitalSourceType The IPTC/C2PA digital source type URL.
     */
    data class Create(val digitalSourceType: String) : SettingsIntent()

    /** An edit of a pre-existing parent asset. */
    data object Edit : SettingsIntent()

    /** A restricted version of edit for non-editorial changes. */
    data object Update : SettingsIntent()
}

/**
 * Custom serializer for [SettingsIntent] that handles the polymorphic JSON format.
 *
 * - `"edit"` / `"update"` are serialized as plain strings.
 * - `Create` is serialized as `{"create": "<digital_source_type>"}`.
 */
internal object SettingsIntentSerializer : KSerializer<SettingsIntent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SettingsIntent")

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: SettingsIntent) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("SettingsIntent can only be serialized as JSON")

        val element: JsonElement = when (value) {
            is SettingsIntent.Edit -> JsonPrimitive("edit")
            is SettingsIntent.Update -> JsonPrimitive("update")
            is SettingsIntent.Create -> buildJsonObject {
                put("create", JsonPrimitive(value.digitalSourceType))
            }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): SettingsIntent {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("SettingsIntent can only be deserialized from JSON")

        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString -> {
                when (element.content) {
                    "edit" -> SettingsIntent.Edit
                    "update" -> SettingsIntent.Update
                    else -> throw IllegalStateException("Unknown intent string: ${element.content}")
                }
            }
            element is JsonObject -> {
                val sourceType = element["create"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Expected 'create' key in intent object")
                SettingsIntent.Create(sourceType)
            }
            else -> throw IllegalStateException("Unexpected JSON element for SettingsIntent: $element")
        }
    }
}

/**
 * Signer settings, matching the Rust `SignerSettings` enum.
 *
 * Serialized as `{"local": {...}}` or `{"remote": {...}}`.
 */
@Serializable(with = SignerSettingsSerializer::class)
sealed class SignerSettings {

    /**
     * A locally configured signer.
     *
     * @property alg Signing algorithm.
     * @property signCert Certificate chain in PEM format.
     * @property privateKey Private key in PEM format.
     * @property tsaUrl Time stamp authority URL.
     * @property referencedAssertions Referenced assertions for CAWG identity signing.
     * @property roles Roles for CAWG identity signing.
     */
    data class Local(
        val alg: String,
        @SerialName("sign_cert")
        val signCert: String,
        @SerialName("private_key")
        val privateKey: String,
        @SerialName("tsa_url")
        val tsaUrl: String? = null,
        @SerialName("referenced_assertions")
        val referencedAssertions: List<String>? = null,
        val roles: List<String>? = null,
    ) : SignerSettings()

    /**
     * A remotely configured signer.
     *
     * @property url URL that the signer will use for signing.
     * @property alg Signing algorithm.
     * @property signCert Certificate chain in PEM format.
     * @property tsaUrl Time stamp authority URL.
     * @property referencedAssertions Referenced assertions for CAWG identity signing.
     * @property roles Roles for CAWG identity signing.
     */
    data class Remote(
        val url: String,
        val alg: String,
        @SerialName("sign_cert")
        val signCert: String,
        @SerialName("tsa_url")
        val tsaUrl: String? = null,
        @SerialName("referenced_assertions")
        val referencedAssertions: List<String>? = null,
        val roles: List<String>? = null,
    ) : SignerSettings()
}

/**
 * Custom serializer for [SignerSettings] that handles the tagged enum format.
 *
 * - Local is serialized as `{"local": {"alg": ..., "sign_cert": ..., "private_key": ...}}`.
 * - Remote is serialized as `{"remote": {"url": ..., "alg": ..., "sign_cert": ...}}`.
 */
internal object SignerSettingsSerializer : KSerializer<SignerSettings> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SignerSettings")

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: SignerSettings) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("SignerSettings can only be serialized as JSON")

        val jsonObject = when (value) {
            is SignerSettings.Local -> buildJsonObject {
                put("local", buildJsonObject {
                    put("alg", JsonPrimitive(value.alg))
                    put("sign_cert", JsonPrimitive(value.signCert))
                    put("private_key", JsonPrimitive(value.privateKey))
                    value.tsaUrl?.let { put("tsa_url", JsonPrimitive(it)) }
                    value.referencedAssertions?.let { refs ->
                        put("referenced_assertions", kotlinx.serialization.json.JsonArray(
                            refs.map { JsonPrimitive(it) },
                        ))
                    }
                    value.roles?.let { roles ->
                        put("roles", kotlinx.serialization.json.JsonArray(
                            roles.map { JsonPrimitive(it) },
                        ))
                    }
                })
            }
            is SignerSettings.Remote -> buildJsonObject {
                put("remote", buildJsonObject {
                    put("url", JsonPrimitive(value.url))
                    put("alg", JsonPrimitive(value.alg))
                    put("sign_cert", JsonPrimitive(value.signCert))
                    value.tsaUrl?.let { put("tsa_url", JsonPrimitive(it)) }
                    value.referencedAssertions?.let { refs ->
                        put("referenced_assertions", kotlinx.serialization.json.JsonArray(
                            refs.map { JsonPrimitive(it) },
                        ))
                    }
                    value.roles?.let { roles ->
                        put("roles", kotlinx.serialization.json.JsonArray(
                            roles.map { JsonPrimitive(it) },
                        ))
                    }
                })
            }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): SignerSettings {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("SignerSettings can only be deserialized from JSON")

        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return when {
            "local" in jsonObject -> {
                val local = jsonObject["local"]!!.jsonObject
                SignerSettings.Local(
                    alg = local["alg"]!!.jsonPrimitive.content,
                    signCert = local["sign_cert"]!!.jsonPrimitive.content,
                    privateKey = local["private_key"]!!.jsonPrimitive.content,
                    tsaUrl = local["tsa_url"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                    referencedAssertions = local["referenced_assertions"]
                        ?.takeIf { it !is JsonNull }
                        ?.let { element ->
                            (element as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }
                        },
                    roles = local["roles"]
                        ?.takeIf { it !is JsonNull }
                        ?.let { element ->
                            (element as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }
                        },
                )
            }
            "remote" in jsonObject -> {
                val remote = jsonObject["remote"]!!.jsonObject
                SignerSettings.Remote(
                    url = remote["url"]!!.jsonPrimitive.content,
                    alg = remote["alg"]!!.jsonPrimitive.content,
                    signCert = remote["sign_cert"]!!.jsonPrimitive.content,
                    tsaUrl = remote["tsa_url"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                    referencedAssertions = remote["referenced_assertions"]
                        ?.takeIf { it !is JsonNull }
                        ?.let { element ->
                            (element as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }
                        },
                    roles = remote["roles"]
                        ?.takeIf { it !is JsonNull }
                        ?.let { element ->
                            (element as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }
                        },
                )
            }
            else -> throw IllegalStateException("Expected 'local' or 'remote' key in SignerSettings")
        }
    }
}
