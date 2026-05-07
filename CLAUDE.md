# CatCatchBrowser - M3U8 嗅探浏览器 + 下载器

## 项目概述

Android 原生应用，集成 WebView 浏览器、M3U8 视频嗅探、视频下载功能。

## 技术栈

- Kotlin
- Android SDK (API 26+, target 35)
- WebView (内置浏览器)
- OkHttp (网络请求/分片下载)
- Kotlin Coroutines (异步并发)
- Gradle Kotlin DSL

## 包名

`top.he2000.catcatchbrowser`

## 项目结构

```
app/src/main/java/top/he2000/catcatchbrowser/
├── ui/               # Activity、Fragment、UI 组件
├── sniffer/          # 嗅探引擎 (JS注入 + 原生拦截)
├── downloader/       # 下载引擎 (M3U8解析 + 分片下载)
└── util/             # 工具类
```

## 核心文件

| 文件 | 说明 |
|------|------|
| ui/BrowserActivity.kt | 浏览器主界面 (WebView + 地址栏) |
| ui/SnifferPanel.kt | 嗅探结果浮窗/底部弹窗 |
| ui/DownloadListActivity.kt | 下载任务列表 |
| ui/DownloadService.kt | 前台服务，通知栏下载进度 |
| sniffer/M3u8Sniffer.kt | 原生层请求拦截嗅探 |
| sniffer/SnifferBridge.kt | JS Bridge，接收嗅探脚本消息 |
| sniffer/sniffer.js | 注入 WebView 的嗅探脚本 |
| downloader/M3u8Parser.kt | M3U8 解析 (master/media playlist) |
| downloader/SegmentDownloader.kt | 分片并发下载 |
| downloader/TaskManager.kt | 下载任务状态管理 |

## 开发环境

| 工具 | 路径/版本 |
|------|-----------|
| Android Studio | D:\Program Files\Android Studio |
| Android SDK | D:\develop\Android\Sdk |
| Android CLI | C:\Users\Administrator\AppData\AndroidCLI (v0.7.15361675) |
| AGP | 8.9.1 |
| Kotlin | 2.0.21 |
| Gradle | 8.11.1 |

### Android CLI 使用

```bash
# 设置环境变量 (bash)
export ANDROID_HOME="D:/develop/Android/Sdk"
export PATH="$PATH:/c/Users/Administrator/AppData/AndroidCLI:$ANDROID_HOME/platform-tools"

# 查看环境信息
android info

# SDK 管理
android sdk list --all
android sdk install platforms/android-35

# 文档搜索
android docs search "WebView JavaScript interface"

# 项目创建
android create empty-activity --name="My App" --output=./my-app
```

## 已安装的 Android Skills

| Skill | 用途 | 适用性 |
|-------|------|--------|
| android-cli | CLI 工具使用指南 | 已使用 |
| android-r8-analyzer | R8/ProGuard 规则分析优化 | 已使用 |
| android-agp-9-upgrade | AGP 9 迁移指南 | 未来升级时使用 |
| android-compose-migration | XML View → Compose 迁移 | 潜在有用 |
| android-edge-to-edge | Edge-to-edge 适配 | 需先迁移 Compose |
| android-camera1-to-camerax | Camera1 → CameraX 迁移 | 不适用 |
| android-navigation-3 | Navigation 3 迁移 | 不适用 |
| android-play-billing-upgrade | Play Billing 升级 | 不适用 |
| android-xr-glimmer | XR/AI 眼镜开发 | 不适用 |

## 开发命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行 Android 测试 (需连接设备)
./gradlew connectedAndroidTest

# 清理构建
./gradlew clean

# 安装到设备
./gradlew installDebug
```

## R8/ProGuard 配置

Release 构建已启用 R8 代码压缩和资源收缩:
- `isMinifyEnabled = true`
- `isShrinkResources = true`
- `android.r8.optimizedResourceShrinking = true` (gradle.properties)

详细分析报告见: [R8_Configuration_Analysis.md](R8_Configuration_Analysis.md)

## 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## 架构设计

```
┌─────────────────────────────────────┐
│           Android APP               │
│                                     │
│  ┌───────────┐    ┌──────────────┐  │
│  │  WebView   │◄──►│  JS Bridge   │  │
│  │  (浏览器)   │    │ (嗅探脚本)    │  │
│  └───────────┘    └──────┬───────┘  │
│                          │ m3u8 URL │
│                   ┌──────▼───────┐  │
│                   │  下载引擎     │  │
│                   │ (解析+分片下载) │  │
│                   └──────┬───────┘  │
│                          │          │
│                   ┌──────▼───────┐  │
│                   │  任务管理     │  │
│                   │ (进度/通知)   │  │
│                   └──────────────┘  │
└─────────────────────────────────────┘
```

## 嗅探机制

双层嗅探，确保可靠捕获：

1. **原生层** — `WebViewClient.shouldInterceptRequest` 拦截所有网络请求
2. **JS 层** — 注入 sniffer.js，拦截 XHR/fetch，通过 `@JavascriptInterface` 回传

## 开发阶段

- Phase 1: WebView 浏览器基础 (地址栏 + 导航)
- Phase 2: 嗅探引擎 (JS注入 + 原生拦截 + Bridge)
- Phase 3: 嗅探结果 UI (浮窗展示 M3U8 链接)
- Phase 4: 下载引擎 (M3U8 解析 + 分片下载 + 进度通知)
- Phase 5: 下载管理 (任务列表 + 暂停/恢复/删除)

## 原型项目参考

嗅探逻辑参考 Python 项目: `d:\workspace\python\cat-catch-assistant`

## 注意事项

- 真机调试需开启 USB 调试
- Android 10+ 使用 Scoped Storage，不要直接写外部存储
- WebView 调试: `WebView.setWebContentsDebuggingEnabled(true)` 可用 Chrome DevTools 远程调试
- 下载使用 Foreground Service + 通知栏，避免被系统杀死
