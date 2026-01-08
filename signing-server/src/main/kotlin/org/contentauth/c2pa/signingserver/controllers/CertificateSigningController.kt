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
import org.contentauth.c2pa.signingserver.models.CertificateSigningRequest
import org.contentauth.c2pa.signingserver.services.CertificateSigningService

class CertificateSigningController(private val certificateService: CertificateSigningService) {
    suspend fun signCSR(call: ApplicationCall) {
        try {
            val csrRequest = call.receive<CertificateSigningRequest>()

            // Validate CSR format
            if (!csrRequest.csr.contains("BEGIN CERTIFICATE REQUEST")) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid CSR format"),
                )
                return
            }

            // Sign the CSR
            val response = certificateService.signCSR(csrRequest.csr)

            // Log the issuance
            call.application.log.info("Issued certificate: ${response.certificate_id}")

            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.application.log.error("Error signing CSR", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Failed to sign CSR")),
            )
        }
    }
}
