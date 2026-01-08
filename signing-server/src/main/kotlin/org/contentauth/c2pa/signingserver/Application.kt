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

package org.contentauth.c2pa.signingserver

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.contentauth.c2pa.signingserver.controllers.C2PAConfigurationController
import org.contentauth.c2pa.signingserver.controllers.C2PASigningController
import org.contentauth.c2pa.signingserver.controllers.CertificateSigningController
import org.contentauth.c2pa.signingserver.services.CertificateSigningService

fun main() {
    val certificateSigningService = CertificateSigningService()

    val c2paSigningController = C2PASigningController()
    val c2paConfigurationController = C2PAConfigurationController()
    val certificateSigningController = CertificateSigningController(certificateSigningService)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }

        install(Authentication) {
            bearer("bearer-auth") {
                authenticate { tokenCredential ->
                    val expectedToken = System.getenv("BEARER_TOKEN")
                    if (expectedToken.isNullOrEmpty()) {
                        null
                    } else if (tokenCredential.token == expectedToken) {
                        UserIdPrincipal("authenticated-user")
                    } else {
                        null
                    }
                }
            }
        }

        routing {
            // Root endpoint
            get {
                call.respond(
                    mapOf(
                        "status" to "C2PA Signing Server is running",
                        "version" to "1.0.0",
                        "mode" to "testing",
                    ),
                )
            }

            // Health check endpoint
            get("/health") { call.respond(HttpStatusCode.OK) }

            // API v1 routes
            route("/api/v1") {
                // Certificate signing endpoint
                route("/certificates") {
                    post("/sign") { certificateSigningController.signCSR(call) }
                }

                // C2PA endpoints with bearer auth protection
                authenticate("bearer-auth") {
                    route("/c2pa") {
                        get("/configuration") {
                            c2paConfigurationController.getConfiguration(call)
                        }
                        post("/sign") { c2paSigningController.signManifest(call) }
                    }
                }
            }
        }
    }
        .start(wait = true)
}
