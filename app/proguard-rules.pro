# Add project specific ProGuard rules here.

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep Retrofit service interfaces and DTO signatures. Release builds parse suspend
# Continuation<T> and generic page wrappers reflectively.
-keep class fail.tiger.komgarot.data.remote.dto.** { *; }
-keep interface fail.tiger.komgarot.data.remote.** { *; }
-keep class kotlin.coroutines.Continuation { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-keep class coil.** { *; }
