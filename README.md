# WearYTMusic

一个独立运行的 Wear OS YouTube Music 客户端：搜索歌曲、后台播放、无需订阅。

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
- 暂无登录/个人歌单/离线缓存/歌词。
