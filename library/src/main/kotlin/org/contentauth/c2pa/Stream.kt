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

    companion object {
        init {
            loadC2PALibraries()
        }
    }

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

    override fun flush(): Long =
        throw UnsupportedOperationException("DataStream is read-only")
}

/**
 * Stream implementation with callbacks.
 *
 * Consider using the type-safe factory methods [forReading], [forWriting], or [forReadWrite]
 * to ensure required callbacks are provided at compile time.
 */
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
            SeekMode.entries.find { it.value == mode }
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

    companion object {
        /**
         * Creates a read-only callback stream.
         *
         * @param reader Callback to read data into a buffer, returning bytes read.
         * @param seeker Callback to seek to a position, returning the new position.
         * @return A CallbackStream configured for reading.
         */
        fun forReading(
            reader: StreamReader,
            seeker: StreamSeeker,
        ): CallbackStream = CallbackStream(reader = reader, seeker = seeker)

        /**
         * Creates a write-only callback stream.
         *
         * @param writer Callback to write data from a buffer, returning bytes written.
         * @param seeker Callback to seek to a position, returning the new position.
         * @param flusher Callback to flush the stream, returning 0 on success.
         * @return A CallbackStream configured for writing.
         */
        fun forWriting(
            writer: StreamWriter,
            seeker: StreamSeeker,
            flusher: StreamFlusher,
        ): CallbackStream = CallbackStream(writer = writer, seeker = seeker, flusher = flusher)

        /**
         * Creates a read-write callback stream.
         *
         * @param reader Callback to read data into a buffer, returning bytes read.
         * @param writer Callback to write data from a buffer, returning bytes written.
         * @param seeker Callback to seek to a position, returning the new position.
         * @param flusher Callback to flush the stream, returning 0 on success.
         * @return A CallbackStream configured for both reading and writing.
         */
        fun forReadWrite(
            reader: StreamReader,
            writer: StreamWriter,
            seeker: StreamSeeker,
            flusher: StreamFlusher,
        ): CallbackStream = CallbackStream(
            reader = reader,
            writer = writer,
            seeker = seeker,
            flusher = flusher,
        )
    }
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
    private var data: ByteArray = initialData?.copyOf() ?: ByteArray(0)
    private var position = 0
    private var size = data.size

    override fun read(buffer: ByteArray, length: Long): Long {
        if (position >= size) return 0
        val toRead = minOf(length.toInt(), size - position)
        System.arraycopy(data, position, buffer, 0, toRead)
        position += toRead
        return toRead.toLong()
    }

    override fun seek(offset: Long, mode: Int): Long {
        position =
            when (mode) {
                SeekMode.START.value -> offset.toInt()
                SeekMode.CURRENT.value -> position + offset.toInt()
                SeekMode.END.value -> size + offset.toInt()
                else -> return -1L
            }
        position = position.coerceIn(0, size)
        return position.toLong()
    }

    override fun write(data: ByteArray, length: Long): Long {
        val len = length.toInt()
        val requiredCapacity = position + len

        // Expand buffer if needed (grow by 2x or to required size, whichever is larger)
        if (requiredCapacity > this.data.size) {
            val newCapacity = maxOf(this.data.size * 2, requiredCapacity)
            this.data = this.data.copyOf(newCapacity)
        }

        // Copy data directly into buffer
        System.arraycopy(data, 0, this.data, position, len)
        position += len

        // Update size if we wrote past the current end
        if (position > size) {
            size = position
        }

        return len.toLong()
    }

    override fun flush(): Long = 0

    /** Get the current data in the stream */
    fun getData(): ByteArray = data.copyOf(size)
}
