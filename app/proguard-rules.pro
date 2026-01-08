# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

# Keep MediaPipe classes
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }

# Keep Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Keep all DAO methods
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract **Dao();
}

# Keep SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }

# Keep data model classes (entities)
-keep class com.muxrotechnologies.muxroattendance.data.entity.** { *; }
-keepclassmembers class com.muxrotechnologies.muxroattendance.data.entity.** { *; }

# Keep ML classes
-keep class com.muxrotechnologies.muxroattendance.ml.** { *; }
-keepclassmembers class com.muxrotechnologies.muxroattendance.ml.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Compose
-keep class androidx.compose.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep security utils (needed for reflection)
-keep class com.muxrotechnologies.muxroattendance.utils.SecurityUtil { *; }
-keep class com.muxrotechnologies.muxroattendance.utils.EncryptionUtil { *; }
-keep class com.muxrotechnologies.muxroattendance.utils.KeystoreManager { *; }

# CameraX
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# Prevent optimization of application class
-keep class com.muxrotechnologies.muxroattendance.AttendanceApplication { *; }

# Keep sealed classes
-keep class * extends com.muxrotechnologies.muxroattendance.ml.MatchResult { *; }
-keep class * extends com.muxrotechnologies.muxroattendance.ml.EnrollmentResult { *; }
-keep class * extends com.muxrotechnologies.muxroattendance.ml.AttendanceResult { *; }
-keep class * extends com.muxrotechnologies.muxroattendance.utils.Result { *; }