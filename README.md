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
