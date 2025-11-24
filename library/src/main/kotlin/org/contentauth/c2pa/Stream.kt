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

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Seek modes for stream operations */
enum class SeekMode(val value: Int) {
    START(0),
    CURRENT(1),
    END(2),
}

// Type aliases for stream callbacks
typealias StreamReader = (buffer: ByteArray, count: Int) -> Int

typealias StreamSeeker = (offset: Long, origin: SeekMode) -> Long

typealias StreamWriter = (buffer: ByteArray, count: Int) -> Int

typealias StreamFlusher = () -> Int

/** Abstract base class for C2PA streams */
abstract class Stream : Closeable {

    private var nativeHandle: Long = 0
    internal val rawPtr: Long
        get() = nativeHandle

    init {
        nativeHandle = createStreamNative()
    }

    /** Read data from the stream */
    abstract fun read(buffer: ByteArray, length: Long): Long

    /** Seek to a position in the stream */
    abstract fun seek(offset: Long, mode: Int): Long

    /** Write data to the stream */
    abstract fun write(data: ByteArray, length: Long): Long

    /** Flush the stream */
    abstract fun flush(): Long

    override fun close() {
        if (nativeHandle != 0L) {
            releaseStreamNative(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun createStreamNative(): Long
    private external fun releaseStreamNative(handle: Long)
}

/** Stream implementation backed by Data */
class DataStream(private val data: ByteArray) : Stream() {
    private var cursor = 0

    override fun read(buffer: ByteArray, length: Long): Long {
        val remain = data.size - cursor
        if (remain <= 0) return 0L
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val n = minOf(remain, safeLen)
        System.arraycopy(data, cursor, buffer, 0, n)
        cursor += n
        return n.toLong()
    }

    override fun seek(offset: Long, mode: Int): Long {
        val safeOffset = offset.coerceIn(-Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        cursor =
            when (mode) {
                SeekMode.START.value -> maxOf(0, minOf(data.size, safeOffset))
                SeekMode.CURRENT.value -> maxOf(0, minOf(data.size, cursor + safeOffset))
                SeekMode.END.value -> maxOf(0, minOf(data.size, data.size + safeOffset))
                else -> return -1L
            }
        return cursor.toLong()
    }

    override fun write(data: ByteArray, length: Long): Long =
        throw UnsupportedOperationException("DataStream is read-only")
    override fun flush(): Long = 0L
}

/** Stream implementation with callbacks */
class CallbackStream(
    private val reader: StreamReader? = null,
    private val seeker: StreamSeeker? = null,
    private val writer: StreamWriter? = null,
    private val flusher: StreamFlusher? = null,
) : Stream() {

    override fun read(buffer: ByteArray, length: Long): Long {
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return reader?.invoke(buffer, safeLen)?.toLong()
            ?: throw UnsupportedOperationException(
                "Read operation not supported: no reader callback provided",
            )
    }

    override fun seek(offset: Long, mode: Int): Long {
        val seekMode =
            SeekMode.values().find { it.value == mode }
                ?: throw IllegalArgumentException("Invalid seek mode: $mode")
        return seeker?.invoke(offset, seekMode)
            ?: throw UnsupportedOperationException(
                "Seek operation not supported: no seeker callback provided",
            )
    }

    override fun write(data: ByteArray, length: Long): Long {
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return writer?.invoke(data, safeLen)?.toLong()
            ?: throw UnsupportedOperationException(
                "Write operation not supported: no writer callback provided",
            )
    }

    override fun flush(): Long = flusher?.invoke()?.toLong()
        ?: throw UnsupportedOperationException(
            "Flush operation not supported: no flusher callback provided",
        )
}

/** File-based stream implementation */
class FileStream(fileURL: File, mode: Mode = Mode.READ_WRITE, createIfNeeded: Boolean = true) : Stream() {

    enum class Mode {
        READ,
        WRITE,
        READ_WRITE,
    }

    private val file: RandomAccessFile

    init {
        if (createIfNeeded && !fileURL.exists()) {
            fileURL.createNewFile()
        }

        val fileMode =
            when (mode) {
                Mode.READ -> "r"
                Mode.WRITE, Mode.READ_WRITE -> "rw"
            }

        file = RandomAccessFile(fileURL, fileMode)
        if (mode == Mode.WRITE) {
            file.setLength(0)
        }
    }

    override fun read(buffer: ByteArray, length: Long): Long = try {
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val bytesRead = file.read(buffer, 0, safeLen)
        // Convert -1 (EOF) to 0L for consistency with DataStream
        if (bytesRead == -1) 0L else bytesRead.toLong()
    } catch (e: Exception) {
        throw IOException("Failed to read from file", e)
    }

    override fun seek(offset: Long, mode: Int): Long = try {
        // Validate mode before any file operations
        val newPosition =
            when (mode) {
                SeekMode.START.value -> offset
                SeekMode.CURRENT.value -> file.filePointer + offset
                SeekMode.END.value -> file.length() + offset
                else -> throw IllegalArgumentException("Invalid seek mode: $mode")
            }
        file.seek(newPosition)
        file.filePointer
    } catch (e: IllegalArgumentException) {
        throw e // Re-throw for invalid mode
    } catch (e: Exception) {
        throw IOException("Failed to seek in file", e)
    }

    override fun write(data: ByteArray, length: Long): Long = try {
        val safeLen = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        file.write(data, 0, safeLen)
        safeLen.toLong()
    } catch (e: Exception) {
        throw IOException("Failed to write to file", e)
    }

    override fun flush(): Long = try {
        file.fd.sync()
        0L
    } catch (e: Exception) {
        throw IOException("Failed to flush file", e)
    }

    override fun close() {
        try {
            file.close()
        } catch (e: Exception) {
            android.util.Log.w("FileStream", "Error closing file", e)
        }
        super.close()
    }
}

/**
 * Read-write stream with growable byte array. Use this for in-memory operations that need to write
 * output.
 */
class ByteArrayStream(initialData: ByteArray? = null) : Stream() {
    private val buffer = ByteArrayOutputStream()
    private var position = 0
    private var data: ByteArray = initialData ?: ByteArray(0)

    init {
        initialData?.let { buffer.write(it) }
    }

    override fun read(buffer: ByteArray, length: Long): Long {
        if (position >= data.size) return 0
        val toRead = minOf(length.toInt(), data.size - position)
        System.arraycopy(data, position, buffer, 0, toRead)
        position += toRead
        return toRead.toLong()
    }

    override fun seek(offset: Long, mode: Int): Long {
        position =
            when (mode) {
                SeekMode.START.value -> offset.toInt()
                SeekMode.CURRENT.value -> position + offset.toInt()
                SeekMode.END.value -> data.size + offset.toInt()
                else -> return -1L
            }
        position = position.coerceIn(0, data.size)
        return position.toLong()
    }

    override fun write(writeData: ByteArray, length: Long): Long {
        val len = length.toInt()
        if (position < data.size) {
            // Writing in the middle - need to handle carefully
            val newData = data.toMutableList()
            for (i in 0 until len) {
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
            buffer.write(writeData, 0, len)
            data = buffer.toByteArray()
        }
        position += len
        return length
    }

    override fun flush(): Long {
        data = buffer.toByteArray()
        return 0
    }

    /** Get the current data in the stream */
    fun getData(): ByteArray = data
}
