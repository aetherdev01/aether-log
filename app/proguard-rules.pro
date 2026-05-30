# ── Repackage & optimasi R8 ───────────────────────────────────────────────────
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-optimizationpasses 5
-optimizations !code/simplification/cast,field/*,class/merging/*,code/allocation/variable

# ── Attributes wajib ─────────────────────────────────────────────────────────
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =============================================================================
# LOGLOG — CORE
# =============================================================================
-keep public class com.aether.lv.MainActivity { <init>(); }
-keep public class com.aether.lv.LogLogApplication { <init>(); }

# =============================================================================
# KOTLIN
# =============================================================================
-keep class kotlin.Metadata { *; }
-keepclassmembernames class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
    public static void parameter*(...);
}

# =============================================================================
# JETPACK COMPOSE
# =============================================================================
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void sourceInformation(...);
    void sourceInformationMarkerStart(...);
    void sourceInformationMarkerEnd(...);
    boolean isTraceInProgress();
    void traceEventStart(...);
    void traceEventEnd();
}

# =============================================================================
# ROOM
# =============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# =============================================================================
# DATASTORE
# =============================================================================
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# =============================================================================
# GSON
# =============================================================================
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# =============================================================================
# PARCELABLE / SERIALIZABLE / ENUM
# =============================================================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final ** name();
    public final int ordinal();
}

# =============================================================================
# SUPPRESS WARNINGS
# =============================================================================
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**

# ─── Unity Ads ───────────────────────────────────────────────────────────────
-keep class com.unity3d.** { *; }
-keep interface com.unity3d.** { *; }
-keep class com.unity3d.services.** { *; }
-keep class com.unity3d.ads.** { *; }
-dontwarn com.unity3d.**

# ─── Native JNI bridge ───────────────────────────────────────────────────────
# Jaga AdsNative agar nama method-nya tidak di-rename ProGuard
# (JNI lookup bergantung pada nama kelas dan method yang exact)
-keepclasseswithmembers class com.aether.lv.ads.AdsNative {
    native <methods>;
    public static <methods>;
}
-keep class com.aether.lv.ads.AdsNative { *; }
