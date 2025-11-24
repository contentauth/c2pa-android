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

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * KeyStoreSigner provides signing capabilities using keys stored in the Android KeyStore, with
 * hardware-backed key storage, biometric authentication, and hardware security features.
 *
 * Example usage:
 * ```kotlin
 * val signer = KeyStoreSigner.createSigner(
 *     algorithm = SigningAlgorithm.ES256,
 *     certificateChainPEM = certificateChain,
 *     keyAlias = "my-signing-key"
 * )
 * ```
 *
 * With biometric authentication:
 * ```kotlin
 * val signer = KeyStoreSigner.createBiometricSigner(
 *     activity = activity,
 *     keyAlias = "my-biometric-key",
 *     algorithm = SigningAlgorithm.ES256,
 *     certificateChainPEM = certificateChain
 * )
 * ```
 */
object KeyStoreSigner {

    /**
     * Creates a Signer that uses a key from the Android KeyStore.
     *
     * @param algorithm The signing algorithm to use
     * @param certificateChainPEM The certificate chain in PEM format
     * @param keyAlias The alias of the key in the KeyStore
     * @param tsaURL Optional timestamp authority URL
     * @return A configured Signer instance
     */
    fun createSigner(
        algorithm: SigningAlgorithm,
        certificateChainPEM: String,
        keyAlias: String,
        tsaURL: String? = null,
    ): Signer {
        val signatureAlgorithm =
            when (algorithm) {
                SigningAlgorithm.ES256 -> "SHA256withECDSA"
                SigningAlgorithm.ES384 -> "SHA384withECDSA"
                SigningAlgorithm.ES512 -> "SHA512withECDSA"
                SigningAlgorithm.PS256 -> "SHA256withRSA/PSS"
                SigningAlgorithm.PS384 -> "SHA384withRSA/PSS"
                SigningAlgorithm.PS512 -> "SHA512withRSA/PSS"
                SigningAlgorithm.ED25519 ->
                    throw C2PAError.Api("Ed25519 not supported by Android KeyStore")
            }

        return Signer.withCallback(
            algorithm = algorithm,
            certificateChainPEM = certificateChainPEM,
            tsaURL = tsaURL,
        ) { data -> signWithKeyStore(data, keyAlias, signatureAlgorithm, algorithm) }
    }

    /**
     * Create a signer with biometric authentication. Extracted from
     * HardwareSecurity.createBiometricSigner
     *
     * @param activity The FragmentActivity for biometric prompt
     * @param keyAlias The alias of the key in the KeyStore
     * @param algorithm The signing algorithm
     * @param certificateChainPEM The certificate chain in PEM format
     * @param tsaURL Optional timestamp authority URL
     * @param promptTitle Title for the biometric prompt
     * @param promptSubtitle Subtitle for the biometric prompt
     * @param promptDescription Description for the biometric prompt
     * @return A configured Signer instance after successful authentication
     */
    suspend fun createBiometricSigner(
        activity: FragmentActivity,
        keyAlias: String,
        algorithm: SigningAlgorithm,
        certificateChainPEM: String,
        tsaURL: String? = null,
        promptTitle: String = "Authenticate to sign",
        promptSubtitle: String = "Use your biometric credential to sign the document",
        promptDescription: String? = null,
    ): Signer = suspendCoroutine { continuation ->
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt =
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        continuation.resumeWithException(
                            C2PAError.Api("Biometric authentication error: $errString"),
                        )
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val signer =
                                createSigner(
                                    algorithm,
                                    certificateChainPEM,
                                    keyAlias,
                                    tsaURL,
                                )
                            continuation.resume(signer)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onAuthenticationFailed() {
                        continuation.resumeWithException(
                            C2PAError.Api("Biometric authentication failed"),
                        )
                    }
                },
            )

        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .apply { promptDescription?.let { setDescription(it) } }
                .setNegativeButtonText("Cancel")
                .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Check if hardware-backed keystore is available. Extracted from
     * HardwareSecurity.isHardwareBackedKeystoreAvailable
     *
     * @return true if hardware-backed KeyStore is available
     */
    fun isHardwareBackedKeystoreAvailable(): Boolean = try {
        KeyStore.getInstance("AndroidKeyStore").load(null)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Check if a key is hardware-backed. Extracted from HardwareSecurity.isKeyHardwareBacked
     *
     * @param keyAlias The alias of the key to check
     * @return true if the key is hardware-backed
     */
    fun isKeyHardwareBacked(keyAlias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return false
            val keyInfo =
                KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                    .getKeySpec(privateKey, KeyInfo::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel >= KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a key exists in the KeyStore.
     *
     * @param keyAlias The alias of the key to check
     * @return true if the key exists
     */
    fun keyExists(keyAlias: String): Boolean = try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.containsAlias(keyAlias)
    } catch (e: Exception) {
        false
    }

    /**
     * Delete a key from the KeyStore.
     *
     * @param keyAlias The alias of the key to delete
     * @return true if the key was deleted or didn't exist
     */
    fun deleteKey(keyAlias: String): Boolean = try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(keyAlias)
        true
    } catch (e: Exception) {
        false
    }

    private fun signWithKeyStore(
        data: ByteArray,
        keyAlias: String,
        signatureAlgorithm: String,
        c2paAlgorithm: SigningAlgorithm,
    ): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val entry =
            keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
                ?: throw C2PAError.Api("Key '$keyAlias' not found in KeyStore")

        val privateKey = entry.privateKey

        val signature = Signature.getInstance(signatureAlgorithm)
        signature.initSign(privateKey)
        signature.update(data)
        val signatureBytes = signature.sign()

        // For ECDSA algorithms, convert from DER to raw format (r || s) for COSE
        return if (c2paAlgorithm in
            listOf(
                SigningAlgorithm.ES256,
                SigningAlgorithm.ES384,
                SigningAlgorithm.ES512,
            )
        ) {
            val componentLength =
                when (c2paAlgorithm) {
                    SigningAlgorithm.ES256 -> 32 // P-256
                    SigningAlgorithm.ES384 -> 48 // P-384
                    SigningAlgorithm.ES512 -> 66 // P-521
                    else -> throw IllegalStateException()
                }
            derToRawSignature(signatureBytes, componentLength)
        } else {
            signatureBytes
        }
    }
}
