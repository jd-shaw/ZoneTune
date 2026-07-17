# 听域 ZoneTune

Android 音乐播放器（Kotlin + Jetpack Compose）。仅播放音频，不支持视频。

> 个人学习项目，非商业用途。业务逻辑参考 [wood3n/biu](https://github.com/wood3n/biu)，独立实现。

## 当前进度（MVP 骨架）

已完成：

- [x] Android 工程骨架（AGP 8.7 / Kotlin 2.0 / Compose）
- [x] Cookie 持久化（DataStore）+ OkHttp CookieJar
- [x] WBI 签名（对照 biu）
- [x] 搜索 / 曲目详情 / 音频取流 API
- [x] 扫码登录（二维码 + 轮询）
- [x] ExoPlayer 播放（带 Referer）
- [x] 底部迷你播放条 + 前台服务占位
- [x] 清爽工作室 UI 壳（发现 / 搜索 / 我的 + 全屏播放）
- [x] 播放模式（顺序 / 随机 / 单曲循环）
- [x] 本地收藏

后续：

- [ ] Gaia / 极验人机验证自动拦截
- [ ] MediaSession 通知栏完整播控
- [ ] 播放队列独立页面
- [ ] 最近播放

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | **21**（或 17+） |
| Android Studio | 最新稳定版（推荐） |
| Android SDK | Platform **35** + Build-Tools |
| 真机 | Android 8.0+（API 26+） |

本机已检测到 JDK 21：

```bash
/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java -version
```

建议在 `~/.zshrc` 中固定：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## 第一次运行

1. 安装 [Android Studio](https://developer.android.com/studio)，打开本目录
2. 按提示安装 SDK Platform 35
3. 连接手机并开启 USB 调试
4. 点击 Run，或终端：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
./gradlew :app:installDebug
```

若尚无 `local.properties`，从示例复制并改路径：

```bash
cp local.properties.example local.properties
# 编辑 sdk.dir=...
```

## 包名

- applicationId: `com.shaw.zonetune`
- 显示名: ZoneTune

## 项目结构

```text
app/src/main/java/com/shaw/zonetune/
├── data/
│   ├── api/          # BiliClient / WbiSigner / BiliRepository
│   ├── cookie/       # CookieStore + PersistentCookieJar
│   └── model/        # 数据模型
├── player/           # ExoPlayer + PlaybackService
└── ui/
    ├── discover/     # 发现页
    ├── search/       # 搜索页
    ├── mine/         # 我的（账号 + 收藏）
    ├── login/        # 扫码登录
    ├── player/       # 迷你条 + 全屏播放 + 播放模式
    ├── navigation/   # 底栏 Tab
    ├── components/   # 工作室组件
    └── theme/
```

本地收藏保存在 DataStore。

## 许可证说明

参考 biu 的 PolyForm Noncommercial 精神：**仅供学习与个人使用，禁止商业用途**。
使用时请遵守相关服务条款与当地法律法规。
