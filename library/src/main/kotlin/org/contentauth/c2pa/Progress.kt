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

/**
 * Phase of a C2PA sign/read operation, reported via [ProgressObserver].
 *
 * Mirrors the native `C2paProgressPhase` enum. [UNKNOWN] is a forward-compatibility
 * fallback for phases added in newer core versions.
 */
enum class ProgressPhase(val value: Int) {
    READING(0),
    VERIFYING_MANIFEST(1),
    VERIFYING_SIGNATURE(2),
    VERIFYING_INGREDIENT(3),
    VERIFYING_ASSET_HASH(4),
    ADDING_INGREDIENT(5),
    THUMBNAIL(6),
    HASHING(7),
    SIGNING(8),
    EMBEDDING(9),
    FETCHING_REMOTE_MANIFEST(10),
    WRITING(11),
    FETCHING_OCSP(12),
    FETCHING_TIMESTAMP(13),
    UNKNOWN(-1),
    ;

    companion object {
        /** Maps a native phase value to a [ProgressPhase], falling back to [UNKNOWN]. */
        fun fromValue(value: Int): ProgressPhase = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

/**
 * A single progress notification.
 *
 * @property phase The current operation phase.
 * @property step Monotonically increasing counter within [phase], starting at 1; a liveness signal.
 * @property total `0` = indeterminate, `1` = single-shot, `> 1` = determinate (`step / total`).
 */
data class ProgressUpdate(val phase: ProgressPhase, val step: Long, val total: Long)

/**
 * Observer notified at progress checkpoints during signing/reading.
 *
 * This is an observer only — returning has no effect on the operation. To cancel an in-flight
 * operation, call [C2PAContext.cancel] from another thread.
 */
fun interface ProgressObserver {
    fun onProgress(update: ProgressUpdate)
}

/**
 * Internal adapter invoked from JNI with primitive args; builds a [ProgressUpdate] and forwards it
 * to the user's [ProgressObserver]. The `onProgress(int, long, long)` signature is referenced by the
 * native trampoline and must not change without updating `c2pa_jni.c`.
 */
internal class ProgressCallbackBridge(private val observer: ProgressObserver) {
    fun onProgress(phase: Int, step: Long, total: Long) {
        observer.onProgress(ProgressUpdate(ProgressPhase.fromValue(phase), step, total))
    }
}
