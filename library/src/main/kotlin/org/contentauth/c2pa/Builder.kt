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

package org.contentauth.c2pa

import java.io.Closeable
import org.contentauth.c2pa.manifest.ManifestValidator

/**
 * C2PA Builder for creating and signing manifest stores.
 *
 * The Builder class provides an API for constructing C2PA manifests with claims, assertions,
 * ingredients, and resources. It supports multiple signing methods and uses stream-based operations
 * for memory efficiency.
 *
 * ## Usage
 *
 * ### Creating a basic signed manifest
 *
 * ```kotlin
 * val manifestJson = """
 * {
 *   "claim_generator": "MyApp/1.0",
 *   "title": "Signed Photo",
 *   "assertions": [
 *     {
 *       "label": "c2pa.actions",
 *       "data": {
 *         "actions": [{"action": "c2pa.created"}]
 *       }
 *     }
 *   ]
 * }
 * """.trimIndent()
 *
 * val builder = Builder.fromJson(manifestJson)
 *
 * val sourceStream = DataStream(imageBytes)
 * val destStream = ByteArrayStream()
 *
 * builder.sign(
 *     format = "image/jpeg",
 *     source = sourceStream,
 *     dest = destStream,
 *     signer = signer
 * )
 *
 * val signedBytes = destStream.getData()
 * ```
 *
 * ### Adding ingredients (parent images)
 *
 * ```kotlin
 * val builder = Builder.fromJson(manifestJson)
 *
 * val ingredientStream = DataStream(originalImageBytes)
 * builder.addIngredient(
 *     ingredientJSON = """{"title": "Original Photo"}""",
 *     format = "image/jpeg",
 *     source = ingredientStream
 * )
 * ```
 *
 * ### Advanced: Data hash signing
 *
 * ```kotlin
 * // Create placeholder for later signing
 * val placeholder = builder.dataHashedPlaceholder(
 *     reservedSize = 4096,
 *     format = "image/jpeg"
 * )
 *
 * // Later, sign with the hash
 * val signedManifest = builder.signDataHashedEmbeddable(
 *     signer = signer,
 *     dataHash = computeHash(placeholder),
 *     format = "image/jpeg"
 * )
 * ```
 *
 * ## Thread Safety
 *
 * Builder instances are not thread-safe. Each thread should use its own Builder instance.
 *
 * ## Resource Management
 *
 * Builder implements [Closeable] and must be closed when done to free native resources. Use `use {
 * }` or explicitly call `close()`.
 *
 * @property ptr Internal pointer to the native C2PA builder instance
 * @see Reader
 * @see Signer
 * @see Stream
 * @since 1.0.0
 */
class Builder internal constructor(private var ptr: Long) : Closeable {

    /**
     * Result of a signing operation containing the manifest size and optional manifest bytes.
     *
     * @property size The size of the signed manifest in bytes (negative values indicate errors)
     * @property manifestBytes Optional manifest data (null for embedded manifests)
     */
    data class SignResult(val size: Long, val manifestBytes: ByteArray?)

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Default assertion labels that are attributed to the signer (created assertions).
         *
         * The C2PA 2.3 spec distinguishes between "created" assertions (attributed to the
         * signer) and "gathered" assertions (from other workflow components, not attributed
         * to the signer). Assertions whose labels match this list are marked as created;
         * all others are treated as gathered.
         *
         * Note: CAWG identity assertions (`cawg.identity`) cannot be added via the manifest
         * definition. They are dynamic assertions generated at signing time when a CAWG X.509
         * signer is configured in the settings (`cawg_x509_signer` section).
         *
         * To customize, use [fromJson] with a [C2PASettings] that includes your own
         * `builder.created_assertion_labels` setting.
         */
        val DEFAULT_CREATED_ASSERTION_LABELS: List<String> = listOf(
            "c2pa.actions",
            "c2pa.actions.v2",
            "c2pa.thumbnail.claim",
            "c2pa.thumbnail.ingredient",
            "c2pa.ingredient",
            "c2pa.ingredient.v3",
        )

        /**
         * Creates a builder from a manifest definition in JSON format.
         *
         * This method automatically configures the SDK with [DEFAULT_CREATED_ASSERTION_LABELS]
         * to mark common assertions (actions, thumbnails, ingredients) as created assertions.
         * Assertions with labels not in the list are automatically treated as gathered
         * assertions.
         *
         * For full control over settings, use [fromJson(String, C2PASettings)].
         *
         * @param manifestJSON The manifest definition as a JSON string
         * @return A Builder instance configured with the provided manifest
         * @throws C2PAError.Api if the JSON is invalid or doesn't conform to the C2PA manifest
         * schema
         *
         * ```kotlin
         * val manifestJson = """
         * {
         *   "claim_generator": "MyApp/1.0",
         *   "assertions": [
         *     {
         *       "label": "c2pa.actions",
         *       "data": {"actions": [{"action": "c2pa.edited"}]}
         *     }
         *   ]
         * }
         * """
         * val builder = Builder.fromJson(manifestJson)
         * ```
         *
         * @see DEFAULT_CREATED_ASSERTION_LABELS
         * @see fromJson(String, C2PASettings)
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromJson(manifestJSON: String): Builder {
            val validation = ManifestValidator.validateJson(manifestJSON, logWarnings = true)
            if (validation.hasErrors()) {
                throw C2PAError.Api(validation.errors.joinToString("; "))
            }

            val labelsArray = DEFAULT_CREATED_ASSERTION_LABELS.joinToString(", ") { "\"$it\"" }
            val settingsJson = """
                {
                    "version": 1,
                    "builder": {
                        "created_assertion_labels": [$labelsArray]
                    }
                }
            """.trimIndent()

            val settings = C2PASettings.create().apply {
                updateFromString(settingsJson, "json")
            }
            val context = C2PAContext.fromSettings(settings)
            settings.close()

            val builder = fromContext(context).withDefinition(manifestJSON)
            context.close()
            return builder
        }

        /**
         * Creates a builder from a C2PA archive stream.
         *
         * A C2PA archive is a portable format containing a manifest and its associated resources.
         * This method is useful for importing manifests that were previously exported or created by
         * other tools.
         *
         * @param archive The input stream containing the C2PA archive
         * @return A Builder instance loaded from the archive
         * @throws C2PAError.Api if the archive is invalid or corrupted
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromArchive(archive: Stream): Builder = executeC2PAOperation("Failed to create builder from archive") {
            val handle = nativeFromArchive(archive.rawPtr)
            if (handle == 0L) null else Builder(handle)
        }

        /**
         * Creates a builder from a shared [C2PAContext].
         *
         * The context can be reused to create multiple builders and readers.
         * The builder will inherit the context's settings.
         *
         * @param context The context to create the builder from
         * @return A Builder instance configured with the context's settings
         * @throws C2PAError.Api if the builder cannot be created
         *
         * ```kotlin
         * val settings = C2PASettings.create()
         *     .updateFromString(settingsJson, "json")
         * val context = C2PAContext.fromSettings(settings)
         *
         * val builder = Builder.fromContext(context)
         *     .withDefinition(manifestJson)
         * ```
         *
         * @see C2PAContext
         * @see withDefinition
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromContext(context: C2PAContext): Builder = executeC2PAOperation("Failed to create builder from context") {
            val handle = nativeFromContext(context.ptr)
            if (handle == 0L) null else Builder(handle)
        }

        /**
         * Creates a builder from a manifest definition with custom settings.
         *
         * This gives full control over all SDK settings while also providing
         * the manifest definition. The caller retains ownership of [settings]
         * and may close it after this call returns.
         *
         * @param manifestJSON The manifest definition as a JSON string
         * @param settings The settings to configure the builder with
         * @return A Builder instance configured with the provided settings and manifest
         * @throws C2PAError.Api if the JSON is invalid or settings cannot be applied
         *
         * ```kotlin
         * val settings = C2PASettings.create()
         *     .updateFromString(settingsJson, "json")
         * val builder = Builder.fromJson(manifestJson, settings)
         * settings.close()
         * ```
         *
         * @see C2PASettings
         * @see fromJson
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromJson(manifestJSON: String, settings: C2PASettings): Builder {
            val validation = ManifestValidator.validateJson(manifestJSON, logWarnings = true)
            if (validation.hasErrors()) {
                throw C2PAError.Api(validation.errors.joinToString("; "))
            }

            val context = C2PAContext.fromSettings(settings)
            val builder = fromContext(context).withDefinition(manifestJSON)
            context.close()
            return builder
        }

        @JvmStatic private external fun nativeFromArchive(streamHandle: Long): Long

        @JvmStatic private external fun nativeFromContext(contextPtr: Long): Long
    }

    /**
     * Updates the builder with a new manifest definition.
     *
     * @param manifestJSON The manifest definition as a JSON string
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the manifest JSON is invalid
     *
     * ```kotlin
     * val builder = Builder.fromContext(context)
     *     .withDefinition(manifestJson)
     * ```
     */
    @Throws(C2PAError::class)
    fun withDefinition(manifestJSON: String): Builder {
        val newPtr = withDefinitionNative(ptr, manifestJSON)
        if (newPtr == 0L) {
            ptr = 0
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set builder definition")
        }
        ptr = newPtr
        return this
    }

    /**
     * Configures the builder with an archive stream.
     *
     * @param archive The input stream containing the C2PA archive
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the archive is invalid
     *
     * ```kotlin
     * val builder = Builder.fromContext(context)
     *     .withArchive(archiveStream)
     * ```
     */
    @Throws(C2PAError::class)
    fun withArchive(archive: Stream): Builder {
        val newPtr = withArchiveNative(ptr, archive.rawPtr)
        if (newPtr == 0L) {
            ptr = 0
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set builder archive")
        }
        ptr = newPtr
        return this
    }

    /**
     * Sets the builder intent, specifying what kind of manifest to create.
     *
     * The intent determines whether this is a new creation, an edit of existing content, or a
     * metadata-only update. This affects what assertions are automatically added and what
     * ingredients are required.
     *
     * @param intent The [BuilderIntent] specifying the type of manifest
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the intent cannot be set
     *
     * ```kotlin
     * val builder = Builder.fromJson(manifestJson)
     *     .setIntent(BuilderIntent.Create(DigitalSourceType.DIGITAL_CAPTURE))
     *     .addAction(Action(PredefinedAction.CREATED))
     * ```
     *
     * @see BuilderIntent
     * @see DigitalSourceType
     */
    @Throws(C2PAError::class)
    fun setIntent(intent: BuilderIntent): Builder {
        val result = setIntentNative(ptr, intent.toNativeIntent(), intent.toNativeDigitalSourceType())
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set intent")
        }
        return this
    }

    /**
     * Adds an action to the manifest being constructed.
     *
     * Actions describe operations performed on the content, such as editing, cropping, or applying
     * filters. Multiple actions can be added to a single manifest to document the complete editing
     * history.
     *
     * @param action The [Action] to add to the manifest
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the action cannot be added
     *
     * ```kotlin
     * val builder = Builder.fromJson(manifestJson)
     *     .addAction(Action(PredefinedAction.EDITED, DigitalSourceType.DIGITAL_CAPTURE))
     *     .addAction(Action(PredefinedAction.CROPPED, DigitalSourceType.DIGITAL_CAPTURE))
     * ```
     *
     * @see Action
     * @see PredefinedAction
     */
    @Throws(C2PAError::class)
    fun addAction(action: Action): Builder {
        val result = addActionNative(ptr, action.toJson())
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to add action")
        }
        return this
    }

    /**
     * Sets the no-embed flag, preventing the manifest from being embedded in the asset.
     *
     * @return This builder for fluent chaining
     */
    fun setNoEmbed(): Builder {
        setNoEmbedNative(ptr)
        return this
    }

    /**
     * Sets a remote URL where the manifest will be hosted.
     *
     * @param url The remote URL for the manifest
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the remote URL cannot be set
     */
    @Throws(C2PAError::class)
    fun setRemoteURL(url: String): Builder {
        val result = setRemoteUrlNative(ptr, url)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set remote URL")
        }
        return this
    }

    /**
     * Adds a resource to the builder.
     *
     * @param uri The URI identifying the resource
     * @param stream The stream containing the resource data
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the resource cannot be added
     */
    @Throws(C2PAError::class)
    fun addResource(uri: String, stream: Stream): Builder {
        val result = addResourceNative(ptr, uri, stream.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to add resource")
        }
        return this
    }

    /**
     * Adds an ingredient from a stream.
     *
     * @param ingredientJSON JSON describing the ingredient
     * @param format The MIME type of the ingredient (e.g., "image/jpeg")
     * @param source The stream containing the ingredient data
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the ingredient cannot be added
     */
    @Throws(C2PAError::class)
    fun addIngredient(ingredientJSON: String, format: String, source: Stream): Builder {
        val result = addIngredientFromStreamNative(ptr, ingredientJSON, format, source.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to add ingredient")
        }
        return this
    }

    /**
     * Writes the builder state to an archive stream.
     *
     * Archives are portable representations of a manifest and its associated resources
     * that can later be loaded with [fromArchive] or [withArchive].
     *
     * @param dest The output stream to write the archive to
     * @throws C2PAError.Api if the archive cannot be written
     */
    @Throws(C2PAError::class)
    fun toArchive(dest: Stream) {
        val result = toArchiveNative(ptr, dest.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to write archive")
        }
    }

    /**
     * Signs the manifest and writes the signed asset to the destination stream.
     *
     * This is the primary method for producing a signed C2PA asset. The source stream
     * provides the original asset data, and the signed output (with embedded manifest)
     * is written to the destination stream.
     *
     * @param format The MIME type of the asset (e.g., "image/jpeg", "image/png")
     * @param source The input stream containing the original asset
     * @param dest The output stream for the signed asset
     * @param signer The [Signer] to use for signing
     * @return A [SignResult] containing the manifest size and optional manifest bytes
     * @throws C2PAError.Api if signing fails
     */
    @Throws(C2PAError::class)
    fun sign(format: String, source: Stream, dest: Stream, signer: Signer): SignResult {
        val result = signNative(ptr, format, source.rawPtr, dest.rawPtr, signer.ptr)
        if (result.size < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to sign")
        }
        return result
    }

    /**
     * Creates a data-hashed placeholder for deferred signing workflows.
     *
     * This generates a placeholder manifest that can be embedded in an asset before
     * the final signature is applied. Use [signDataHashedEmbeddable] to produce the
     * final signed manifest after computing the asset's data hash.
     *
     * @param reservedSize The number of bytes to reserve for the manifest
     * @param format The MIME type of the asset (e.g., "image/jpeg")
     * @return The placeholder manifest as a byte array
     * @throws C2PAError.Api if the placeholder cannot be created
     *
     * @see signDataHashedEmbeddable
     */
    @Throws(C2PAError::class)
    fun dataHashedPlaceholder(reservedSize: Long, format: String): ByteArray {
        val result = dataHashedPlaceholderNative(ptr, reservedSize, format)
        if (result == null) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to create placeholder")
        }
        return result
    }

    /**
     * Produces a signed manifest using a pre-computed data hash.
     *
     * This completes the deferred signing workflow started with [dataHashedPlaceholder].
     * The caller provides the hash of the asset data, and this method returns the final
     * signed manifest bytes that can be embedded in the asset.
     *
     * @param signer The [Signer] to use for signing
     * @param dataHash The hex-encoded hash of the asset data
     * @param format The MIME type of the asset (e.g., "image/jpeg")
     * @param asset Optional stream containing the asset (used for validation)
     * @return The signed manifest as a byte array
     * @throws C2PAError.Api if signing fails
     *
     * @see dataHashedPlaceholder
     */
    @Throws(C2PAError::class)
    fun signDataHashedEmbeddable(signer: Signer, dataHash: String, format: String, asset: Stream? = null): ByteArray {
        val result =
            signDataHashedEmbeddableNative(
                ptr,
                signer.ptr,
                dataHash,
                format,
                asset?.rawPtr ?: 0L,
            )
        if (result == null) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to sign with data hash")
        }
        return result
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
    private external fun withDefinitionNative(handle: Long, manifestJson: String): Long
    private external fun withArchiveNative(handle: Long, streamHandle: Long): Long
    private external fun setIntentNative(handle: Long, intent: Int, digitalSourceType: Int): Int
    private external fun addActionNative(handle: Long, actionJson: String): Int
    private external fun setNoEmbedNative(handle: Long)
    private external fun setRemoteUrlNative(handle: Long, remoteUrl: String): Int
    private external fun addResourceNative(handle: Long, uri: String, streamHandle: Long): Int
    private external fun addIngredientFromStreamNative(
        handle: Long,
        ingredientJson: String,
        format: String,
        sourceHandle: Long,
    ): Int
    private external fun toArchiveNative(handle: Long, streamHandle: Long): Int
    private external fun signNative(
        handle: Long,
        format: String,
        sourceHandle: Long,
        destHandle: Long,
        signerHandle: Long,
    ): SignResult
    private external fun dataHashedPlaceholderNative(handle: Long, reservedSize: Long, format: String): ByteArray?
    private external fun signDataHashedEmbeddableNative(
        handle: Long,
        signerHandle: Long,
        dataHash: String,
        format: String,
        assetHandle: Long,
    ): ByteArray?
}
