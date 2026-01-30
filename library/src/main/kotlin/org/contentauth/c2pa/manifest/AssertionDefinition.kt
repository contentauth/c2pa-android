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
    data class TrainingMining(
        val entries: List<TrainingMiningEntry>,
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
         * Creates a training/mining assertion.
         */
        fun trainingMining(entries: List<TrainingMiningEntry>) = TrainingMining(entries)

        /**
         * Creates a custom assertion.
         */
        fun custom(label: String, data: JsonElement) = Custom(label, data)
    }
}

/**
 * Represents a training/mining permission entry.
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
 * Custom serializer for AssertionDefinition that handles the label/data structure.
 */
internal object AssertionDefinitionSerializer : KSerializer<AssertionDefinition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AssertionDefinition")

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: AssertionDefinition) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("AssertionDefinition can only be serialized as JSON")

        val jsonObject = when (value) {
            is AssertionDefinition.Actions -> buildJsonObject {
                put("label", StandardAssertionLabel.ACTIONS.serialName())
                put("data", buildJsonObject {
                    put("actions", jsonEncoder.json.encodeToJsonElement(value.actions))
                    value.metadata?.let { put("metadata", jsonEncoder.json.encodeToJsonElement(it)) }
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
                AssertionDefinition.Actions(actions, metadata)
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
            else -> {
                AssertionDefinition.Custom(label, JsonObject(data))
            }
        }
    }
}

private fun StandardAssertionLabel.serialName(): String = when (this) {
    StandardAssertionLabel.ACTIONS -> "c2pa.actions"
    StandardAssertionLabel.ACTIONS_V2 -> "c2pa.actions.v2"
    StandardAssertionLabel.HASH_DATA -> "c2pa.hash.data"
    StandardAssertionLabel.HASH_BOXES -> "c2pa.hash.boxes"
    StandardAssertionLabel.HASH_BMFF_V2 -> "c2pa.hash.bmff.v2"
    StandardAssertionLabel.HASH_COLLECTION -> "c2pa.hash.collection"
    StandardAssertionLabel.SOFT_BINDING -> "c2pa.soft-binding"
    StandardAssertionLabel.CLOUD_DATA -> "c2pa.cloud-data"
    StandardAssertionLabel.THUMBNAIL_CLAIM -> "c2pa.thumbnail.claim"
    StandardAssertionLabel.THUMBNAIL_INGREDIENT -> "c2pa.thumbnail.ingredient"
    StandardAssertionLabel.DEPTHMAP -> "c2pa.depthmap"
    StandardAssertionLabel.TRAINING_MINING -> "c2pa.training-mining"
    StandardAssertionLabel.EXIF -> "stds.exif"
    StandardAssertionLabel.CREATIVE_WORK -> "stds.schema-org.CreativeWork"
    StandardAssertionLabel.IPTC_PHOTO_METADATA -> "stds.iptc.photo-metadata"
    StandardAssertionLabel.ISO_LOCATION -> "stds.iso.location.v1"
    StandardAssertionLabel.CAWG_IDENTITY -> "cawg.identity"
    StandardAssertionLabel.CAWG_AI_TRAINING -> "cawg.ai_training_and_data_mining"
}
