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

package org.contentauth.c2pa.signingserver.controllers

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.contentauth.c2pa.signingserver.models.C2PASigningRequest
import org.contentauth.c2pa.signingserver.models.C2PASigningResponse
import java.io.StringReader
import java.security.KeyFactory
import java.security.Security
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class C2PASigningController {

    private val certificateChain: String
    private val privateKeyPEM: String

    init {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        // Load certificates once at initialization
        certificateChain =
            requireNotNull(
                this::class.java.classLoader?.getResourceAsStream(
                    "certs/es256_certs.pem",
                ),
            ) { "Certificate file not found in resources" }
                .bufferedReader()
                .use { it.readText() }

        privateKeyPEM =
            requireNotNull(
                this::class.java.classLoader?.getResourceAsStream(
                    "certs/es256_private.key",
                ),
            ) { "Private key file not found in resources" }
                .bufferedReader()
                .use { it.readText() }
    }

    private fun signWithECDSA(data: ByteArray, privateKeyPEM: String, algorithm: String): ByteArray {
        // Parse the private key from PEM
        val privateKey =
            when {
                privateKeyPEM.contains("BEGIN PRIVATE KEY") -> {
                    // PKCS#8 format
                    val privateKeyContent =
                        privateKeyPEM
                            .replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replace("\n", "")
                            .replace("\r", "")
                    val keyBytes = Base64.getDecoder().decode(privateKeyContent)
                    val keySpec = PKCS8EncodedKeySpec(keyBytes)
                    val keyFactory = KeyFactory.getInstance("EC")
                    keyFactory.generatePrivate(keySpec)
                }
                privateKeyPEM.contains("BEGIN EC PRIVATE KEY") -> {
                    // Traditional EC format - use BouncyCastle to parse
                    val reader = StringReader(privateKeyPEM)
                    val parser = PEMParser(reader)
                    val pemObject = parser.readObject()
                    parser.close()

                    val converter =
                        JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    when (pemObject) {
                        is PEMKeyPair -> converter.getPrivateKey(pemObject.privateKeyInfo)
                        else -> throw IllegalArgumentException("Unsupported PEM format")
                    }
                }
                else -> throw IllegalArgumentException("Unsupported private key format")
            }

        // Sign the data - get DER signature first
        val signature = Signature.getInstance(algorithm)
        signature.initSign(privateKey)
        signature.update(data)
        val derSignature = signature.sign()

        // Convert DER to raw r||s format for COSE
        return derToRaw(derSignature)
    }

    private fun derToRaw(derSignature: ByteArray): ByteArray {
        // DER format: 0x30 [total-length] 0x02 [r-length] [r-bytes] 0x02 [s-length] [s-bytes]
        // Raw format: [r-bytes] [s-bytes] (each zero-padded to coordinate size)

        var offset = 0

        // Check DER sequence tag
        if (derSignature[offset++] != 0x30.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format")
        }

        // Skip total length
        offset++

        // Read r
        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format - expected INTEGER for r")
        }

        val rLength = derSignature[offset++].toInt() and 0xFF
        val r = derSignature.copyOfRange(offset, offset + rLength)
        offset += rLength

        // Read s
        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature format - expected INTEGER for s")
        }

        val sLength = derSignature[offset++].toInt() and 0xFF
        val s = derSignature.copyOfRange(offset, offset + sLength)

        // Remove leading zeros from r and s if present (DER padding)
        val rStripped = r.dropWhile { it == 0.toByte() }.toByteArray()
        val sStripped = s.dropWhile { it == 0.toByte() }.toByteArray()

        // Pad to 32 bytes (ES256 uses P-256, which has 32-byte coordinates)
        val componentLength = 32
        val rPadded = ByteArray(componentLength)
        val sPadded = ByteArray(componentLength)

        System.arraycopy(rStripped, 0, rPadded, componentLength - rStripped.size, rStripped.size)
        System.arraycopy(sStripped, 0, sPadded, componentLength - sStripped.size, sStripped.size)

        // Return r || s
        return rPadded + sPadded
    }

    private fun signWithEd25519(data: ByteArray, privateKeyPEM: String): ByteArray {
        // Parse the private key
        val privateKeyContent =
            privateKeyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
        val keyBytes = Base64.getDecoder().decode(privateKeyContent)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        val privateKey = keyFactory.generatePrivate(keySpec)

        // Sign the data - Ed25519 signature is already in raw format
        val signature = Signature.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    suspend fun signManifest(call: ApplicationCall) {
        try {
            call.application.log.info("[C2PA] Signing manifest request received")
            val signingRequest = call.receive<C2PASigningRequest>()

            // Decode the base64-encoded data to sign
            val dataToSign =
                try {
                    Base64.getDecoder().decode(signingRequest.claim)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid base64-encoded data"),
                    )
                    return
                }

            // Sign based on key type
            val signatureBytes =
                when {
                    privateKeyPEM.contains("ED25519") || privateKeyPEM.contains("Ed25519") -> {
                        signWithEd25519(dataToSign, privateKeyPEM)
                    }
                    else -> {
                        // Default to ES256 (ECDSA with SHA-256 on P-256 curve)
                        signWithECDSA(dataToSign, privateKeyPEM, "SHA256withECDSA")
                    }
                }

            val base64Signature = Base64.getEncoder().encodeToString(signatureBytes)
            call.application.log.info(
                "[C2PA] Manifest signed successfully, signature size: ${signatureBytes.size} bytes",
            )

            val response = C2PASigningResponse(signature = base64Signature)
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.application.log.error("Error signing manifest", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Failed to sign manifest")),
            )
        }
    }
}
