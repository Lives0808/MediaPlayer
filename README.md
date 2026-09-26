# MediaPlayer

一个功能完整的 Android 本地视频播放器，使用 **Kotlin + Jetpack Compose + Media3 (ExoPlayer)** 实现。

包名 `com.mediaplayer.app`，最低支持 Android 7.0（API 24），目标 Android 15（API 35）。

---

## 下载安装

👉 **[下载最新 APK](https://github.com/Lives0808/MediaPlayer/releases/latest)** ｜ 当前版本 [v1.0.0](https://github.com/Lives0808/MediaPlayer/releases/tag/v1.0.0)

| 版本 | 大小 | SHA256 |
| --- | --- | --- |
| 1.0.0 | 3.0 MB | `72679bff80037fb880040bd35bdfd4ed0471e80b88b3ecc6d77a85ae85676681` |

安装：下载 APK → 允许「安装未知来源应用」→ 安装后授予「访问视频」权限。

---

## 当前状态

- ✅ Release APK 已发布到 [GitHub Releases](https://github.com/Lives0808/MediaPlayer/releases)（已用正式证书签名 + R8 混淆）
- ✅ Android Lint **0 Error**
- ✅ 已在 Android 15（API 35）模拟器上实测通过，见下方「实测结果」

---

## 功能

### 媒体库
- 自动扫描本机所有视频（MediaStore），按「文件夹 / 添加时间 / 名称 / 时长 / 大小」排序，支持正序倒序
- 列表 / 网格两种浏览模式，标题实时搜索，文件夹横向筛选
- 视频缩略图（Coil 抽帧）、时长角标、进度条显示「上次看到哪」
- 长按视频：播放 / 分享 / 详细信息 / 删除（Android 11+ 走系统删除确认）

### 播放页
- **手势控制**
  - 左半屏上下滑动：调节亮度
  - 右半屏上下滑动：调节音量
  - 横向滑动：拖动进度（松手生效，带时间预览）
  - 单击：显示 / 隐藏控制栏；双击左 / 右侧：快退 / 快进 10 秒；双击中间：播放 / 暂停
- **控制栏**：上一集 / 下一集、±10 秒、进度条拖拽、锁定屏幕（防误触）
- **倍速播放**：0.25x ~ 3.0x
- **字幕**：自动识别内嵌 / 外挂字幕轨，可切换或关闭
- **音轨**：多音轨视频可选择语言
- **画面比例**：适应屏幕 / 拉伸铺满 / 裁剪铺满 / 铺满宽度 / 铺满高度
- **画中画 (PiP)**：手动进入，Android 12+ 可开启「按 Home 键自动进入小窗」
- **后台播放**：退到后台继续出声，系统媒体通知支持锁屏控制
- 横竖屏切换、屏幕常亮、全屏沉浸式（刘海屏适配）
- 同一文件夹自动组成播放列表，播完自动下一集

### 其它
- 播放进度记忆，重新打开自动续播，看完自动清零
- 支持从文件管理器等外部应用「打开方式」直接播放
- 支持 HLS / DASH / RTSP 网络流（可打开 m3u8 等链接）
- 设置页：自动横屏、记忆进度、后台播放、自动画中画、默认倍速、默认画面比例、清除播放记录

---

## 实测结果（Android 15 / API 35 模拟器）

| 功能 | 结果 |
| --- | --- |
| 冷启动、权限申请、扫描本地视频 | ✅ 正常 |
| 列表 / 网格切换、缩略图抽帧、时长与分辨率显示 | ✅ 正常 |
| 视频起播、画面渲染（PlayerView + ExoPlayer） | ✅ 正常 |
| 上一集 / 下一集切换、播完自动连播 | ✅ 正常 |
| 双击快进 10 秒 | ✅ 正常 |
| 右侧竖滑调音量（带百分比浮层） | ✅ 正常 |
| 倍速切换（0.5x 实测生效） | ✅ 正常 |
| 进度条拖拽与时间显示 | ✅ 正常 |
| 播完显示「重播」按钮 | ✅ 正常 |
| 后台播放 + 前台服务 + 媒体通知 | ✅ `isForeground=true, types=MEDIA_PLAYBACK`，退到桌面后进度继续增长 |
| 画中画 | ✅ 成功进入小窗并继续播放 |
| 播放进度记忆与续播 | ✅ 播放到 25.2s → 保存 24.8s → 强杀后重开从 24.8s 续播 |
| 返回键 / 沉浸式全屏 / 横屏 | ✅ 正常 |

> 说明：测试用的 15 秒 / 180 秒视频由 `tools/make_test_video.swift` 生成（macOS 自带 AVFoundation，无需 ffmpeg）。

---

## 技术栈

| 用途 | 方案 |
| --- | --- |
| 语言 / UI | Kotlin 2.0.21、Jetpack Compose（Material 3） |
| 播放内核 | Media3 ExoPlayer 1.5.0（含 HLS / DASH / RTSP 扩展） |
| 后台播放 | Media3 `MediaSessionService` + `MediaController` |
| 缩略图 | Coil 2.7.0 + `coil-video`（`MediaMetadataRetriever` 抽帧） |
| 本地存储 | DataStore Preferences |
| 导航 | Navigation Compose |
| 构建 | AGP 8.7.3 / Gradle 8.9 / JDK 17 |

---

## 目录结构

```
app/src/main/java/com/mediaplayer/app/
├── MediaPlayerApp.kt           # Application：全局容器 + Coil 视频帧解码器
├── MainActivity.kt             # 单 Activity + Compose 导航，处理外部「打开方式」
├── Routes.kt                   # 路由定义
├── core/AppContainer.kt        # 极简依赖容器
├── data/
│   ├── VideoItem.kt            # 视频数据模型
│   ├── MediaRepository.kt      # MediaStore 扫描
│   ├── SettingsRepository.kt   # 设置项（DataStore）
│   └── PlaybackPositionStore.kt# 播放进度记录
├── playback/PlaybackService.kt # 前台播放服务（MediaSession）
├── ui/
│   ├── home/                   # 媒体库列表页
│   ├── player/                 # 播放页（手势、控制栏、底部选择面板）
│   ├── settings/               # 设置页
│   └── theme/                  # 主题配色
└── util/                       # 格式化、权限、音量/亮度等工具

tools/
├── generate_icons.py           # 生成启动图标 PNG（纯 Python，无需 Pillow）
└── make_test_video.swift       # 生成测试视频（macOS AVFoundation）
```

---

## 编译与运行

### 本机工具链位置

| 组件 | 路径 |
| --- | --- |
| JDK 17 | `~/android-dev/jdk/Contents/Home` |
| Gradle 8.9 | `~/android-dev/gradle` |
| Android SDK | `~/Library/Android/sdk`（platform 35 / build-tools 35.0.0 / platform-tools） |
| 模拟器 + 系统镜像 | `~/Library/Android/sdk/emulator`、`system-images/android-35/google_apis/arm64-v8a` |

已写好环境脚本，每条新终端先执行一次：

```bash
source ~/android-dev/env.sh
```

### 命令行编译

```bash
cd /Users/levis/orca/projects/bofangqi

./gradlew :app:assembleDebug      # 调试包
./gradlew :app:assembleRelease    # 正式包
./gradlew :app:lintDebug          # 静态检查
```

产物位置：

- 调试包：`app/build/outputs/apk/debug/app-debug.apk`
- 正式包：`app/build/outputs/apk/release/app-release.apk`

### Android Studio

直接 `Open` 本目录即可（版本目录、SDK 版本、镜像源都已配置好）。

### 安装到真机

```bash
source ~/android-dev/env.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 使用模拟器

AVD 已经建好（名称 `MediaPlayer`，Android 15 / arm64）：

```bash
source ~/android-dev/env.sh

# 无窗口启动（省资源，用 adb 操作）
~/Library/Android/sdk/emulator/emulator -avd MediaPlayer -no-snapshot-save -no-audio -no-boot-anim -gpu auto -no-window &

# 等待启动完成
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done

# 安装并启动
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.mediaplayer.app.debug android.permission.READ_MEDIA_VIDEO
adb shell am start -n com.mediaplayer.app.debug/com.mediaplayer.app.MainActivity
```

想要看到图形界面，把 `-no-window` 去掉即可。

生成一个测试视频丢进模拟器：

```bash
swift tools/make_test_video.swift /tmp/demo.mp4 60
adb push /tmp/demo.mp4 /sdcard/Movies/
```

---

## 关于国内网络

`settings.gradle.kts` 已配置 **阿里云 Maven 镜像**优先、官方源兜底；
`gradle-wrapper.properties` 的 Gradle 下载地址指向了 **腾讯云镜像**。
如果你的网络可直连 Google / Maven Central，可以把镜像配置删掉恢复官方源。

---

## 备注

- 界面文案目前直接写在 Compose 代码里（中文），如需多语言建议后续迁移到 `strings.xml`。
- Release 包默认复用 debug 签名，仅为方便安装体验；正式上架请替换 `app/build.gradle.kts` 中的 `signingConfig`。
- `PlaybackService` 设置了 `android:exported="true"`（Media3 要求，用于系统媒体控制 / Android Auto 等外部控制器），Lint 的 `ExportedService` 提示属预期。
