# ============================================================
#  代码混淆规则
# ============================================================

# 通用属性保留
-keepattributes *Annotation*,InnerClasses,Signature,Exceptions,SourceFile,LineNumberTable,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations

# ------------------------------------------------------------
# 应用自身代码：按层收紧
# domain model（序列化 / 反序列化 / Room Entity 需要字段名）
# engine / util / export 层允许混淆
# ------------------------------------------------------------

# Domain models（Room Entity、导航参数、数据类等需要字段名称）
-keep class com.scanner.offline.domain.model.** { *; }
-keep class com.scanner.offline.data.db.** { *; }
-keep class com.scanner.offline.data.storage.** { *; }

# Hilt 注入的 ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }

# UI 层 Composable 需要保留（导航反射）
-keep class com.scanner.offline.ui.** { *; }

# domain usecase / repository 接口（Hilt 需要）
-keep class com.scanner.offline.domain.repository.** { *; }
-keep class com.scanner.offline.domain.usecase.** { *; }
# ------------------------------------------------------------
# Hilt
# ------------------------------------------------------------
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembernames class * {
    @javax.inject.* *;
}

# ------------------------------------------------------------
# Compose
# ------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.tooling.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# ------------------------------------------------------------
# Room
# ------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------
# MLKit
# ------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ------------------------------------------------------------
# OpenCV
# ------------------------------------------------------------
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# ------------------------------------------------------------
# RapidOCR + ONNX Runtime
# ------------------------------------------------------------
-keep class io.github.hzkitty.** { *; }
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ------------------------------------------------------------
# Coil
# ------------------------------------------------------------
-dontwarn coil.**

# ------------------------------------------------------------
# Kotlin / Coroutines
# ------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembernames class kotlinx.coroutines.** { volatile <fields>; }

# ------------------------------------------------------------
# Java desugar 残留
# ------------------------------------------------------------
-dontwarn java.awt.**
-dontwarn javax.xml.**
-dontwarn java.beans.**
