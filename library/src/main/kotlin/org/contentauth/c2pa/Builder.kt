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
         * Creates a builder from a manifest definition in JSON format.
         *
         * The JSON should contain the manifest structure including claims, assertions, and metadata
         * according to the C2PA specification. This is useful for programmatically constructing
         * manifests or loading manifest templates.
         *
         * @param manifestJSON The manifest definition as a JSON string
         * @return A Builder instance configured with the provided manifest
         * @throws C2PAError.Api if the JSON is invalid or doesn't conform to the C2PA manifest
         * schema
         *
         * @sample
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
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromJson(manifestJSON: String): Builder = executeC2PAOperation("Failed to create builder from JSON") {
            val handle = nativeFromJson(manifestJSON)
            if (handle == 0L) null else Builder(handle)
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

        @JvmStatic private external fun nativeFromJson(manifestJson: String): Long

        @JvmStatic private external fun nativeFromArchive(streamHandle: Long): Long
    }

    /** Set the no-embed flag */
    fun setNoEmbed() = setNoEmbedNative(ptr)

    /** Set the remote URL */
    @Throws(C2PAError::class)
    fun setRemoteURL(url: String) {
        val result = setRemoteUrlNative(ptr, url)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set remote URL")
        }
    }

    /** Add a resource to the builder */
    @Throws(C2PAError::class)
    fun addResource(uri: String, stream: Stream) {
        val result = addResourceNative(ptr, uri, stream.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to add resource")
        }
    }

    /** Add an ingredient from a stream */
    @Throws(C2PAError::class)
    fun addIngredient(ingredientJSON: String, format: String, source: Stream) {
        val result = addIngredientFromStreamNative(ptr, ingredientJSON, format, source.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to add ingredient")
        }
    }

    /** Write the builder to an archive */
    @Throws(C2PAError::class)
    fun toArchive(dest: Stream) {
        val result = toArchiveNative(ptr, dest.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to write archive")
        }
    }

    /** Sign and write the manifest */
    @Throws(C2PAError::class)
    fun sign(format: String, source: Stream, dest: Stream, signer: Signer): SignResult {
        val result = signNative(ptr, format, source.rawPtr, dest.rawPtr, signer.ptr)
        if (result.size < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to sign")
        }
        return result
    }

    /** Create a hashed placeholder for later signing */
    @Throws(C2PAError::class)
    fun dataHashedPlaceholder(reservedSize: Long, format: String): ByteArray {
        val result = dataHashedPlaceholderNative(ptr, reservedSize, format)
        if (result == null) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to create placeholder")
        }
        return result
    }

    /** Sign using data hash (advanced use) */
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
