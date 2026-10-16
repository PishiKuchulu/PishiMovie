# ─────────────────────────────────────────────────────────────────────────────
# PishiMovie ProGuard / R8 rules (Phase 7: production release)
# ─────────────────────────────────────────────────────────────────────────────

# Preserve line numbers for crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve signatures + inner classes for reflection-based libraries
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ─── Kotlin serialization ────────────────────────────────────────────────────
# Keep all serializable models + their serializers
-keep class com.pishimovie.data.model.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep @Serializable classes
-keep,allowobfuscation,allowshrinking @kotlinx.serialization.Serializable class *

# ─── ExoPlayer / Media3 ──────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ─── Palette (cinematic color extraction) ────────────────────────────────────
-keep class androidx.palette.** { *; }
-dontwarn androidx.palette.**

# ─── Coil image loading ──────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ─── OkHttp ──────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ─── Compose ─────────────────────────────────────────────────────────────────
# Compose is generally safe under R8; keep the Result class for Kotlin
-keep class kotlin.Result { *; }

# ─── BuildConfig (contains API endpoints — keep field names) ─────────────────
-keep class com.pishimovie.BuildConfig { *; }

# ─── ViewModel ───────────────────────────────────────────────────────────────
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ─── Native methods ──────────────────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─── Enums ───────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Video player activity (keeps Compose setContent working) ────────────────
-keep class com.pishimovie.VideoPlayerActivity { *; }
-keep class com.pishimovie.MainActivity { *; }
