# 随听 - Wear OS 音乐播放器

一款为 Wear OS 手表设计的本地音乐播放器，使用 Jetpack Compose for Wear OS + Material3 构建。

## 功能

- 📋 **音乐列表**：自动扫描手表本机存储中的所有音频文件
- 🎵 **播放器页面**：
  - 四周波浪进度条（圆环形，适配圆屏/方屏）
  - 音乐标题 + 艺术家名称
  - 播放/暂停、上一首/下一首控制
  - 音量调节（步进 10%）
  - 歌词显示（点击后全屏滚动，再次点击返回）
- 🎨 Material3 动态配色主题

## 构建

### 本地构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需配置签名）
./gradlew assembleRelease
```

### GitHub Actions 自动构建

本项目使用 GitHub Actions 进行 CI/CD：

| 工作流 | 触发条件 | 说明 |
|--------|---------|------|
| `build.yml` → Debug | push/PR 到 main | 构建调试版，产物保留 14 天 |
| `build.yml` → Release | 推送 `v*` tag | 构建签名版，自动创建 GitHub Release |
| `dependency-check.yml` | 每周一 / 手动 | 检查依赖是否有新版本 |

#### 配置 Release 签名（Secrets）

在仓库的 **Settings → Secrets and variables → Actions** 中添加以下 Secrets：

| Secret 名称 | 说明 |
|------------|------|
| `KEYSTORE_BASE64` | `.jks` 密钥库文件的 Base64 编码 |
| `KEY_STORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

**生成密钥库并编码为 Base64：**

```bash
# 生成密钥库
keytool -genkey -v -keystore suiting-release.jks \
  -alias suiting -keyalg RSA -keysize 2048 -validity 10000

# 编码为 Base64（macOS/Linux）
base64 -i suiting-release.jks | pbcopy   # macOS，直接复制到剪贴板
base64 -i suiting-release.jks            # Linux，复制输出内容
```

将输出的 Base64 字符串粘贴到 `KEYSTORE_BASE64` Secret 中。

#### 发布新版本

```bash
git tag v1.0.0
git push origin v1.0.0
```

推送 tag 后，Actions 会自动构建签名 APK 并在 GitHub Releases 页面发布。

## 安装到手表

```bash
# 通过 ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 如果手表通过 WiFi 连接
adb connect <手表IP>:5555
adb install app-debug.apk
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_AUDIO` (Android 13+) | 读取音频文件 |
| `READ_EXTERNAL_STORAGE` (Android ≤12) | 读取外部存储中的音乐 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 后台持续播放音乐 |
| `WAKE_LOCK` | 防止播放时屏幕息屏中断音频 |

## 项目结构

```
app/src/main/java/com/example/suiting/presentation/
├── MainActivity.kt          # 入口，权限请求，服务绑定，导航
├── MusicItem.kt             # 音乐数据模型
├── MusicRepository.kt       # MediaStore 查询本机音乐
├── MusicService.kt          # 前台服务，MediaPlayer 播放控制
├── MusicListScreen.kt       # 音乐列表页
├── PlayerScreen.kt          # 播放器页（控件 + 歌词视图）
├── WaveProgressIndicator.kt # 波浪圆环进度条组件
└── theme/
    ├── Color.kt
    ├── Type.kt
    └── WearAppTheme.kt
```
