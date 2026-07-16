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

import java.io.File

/**
 * Main C2PA object for static operations
 *
 * The native libraries are automatically loaded when this object is first accessed.
 * No manual initialization is required.
 */
object C2PA {
    init {
        loadC2PALibraries()
    }

    /**
     * Returns the version string of the C2PA library
     */
    @JvmStatic
    external fun version(): String

    /**
     * Returns the last error message, if any
     */
    @JvmStatic
    external fun getError(): String?

    @JvmStatic
    private external fun loadSettingsNative(settings: String, format: String): Int

    /**
     * Load settings from a string.
     * Returns the result code from the native call (0 for success).
     */
    @JvmStatic
    fun loadSettingsResult(settings: String, format: String): Int = loadSettingsNative(settings, format)

    /**
     * Load settings from a string
     */
    @Throws(C2PAError::class)
    fun loadSettings(settings: String, format: String) {
        executeC2PAOperation("Failed to load settings") {
            val result = loadSettingsNative(settings, format)
            if (result < 0) null else Unit
        }
    }

    /**
     * Read a C2PA manifest store from a file.
     *
     * Reads via the stream-based [Reader] API; the asset format is inferred from the file
     * extension. To extract embedded resources, use a [Reader] with [Reader.resource].
     *
     * @param path Filesystem path to the asset to read
     * @return The manifest store as a JSON string
     * @throws C2PAError if the file is missing or contains no valid manifest
     */
    @Throws(C2PAError::class)
    fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists()) {
            throw C2PAError.Api("File not found: $path")
        }
        return try {
            FileStream(file, FileStream.Mode.READ, createIfNeeded = false).use { stream ->
                Reader.fromStream(formatFromPath(path), stream).use { reader ->
                    reader.json()
                }
            }
        } catch (e: C2PAError) {
            throw e
        } catch (e: Exception) {
            throw C2PAError.Api(e.message ?: "Failed to read file")
        }
    }

    /**
     * Sign a file with a manifest, writing the signed asset to [destPath].
     *
     * Signs via the stream-based [Builder] API; the asset format is inferred from the
     * source file extension.
     *
     * @param sourcePath Filesystem path to the source asset
     * @param destPath Filesystem path for the signed output asset
     * @param manifest The manifest definition as a JSON string
     * @param signerInfo The signer configuration
     * @return The signed manifest store as a JSON string (read back from the destination)
     * @throws C2PAError if signing fails
     */
    @Throws(C2PAError::class)
    fun signFile(
        sourcePath: String,
        destPath: String,
        manifest: String,
        signerInfo: SignerInfo,
    ): String {
        val source = File(sourcePath)
        if (!source.exists()) {
            throw C2PAError.Api("Source file not found: $sourcePath")
        }
        val format = formatFromPath(sourcePath)
        try {
            Builder.fromJson(manifest).use { builder ->
                Signer.fromInfo(signerInfo).use { signer ->
                    FileStream(source, FileStream.Mode.READ, createIfNeeded = false).use { src ->
                        FileStream(File(destPath), FileStream.Mode.WRITE).use { dst ->
                            builder.sign(format, src, dst, signer)
                        }
                    }
                }
            }
        } catch (e: C2PAError) {
            throw e
        } catch (e: Exception) {
            throw C2PAError.Api(e.message ?: "Failed to sign file")
        }
        return readFile(destPath)
    }

    /** Infers a C2PA format (file extension) from a path; c2pa-rs accepts an extension as a format. */
    private fun formatFromPath(path: String): String {
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "")
        return ext.lowercase().ifEmpty { name.lowercase() }
    }

    @JvmStatic
    private external fun ed25519SignNative(data: ByteArray, privateKey: String): ByteArray?

    /**
     * Sign data using Ed25519
     */
    @Throws(C2PAError::class)
    fun ed25519Sign(data: ByteArray, privateKey: String): ByteArray =
        executeC2PAOperation("Failed to sign with Ed25519") {
            ed25519SignNative(data, privateKey)
        }

    /**
     * Read a manifest from a file (convenience method)
     */
    @Throws(C2PAError::class)
    fun read(from: File): String = readFile(from.absolutePath)

    /**
     * Sign a file (convenience method)
     */
    @Throws(C2PAError::class)
    fun sign(source: File, destination: File, manifest: String, signer: SignerInfo) {
        signFile(
            source.absolutePath,
            destination.absolutePath,
            manifest,
            signer,
        )
    }
}
