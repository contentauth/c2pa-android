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

import org.junit.Test
import java.io.Closeable
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Tests for [closeOnFailure], the helper factories use so that an object they created is closed
 * when configuring it fails. It is internal, so it is tested here and not in the shared suites.
 */
class CloseOnFailureTest {

    private class CountingCloseable : Closeable {
        var closeCount = 0

        override fun close() {
            closeCount++
        }
    }

    @Test
    fun closesAndRethrowsWhenTheBlockThrows() {
        val resource = CountingCloseable()
        val thrown = IllegalStateException("configure failed")

        val caught = assertFailsWith<IllegalStateException> {
            resource.closeOnFailure { throw thrown }
        }

        assertSame(thrown, caught)
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun returnsTheReceiverOpenWhenTheBlockSucceeds() {
        val resource = CountingCloseable()
        var configured: CountingCloseable? = null

        val result = resource.closeOnFailure { configured = it }

        assertSame(resource, result)
        assertSame(resource, configured)
        assertEquals(0, resource.closeCount)
    }

    @Test
    fun keepsTheOriginalFailureWhenCloseAlsoThrows() {
        val resource = object : Closeable {
            override fun close(): Unit = throw IllegalArgumentException("close failed")
        }
        val thrown = IllegalStateException("configure failed")

        val caught = assertFailsWith<IllegalStateException> {
            resource.closeOnFailure { throw thrown }
        }

        assertSame(thrown, caught)
        assertEquals("close failed", caught.suppressed.single().message)
    }
}
