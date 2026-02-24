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

/**
 * C2PA Context for creating readers and builders with shared configuration.
 *
 * C2PAContext wraps the native C2PAContext struct and provides an immutable, shareable
 * configuration context. Once created, a context can be used to create multiple
 * [Reader] and [Builder] instances that share the same settings.
 *
 * ## Usage
 *
 * ### Default context
 * ```kotlin
 * val context = C2PAContext.create()
 * val builder = Builder.fromContext(context)
 * val reader = Reader.fromContext(context)
 * context.close()
 * ```
 *
 * ### Custom settings
 * ```kotlin
 * val settings = C2PASettings.create()
 *     .updateFromString(settingsJson, "json")
 *
 * val context = C2PAContext.fromSettings(settings)
 * settings.close()
 *
 * val builder = Builder.fromContext(context)
 *     .withDefinition(manifestJson)
 * ```
 *
 * ## Resource Management
 *
 * C2PAContext implements [Closeable] and must be closed when done to free native resources.
 * The context can be closed after creating readers/builders from it.
 *
 * @property ptr Internal pointer to the native C2PAContext instance
 * @see C2PASettings
 * @see Builder
 * @see Reader
 * @since 1.0.0
 */
class C2PAContext internal constructor(internal var ptr: Long) : Closeable {

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Creates a context with default settings.
         *
         * @return A new [C2PAContext] with default configuration
         * @throws C2PAError.Api if the context cannot be created
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun create(): C2PAContext = executeC2PAOperation("Failed to create C2PAContext") {
            val handle = nativeNew()
            if (handle == 0L) null else C2PAContext(handle)
        }

        /**
         * Creates a context with custom settings.
         *
         * The settings are cloned internally, so the caller retains ownership of [settings].
         *
         * @param settings The settings to configure this context with
         * @return A new [C2PAContext] configured with the provided settings
         * @throws C2PAError.Api if the context cannot be created with the given settings
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromSettings(settings: C2PASettings): C2PAContext = executeC2PAOperation("Failed to create C2PAContext with settings") {
            val handle = nativeNewWithSettings(settings.ptr)
            if (handle == 0L) null else C2PAContext(handle)
        }

        @JvmStatic private external fun nativeNew(): Long
        @JvmStatic private external fun nativeNewWithSettings(settingsPtr: Long): Long
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
}
