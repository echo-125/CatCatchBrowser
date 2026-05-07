# ============================================================
# CatCatchBrowser ProGuard / R8 Rules
# ============================================================
# 分析依据: android-r8-analyzer skill 参考文档
# - REDUNDANT-RULES.md
# - KEEP-RULES-IMPACT-HIERARCHY.md
# - REFLECTION-GUIDE.md
# - CONFIGURATION.md

# --- 通用属性保留 ---
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin Serialization ---
# 项目使用 kotlinx.serialization，需要保留序列化器和 Companion
# 参考: REFLECTION-GUIDE.md - annotation-based reflection
-keepclassmembers class top.he2000.catcatchbrowser.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class top.he2000.catcatchbrowser.sniffer.SnifferBridge$VariantJson {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class top.he2000.catcatchbrowser.**$$serializer { *; }
-keepclassmembers class top.he2000.catcatchbrowser.**$$serializer { *; }

# --- WebView JavaScript Interface ---
# 只保留 @JavascriptInterface 标注的方法，不保留整个类
# 参考: REFLECTION-GUIDE.md - annotation-based reflection
-keepclassmembers class top.he2000.catcatchbrowser.sniffer.SnifferBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# --- OkHttp dontwarn ---
# OkHttp 4.x 已内嵌 ProGuard 规则，无需手动 keep
# 参考: REDUNDANT-RULES.md - 不需要 keep okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Room ---
# Room 通过 KSP 代码生成器自动处理 ProGuard 规则，无需手动 keep
# 参考: REDUNDANT-RULES.md - Room Database section

# --- Kotlin 反射 (仅在实际使用时需要) ---
-dontwarn kotlin.reflect.**

# --- 已移除的冗余规则 ---
# 以下规则已根据 REDUNDANT-RULES.md 移除:
# - -keep class kotlin.Metadata { *; }         → Kotlin 自带规则
# - -keepnames class kotlinx.coroutines...      → 协程库自带规则 (v1.7.0+)
# - -keep class okhttp3.** { *; }              → OkHttp 自带规则 (4.x)
# - -keep class * extends ...RoomDatabase       → Room 自动生成
# - -keep @androidx.room.Entity class *         → Room 自动生成
# - -keep class * extends ...ViewModel { *; }  → AAPT2/R8 自动处理
# - -keep class top.he2000.catcatchbrowser.data.** { *; }  → 包级通配符过宽
# - -keep class top.he2000.catcatchbrowser.ui.** { *; }    → 包级通配符过宽
# - -keep class top.he2000.catcatchbrowser.sniffer.** { *; } → 包级通配符过宽
# - -keep class top.he2000.catcatchbrowser.downloader.** { *; } → 包级通配符过宽
