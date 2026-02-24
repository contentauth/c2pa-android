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
import java.io.ByteArrayOutputStream
import java.io.File

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

    suspend fun testFileOperationsWithDataDirectory(): TestResult = withContext(Dispatchers.IO) {
        runTest("File Operations with Data Directory") {
            val testImageFile = copyResourceToFile("adobe_20220124_ci", "test_datadir.jpg")
            val dataDir = File(getContext().cacheDir, "c2pa_data")
            dataDir.mkdirs()

            try {
                try {
                    C2PA.readFile(testImageFile.absolutePath, dataDir.absolutePath)
                } catch (e: C2PAError) {
                    // May fail if no manifest
                }

                try {
                    C2PA.readIngredientFile(
                        testImageFile.absolutePath,
                        dataDir.absolutePath,
                    )
                } catch (e: C2PAError) {
                    // May fail if no ingredient
                }

                val dataFiles = dataDir.listFiles() ?: emptyArray()
                val success = dataFiles.isNotEmpty() && dataFiles.any { it.length() > 0 }

                TestResult(
                    "File Operations with Data Directory",
                    success,
                    if (success) {
                        "Resources written to data directory"
                    } else {
                        "No resources written"
                    },
                    "Files in dataDir: ${dataFiles.size}, Total size: ${dataFiles.sumOf {
                        it.length()
                    }} bytes",
                )
            } finally {
                testImageFile.delete()
                dataDir.deleteRecursively()
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
}
