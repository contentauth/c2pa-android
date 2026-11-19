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

package org.contentauth.c2pa.manifest

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.intOrNull
import java.util.Base64

/**
 * A URI reference with an associated hash for integrity verification.
 *
 * The hash is held as a base64 string. c2pa-rs takes hash bytes as a JSON array of numbers
 * and reports them as `b64'<base64>'`, so the hash is written as a byte array and read from
 * either form.
 *
 * @property url The URL being referenced.
 * @property hash The hash bytes for integrity verification, base64 encoded.
 * @property alg The algorithm used for hashing.
 * @see MetadataActor
 */
@Serializable
data class HashedUri(
    val url: String,
    @Serializable(with = HashBytesSerializer::class)
    val hash: String,
    val alg: String? = null,
)

/**
 * Serializes a base64 string as the JSON byte array c2pa-rs reads hash bytes from.
 *
 * c2pa-rs reads a JSON string in a byte field as the raw bytes of the string, so a base64
 * string has to be written as an array of numbers to be signed as the bytes it encodes.
 * Reading accepts that array, the `b64'<base64>'` form a [org.contentauth.c2pa.Reader]
 * reports, and a plain base64 string.
 */
internal object HashBytesSerializer : JsonTransformingSerializer<String>(String.serializer()) {

    private const val MARKER_PREFIX = "b64'"
    private const val MARKER_SUFFIX = "'"

    override fun transformSerialize(element: JsonElement): JsonElement {
        val base64 = (element as JsonPrimitive).content
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Hash is not valid base64: $base64", e)
        }
        return JsonArray(bytes.map { JsonPrimitive(it.toInt() and 0xFF) })
    }

    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> {
            val bytes = ByteArray(element.size) { index ->
                val value = (element[index] as? JsonPrimitive)?.intOrNull
                if (value == null || value !in 0..255) {
                    throw SerializationException("Hash byte at index $index is not in 0..255: ${element[index]}")
                }
                value.toByte()
            }
            JsonPrimitive(Base64.getEncoder().encodeToString(bytes))
        }
        is JsonPrimitive -> {
            val content = element.content
            if (element.isString && content.startsWith(MARKER_PREFIX) && content.endsWith(MARKER_SUFFIX)) {
                JsonPrimitive(content.substring(MARKER_PREFIX.length, content.length - MARKER_SUFFIX.length))
            } else {
                element
            }
        }
        else -> element
    }
}
