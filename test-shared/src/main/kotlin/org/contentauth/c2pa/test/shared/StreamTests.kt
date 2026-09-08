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
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.ByteArrayStream
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.C2PAError
import org.contentauth.c2pa.CallbackStream
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.Reader
import org.contentauth.c2pa.SeekMode
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** StreamTests - Stream operations and I/O tests */
abstract class StreamTests : TestBase() {

    suspend fun testStreamOperations(): TestResult = withContext(Dispatchers.IO) {
        runTest("Stream API") {
            val testImageData = loadResourceAsBytes("adobe_20220124_ci")
            ByteArrayStream(testImageData).use { memStream ->
                try {
                    Reader.fromStream("image/jpeg", memStream).use { reader ->
                        val json = reader.json()
                        TestResult(
                            "Stream API",
                            json.isNotEmpty(),
                            "Stream API working",
                            json.take(200),
                        )
                    }
                } catch (e: C2PAError) {
                    TestResult(
                        "Stream API",
                        false,
                        "Failed to create reader from stream",
                        e.toString(),
                    )
                }
            }
        }
    }

    suspend fun testStreamExceptionPropagation(): TestResult = withContext(Dispatchers.IO) {
        runTest("Stream Exception Propagation") {
            // An exception thrown by a stream callback must surface to the caller as
            // itself, not as a generic error, and must not be left pending while the
            // native operation keeps running.
            val marker = "stream deliberately broken"
            var thrown: Throwable? = null

            CallbackStream(
                reader = { _, _ -> throw IOException(marker) },
                seeker = { _, _ -> 0L },
            ).use { stream ->
                try {
                    Reader.fromStream("image/jpeg", stream).use { }
                } catch (e: Throwable) {
                    thrown = e
                }
            }

            val success = thrown is IOException && thrown?.message == marker
            TestResult(
                "Stream Exception Propagation",
                success,
                if (success) {
                    "Stream callback exception surfaced as the original IOException"
                } else {
                    "Expected the callback's IOException, got: $thrown"
                },
                "Thrown: $thrown",
            )
        }
    }

    suspend fun testStreamWriteExceptionPropagation(): TestResult = withContext(Dispatchers.IO) {
        runTest("Stream Write Exception Propagation") {
            // The write path through a result-code entry point (toArchive) must surface
            // the callback's exception as itself, and the exception must not linger:
            // an unrelated failure on the same thread afterwards must raise its own
            // error, not the stream's.
            val marker = "write deliberately broken"
            var archiveThrown: Throwable? = null
            var laterThrown: Throwable? = null

            Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                CallbackStream(
                    writer = { _, _ -> throw IOException(marker) },
                    seeker = { _, _ -> 0L },
                    flusher = { 0 },
                ).use { dest ->
                    try {
                        builder.toArchive(dest)
                    } catch (e: Throwable) {
                        archiveThrown = e
                    }
                }
            }

            Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                try {
                    builder.withDefinition("{ not valid json")
                } catch (e: Throwable) {
                    laterThrown = e
                }
            }

            val propagated = archiveThrown is IOException && archiveThrown?.message == marker
            val isolated = laterThrown != null && laterThrown !is IOException
            val success = propagated && isolated
            TestResult(
                "Stream Write Exception Propagation",
                success,
                when {
                    success -> "toArchive surfaced the IOException and it did not leak into the next call"
                    !propagated -> "Expected the writer's IOException from toArchive, got: $archiveThrown"
                    else -> "Stale stream exception leaked into an unrelated call: $laterThrown"
                },
                "toArchive threw: $archiveThrown\nwithDefinition threw: $laterThrown",
            )
        }
    }

    suspend fun testStreamFileOptions(): TestResult = withContext(Dispatchers.IO) {
        runTest("Stream File Options") {
            val tempFile =
                File.createTempFile(
                    "file-stream-preserve",
                    ".dat",
                    getContext().cacheDir,
                )
            tempFile.writeBytes(ByteArray(100) { it.toByte() })

            try {
                FileStream(
                    tempFile,
                    FileStream.Mode.READ_WRITE,
                    createIfNeeded = false,
                ).use { preserveStream ->
                    val buffer = ByteArray(50)
                    val bytesRead = preserveStream.read(buffer, 50)
                    val success = bytesRead == 50L

                    TestResult(
                        "Stream File Options",
                        success,
                        if (success) {
                            "File stream operations working"
                        } else {
                            "File stream operations failed"
                        },
                        "Bytes read: $bytesRead",
                    )
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun testWriteOnlyStreams(): TestResult = withContext(Dispatchers.IO) {
        runTest("Write-Only Streams") {
            val manifestJson = TEST_MANIFEST_JSON

            try {
                Builder.fromJson(manifestJson).use { builder ->
                    builder.setNoEmbed()
                    ByteArrayStream().use { writeOnlyStream ->
                        builder.toArchive(writeOnlyStream)
                        val data = writeOnlyStream.getData()
                        val success = data.isNotEmpty()

                        TestResult(
                            "Write-Only Streams",
                            success,
                            if (success) {
                                "Write-only stream working"
                            } else {
                                "Write-only stream failed"
                            },
                            "Data size: ${data.size}",
                        )
                    }
                }
            } catch (e: C2PAError) {
                TestResult(
                    "Write-Only Streams",
                    false,
                    "Failed to create builder",
                    e.toString(),
                )
            }
        }
    }

    suspend fun testCustomStreamCallbacks(): TestResult = withContext(Dispatchers.IO) {
        runTest("Custom Stream Callbacks") {
            var readCalled = false
            var writeCalled = false
            var seekCalled = false
            var flushCalled = false

            val buffer = ByteArrayOutputStream()
            var position = 0
            var data = ByteArray(0)

            CallbackStream(
                reader = { buf, length ->
                    readCalled = true
                    if (position >= data.size) return@CallbackStream 0
                    val toRead = minOf(length, data.size - position)
                    System.arraycopy(data, position, buf, 0, toRead)
                    position += toRead
                    toRead
                },
                seeker = { offset, mode ->
                    seekCalled = true
                    position =
                        when (mode) {
                            SeekMode.START -> offset.toInt()
                            SeekMode.CURRENT -> position + offset.toInt()
                            SeekMode.END -> data.size + offset.toInt()
                        }
                    position = position.coerceIn(0, data.size)
                    position.toLong()
                },
                writer = { writeData, length ->
                    writeCalled = true
                    buffer.write(writeData, 0, length)
                    data = buffer.toByteArray()
                    position += length
                    length
                },
                flusher = {
                    flushCalled = true
                    data = buffer.toByteArray()
                    0
                },
            ).use { customStream ->
                customStream.write(ByteArray(10), 10)
                customStream.seek(0, SeekMode.START.value)
                customStream.read(ByteArray(5), 5)
                customStream.flush()

                val allCalled = readCalled && writeCalled && seekCalled && flushCalled
                TestResult(
                    "Custom Stream Callbacks",
                    allCalled,
                    if (allCalled) {
                        "All callbacks invoked"
                    } else {
                        "Some callbacks not invoked"
                    },
                    "Read: $readCalled, Write: $writeCalled, Seek: $seekCalled, Flush: $flushCalled",
                )
            }
        }
    }

    suspend fun testCallbackStreamFactories(): TestResult = withContext(Dispatchers.IO) {
        runTest("Callback Stream Factories") {
            val errors = mutableListOf<String>()

            // forReading factory
            CallbackStream.forReading(
                reader = { _, _ -> 0 },
                seeker = { _, _ -> 0L },
            ).use { stream ->
                // Should support read and seek
                stream.read(ByteArray(1), 1)
                stream.seek(0, SeekMode.START.value)
                // Should throw on write
                try {
                    stream.write(ByteArray(1), 1)
                    errors.add("forReading should not support write")
                } catch (e: UnsupportedOperationException) {
                    // expected
                }
                // Should throw on flush
                try {
                    stream.flush()
                    errors.add("forReading should not support flush")
                } catch (e: UnsupportedOperationException) {
                    // expected
                }
            }

            // forWriting factory
            CallbackStream.forWriting(
                writer = { _, length -> length },
                seeker = { _, _ -> 0L },
                flusher = { 0 },
            ).use { stream ->
                // Should support write, seek, flush
                stream.write(ByteArray(1), 1)
                stream.seek(0, SeekMode.START.value)
                stream.flush()
                // Should throw on read
                try {
                    stream.read(ByteArray(1), 1)
                    errors.add("forWriting should not support read")
                } catch (e: UnsupportedOperationException) {
                    // expected
                }
            }

            // forReadWrite factory
            CallbackStream.forReadWrite(
                reader = { _, _ -> 0 },
                writer = { _, length -> length },
                seeker = { _, _ -> 0L },
                flusher = { 0 },
            ).use { stream ->
                // Should support all operations
                stream.read(ByteArray(1), 1)
                stream.write(ByteArray(1), 1)
                stream.seek(0, SeekMode.START.value)
                stream.flush()
            }

            val success = errors.isEmpty()
            TestResult(
                "Callback Stream Factories",
                success,
                if (success) "All factory methods work correctly" else "Factory method failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testByteArrayStreamBufferGrowth(): TestResult = withContext(Dispatchers.IO) {
        runTest("ByteArrayStream Buffer Growth") {
            val errors = mutableListOf<String>()

            // Start with empty stream
            val stream = ByteArrayStream()
            stream.use {
                // Write data to trigger buffer growth
                val data1 = ByteArray(100) { 0xAA.toByte() }
                it.write(data1, 100)

                // Verify position and data
                var result = it.getData()
                if (result.size != 100) {
                    errors.add("After first write: expected size 100, got ${result.size}")
                }

                // Write more to trigger growth
                val data2 = ByteArray(200) { 0xBB.toByte() }
                it.write(data2, 200)

                result = it.getData()
                if (result.size != 300) {
                    errors.add("After second write: expected size 300, got ${result.size}")
                }

                // Seek back and verify read
                it.seek(0, SeekMode.START.value)
                val readBuf = ByteArray(100)
                val bytesRead = it.read(readBuf, 100)
                if (bytesRead != 100L) {
                    errors.add("Read returned $bytesRead instead of 100")
                }
                if (readBuf[0] != 0xAA.toByte()) {
                    errors.add("Read data mismatch at position 0")
                }

                // Seek to middle and overwrite
                it.seek(50, SeekMode.START.value)
                val data3 = ByteArray(10) { 0xCC.toByte() }
                it.write(data3, 10)

                // Size should not change (overwrite within existing bounds)
                result = it.getData()
                if (result.size != 300) {
                    errors.add("After overwrite: expected size 300, got ${result.size}")
                }
                if (result[50] != 0xCC.toByte()) {
                    errors.add("Overwrite data mismatch at position 50")
                }

                // Seek to end and verify
                val endPos = it.seek(0, SeekMode.END.value)
                if (endPos != 300L) {
                    errors.add("Seek to end returned $endPos instead of 300")
                }

                // Read at end should return 0
                val endRead = it.read(ByteArray(10), 10)
                if (endRead != 0L) {
                    errors.add("Read at end returned $endRead instead of 0")
                }
            }

            val success = errors.isEmpty()
            TestResult(
                "ByteArrayStream Buffer Growth",
                success,
                if (success) "Buffer growth and operations work correctly" else "Buffer growth failures",
                errors.joinToString("\n"),
            )
        }
    }

    suspend fun testLargeBufferHandling(): TestResult = withContext(Dispatchers.IO) {
        runTest("Large Buffer Handling") {
            val largeSize = Int.MAX_VALUE.toLong() + 1L

            CallbackStream(
                reader = { buf, length ->
                    // This should trigger our overflow protection
                    0
                },
                writer = { data, length ->
                    // This should trigger our overflow protection
                    0
                },
            ).use { mockStream ->
                // Try to read with a buffer larger than Int.MAX_VALUE
                val result = mockStream.read(ByteArray(1024), largeSize)
                // The implementation should safely handle this
                val success = result <= Int.MAX_VALUE

                TestResult(
                    "Large Buffer Handling",
                    success,
                    if (success) {
                        "Large buffer handled safely"
                    } else {
                        "Large buffer not handled properly"
                    },
                    "Requested: $largeSize, Got: $result",
                )
            }
        }
    }

    suspend fun testLargeAssetStreamChunking(): TestResult = withContext(Dispatchers.IO) {
        runTest("Large Asset Stream Chunking") {
            chunkingTest("Large Asset Stream Chunking", LARGE_ASSET_BYTES)
        }
    }

    suspend fun testVeryLargeAssetStreamChunking(): TestResult = withContext(Dispatchers.IO) {
        runTest("Very Large Asset Stream Chunking") {
            chunkingTest("Very Large Asset Stream Chunking", VERY_LARGE_ASSET_BYTES)
        }
    }

    /**
     * Signs and then verifies a synthetic asset of [assetBytes], asserting that the JNI bridge
     * never asks a stream callback for more than [STREAM_CHUNK_BYTES] at a time.
     *
     * The bridge used to mirror the core's request size into a Java array, so the core asking for
     * a whole hash range at once made peak heap a function of asset size. This fails against that
     * behaviour: the observed request would be the size of the asset.
     */
    private fun chunkingTest(name: String, assetBytes: Long): TestResult {
        val sourceJpeg = loadResourceAsBytes("pexels_asadphoto_457882")
        val source = File(getContext().cacheDir, "chunking_source_$assetBytes.jpg")
        val signed = File(getContext().cacheDir, "chunking_signed_$assetBytes.jpg")

        try {
            writeInflatedJpeg(sourceJpeg, assetBytes, source)

            // Guard against a fixture too small to exercise chunking, which would let the
            // assertions below pass without proving anything.
            if (source.length() <= STREAM_CHUNK_BYTES) {
                return TestResult(
                    name,
                    false,
                    "Fixture is not larger than the chunk size, so the test proves nothing",
                    "Fixture: ${source.length()} bytes, chunk: $STREAM_CHUNK_BYTES bytes",
                )
            }

            val signObserver = ChunkObserver()
            val certPem = loadResourceAsString("es256_certs")
            val keyPem = loadResourceAsString("es256_private")

            Signer.fromInfo(SignerInfo(SigningAlgorithm.ES256, certPem, keyPem)).use { signer ->
                Builder.fromJson(TEST_MANIFEST_JSON).use { builder ->
                    RecordingFileStream(source, "r", signObserver).use { src ->
                        RecordingFileStream(signed, "rw", signObserver).use { dest ->
                            builder.sign("image/jpeg", src.stream, dest.stream, signer)
                        }
                    }
                }
            }

            val readObserver = ChunkObserver()
            val manifestJson = RecordingFileStream(signed, "r", readObserver).use { stream ->
                Reader.fromStream("image/jpeg", stream.stream).use { reader -> reader.json() }
            }

            val observers = listOf(signObserver, readObserver)
            val maxRead = observers.maxOf { it.maxReadCount }
            val maxWrite = observers.maxOf { it.maxWriteCount }
            val mismatches = observers.sumOf { it.bufferSizeMismatches }

            val details = buildString {
                append("Asset: ${source.length()} bytes, chunk cap: $STREAM_CHUNK_BYTES bytes. ")
                append("Largest read: $maxRead, largest write: $maxWrite. ")
                append("Sign calls: ${signObserver.readCalls} read / ${signObserver.writeCalls} write, ")
                append("verify calls: ${readObserver.readCalls} read. ")
                append("Buffer size mismatches: $mismatches.")
            }

            // Reported on success too, so a passing run still shows the sizes it observed.
            println("$name: $details")

            // A slice of exactly one chunk, rather than merely no more than one, is what proves the
            // cap engaged: the core hands over a whole hash range at once, so a full-size slice can
            // only be the result of that being split. An upper bound alone would also hold if the
            // core stopped making large requests, and would pass while defending nothing.
            val success = maxRead == STREAM_CHUNK_BYTES &&
                maxWrite == STREAM_CHUNK_BYTES &&
                mismatches == 0 &&
                manifestJson.isNotEmpty()

            return TestResult(
                name,
                success,
                when {
                    maxRead > STREAM_CHUNK_BYTES -> "Read request of $maxRead exceeds the chunk cap"
                    maxRead < STREAM_CHUNK_BYTES -> "No read filled a chunk, so the cap is unproven"
                    maxWrite > STREAM_CHUNK_BYTES -> "Write request of $maxWrite exceeds the chunk cap"
                    maxWrite < STREAM_CHUNK_BYTES -> "No write filled a chunk, so the cap is unproven"
                    mismatches > 0 -> "Callback saw a buffer whose size did not match its count"
                    manifestJson.isEmpty() -> "Signed asset produced no manifest"
                    else -> "Stream requests stayed within the chunk cap and the asset verified"
                },
                details,
            )
        } finally {
            source.delete()
            signed.delete()
        }
    }
}

/** Chunk size the JNI bridge caps stream callback requests at, mirroring the native constant. */
private const val STREAM_CHUNK_BYTES = 1 shl 20

/** Asset size for the fast chunking test, large enough to need several chunks per hash range. */
private const val LARGE_ASSET_BYTES = 16L * 1024 * 1024

/**
 * Asset size for the second chunking test. Chosen to exceed a typical Android heap so that, before
 * the bridge capped its allocations, a single request for this range would throw OutOfMemoryError.
 * Running it alongside the smaller asset is what shows peak memory holding constant as the asset
 * grows. See c2pa-android issue 133.
 */
private const val VERY_LARGE_ASSET_BYTES = 200L * 1024 * 1024

/** Largest payload a JPEG APPn segment can carry: the length field covers itself and the payload. */
private const val FILLER_PAYLOAD_BYTES = 65533

/** Records the size of every request the JNI bridge makes to a stream callback. */
private class ChunkObserver {
    var maxReadCount = 0
        private set
    var maxWriteCount = 0
        private set
    var readCalls = 0
        private set
    var writeCalls = 0
        private set

    /**
     * Counts callbacks handed a buffer whose length differs from the count they were asked for.
     * The two have always matched, so a stream implementation that sizes its work from the buffer
     * rather than the count still behaves correctly.
     */
    var bufferSizeMismatches = 0
        private set

    fun recordRead(count: Int, bufferSize: Int) {
        readCalls++
        if (count > maxReadCount) maxReadCount = count
        if (bufferSize != count) bufferSizeMismatches++
    }

    fun recordWrite(count: Int, bufferSize: Int) {
        writeCalls++
        if (count > maxWriteCount) maxWriteCount = count
        if (bufferSize != count) bufferSizeMismatches++
    }
}

/**
 * File-backed [CallbackStream] that reports every request it receives to [observer].
 *
 * Uses the public callback API rather than [FileStream] so that the test exercises the same path
 * a third-party stream implementation would.
 */
private class RecordingFileStream(target: File, mode: String, private val observer: ChunkObserver) : Closeable {

    private val file = RandomAccessFile(target, mode)

    val stream: CallbackStream = CallbackStream(
        reader = { buffer, count ->
            observer.recordRead(count, buffer.size)
            val read = file.read(buffer, 0, count)
            if (read == -1) 0 else read
        },
        writer = { buffer, count ->
            observer.recordWrite(count, buffer.size)
            file.write(buffer, 0, count)
            count
        },
        seeker = { offset, origin ->
            val target = when (origin) {
                SeekMode.START -> offset
                SeekMode.CURRENT -> file.filePointer + offset
                SeekMode.END -> file.length() + offset
            }
            file.seek(target)
            target
        },
        flusher = { 0 },
    )

    override fun close() {
        stream.close()
        file.close()
    }
}

/**
 * Writes [source] to [dest], padded with filler JPEG segments until it reaches at least
 * [targetBytes].
 *
 * Gives the suite a large asset without committing a large fixture or holding one in memory: the
 * padding is written a segment at a time from a single reused buffer. APP9 carries no meaning to
 * either a decoder or the C2PA core, so both skip it and the result stays a valid JPEG.
 */
private fun writeInflatedJpeg(source: ByteArray, targetBytes: Long, dest: File) {
    require(source.size > 6 && source[0] == 0xFF.toByte() && source[1] == 0xD8.toByte()) {
        "Source resource is not a JPEG"
    }

    // JFIF requires APP0 to come directly after SOI, so the filler is inserted after the source's
    // first segment rather than ahead of it. An APPn segment carries a two-byte length covering
    // itself, so where the first one ends is known without parsing any further.
    val insertAt = if (source[2] == 0xFF.toByte() && (source[3].toInt() and 0xF0) == 0xE0) {
        4 + (((source[4].toInt() and 0xFF) shl 8) or (source[5].toInt() and 0xFF))
    } else {
        2
    }

    val payload = ByteArray(FILLER_PAYLOAD_BYTES)
    val segmentLength = FILLER_PAYLOAD_BYTES + 2
    val header = byteArrayOf(
        0xFF.toByte(),
        0xE9.toByte(),
        ((segmentLength shr 8) and 0xFF).toByte(),
        (segmentLength and 0xFF).toByte(),
    )
    val segmentBytes = header.size + payload.size
    val padding = (targetBytes - source.size).coerceAtLeast(0L)
    val segments = (padding + segmentBytes - 1) / segmentBytes

    dest.outputStream().buffered().use { out ->
        out.write(source, 0, insertAt)
        repeat(segments.toInt()) {
            out.write(header)
            out.write(payload)
        }
        out.write(source, insertAt, source.size - insertAt)
    }
}
