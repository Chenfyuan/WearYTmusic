# WearYTMusic

一个独立运行的 Wear OS YouTube Music 客户端：搜索、后台播放、登录、个人歌单、离线缓存、歌词，无需订阅。

## 功能

- **搜索 / 播放**：YouTube Music 搜索，队列、上/下一首，后台播放与系统媒体控制。
- **登录**：内置 WebView 登录 Google，cookie 加密保存（`EncryptedSharedPreferences`）。
- **个人歌单**：登录后可看“喜欢的音乐”和资料库歌单（自建 InnerTube 客户端，cookie + SAPISIDHASH 签名），可一键播放全部/全部下载。
- **离线**：① 播放过的歌自动缓存（LRU，128 MB）；② 手动下载（WorkManager 分块下载到本地，播放页“下载”/再点一次删除）。
- **歌词**：LRCLIB（免费，无需 key），带时间轴时按播放进度高亮并自动滚动，否则显示纯文本。

- UI：Compose for Wear OS（搜索页 + 播放页，表冠调音量）
- 数据：[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)（搜索 YouTube Music、解析音频流，不使用官方 App/API 密钥）
- 播放：Media3 ExoPlayer + `MediaSessionService`（后台播放、通知栏/系统媒体控制、队列上/下一首）
- 手表需要自己联网（Wi‑Fi / LTE）。音频流码率上限约 160 kbps 以省电省流量。

## 构建 & 安装

需要 JDK 17 与 Android SDK（platform 34）。

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
adb connect <手表IP>:<端口>          # 手表：开发者选项 → 无线调试
adb install app/build/outputs/apk/debug/app-debug.apk
```

联网冒烟测试（搜索 + 解析流）：`./gradlew :app:testDebugUnitTest -PliveTests`

## 已知限制

- YouTube 常改接口，播放失效时先把 `app/build.gradle.kts` 里的 NewPipeExtractor 升到最新版本。
- 该方式不是官方 API，可能违反 YouTube 服务条款，仅供个人使用。
- Google 可能拦截内嵌 WebView 登录（提示“此浏览器或应用不安全”），此时登录会失败。
- 个人歌单解析依据 YouTube Music 网页接口的结构；接口一变需要跟着改 `InnerTube.kt`。
- 歌词来自 LRCLIB 的社区数据，冷门歌可能没有或不准。
- 歌单只取前 300 首；暂不支持专辑/艺人页、点赞、新建歌单。
