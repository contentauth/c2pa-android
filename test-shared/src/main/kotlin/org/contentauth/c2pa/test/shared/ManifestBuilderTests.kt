package org.contentauth.c2pa.test.shared

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.CallbackStream
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.SeekMode
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.Stream
import org.contentauth.c2pa.manifest.Action
import org.contentauth.c2pa.manifest.AttestationBuilder
import org.contentauth.c2pa.manifest.C2PAActions
import org.contentauth.c2pa.manifest.C2PAFormats
import org.contentauth.c2pa.manifest.ManifestHelpers
import org.contentauth.c2pa.manifest.Thumbnail
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** ManifestBuilderTests - Builder API tests for advanced manifest creation */
abstract class ManifestBuilderTests : TestBase() {

    suspend fun testManifestBuilder (): TestResult = withContext(Dispatchers.IO) {

        runTest("Builder API") {

            // Basic image manifest
            val manifestJson = getTestManifest("pexels_asadphoto_457882.jpg")


            try {
                val builder = Builder.fromJson(manifestJson)
                try {
                    val sourceImageData = loadResourceAsBytes("pexels_asadphoto_457882")
                    val sourceStream = MemoryStream(sourceImageData)

                    val fileTest = File.createTempFile("c2pa-stream-api-test",".jpg")
                    val destStream = FileStream(fileTest)
                    try {
                        val certPem = loadResourceAsString("es256_certs")
                        val keyPem = loadResourceAsString("es256_private")

                        val signerInfo = SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)
                        val signer = Signer.fromInfo(signerInfo)

                        try {
                            val result = builder.sign("image/jpeg", sourceStream.stream, destStream, signer)

                            val manifest = C2PA.readFile(fileTest.absolutePath)
                            val json = if (manifest != null) JSONObject(manifest) else null
                            val success = json?.has("manifests") ?: false

                            TestResult(
                                "Builder API",
                                success,
                                if (success) "Successfully signed image" else "Signing failed",
                                "Original: ${sourceImageData.size}, Signed: ${fileTest.length()}, Result size: ${result.size}\n\n${json}"
                            )
                        } finally {
                            signer.close()
                        }
                    } finally {
                        sourceStream.close()
                        destStream.close()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: C2PAError) {
                TestResult("Builder API", false, "Failed to create builder", e.toString())
            }
        }

        runTest("Builder No-Embed") {

            val manifestJson = getTestManifestAdvanced("pexels_asadphoto_457882.jpg")

            try {
                val builder = Builder.fromJson(manifestJson)
                try {
                    builder.setNoEmbed()
                    val archiveStream = ByteArrayStream()
                    try {
                        builder.toArchive(archiveStream)
                        val data = archiveStream.getData()
                        val success = data.isNotEmpty()
                        TestResult(
                            "Builder No-Embed",
                            success,
                            if (success) {
                                "Archive created successfully"
                            } else {
                                "Archive creation failed"
                            },
                            "Archive size: ${data.size}",
                        )
                    } finally {
                        archiveStream.close()
                    }
                } finally {
                    builder.close()
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Builder No-Embed",
                    false,
                    "Failed to create builder",
                    e.toString(),
                )
            }
        }
    }

    private fun getTestManifest(title: String): String {
        // Basic image manifest
        val manifestJson = ManifestHelpers.createBasicImageManifest(
            title = title,
            format = C2PAFormats.JPEG
        ).claimGenerator("Android Test Suite","1.0.0")
            .addAction(Action(C2PAActions.OPENED, whenTimestamp = getCurrentTimestamp(),"Android Test Suite"))
            .addAssertion("c2pa.test","{\"test\": true}")
            .buildJson()

        return manifestJson
    }

    private fun getTestManifestAdvanced(title: String): String {

        // Basic image manifest
        val manifestBuilder = ManifestHelpers.createBasicImageManifest(
            title = title,
            format = C2PAFormats.JPEG
        ).claimGenerator("Android Test Suite","1.0.0")
            .addAction(Action(C2PAActions.PLACED, whenTimestamp = getCurrentTimestamp(),"Android Test Suite"))
            .addAssertion("c2pa.test","{\"test\": true}")
            .addThumbnail(Thumbnail(C2PAFormats.JPEG,"${title}_thumb.jpg"))

        val attestationBuilder = AttestationBuilder()

        attestationBuilder.addCreativeWork {
            addAuthor("Test Author")
            dateCreated(Date())
        }

        attestationBuilder.addCreativeWork {
            addAuthor("Test Author")
            dateCreated(Date())
        }

        val locationJson = JSONObject().apply {
            put("@type", "Place")
            put("latitude", "0.0")
            put("longitude", "0.0")
            put("name", "Somewhere")
        }

        attestationBuilder.addAssertionMetadata {
            dateTime(getCurrentTimestamp())
            device("Test Device")
            location(locationJson)
        }

        val customAttestationJson = JSONObject().apply {
            put("@type", "Integrity")
            put("nonce", "something")
            put("response", "b64encodedresponse")
        }

        attestationBuilder.addCustomAttestation("app.integrity", customAttestationJson)

        attestationBuilder.addCAWGIdentity {
            validFromNow()
            addInstagramIdentity("photographer_john", "2024-10-08T18:04:08Z")
            addLinkedInIdentity("John Smith", "https://www.linkedin.com/in/jsmith", "2024-10-08T18:03:41Z")
            addBehanceIdentity("johnsmith_photos", "2024-10-22T19:31:17Z")
        }

        attestationBuilder.buildForManifest(manifestBuilder)

        manifestBuilder.addAction(Action(C2PAActions.RECOMPRESSED, getCurrentTimestamp(), "Photo Resizer"))

        val resultJson = manifestBuilder.buildJson()

        Log.d("Test Manifest",resultJson)

        return resultJson
    }

    private fun getCurrentTimestamp(): String {
        val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return iso8601.format(Date())
    }
}

/**
 * Memory stream implementation using CallbackStream
 * Shared between instrumented tests and test app
 */
class MemoryStream {
    private val buffer = ByteArrayOutputStream()
    private var position = 0
    private var data: ByteArray

    val stream: Stream

    constructor() {
        data = ByteArray(0)
        stream = createStream()
    }

    constructor(initialData: ByteArray) {
        buffer.write(initialData)
        data = buffer.toByteArray()
        stream = createStream()
    }

    private fun createStream(): Stream {
        return CallbackStream(
            reader = { buffer, length ->
                if (position >= data.size) return@CallbackStream 0
                val toRead = minOf(length, data.size - position)
                System.arraycopy(data, position, buffer, 0, toRead)
                position += toRead
                toRead
            },
            seeker = { offset, mode ->
                position = when (mode) {
                    SeekMode.START -> offset.toInt()
                    SeekMode.CURRENT -> position + offset.toInt()
                    SeekMode.END -> data.size + offset.toInt()
                }
                position = position.coerceIn(0, data.size)
                position.toLong()
            },
            writer = { writeData, length ->
                if (position < data.size) {
                    // Writing in the middle - need to handle carefully
                    val newData = data.toMutableList()
                    for (i in 0 until length) {
                        if (position + i < newData.size) {
                            newData[position + i] = writeData[i]
                        } else {
                            newData.add(writeData[i])
                        }
                    }
                    data = newData.toByteArray()
                    buffer.reset()
                    buffer.write(data)
                } else {
                    // Appending
                    buffer.write(writeData, 0, length)
                    data = buffer.toByteArray()
                }
                position += length
                length
            },
            flusher = {
                data = buffer.toByteArray()
                0
            }
        )
    }

    fun seek(offset: Long, mode: Int): Long = stream.seek(offset, mode)
    fun close() = stream.close()
    fun getData(): ByteArray = data
}
