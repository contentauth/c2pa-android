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
 * Hash binding type a [Builder] uses for a given asset format.
 *
 * Mirrors the native `C2paHashType` enum.
 */
enum class HashType(val value: Int) {
    /** Placeholder + exclusions + hash + sign (JPEG, PNG, etc.). */
    DATA_HASH(0),

    /** Placeholder + hash + sign (MP4, AVIF, HEIF/HEIC). */
    BMFF_HASH(1),

    /** Hash + sign, no placeholder needed. */
    BOX_HASH(2),
    ;

    companion object {
        /**
         * Returns the [HashType] for the given native enum value.
         *
         * @throws IllegalArgumentException if the value does not correspond to a known type
         */
        fun fromValue(value: Int): HashType =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown hash type value: $value")
    }
}
