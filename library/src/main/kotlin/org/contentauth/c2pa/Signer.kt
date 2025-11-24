/* 
This file is licensed to you under the Apache License, Version 2.0
(http://www.apache.org/licenses/LICENSE-2.0) or the MIT license
(http://opensource.org/licenses/MIT), at your option.

Unless required by applicable law or agreed to in writing, this software is
distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS OF
ANY KIND, either express or implied. See the LICENSE-MIT and LICENSE-APACHE
files for the specific language governing permissions and limitations under
each license.
*/
package org.contentauth.c2pa

import java.io.Closeable

/** Callback interface for custom signing operations */
interface SignCallback {
    fun sign(data: ByteArray): ByteArray
}

/** C2PA Signer for signing manifests */
class Signer internal constructor(internal var ptr: Long) : Closeable {

    companion object {
        init {
            loadC2PALibraries()
        }

        /** Create signer from certificates and private key */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromKeys(
            certsPEM: String,
            privateKeyPEM: String,
            algorithm: SigningAlgorithm,
            tsaURL: String? = null,
        ): Signer {
            val info = SignerInfo(algorithm, certsPEM, privateKeyPEM, tsaURL)
            return fromInfo(info)
        }

        /** Create signer from SignerInfo */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromInfo(info: SignerInfo): Signer = executeC2PAOperation("Failed to create signer") {
            val handle =
                nativeFromInfo(
                    info.algorithm.description,
                    info.certificatePEM,
                    info.privateKeyPEM,
                    info.tsaURL,
                )
            if (handle == 0L) null else Signer(handle)
        }

        /** Create signer with custom signing callback */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withCallback(
            algorithm: SigningAlgorithm,
            certificateChainPEM: String,
            tsaURL: String? = null,
            sign: (ByteArray) -> ByteArray,
        ): Signer = executeC2PAOperation("Failed to create callback signer") {
            val callback =
                object : SignCallback {
                    override fun sign(data: ByteArray): ByteArray = sign(data)
                }
            val handle =
                nativeFromCallback(
                    algorithm.description,
                    certificateChainPEM,
                    tsaURL,
                    callback,
                )
            if (handle == 0L) null else Signer(handle)
        }

        @JvmStatic
        private external fun nativeFromInfo(
            algorithm: String,
            certificatePEM: String,
            privateKeyPEM: String,
            tsaURL: String?,
        ): Long

        @JvmStatic
        private external fun nativeFromCallback(
            algorithm: String,
            certificateChain: String,
            tsaURL: String?,
            callback: SignCallback,
        ): Long
    }

    /** Get the reserve size for this signer */
    @Throws(C2PAError::class)
    fun reserveSize(): Int {
        val size = reserveSizeNative(ptr)
        if (size < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to get reserve size")
        }
        if (size > Int.MAX_VALUE) {
            throw C2PAError.Api("Reserve size too large: $size")
        }
        return size.toInt()
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0L
        }
    }

    private external fun reserveSizeNative(handle: Long): Long
    private external fun free(handle: Long)
}
