# Mason Architecture Direction

## Product Shape

Mason is an AI assistant for Android devices. The core loop is:

1. The user chats with Mason.
2. The model requests tools when device context or device actions are needed.
3. Mason executes local Android tools behind permission and safety checks.
4. Mason returns a human-readable answer and, when useful, a visible task status notification.

HyperOS Dynamic Island support should enhance this loop by showing task state, progress, and completion. It should not become the main product architecture.

## Module Boundaries

### app

Owns Android-specific behavior:

- Compose screens
- Hilt application wiring
- Android permissions
- concrete tool implementations
- notifications
- future HyperOS Dynamic Island integration

### tool-runtime

Owns only shared tool contracts:

- `Tool`
- `ParameterDef`
- `ToolDefinition`
- `ToolResult`
- `ToolRegistry`
- `ToolExecutor`

It should not compile concrete Android tools. The duplicated concrete tool sources currently remain in the module tree for history, but are excluded from Kotlin compilation. A future cleanup can move them into an archive or remove them one by one.

### llm-client

Owns the OpenAI-compatible API protocol:

- request and response models
- streaming parser
- tool schema serialization
- model error handling

It should not know Android permission details.

### sync

Owns local persistence:

- conversations
- messages
- import/export

### crash-guard

Owns crash and ANR capture.

## Dynamic Island Plan

Mason should take the ordinary-app route first:

1. Build a standard Android notification layer for Mason task states.
2. Add a small `IslandNotifier` abstraction in the app layer.
3. Detect whether Xiaomi HyperOS focus notification / Dynamic Island features are available.
4. Use HyperOS-specific payloads only when supported.
5. Fall back to normal Android notifications everywhere else.

Recommended task states:

- tool running
- waiting for permission
- task completed
- task failed
- long-running task still active

Useful reference projects:

- `1812z/HyperIsland`: feature reference for HyperOS 3 island behavior, but it uses Root/LSPosed/Xposed and should not be Mason's default route.
- `D4vidDf/HyperIsland-ToolKit`: better first reference for standard-app HyperOS Dynamic Island notification payloads.
- `D4vidDf/HyperBridge`: good product reference for notification filtering, app rules, media/navigation/download layouts, and theme ideas.

## Safety Direction

High-risk tools need an explicit confirmation layer before execution:

- shell commands
- file overwrite or delete
- SMS sending
- contacts, SMS, and call-log reads
- system setting changes
- external HTTP requests
- app management actions

The model can suggest these actions, but Mason should keep final control in the app.
