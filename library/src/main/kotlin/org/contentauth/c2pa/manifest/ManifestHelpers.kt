package org.contentauth.c2pa.manifest

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object  ManifestHelpers {
    
    fun createBasicImageManifest(
        title: String,
        format: String = C2PAFormats.JPEG,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0",
        timestampAuthorityUrl: String? = null
    ): ManifestBuilder {
        val builder = ManifestBuilder()
            .title(title)
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
        
        timestampAuthorityUrl?.let { builder.timestampAuthorityUrl(it) }
        return builder
    }

    fun createImageEditManifest(
        title: String,
        originalIngredientTitle: String,
        originalFormat: String = C2PAFormats.JPEG,
        format: String = C2PAFormats.JPEG,
        softwareAgent: String? = null,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val ingredient = Ingredient(
            title = originalIngredientTitle,
            format = originalFormat,
            relationship = C2PARelationships.PARENT_OF
        )
        
        return ManifestBuilder()
            .title(title)
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addIngredient(ingredient)
            .addAction(Action(
                action = C2PAActions.EDITED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = softwareAgent
            ))
    }

    fun createPhotoManifest(
        title: String,
        format: String = C2PAFormats.JPEG,
        authorName: String? = null,
        deviceName: String? = null,
        location: JSONObject? = null,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val builder = ManifestBuilder()
            .title(title)
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addAction(Action(
                action = C2PAActions.CREATED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = deviceName
            ))

        if (authorName != null || deviceName != null || location != null) {
            val attestationBuilder = AttestationBuilder()
            
            if (authorName != null) {
                attestationBuilder.addCreativeWork {
                    addAuthor(authorName)
                    dateCreated(Date())
                }
            }
            
            attestationBuilder.addAssertionMetadata {
                dateTime(getCurrentTimestamp())
                deviceName?.let { device(it) }
                location?.let { location(it) }
            }
            
            attestationBuilder.buildForManifest(builder)
        }

        return builder
    }

    fun createVideoEditManifest(
        title: String,
        originalIngredientTitle: String,
        originalFormat: String = C2PAFormats.MP4,
        format: String = C2PAFormats.MP4,
        editingSoftware: String? = null,
        editActions: List<String> = emptyList(),
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val ingredient = Ingredient(
            title = originalIngredientTitle,
            format = originalFormat,
            relationship = C2PARelationships.PARENT_OF
        )
        
        val builder = ManifestBuilder()
            .title(title)
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addIngredient(ingredient)

        val timestamp = getCurrentTimestamp()
        
        builder.addAction(Action(
            action = C2PAActions.OPENED,
            whenTimestamp = timestamp,
            softwareAgent = editingSoftware
        ))

        editActions.forEach { actionType ->
            builder.addAction(Action(
                action = actionType,
                whenTimestamp = timestamp,
                softwareAgent = editingSoftware
            ))
        }

        return builder
    }

    fun createCompositeManifest(
        title: String,
        format: String = C2PAFormats.JPEG,
        ingredients: List<Ingredient>,
        compositingSoftware: String? = null,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val builder = ManifestBuilder()
            .title(title)
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addAction(Action(
                action = C2PAActions.CREATED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = compositingSoftware
            ))

        ingredients.forEach { ingredient ->
            builder.addIngredient(ingredient)
            builder.addAction(Action(
                action = C2PAActions.PLACED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = compositingSoftware
            ))
        }

        return builder
    }

    fun createScreenshotManifest(
        deviceName: String,
        appName: String? = null,
        format: String = C2PAFormats.PNG,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val builder = ManifestBuilder()
            .title("Screenshot")
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .producer(deviceName)
            .addAction(Action(
                action = C2PAActions.CREATED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = appName ?: "Screenshot"
            ))

        val attestationBuilder = AttestationBuilder()
        attestationBuilder.addAssertionMetadata {
            device(deviceName)
            dateTime(getCurrentTimestamp())
            addMetadata("capture_method", "screenshot")
            appName?.let { addMetadata("source_application", it) }
        }
        
        attestationBuilder.buildForManifest(builder)
        return builder
    }

    fun createSocialMediaShareManifest(
        originalTitle: String,
        platform: String,
        originalFormat: String = C2PAFormats.JPEG,
        format: String = C2PAFormats.JPEG,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val ingredient = Ingredient(
            title = originalTitle,
            format = originalFormat,
            relationship = C2PARelationships.PARENT_OF
        )
        
        return ManifestBuilder()
            .title("Shared on $platform")
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addIngredient(ingredient)
            .addAction(Action(
                action = C2PAActions.RECOMPRESSED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = platform,
                reason = "Social media optimization"
            ))
            .addAction(Action(
                action = C2PAActions.PUBLISHED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = platform
            ))
    }

    fun createFilteredImageManifest(
        originalTitle: String,
        filterName: String,
        originalFormat: String = C2PAFormats.JPEG,
        format: String = C2PAFormats.JPEG,
        appName: String? = null,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val ingredient = Ingredient(
            title = originalTitle,
            format = originalFormat,
            relationship = C2PARelationships.PARENT_OF
        )
        
        val filterParameters = mapOf(
            "filter_name" to filterName,
            "filter_type" to "digital_filter"
        )
        
        return ManifestBuilder()
            .title("$originalTitle (Filtered)")
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addIngredient(ingredient)
            .addAction(Action(
                action = C2PAActions.FILTERED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = appName,
                parameters = filterParameters
            ))
    }

    private fun getCurrentTimestamp(): String {
        val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return iso8601.format(Date())
    }

    fun createLocation(latitude: Double, longitude: Double, name: String? = null): JSONObject {
        return JSONObject().apply {
            put("@type", "Place")
            put("latitude", latitude)
            put("longitude", longitude)
            name?.let { put("name", it) }
        }
    }

    fun createGeoLocation(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null,
        accuracy: Double? = null
    ): JSONObject {
        return JSONObject().apply {
            put("@type", "GeoCoordinates")
            put("latitude", latitude)
            put("longitude", longitude)
            altitude?.let { put("elevation", it) }
            accuracy?.let { put("accuracy", it) }
        }
    }

    fun addStandardThumbnail(
        manifestBuilder: ManifestBuilder,
        thumbnailIdentifier: String = "thumbnail.jpg",
        format: String = C2PAFormats.JPEG
    ): ManifestBuilder {
        return manifestBuilder.addThumbnail(
            Thumbnail(
                format = format,
                identifier = thumbnailIdentifier,
                contentType = format
            )
        )
    }

    fun createCreatorVerifiedManifest(
        title: String,
        format: String = C2PAFormats.JPEG,
        authorName: String? = null,
        deviceName: String? = null,
        location: JSONObject? = null,
        creatorIdentities: List<VerifiedIdentity> = emptyList(),
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val builder = ManifestBuilder()
            .title(title)
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addAction(Action(
                action = C2PAActions.CREATED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = deviceName
            ))

        val attestationBuilder = AttestationBuilder()

        if (authorName != null) {
            attestationBuilder.addCreativeWork {
                addAuthor(authorName)
                dateCreated(Date())
            }
        }

        if (creatorIdentities.isNotEmpty()) {
            attestationBuilder.addCAWGIdentity {
                validFromNow()
                creatorIdentities.forEach { identity ->
                    addVerifiedIdentity(
                        type = identity.type,
                        username = identity.username,
                        uri = identity.uri,
                        verifiedAt = identity.verifiedAt,
                        providerId = identity.provider.id,
                        providerName = identity.provider.name
                    )
                }
            }
        }

        if (deviceName != null || location != null) {
            attestationBuilder.addAssertionMetadata {
                dateTime(getCurrentTimestamp())
                deviceName?.let { device(it) }
                location?.let { location(it) }
            }
        }

        attestationBuilder.buildForManifest(builder)
        return builder
    }

    fun createSocialMediaCreatorManifest(
        title: String,
        platform: String,
        username: String,
        verifiedAt: String = getCurrentTimestamp(),
        format: String = C2PAFormats.JPEG,
        claimGeneratorName: String = "Android C2PA SDK",
        claimGeneratorVersion: String = "1.0.0"
    ): ManifestBuilder {
        val builder = ManifestBuilder()
            .title(title)
            .format(format)
            .claimGenerator(claimGeneratorName, claimGeneratorVersion)
            .addAction(Action(
                action = C2PAActions.CREATED,
                whenTimestamp = getCurrentTimestamp(),
                softwareAgent = platform
            ))

        val attestationBuilder = AttestationBuilder()
        attestationBuilder.addCAWGIdentity {
            validFromNow()
            when (platform.lowercase()) {
                "instagram" -> addInstagramIdentity(username, verifiedAt)
                "twitter", "x" -> addTwitterIdentity(username, verifiedAt)
                "behance" -> addBehanceIdentity(username, verifiedAt)
                "github" -> addGitHubIdentity(username, verifiedAt)
                else -> addSocialMediaIdentity(
                    username = username,
                    uri = "https://$platform.com/$username",
                    verifiedAt = verifiedAt,
                    providerId = "https://$platform.com",
                    providerName = platform.lowercase()
                )
            }
        }

        attestationBuilder.buildForManifest(builder)
        return builder
    }
}