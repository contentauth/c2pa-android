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
 * C2PA Reader for reading and validating manifest stores from media files.
 *
 * The Reader class provides functionality to extract C2PA manifests from media files and access
 * embedded resources. It uses stream-based operations for memory efficiency.
 *
 * ## Usage
 *
 * ### Reading a manifest from a file
 *
 * ```kotlin
 * val stream = DataStream(imageBytes)
 * val reader = Reader.fromStream("image/jpeg", stream)
 * val manifestJson = reader.json()
 * ```
 *
 * ### Parsing manifest data
 *
 * ```kotlin
 * val manifestJson = reader.json()
 * val manifest = JSONObject(manifestJson)
 *
 * manifest.optJSONObject("active_manifest")?.let { activeManifest ->
 *     val title = activeManifest.optString("title")
 *     val claimGenerator = activeManifest.optString("claim_generator")
 * }
 * ```
 *
 * ### Extracting embedded resources
 *
 * ```kotlin
 * val thumbnailUri = "self#jumbf=/c2pa/urn:uuid:12345/c2pa.thumbnail.claim.jpeg"
 * val outputStream = ByteArrayStream()
 *
 * reader.resource(thumbnailUri, outputStream)
 * val thumbnailBytes = outputStream.getData()
 * ```
 *
 * ## Thread Safety
 *
 * Reader instances are not thread-safe. Each thread should use its own Reader instance.
 *
 * ## Resource Management
 *
 * Reader implements [Closeable] and must be closed when done to free native resources. Use `use {
 * }` or explicitly call `close()`.
 *
 * @property ptr Internal pointer to the native C2PA reader instance
 * @see Builder
 * @see Stream
 * @since 1.0.0
 */
class Reader internal constructor(private var ptr: Long) : Closeable {

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Creates a reader from a stream containing media with an embedded C2PA manifest.
         *
         * This is the primary method for reading C2PA manifests from media files. The stream should
         * contain the complete media file (e.g., JPEG, PNG, MP4) with an embedded manifest.
         *
         * @param format The MIME type of the media (e.g., "image/jpeg", "image/png", "video/mp4")
         * @param stream The input stream containing the media file
         * @return A Reader instance for accessing the manifest
         * @throws C2PAError.Api if the stream doesn't contain a valid C2PA manifest or the format
         * is unsupported
         *
         * @sample
         * ```kotlin
         * val inputStream = FileInputStream("signed_photo.jpg")
         * val stream = Stream.fromInputStream(inputStream)
         * val reader = Reader.fromStream("image/jpeg", stream).use { reader ->
         *     reader.json()
         * }
         * ```
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromStream(format: String, stream: Stream): Reader =
            executeC2PAOperation("Failed to create reader from stream") {
                val handle = fromStreamNative(format, stream.rawPtr)
                if (handle == 0L) null else Reader(handle)
            }

        /**
         * Creates a reader from manifest data and an associated media stream.
         *
         * This method is used when the manifest is stored separately from the media file, such as
         * with sidecar manifests or remote manifests. The manifest data should be in C2PA binary
         * format.
         *
         * @param format The MIME type of the media (e.g., "image/jpeg", "image/png")
         * @param stream The input stream containing the media file
         * @param manifest The manifest data as a byte array
         * @return A Reader instance for accessing the manifest
         * @throws C2PAError.Api if the manifest data is invalid or incompatible with the media
         *
         * @sample
         * ```kotlin
         * val mediaStream = Stream.fromInputStream(FileInputStream("photo.jpg"))
         * val manifestBytes = File("photo.c2pa").readBytes()
         * val reader = Reader.fromManifestAndStream("image/jpeg", mediaStream, manifestBytes)
         * ```
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromManifestAndStream(format: String, stream: Stream, manifest: ByteArray): Reader =
            executeC2PAOperation("Failed to create reader from manifest and stream") {
                val handle = fromManifestDataAndStreamNative(format, stream.rawPtr, manifest)
                if (handle == 0L) null else Reader(handle)
            }

        @JvmStatic private external fun fromStreamNative(format: String, streamHandle: Long): Long

        @JvmStatic
        private external fun fromManifestDataAndStreamNative(
            format: String,
            streamHandle: Long,
            manifestData: ByteArray,
        ): Long
    }

    /**
     * Converts the C2PA manifest to a JSON string representation.
     *
     * The returned JSON contains the complete manifest store including all claims, assertions,
     * signatures, and validation results. The structure follows the C2PA specification's JSON
     * format.
     *
     * @return The manifest as a JSON string
     * @throws C2PAError.Api if the manifest cannot be serialized to JSON
     *
     * @sample
     * ```kotlin
     * val reader = Reader.fromStream("image/jpeg", stream)
     * val json = reader.json()
     * val manifest = JSONObject(json)
     * val author = manifest.getJSONObject("active_manifest")
     *     .getJSONArray("assertions")
     *     .getJSONObject(0)
     *     .getString("author")
     * ```
     */
    @Throws(C2PAError::class)
    fun json(): String {
        val json = toJsonNative(ptr)
        if (json == null) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to convert to JSON")
        }
        return json
    }

    /**
     * Extracts an embedded resource from the manifest and writes it to a stream.
     *
     * C2PA manifests can contain embedded resources such as thumbnails, ingredient images, or other
     * assets. This method allows you to extract these resources by their URI.
     *
     * Resource URIs typically follow the pattern:
     * `self#jumbf=/c2pa/urn:uuid:<manifest-id>/<resource-name>`
     *
     * @param uri The URI of the resource to extract (found in the manifest JSON)
     * @param to The output stream to write the resource data to
     * @throws C2PAError.Api if the resource URI is not found or cannot be extracted
     *
     * @sample
     * ```kotlin
     * val reader = Reader.fromStream("image/jpeg", stream)
     * val manifestJson = reader.json()
     *
     * // Parse JSON to find thumbnail URI
     * val thumbnailUri = "self#jumbf=/c2pa/urn:uuid:12345/c2pa.thumbnail.claim.jpeg"
     *
     * val outputStream = FileOutputStream("thumbnail.jpg")
     * val outputStreamWrapper = Stream.fromOutputStream(outputStream)
     * reader.resource(thumbnailUri, outputStreamWrapper)
     * ```
     */
    @Throws(C2PAError::class)
    fun resource(uri: String, to: Stream) {
        val result = resourceToStreamNative(ptr, uri, to.rawPtr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to write resource")
        }
    }

    /**
     * Closes the reader and releases native resources.
     *
     * This method must be called when the reader is no longer needed to prevent native memory
     * leaks. It's safe to call this method multiple times.
     */
    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
    private external fun toJsonNative(handle: Long): String?
    private external fun resourceToStreamNative(handle: Long, uri: String, streamHandle: Long): Long
}
