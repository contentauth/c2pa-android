package org.contentauth.c2pa.signingserver.controllers

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.response.respond
import org.contentauth.c2pa.signingserver.models.C2PAConfiguration
import java.util.Base64

class C2PAConfigurationController {
    suspend fun getConfiguration(call: ApplicationCall) {
        try {
            val certificateChain =
                requireNotNull(
                    this::class.java.classLoader?.getResourceAsStream(
                        "certs/es256_certs.pem",
                    ),
                ) { "Certificate file not found in resources" }
                    .bufferedReader()
                    .use { it.readText() }
            val encodedCertChain =
                Base64.getEncoder().encodeToString(certificateChain.toByteArray())

            val serverURL = System.getenv("SIGNING_SERVER_URL")
            if (serverURL.isNullOrEmpty()) {
                call.application.log.error(
                    "SIGNING_SERVER_URL environment variable is not set or is empty",
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to
                            "SIGNING_SERVER_URL environment variable is not set or is empty",
                    ),
                )
                return
            }

            val signingURL = "$serverURL/api/v1/c2pa/sign"
            call.application.log.info("Configuration: serverURL=$serverURL, signingURL=$signingURL")

            val configuration =
                C2PAConfiguration(
                    algorithm = "es256",
                    timestamp_url = "http://timestamp.digicert.com",
                    signing_url = signingURL,
                    certificate_chain = encodedCertChain,
                )

            call.respond(HttpStatusCode.OK, configuration)
        } catch (e: Exception) {
            call.application.log.error("Error getting configuration", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Failed to get configuration")),
            )
        }
    }
}
