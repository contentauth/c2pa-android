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

// Signer callback context
typedef struct {
    jobject callback;      // Global reference
    jmethodID signMethod;
    jboolean isActive;     // Track if context is still valid
} JavaSignerContext;

typedef struct SignerContextNode {
    JavaSignerContext *context;
    struct C2paSigner *signer;
    struct SignerContextNode *next;
} SignerContextNode;

static SignerContextNode *g_signerContexts = NULL;
static pthread_mutex_t g_signerContextsMutex = PTHREAD_MUTEX_INITIALIZER;

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
    
    SignerContextNode *current = g_signerContexts;
    while (current != NULL) {
        SignerContextNode *next = current->next;
        JavaSignerContext *ctx = current->context;
        
        if (ctx != NULL) {
            ctx->isActive = JNI_FALSE;
            if (ctx->callback != NULL) {
                (*env)->DeleteGlobalRef(env, ctx->callback);
            }
            free(ctx);
        }
        
        free(current);
        current = next;
    }
    
    g_signerContexts = NULL;
    pthread_mutex_unlock(&g_signerContextsMutex);
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

// Helper to throw an exception with proper error message from C2PA
static void throw_c2pa_exception(JNIEnv *env, const char *defaultMessage) {
    char *error = c2pa_error();
    if (error != NULL && strlen(error) > 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), error);
        c2pa_string_free(error);
    } else {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), defaultMessage);
    }
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

// Stream callbacks
static intptr_t java_read_callback(struct StreamContext *context, uint8_t *data, intptr_t len) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        return -1;
    }
    
    if (len > INT32_MAX) {
        throw_c2pa_exception(env, "Requested buffer too large for JNI");
        return -1;
    }
    
    jbyteArray jdata = safe_new_byte_array(env, (jsize)len);
    if (jdata == NULL) {
        return -1;
    }
    
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, g_streamReadMethod, jdata, (jlong)len);
    if (check_exception(env)) {
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }
    
    if (result > 0 && result <= len) {
        (*env)->GetByteArrayRegion(env, jdata, 0, result, (jbyte*)data);
        if (check_exception(env)) {
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
    if (check_exception(env)) {
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
        throw_c2pa_exception(env, "Requested buffer too large for JNI");
        return -1;
    }
    
    jbyteArray jdata = safe_new_byte_array(env, (jsize)len);
    if (jdata == NULL) {
        return -1;
    }
    
    (*env)->SetByteArrayRegion(env, jdata, 0, len, (const jbyte*)data);
    if (check_exception(env)) {
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }
    
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, g_streamWriteMethod, jdata, (jlong)len);
    if (check_exception(env)) {
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
    if (check_exception(env)) {
        return -1;
    }
    
    return (intptr_t)result;
}

// Signer callback function
static intptr_t java_signer_callback(const void *context, const unsigned char *data, uintptr_t len, 
                                    unsigned char *signed_bytes, uintptr_t signed_len) {
    JavaSignerContext *jctx = (JavaSignerContext*)context;
    
    // Check if context is still valid
    if (!jctx->isActive) {
        return -1;
    }
    
    JNIEnv *env = get_jni_env();
    if (env == NULL) {
        return -1;
    }
    
    // Create byte array from data
    if (len > INT32_MAX) {
        throw_c2pa_exception(env, "Requested buffer too large for JNI");
        return -1;
    }
    
    jbyteArray jdata = safe_new_byte_array(env, (jsize)len);
    if (jdata == NULL) {
        return -1;
    }
    
    (*env)->SetByteArrayRegion(env, jdata, 0, len, (const jbyte*)data);
    if (check_exception(env)) {
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }
    
    // Call the sign method
    jbyteArray jsignature = (jbyteArray)(*env)->CallObjectMethod(env, jctx->callback, jctx->signMethod, jdata);
    (*env)->DeleteLocalRef(env, jdata);
    
    if (check_exception(env)) {
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
    if (check_exception(env)) {
        (*env)->DeleteLocalRef(env, jsignature);
        return -1;
    }
    
    (*env)->DeleteLocalRef(env, jsignature);
    return sig_len;
}

// Native methods implementation

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_C2PA_version(JNIEnv *env, jclass clazz) {
    char *version = c2pa_version();
    jstring result = cstring_to_jstring(env, version);
    c2pa_string_free(version);
    return result;
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_C2PA_getError(JNIEnv *env, jclass clazz) {
    char *error = c2pa_error();
    jstring result = cstring_to_jstring(env, error);
    c2pa_string_free(error);
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

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_C2PA_readFileNative(JNIEnv *env, jclass clazz, jstring path, jstring dataDir) {
    const char *cpath = jstring_to_cstring(env, path);
    const char *cdataDir = jstring_to_cstring(env, dataDir);
    
    char *result = c2pa_read_file(cpath, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    c2pa_string_free(result);
    release_cstring(env, path, cpath);
    release_cstring(env, dataDir, cdataDir);
    
    return jresult;
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_C2PA_readIngredientFileNative(JNIEnv *env, jclass clazz, jstring path, jstring dataDir) {
    const char *cpath = jstring_to_cstring(env, path);
    const char *cdataDir = jstring_to_cstring(env, dataDir);
    
    char *result = c2pa_read_ingredient_file(cpath, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    c2pa_string_free(result);
    release_cstring(env, path, cpath);
    release_cstring(env, dataDir, cdataDir);
    
    return jresult;
}

JNIEXPORT jstring JNICALL Java_org_contentauth_c2pa_C2PA_signFileNative(JNIEnv *env, jclass clazz, jstring sourcePath, jstring destPath, jstring manifest, jstring algorithm, jstring certificatePEM, jstring privateKeyPEM, jstring tsaURL, jstring dataDir) {
    if (sourcePath == NULL || destPath == NULL || manifest == NULL || algorithm == NULL || certificatePEM == NULL || privateKeyPEM == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Required parameters cannot be null");
        return NULL;
    }
    
    const char *csourcePath = jstring_to_cstring(env, sourcePath);
    const char *cdestPath = jstring_to_cstring(env, destPath);
    const char *cmanifest = jstring_to_cstring(env, manifest);
    const char *calgorithm = jstring_to_cstring(env, algorithm);
    const char *ccertificatePEM = jstring_to_cstring(env, certificatePEM);
    const char *cprivateKeyPEM = jstring_to_cstring(env, privateKeyPEM);
    const char *ctsaURL = jstring_to_cstring(env, tsaURL);
    const char *cdataDir = jstring_to_cstring(env, dataDir);
    
    if (csourcePath == NULL || cdestPath == NULL || cmanifest == NULL || 
        calgorithm == NULL || ccertificatePEM == NULL || cprivateKeyPEM == NULL) {
        release_cstring(env, sourcePath, csourcePath);
        release_cstring(env, destPath, cdestPath);
        release_cstring(env, manifest, cmanifest);
        release_cstring(env, algorithm, calgorithm);
        release_cstring(env, certificatePEM, ccertificatePEM);
        release_cstring(env, privateKeyPEM, cprivateKeyPEM);
        release_cstring(env, tsaURL, ctsaURL);
        release_cstring(env, dataDir, cdataDir);
        return NULL;
    }
    
    struct C2paSignerInfo cSignerInfo = {
        .alg = calgorithm,
        .sign_cert = ccertificatePEM,
        .private_key = cprivateKeyPEM,
        .ta_url = ctsaURL
    };
    
    char *result = c2pa_sign_file(csourcePath, cdestPath, cmanifest, &cSignerInfo, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    c2pa_string_free(result);
    release_cstring(env, sourcePath, csourcePath);
    release_cstring(env, destPath, cdestPath);
    release_cstring(env, manifest, cmanifest);
    release_cstring(env, algorithm, calgorithm);
    release_cstring(env, certificatePEM, ccertificatePEM);
    release_cstring(env, privateKeyPEM, cprivateKeyPEM);
    release_cstring(env, tsaURL, ctsaURL);
    release_cstring(env, dataDir, cdataDir);
    
    return jresult;
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
    struct C2paReader *reader = c2pa_reader_from_stream(cformat, stream);
    
    release_cstring(env, format, cformat);
    
    if (reader == NULL) {
        throw_c2pa_exception(env, "Failed to create reader from stream");
        return 0;
    }
    
    return (jlong)(uintptr_t)reader;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Reader_fromManifestDataAndStreamNative(JNIEnv *env, jclass clazz, jstring format, jlong streamPtr, jbyteArray manifestData) {
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
    
    struct C2paReader *reader = c2pa_reader_from_manifest_data_and_stream(
        cformat, stream, (const unsigned char*)data, dataSize
    );
    
    (*env)->ReleaseByteArrayElements(env, manifestData, data, JNI_ABORT);
    release_cstring(env, format, cformat);
    
    return (jlong)(uintptr_t)reader;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Reader_free(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr != 0) {
        c2pa_reader_free((struct C2paReader*)(uintptr_t)readerPtr);
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
        throw_c2pa_exception(env, "Failed to generate JSON from reader");
        return NULL;
    }
    
    jstring result = cstring_to_jstring(env, json);
    c2pa_string_free(json);
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
        throw_c2pa_exception(env, "Failed to generate detailed JSON from reader");
        return NULL;
    }
    
    jstring result = cstring_to_jstring(env, json);
    c2pa_string_free(json);
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
    c2pa_string_free((char*)url);
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
    
    return (jlong)(uintptr_t)result;
}

// Builder native methods
JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Builder_nativeFromJson(JNIEnv *env, jclass clazz, jstring manifestJson) {
    if (manifestJson == NULL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Manifest JSON cannot be null");
        return 0;
    }
    
    const char *cmanifestJson = jstring_to_cstring(env, manifestJson);
    if (cmanifestJson == NULL) {
        return 0;
    }
    
    struct C2paBuilder *builder = c2pa_builder_from_json(cmanifestJson);
    release_cstring(env, manifestJson, cmanifestJson);
    
    if (builder == NULL) {
        throw_c2pa_exception(env, "Failed to create builder from JSON");
        return 0;
    }
    
    return (jlong)(uintptr_t)builder;
}

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Builder_nativeFromArchive(JNIEnv *env, jclass clazz, jlong streamPtr) {
    if (streamPtr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), 
                         "Stream cannot be null");
        return 0;
    }
    
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    struct C2paBuilder *builder = c2pa_builder_from_archive(stream);
    
    if (builder == NULL) {
        throw_c2pa_exception(env, "Failed to create builder from archive");
        return 0;
    }
    
    return (jlong)(uintptr_t)builder;
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Builder_free(JNIEnv *env, jobject obj, jlong builderPtr) {
    if (builderPtr != 0) {
        c2pa_builder_free((struct C2paBuilder*)(uintptr_t)builderPtr);
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

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_addResourceNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring uri, jlong streamPtr) {
    const char *curi = jstring_to_cstring(env, uri);
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    int result = c2pa_builder_add_resource((struct C2paBuilder*)(uintptr_t)builderPtr, curi, stream);
    release_cstring(env, uri, curi);
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_addIngredientFromStreamNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring ingredientJson, jstring format, jlong streamPtr) {
    const char *cingredientJson = jstring_to_cstring(env, ingredientJson);
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    
    int result = c2pa_builder_add_ingredient_from_stream(
        (struct C2paBuilder*)(uintptr_t)builderPtr, cingredientJson, cformat, stream
    );
    
    release_cstring(env, ingredientJson, cingredientJson);
    release_cstring(env, format, cformat);
    
    return result;
}

JNIEXPORT jint JNICALL Java_org_contentauth_c2pa_Builder_toArchiveNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong streamPtr) {
    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    struct C2paStream *stream = (struct C2paStream*)(uintptr_t)streamPtr;
    return c2pa_builder_to_archive(builder, stream);
}

JNIEXPORT jobject JNICALL Java_org_contentauth_c2pa_Builder_signNative(JNIEnv *env, jobject obj, jlong builderPtr, jstring format, jlong sourceStreamPtr, jlong destStreamPtr, jlong signerPtr) {
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
    
    if (size < 0) {
        throw_c2pa_exception(env, "Failed to sign builder");
        return NULL;
    }
    
    // Create result object
    jclass resultClass = g_signResultClass;
    if (resultClass == NULL) {
        resultClass = (*env)->FindClass(env, "org/contentauth/c2pa/Builder$SignResult");
        if (resultClass == NULL) {
            check_exception(env);
            if (manifestBytes != NULL) {
                c2pa_manifest_bytes_free(manifestBytes);
            }
            return NULL;
        }
    }
    
    jmethodID constructor = (*env)->GetMethodID(env, resultClass, "<init>", "(J[B)V");
    if (constructor == NULL) {
        check_exception(env);
        if (manifestBytes != NULL) {
            c2pa_manifest_bytes_free(manifestBytes);
        }
        return NULL;
    }
    
    jbyteArray jmanifestBytes = NULL;
    if (manifestBytes != NULL && size > 0) {
        jmanifestBytes = safe_new_byte_array(env, size);
        if (jmanifestBytes == NULL) {
            c2pa_manifest_bytes_free(manifestBytes);
            return NULL;
        }
        
        (*env)->SetByteArrayRegion(env, jmanifestBytes, 0, size, (const jbyte*)manifestBytes);
        if (check_exception(env)) {
            c2pa_manifest_bytes_free(manifestBytes);
            return NULL;
        }
        
        c2pa_manifest_bytes_free(manifestBytes);
    }
    
    jobject result = (*env)->NewObject(env, resultClass, constructor, (jlong)size, jmanifestBytes);
    if (result == NULL) {
        check_exception(env);
    }
    
    return result;
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
        throw_c2pa_exception(env, "Failed to create data hashed placeholder");
        return NULL;
    }
    
    jbyteArray result = safe_new_byte_array(env, size);
    if (result == NULL) {
        c2pa_manifest_bytes_free(manifestBytes);
        return NULL;
    }
    
    (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte*)manifestBytes);
    if (check_exception(env)) {
        c2pa_manifest_bytes_free(manifestBytes);
        return NULL;
    }
    
    c2pa_manifest_bytes_free(manifestBytes);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_org_contentauth_c2pa_Builder_signDataHashedEmbeddableNative(JNIEnv *env, jobject obj, jlong builderPtr, jlong signerPtr, jstring dataHash, jstring format, jlong assetPtr) {
    struct C2paBuilder *builder = (struct C2paBuilder*)(uintptr_t)builderPtr;
    struct C2paSigner *signer = (struct C2paSigner*)(uintptr_t)signerPtr;
    const char *cdataHash = jstring_to_cstring(env, dataHash);
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *asset = assetPtr != 0 ? (struct C2paStream*)(uintptr_t)assetPtr : NULL;
    const unsigned char *manifestBytes = NULL;
    
    int64_t size = c2pa_builder_sign_data_hashed_embeddable(builder, signer, cdataHash, cformat, asset, &manifestBytes);
    
    release_cstring(env, dataHash, cdataHash);
    release_cstring(env, format, cformat);
    
    if (size < 0 || manifestBytes == NULL) {
        return NULL;
    }
    
    jbyteArray result = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte*)manifestBytes);
    c2pa_manifest_bytes_free(manifestBytes);
    
    return result;
}

// Signer native methods
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

// Unregister and free a signer context
static void unregister_signer_context(struct C2paSigner *signer) {
    pthread_mutex_lock(&g_signerContextsMutex);
    
    SignerContextNode **current = &g_signerContexts;
    while (*current != NULL) {
        if ((*current)->signer == signer) {
            SignerContextNode *toDelete = *current;
            JavaSignerContext *ctx = toDelete->context;
            
            // Mark context as inactive
            if (ctx != NULL) {
                ctx->isActive = JNI_FALSE;
                
                JNIEnv *env = get_jni_env();
                if (env != NULL && ctx->callback != NULL) {
                    (*env)->DeleteGlobalRef(env, ctx->callback);
                }
                free(ctx);
            }
            
            *current = toDelete->next;
            free(toDelete);
            break;
        }
        current = &(*current)->next;
    }
    
    pthread_mutex_unlock(&g_signerContextsMutex);
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
    
    ctx->isActive = JNI_TRUE;
    
    // Create the signer
    struct C2paSigner *signer = c2pa_signer_create(ctx, java_signer_callback, alg, ccerts, ctsaURL);
    
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

JNIEXPORT jlong JNICALL Java_org_contentauth_c2pa_Signer_reserveSizeNative(JNIEnv *env, jobject obj, jlong signerPtr) {
    return c2pa_signer_reserve_size((struct C2paSigner*)(uintptr_t)signerPtr);
}

JNIEXPORT void JNICALL Java_org_contentauth_c2pa_Signer_free(JNIEnv *env, jobject obj, jlong signerPtr) {
    if (signerPtr != 0) {
        struct C2paSigner *signer = (struct C2paSigner*)(uintptr_t)signerPtr;
        
        // Clean up any associated callback context
        unregister_signer_context(signer);
        
        c2pa_signer_free(signer);
    }
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
        c2pa_signature_free(signature);
    }
    
    (*env)->ReleaseByteArrayElements(env, data, cdata, JNI_ABORT);
    release_cstring(env, privateKey, cprivateKey);
    
    return result;
}

