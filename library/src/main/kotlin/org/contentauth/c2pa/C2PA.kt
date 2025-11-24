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
     * Load settings from a string
     */
    @Throws(C2PAError::class)
    fun loadSettings(settings: String, format: String) {
        executeC2PAOperation("Failed to load settings") {
            val result = loadSettingsNative(settings, format)
            if (result < 0) null else Unit
        }
    }

    @JvmStatic
    private external fun readFileNative(path: String, dataDir: String? = null): String?

    /**
     * Read a manifest store from a file
     */
    @Throws(C2PAError::class)
    fun readFile(path: String, dataDir: String? = null): String = executeC2PAOperation("Failed to read file") {
        readFileNative(path, dataDir)
    }

    @JvmStatic
    private external fun readIngredientFileNative(path: String, dataDir: String? = null): String?

    /**
     * Read an ingredient from a file
     */
    @Throws(C2PAError::class)
    fun readIngredientFile(path: String, dataDir: String? = null): String =
        executeC2PAOperation("Failed to read ingredient file") {
            readIngredientFileNative(path, dataDir)
        }

    @JvmStatic
    private external fun signFileNative(
        sourcePath: String,
        destPath: String,
        manifest: String,
        algorithm: String,
        certificatePEM: String,
        privateKeyPEM: String,
        tsaURL: String?,
        dataDir: String? = null,
    ): String?

    /**
     * Sign a file with a manifest
     */
    @Throws(C2PAError::class)
    fun signFile(
        sourcePath: String,
        destPath: String,
        manifest: String,
        signerInfo: SignerInfo,
        dataDir: String? = null,
    ): String = executeC2PAOperation("Failed to sign file") {
        signFileNative(
            sourcePath,
            destPath,
            manifest,
            signerInfo.algorithm.description,
            signerInfo.certificatePEM,
            signerInfo.privateKeyPEM,
            signerInfo.tsaURL,
            dataDir,
        )
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
    fun read(from: File, resourcesDir: File? = null): String = readFile(from.absolutePath, resourcesDir?.absolutePath)

    /**
     * Sign a file (convenience method)
     */
    @Throws(C2PAError::class)
    fun sign(source: File, destination: File, manifest: String, signer: SignerInfo, resourcesDir: File? = null) {
        signFile(
            source.absolutePath,
            destination.absolutePath,
            manifest,
            signer,
            resourcesDir?.absolutePath,
        )
    }
}
