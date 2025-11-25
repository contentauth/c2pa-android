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
 * Error model for C2PA operations
 */
sealed class C2PAError : Exception() {
    data class Api(override val message: String) : C2PAError() {
        override fun toString() = "C2PA-API error: $message"
    }

    object NilPointer : C2PAError() {
        override fun toString() = "Unexpected NULL pointer"
    }

    object Utf8 : C2PAError() {
        override fun toString() = "Invalid UTF-8 from C2PA"
    }

    data class Negative(val value: Long) : C2PAError() {
        override fun toString() = "C2PA negative status $value"
    }
}
