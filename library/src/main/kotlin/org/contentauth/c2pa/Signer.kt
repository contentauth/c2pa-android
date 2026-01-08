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

        /**
         * Creates a signer from JSON settings configuration.
         *
         * This method creates a signer from a JSON settings object that can include certificate
         * paths, private keys, algorithm selection, and other configuration options. This is useful
         * for loading signer configuration from external sources, configuration files, or for CAWG
         * (Creator Assertions Working Group) signers.
         *
         * @param settingsJson A JSON string containing signer configuration.
         * @return A new [Signer] instance configured according to the settings.
         * @throws C2PAError if the settings are invalid or the signer cannot be created.
         *
         * Example JSON for a CAWG signer:
         * ```json
         * {
         *     "version": 1,
         *     "signer": {
         *         "local": {
         *             "alg": "es256",
         *             "sign_cert": "-----BEGIN CERTIFICATE-----\n...",
         *             "private_key": "-----BEGIN PRIVATE KEY-----\n...",
         *             "tsa_url": "http://timestamp.digicert.com"
         *         }
         *     },
         *     "cawg_x509_signer": {
         *         "local": {
         *             "alg": "es256",
         *             "sign_cert": "-----BEGIN CERTIFICATE-----\n...",
         *             "private_key": "-----BEGIN PRIVATE KEY-----\n...",
         *             "tsa_url": "http://timestamp.digicert.com",
         *             "referenced_assertions": ["cawg.training-mining"]
         *         }
         *     }
         * }
         * ```
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromSettingsJson(settingsJson: String): Signer =
            fromSettings(settingsJson, "json")

        /**
         * Creates a signer from TOML settings configuration.
         *
         * This method creates a signer from a TOML settings string. TOML format supports additional
         * features like CAWG (Creator Assertions Working Group) X.509 signers that generate identity
         * assertions.
         *
         * @param settingsToml A TOML string containing signer configuration.
         * @return A new [Signer] instance configured according to the settings.
         * @throws C2PAError if the settings are invalid or the signer cannot be created.
         *
         * Example TOML for a CAWG signer:
         * ```toml
         * version = 1
         *
         * [signer.local]
         * alg = "es256"
         * sign_cert = """-----BEGIN CERTIFICATE-----
         * ...certificate chain...
         * -----END CERTIFICATE-----
         * """
         * private_key = """-----BEGIN PRIVATE KEY-----
         * ...private key...
         * -----END PRIVATE KEY-----
         * """
         * tsa_url = "http://timestamp.digicert.com"
         *
         * [cawg_x509_signer.local]
         * alg = "es256"
         * sign_cert = """-----BEGIN CERTIFICATE-----
         * ...certificate chain...
         * -----END CERTIFICATE-----
         * """
         * private_key = """-----BEGIN PRIVATE KEY-----
         * ...private key...
         * -----END PRIVATE KEY-----
         * """
         * tsa_url = "http://timestamp.digicert.com"
         * referenced_assertions = ["cawg.training-mining"]
         * ```
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromSettingsToml(settingsToml: String): Signer =
            fromSettings(settingsToml, "toml")

        /**
         * Creates a signer from settings configuration in the specified format.
         *
         * @param settings The settings string in the specified format.
         * @param format The format of the settings string ("json" or "toml").
         * @return A new [Signer] instance configured according to the settings.
         * @throws C2PAError if the settings are invalid or the signer cannot be created.
         */
        @JvmStatic
        @Throws(C2PAError::class)
        private fun fromSettings(settings: String, format: String): Signer =
            executeC2PAOperation("Failed to create signer from settings") {
                val loadResult = C2PA.loadSettingsResult(settings, format)
                if (loadResult != 0) {
                    throw C2PAError.Api(C2PA.getError() ?: "Failed to load settings")
                }
                val handle = nativeFromSettings()
                if (handle == 0L) null else Signer(handle)
            }

        /**
         * Loads global C2PA settings without creating a signer.
         *
         * This method loads settings that will be used by subsequent signing operations. Use this
         * to load CAWG identity assertion settings separately from the main signer.
         *
         * @param settings The settings string in the specified format.
         * @param format The format of the settings string ("json" or "toml").
         * @throws C2PAError if the settings are invalid.
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun loadSettings(settings: String, format: String) {
            val result = C2PA.loadSettingsResult(settings, format)
            if (result != 0) {
                throw C2PAError.Api(C2PA.getError() ?: "Failed to load settings")
            }
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

        @JvmStatic
        private external fun nativeFromSettings(): Long
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
