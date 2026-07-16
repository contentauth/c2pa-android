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

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * An HTTP request the SDK needs resolved (remote manifest fetch, OCSP, timestamp, etc.),
 * passed to a [HttpResolver].
 *
 * @property url The request URL.
 * @property method The HTTP method (e.g. "GET", "POST").
 * @property headers Request headers.
 * @property body Request body bytes, or `null` if none.
 */
data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
)

/**
 * The response a [HttpResolver] returns for a [HttpRequest].
 *
 * @property status HTTP status code (e.g. 200, 404).
 * @property body Response body bytes, or `null` if none.
 */
data class HttpResponse(val status: Int, val body: ByteArray?)

/**
 * Resolves HTTP requests the SDK makes during signing/reading. Called synchronously by the core;
 * implementations should perform the request and return the [HttpResponse], or throw to signal a
 * failure. Configure via [C2PAContextBuilder.setHttpResolver].
 */
fun interface HttpResolver {
    fun resolve(request: HttpRequest): HttpResponse
}

/**
 * Internal adapter invoked from JNI: builds a [HttpRequest] from the native request fields and
 * forwards to the user's [HttpResolver]. The `resolve(String, String, String, byte[])` signature and
 * the returned [HttpResponse] are referenced by the native trampoline and must not change without
 * updating `c2pa_jni.c`.
 */
internal class HttpResolverBridge(private val resolver: HttpResolver) {
    fun resolve(url: String, method: String, headers: String?, body: ByteArray?): HttpResponse =
        resolver.resolve(HttpRequest(url, method, parseHeaders(headers), body))

    private fun parseHeaders(headers: String?): Map<String, String> {
        if (headers.isNullOrEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (line in headers.split('\n')) {
            if (line.isBlank()) continue
            val idx = line.indexOf(':')
            if (idx > 0) {
                map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
        }
        return map
    }
}

/**
 * [HttpResolver] backed by an [OkHttpClient]. Used by the convenience
 * [C2PAContextBuilder.setHttpResolver] overload. OkHttp's synchronous `execute()` matches the core's
 * synchronous resolver contract, so no extra blocking is needed.
 */
internal class OkHttpHttpResolver(private val client: OkHttpClient) : HttpResolver {
    override fun resolve(request: HttpRequest): HttpResponse {
        val requestBody = request.body?.toRequestBody()
        val builder = Request.Builder().url(request.url).method(request.method, requestBody)
        for ((name, value) in request.headers) {
            builder.addHeader(name, value)
        }
        client.newCall(builder.build()).execute().use { response ->
            return HttpResponse(response.code, response.body?.bytes())
        }
    }
}
