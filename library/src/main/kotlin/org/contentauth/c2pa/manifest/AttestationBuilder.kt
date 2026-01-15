package org.contentauth.c2pa.manifest

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

abstract class Attestation(val type: String) {
    abstract fun toJsonObject(): JSONObject
}

class CreativeWorkAttestation : Attestation(C2PAAssertionTypes.CREATIVE_WORK) {
    private val authors = mutableListOf<Author>()
    private var dateCreated: String? = null
    private var reviewStatus: String? = null

    data class Author(
        val name: String,
        val credential: String? = null,
        val identifier: String? = null
    )

    fun addAuthor(name: String, credential: String? = null, identifier: String? = null): CreativeWorkAttestation {
        authors.add(Author(name, credential, identifier))
        return this
    }

    fun dateCreated(date: Date): CreativeWorkAttestation {
        val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        this.dateCreated = iso8601.format(date)
        return this
    }

    fun dateCreated(isoDateString: String): CreativeWorkAttestation {
        this.dateCreated = isoDateString
        return this
    }

    fun reviewStatus(status: String): CreativeWorkAttestation {
        this.reviewStatus = status
        return this
    }

    override fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            if (authors.isNotEmpty()) {
                put("author", JSONArray().apply {
                    authors.forEach { author ->
                        put(JSONObject().apply {
                            put("name", author.name)
                            author.credential?.let { put("credential", it) }
                            author.identifier?.let { put("@id", it) }
                        })
                    }
                })
            }
            dateCreated?.let { put("dateCreated", it) }
            reviewStatus?.let { put("reviewStatus", it) }
        }
    }
}

class ActionsAttestation : Attestation("c2pa.actions") {
    private val actionsList = mutableListOf<Action>()

    fun addAction(action: Action): ActionsAttestation {
        actionsList.add(action)
        return this
    }

    fun addCreatedAction(softwareAgent: SoftwareAgent? = null, whenTimestamp: String? = null): ActionsAttestation {
        actionsList.add(Action(C2PAActions.CREATED, whenTimestamp, softwareAgent))
        return this
    }

    fun addEditedAction(softwareAgent: SoftwareAgent? = null, whenTimestamp: String? = null, changes: List<ActionChange> = emptyList()): ActionsAttestation {
        actionsList.add(Action(C2PAActions.EDITED, whenTimestamp, softwareAgent, changes))
        return this
    }

    fun addOpenedAction(softwareAgent: SoftwareAgent? = null, whenTimestamp: String? = null): ActionsAttestation {
        actionsList.add(Action(C2PAActions.OPENED, whenTimestamp, softwareAgent))
        return this
    }

    fun addPlacedAction(softwareAgent: String? = null, whenTimestamp: String? = null): ActionsAttestation {
        actionsList.add(Action(C2PAActions.PLACED, whenTimestamp, softwareAgent))
        return this
    }

    fun addDrawingAction(softwareAgent: SoftwareAgent? = null, whenTimestamp: String? = null): ActionsAttestation {
        actionsList.add(Action(C2PAActions.DRAWING, whenTimestamp, softwareAgent))
        return this
    }

    fun addColorAdjustmentsAction(softwareAgent: SoftwareAgent? = null, whenTimestamp: String? = null, parameters: Map<String, Any> = emptyMap()): ActionsAttestation {
        actionsList.add(Action(C2PAActions.COLOR_ADJUSTMENTS, whenTimestamp, softwareAgent, emptyList(), null, parameters))
        return this
    }

    fun addResizedAction(softwareAgent: SoftwareAgent? = null, whenTimestamp: String? = null): ActionsAttestation {
        actionsList.add(Action(C2PAActions.RESIZED, whenTimestamp, softwareAgent))
        return this
    }

    override fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("actions", JSONArray().apply {
                actionsList.forEach { action ->
                    put(JSONObject().apply {
                        put("action", action.action)
                        action.whenTimestamp?.let { put("when", it) }
                        action.softwareAgent?.let { put("softwareAgent", it) }
                        action.reason?.let { put("reason", it) }
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
        }
    }
}

class AssertionMetadataAttestation : Attestation("c2pa.assertion.metadata") {
    private val metadata = mutableMapOf<String, Any>()

    fun addMetadata(key: String, value: Any): AssertionMetadataAttestation {
        metadata[key] = value
        return this
    }

    fun dateTime(dateTime: String): AssertionMetadataAttestation {
        metadata["dateTime"] = dateTime
        return this
    }

    fun location(location: JSONObject): AssertionMetadataAttestation {
        metadata["location"] = location
        return this
    }

    fun device(device: String): AssertionMetadataAttestation {
        metadata["device"] = device
        return this
    }

    override fun toJsonObject(): JSONObject {
        val json = JSONObject()
        metadata.forEach { (key, value) ->
            when (value) {
                is JSONObject, is JSONArray -> json.put(key, value)
                is String -> json.put(key, value)
                is Number -> json.put(key, value)
                is Boolean -> json.put(key, value)
                else -> json.put(key, value.toString())
            }
        }
        return json
    }
}

class ThumbnailAttestation : Attestation("c2pa.thumbnail") {
    private var format: String? = null
    private var identifier: String? = null
    private var contentType: String = "image/jpeg"

    fun format(format: String): ThumbnailAttestation {
        this.format = format
        return this
    }

    fun identifier(identifier: String): ThumbnailAttestation {
        this.identifier = identifier
        return this
    }

    fun contentType(contentType: String): ThumbnailAttestation {
        this.contentType = contentType
        return this
    }

    override fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            format?.let { put("format", it) }
            identifier?.let { put("identifier", it) }
            put("contentType", contentType)
        }
    }
}

class DataHashAttestation : Attestation("c2pa.data_hash") {
    private var exclusions: List<Map<String, Any>> = emptyList()
    private var name: String = "jumbf manifest"
    private var pad: Int? = null

    fun exclusions(exclusions: List<Map<String, Any>>): DataHashAttestation {
        this.exclusions = exclusions
        return this
    }

    fun name(name: String): DataHashAttestation {
        this.name = name
        return this
    }

    fun pad(pad: Int): DataHashAttestation {
        this.pad = pad
        return this
    }

    override fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            if (exclusions.isNotEmpty()) {
                put("exclusions", JSONArray().apply {
                    exclusions.forEach { exclusion ->
                        put(JSONObject().apply {
                            exclusion.forEach { (key, value) ->
                                put(key, value)
                            }
                        })
                    }
                })
            }
            put("name", name)
            pad?.let { put("pad", it) }
        }
    }
}

data class VerifiedIdentity(
    val type: String,
    val username: String,
    val uri: String,
    val verifiedAt: String,
    val provider: IdentityProvider
)

data class IdentityProvider(
    val id: String,
    val name: String
)

data class CredentialSchema(
    val id: String = "https://cawg.io/identity/1.1/ica/schema/",
    val type: String = "JSONSchema"
)

class CAWGIdentityAttestation : Attestation(C2PAAssertionTypes.CAWG_IDENTITY) {
    private val contexts = mutableListOf(
        "https://www.w3.org/ns/credentials/v2",
        "https://cawg.io/identity/1.1/ica/context/"
    )
    private val types = mutableListOf("VerifiableCredential", "IdentityClaimsAggregationCredential")
    private var issuer: String = "did:web:connected-identities.identity.adobe.com"
    private var validFrom: String? = null
    private val verifiedIdentities = mutableListOf<VerifiedIdentity>()
    private val credentialSchemas = mutableListOf<CredentialSchema>()

    init {
        credentialSchemas.add(CredentialSchema())
    }

    fun issuer(issuer: String): CAWGIdentityAttestation {
        this.issuer = issuer
        return this
    }

    fun validFrom(validFrom: String): CAWGIdentityAttestation {
        this.validFrom = validFrom
        return this
    }

    fun validFromNow(): CAWGIdentityAttestation {
        val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        this.validFrom = iso8601.format(Date())
        return this
    }

    fun addContext(context: String): CAWGIdentityAttestation {
        if (!contexts.contains(context)) {
            contexts.add(context)
        }
        return this
    }

    fun addType(type: String): CAWGIdentityAttestation {
        if (!types.contains(type)) {
            types.add(type)
        }
        return this
    }

    fun addVerifiedIdentity(
        type: String,
        username: String,
        uri: String,
        verifiedAt: String,
        providerId: String,
        providerName: String
    ): CAWGIdentityAttestation {
        verifiedIdentities.add(
            VerifiedIdentity(
                type = type,
                username = username,
                uri = uri,
                verifiedAt = verifiedAt,
                provider = IdentityProvider(providerId, providerName)
            )
        )
        return this
    }

    fun addSocialMediaIdentity(
        username: String,
        uri: String,
        verifiedAt: String,
        providerId: String,
        providerName: String
    ): CAWGIdentityAttestation {
        return addVerifiedIdentity(CAWGIdentityTypes.SOCIAL_MEDIA, username, uri, verifiedAt, providerId, providerName)
    }

    fun addInstagramIdentity(username: String, verifiedAt: String): CAWGIdentityAttestation {
        return addSocialMediaIdentity(
            username = username,
            uri = "https://www.instagram.com/$username",
            verifiedAt = verifiedAt,
            providerId = CAWGProviders.INSTAGRAM,
            providerName = "instagram"
        )
    }

    fun addTwitterIdentity(username: String, verifiedAt: String): CAWGIdentityAttestation {
        return addSocialMediaIdentity(
            username = username,
            uri = "https://twitter.com/$username",
            verifiedAt = verifiedAt,
            providerId = CAWGProviders.TWITTER,
            providerName = "twitter"
        )
    }

    fun addLinkedInIdentity(displayName: String, profileUrl: String, verifiedAt: String): CAWGIdentityAttestation {
        return addSocialMediaIdentity(
            username = displayName,
            uri = profileUrl,
            verifiedAt = verifiedAt,
            providerId = CAWGProviders.LINKEDIN,
            providerName = "linkedin"
        )
    }

    fun addBehanceIdentity(username: String, verifiedAt: String): CAWGIdentityAttestation {
        return addSocialMediaIdentity(
            username = username,
            uri = "https://www.behance.net/$username",
            verifiedAt = verifiedAt,
            providerId = CAWGProviders.BEHANCE,
            providerName = "behance"
        )
    }

    fun addYouTubeIdentity(channelName: String, channelUrl: String, verifiedAt: String): CAWGIdentityAttestation {
        return addSocialMediaIdentity(
            username = channelName,
            uri = channelUrl,
            verifiedAt = verifiedAt,
            providerId = CAWGProviders.YOUTUBE,
            providerName = "youtube"
        )
    }

    fun addGitHubIdentity(username: String, verifiedAt: String): CAWGIdentityAttestation {
        return addSocialMediaIdentity(
            username = username,
            uri = "https://github.com/$username",
            verifiedAt = verifiedAt,
            providerId = CAWGProviders.GITHUB,
            providerName = "github"
        )
    }

    fun addCredentialSchema(id: String, type: String): CAWGIdentityAttestation {
        credentialSchemas.add(CredentialSchema(id, type))
        return this
    }

    override fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("@context", JSONArray(contexts))
            put("type", JSONArray(types))
            put("issuer", issuer)
            validFrom?.let { put("validFrom", it) }

            if (verifiedIdentities.isNotEmpty()) {
                put("verifiedIdentities", JSONArray().apply {
                    verifiedIdentities.forEach { identity ->
                        put(JSONObject().apply {
                            put("type", identity.type)
                            put("username", identity.username)
                            put("uri", identity.uri)
                            put("verifiedAt", identity.verifiedAt)
                            put("provider", JSONObject().apply {
                                put("id", identity.provider.id)
                                put("name", identity.provider.name)
                            })
                        })
                    }
                })
            }

            if (credentialSchemas.isNotEmpty()) {
                put("credentialSchema", JSONArray().apply {
                    credentialSchemas.forEach { schema ->
                        put(JSONObject().apply {
                            put("id", schema.id)
                            put("type", schema.type)
                        })
                    }
                })
            }
        }
    }
}

class AttestationBuilder {
    private val attestations = mutableListOf<Attestation>()

    fun addCreativeWork(configure: CreativeWorkAttestation.() -> Unit): AttestationBuilder {
        val attestation = CreativeWorkAttestation()
        attestation.configure()
        attestations.add(attestation)
        return this
    }

    fun addActions(configure: ActionsAttestation.() -> Unit): AttestationBuilder {
        val attestation = ActionsAttestation()
        attestation.configure()
        attestations.add(attestation)
        return this
    }

    fun addAssertionMetadata(configure: AssertionMetadataAttestation.() -> Unit): AttestationBuilder {
        val attestation = AssertionMetadataAttestation()
        attestation.configure()
        attestations.add(attestation)
        return this
    }

    fun addThumbnail(configure: ThumbnailAttestation.() -> Unit): AttestationBuilder {
        val attestation = ThumbnailAttestation()
        attestation.configure()
        attestations.add(attestation)
        return this
    }

    fun addDataHash(configure: DataHashAttestation.() -> Unit): AttestationBuilder {
        val attestation = DataHashAttestation()
        attestation.configure()
        attestations.add(attestation)
        return this
    }

    fun addCAWGIdentity(configure: CAWGIdentityAttestation.() -> Unit): AttestationBuilder {
        val attestation = CAWGIdentityAttestation()
        attestation.configure()
        attestations.add(attestation)
        return this
    }

    fun addCustomAttestation(type: String, data: JSONObject): AttestationBuilder {
        attestations.add(object : Attestation(type) {
            override fun toJsonObject(): JSONObject = data
        })
        return this
    }

    fun build(): Map<String, JSONObject> {
        return attestations.associate { it.type to it.toJsonObject() }
    }

    fun buildForManifest(manifestBuilder: ManifestBuilder): ManifestBuilder {
        val builtAttestations = build()
        builtAttestations.forEach { (type, data) ->
            manifestBuilder.addAssertion(type, data)
        }
        return manifestBuilder
    }
}
