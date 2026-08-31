# Mason

Mason is an Android AI system assistant. It provides a chat interface backed by an OpenAI-compatible API and can expose local Android capabilities as model-callable tools.

## Current Status

- Native Android app written in Kotlin and Jetpack Compose.
- Local conversation storage through Room.
- OpenAI-compatible providers and on-device Gemma/LiteRT and MiniCPM5/llama.cpp routing.
- Resumable task state, explicit tool approval, secure local Skills ZIP import, and conversation handoff.
- Android Room v2 identity, outbox, and cursor foundations for local-first multi-device sync.
- Windows Codex Connector and shared protocol foundations.
- Debug APK builds for `arm64-v8a` and `x86_64` with `assembleDebug`.

## Modules

- `app`: Compose UI, Hilt wiring, Android tool implementations, permissions, and app entry points.
- `llm-client`: OpenAI-compatible chat client and streaming parser.
- `tool-runtime`: Shared tool contracts, tool definitions, registry, executor, and result model.
- `sync`: Room database, migrations, device identity, outbox, and sync cursors.
- `crash-guard`: Crash and ANR capture.
- `protocol`: Shared Kotlin Multiplatform models and protocol envelopes.
- `codex-connector`: Windows/JVM bridge for managed Codex App Server sessions.

## Remote pairing

After deploying the Windows Connector, run `codex-connector.bat pair`. The
Connector asks whether to use Cloudflare Tunnel or WebRTC Direct, starts the
selected transport, generates a short-lived QR code, and opens the QR image.
The mobile app must select the same transport before scanning.

The intended end-to-end flow is:

1. Install the `cloud code` APK from the companion mobile project.
2. Run `codex-connector.bat pair` on Windows.
3. Choose Cloudflare Tunnel or WebRTC Direct in the Agent.
4. Choose the same mode in the APK, tap `开始`, scan the displayed QR code,
   and confirm pairing.

Cloudflare Quick Tunnel is the no-domain/no-token test path, but requires
`cloudflared.exe` on the Windows machine and its URL changes after restart.
WebRTC requires an HTTPS signaling endpoint. In both modes the QR offer is
short-lived and signed; the phone stores the authorized device relationship,
not the one-time QR token.

The direct commands remain available for automation:

- `pair-cloudflare <port> <qr-output.png> [state-directory] [cloudflared-path]`
- `pair-webrtc <port> <qr-output.png> <signaling-endpoint> [state-directory]`
- `llama-runtime`: Android llama.cpp runtime used by MiniCPM5 GGUF models.
- `build-logic`: Shared Gradle convention plugins.

## Build

Clone with submodules so the pinned llama.cpp runtime is available:

```text
git clone --recurse-submodules https://github.com/DENGGL2/MASON.git
```

If the repository was cloned without submodules, initialize them before building:

```text
git submodule update --init --recursive
```

The repository currently includes the Unix Gradle wrapper script. On Windows, point the environment variables at a JDK 17 installation and Android SDK, then run the wrapper jar directly:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
$env:ANDROID_HOME='C:\path\to\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
& "$env:JAVA_HOME\bin\java.exe" -classpath 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Direction

Mason should stay an AI phone assistant first. HyperOS Dynamic Island support should be added as a notification/status layer for Mason tasks, not as a root/Xposed system enhancement module.

See `docs/architecture.md` for the current architecture direction.
