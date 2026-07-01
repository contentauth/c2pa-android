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
import okhttp3.OkHttpClient

/**
 * Mutable builder for assembling a configured [C2PAContext].
 *
 * Use this when a context needs more than default or settings-only configuration — for example to
 * attach a programmatic signer that [Reader] and [Builder] instances created from the context will
 * use. Configure the builder fluently, then call [build] to produce an immutable, shareable
 * [C2PAContext].
 *
 * ## Usage
 *
 * ```kotlin
 * val context = C2PAContextBuilder.create()
 *     .setSettings(settings)
 *     .setSigner(signer)
 *     .build()
 * ```
 *
 * ## Resource Management
 *
 * The builder implements [Closeable]. [build] consumes the builder, so an explicit [close] is only
 * needed when the builder is abandoned without building (use a `use { }` block to be safe).
 *
 * @property ptr Internal pointer to the native C2paContextBuilder instance
 * @see C2PAContext
 * @see C2PASettings
 * @see Signer
 */
class C2PAContextBuilder internal constructor(private var ptr: Long) : Closeable {

    // Native callback-context pointers created via setProgressCallback/setHttpResolver.
    // Ownership transfers to the built C2PAContext on build(); freed here if the builder is
    // abandoned or the build fails.
    private val callbackContexts = mutableListOf<Long>()

    companion object {
        init {
            loadC2PALibraries()
        }

        /**
         * Creates a new, empty context builder.
         *
         * @return A new [C2PAContextBuilder]
         * @throws C2PAError.Api if the builder cannot be created
         */
        @JvmStatic
        @Throws(C2PAError::class)
        fun create(): C2PAContextBuilder = executeC2PAOperation("Failed to create C2PAContextBuilder") {
            val handle = nativeNew()
            if (handle == 0L) null else C2PAContextBuilder(handle)
        }

        @JvmStatic private external fun nativeNew(): Long
    }

    /**
     * Applies settings to the context being built.
     *
     * The settings are cloned internally, so the caller retains ownership of [settings].
     *
     * @param settings The settings to apply
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the settings cannot be applied
     */
    @Throws(C2PAError::class)
    fun setSettings(settings: C2PASettings): C2PAContextBuilder {
        val result = setSettingsNative(ptr, settings.ptr)
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set context settings")
        }
        return this
    }

    /**
     * Attaches a signer to the context being built.
     *
     * Ownership of [signer] transfers to the context: it is *consumed* by this call and must not be
     * used or closed again. The wrapper zeros the signer's internal pointer on success, so calling
     * `close()` on it (or wrapping it in a `use { }` block) is a safe no-op afterward. If a signer
     * is also configured via settings, the programmatic signer set here takes priority.
     *
     * @param signer The signer to attach (consumed by this call)
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the signer is already consumed/closed or cannot be attached
     */
    @Throws(C2PAError::class)
    fun setSigner(signer: Signer): C2PAContextBuilder {
        if (signer.ptr == 0L) {
            throw C2PAError.Api("signer is already closed or consumed")
        }
        val result = setSignerNative(ptr, signer.ptr)
        // The FFI consumes the signer whether or not it succeeds; drop our reference either way.
        signer.ptr = 0L
        if (result < 0) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set context signer")
        }
        return this
    }

    /**
     * Attaches a progress observer invoked at checkpoints during signing/reading on contexts (and
     * the readers/builders) derived from this builder.
     *
     * The observer is notification-only; to cancel an in-flight operation call [C2PAContext.cancel].
     * The observer is retained until the built [C2PAContext] is closed.
     *
     * @param observer The progress observer
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the callback cannot be attached
     */
    @Throws(C2PAError::class)
    fun setProgressCallback(observer: ProgressObserver): C2PAContextBuilder {
        if (ptr == 0L) {
            throw C2PAError.Api("context builder is already consumed")
        }
        val callbackPtr = setProgressCallbackNative(ptr, ProgressCallbackBridge(observer))
        if (callbackPtr == 0L) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set progress callback")
        }
        callbackContexts.add(callbackPtr)
        return this
    }

    /**
     * Attaches a custom HTTP resolver invoked when the SDK needs to make an HTTP request
     * (remote-manifest fetch, OCSP, timestamp) on contexts derived from this builder.
     *
     * The resolver is called synchronously and is retained until the built [C2PAContext] is closed.
     *
     * @param resolver The resolver
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the resolver cannot be attached
     */
    @Throws(C2PAError::class)
    fun setHttpResolver(resolver: HttpResolver): C2PAContextBuilder {
        if (ptr == 0L) {
            throw C2PAError.Api("context builder is already consumed")
        }
        val callbackPtr = setHttpResolverNative(ptr, HttpResolverBridge(resolver))
        if (callbackPtr == 0L) {
            throw C2PAError.Api(C2PA.getError() ?: "Failed to set HTTP resolver")
        }
        callbackContexts.add(callbackPtr)
        return this
    }

    /**
     * Convenience [setHttpResolver] backed by an [OkHttpClient] (defaults to a new client).
     *
     * @param client The OkHttp client to perform requests with
     * @return This builder for fluent chaining
     * @throws C2PAError.Api if the resolver cannot be attached
     */
    @JvmOverloads
    @Throws(C2PAError::class)
    fun setHttpResolver(client: OkHttpClient = OkHttpClient()): C2PAContextBuilder =
        setHttpResolver(OkHttpHttpResolver(client))

    /**
     * Builds an immutable, shareable [C2PAContext] from this builder.
     *
     * The builder is *consumed* by this call and must not be used again; [close] afterward is a
     * safe no-op. Any callbacks configured on this builder are transferred to the built context and
     * freed when it is closed.
     *
     * @return The built [C2PAContext]
     * @throws C2PAError.Api if the context cannot be built
     */
    @Throws(C2PAError::class)
    fun build(): C2PAContext {
        if (ptr == 0L) {
            throw C2PAError.Api("context builder is already consumed")
        }
        val handle = buildNative(ptr)
        // build() consumes the builder regardless of outcome.
        ptr = 0L
        if (handle == 0L) {
            // The builder (and its registered callbacks) is gone; free the orphaned callback boxes.
            callbackContexts.forEach { C2PAContext.releaseCallbackContext(it) }
            callbackContexts.clear()
            throw C2PAError.Api(C2PA.getError() ?: "Failed to build context")
        }
        val context = C2PAContext(handle)
        context.attachCallbackContexts(callbackContexts.toLongArray())
        callbackContexts.clear()
        return context
    }

    override fun close() {
        if (ptr != 0L) {
            free(ptr)
            ptr = 0
        }
        // Free callbacks not transferred to a built context (builder abandoned without build()).
        if (callbackContexts.isNotEmpty()) {
            callbackContexts.forEach { C2PAContext.releaseCallbackContext(it) }
            callbackContexts.clear()
        }
    }

    private external fun free(handle: Long)
    private external fun setSettingsNative(handle: Long, settingsPtr: Long): Int
    private external fun setSignerNative(handle: Long, signerPtr: Long): Int
    private external fun setProgressCallbackNative(handle: Long, bridge: ProgressCallbackBridge): Long
    private external fun setHttpResolverNative(handle: Long, bridge: HttpResolverBridge): Long
    private external fun buildNative(handle: Long): Long
}
