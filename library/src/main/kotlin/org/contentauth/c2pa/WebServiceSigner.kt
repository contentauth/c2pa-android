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

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * WebServiceSigner provides remote signing capabilities through a C2PA signing server.
 *
 * Example usage:
 * ```kotlin
 * val webServiceSigner = WebServiceSigner(
 *     configurationURL = "http://10.0.2.2:8080/api/v1/c2pa/configuration",
 *     bearerToken = "your-token-here"
 * )
 *
 * val signer = webServiceSigner.createSigner()
 * ```
 */
class WebServiceSigner(
    private val configurationURL: String,
    private val bearerToken: String? = null,
    private val customHeaders: Map<String, String> = emptyMap(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private var signingURL: String? = null

    /**
     * Creates a Signer instance configured for remote signing. This method fetches the
     * configuration from the server and sets up the signing callback.
     */
    suspend fun createSigner(): Signer {
        val configuration = fetchConfiguration()
        val signingAlgorithm = mapAlgorithm(configuration.algorithm)
        signingURL = configuration.signing_url
        val certificateChain = parseCertificateChain(configuration.certificate_chain)

        return Signer.withCallback(
            algorithm = signingAlgorithm,
            certificateChainPEM = certificateChain,
            tsaURL = configuration.timestamp_url.takeIf { it.isNotEmpty() },
        ) { data -> signData(data, configuration.signing_url) }
    }

    private fun mapAlgorithm(algorithmString: String): SigningAlgorithm = when (algorithmString.lowercase()) {
        "es256" -> SigningAlgorithm.ES256
        "es384" -> SigningAlgorithm.ES384
        "es512" -> SigningAlgorithm.ES512
        "ps256" -> SigningAlgorithm.PS256
        "ps384" -> SigningAlgorithm.PS384
        "ps512" -> SigningAlgorithm.PS512
        "ed25519" -> SigningAlgorithm.ED25519
        else -> throw SignerException.UnsupportedAlgorithm(algorithmString)
    }

    private suspend fun fetchConfiguration(): SignerConfiguration {
        val requestBuilder =
            Request.Builder().url(configurationURL).get().header("Accept", "application/json")

        customHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }

        bearerToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

        val response = httpClient.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful) {
            throw SignerException.HttpError(response.code)
        }

        val responseBody = response.body?.string() ?: throw SignerException.InvalidResponse

        return json.decodeFromString(responseBody)
    }

    private fun signData(data: ByteArray, signingURL: String): ByteArray {
        val dataToSignBase64 = Base64.encodeToString(data, Base64.NO_WRAP)
        val requestJson =
            json.encodeToString(SignRequest.serializer(), SignRequest(claim = dataToSignBase64))

        val requestBuilder =
            Request.Builder()
                .url(signingURL)
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")

        customHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }

        bearerToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

        val response = httpClient.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            throw SignerException.HttpError(response.code, errorBody)
        }

        val responseBody = response.body?.string() ?: throw SignerException.InvalidResponse
        val signResponse = json.decodeFromString<SignResponse>(responseBody)
        return Base64.decode(signResponse.signature, Base64.NO_WRAP)
    }

    private fun parseCertificateChain(base64Chain: String): String {
        val chainData = Base64.decode(base64Chain, Base64.DEFAULT)
        val chainString = String(chainData, Charsets.UTF_8)

        if (!chainString.contains("BEGIN CERTIFICATE") || !chainString.contains("END CERTIFICATE")) {
            throw SignerException.InvalidCertificateChain
        }

        return chainString
    }

    @Serializable
    private data class SignerConfiguration(
        val algorithm: String,
        val timestamp_url: String,
        val signing_url: String,
        val certificate_chain: String,
    )

    @Serializable private data class SignRequest(val claim: String)

    @Serializable private data class SignResponse(val signature: String)
}

/** Exceptions specific to signer operations */
sealed class SignerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object InvalidURL : SignerException("Invalid URL")
    object InvalidResponse : SignerException("Invalid response from server")
    data class HttpError(val statusCode: Int, val body: String? = null) :
        SignerException("HTTP error: $statusCode${body?.let { " - $it" } ?: ""}")
    data class UnsupportedAlgorithm(val algorithm: String) :
        SignerException("Unsupported algorithm: $algorithm")
    object InvalidCertificateChain : SignerException("Invalid certificate chain")
    object NoCertificatesFound : SignerException("No certificates found in chain")
    object InvalidSignature : SignerException("Invalid signature format")
}
