# CatCatchBrowser

M3U8 视频嗅探浏览器 + 下载器，Android 原生应用。

## 功能

- **内置浏览器** — 基于 WebView，支持多窗口、前进/后退、网址导航
- **M3U8 嗅探** — 双层嗅探机制（JS 注入 + 原生请求拦截），自动捕获页面中的 M3U8 视频流
- **视频下载** — 解析 M3U8 playlist，分片并发下载，支持暂停/恢复/删除
- **下载管理** — 前台服务 + 通知栏进度，下载完成后可直接打开播放

## 技术栈

| 组件 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow |
| 导航 | Navigation Compose |
| 网络 | OkHttp |
| 数据库 | Room |
| 异步 | Kotlin Coroutines |
| 图片加载 | Coil |

## 项目结构

```
app/src/main/java/top/he2000/catcatchbrowser/
├── data/                  # 数据模型、DAO
│   ├── Models.kt          # DownloadTaskEntity、SniffedM3u8
│   ├── DownloadTaskDao.kt # Room DAO
│   └── BrowserWindow.kt   # 窗口数据类
├── sniffer/               # 嗅探引擎
│   ├── M3u8Sniffer.kt     # 原生层请求拦截
│   └── SnifferBridge.kt   # JS Bridge 接口
├── downloader/            # 下载引擎
│   ├── M3u8Parser.kt      # M3U8 解析
│   ├── SegmentDownloader.kt # 分片下载
│   ├── TaskManager.kt     # 任务状态管理
│   └── DownloadService.kt # 前台服务
├── viewmodel/
│   └── MainViewModel.kt   # 统一状态管理
└── ui/                    # Compose UI
    ├── MainActivity.kt    # 入口
    ├── MainScreen.kt      # Scaffold + NavHost
    ├── WindowsScreen.kt   # 浏览器页面
    ├── DownloadingScreen.kt
    ├── DownloadedScreen.kt
    ├── components/        # 可复用组件
    ├── navigation/        # 路由 + 底部导航
    └── theme/             # Material 3 主题
```

## 架构

```
┌─────────────────────────────────────────┐
│             Compose UI                  │
│  ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │ Windows  │ │Downloading│ │Downloaded│ │
│  └────┬─────┘ └────┬─────┘ └────┬────┘ │
│       └──────┬─────┘            │       │
│         MainViewModel (StateFlow)       │
├─────────────────────────────────────────┤
│         WebView ──► JS Bridge           │
│              │                           │
│         M3u8Sniffer (拦截)               │
│              │ m3u8 URL                  │
│         M3u8Parser ─► SegmentDownloader  │
│              │                           │
│         TaskManager ─► Room DB           │
│         DownloadService (前台服务)        │
└─────────────────────────────────────────┘
```

## 嗅探机制

1. **JS 层** — 注入 `sniffer.js`，拦截 XHR/fetch 请求，匹配 `.m3u8` 后通过 `@JavascriptInterface` 回传
2. **原生层** — `WebViewClient.shouldInterceptRequest` 拦截所有网络请求，过滤 M3U8 链接

## 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (启用 R8 混淆和资源收缩)
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

## 环境要求

- Android Studio Hedgehog+
- Android SDK 35
- JDK 11+
- 最低 Android 版本：8.0 (API 26)

## 权限

| 权限 | 用途 |
|------|------|
| INTERNET | 网络访问 |
| FOREGROUND_SERVICE | 下载前台服务 |
| POST_NOTIFICATIONS | 下载进度通知 |
| WRITE_EXTERNAL_STORAGE | 存储下载文件 (API 28-) |

## License

MIT
