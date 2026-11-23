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

/**
 * C2PA Signer for signing manifests.
 *
 * Signer implements [Closeable]; calling a method after `close()` throws [IllegalStateException].
 */
class Signer internal constructor(internal var ptr: Long) : Closeable {

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Per-array slot limit for FFI string array parameters. Mirrors
         * `MAX_STRING_ARRAY_LEN` in `c2pa.h`. The FFI requires the NULL
         * terminator to fit within this limit, so each array may carry at most
         * `MAX_STRING_ARRAY_LEN - 1` entries.
         */
        private const val MAX_STRING_ARRAY_LEN = 256

        /**
         * Creates a signer from PEM-encoded certificates and a private key.
         *
         * This is the simplest way to create a signer when you have the certificate chain
         * and private key available as PEM strings.
         *
         * @param certsPEM The certificate chain in PEM format
         * @param privateKeyPEM The private key in PEM format
         * @param algorithm The [SigningAlgorithm] to use (e.g., ES256, ES384)
         * @param tsaURL Optional timestamp authority URL for trusted timestamping
         * @return A configured [Signer] instance
         * @throws C2PAError.Api if the certificates or key are invalid
         */
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

        /**
         * Creates a signer from a [SignerInfo] configuration object.
         *
         * @param info The [SignerInfo] containing algorithm, certificates, key, and TSA URL
         * @return A configured [Signer] instance
         * @throws C2PAError.Api if the signer cannot be created from the provided info
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromInfo(info: SignerInfo): Signer = executeC2PAOperation("Failed to create signer") {
            val handle =
                nativeFromInfo(
                    info.algorithm.description.toNativeUtf8(),
                    info.certificatePEM.toNativeUtf8(),
                    info.privateKeyPEM.toNativeUtf8(),
                    info.tsaURL?.toNativeUtf8(),
                )
            if (handle == 0L) null else Signer(handle)
        }

        /**
         * Creates a signer with a custom signing callback.
         *
         * Use this when the signing operation is handled externally, such as with
         * hardware security modules (StrongBox, Android Keystore) or remote signing
         * services. The callback receives raw bytes to sign and must return the signature.
         *
         * @param algorithm The [SigningAlgorithm] to use
         * @param certificateChainPEM The certificate chain in PEM format
         * @param tsaURL Optional timestamp authority URL for trusted timestamping
         * @param sign Callback that receives data bytes and returns the signature bytes
         * @return A configured [Signer] instance
         * @throws C2PAError.Api if the callback signer cannot be created
         *
         * @see StrongBoxSigner
         * @see KeyStoreSigner
         */
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
                    algorithm.description.toNativeUtf8(),
                    certificateChainPEM.toNativeUtf8(),
                    tsaURL?.toNativeUtf8(),
                    callback,
                )
            if (handle == 0L) null else Signer(handle)
        }

        /**
         * Combine a C2PA claim signer with an X.509 identity signer to produce a
         * combined signer that emits a `cawg.identity` assertion alongside the C2PA
         * claim signature.
         *
         * Both input signers may be created via any existing factory: [fromKeys],
         * [fromInfo], [withCallback], or hardware-backed factories such as
         * [StrongBoxSigner] / [KeyStoreSigner] / [WebServiceSigner]. The combined
         * signer is then passed to [Builder.sign] like any other signer.
         *
         * Ownership: once the underlying FFI call is reached, both input signers
         * are *consumed* — ownership transfers to the returned signer and neither
         * input may be used again. The wrapper zeros each input's internal pointer
         * on consumption, so calling `close()` on the inputs (or wrapping them in
         * a `use { }` block) is a safe no-op. Only the returned [Signer] must be
         * closed by the caller. If this method throws *before* the FFI call (e.g.
         * an input was already closed, or an argument failed validation), the
         * inputs are NOT consumed and the caller remains responsible for closing
         * them.
         *
         * Thread safety: this method reads each input's internal pointer without
         * synchronization. Do not call it concurrently with [close] on the same
         * signer, or with another operation that may free the underlying native
         * pointer.
         *
         * @param c2pa The signer that produces the C2PA claim signature. Must
         *   not be the same instance as [identity] (each input is consumed
         *   separately).
         * @param identity The signer that produces the CAWG identity assertion.
         * @param referencedAssertions Manifest assertion labels covered by the
         *   identity assertion (e.g. `"c2pa.actions"`). The list may have up to
         *   255 entries.
         * @param roles Optional role strings to attach to the identity assertion.
         *   The list may have up to 255 entries.
         * @return A combined [Signer] suitable for passing to [Builder.sign].
         * @throws C2PAError.Api if the two inputs are the same instance, if
         *   either input signer is already closed, if either list exceeds 255
         *   entries, or if the underlying FFI call fails.
         * @see Builder.sign
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun withCawgIdentity(
            c2pa: Signer,
            identity: Signer,
            referencedAssertions: List<String>,
            roles: List<String> = emptyList(),
        ): Signer {
            if (c2pa === identity) {
                throw C2PAError.Api("c2pa and identity signers must be distinct instances")
            }
            if (c2pa.ptr == 0L) throw C2PAError.Api("c2pa signer is already closed")
            if (identity.ptr == 0L) throw C2PAError.Api("identity signer is already closed")
            if (referencedAssertions.size >= MAX_STRING_ARRAY_LEN) {
                throw C2PAError.Api(
                    "referencedAssertions cannot exceed ${MAX_STRING_ARRAY_LEN - 1} entries",
                )
            }
            if (roles.size >= MAX_STRING_ARRAY_LEN) {
                throw C2PAError.Api("roles cannot exceed ${MAX_STRING_ARRAY_LEN - 1} entries")
            }

            return executeC2PAOperation("Failed to combine signers for CAWG identity") {
                val handle =
                    nativeCombineCawg(
                        c2pa.ptr,
                        identity.ptr,
                        referencedAssertions.map { it.toNativeUtf8() }.toTypedArray(),
                        roles.map { it.toNativeUtf8() }.toTypedArray(),
                    )
                c2pa.ptr = 0L
                identity.ptr = 0L
                if (handle == 0L) null else Signer(handle)
            }
        }

        @JvmStatic
        private external fun nativeFromInfo(
            algorithm: ByteArray,
            certificatePEM: ByteArray,
            privateKeyPEM: ByteArray,
            tsaURL: ByteArray?,
        ): Long

        @JvmStatic
        private external fun nativeFromCallback(
            algorithm: ByteArray,
            certificateChain: ByteArray,
            tsaURL: ByteArray?,
            callback: SignCallback,
        ): Long

        @JvmStatic
        private external fun nativeCombineCawg(
            c2paHandle: Long,
            identityHandle: Long,
            referencedAssertions: Array<ByteArray>,
            roles: Array<ByteArray>,
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

    /**
     * Staples an OCSP response into every signature this signer produces.
     *
     * A stapled response lets a validator check the revocation status of the signing
     * certificate without a network request. The response is DER encoded, as returned by an
     * OCSP responder.
     *
     * Calls stack. Call once per certificate along the chain, signing certificate first,
     * because validators check the first response against the signing certificate. Each
     * response adds its length to [reserveSize].
     *
     * The caller fetches the responses and keeps them fresh. OCSP responses expire, so create
     * a new signer when they are refreshed.
     *
     * ```kotlin
     * Signer.fromInfo(signerInfo).use { signer ->
     *     signer.withOcspResponse(leafResponse).withOcspResponse(intermediateResponse)
     *     builder.sign("image/jpeg", source, dest, signer)
     * }
     * ```
     *
     * Thread safety: a successful call replaces this signer's native handle and frees the
     * previous one. Do not call it while another thread is signing with, or closing, the same
     * signer. Staple the responses before the signer is shared.
     *
     * @param response A DER-encoded OCSP response. Must not be empty.
     * @return This signer for fluent chaining
     * @throws C2PAError.Api if the response cannot be stapled. The signer stays usable.
     */
    @Throws(C2PAError::class)
    fun withOcspResponse(response: ByteArray): Signer {
        val handle = withOcspResponseNative(ptr, response)
        if (handle == 0L) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to staple OCSP response")
        }
        ptr = handle
        return this
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0L
        }
    }

    private external fun reserveSizeNative(handle: Long): Long
    private external fun withOcspResponseNative(handle: Long, response: ByteArray): Long
    private external fun free(handle: Long)
}
