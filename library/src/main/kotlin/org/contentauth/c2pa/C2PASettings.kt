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

import org.contentauth.c2pa.settings.C2PASettingsDefinition
import java.io.Closeable

/**
 * C2PA Settings for configuring context-based operations.
 *
 * C2PASettings wraps the native C2PASettings struct and provides a fluent API for
 * configuring settings that can be passed to [C2PAContext].
 *
 * ## Usage
 *
 * ```kotlin
 * val settings = C2PASettings.create()
 *     .updateFromString("""{"version": 1, "builder": {"created_assertion_labels": ["c2pa.actions"]}}""", "json")
 *     .setValue("verify.verify_after_sign", "true")
 *
 * val context = C2PAContext.fromSettings(settings)
 * settings.close() // settings can be closed after creating the context
 * ```
 *
 * ## Resource Management
 *
 * C2PASettings implements [Closeable] and must be closed when done to free native resources.
 *
 * @property ptr Internal pointer to the native C2PASettings instance
 * @see C2PAContext
 * @since 1.0.0
 */
class C2PASettings internal constructor(internal var ptr: Long) : Closeable {

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Creates a new settings instance with default values.
         *
         * @return A new [C2PASettings] instance
         * @throws C2PAError.Api if the settings cannot be created
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun create(): C2PASettings = executeC2PAOperation("Failed to create C2PASettings") {
            val handle = nativeNew()
            if (handle == 0L) null else C2PASettings(handle)
        }

        /**
         * Creates a new settings instance from a [C2PASettingsDefinition].
         *
         * @param definition The typed settings definition to apply.
         * @return A new [C2PASettings] instance configured from the definition.
         * @throws C2PAError.Api if the settings cannot be created or the definition is invalid.
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun fromDefinition(definition: C2PASettingsDefinition): C2PASettings =
            create().updateFrom(definition)

        @JvmStatic private external fun nativeNew(): Long
    }

    /**
     * Updates settings from a JSON or TOML string.
     *
     * @param settingsStr The settings string in JSON or TOML format
     * @param format The format of the string ("json" or "toml")
     * @return This settings instance for fluent chaining
     * @throws C2PAError.Api if the settings string is invalid
     */
    @Throws(C2PAError::class)
    fun updateFromString(settingsStr: String, format: String): C2PASettings {
        val result = updateFromStringNative(ptr, settingsStr, format)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to update settings from string")
        }
        return this
    }

    /**
     * Updates settings from a typed [C2PASettingsDefinition].
     *
     * @param definition The typed settings definition to apply.
     * @return This settings instance for fluent chaining.
     * @throws C2PAError.Api if the definition is invalid.
     */
    @Throws(C2PAError::class)
    fun updateFrom(definition: C2PASettingsDefinition): C2PASettings =
        updateFromString(definition.toJson(), "json")

    /**
     * Sets a specific configuration value using dot notation.
     *
     * @param path Dot-separated path (e.g., "verify.verify_after_sign")
     * @param value JSON value as a string (e.g., "true", "\"ps256\"", "42")
     * @return This settings instance for fluent chaining
     * @throws C2PAError.Api if the path or value is invalid
     */
    @Throws(C2PAError::class)
    fun setValue(path: String, value: String): C2PASettings {
        val result = setValueNative(ptr, path, value)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set settings value")
        }
        return this
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
    }

    private external fun free(handle: Long)
    private external fun updateFromStringNative(handle: Long, settingsStr: String, format: String): Int
    private external fun setValueNative(handle: Long, path: String, value: String): Int
}
