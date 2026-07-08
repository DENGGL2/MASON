# Mason

Mason is an Android AI system assistant. It provides a chat interface backed by an OpenAI-compatible API and can expose local Android capabilities as model-callable tools.

## Current Status

- Native Android app written in Kotlin and Jetpack Compose.
- Local conversation storage through Room.
- API settings stored with DataStore.
- Tool runtime contracts shared across modules.
- Android tool implementations live in the app layer.
- Debug APK builds with `assembleDebug`.

## Modules

- `app`: Compose UI, Hilt wiring, Android tool implementations, permissions, and app entry points.
- `llm-client`: OpenAI-compatible chat client and streaming parser.
- `tool-runtime`: Shared tool contracts, tool definitions, registry, executor, and result model.
- `sync`: Room database for conversations and messages.
- `crash-guard`: Crash and ANR capture.
- `build-logic`: Shared Gradle convention plugins.

## Build

The repository currently includes the Unix Gradle wrapper script. On Windows, run the wrapper jar directly:

```powershell
$env:JAVA_HOME='D:\CodexWork\tools\jdk-17'
$env:ANDROID_HOME='D:\CodexWork\tools\android-sdk'
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
