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

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store of C2PA manifest stores, keyed by client-chosen id.
 *
 * Tests publish a manifest with PUT and point a no-embed asset's remote manifest URL at the
 * matching GET, so the SDK's HTTP resolver fetches it back over real HTTP.
 */
class ManifestStoreController {
    private val manifests = ConcurrentHashMap<String, ByteArray>()

    suspend fun store(call: ApplicationCall) {
        val id = call.parameters["id"]
        if (id.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing manifest id"))
            return
        }
        val bytes = call.receive<ByteArray>()
        if (bytes.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Empty manifest body"))
            return
        }
        manifests[id] = bytes
        call.respond(HttpStatusCode.Created, mapOf("id" to id, "size" to bytes.size.toString()))
    }

    suspend fun fetch(call: ApplicationCall) {
        val id = call.parameters["id"]
        val bytes = if (id.isNullOrEmpty()) null else manifests[id]
        if (bytes == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Manifest not found"))
            return
        }
        call.respondBytes(bytes, ContentType("application", "c2pa"))
    }
}
