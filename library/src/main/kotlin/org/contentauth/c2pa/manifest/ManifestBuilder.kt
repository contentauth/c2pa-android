package org.contentauth.c2pa.manifest

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class ClaimGenerator(
    val name: String,
    val version: String,
    val icon: String? = null,
    val operatingSystem: String? = null
)

data class Ingredient(
    val title: String? = null,
    val format: String,
    val documentId: String? = null,
    val provenance: String? = null,
    val hash: String? = null,
    val relationship: String = "parentOf",
    val validationStatus: List<String> = emptyList(),
    val thumbnail: Thumbnail? = null
)

data class Thumbnail(
    val format: String,
    val identifier: String,
    val contentType: String = "image/jpeg"
)

data class Action(
    val action: String,
    val whenTimestamp: String? = null,
    val softwareAgent: SoftwareAgent? = null,
    val changes: List<ActionChange> = emptyList(),
    val reason: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val digitalSourceType: String? = null
)

data class SoftwareAgent(
    val name: String,
    val version: String,
    val operatingSystem: String
)

data class ActionChange(
    val field: String,
    val description: String
)

class ManifestBuilder {
    private var claimGenerator: ClaimGenerator? = null
    private var format: String? = null
    private var title: String? = null
    private var documentId: String? = null
    private val ingredients = mutableListOf<Ingredient>()
    private val actions = mutableListOf<Action>()
    private val assertions = mutableMapOf<String, Any>()
    private var thumbnail: Thumbnail? = null
    private var producer: String? = null
    private var taUrl: String? = null

    fun claimGenerator(name: String, version: String, icon: String? = null, operatingSystem: String? = null): ManifestBuilder {
        claimGenerator = ClaimGenerator(name, version, icon, operatingSystem)
        return this
    }

    fun format(format: String): ManifestBuilder {
        this.format = format
        return this
    }

    fun title(title: String): ManifestBuilder {
        this.title = title
        return this
    }

    fun documentId(documentId: String): ManifestBuilder {
        this.documentId = documentId
        return this
    }

    fun producer(producer: String): ManifestBuilder {
        this.producer = producer
        return this
    }

    fun timestampAuthorityUrl(taUrl: String): ManifestBuilder {
        this.taUrl = taUrl
        return this
    }

    fun addIngredient(ingredient: Ingredient): ManifestBuilder {
        ingredients.add(ingredient)
        return this
    }

    fun addAction(action: Action): ManifestBuilder {
        actions.add(action)
        return this
    }

    fun addThumbnail(thumbnail: Thumbnail): ManifestBuilder {
        this.thumbnail = thumbnail
        return this
    }

    fun addAssertion(type: String, data: Any): ManifestBuilder {
        assertions[type] = data
        return this
    }

    fun build(): JSONObject {
        val manifest = JSONObject()

        // Add claim version
        manifest.put("claim_version", 1)
        
        // Add timestamp authority URL if present
        taUrl?.let { manifest.put("ta_url", it) }

        // Add basic manifest fields
        format?.let { manifest.put("format", it) }
        title?.let { manifest.put("title", it) }
        documentId?.let { manifest.put("documentID", it) }
        producer?.let { manifest.put("producer", it) }

        // Add claim generator info as array
        claimGenerator?.let { generator ->
            manifest.put("claim_generator_info", JSONObject().apply {
                put("name", generator.name)
                put("version", generator.version)
                generator.icon?.let { put("icon", it) }
                generator.operatingSystem?.let { put("operating_system", it) }
            })
        }

        // Add thumbnail
        thumbnail?.let { thumb ->
            manifest.put("thumbnail", JSONObject().apply {
                put("format", thumb.format)
                put("identifier", thumb.identifier)
            })
        }

        // Add ingredients
        if (ingredients.isNotEmpty()) {
            manifest.put("ingredients", JSONArray().apply {
                ingredients.forEach { ingredient ->
                    put(JSONObject().apply {
                        ingredient.title?.let { put("title", it) }
                        put("format", ingredient.format)
                        ingredient.documentId?.let { put("documentID", it) }
                        ingredient.provenance?.let { put("provenance", it) }
                        ingredient.hash?.let { put("hash", it) }
                        put("relationship", ingredient.relationship)

                        if (ingredient.validationStatus.isNotEmpty()) {
                            put("validationStatus", JSONArray(ingredient.validationStatus))
                        }

                        
                        if (ingredient.validationStatus.isNotEmpty()) {
                            put("validationStatus", JSONArray(ingredient.validationStatus))
                        }
                        
                        ingredient.thumbnail?.let { thumb ->
                            put("thumbnail", JSONObject().apply {
                                put("format", thumb.format)
                                put("identifier", thumb.identifier)
                            })
                        }
                    })
                }
            })
        }

        // Build assertions array
        val assertionsArray = JSONArray()

        // Add actions as an assertion if present
        if (actions.isNotEmpty()) {
            assertionsArray.put(JSONObject().apply {
                put("label", "c2pa.actions")
                put("data", JSONObject().apply {
                    put("actions", JSONArray().apply {
                        actions.forEach { action ->
                            put(JSONObject().apply {
                                put("action", action.action)
                                action.whenTimestamp?.let { put("when", it) }

                                action.softwareAgent?.let { put("softwareAgent", it) }
                                action.softwareAgent?.let {
                                    put("softwareAgent", JSONObject().apply {
                                        action.softwareAgent?.name.let { put("name", action.softwareAgent?.name) }
                                        action.softwareAgent?.operatingSystem.let { put("operating_system", action.softwareAgent?.operatingSystem) }
                                        action.softwareAgent?.version.let { put("version", action.softwareAgent?.version) }
                                    })
                                }

                                action.reason?.let { put("reason", it) }
                                action.digitalSourceType?.let { put("digitalSourceType", it) }

                                if (action.changes.isNotEmpty()) {
                                    put("changes", JSONArray().apply {
                                        action.changes.forEach { change ->
                                            put(JSONObject().apply {
                                                put("field", change.field)
                                                put("description", change.description)
                                            })
                                        }
                                    })
                                }
                                if (action.parameters.isNotEmpty()) {
                                    val params = JSONObject()
                                    action.parameters.forEach { (key, value) ->
                                        params.put(key, value)
                                    }
                                    put("parameters", params)
                                }
                            })
                        }
                    })
                })
            })
        }

        // Add other assertions
        assertions.forEach { (label, data) ->
            assertionsArray.put(JSONObject().apply {
                put("label", label)
                when (data) {
                    is JSONObject -> put("data", data)
                    is JSONArray -> put("data", data)
                    is String -> put("data", data)
                    is Number -> put("data", data)
                    is Boolean -> put("data", data)
                    else -> put("data", data.toString())
                }
            })
        }

        // Add assertions array if not empty
        if (assertionsArray.length() > 0) {
            manifest.put("assertions", assertionsArray)
        }

        return manifest
    }

    fun buildJson(): String {
        return build().toString(2)
    }
}
