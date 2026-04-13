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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Represents a C2PA assertion within a manifest.
 *
 * Assertions are statements about the asset, such as actions performed,
 * metadata, or cryptographic bindings.
 *
 * @see ManifestDefinition
 * @see StandardAssertionLabel
 */
@Serializable(with = AssertionDefinitionSerializer::class)
sealed class AssertionDefinition {
    /**
     * An actions assertion containing a list of actions performed on the asset.
     *
     * @property actions The list of actions performed.
     * @property metadata Optional metadata about the actions.
     */
    data class Actions(
        val actions: List<ActionAssertion>,
        val metadata: Metadata? = null,
        val templates: List<ActionAssertion>? = null,
        val softwareAgents: List<ClaimGeneratorInfo>? = null,
        val allActionsIncluded: Boolean? = null,
    ) : AssertionDefinition()

    /**
     * A creative work assertion containing Schema.org CreativeWork metadata.
     *
     * @property data The creative work data as key-value pairs.
     */
    data class CreativeWork(
        val data: Map<String, JsonElement>,
    ) : AssertionDefinition()

    /**
     * An EXIF metadata assertion.
     *
     * @property data The EXIF data as key-value pairs.
     */
    data class Exif(
        val data: Map<String, JsonElement>,
    ) : AssertionDefinition()

    /**
     * An IPTC photo metadata assertion.
     *
     * @property data The IPTC photo metadata as key-value pairs.
     */
    data class IptcPhotoMetadata(
        val data: Map<String, JsonElement>,
    ) : AssertionDefinition()

    /**
     * A training/mining assertion specifying AI training permissions.
     *
     * @property entries The training/mining permission entries.
     */
    @Deprecated(
        message = "c2pa.training-mining is deprecated. Use CawgTrainingMining (cawg.training-mining) instead.",
        replaceWith = ReplaceWith("CawgTrainingMining"),
    )
    data class TrainingMining(
        val entries: List<TrainingMiningEntry>,
    ) : AssertionDefinition()

    /**
     * A CAWG AI training and data mining assertion.
     *
     * This follows the CAWG AI Training and Data Mining specification format,
     * which is distinct from the C2PA training-mining assertion.
     *
     * @property entries The CAWG training/mining permission entries.
     */
    data class CawgTrainingMining(
        val entries: List<CawgTrainingMiningEntry>,
    ) : AssertionDefinition()

    /**
     * A custom assertion with an arbitrary label and data.
     *
     * @property label The assertion label.
     * @property data The assertion data.
     */
    data class Custom(
        val label: String,
        val data: JsonElement,
    ) : AssertionDefinition()

    /**
     * Returns the base label for this assertion type.
     *
     * The base label is used by the SDK to determine whether this assertion should be
     * placed in `created_assertions` or `gathered_assertions` based on the
     * `builder.created_assertion_labels` setting.
     */
    @Suppress("DEPRECATION")
    fun baseLabel(): String = when (this) {
        is Actions -> "c2pa.actions"
        is CreativeWork -> "stds.schema-org.CreativeWork"
        is Exif -> "stds.exif"
        is IptcPhotoMetadata -> "stds.iptc.photo-metadata"
        is TrainingMining -> "c2pa.training-mining"
        is CawgTrainingMining -> "cawg.training-mining"
        is Custom -> label
    }

    companion object {
        /**
         * Creates an actions assertion with the specified actions.
         */
        fun actions(
            actions: List<ActionAssertion>,
            metadata: Metadata? = null,
        ) = Actions(actions, metadata)

        /**
         * Creates a single action assertion.
         */
        fun action(action: ActionAssertion) = Actions(listOf(action))

        /**
         * Creates a creative work assertion.
         */
        fun creativeWork(data: Map<String, JsonElement>) = CreativeWork(data)

        /**
         * Creates an EXIF assertion.
         */
        fun exif(data: Map<String, JsonElement>) = Exif(data)

        /**
         * Creates a training/mining assertion (C2PA format).
         */
        @Deprecated(
            message = "c2pa.training-mining is deprecated. Use cawgTrainingMining instead.",
            replaceWith = ReplaceWith("cawgTrainingMining(entries)"),
        )
        @Suppress("DEPRECATION")
        fun trainingMining(entries: List<TrainingMiningEntry>) = TrainingMining(entries)

        /**
         * Creates a CAWG AI training and data mining assertion.
         */
        fun cawgTrainingMining(entries: List<CawgTrainingMiningEntry>) = CawgTrainingMining(entries)

        /**
         * Creates a custom assertion.
         */
        fun custom(label: String, data: JsonElement) = Custom(label, data)
    }
}

/**
 * Represents a training/mining permission entry (C2PA format).
 *
 * @property use The type of use (e.g., "allowed", "notAllowed", "constrained").
 * @property constraint Optional constraint URL or description.
 */
@Serializable
data class TrainingMiningEntry(
    val use: String,
    @SerialName("constraint_info")
    val constraintInfo: String? = null,
)

/**
 * Represents a CAWG AI training and data mining permission entry.
 *
 * This follows the CAWG specification format which has additional fields
 * compared to the C2PA training-mining assertion.
 *
 * @property use The use permission: "allowed", "notAllowed", or "constrained".
 * @property constraintInfo Optional constraint information URI.
 * @property aiModelLearningType Optional learning type: "dataAggregation" or "machineLearning".
 * @property aiMiningType Optional mining type: "dataAggregation" or "other".
 */
@Serializable
data class CawgTrainingMiningEntry(
    val use: String,
    @SerialName("constraint_info")
    val constraintInfo: String? = null,
    @SerialName("ai_model_learning_type")
    val aiModelLearningType: String? = null,
    @SerialName("ai_mining_type")
    val aiMiningType: String? = null,
)

/**
 * Custom serializer for AssertionDefinition that handles the label/data structure.
 */
@Suppress("DEPRECATION")
internal object AssertionDefinitionSerializer : KSerializer<AssertionDefinition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AssertionDefinition")

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: AssertionDefinition) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("AssertionDefinition can only be serialized as JSON")

        val jsonObject = when (value) {
            is AssertionDefinition.Actions -> buildJsonObject {
                put("label", StandardAssertionLabel.ACTIONS_V2.serialName())
                put("data", buildJsonObject {
                    put("actions", jsonEncoder.json.encodeToJsonElement(value.actions))
                    value.metadata?.let { put("metadata", jsonEncoder.json.encodeToJsonElement(it)) }
                    value.templates?.let { put("templates", jsonEncoder.json.encodeToJsonElement(it)) }
                    value.softwareAgents?.let { put("softwareAgents", jsonEncoder.json.encodeToJsonElement(it)) }
                    value.allActionsIncluded?.let { put("allActionsIncluded", it) }
                })
            }
            is AssertionDefinition.CreativeWork -> buildJsonObject {
                put("label", StandardAssertionLabel.CREATIVE_WORK.serialName())
                put("data", JsonObject(value.data))
            }
            is AssertionDefinition.Exif -> buildJsonObject {
                put("label", StandardAssertionLabel.EXIF.serialName())
                put("data", JsonObject(value.data))
            }
            is AssertionDefinition.IptcPhotoMetadata -> buildJsonObject {
                put("label", StandardAssertionLabel.IPTC_PHOTO_METADATA.serialName())
                put("data", JsonObject(value.data))
            }
            is AssertionDefinition.TrainingMining -> buildJsonObject {
                put("label", StandardAssertionLabel.TRAINING_MINING.serialName())
                put("data", buildJsonObject {
                    put("entries", jsonEncoder.json.encodeToJsonElement(value.entries))
                })
            }
            is AssertionDefinition.CawgTrainingMining -> buildJsonObject {
                put("label", StandardAssertionLabel.CAWG_AI_TRAINING.serialName())
                put("data", buildJsonObject {
                    put("entries", jsonEncoder.json.encodeToJsonElement(value.entries))
                })
            }
            is AssertionDefinition.Custom -> buildJsonObject {
                put("label", value.label)
                put("data", value.data)
            }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): AssertionDefinition {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("AssertionDefinition can only be deserialized from JSON")

        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val label = jsonObject["label"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing label in assertion")
        val data = jsonObject["data"]?.jsonObject
            ?: throw IllegalStateException("Missing data in assertion")

        return when (label) {
            StandardAssertionLabel.ACTIONS.serialName(),
            StandardAssertionLabel.ACTIONS_V2.serialName() -> {
                val actions = data["actions"]?.let {
                    jsonDecoder.json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(ActionAssertion.serializer()),
                        it,
                    )
                } ?: emptyList()
                val metadata = data["metadata"]?.let {
                    jsonDecoder.json.decodeFromJsonElement(Metadata.serializer(), it)
                }
                val templates = data["templates"]?.let {
                    jsonDecoder.json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(ActionAssertion.serializer()),
                        it,
                    )
                }
                val softwareAgents = data["softwareAgents"]?.let {
                    jsonDecoder.json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(ClaimGeneratorInfo.serializer()),
                        it,
                    )
                }
                val allActionsIncluded = data["allActionsIncluded"]?.jsonPrimitive?.let {
                    it.content.toBooleanStrictOrNull()
                }
                AssertionDefinition.Actions(actions, metadata, templates, softwareAgents, allActionsIncluded)
            }
            StandardAssertionLabel.CREATIVE_WORK.serialName() -> {
                AssertionDefinition.CreativeWork(data.toMap())
            }
            StandardAssertionLabel.EXIF.serialName() -> {
                AssertionDefinition.Exif(data.toMap())
            }
            StandardAssertionLabel.IPTC_PHOTO_METADATA.serialName() -> {
                AssertionDefinition.IptcPhotoMetadata(data.toMap())
            }
            StandardAssertionLabel.TRAINING_MINING.serialName() -> {
                val entries = data["entries"]?.let {
                    jsonDecoder.json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(TrainingMiningEntry.serializer()),
                        it,
                    )
                } ?: emptyList()
                AssertionDefinition.TrainingMining(entries)
            }
            StandardAssertionLabel.CAWG_AI_TRAINING.serialName() -> {
                val entries = data["entries"]?.let {
                    jsonDecoder.json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(CawgTrainingMiningEntry.serializer()),
                        it,
                    )
                } ?: emptyList()
                AssertionDefinition.CawgTrainingMining(entries)
            }
            else -> {
                AssertionDefinition.Custom(label, JsonObject(data))
            }
        }
    }
}
