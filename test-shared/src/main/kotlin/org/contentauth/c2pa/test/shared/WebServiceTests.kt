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

package org.contentauth.c2pa.test.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.DataStream
import org.contentauth.c2pa.Reader
import org.contentauth.c2pa.WebServiceSigner
import java.util.concurrent.TimeUnit

/** WebServiceTests - Web service tests for signing server */
abstract class WebServiceTests : TestBase() {

    companion object {
        // Use 10.0.2.2 for Android emulator to access host's localhost
        private const val EMULATOR_SERVER_URL = "http://10.0.2.2:8080"
        private const val DEFAULT_BEARER_TOKEN = "test-12345"

        private fun getServerUrl(): String = System.getenv("SIGNING_SERVER_URL") ?: EMULATOR_SERVER_URL

        private fun getBearerToken(): String = System.getenv("BEARER_TOKEN") ?: DEFAULT_BEARER_TOKEN

        private val httpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    private suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val healthClient =
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

            val request = Request.Builder().url("${getServerUrl()}/health").build()

            val response = healthClient.newCall(request).execute()
            response.isSuccessful.also { response.close() }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testWebServiceSigningAndVerification(): TestResult = withContext(Dispatchers.IO) {
        if (!isServerAvailable()) {
            return@withContext TestResult(
                "Web Service Signing & Verification",
                true, // Mark as success but skipped
                "SKIPPED: Server not available",
                status = TestStatus.SKIPPED,
            )
        }

        runTest("Web Service Signing & Verification") {
            try {
                // Use the new WebServiceSigner class
                val webServiceSigner =
                    WebServiceSigner(
                        configurationURL =
                        "${getServerUrl()}/api/v1/c2pa/configuration",
                        bearerToken = getBearerToken(),
                    )

                val signer = webServiceSigner.createSigner()

                // Create test image and manifest
                val testImageData = loadResourceAsBytes("adobe_20220124_ci")
                val manifestJson = TEST_MANIFEST_JSON

                // Sign the image
                val builder = Builder.fromJson(manifestJson)
                val sourceStream = DataStream(testImageData)
                val destStream = ByteArrayStream()

                builder.sign("image/jpeg", sourceStream, destStream, signer)
                val signedImageData = destStream.getData()

                // Verify the signed image has a manifest
                val manifest =
                    Reader.fromStream(
                        format = "image/jpeg",
                        stream = ByteArrayStream(signedImageData),
                    )
                        .use { reader -> reader.json() }
                val hasManifest = manifest.isNotEmpty()

                TestResult(
                    "Web Service Signing & Verification",
                    hasManifest,
                    if (hasManifest) {
                        "Successfully signed via web service"
                    } else {
                        "Signed but no manifest found"
                    },
                    "Signed image size: ${signedImageData.size} bytes",
                )
            } catch (e: Exception) {
                TestResult(
                    "Web Service Signing & Verification",
                    false,
                    "Exception during web service signing: ${e.message}",
                    "${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}",
                )
            }
        }
    }

    suspend fun testWebServiceSignerCreation(): TestResult = withContext(Dispatchers.IO) {
        if (!isServerAvailable()) {
            return@withContext TestResult(
                "Web Service Signer Creation",
                true, // Mark as success but skipped
                "SKIPPED: Server not available",
                status = TestStatus.SKIPPED,
            )
        }

        runTest("Web Service Signer Creation") {
            try {
                // Use the new WebServiceSigner class
                val webServiceSigner =
                    WebServiceSigner(
                        configurationURL =
                        "${getServerUrl()}/api/v1/c2pa/configuration",
                        bearerToken = getBearerToken(),
                    )

                val signer = webServiceSigner.createSigner()

                // Simple test: sign a small image
                val testImageData = loadResourceAsBytes("adobe_20220124_ci")
                val manifestJson =
                    """
                                {
                                    "claim_generator": "c2pa-android-test/1.0",
                                    "title": "Web Service Test"
                                }
                    """.trimIndent()

                val builder = Builder.fromJson(manifestJson)
                val sourceStream = DataStream(testImageData)
                val destStream = ByteArrayStream()

                builder.sign("image/jpeg", sourceStream, destStream, signer)
                val signedImageData = destStream.getData()

                // Verify the signed image has a manifest
                val manifest =
                    Reader.fromStream(
                        format = "image/jpeg",
                        stream = ByteArrayStream(signedImageData),
                    )
                        .use { reader -> reader.json() }
                val hasManifest = manifest.isNotEmpty()

                TestResult(
                    "Web Service Signer Creation",
                    hasManifest,
                    if (hasManifest) {
                        "Successfully created and used web service signer"
                    } else {
                        "Signer created but manifest not found"
                    },
                    "Signed image size: ${signedImageData.size} bytes",
                )
            } catch (e: Exception) {
                TestResult(
                    "Web Service Signer Creation",
                    false,
                    "Exception creating web service signer: ${e.message}",
                    "${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}",
                )
            }
        }
    }

    suspend fun testCSRSigning(): TestResult = withContext(Dispatchers.IO) {
        if (!isServerAvailable()) {
            return@withContext TestResult(
                "CSR Signing",
                true, // Mark as success but skipped
                "SKIPPED: Server not available",
                status = TestStatus.SKIPPED,
            )
        }

        runTest("CSR Signing") {
            // Skip this test for now as it requires HardwareSecurity which is
            // Android-specific
            // and not available in the test-shared module
            TestResult(
                "CSR Signing",
                true,
                "SKIPPED: CSR signing requires Android-specific HardwareSecurity",
                status = TestStatus.SKIPPED,
            )
        }
    }
}
