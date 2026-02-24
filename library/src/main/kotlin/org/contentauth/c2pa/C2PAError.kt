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
 * Sealed error hierarchy for C2PA operations.
 *
 * All errors thrown by the C2PA library are subtypes of this class, allowing
 * callers to handle them with exhaustive `when` expressions.
 */
sealed class C2PAError : Exception() {

    /** An error returned by the native C2PA API with a descriptive message. */
    data class Api(override val message: String) : C2PAError() {
        override fun toString() = "C2PA-API error: $message"
    }

    /** A null pointer was unexpectedly returned from the native layer. */
    object NilPointer : C2PAError() {
        override fun toString() = "Unexpected NULL pointer"
    }

    /** A UTF-8 conversion error occurred when reading native string data. */
    object Utf8 : C2PAError() {
        override fun toString() = "Invalid UTF-8 from C2PA"
    }

    /** A negative status code was returned from a native operation. */
    data class Negative(val value: Long) : C2PAError() {
        override fun toString() = "C2PA negative status $value"
    }
}
