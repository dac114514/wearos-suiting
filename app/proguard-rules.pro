# Wear OS 随听 ProGuard 规则

# Keep Compose entrypoints
-keep class androidx.compose.** { *; }
-keep class androidx.wear.compose.** { *; }

# Keep our app classes
-keep class com.example.suiting.** { *; }

# MediaPlayer
-keep class android.media.MediaPlayer { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn kotlin.**
-dontwarn kotlinx.**
