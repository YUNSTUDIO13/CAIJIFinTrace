# ─── Kotlin Serialization ───
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.example.zengchubao.**$$serializer { *; }
-keepclassmembers class com.example.zengchubao.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.zengchubao.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.zengchubao.model.** {
    <fields>;
}

# ─── Compose ───
-keep class androidx.compose.** { *; }

# ─── Coroutines ───
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
