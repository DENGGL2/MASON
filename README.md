# MASON

**中文** | [English](#english)

MASON 是一个面向 Android 的 AI 助手与工作台。它把 OpenAI 兼容 API、本地模型、手机能力工具、任务自动化和 Codex Remote 远程控制整合在同一个应用中。Remote 已作为内嵌模块进入 MASON，远程入口直接打开配对、会话和对话界面。

MASON 与 OpenAI 没有隶属、授权或背书关系。

## 下载

[**下载最新版 MASON ARM64 Debug APK**](https://github.com/DENGGL2/MASON/releases/download/v0.2.10/MASON-0.2.10-arm64-v8a-debug.apk)

## 界面截图

截图目录已预留，补图时将文件放入 `docs/screenshots/`，并保持下表文件名不变：

| 1. MASON 首页 | 2. Remote 配对 | 3. Remote 会话列表 | 4. 对话与图片预览 | 5. 设置与语言 |
|:--:|:--:|:--:|:--:|:--:|
| ![截图占位：MASON 首页 / MASON home](docs/screenshots/01-home.png) | ![截图占位：Remote 配对 / Remote pairing](docs/screenshots/02-remote-pairing.png) | ![截图占位：Remote 会话 / Remote conversations](docs/screenshots/03-remote-conversations.png) | ![截图占位：图片预览 / Image preview](docs/screenshots/04-image-preview.png) | ![截图占位：设置与语言 / Settings and language](docs/screenshots/05-settings-language.png) |

## 功能分类

### 核心 AI 与模型

- 使用 OpenAI 兼容接口连接云端模型。
- 支持 Gemma/LiteRT、MiniCPM5/llama.cpp 等本地模型路径。
- 支持模型、API 地址、密钥和推理配置管理。
- 对话任务支持流式回复、可恢复执行和明确的工具授权。

### Remote 远程控制

- Remote 作为 MASON 内嵌模块，远程入口直接进入 Remote，而不是跳转到独立应用。
- 通过一次性二维码完成设备配对，支持 Cloudflare Tunnel 与 WebRTC Direct。
- 查看电脑端会话、执行过程、任务结果和连接状态（已连接/已断开）。
- 新建会话、发送消息、停止任务，并处理电脑端发起的权限确认。
- 支持插队或排队发送模式、会话置顶/归档、附件、文件内容和图片预览。
- Remote 的配对、会话、对话、确认和附件协议保持独立；MASON 设置统一承载 Remote 的外观、语言、通知和发送选项。

### 对话与产出

- 以时间顺序展示用户消息、AI 回复和执行活动。
- 支持 Markdown 标题、列表、任务项、引用、代码块、表格和差异内容。
- 支持图片缩略图、大图预览、文件预览、下载、分享和产出物管理。
- 可查看命令详情与输出，保留任务状态和执行耗时。

### 屏幕助手与自动化

- 在用户授权后读取当前屏幕，并执行点击、输入、滚动和系统导航。
- 提供自动化服务和任务通知，用于跟踪长时间运行的操作。
- 权限页面集中展示无障碍、通知和其他系统能力状态。

### 工作台与集成

- Skills ZIP 本地导入与安全校验。
- MCP、A2A 等能力集成入口。
- Collection、工作台和会话交接等本地工作流。
- Room 本地存储、设备身份、outbox 与同步游标基础设施。

### 隐私与安全

- 配对二维码只承载短时有效的签名引导数据。
- 手机保存授权设备关系，不保存一次性二维码令牌或 nonce。
- Cloudflare/WebRTC 的传输选择由用户明确确认，敏感凭据不写入 APK。
- 提交日志或截图前，请移除二维码、配对令牌、TURN 凭据和访问令牌。

## Remote 使用流程

1. 在 Android 手机上安装 MASON。
2. 在 Windows 电脑上准备 Codex Connector，并启动配对命令。
3. 在手机的 Remote 入口选择与电脑相同的 Cloudflare Tunnel 或 WebRTC Direct。
4. 点击开始，扫描桌面端生成的一次性二维码并确认设备信息。
5. 配对成功后，Remote 首页显示连接状态并加载电脑端会话。

Cloudflare Quick Tunnel 不需要域名或令牌，但电脑端重启后需要重新扫描新二维码。WebRTC Direct 使用加密 DataChannel 或 TURN 中继；受限网络环境下建议配置短时 TURN 凭据。

## 项目结构

- `app`：MASON Compose UI、入口、设置、权限和 Android 工具。
- `remote`：内嵌 Remote 的配对、传输、会话、对话、图片预览和通知 UI。
- `llm-client`：OpenAI 兼容客户端与流式解析。
- `tool-runtime`：工具契约、注册表、执行器和结果模型。
- `sync`：Room 数据库、迁移、设备身份、outbox 和同步游标。
- `protocol`：共享 Kotlin Multiplatform 协议模型。
- `codex-connector`：Windows/JVM Codex App Server 桥接。
- `crash-guard`：崩溃与 ANR 捕获。
- `llama-runtime`：Android llama.cpp 运行时。

## 从源码构建

需要 JDK 17、Android SDK、Git 和可用的 Gradle 依赖缓存。仓库包含 Unix Gradle wrapper；Windows 推荐使用 Git Bash：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug -PmasonArm64Only=true --console=plain
```

如果没有 Git Bash，也可以使用 JDK 17 直接运行 wrapper：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = 'C:\path\to\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
& "$env:JAVA_HOME\bin\java.exe" -classpath 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :app:assembleDebug -PmasonArm64Only=true
```

ARM64 APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

发布时会复制为带版本号的文件，例如 `MASON-0.2.10-arm64-v8a-debug.apk`。

## 版本规则

- `versionName` 遵循 `MAJOR.MINOR.PATCH`：不兼容变更递增主版本，兼容的新功能递增次版本，兼容的修复、视觉调整和文档更新递增修订号。
- `versionCode` 是 Android 使用的单调递增整数；每个可分发 APK 递增 1，不回退、不复用。
- 每次发布同步更新 `versionName`、`versionCode`、`v<versionName>` Git 标签、GitHub Release 和 README 下载链接。
- 当前发布版本：`0.2.10`，`versionCode 12`，标签：`v0.2.10`。

## English

MASON is an Android AI assistant and workbench that combines OpenAI-compatible APIs, on-device models, phone capability tools, automation, and Codex Remote control in one app. Remote is embedded as a first-class module, so the Remote entry opens pairing, conversations, and chat directly inside MASON.

MASON is independent and is not affiliated with or endorsed by OpenAI.

### Screenshots

The screenshot slots below are reserved for the next visual capture. Add the images under `docs/screenshots/` with the listed names.

| 1. MASON home | 2. Remote pairing | 3. Remote conversations | 4. Chat and image preview | 5. Settings and language |
|:--:|:--:|:--:|:--:|:--:|
| ![MASON home placeholder](docs/screenshots/01-home.png) | ![Remote pairing placeholder](docs/screenshots/02-remote-pairing.png) | ![Remote conversations placeholder](docs/screenshots/03-remote-conversations.png) | ![Image preview placeholder](docs/screenshots/04-image-preview.png) | ![Settings and language placeholder](docs/screenshots/05-settings-language.png) |

### Download

[**Download the latest MASON ARM64 Debug APK**](https://github.com/DENGGL2/MASON/releases/download/v0.2.10/MASON-0.2.10-arm64-v8a-debug.apk)

### Feature categories

#### AI and models

- Connect to OpenAI-compatible APIs.
- Route requests to Gemma/LiteRT and MiniCPM5/llama.cpp on-device paths.
- Manage providers, API endpoints, keys, models, and reasoning settings.
- Stream replies with resumable task state and explicit tool approval.

#### Embedded Remote control

- Open the embedded Remote experience directly from MASON.
- Pair through a short-lived QR offer using Cloudflare Tunnel or WebRTC Direct.
- View desktop conversations, execution activity, results, and connected/disconnected status.
- Start conversations, send or stop tasks, and handle approval requests.
- Use queue/interruption send modes, pin/archive actions, attachments, file previews, and the repaired image preview flow.
- Control Remote appearance, language, notifications, and send behavior from MASON Settings.

#### Conversations and artifacts

- Render messages and execution activity in chronological order.
- Support Markdown headings, lists, tasks, quotes, code, tables, and diffs.
- Preview, download, share, and manage images, files, and generated artifacts.
- Inspect command output, status, and duration.

#### Screen assistant and automation

- Read the current screen and perform taps, text input, scrolling, and system navigation after user approval.
- Track long-running operations with automation services and task notifications.

#### Workbench and integrations

- Import and validate local Skills ZIP files.
- Provide MCP and A2A capability entry points.
- Include collections, handoff workflows, Room storage, device identity, outbox, and sync cursor foundations.

#### Privacy and security

- Pairing QR codes contain short-lived signed bootstrap data only.
- The phone stores the authorized device relationship, not one-time QR tokens or nonces.
- Transport and sensitive credentials remain explicit and are not packaged into the APK.

### Remote quick start

1. Install MASON on Android.
2. Prepare and start the Codex Connector on Windows.
3. Select the same Cloudflare Tunnel or WebRTC Direct transport in MASON Remote.
4. Scan the one-time QR code and confirm the device information.
5. Use the embedded Remote conversation list and detail view.

### Build

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug -PmasonArm64Only=true --console=plain
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk` and is copied to a versioned asset such as `MASON-0.2.10-arm64-v8a-debug.apk` for a release.

### Versioning

- `versionName` follows `MAJOR.MINOR.PATCH`: use `PATCH` for compatible fixes, visual adjustments, and documentation updates; `MINOR` for compatible user-facing features; and `MAJOR` for incompatible workflow or protocol changes.
- `versionCode` is a monotonically increasing Android integer. Increase it for every distributable APK; never reuse or decrease it.
- Each release updates both values, creates the matching `v<versionName>` tag and GitHub Release, and refreshes the README download links.

Current release: `0.2.10`, `versionCode 12`, tag `v0.2.10`.

Please remove QR codes, pairing tokens, TURN credentials, access tokens, and other secrets before attaching logs or screenshots to GitHub Issues.
