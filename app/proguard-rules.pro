# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep WhisperEngine Kotlin class and Native callbacks
-keep class fr.schwanx.vox.core.native.** { *; }
-keepclassmembers class fr.schwanx.vox.core.native.** { *; }

# Room keep rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
