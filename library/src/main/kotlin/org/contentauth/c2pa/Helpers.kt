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

import android.util.Log

private const val TAG = "C2PA"

/**
 * Load native C2PA libraries. Safe to call multiple times - System.loadLibrary handles duplicates.
 */
internal fun loadC2PALibraries() {
    try {
        System.loadLibrary("c2pa_c")
        System.loadLibrary("c2pa_jni")
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Failed to load C2PA libraries - they may already be loaded", e)
    }
}

/** Execute a C2PA operation with standard error handling */
internal inline fun <T : Any> executeC2PAOperation(errorMessage: String, operation: () -> T?): T = try {
    operation() ?: throw C2PAError.Api(C2PA.getError() ?: errorMessage)
} catch (e: IllegalArgumentException) {
    throw C2PAError.Api(e.message ?: "Invalid arguments")
} catch (e: RuntimeException) {
    val error = C2PA.getError()
    if (error != null) {
        throw C2PAError.Api(error)
    }
    throw C2PAError.Api(e.message ?: "Runtime error")
}

/**
 * Converts a DER-encoded ECDSA signature to raw format (r || s) required by COSE.
 *
 * @param derSignature The DER-encoded signature
 * @param componentLength The length of each component (r and s) in bytes
 * @return Raw signature as concatenated r and s components
 */
fun derToRawSignature(derSignature: ByteArray, componentLength: Int): ByteArray {
    var offset = 0

    // Skip SEQUENCE tag (0x30)
    if (derSignature[offset++].toInt() != 0x30) {
        throw IllegalArgumentException("Invalid DER signature: missing SEQUENCE tag")
    }

    // Skip sequence length
    offset++

    // Read r
    if (derSignature[offset++].toInt() != 0x02) {
        throw IllegalArgumentException("Invalid DER signature: missing INTEGER tag for r")
    }

    val rLength = derSignature[offset++].toInt() and 0xFF
    val r = derSignature.copyOfRange(offset, offset + rLength)
    offset += rLength

    // Read s
    if (derSignature[offset++].toInt() != 0x02) {
        throw IllegalArgumentException("Invalid DER signature: missing INTEGER tag for s")
    }

    val sLength = derSignature[offset++].toInt() and 0xFF
    val s = derSignature.copyOfRange(offset, offset + sLength)

    // Remove leading zeros from r and s if present (DER padding)
    val rStripped = r.dropWhile { it == 0.toByte() }.toByteArray()
    val sStripped = s.dropWhile { it == 0.toByte() }.toByteArray()

    // Pad to required length
    val rPadded = ByteArray(componentLength)
    val sPadded = ByteArray(componentLength)

    System.arraycopy(rStripped, 0, rPadded, componentLength - rStripped.size, rStripped.size)
    System.arraycopy(sStripped, 0, sPadded, componentLength - sStripped.size, sStripped.size)

    // Return r || s
    return rPadded + sPadded
}

/** C2PA version fetched once */
val c2paVersion: String by lazy { C2PA.version() }
