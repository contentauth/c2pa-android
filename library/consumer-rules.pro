# Consumer ProGuard rules for C2PA Android library
-keep class org.contentauth.c2pa.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep callback interface (used by JNI)
-keep interface org.contentauth.c2pa.SignCallback { *; }

# Java 17 compatibility
-dontwarn java.lang.invoke.StringConcatFactory

# kotlinx.serialization (used internally for JSON parsing)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.contentauth.c2pa.**$$serializer { *; }
-keepclassmembers class org.contentauth.c2pa.** {
    *** Companion;
}

# BouncyCastle (used internally for CSR generation)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# OkHttp (used internally for web service signing)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }