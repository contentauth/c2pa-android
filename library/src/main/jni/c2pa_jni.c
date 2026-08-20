/*
 * C2PA JNI Implementation
 * JNI bridge for the C2PA native library
 */

#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <pthread.h>
#include "c2pa.h"

// Global JavaVM reference for callback handling
static JavaVM *g_jvm = NULL;
static pthread_mutex_t g_jvm_mutex = PTHREAD_MUTEX_INITIALIZER;

// Thread-local key for tracking attached threads
static pthread_key_t g_thread_attached_key;
static pthread_once_t g_thread_key_once = PTHREAD_ONCE_INIT;

// Cached class references
static jclass g_streamClass = NULL;
// SignerInfo class reference no longer needed
static jclass g_signResultClass = NULL;

// Cached method IDs for Stream
static jmethodID g_streamReadMethod = NULL;
static jmethodID g_streamSeekMethod = NULL;
static jmethodID g_streamWriteMethod = NULL;
static jmethodID g_streamFlushMethod = NULL;

// SignerInfo class is no longer accessed directly from JNI

// Stream context wrapper for Java callbacks
typedef struct {
    jobject streamObject;  // Global reference
} JavaStreamContext;

// Signer callback context. The core may invoke a callback with its user_data
// at any time until every object derived from the owning signer is gone, and
// provides no destructor hook, so a trampoline must never treat what it is
// handed as a pointer. The core is given an opaque id that is never reused, and
// liveness is a registry lookup: a context is invocable only while an entry
// with that id is present in its registry, and refCount (guarded by the
// registry mutex, one reference held by the registry and one per in-flight
// invocation) defers the free until the last holder releases. A stale id from
// the core is simply not found and ignored, and can never alias a newer context
// the way a recycled heap address could.
typedef struct {
    uintptr_t id;          // Opaque token handed to the core as user_data
    jobject callback;      // Global reference
    jmethodID signMethod;
    int refCount;
} JavaSignerContext;

// Context-builder callback context (progress observer / HTTP resolver).
// Created on the builder, ownership transferred to the built C2PAContext, and
// released when that context is closed, with the same registry-lookup liveness
// scheme as JavaSignerContext (under g_contextCallbacksMutex).
typedef struct {
    uintptr_t id;          // Opaque token handed to the core as user_data
    jobject callback;      // Global reference to the Kotlin bridge object
    jmethodID method;      // Cached bridge method id
    int refCount;
} JavaContextCallback;

typedef struct SignerContextNode {
    JavaSignerContext *context;
    struct C2paSigner *signer;
    struct SignerContextNode *next;
} SignerContextNode;

typedef struct ContextCallbackNode {
    JavaContextCallback *context;
    struct ContextCallbackNode *next;
} ContextCallbackNode;

static SignerContextNode *g_signerContexts = NULL;
static pthread_mutex_t g_signerContextsMutex = PTHREAD_MUTEX_INITIALIZER;
static ContextCallbackNode *g_contextCallbacks = NULL;
static pthread_mutex_t g_contextCallbacksMutex = PTHREAD_MUTEX_INITIALIZER;

// Monotonic source of callback-context ids. Never reused for the lifetime of
// the process; 0 is reserved so a NULL user_data never matches.
static uintptr_t g_nextCallbackId = 1;
static pthread_mutex_t g_callbackIdMutex = PTHREAD_MUTEX_INITIALIZER;

static uintptr_t next_callback_id(void) {
    pthread_mutex_lock(&g_callbackIdMutex);
    uintptr_t id = g_nextCallbackId++;
    pthread_mutex_unlock(&g_callbackIdMutex);
    return id;
}

// JNI OnLoad - save JavaVM reference and cache IDs
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    pthread_mutex_lock(&g_jvm_mutex);
    g_jvm = vm;
    pthread_mutex_unlock(&g_jvm_mutex);
    
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    // Cache frequently used classes and methods
    jclass localStreamClass = (*env)->FindClass(env, "org/contentauth/c2pa/Stream");
    if (localStreamClass != NULL) {
        g_streamClass = (*env)->NewGlobalRef(env, localStreamClass);
        (*env)->DeleteLocalRef(env, localStreamClass);
        
        g_streamReadMethod = (*env)->GetMethodID(env, g_streamClass, "read", "([BJ)J");
        g_streamSeekMethod = (*env)->GetMethodID(env, g_streamClass, "seek", "(JI)J");
        g_streamWriteMethod = (*env)->GetMethodID(env, g_streamClass, "write", "([BJ)J");
        g_streamFlushMethod = (*env)->GetMethodID(env, g_streamClass, "flush", "()J");
    }
    
    // SignerInfo class is no longer needed - parameters are passed directly
    
    jclass localSignResultClass = (*env)->FindClass(env, "org/contentauth/c2pa/Builder$SignResult");
    if (localSignResultClass != NULL) {
        g_signResultClass = (*env)->NewGlobalRef(env, localSignResultClass);
        (*env)->DeleteLocalRef(env, localSignResultClass);
    }
    
    return JNI_VERSION_1_6;
}

// Cleanup all remaining signer contexts
static void cleanup_all_signer_contexts(JNIEnv *env) {
    pthread_mutex_lock(&g_signerContextsMutex);
    SignerContextNode *detached = g_signerContexts;
    g_signerContexts = NULL;
    pthread_mutex_unlock(&g_signerContextsMutex);

    while (detached != NULL) {
        SignerContextNode *next = detached->next;
        JavaSignerContext *ctx = detached->context;

        if (ctx != NULL) {
            pthread_mutex_lock(&g_signerContextsMutex);
            int remaining = --ctx->refCount;
            pthread_mutex_unlock(&g_signerContextsMutex);
            if (remaining == 0) {
                if (ctx->callback != NULL) {
                    (*env)->DeleteGlobalRef(env, ctx->callback);
                }
                free(ctx);
            }
        }

        free(detached);
        detached = next;
    }
}

// JNI OnUnload - cleanup global references
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    
    // Clean up any remaining signer contexts
    cleanup_all_signer_contexts(env);
    
    if (g_streamClass != NULL) {
        (*env)->DeleteGlobalRef(env, g_streamClass);
        g_streamClass = NULL;
    }
    
    // SignerInfo class cleanup no longer needed
    
    if (g_signResultClass != NULL) {
        (*env)->DeleteGlobalRef(env, g_signResultClass);
        g_signResultClass = NULL;
    }
    
    pthread_mutex_lock(&g_jvm_mutex);
    g_jvm = NULL;
    pthread_mutex_unlock(&g_jvm_mutex);
}

// Helper function to check for pending exceptions
static int check_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 1;
    }
    return 0;
}

// Helper function to convert jstring to C string with null checking
static const char* jstring_to_cstring(JNIEnv *env, jstring jstr) {
    if (jstr == NULL) return NULL;
    const char* cstr = (*env)->GetStringUTFChars(env, jstr, NULL);
    if (cstr == NULL) {
        check_exception(env);
    }
    return cstr;
}

// Helper function to release C string from jstring
static void release_cstring(JNIEnv *env, jstring jstr, const char* cstr) {
    if (jstr != NULL && cstr != NULL) {
        (*env)->ReleaseStringUTFChars(env, jstr, cstr);
    }
}

// Helper function to convert C string to jstring with null checking
static jstring cstring_to_jstring(JNIEnv *env, const char* cstr) {
    if (cstr == NULL) return NULL;
    jstring jstr = (*env)->NewStringUTF(env, cstr);
    if (jstr == NULL) {
        check_exception(env);
    }
    return jstr;
}

// Helper to convert a C string array (as returned by c2pa_*_supported_mime_types)
// into a Java String[]. Does not free the source array; the caller is responsible.
static jobjectArray cstring_array_to_jarray(JNIEnv *env, const char *const *items, uintptr_t count) {
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    if (stringClass == NULL) {
        check_exception(env);
        return NULL;
    }
    jobjectArray result = (*env)->NewObjectArray(env, (jsize)count, stringClass, NULL);
    (*env)->DeleteLocalRef(env, stringClass);
    if (result == NULL) {
        check_exception(env);
        return NULL;
    }
    for (uintptr_t i = 0; i < count; i++) {
        jstring item = cstring_to_jstring(env, items[i]);
        if (item != NULL) {
            (*env)->SetObjectArrayElement(env, result, (jsize)i, item);
            (*env)->DeleteLocalRef(env, item);
        }
    }
    return result;
}

// Thread key destructor - detaches thread when it exits
static void thread_detach_destructor(void *value) {
    if (value != NULL) {
        JavaVM *jvm = NULL;
        pthread_mutex_lock(&g_jvm_mutex);
        jvm = g_jvm;
        pthread_mutex_unlock(&g_jvm_mutex);
        
        if (jvm != NULL) {
            (*jvm)->DetachCurrentThread(jvm);
        }
    }
}

// Initialize thread-local storage key
static void init_thread_key() {
    pthread_key_create(&g_thread_attached_key, thread_detach_destructor);
}

// Helper to get JNIEnv for current thread
static JNIEnv* get_jni_env() {
    JNIEnv *env = NULL;
    JavaVM *jvm = NULL;
    
    pthread_mutex_lock(&g_jvm_mutex);
    jvm = g_jvm;
    pthread_mutex_unlock(&g_jvm_mutex);
    
    if (jvm == NULL) {
        return NULL;
    }
    
    // Ensure thread key is initialized
    pthread_once(&g_thread_key_once, init_thread_key);
    
    jint status = (*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread(jvm, &env, NULL) != JNI_OK) {
            return NULL;
        }
        // Mark this thread as attached so it gets detached on exit
        pthread_setspecific(g_thread_attached_key, (void*)1);
    } else if (status != JNI_OK) {
        return NULL;
    }
    
    return env;
}

// A Java exception raised inside a stream/signer/resolver callback cannot be left
// pending while control returns into Rust: the FFI treats -1 as a recoverable error
// and keeps calling JNI functions, which is undefined behavior with an exception
// pending. Callbacks therefore clear and stash the throwable here, and the outer
// JNI boundary rethrows it so callers see the app's real exception.
//
// Contract: a stash never outlives the JNI entry point whose FFI call produced
// it. Every entry point that runs callbacks clears any leftover at entry and
// calls finish_stashed_exception on every exit after the FFI call, which rethrows
// the stash on failure and drops it on success. Entry points that do not run
// callbacks never consult the stash. Thread-local: a callback stashes on the
// thread that runs it, and the boundary consumes it on the same thread. A stash
// made on a Rust-spawned worker thread has no Java frame to surface in; it is
// dropped when the next stash on that thread replaces it.
static __thread jthrowable g_stashedCallbackException = NULL;

// Drops any exception stashed by a previous native call on this thread.
static void clear_stashed_exception(JNIEnv *env) {
    if (g_stashedCallbackException != NULL) {
        (*env)->DeleteGlobalRef(env, g_stashedCallbackException);
        g_stashedCallbackException = NULL;
    }
}

// If a Java exception is pending, clears it and stashes it for the outer JNI
// boundary. Returns 1 if an exception was pending.
static int stash_pending_exception(JNIEnv *env) {
    jthrowable pending = (*env)->ExceptionOccurred(env);
    if (pending == NULL) {
        return 0;
    }
    (*env)->ExceptionClear(env);
    clear_stashed_exception(env);
    g_stashedCallbackException = (jthrowable)(*env)->NewGlobalRef(env, pending);
    (*env)->DeleteLocalRef(env, pending);
    return 1;
}

// Rethrows the stashed callback exception, if any. Returns 1 if one was thrown.
static int rethrow_stashed_exception(JNIEnv *env) {
    if (g_stashedCallbackException == NULL) {
        return 0;
    }
    (*env)->Throw(env, g_stashedCallbackException);
    (*env)->DeleteGlobalRef(env, g_stashedCallbackException);
    g_stashedCallbackException = NULL;
    return 1;
}

// Consumes the stash at the boundary that produced it. On failure the app's own
// exception is rethrown so it takes precedence over the core's error string; on
// success a stash left by a callback whose failure the core tolerated is dropped
// rather than leaking into a later call. Returns 1 if an exception was thrown.
static int finish_stashed_exception(JNIEnv *env, int failed) {
    if (failed) {
        return rethrow_stashed_exception(env);
    }
    clear_stashed_exception(env);
    return 0;
}

// Helper for safe array allocation with error handling
static jbyteArray safe_new_byte_array(JNIEnv *env, jsize size) {
    if (size < 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Array size cannot be negative");
        return NULL;
    }
    
    jbyteArray array = (*env)->NewByteArray(env, size);
    if (array == NULL) {
        check_exception(env);
    }
    return array;
}

// Stream callbacks. Java exceptions are stashed rather than left pending, since
// these return into Rust code that keeps making JNI calls after a -1.
static intptr_t java_read_callback(struct StreamContext *context, uint8_t *data, intptr_t len) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        return -1;
    }

    if (len > INT32_MAX) {
        c2pa_error_set_last("Requested buffer too large for JNI");
        return -1;
    }

    jbyteArray jdata = safe_new_byte_array(env, (jsize)len);
    if (jdata == NULL) {
        stash_pending_exception(env);
        return -1;
    }

    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, g_streamReadMethod, jdata, (jlong)len);
    if (stash_pending_exception(env)) {
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }

    if (result > 0 && result <= len) {
        (*env)->GetByteArrayRegion(env, jdata, 0, result, (jbyte*)data);
        if (stash_pending_exception(env)) {
            (*env)->DeleteLocalRef(env, jdata);
            return -1;
        }
    }
    (*env)->DeleteLocalRef(env, jdata);

    return (intptr_t)result;
}

static intptr_t java_seek_callback(struct StreamContext *context, intptr_t offset, enum C2paSeekMode mode) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        return -1;
    }

    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, g_streamSeekMethod, (jlong)offset, (jint)mode);
    if (stash_pending_exception(env)) {
        return -1;
    }

    return (intptr_t)result;
}

static intptr_t java_write_callback(struct StreamContext *context, const uint8_t *data, intptr_t len) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        return -1;
    }

    if (len > INT32_MAX) {
        c2pa_error_set_last("Requested buffer too large for JNI");
        return -1;
    }

    jbyteArray jdata = safe_new_byte_array(env, (jsize)len);
    if (jdata == NULL) {
        stash_pending_exception(env);
        return -1;
    }

    (*env)->SetByteArrayRegion(env, jdata, 0, len, (const jbyte*)data);
    if (stash_pending_exception(env)) {
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }

    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, g_streamWriteMethod, jdata, (jlong)len);
    if (stash_pending_exception(env)) {
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }

    (*env)->DeleteLocalRef(env, jdata);
    return (intptr_t)result;
}

static intptr_t java_flush_callback(struct StreamContext *context) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        return -1;
    }

    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, g_streamFlushMethod);
    if (stash_pending_exception(env)) {
        return -1;
    }

    return (intptr_t)result;
}

// Acquire a signer context for a callback invocation. The token handed to us by
// the core is an id, never a pointer; it is resolved through the registry, so a
// stale id for an already-released context is not found and ignored. Returns
// NULL when not found. Must be paired with release_signer_context.
static JavaSignerContext *acquire_signer_context(const void *token) {
    uintptr_t id = (uintptr_t)token;
    JavaSignerContext *found = NULL;
    pthread_mutex_lock(&g_signerContextsMutex);
    for (SignerContextNode *node = g_signerContexts; node != NULL; node = node->next) {
        if (node->context != NULL && node->context->id == id) {
            node->context->refCount++;
            found = node->context;
            break;
        }
    }
    pthread_mutex_unlock(&g_signerContextsMutex);
    return found;
}

// Drop one reference to a signer context, freeing it (and its callback global
// ref) once the last reference is gone. If env is NULL the global ref cannot be
// deleted and is leaked; the struct is still freed.
static void release_signer_context(JNIEnv *env, JavaSignerContext *ctx) {
    pthread_mutex_lock(&g_signerContextsMutex);
    int remaining = --ctx->refCount;
    pthread_mutex_unlock(&g_signerContextsMutex);
    if (remaining == 0) {
        if (env != NULL && ctx->callback != NULL) {
            (*env)->DeleteGlobalRef(env, ctx->callback);
        }
        free(ctx);
    }
}

// Registry lookup acquire/release for context-builder callbacks, same scheme as
// the signer context.
static JavaContextCallback *acquire_context_callback(const void *token) {
    uintptr_t id = (uintptr_t)token;
    JavaContextCallback *found = NULL;
    pthread_mutex_lock(&g_contextCallbacksMutex);
    for (ContextCallbackNode *node = g_contextCallbacks; node != NULL; node = node->next) {
        if (node->context->id == id) {
            node->context->refCount++;
            found = node->context;
            break;
        }
    }
    pthread_mutex_unlock(&g_contextCallbacksMutex);
    return found;
}

static void release_context_callback(JNIEnv *env, JavaContextCallback *ctx) {
    pthread_mutex_lock(&g_contextCallbacksMutex);
    int remaining = --ctx->refCount;
    pthread_mutex_unlock(&g_contextCallbacksMutex);
    if (remaining == 0) {
        if (env != NULL && ctx->callback != NULL) {
            (*env)->DeleteGlobalRef(env, ctx->callback);
        }
        free(ctx);
    }
}

// Adds a context callback to the live registry, making it invocable. Returns
// JNI_FALSE on allocation failure.
static jboolean register_context_callback(JavaContextCallback *ctx) {
    ContextCallbackNode *node = (ContextCallbackNode*)malloc(sizeof(ContextCallbackNode));
    if (node == NULL) {
        return JNI_FALSE;
    }
    node->context = ctx;
    pthread_mutex_lock(&g_contextCallbacksMutex);
    node->next = g_contextCallbacks;
    g_contextCallbacks = node;
    pthread_mutex_unlock(&g_contextCallbacksMutex);
    return JNI_TRUE;
}

// Removes a context callback from the live registry. Later invocations from the
// core no longer find it and are ignored. Returns JNI_TRUE if it was present.
static jboolean unregister_context_callback(JavaContextCallback *ctx) {
    jboolean removed = JNI_FALSE;
    pthread_mutex_lock(&g_contextCallbacksMutex);
    ContextCallbackNode **current = &g_contextCallbacks;
    while (*current != NULL) {
        if ((*current)->context == ctx) {
            ContextCallbackNode *node = *current;
            *current = node->next;
            free(node);
            removed = JNI_TRUE;
            break;
        }
        current = &(*current)->next;
    }
    pthread_mutex_unlock(&g_contextCallbacksMutex);
    return removed;
}

// Body of the signer callback, run while holding a reference on jctx.
static intptr_t java_signer_callback_invoke(JNIEnv *env, JavaSignerContext *jctx,
                                            const unsigned char *data, uintptr_t len,
                                            unsigned char *signed_bytes, uintptr_t signed_len) {
    // Create byte array from data
    if (len > INT32_MAX) {
        c2pa_error_set_last("Requested buffer too large for JNI");
        return -1;
    }

    jbyteArray jdata = safe_new_byte_array(env, (jsize)len);
    if (jdata == NULL) {
        stash_pending_exception(env);
        return -1;
    }

    (*env)->SetByteArrayRegion(env, jdata, 0, len, (const jbyte*)data);
    if (stash_pending_exception(env)) {
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }

    // Call the sign method
    jbyteArray jsignature = (jbyteArray)(*env)->CallObjectMethod(env, jctx->callback, jctx->signMethod, jdata);
    (*env)->DeleteLocalRef(env, jdata);

    if (stash_pending_exception(env)) {
        return -1;
    }

    if (jsignature == NULL) {
        return -1;
    }

    // Get signature data
    jsize sig_len = (*env)->GetArrayLength(env, jsignature);
    if (sig_len > signed_len) {
        (*env)->DeleteLocalRef(env, jsignature);
        return -1;
    }

    (*env)->GetByteArrayRegion(env, jsignature, 0, sig_len, (jbyte*)signed_bytes);
    if (stash_pending_exception(env)) {
        (*env)->DeleteLocalRef(env, jsignature);
        return -1;
    }

    (*env)->DeleteLocalRef(env, jsignature);
    return sig_len;
}

// Signer callback function
static intptr_t java_signer_callback(const void *context, const unsigned char *data, uintptr_t len,
                                    unsigned char *signed_bytes, uintptr_t signed_len) {
    JavaSignerContext *jctx = acquire_signer_context(context);
    if (jctx == NULL) {
        return -1;
    }

    JNIEnv *env = get_jni_env();
    intptr_t result = -1;
    if (env != NULL) {
        result = java_signer_callback_invoke(env, jctx, data, len, signed_bytes, signed_len);
    }

    release_signer_context(env, jctx);
    return result;
}

// Progress callback trampoline. The Kotlin side is a Void observer, so this always
// returns 1 (continue) — cancellation is exposed separately via C2PAContext.cancel().
static int java_progress_callback(const void *context, enum C2paProgressPhase phase, uint32_t step, uint32_t total) {
    JavaContextCallback *jctx = acquire_context_callback(context);
    if (jctx == NULL) {
        return 1;
    }

    JNIEnv *env = get_jni_env();
    if (env != NULL) {
        // Bridge signature: onProgress(int phase, long step, long total) -> void
        (*env)->CallVoidMethod(env, jctx->callback, jctx->method, (jint)phase, (jlong)step, (jlong)total);
        // The observer must not affect the operation, so its exceptions are
        // logged and dropped rather than stashed for the outer boundary.
        check_exception(env);
    }

    release_context_callback(env, jctx);
    return 1;
}

// Body of the HTTP resolver callback, run while holding a reference on jctx.
static int java_http_resolver_invoke(JNIEnv *env, JavaContextCallback *jctx,
                                     const struct C2paHttpRequest *request,
                                     struct C2paHttpResponse *response) {
    jstring jurl = (request->url != NULL) ? cstring_to_jstring(env, request->url) : NULL;
    jstring jmethod = (request->method != NULL) ? cstring_to_jstring(env, request->method) : NULL;
    jstring jheaders = (request->headers != NULL) ? cstring_to_jstring(env, request->headers) : NULL;
    jbyteArray jbody = NULL;
    if (request->body != NULL && request->body_len > 0 && request->body_len <= INT32_MAX) {
        jbody = safe_new_byte_array(env, (jsize)request->body_len);
        if (jbody != NULL) {
            (*env)->SetByteArrayRegion(env, jbody, 0, (jsize)request->body_len, (const jbyte*)request->body);
        }
    }

    // Bridge: resolve(String url, String method, String headers, byte[] body) -> HttpResponse
    jobject jresp = (*env)->CallObjectMethod(env, jctx->callback, jctx->method, jurl, jmethod, jheaders, jbody);
    if (jurl != NULL) (*env)->DeleteLocalRef(env, jurl);
    if (jmethod != NULL) (*env)->DeleteLocalRef(env, jmethod);
    if (jheaders != NULL) (*env)->DeleteLocalRef(env, jheaders);
    if (jbody != NULL) (*env)->DeleteLocalRef(env, jbody);

    if (stash_pending_exception(env) || jresp == NULL) {
        c2pa_error_set_last("HTTP resolver callback failed");
        return -1;
    }

    jclass respClass = (*env)->GetObjectClass(env, jresp);
    jmethodID getStatus = (*env)->GetMethodID(env, respClass, "getStatus", "()I");
    jmethodID getBody = (*env)->GetMethodID(env, respClass, "getBody", "()[B");
    (*env)->DeleteLocalRef(env, respClass);
    if (getStatus == NULL || getBody == NULL) {
        (*env)->DeleteLocalRef(env, jresp);
        check_exception(env);
        c2pa_error_set_last("Invalid HttpResponse from resolver");
        return -1;
    }

    jint status = (*env)->CallIntMethod(env, jresp, getStatus);
    jbyteArray respBody = (jbyteArray)(*env)->CallObjectMethod(env, jresp, getBody);
    (*env)->DeleteLocalRef(env, jresp);

    response->status = (int32_t)status;
    response->body = NULL;
    response->body_len = 0;

    if (respBody != NULL) {
        jsize blen = (*env)->GetArrayLength(env, respBody);
        if (blen > 0) {
            unsigned char *buf = (unsigned char*)malloc((size_t)blen);
            if (buf == NULL) {
                (*env)->DeleteLocalRef(env, respBody);
                c2pa_error_set_last("Out of memory copying HTTP response body");
                return -1;
            }
            (*env)->GetByteArrayRegion(env, respBody, 0, blen, (jbyte*)buf);
            response->body = buf;          // Rust takes ownership and frees with free()
            response->body_len = (uintptr_t)blen;
        }
        (*env)->DeleteLocalRef(env, respBody);
    }

    return 0;
}

// HTTP resolver trampoline. Marshals the C request into the Kotlin bridge, reads back
// status + body from the returned HttpResponse, and mallocs the body for Rust to free.
// Returns 0 on success, -1 on error (with c2pa_error_set_last set).
static int java_http_resolver_callback(void *context, const struct C2paHttpRequest *request,
                                       struct C2paHttpResponse *response) {
    JavaContextCallback *jctx = acquire_context_callback(context);
    if (jctx == NULL) {
        c2pa_error_set_last("HTTP resolver is no longer active");
        return -1;
    }

    JNIEnv *env = get_jni_env();
    int result = -1;
    if (env == NULL) {
        c2pa_error_set_last("Failed to attach JNI environment for HTTP resolver");
    } else {
        result = java_http_resolver_invoke(env, jctx, request, response);
    }

    release_context_callback(env, jctx);
    return result;
}

// Native methods implementation

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_C2PA_version(JNIEnv *env, jclass clazz) {
    char *version = c2pa_version();
    jstring result = cstring_to_jstring(env, version);
    c2pa_free(version);
    return result;
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_C2PA_getError(JNIEnv *env, jclass clazz) {
    char *error = c2pa_error();
    jstring result = cstring_to_jstring(env, error);
    c2pa_free(error);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_C2PA_loadSettingsNative(JNIEnv *env, jclass clazz, jstring settings, jstring format) {
    const char *csettings = jstring_to_cstring(env, settings);
    const char *cformat = jstring_to_cstring(env, format);
    
    int result = c2pa_load_settings(csettings, cformat);
    
    release_cstring(env, settings, csettings);
    release_cstring(env, format, cformat);
    
    return result;
}

// Stream native methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Stream_createStreamNative(JNIEnv *env, jobject obj) {
    JavaStreamContext *ctx = (JavaStreamContext*)calloc(1, sizeof(JavaStreamContext));
    if (ctx == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), 
                         "Failed to allocate stream context");
        return 0;
    }
    
    ctx->streamObject = (*env)->NewGlobalRef(env, obj);
    if (ctx->streamObject == NULL) {
        free(ctx);
        check_exception(env);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), 
                         "Failed to create global reference");
        return 0;
    }
    
    // Verify cached method IDs are available
    if (g_streamReadMethod == NULL || g_streamSeekMethod == NULL || 
        g_streamWriteMethod == NULL || g_streamFlushMethod == NULL) {
        (*env)->DeleteGlobalRef(env, ctx->streamObject);
        free(ctx);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), 
                         "Stream method IDs not cached");
        return 0;
    }
    
    struct C2paStream *stream = c2pa_create_stream(
        (struct StreamContext*)ctx,
        java_read_callback,
        java_seek_callback,
        java_write_callback,
        java_flush_callback
    );
    
    if (stream == NULL) {
        (*env)->DeleteGlobalRef(env, ctx->streamObject);
        free(ctx);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), 
                         "Failed to create C2PA stream");
        return 0;
    }
    
    return (jlong)(uintptr_t)stream;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Stream_releaseStreamNative(JNIEnv *env, jobject obj, jlong streamPtr) {
    if (streamPtr != 0) {
        struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
        // Free the Java context
        JavaStreamContext *ctx = (JavaStreamContext*)stream->context;
        if (ctx != NULL) {
            if (ctx->streamObject != NULL) {
                (*env)->DeleteGlobalRef(env, ctx->streamObject);
            }
            free(ctx);
        }
        // Release the stream
        c2pa_release_stream(stream);
    }
}

// Reader native methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Reader_fromStreamNative(JNIEnv *env, jclass clazz, jstring format, jlong streamPtr) {
    clear_stashed_exception(env);
    if (format == NULL || streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Format and stream cannot be null");
        return 0;
    }
    
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return 0;
    }
    
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;

    // Create a reader from a default context, then attach the stream. The
    // context can be released once the reader has been created from it.
    struct C2paContext *ctx = c2pa_context_new();
    struct C2paReader *reader = NULL;
    if (ctx != NULL) {
        struct C2paReader *base = c2pa_reader_from_context(ctx);
        if (base != NULL) {
            // with_stream consumes `base` and returns a new reader.
            reader = c2pa_reader_with_stream(base, cformat, stream);
        }
        c2pa_free(ctx);
    }

    release_cstring(env, format, cformat);

    finish_stashed_exception(env, reader == NULL);
    if (reader == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)reader;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Reader_fromManifestDataAndStreamNative(JNIEnv *env, jclass clazz, jstring format, jlong streamPtr, jbyteArray manifestData) {
    clear_stashed_exception(env);
    if (format == NULL || streamPtr == 0 || manifestData == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Format, stream, and manifest data cannot be null");
        return 0;
    }
    
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return 0;
    }
    
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    
    jsize dataSize = (*env)->GetArrayLength(env, manifestData);
    if (check_exception(env)) {
        release_cstring(env, format, cformat);
        return 0;
    }
    
    if (dataSize <= 0) {
        release_cstring(env, format, cformat);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Manifest data cannot be empty");
        return 0;
    }
    
    jbyte *data = (*env)->GetByteArrayElements(env, manifestData, NULL);
    if (data == NULL) {
        release_cstring(env, format, cformat);
        check_exception(env);
        return 0;
    }
    
    // Create a reader from a default context, then attach the manifest data and
    // stream. The context can be released once the reader has been created from it.
    struct C2paContext *ctx = c2pa_context_new();
    struct C2paReader *reader = NULL;
    if (ctx != NULL) {
        struct C2paReader *base = c2pa_reader_from_context(ctx);
        if (base != NULL) {
            // with_manifest_data_and_stream consumes `base` and returns a new reader.
            reader = c2pa_reader_with_manifest_data_and_stream(
                base, cformat, stream, (const unsigned char*)data, dataSize
            );
        }
        c2pa_free(ctx);
    }

    (*env)->ReleaseByteArrayElements(env, manifestData, data, JNI_ABORT);
    release_cstring(env, format, cformat);

    if (finish_stashed_exception(env, reader == NULL)) {
        return 0;
    }
    return (jlong)(uintptr_t)reader;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Reader_free(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr != 0) {
        c2pa_free((struct C2paReader*)(uintptr_t)readerPtr);
    }
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_Reader_toJsonNative(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), 
                         "Reader is not initialized");
        return NULL;
    }
    
    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    char *json = c2pa_reader_json(reader);
    
    if (json == NULL) {
        return NULL;
    }
    
    jstring result = cstring_to_jstring(env, json);
    c2pa_free(json);
    return result;
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_Reader_toDetailedJsonNative(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), 
                         "Reader is not initialized");
        return NULL;
    }
    
    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    char *json = c2pa_reader_detailed_json(reader);
    
    if (json == NULL) {
        return NULL;
    }
    
    jstring result = cstring_to_jstring(env, json);
    c2pa_free(json);
    return result;
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_Reader_crjsonNative(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
                         "Reader is not initialized");
        return NULL;
    }

    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    char *json = c2pa_reader_crjson(reader);

    if (json == NULL) {
        return NULL;
    }

    jstring result = cstring_to_jstring(env, json);
    c2pa_free(json);
    return result;
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_Reader_remoteUrlNative(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), 
                         "Reader is not initialized");
        return NULL;
    }
    
    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    const char *url = c2pa_reader_remote_url(reader);
    
    if (url == NULL) {
        return NULL;
    }
    
    jstring result = cstring_to_jstring(env, url);
    c2pa_free((char*)url);
    return result;
}

JNIEXPORT jboolean JNICALL Java_org_contentauth_c2pa_Reader_isEmbeddedNative(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), 
                         "Reader is not initialized");
        return JNI_FALSE;
    }
    
    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    return c2pa_reader_is_embedded(reader) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Reader_resourceToStreamNative(JNIEnv *env, jobject obj, jlong readerPtr, jstring uri, jlong streamPtr) {
    clear_stashed_exception(env);
    if (readerPtr == 0 || uri == NULL || streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Reader, URI, and stream cannot be null");
        return -1;
    }
    
    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    const char *curi = jstring_to_cstring(env, uri);
    if (curi == NULL) {
        return -1;
    }
    
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    
    int64_t result = c2pa_reader_resource_to_stream(reader, curi, stream);

    release_cstring(env, uri, curi);

    if (finish_stashed_exception(env, result < 0)) {
        return -1;
    }
    return (jlong)(uintptr_t)result;
}

JNIEXPORT jobjectArray JNICALL Java_org_contentauth_c2pa_Reader_supportedMimeTypesNative(JNIEnv *env, jclass clazz) {
    uintptr_t count = 0;
    const char *const *types = c2pa_reader_supported_mime_types(&count);
    if (types == NULL) {
        return NULL;
    }
    jobjectArray result = cstring_array_to_jarray(env, types, count);
    c2pa_free_string_array(types, count);
    return result;
}

// Builder native methods
JNIEXPORT jobjectArray JNICALL Java_org_contentauth_c2pa_Builder_supportedMimeTypesNative(JNIEnv *env, jclass clazz) {
    uintptr_t count = 0;
    const char *const *types = c2pa_builder_supported_mime_types(&count);
    if (types == NULL) {
        return NULL;
    }
    jobjectArray result = cstring_array_to_jarray(env, types, count);
    c2pa_free_string_array(types, count);
    return result;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Builder_nativeFromArchive(JNIEnv *env, jclass clazz, jlong streamPtr) {
    clear_stashed_exception(env);
    if (streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Stream cannot be null");
        return 0;
    }
    
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;

    // Create a builder from a default context, then attach the archive. The
    // context can be released once the builder has been created from it.
    struct C2paContext *ctx = c2pa_context_new();
    struct C2paBuilder *builder = NULL;
    if (ctx != NULL) {
        struct C2paBuilder *base = c2pa_builder_from_context(ctx);
        if (base != NULL) {
            // with_archive consumes `base` and returns a new builder.
            builder = c2pa_builder_with_archive(base, stream);
        }
        c2pa_free(ctx);
    }

    finish_stashed_exception(env, builder == NULL);
    if (builder == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)builder;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Builder_free(JNIEnv *env, jobject obj, jlong builderPtr) {
    if (builderPtr != 0) {
        c2pa_free((struct C2paBuilder*)(uintptr_t)builderPtr);
    }
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_setIntentNative(JNIEnv *env, jobject obj, jlong builderPtr, jint intent, jint digitalSourceType) {
    if (builderPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), 
                         "Builder is not initialized");
        return -1;
    }
    
    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    return c2pa_builder_set_intent(builder, (enum C2paBuilderIntent)intent, (enum C2paDigitalSourceType)digitalSourceType);
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_addActionNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring actionJson) {
    if (builderPtr == 0 || actionJson == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Builder and action JSON cannot be null");
        return -1;
    }
    
    const char *cactionJson = jstring_to_cstring(env, actionJson);
    if (cactionJson == NULL) {
        return -1;
    }
    
    int result = c2pa_builder_add_action((struct C2paBuilder*)(uintptr_t)builderPtr, cactionJson);
    release_cstring(env, actionJson, cactionJson);
    return result;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Builder_setNoEmbedNative(JNIEnv *env, jobject obj, jlong builderPtr) {
    if (builderPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), 
                         "Builder is not initialized");
        return;
    }
    
    c2pa_builder_set_no_embed((struct C2paBuilder*)(uintptr_t)builderPtr);
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_setRemoteUrlNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring remoteUrl) {
    if (builderPtr == 0 || remoteUrl == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Builder and remote URL cannot be null");
        return -1;
    }
    
    const char *cremoteUrl = jstring_to_cstring(env, remoteUrl);
    if (cremoteUrl == NULL) {
        return -1;
    }
    
    int result = c2pa_builder_set_remote_url((struct C2paBuilder*)(uintptr_t)builderPtr, cremoteUrl);
    release_cstring(env, remoteUrl, cremoteUrl);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_setBasePathNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring basePath) {
    if (builderPtr == 0 || basePath == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and base path cannot be null");
        return -1;
    }

    const char *cbasePath = jstring_to_cstring(env, basePath);
    if (cbasePath == NULL) {
        return -1;
    }

    int result = c2pa_builder_set_base_path((struct C2paBuilder*)(uintptr_t)builderPtr, cbasePath);
    release_cstring(env, basePath, cbasePath);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_addResourceNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring uri, jlong streamPtr) {
    clear_stashed_exception(env);
    const char *curi = jstring_to_cstring(env, uri);
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    int result = c2pa_builder_add_resource((struct C2paBuilder*)(uintptr_t)builderPtr, curi, stream);
    release_cstring(env, uri, curi);
    finish_stashed_exception(env, result < 0);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_addIngredientFromStreamNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring ingredientJson, jstring format, jlong streamPtr) {
    clear_stashed_exception(env);
    const char *cingredientJson = jstring_to_cstring(env, ingredientJson);
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    
    int result = c2pa_builder_add_ingredient_from_stream(
        (struct C2paBuilder*)(uintptr_t)builderPtr, cingredientJson, cformat, stream
    );
    
    release_cstring(env, ingredientJson, cingredientJson);
    release_cstring(env, format, cformat);

    finish_stashed_exception(env, result < 0);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_toArchiveNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong streamPtr) {
    clear_stashed_exception(env);
    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    int result = c2pa_builder_to_archive(builder, stream);
    finish_stashed_exception(env, result < 0);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_addIngredientFromArchiveNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong streamPtr) {
    clear_stashed_exception(env);
    if (builderPtr == 0 || streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and stream cannot be null");
        return -1;
    }

    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    int result = c2pa_builder_add_ingredient_from_archive(builder, stream);
    finish_stashed_exception(env, result < 0);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_writeIngredientArchiveNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring ingredientId, jlong streamPtr) {
    clear_stashed_exception(env);
    if (builderPtr == 0 || ingredientId == NULL || streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder, ingredient id, and stream cannot be null");
        return -1;
    }

    const char *cingredientId = jstring_to_cstring(env, ingredientId);
    if (cingredientId == NULL) {
        return -1;
    }

    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    int result = c2pa_builder_write_ingredient_archive(
        (struct C2paBuilder*)(uintptr_t)builderPtr, cingredientId, stream
    );
    release_cstring(env, ingredientId, cingredientId);
    finish_stashed_exception(env, result < 0);
    return result;
}

// Builds a Builder.SignResult from a sign call's outputs. Frees manifestBytes on every
// path; returns NULL (with the pending exception cleared) if construction fails.
static jobject build_sign_result(JNIEnv *env, int64_t size, const unsigned char *manifestBytes) {
    jclass resultClass = g_signResultClass;
    if (resultClass == NULL) {
        resultClass = (*env)->FindClass(env, "org/contentauth/c2pa/Builder$SignResult");
        if (resultClass == NULL) {
            check_exception(env);
            if (manifestBytes != NULL) {
                c2pa_free(manifestBytes);
            }
            return NULL;
        }
    }

    jmethodID constructor = (*env)->GetMethodID(env, resultClass, "<init>", "(J[B)V");
    if (constructor == NULL) {
        check_exception(env);
        if (manifestBytes != NULL) {
            c2pa_free(manifestBytes);
        }
        return NULL;
    }

    jbyteArray jmanifestBytes = NULL;
    if (manifestBytes != NULL && size > 0) {
        jmanifestBytes = safe_new_byte_array(env, size);
        if (jmanifestBytes == NULL) {
            c2pa_free(manifestBytes);
            return NULL;
        }

        (*env)->SetByteArrayRegion(env, jmanifestBytes, 0, size, (const jbyte*)manifestBytes);
        if (check_exception(env)) {
            c2pa_free(manifestBytes);
            return NULL;
        }
    }
    if (manifestBytes != NULL) {
        c2pa_free(manifestBytes);
    }

    jobject result = (*env)->NewObject(env, resultClass, constructor, (jlong)size, jmanifestBytes);
    if (result == NULL) {
        check_exception(env);
    }

    return result;
}

JNIEXPORT jobject JNICALL Java_org_contentauth_c2pa_Builder_signNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format, jlong sourceStreamPtr, jlong destStreamPtr, jlong signerPtr) {
    clear_stashed_exception(env);
    if (builderPtr == 0 || format == NULL || sourceStreamPtr == 0 || destStreamPtr == 0 || signerPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Builder, format, streams, and signer cannot be null");
        return NULL;
    }
    
    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return NULL;
    }
    
    struct C2paStream *source = (struct C2paStream*)(uintptr_t)sourceStreamPtr;
    struct C2paStream *dest = (struct C2paStream*)(uintptr_t)destStreamPtr;
    struct C2paSigner *signer = (struct C2paSigner*)(uintptr_t)signerPtr;
    
    const unsigned char *manifestBytes = NULL;
    int64_t size = c2pa_builder_sign(builder, cformat, source, dest, signer, &manifestBytes);
    
    release_cstring(env, format, cformat);

    // On failure, surface the app's own exception stashed by a stream/signer
    // callback if there is one; otherwise return NULL and let the Kotlin
    // wrapper raise C2PAError from c2pa_error().
    finish_stashed_exception(env, size < 0);
    if (size < 0) {
        return NULL;
    }

    return build_sign_result(env, size, manifestBytes);
}

JNIEXPORT jobject JNICALL Java_org_contentauth_c2pa_Builder_signWithContextNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format, jlong sourceStreamPtr, jlong destStreamPtr) {
    clear_stashed_exception(env);
    if (builderPtr == 0 || format == NULL || sourceStreamPtr == 0 || destStreamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder, format, and streams cannot be null");
        return NULL;
    }

    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return NULL;
    }

    struct C2paStream *source = (struct C2paStream*)(uintptr_t)sourceStreamPtr;
    struct C2paStream *dest = (struct C2paStream*)(uintptr_t)destStreamPtr;

    // Signer comes from the builder's context (programmatic or from settings).
    const unsigned char *manifestBytes = NULL;
    int64_t size = c2pa_builder_sign_context(builder, cformat, source, dest, &manifestBytes);

    release_cstring(env, format, cformat);

    // On failure, surface the app's own exception stashed by a stream/signer
    // callback if there is one; otherwise return NULL and let the Kotlin
    // wrapper raise C2PAError from c2pa_error().
    finish_stashed_exception(env, size < 0);
    if (size < 0) {
        return NULL;
    }

    return build_sign_result(env, size, manifestBytes);
}

// New Builder methods
JNIEXPORT jbyteArray JNICALL Java_org_contentauth_c2pa_Builder_dataHashedPlaceholderNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong reservedSize, jstring format) {
    if (builderPtr == 0 || format == NULL || reservedSize <= 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Builder, format cannot be null and reserved size must be positive");
        return NULL;
    }
    
    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return NULL;
    }
    
    const unsigned char *manifestBytes = NULL;
    int64_t size = c2pa_builder_data_hashed_placeholder(builder, (uintptr_t)reservedSize, cformat, &manifestBytes);
    
    release_cstring(env, format, cformat);
    
    if (size < 0 || manifestBytes == NULL) {
        return NULL;
    }
    
    jbyteArray result = safe_new_byte_array(env, size);
    if (result == NULL) {
        c2pa_free(manifestBytes);
        return NULL;
    }
    
    (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte*)manifestBytes);
    if (check_exception(env)) {
        c2pa_free(manifestBytes);
        return NULL;
    }
    
    c2pa_free(manifestBytes);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_org_contentauth_c2pa_Builder_signDataHashedEmbeddableNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong signerPtr, jstring dataHash, jstring format, jlong assetPtr) {
    clear_stashed_exception(env);
    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    struct C2paSigner *signer = (struct C2paSigner*)(uintptr_t)signerPtr;
    const char *cdataHash = jstring_to_cstring(env, dataHash);
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *asset = assetPtr != 0 ? (struct C2paStream*)(uintptr_t)assetPtr : NULL;
    const unsigned char *manifestBytes = NULL;
    
    int64_t size = c2pa_builder_sign_data_hashed_embeddable(builder, signer, cdataHash, cformat, asset, &manifestBytes);
    
    release_cstring(env, dataHash, cdataHash);
    release_cstring(env, format, cformat);

    if (finish_stashed_exception(env, size < 0 || manifestBytes == NULL)) {
        if (manifestBytes != NULL) {
            c2pa_free(manifestBytes);
        }
        return NULL;
    }
    if (size < 0 || manifestBytes == NULL) {
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte*)manifestBytes);
    c2pa_free(manifestBytes);

    return result;
}

JNIEXPORT jbyteArray JNICALL Java_org_contentauth_c2pa_Builder_signEmbeddableNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format) {
    clear_stashed_exception(env);
    if (builderPtr == 0 || format == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and format cannot be null");
        return NULL;
    }

    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return NULL;
    }

    const unsigned char *manifestBytes = NULL;
    int64_t size = c2pa_builder_sign_embeddable(builder, cformat, &manifestBytes);
    release_cstring(env, format, cformat);

    if (finish_stashed_exception(env, size < 0 || manifestBytes == NULL)) {
        if (manifestBytes != NULL) {
            c2pa_free(manifestBytes);
        }
        return NULL;
    }
    if (size < 0 || manifestBytes == NULL) {
        return NULL;
    }

    jbyteArray result = safe_new_byte_array(env, size);
    if (result == NULL) {
        c2pa_free(manifestBytes);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte*)manifestBytes);
    if (check_exception(env)) {
        c2pa_free(manifestBytes);
        return NULL;
    }
    c2pa_free(manifestBytes);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_org_contentauth_c2pa_Builder_placeholderNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format) {
    if (builderPtr == 0 || format == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and format cannot be null");
        return NULL;
    }

    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return NULL;
    }

    const unsigned char *manifestBytes = NULL;
    int64_t size = c2pa_builder_placeholder(builder, cformat, &manifestBytes);
    release_cstring(env, format, cformat);

    if (size < 0 || manifestBytes == NULL) {
        return NULL;
    }

    jbyteArray result = safe_new_byte_array(env, size);
    if (result == NULL) {
        c2pa_free(manifestBytes);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte*)manifestBytes);
    if (check_exception(env)) {
        c2pa_free(manifestBytes);
        return NULL;
    }
    c2pa_free(manifestBytes);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_needsPlaceholderNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format) {
    if (builderPtr == 0 || format == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and format cannot be null");
        return -1;
    }

    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return -1;
    }

    int result = c2pa_builder_needs_placeholder((struct C2paBuilder*)(uintptr_t)builderPtr, cformat);
    release_cstring(env, format, cformat);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_setDataHashExclusionsNative(JNIEnv *env, jobject obj, jlong builderPtr, jlongArray exclusions) {
    if (builderPtr == 0 || exclusions == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and exclusions cannot be null");
        return -1;
    }

    jsize len = (*env)->GetArrayLength(env, exclusions);
    if (len % 2 != 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Exclusions must be a flat array of (start, length) pairs");
        return -1;
    }

    jlong *elems = (*env)->GetLongArrayElements(env, exclusions, NULL);
    if (elems == NULL) {
        check_exception(env);
        return -1;
    }

    // jlong and uint64_t are both 64-bit; the bit patterns are identical.
    int result = c2pa_builder_set_data_hash_exclusions(
        (struct C2paBuilder*)(uintptr_t)builderPtr,
        (const uint64_t*)elems,
        (uintptr_t)(len / 2)
    );

    (*env)->ReleaseLongArrayElements(env, exclusions, elems, JNI_ABORT);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_org_contentauth_c2pa_Builder_formatEmbeddableNative(JNIEnv *env, jclass clazz, jstring format, jbyteArray manifestData) {
    if (format == NULL || manifestData == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Format and manifest data cannot be null");
        return NULL;
    }

    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return NULL;
    }

    jsize dataSize = (*env)->GetArrayLength(env, manifestData);
    jbyte *data = (*env)->GetByteArrayElements(env, manifestData, NULL);
    if (data == NULL) {
        release_cstring(env, format, cformat);
        check_exception(env);
        return NULL;
    }

    const unsigned char *resultBytes = NULL;
    int64_t size = c2pa_format_embeddable(cformat, (const unsigned char*)data, (uintptr_t)dataSize, &resultBytes);

    (*env)->ReleaseByteArrayElements(env, manifestData, data, JNI_ABORT);
    release_cstring(env, format, cformat);

    if (size < 0 || resultBytes == NULL) {
        return NULL;
    }

    jbyteArray result = safe_new_byte_array(env, size);
    if (result == NULL) {
        c2pa_free(resultBytes);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte*)resultBytes);
    if (check_exception(env)) {
        c2pa_free(resultBytes);
        return NULL;
    }
    c2pa_free(resultBytes);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_setFixedSizeMerkleNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong fixedSizeKb) {
    if (builderPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder cannot be null");
        return -1;
    }
    return c2pa_builder_set_fixed_size_merkle((struct C2paBuilder*)(uintptr_t)builderPtr, (uintptr_t)fixedSizeKb);
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_hashMdatBytesNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong mdatId, jbyteArray data, jboolean largeSize) {
    if (builderPtr == 0 || data == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and data cannot be null");
        return -1;
    }

    jsize dataLen = (*env)->GetArrayLength(env, data);
    jbyte *dataPtr = (*env)->GetByteArrayElements(env, data, NULL);
    if (dataPtr == NULL) {
        check_exception(env);
        return -1;
    }

    int result = c2pa_builder_hash_mdat_bytes(
        (struct C2paBuilder*)(uintptr_t)builderPtr,
        (uintptr_t)mdatId,
        (const unsigned char*)dataPtr,
        (uintptr_t)dataLen,
        largeSize == JNI_TRUE
    );

    (*env)->ReleaseByteArrayElements(env, data, dataPtr, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_updateHashFromStreamNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format, jlong streamPtr) {
    clear_stashed_exception(env);
    if (builderPtr == 0 || format == NULL || streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder, format, and stream cannot be null");
        return -1;
    }

    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return -1;
    }

    int result = c2pa_builder_update_hash_from_stream(
        (struct C2paBuilder*)(uintptr_t)builderPtr,
        cformat,
        (struct C2paStream*)(uintptr_t)streamPtr
    );

    release_cstring(env, format, cformat);
    finish_stashed_exception(env, result < 0);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_hashTypeNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format) {
    if (builderPtr == 0 || format == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and format cannot be null");
        return -1;
    }

    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return -1;
    }

    enum C2paHashType hashType;
    int result = c2pa_builder_hash_type((struct C2paBuilder*)(uintptr_t)builderPtr, cformat, &hashType);
    release_cstring(env, format, cformat);

    if (result < 0) {
        return -1;
    }
    return (jint)hashType;
}

// Signer native methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Signer_nativeFromSettings(JNIEnv *env, jclass clazz) {
    struct C2paSigner *signer = c2pa_signer_from_settings();
    
    if (signer == NULL) {
        return 0;
    }
    
    return (jlong)(uintptr_t)signer;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Signer_nativeFromInfo(JNIEnv *env, jclass clazz, jstring algorithm, jstring certificatePEM, jstring privateKeyPEM, jstring tsaURL) {
    if (algorithm == NULL || certificatePEM == NULL || privateKeyPEM == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Required parameters cannot be null");
        return 0;
    }
    
    const char *calgorithm = jstring_to_cstring(env, algorithm);
    const char *ccertificatePEM = jstring_to_cstring(env, certificatePEM);
    const char *cprivateKeyPEM = jstring_to_cstring(env, privateKeyPEM);
    const char *ctsaURL = jstring_to_cstring(env, tsaURL);
    
    if (calgorithm == NULL || ccertificatePEM == NULL || cprivateKeyPEM == NULL) {
        release_cstring(env, algorithm, calgorithm);
        release_cstring(env, certificatePEM, ccertificatePEM);
        release_cstring(env, privateKeyPEM, cprivateKeyPEM);
        release_cstring(env, tsaURL, ctsaURL);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Required signer info fields cannot be null");
        return 0;
    }
    
    struct C2paSignerInfo cSignerInfo = {
        .alg = calgorithm,
        .sign_cert = ccertificatePEM,
        .private_key = cprivateKeyPEM,
        .ta_url = ctsaURL
    };
    
    struct C2paSigner *signer = c2pa_signer_from_info(&cSignerInfo);
    
    release_cstring(env, algorithm, calgorithm);
    release_cstring(env, certificatePEM, ccertificatePEM);
    release_cstring(env, privateKeyPEM, cprivateKeyPEM);
    release_cstring(env, tsaURL, ctsaURL);
    
    return (jlong)(uintptr_t)signer;
}

// Register a signer context for tracking
static void register_signer_context(struct C2paSigner *signer, JavaSignerContext *context) {
    SignerContextNode *node = (SignerContextNode*)malloc(sizeof(SignerContextNode));
    if (node != NULL) {
        node->signer = signer;
        node->context = context;
        
        pthread_mutex_lock(&g_signerContextsMutex);
        node->next = g_signerContexts;
        g_signerContexts = node;
        pthread_mutex_unlock(&g_signerContextsMutex);
    }
}

// Unregister all signer contexts associated with a signer (a CAWG combined
// signer may carry more than one after attach_signer_contexts). Each context is
// marked inactive and the registry's reference dropped; a context with an
// in-flight callback stays allocated until that callback releases it.
static void unregister_signer_context(struct C2paSigner *signer) {
    SignerContextNode *toFree = NULL;

    pthread_mutex_lock(&g_signerContextsMutex);
    SignerContextNode **current = &g_signerContexts;
    while (*current != NULL) {
        if ((*current)->signer == signer) {
            SignerContextNode *node = *current;
            *current = node->next;
            JavaSignerContext *ctx = node->context;

            if (ctx != NULL) {
                if (--ctx->refCount == 0) {
                    // Reuse the node to carry the context to the free pass below.
                    node->next = toFree;
                    toFree = node;
                    continue;
                }
            }
            free(node);
            // Continue scanning — all matches are removed.
        } else {
            current = &(*current)->next;
        }
    }
    pthread_mutex_unlock(&g_signerContextsMutex);

    // Free outside the mutex; DeleteGlobalRef needs a JNI environment.
    if (toFree != NULL) {
        JNIEnv *env = get_jni_env();
        while (toFree != NULL) {
            SignerContextNode *next = toFree->next;
            if (env != NULL && toFree->context->callback != NULL) {
                (*env)->DeleteGlobalRef(env, toFree->context->callback);
            }
            free(toFree->context);
            free(toFree);
            toFree = next;
        }
    }
}

// Detach all context nodes keyed by a signer from the registry and return them
// as a chain. Called BEFORE c2pa_identity_signer_create, which frees the input
// signer allocations even on failure: their addresses must not remain registry
// keys, or a concurrent signer allocated at a recycled address would alias them.
static SignerContextNode *detach_signer_contexts(struct C2paSigner *signer) {
    SignerContextNode *detached = NULL;

    pthread_mutex_lock(&g_signerContextsMutex);
    SignerContextNode **current = &g_signerContexts;
    while (*current != NULL) {
        if ((*current)->signer == signer) {
            SignerContextNode *node = *current;
            *current = node->next;
            node->next = detached;
            detached = node;
        } else {
            current = &(*current)->next;
        }
    }
    pthread_mutex_unlock(&g_signerContextsMutex);

    return detached;
}

// Re-insert detached context nodes keyed to the combined signer that now owns
// the consumed inputs' callbacks, so they are freed when it is freed.
static void attach_signer_contexts(SignerContextNode *nodes, struct C2paSigner *signer) {
    if (nodes == NULL) {
        return;
    }
    pthread_mutex_lock(&g_signerContextsMutex);
    while (nodes != NULL) {
        SignerContextNode *next = nodes->next;
        nodes->signer = signer;
        nodes->next = g_signerContexts;
        g_signerContexts = nodes;
        nodes = next;
    }
    pthread_mutex_unlock(&g_signerContextsMutex);
}

// Free detached context nodes whose signers were consumed by a failed combine.
static void free_detached_contexts(JNIEnv *env, SignerContextNode *nodes) {
    while (nodes != NULL) {
        SignerContextNode *next = nodes->next;
        JavaSignerContext *ctx = nodes->context;
        if (ctx != NULL) {
            release_signer_context(env, ctx);
        }
        free(nodes);
        nodes = next;
    }
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Signer_nativeFromCallback(JNIEnv *env, jclass clazz, jstring algorithm, jstring certificateChain, jstring tsaURL, jobject callback) {
    if (algorithm == NULL || certificateChain == NULL || callback == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Required parameters cannot be null");
        return 0;
    }
    
    // Convert algorithm string to enum
    const char *calg = jstring_to_cstring(env, algorithm);
    if (calg == NULL) return 0;
    
    enum C2paSigningAlg alg;
    if (strcmp(calg, "es256") == 0) alg = Es256;
    else if (strcmp(calg, "es384") == 0) alg = Es384;
    else if (strcmp(calg, "es512") == 0) alg = Es512;
    else if (strcmp(calg, "ps256") == 0) alg = Ps256;
    else if (strcmp(calg, "ps384") == 0) alg = Ps384;
    else if (strcmp(calg, "ps512") == 0) alg = Ps512;
    else if (strcmp(calg, "ed25519") == 0) alg = Ed25519;
    else {
        release_cstring(env, algorithm, calg);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Unknown signing algorithm");
        return 0;
    }
    
    release_cstring(env, algorithm, calg);
    
    const char *ccerts = jstring_to_cstring(env, certificateChain);
    const char *ctsaURL = jstring_to_cstring(env, tsaURL);
    
    if (ccerts == NULL) {
        release_cstring(env, tsaURL, ctsaURL);
        return 0;
    }
    
    // Create callback context
    JavaSignerContext *ctx = (JavaSignerContext*)calloc(1, sizeof(JavaSignerContext));
    if (ctx == NULL) {
        release_cstring(env, certificateChain, ccerts);
        release_cstring(env, tsaURL, ctsaURL);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), 
                         "Failed to allocate signer context");
        return 0;
    }
    
    ctx->callback = (*env)->NewGlobalRef(env, callback);
    if (ctx->callback == NULL) {
        free(ctx);
        release_cstring(env, certificateChain, ccerts);
        release_cstring(env, tsaURL, ctsaURL);
        check_exception(env);
        return 0;
    }
    
    // Get the sign method
    jclass callbackClass = (*env)->GetObjectClass(env, callback);
    ctx->signMethod = (*env)->GetMethodID(env, callbackClass, "sign", "([B)[B");
    if (ctx->signMethod == NULL) {
        (*env)->DeleteGlobalRef(env, ctx->callback);
        free(ctx);
        release_cstring(env, certificateChain, ccerts);
        release_cstring(env, tsaURL, ctsaURL);
        check_exception(env);
        return 0;
    }
    
    ctx->refCount = 1;
    ctx->id = next_callback_id();

    // Create the signer. The core receives the opaque id, not the struct pointer.
    struct C2paSigner *signer = c2pa_signer_create((const void*)ctx->id, java_signer_callback, alg, ccerts, ctsaURL);
    
    release_cstring(env, certificateChain, ccerts);
    release_cstring(env, tsaURL, ctsaURL);
    
    if (signer == NULL) {
        (*env)->DeleteGlobalRef(env, ctx->callback);
        free(ctx);
        return 0;
    }
    
    // Register the context for cleanup
    register_signer_context(signer, ctx);
    
    return (jlong)(uintptr_t)signer;
}

// Free the C string array allocated by build_cstring_array. Safe on partially
// built arrays (calloc'd, so unset entries are NULL).
static void release_cstring_array(const char **arr, jsize len) {
    if (arr == NULL) {
        return;
    }
    for (jsize i = 0; i < len; i++) {
        free((void *)arr[i]);
    }
    free((void *)arr);
}

// Convert a String[] into a NULL-terminated array of malloc'd C strings for the
// FFI. Each element is copied and its JNI references released immediately, so no
// local references are held across the FFI call (two near-limit arrays would
// otherwise exceed ART's local reference budget). An empty or NULL input maps to
// NULL out_array (the FFI's "no entries" sentinel). Returns 0 on success; on
// failure throws a Java exception and returns -1.
static int build_cstring_array(JNIEnv *env, jobjectArray jarray, const char ***out_array, jsize *out_len) {
    *out_array = NULL;
    *out_len = 0;
    if (jarray == NULL) {
        return 0;
    }
    jsize len = (*env)->GetArrayLength(env, jarray);
    if (len == 0) {
        return 0;
    }
    const char **arr = (const char **)calloc((size_t)len + 1, sizeof(const char *));
    if (arr == NULL) {
        (*env)->ThrowNew(env,
                         (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                         "Failed to allocate string array");
        return -1;
    }
    for (jsize i = 0; i < len; i++) {
        jstring js = (jstring)(*env)->GetObjectArrayElement(env, jarray, i);
        if (js == NULL) {
            release_cstring_array(arr, len);
            (*env)->ThrowNew(env,
                             (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                             "Array element cannot be null");
            return -1;
        }
        const char *cs = jstring_to_cstring(env, js);
        char *copy = cs != NULL ? strdup(cs) : NULL;
        release_cstring(env, js, cs);
        (*env)->DeleteLocalRef(env, js);
        if (copy == NULL) {
            release_cstring_array(arr, len);
            (*env)->ThrowNew(env,
                             (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                             "Failed to copy array element");
            return -1;
        }
        arr[i] = copy;
    }
    *out_array = arr;
    *out_len = len;
    return 0;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Signer_nativeCombineCawg(JNIEnv *env, jclass clazz, jlong c2paHandle, jlong identityHandle, jobjectArray referencedAssertions, jobjectArray roles) {
    if (c2paHandle == 0 || identityHandle == 0) {
        (*env)->ThrowNew(env,
                         (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Signer handles cannot be zero");
        return 0;
    }
    if (c2paHandle == identityHandle) {
        // The FFI consumes each input; aliasing the same signer would untrack it
        // without freeing, leaking it irrecoverably.
        (*env)->ThrowNew(env,
                         (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "c2pa and identity signers must be distinct");
        return 0;
    }

    const char **refs_arr = NULL;
    jsize refs_len = 0;
    if (build_cstring_array(env, referencedAssertions, &refs_arr, &refs_len) != 0) {
        return 0;
    }

    const char **roles_arr = NULL;
    jsize roles_len = 0;
    if (build_cstring_array(env, roles, &roles_arr, &roles_len) != 0) {
        release_cstring_array(refs_arr, refs_len);
        return 0;
    }

    struct C2paSigner *c2pa_signer = (struct C2paSigner *)(uintptr_t)c2paHandle;
    struct C2paSigner *identity_signer = (struct C2paSigner *)(uintptr_t)identityHandle;

    SignerContextNode *c2pa_contexts = detach_signer_contexts(c2pa_signer);
    SignerContextNode *identity_contexts = detach_signer_contexts(identity_signer);

    struct C2paSigner *combined = c2pa_identity_signer_create(
        c2pa_signer, identity_signer, refs_arr, roles_arr);

    release_cstring_array(refs_arr, refs_len);
    release_cstring_array(roles_arr, roles_len);

    if (combined != NULL) {
        // Re-key input contexts so callback signers' global refs are freed with the combined signer.
        attach_signer_contexts(c2pa_contexts, combined);
        attach_signer_contexts(identity_contexts, combined);
    } else {
        // The FFI consumed and freed the inputs even on failure; their contexts are dead.
        free_detached_contexts(env, c2pa_contexts);
        free_detached_contexts(env, identity_contexts);
    }

    return (jlong)(uintptr_t)combined;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Signer_reserveSizeNative(JNIEnv *env, jobject obj, jlong signerPtr) {
    return c2pa_signer_reserve_size((struct C2paSigner*)(uintptr_t)signerPtr);
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Signer_free(JNIEnv *env, jobject obj, jlong signerPtr) {
    if (signerPtr != 0) {
        struct C2paSigner *signer = (struct C2paSigner*)(uintptr_t)signerPtr;
        
        // Clean up any associated callback context
        unregister_signer_context(signer);
        
        c2pa_free(signer);
    }
}

// C2PASettings native methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_C2PASettings_nativeNew(JNIEnv *env, jclass clazz) {
    struct C2paSettings *settings = c2pa_settings_new();
    if (settings == NULL) {
        return 0;
    }
    return (jlong)(uintptr_t)settings;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_C2PASettings_updateFromStringNative(JNIEnv *env, jobject obj, jlong settingsPtr, jstring settingsStr, jstring format) {
    if (settingsPtr == 0 || settingsStr == NULL || format == NULL) {
        return -1;
    }

    struct C2paSettings *settings = (struct C2paSettings*)(uintptr_t)settingsPtr;
    const char *csettingsStr = jstring_to_cstring(env, settingsStr);
    const char *cformat = jstring_to_cstring(env, format);

    if (csettingsStr == NULL || cformat == NULL) {
        release_cstring(env, settingsStr, csettingsStr);
        release_cstring(env, format, cformat);
        return -1;
    }

    int result = c2pa_settings_update_from_string(settings, csettingsStr, cformat);

    release_cstring(env, settingsStr, csettingsStr);
    release_cstring(env, format, cformat);

    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_C2PASettings_setValueNative(JNIEnv *env, jobject obj, jlong settingsPtr, jstring path, jstring value) {
    if (settingsPtr == 0 || path == NULL || value == NULL) {
        return -1;
    }

    struct C2paSettings *settings = (struct C2paSettings*)(uintptr_t)settingsPtr;
    const char *cpath = jstring_to_cstring(env, path);
    const char *cvalue = jstring_to_cstring(env, value);

    if (cpath == NULL || cvalue == NULL) {
        release_cstring(env, path, cpath);
        release_cstring(env, value, cvalue);
        return -1;
    }

    int result = c2pa_settings_set_value(settings, cpath, cvalue);

    release_cstring(env, path, cpath);
    release_cstring(env, value, cvalue);

    return result;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_C2PASettings_free(JNIEnv *env, jobject obj, jlong settingsPtr) {
    if (settingsPtr != 0) {
        c2pa_free((const void*)(uintptr_t)settingsPtr);
    }
}

// C2PAContext native methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_C2PAContext_nativeNew(JNIEnv *env, jclass clazz) {
    struct C2paContext *context = c2pa_context_new();
    if (context == NULL) {
        return 0;
    }
    return (jlong)(uintptr_t)context;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_C2PAContext_nativeNewWithSettings(JNIEnv *env, jclass clazz, jlong settingsPtr) {
    if (settingsPtr == 0) {
        return 0;
    }

    struct C2paContextBuilder *builder = c2pa_context_builder_new();
    if (builder == NULL) {
        return 0;
    }

    struct C2paSettings *settings = (struct C2paSettings*)(uintptr_t)settingsPtr;
    int result = c2pa_context_builder_set_settings(builder, settings);
    if (result < 0) {
        c2pa_free(builder);
        return 0;
    }

    // build consumes the builder
    struct C2paContext *context = c2pa_context_builder_build(builder);
    if (context == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)context;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_C2PAContext_free(JNIEnv *env, jobject obj, jlong contextPtr) {
    if (contextPtr != 0) {
        c2pa_free((const void*)(uintptr_t)contextPtr);
    }
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_C2PAContext_cancelNative(JNIEnv *env, jobject obj, jlong contextPtr) {
    if (contextPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Context cannot be null");
        return -1;
    }
    return c2pa_context_cancel((struct C2paContext*)(uintptr_t)contextPtr);
}

// Releases a context callback (progress/HTTP-resolver) struct owned by a built context.
// Called from C2PAContext.close() after the context itself has been freed. A callback
// invocation still in flight keeps the struct allocated until it completes.
JNIEXPORT void JNICALL Java_org_contentauth_c2pa_C2PAContext_freeCallbackContextNative(JNIEnv *env, jclass clazz, jlong callbackPtr) {
    if (callbackPtr == 0) {
        return;
    }
    JavaContextCallback *jctx = (JavaContextCallback*)(uintptr_t)callbackPtr;
    unregister_context_callback(jctx);
    release_context_callback(env, jctx);
}

// Context builder methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_C2PAContextBuilder_nativeNew(JNIEnv *env, jclass clazz) {
    struct C2paContextBuilder *builder = c2pa_context_builder_new();
    return (jlong)(uintptr_t)builder;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_C2PAContextBuilder_setSettingsNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong settingsPtr) {
    if (builderPtr == 0 || settingsPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and settings cannot be null");
        return -1;
    }
    return c2pa_context_builder_set_settings(
        (struct C2paContextBuilder*)(uintptr_t)builderPtr,
        (struct C2paSettings*)(uintptr_t)settingsPtr
    );
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_C2PAContextBuilder_setSignerNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong signerPtr) {
    if (builderPtr == 0 || signerPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and signer cannot be null");
        return -1;
    }
    // The FFI consumes the signer; the Kotlin wrapper zeros its pointer on success.
    return c2pa_context_builder_set_signer(
        (struct C2paContextBuilder*)(uintptr_t)builderPtr,
        (struct C2paSigner*)(uintptr_t)signerPtr
    );
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_C2PAContextBuilder_setProgressCallbackNative(JNIEnv *env, jobject obj, jlong builderPtr, jobject bridge) {
    if (builderPtr == 0 || bridge == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and progress callback cannot be null");
        return 0;
    }

    JavaContextCallback *jctx = (JavaContextCallback*)calloc(1, sizeof(JavaContextCallback));
    if (jctx == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                         "Failed to allocate progress callback context");
        return 0;
    }

    jctx->callback = (*env)->NewGlobalRef(env, bridge);
    if (jctx->callback == NULL) {
        free(jctx);
        check_exception(env);
        return 0;
    }

    jclass bridgeClass = (*env)->GetObjectClass(env, bridge);
    jctx->method = (*env)->GetMethodID(env, bridgeClass, "onProgress", "(IJJ)V");
    (*env)->DeleteLocalRef(env, bridgeClass);
    if (jctx->method == NULL) {
        (*env)->DeleteGlobalRef(env, jctx->callback);
        free(jctx);
        check_exception(env);
        return 0;
    }

    jctx->refCount = 1;
    jctx->id = next_callback_id();
    if (!register_context_callback(jctx)) {
        (*env)->DeleteGlobalRef(env, jctx->callback);
        free(jctx);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                         "Failed to register progress callback");
        return 0;
    }

    // The core receives the opaque id, not the struct pointer.
    int result = c2pa_context_builder_set_progress_callback(
        (struct C2paContextBuilder*)(uintptr_t)builderPtr,
        (const void*)jctx->id,
        java_progress_callback
    );
    if (result != 0) {
        unregister_context_callback(jctx);
        (*env)->DeleteGlobalRef(env, jctx->callback);
        free(jctx);
        return 0;
    }

    // Ownership of jctx transfers to the built context (freed in C2PAContext.close()).
    return (jlong)(uintptr_t)jctx;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_C2PAContextBuilder_setHttpResolverNative(JNIEnv *env, jobject obj, jlong builderPtr, jobject bridge) {
    if (builderPtr == 0 || bridge == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and HTTP resolver cannot be null");
        return 0;
    }

    JavaContextCallback *jctx = (JavaContextCallback*)calloc(1, sizeof(JavaContextCallback));
    if (jctx == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                         "Failed to allocate HTTP resolver context");
        return 0;
    }

    jctx->callback = (*env)->NewGlobalRef(env, bridge);
    if (jctx->callback == NULL) {
        free(jctx);
        check_exception(env);
        return 0;
    }

    jclass bridgeClass = (*env)->GetObjectClass(env, bridge);
    jctx->method = (*env)->GetMethodID(env, bridgeClass, "resolve",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)Lorg/contentauth/c2pa/HttpResponse;");
    (*env)->DeleteLocalRef(env, bridgeClass);
    if (jctx->method == NULL) {
        (*env)->DeleteGlobalRef(env, jctx->callback);
        free(jctx);
        check_exception(env);
        return 0;
    }

    jctx->refCount = 1;
    jctx->id = next_callback_id();
    if (!register_context_callback(jctx)) {
        (*env)->DeleteGlobalRef(env, jctx->callback);
        free(jctx);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                         "Failed to register HTTP resolver");
        return 0;
    }

    // The core receives the opaque id, not the struct pointer.
    struct C2paHttpResolver *resolver = c2pa_http_resolver_create((void*)jctx->id, java_http_resolver_callback);
    if (resolver == NULL) {
        unregister_context_callback(jctx);
        (*env)->DeleteGlobalRef(env, jctx->callback);
        free(jctx);
        return 0;
    }

    int result = c2pa_context_builder_set_http_resolver((struct C2paContextBuilder*)(uintptr_t)builderPtr, resolver);
    if (result != 0) {
        // set_http_resolver only consumes the resolver on success; free it on failure.
        c2pa_free(resolver);
        unregister_context_callback(jctx);
        (*env)->DeleteGlobalRef(env, jctx->callback);
        free(jctx);
        return 0;
    }

    // Ownership of jctx transfers to the built context (freed in C2PAContext.close()).
    return (jlong)(uintptr_t)jctx;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_C2PAContextBuilder_buildNative(JNIEnv *env, jobject obj, jlong builderPtr) {
    if (builderPtr == 0) {
        return 0;
    }
    // build consumes the builder regardless of outcome.
    struct C2paContext *context = c2pa_context_builder_build((struct C2paContextBuilder*)(uintptr_t)builderPtr);
    return (jlong)(uintptr_t)context;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_C2PAContextBuilder_free(JNIEnv *env, jobject obj, jlong builderPtr) {
    if (builderPtr != 0) {
        c2pa_free((const void*)(uintptr_t)builderPtr);
    }
}

// Builder context-based methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Builder_nativeFromContext(JNIEnv *env, jclass clazz, jlong contextPtr) {
    if (contextPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Context cannot be null");
        return 0;
    }

    struct C2paContext *context = (struct C2paContext*)(uintptr_t)contextPtr;
    struct C2paBuilder *builder = c2pa_builder_from_context(context);

    if (builder == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)builder;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Builder_withDefinitionNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring manifestJson) {
    if (builderPtr == 0 || manifestJson == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and manifest JSON cannot be null");
        return 0;
    }

    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    const char *cmanifestJson = jstring_to_cstring(env, manifestJson);
    if (cmanifestJson == NULL) {
        return 0;
    }

    // This consumes the old builder pointer
    struct C2paBuilder *newBuilder = c2pa_builder_with_definition(builder, cmanifestJson);
    release_cstring(env, manifestJson, cmanifestJson);

    if (newBuilder == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)newBuilder;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Builder_withArchiveNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong streamPtr) {
    clear_stashed_exception(env);
    if (builderPtr == 0 || streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Builder and stream cannot be null");
        return 0;
    }

    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;

    // This consumes the old builder pointer
    struct C2paBuilder *newBuilder = c2pa_builder_with_archive(builder, stream);

    finish_stashed_exception(env, newBuilder == NULL);
    if (newBuilder == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)newBuilder;
}

// Reader context-based methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Reader_nativeFromContext(JNIEnv *env, jclass clazz, jlong contextPtr) {
    if (contextPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Context cannot be null");
        return 0;
    }

    struct C2paContext *context = (struct C2paContext*)(uintptr_t)contextPtr;
    struct C2paReader *reader = c2pa_reader_from_context(context);

    if (reader == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)reader;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Reader_withStreamNative(JNIEnv *env, jobject obj, jlong readerPtr, jstring format, jlong streamPtr) {
    clear_stashed_exception(env);
    if (readerPtr == 0 || format == NULL || streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Reader, format, and stream cannot be null");
        return 0;
    }

    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return 0;
    }

    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;

    // This consumes the old reader pointer
    struct C2paReader *newReader = c2pa_reader_with_stream(reader, cformat, stream);
    release_cstring(env, format, cformat);

    finish_stashed_exception(env, newReader == NULL);
    if (newReader == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)newReader;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Reader_withFragmentNative(JNIEnv *env, jobject obj, jlong readerPtr, jstring format, jlong streamPtr, jlong fragmentPtr) {
    clear_stashed_exception(env);
    if (readerPtr == 0 || format == NULL || streamPtr == 0 || fragmentPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
                         "Reader, format, stream, and fragment cannot be null");
        return 0;
    }

    struct C2paReader *reader = (struct C2paReader*)(uintptr_t)readerPtr;
    const char *cformat = jstring_to_cstring(env, format);
    if (cformat == NULL) {
        return 0;
    }

    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    struct C2paStream *fragment = (struct C2paStream*)(uintptr_t)fragmentPtr;

    // This consumes the old reader pointer
    struct C2paReader *newReader = c2pa_reader_with_fragment(reader, cformat, stream, fragment);
    release_cstring(env, format, cformat);

    finish_stashed_exception(env, newReader == NULL);
    if (newReader == NULL) {
        return 0;
    }

    return (jlong)(uintptr_t)newReader;
}

// Ed25519 signing
JNIEXPORT jbyteArray JNICALL Java_org_contentauth_c2pa_C2PA_ed25519SignNative(JNIEnv *env, jclass clazz, jbyteArray data, jstring privateKey) {
    if (data == NULL || privateKey == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Data and private key cannot be null");
        return NULL;
    }
    
    jsize dataSize = (*env)->GetArrayLength(env, data);
    if (check_exception(env) || dataSize <= 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Data cannot be empty");
        return NULL;
    }
    
    jbyte *cdata = (*env)->GetByteArrayElements(env, data, NULL);
    if (cdata == NULL) {
        check_exception(env);
        return NULL;
    }
    
    const char *cprivateKey = jstring_to_cstring(env, privateKey);
    if (cprivateKey == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, cdata, JNI_ABORT);
        return NULL;
    }
    
    const unsigned char *signature = c2pa_ed25519_sign((const unsigned char*)cdata, dataSize, cprivateKey);
    
    jbyteArray result = NULL;
    if (signature != NULL) {
        // Ed25519 signatures are always 64 bytes
        result = safe_new_byte_array(env, 64);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, 64, (const jbyte*)signature);
            if (check_exception(env)) {
                result = NULL;
            }
        }
        c2pa_free(signature);
    }
    
    (*env)->ReleaseByteArrayElements(env, data, cdata, JNI_ABORT);
    release_cstring(env, privateKey, cprivateKey);
    
    return result;
}

