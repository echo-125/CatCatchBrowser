# R8 配置分析报告

分析工具: android-r8-analyzer skill
分析日期: 2026-05-07

## 1. 构建配置

| 配置项 | 值 | 状态 |
|--------|-----|------|
| AGP 版本 | 8.9.1 | OK |
| isMinifyEnabled | true | OK |
| isShrinkResources | true | OK |
| proguard 文件 | proguard-android-optimize.txt | OK (使用优化版本) |
| R8 Full Mode | 默认开启 (AGP 8.0+) | OK |
| optimizedResourceShrinking | true | 已启用 |

## 2. 已移除的冗余规则

| 规则 | 原因 | 来源 |
|------|------|------|
| `-keep class kotlin.Metadata { *; }` | Kotlin 库自带 ProGuard 规则 | REDUNDANT-RULES.md |
| `-keepnames class kotlinx.coroutines...` | 协程库 v1.7.0+ 自带规则 | REDUNDANT-RULES.md |
| `-keep class okhttp3.** { *; }` | OkHttp 4.x 自带规则 | REDUNDANT-RULES.md |
| `-keep class * extends ...RoomDatabase` | Room KSP 自动生成规则 | REDUNDANT-RULES.md |
| `-keep @androidx.room.Entity class *` | Room KSP 自动生成规则 | REDUNDANT-RULES.md |
| `-keep class * extends ...ViewModel { *; }` | AAPT2/R8 自动处理 Android 组件 | REDUNDANT-RULES.md |
| `-keep class top.he2000.catcatchbrowser.data.** { *; }` | 包级通配符过宽 (影响层级 1) | KEEP-RULES-IMPACT-HIERARCHY.md |
| `-keep class top.he2000.catcatchbrowser.ui.** { *; }` | 包级通配符过宽 (影响层级 1) | KEEP-RULES-IMPACT-HIERARCHY.md |
| `-keep class top.he2000.catcatchbrowser.sniffer.** { *; }` | 包级通配符过宽 (影响层级 1) | KEEP-RULES-IMPACT-HIERARCHY.md |
| `-keep class top.he2000.catcatchbrowser.downloader.** { *; }` | 包级通配符过宽 (影响层级 1) | KEEP-RULES-IMPACT-HIERARCHY.md |

## 3. 保留的必要规则

| 规则 | 原因 | 影响层级 |
|------|------|----------|
| Kotlin Serialization serializer/companion | 反射机制需要保留 | 层级 3 |
| @JavascriptInterface 方法 | WebView JS 桥接需要 | 层级 6 |
| OkHttp/OkIO dontwarn | 消除编译警告 | 无 |

## 4. 反射使用情况

项目中未发现以下反射模式:
- Class.forName()
- ::class.java 传递
- getDeclaredField/getDeclaredMethod
- 动态类加载

## 5. 构建验证

Release 构建成功，R8 优化正常工作。
