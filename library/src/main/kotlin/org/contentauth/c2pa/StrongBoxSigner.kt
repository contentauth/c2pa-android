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

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * StrongBoxSigner provides signing capabilities using Android's StrongBox Keymaster, which provides
 * hardware-isolated key storage and cryptographic operations.
 *
 * Requirements:
 * - Android 9.0 (API 28) or higher
 * - Device with StrongBox support
 *
 * Example usage:
 * ```kotlin
 * val config = StrongBoxSigner.Config(
 *     keyTag = "my-strongbox-key",
 *     requireUserAuthentication = false
 * )
 *
 * if (!StrongBoxSigner.keyExists(config.keyTag)) {
 *     StrongBoxSigner.createKey(config)
 * }
 *
 * val signer = StrongBoxSigner.createSigner(
 *     algorithm = SigningAlgorithm.ES256,
 *     certificateChainPEM = certificateChain,
 *     config = config
 * )
 * ```
 */
object StrongBoxSigner {

    /** Configuration for StrongBox key creation and usage. */
    data class Config(
        val keyTag: String,
        val accessControl: Int = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        val requireUserAuthentication: Boolean = false,
    )

    /**
     * Creates a Signer that uses a key from StrongBox. Only ES256 is supported as it's the most
     * widely supported algorithm in StrongBox.
     *
     * Extracted from HardwareSecurity.withStrongBox
     *
     * @param algorithm The signing algorithm (only ES256 supported)
     * @param certificateChainPEM The certificate chain in PEM format
     * @param config Configuration for the StrongBox key
     * @param tsaURL Optional timestamp authority URL
     * @return A configured Signer instance
     */
    fun createSigner(
        algorithm: SigningAlgorithm,
        certificateChainPEM: String,
        config: Config,
        tsaURL: String? = null,
    ): Signer {
        require(algorithm == SigningAlgorithm.ES256) { "StrongBox only supports ES256 (P-256)" }

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        // Get or create StrongBox key
        val privateKey = keyStore.getKey(config.keyTag, null) as? PrivateKey ?: createKey(config)

        return Signer.withCallback(algorithm, certificateChainPEM, tsaURL) { data ->
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(data)
                val derSignature = sign()

                derToRawSignature(derSignature, 32)
            }
        }
    }

    /**
     * Create a new key in StrongBox. Extracted and refactored from
     * HardwareSecurity.createStrongBoxKey
     *
     * @param config Configuration for the key
     * @return The created private key
     */
    fun createKey(config: Config): PrivateKey =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(
                KeyGenParameterSpec.Builder(config.keyTag, config.accessControl)
                    .apply {
                        setDigests(KeyProperties.DIGEST_SHA256)
                        setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        setUserAuthenticationRequired(config.requireUserAuthentication)
                        setIsStrongBoxBacked(true)
                        setCertificateSubject(X500Principal("CN=StrongBox Key"))
                        setCertificateSerialNumber(java.math.BigInteger.valueOf(1))
                        setCertificateNotBefore(Date())
                        setCertificateNotAfter(
                            Date(
                                System.currentTimeMillis() +
                                    365L * 24 * 60 * 60 * 1000,
                            ),
                        )
                    }
                    .build(),
            )
            generateKeyPair().private
        }

    /**
     * Deletes a key from StrongBox. Extracted from HardwareSecurity.deleteStrongBoxKey
     *
     * @param keyTag The alias of the key to delete
     * @return true if the key was deleted or didn't exist
     */
    fun deleteKey(keyTag: String): Boolean = try {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(keyTag)
        }
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Checks if a key exists in StrongBox.
     *
     * @param keyTag The alias of the key to check
     * @return true if the key exists
     */
    fun keyExists(keyTag: String): Boolean = try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.containsAlias(keyTag)
    } catch (e: Exception) {
        false
    }

    /**
     * Check if StrongBox is available on this device.
     *
     * @param context Android context
     * @return true if StrongBox is available
     */
    fun isAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")

    /**
     * Check if a key is StrongBox-backed.
     *
     * @param keyTag The alias of the key to check
     * @return true if the key is StrongBox-backed
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun isKeyStrongBoxBacked(keyTag: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = keyStore.getKey(keyTag, null) as? PrivateKey ?: return false
            val keyInfo =
                KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                    .getKeySpec(privateKey, KeyInfo::class.java)
            keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } catch (e: Exception) {
            false
        }
    }
}
