# MASON 多端统一对话 V1 技术规格

状态：Draft 0.11

日期：2026-07-30

适用范围：Android MASON、Windows MASON Connector、本地 Codex App Server

## 1. 目标

MASON 将从 Android 单端应用演进为全平台、local-first 的统一 AI 工作空间。

V1 的交付目标是：

1. Android 与 Windows 使用同一套对话和项目身份。
2. Windows 电脑作为个人主节点，保存共享对话的权威事件流，并执行本地 Codex 任务。
3. Android 通过私有组网查看和继续电脑端对话，处理审批、停止任务并接收指定文件。
4. 项目文件默认保留在各设备本地；跨端只同步项目身份、副本元数据、对话和执行状态。
5. 用户可创建仅保留在当前设备的私密对话。

V1 不建设 MASON 云服务，不依赖 OpenAI Remote 的中继链路。

## 2. 已确认的产品决策

### 2.1 统一对象，设备本地执行

- 对话属于 MASON 用户，而不是某台设备。
- 项目使用跨端统一的 `LogicalProject` 身份。
- 每台设备上的项目目录是独立的 `ProjectReplica`。
- 一次执行始终绑定一个具体设备和项目副本。
- 同一对话同一时间只允许一个活动执行端。

### 2.2 三种对话同步模式

| 模式 | 行为 |
| --- | --- |
| `SHARED` | 对话和任务状态同步到已配对设备，默认模式 |
| `LOCAL_ONLY` | 对话、附件和项目绑定不离开当前设备 |
| `REMOTE_MIRROR` | 映射外部 Codex 会话；历史可同步，实时能力取决于 Connector 是否拥有该会话 |

同步模式是对话设置，不控制项目文件。项目文件传送和项目迁移使用独立功能。

### 2.3 Codex 会话能力分级

| 会话来源 | V1 能力 |
| --- | --- |
| MASON Connector 创建或恢复 | 实时消息、计划、命令、Diff、审批、继续、插话、停止 |
| 官方 Codex 桌面应用已保存会话 | 列表和完整历史读取 |
| 官方 Codex 桌面应用当前活动会话 | V1 仅轮询已落盘历史，不承诺实时接管 |

原因：不同 Codex App Server 进程可以读取共享持久历史，但运行状态和事件订阅是进程内的。

## 3. V1 非目标

以下能力不进入 V1：

- 自动同步完整项目目录。
- 任意任务在设备之间无缝迁移。
- 多设备并行修改同一 Codex 回合。
- A2A 调度或第三方 Agent 编排。
- 直接暴露 Codex App Server 的实验性 WebSocket 监听。
- 解析 Codex 内部 JSONL 或 SQLite 作为正式集成协议。
- 同步原始隐藏推理内容。
- Web、macOS 和 iOS 完整客户端交付。

## 4. 部署拓扑

```text
Codex App Server
  | stdio JSONL, localhost only
  v
Windows MASON Connector (personal authority node)
  |- Codex protocol adapter
  |- shared conversation event store
  |- project replica registry
  |- approval broker
  |- file transfer service
  |
  | private mesh + application authentication
  v
Android MASON
  |- local Room cache
  |- outbox
  |- native conversation/project UI
  |- optional foreground sync service
```

私有组网只负责设备可达和链路加密。MASON 仍执行应用层配对、身份校验、授权和重放防护。

## 5. 目标模块

V1 建议逐步形成以下模块；名称为目标边界，不要求一次完成全部 Gradle 调整。

### 5.1 `:protocol`

纯 Kotlin、无 Android 依赖，可演进为 Kotlin Multiplatform：

- 全局 ID 和协议版本
- 领域 DTO
- 命令与事件封装
- 序列化
- 协议错误模型

### 5.2 `:sync-core`

纯 Kotlin 同步状态机：

- cursor 和幂等去重
- outbox
- 重连和补拉
- 执行租约
- 冲突策略

### 5.3 现有 `:sync`

V1 继续作为 Android Room 适配层：

- 本地会话缓存
- 消息事件投影
- 旧数据迁移
- 导入导出兼容

长期应避免让 Room Entity 直接成为跨端协议模型。

### 5.4 `:codex-connector`

Windows Kotlin/JVM 后台进程：

- 启动并监控 Codex App Server
- 本地 stdio JSON-RPC 客户端
- Codex 事件归一化
- 主节点 WebSocket/HTTPS 服务
- 设备配对和授权
- 文件传送

### 5.5 `:app`

保留 Android UI、权限和后台服务：

- 统一对话和项目界面
- 设备/执行位置选择
- 审批 UI
- 文件接收
- 后台连接策略

长期桌面 UI 可使用 Compose Multiplatform，但 V1 的 Windows 端先交付 Connector，不要求重做完整桌面界面。

## 6. 核心数据模型

所有跨端实体使用 UUIDv7 或 ULID。时间使用 UTC epoch milliseconds。

### 6.1 OwnerProfile

V1 是单所有者个人空间，不建设在线账号和多用户系统。Windows 主节点首次启动时创建 owner 身份，配对设备加入该 owner 的个人空间。

```kotlin
data class OwnerProfile(
    val id: String,
    val displayName: String,
    val createdAt: Long,
)
```

owner ID 是本地个人空间标识，不是 OpenAI 账号 ID。配对证明设备属于该个人空间；未来接入云账号时通过显式迁移关联，不能静默替换。

### 6.2 Device

```kotlin
data class Device(
    val id: String,
    val displayName: String,
    val platform: Platform,
    val publicKey: String,
    val capabilities: Set<DeviceCapability>,
    val lastSeenAt: Long?,
    val revokedAt: Long?,
)
```

V1 能力至少包括 `CODEX_EXECUTION`、`ANDROID_TOOLS`、`FILE_SEND` 和 `FILE_RECEIVE`。

### 6.3 LogicalProject

```kotlin
data class LogicalProject(
    val id: String,
    val displayName: String,
    val repositoryIdentity: String?,
    val createdAt: Long,
)
```

`repositoryIdentity` 优先使用规范化 remote URL；无 Git 项目可以为空。

### 6.4 ProjectReplica

```kotlin
data class ProjectReplica(
    val id: String,
    val projectId: String,
    val deviceId: String,
    val rootPath: String,
    val gitRemote: String?,
    val branch: String?,
    val commitSha: String?,
    val dirty: Boolean?,
    val availability: ReplicaAvailability,
    val lastObservedAt: Long,
)
```

路径只在拥有该副本的设备上具有执行意义。其他设备只能显示路径和请求远程操作。

### 6.5 Conversation

```kotlin
data class Conversation(
    val id: String,
    val title: String,
    val syncMode: ConversationSyncMode,
    val projectId: String?,
    val selectedReplicaId: String?,
    val authorityDeviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

### 6.6 CodexThreadBinding

```kotlin
data class CodexThreadBinding(
    val conversationId: String,
    val deviceId: String,
    val codexThreadId: String,
    val cwd: String?,
    val ownership: CodexOwnership,
    val protocolVersion: String,
)
```

`ownership` 为 `MASON_MANAGED` 或 `EXTERNAL_HISTORY_ONLY`。

### 6.7 ConversationEvent

```kotlin
data class ConversationEvent(
    val protocolVersion: Int,
    val eventId: String,
    val conversationId: String,
    val sourceDeviceId: String,
    val sequence: Long,
    val occurredAt: Long,
    val type: ConversationEventType,
    val payload: JsonObject,
)
```

主节点为每个共享对话分配单调递增 `sequence`。`eventId` 用于跨重连幂等去重。

### 6.8 ExecutionSession

```kotlin
data class ExecutionSession(
    val id: String,
    val conversationId: String,
    val executorDeviceId: String,
    val replicaId: String?,
    val externalThreadId: String?,
    val status: ExecutionStatus,
    val leaseExpiresAt: Long,
    val startedAt: Long,
    val finishedAt: Long?,
)
```

一个对话最多有一个未结束的执行租约。租约只防止并发执行，不代表后台任务自动拥有高风险权限。

### 6.9 ApprovalRequest

```kotlin
data class ApprovalRequest(
    val id: String,
    val conversationId: String,
    val executionId: String,
    val sourceRequestId: String,
    val kind: ApprovalKind,
    val summary: String,
    val command: String?,
    val cwd: String?,
    val expiresAt: Long,
    val status: ApprovalStatus,
)
```

审批过期或连接断开时必须 fail closed。旧审批不能离线排队后补批。

### 6.10 FileTransfer

```kotlin
data class FileTransfer(
    val id: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val sourcePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val transferredBytes: Long,
    val expiresAt: Long,
    val status: FileTransferStatus,
)
```

V1 只支持用户明确选择的单文件传送。目录传送和项目迁移后置。

## 7. 事件与命令协议

### 7.1 Android 到主节点的命令

- `conversation.create`
- `conversation.rename`
- `conversation.syncMode.set`
- `execution.start`
- `execution.steer`
- `execution.interrupt`
- `approval.resolve`
- `file.request`
- `sync.pull`

每个命令包含：

```json
{
  "protocolVersion": 1,
  "commandId": "uuid",
  "deviceId": "uuid",
  "issuedAt": 0,
  "expiresAt": 0,
  "type": "execution.start",
  "payload": {}
}
```

`commandId` 是幂等键。主节点必须缓存已完成命令结果，重复请求返回同一结果，不重复执行。

### 7.2 主节点到 Android 的事件

V1 至少支持：

- `conversation.created`
- `conversation.updated`
- `user_message.completed`
- `assistant_message.delta`
- `assistant_message.completed`
- `plan.updated`
- `command.started`
- `command.output.delta`
- `command.completed`
- `file_change.updated`
- `approval.requested`
- `approval.resolved`
- `artifact.available`
- `execution.completed`
- `execution.failed`
- `execution.interrupted`
- `device.presence.changed`

Android 使用最后确认的 `sequence` 重连。主节点从下一条事件补发；若事件保留窗口已过，则返回对话快照和新的基线 cursor。

## 8. Codex App Server 映射

Windows Connector 只通过本机 `stdio://` 使用 Codex App Server。

| Codex RPC/事件 | MASON 行为 |
| --- | --- |
| `thread/list` | 导入或刷新外部 Codex 历史索引 |
| `thread/read` | 构建外部历史会话投影 |
| `thread/start` | 创建 MASON 管理的 Codex 会话 |
| `thread/resume` | 恢复 MASON 管理的会话并订阅事件 |
| `turn/start` | `execution.start` |
| `turn/steer` | `execution.steer` |
| `turn/interrupt` | `execution.interrupt` |
| `item/agentMessage/delta` | `assistant_message.delta` |
| `turn/plan/updated` | `plan.updated` |
| `item/commandExecution/outputDelta` | `command.output.delta` |
| `turn/diff/updated` | `file_change.updated` |
| `item/*/requestApproval` | `approval.requested` |
| `serverRequest/resolved` | `approval.resolved` |
| `turn/completed` | 最终执行状态 |

Connector 不向手机同步 `reasoning.content`。可展示官方协议中的计划、进度和最终回答，但不得把隐藏推理当作用户可见对话。

### 8.1 版本兼容

Connector 启动时必须：

1. 定位可执行的 Codex CLI，而不是依赖可能返回 `Access is denied` 的 WindowsApps 别名。
2. 读取 CLI/App Server 版本。
3. 执行 `initialize` 并验证所需方法。
4. 使用与该版本匹配的生成 Schema 或兼容适配器。
5. 缺少关键方法时将 Connector 标记为 `INCOMPATIBLE`，不静默降级执行。

已验证本机版本使用连字符枚举，例如 `on-request` 和 `read-only`。实现不得硬编码其他版本文档中的驼峰枚举。

## 9. 私有组网与应用层安全

### 9.1 配对

Windows Connector 显示一次性二维码，包含：

- Connector 设备 ID
- 私有组网地址和端口
- Connector 公钥指纹
- 一次性配对 token
- 过期时间

Android 提交自身设备 ID、公钥和设备名称。配对成功后，一次性 token 立即失效。

### 9.2 会话认证

- 每次连接验证已配对设备签名。
- 会话 token 短时有效，可刷新。
- 每条命令包含时间戳、到期时间和唯一 `commandId`。
- Connector 支持单设备撤销和全部设备撤销。
- 不因设备位于同一 Tailnet 就跳过应用层认证。

### 9.3 权限范围

设备授权至少拆分为：

- 查看共享对话
- 发送消息
- 控制执行
- 处理审批
- 请求文件

默认配对可以授予全部个人设备能力，但数据模型必须支持以后收紧。

### 9.4 路径安全

- 所有文件路径在拥有副本的设备上 canonicalize。
- 文件请求必须绑定已登记的项目副本或明确产物。
- 拒绝 `..` 穿越、符号链接逃逸和设备路径混用。
- V1 不提供手机直接调用任意 `command/exec` 的接口。
- 手机通过 Codex 会话请求项目操作，Codex 沙箱和审批仍然生效。

## 10. 离线与冲突规则

### 10.1 Android outbox

允许离线排队：

- 新用户消息
- 对话重命名
- 同步模式变更

不允许离线排队：

- 审批决定
- 停止指令
- 文件下载授权

这些时效性命令在设备离线时直接显示失败或不可用，避免上线后执行过期意图。

### 10.2 并发发送

- 活动回合期间的新消息默认映射为 `turn/steer`。
- 用户可选择“排到下一回合”。
- 不允许另一设备静默抢占执行租约。
- 切换执行设备必须先中断当前回合或等待完成。

### 10.3 主节点离线

- Android 继续展示本地缓存。
- 绑定电脑项目的对话显示“电脑离线”。
- 可排队普通消息，并显示“等待发送”。
- 不展示虚假执行进度。

## 11. Android 后台策略

由用户选择：

| 模式 | 行为 |
| --- | --- |
| `ON_DEMAND` | 仅前台打开 MASON 时连接，最省电 |
| `WHILE_TASK_ACTIVE` | 有活动任务时使用前台服务和常驻通知，推荐默认 |
| `ALWAYS_ON` | 尽量持续连接，并引导用户将电池策略设为“不受限制” |

应用必须明确说明后台常驻的耗电影响。即使用户开启系统后台无限制，MASON 仍需处理网络切换、进程重启和指数退避。

## 12. 文件操作与文件传送

### 12.1 远程文件操作

手机在对话中要求电脑读取、创建或修改项目文件，实际操作由电脑端 Codex 完成，受项目路径、沙箱和审批约束。

### 12.2 文件传送

电脑可把指定项目文件或产物发送到手机：

1. Connector 生成 `FileTransfer` 元数据和 SHA-256。
2. Android 显示文件名、来源设备、大小和项目。
3. 用户接受后通过 HTTPS 分块下载。
4. 支持基于字节偏移的断点续传。
5. 完成后校验 SHA-256。
6. 文件先进入应用私有目录，再由用户预览、分享或导出。

V1 不自动传送目录，不允许模型在无用户确认时向手机推送任意敏感文件。

## 13. 本地数据库迁移策略

现有 Room `Conversation.id` 和 `Message.id` 是本地自增 `Long`。V1 不直接替换主键，而采用渐进迁移：

1. `Conversation` 增加唯一 `global_id`，旧数据首次迁移时生成。
2. `Message` 增加唯一 `event_id`、`source_device_id` 和同步状态。
3. 保留本地 `Long` 作为 Room 关系和 UI 性能键。
4. 所有新跨端 API 只使用全局 ID。
5. 项目上下文从 SharedPreferences 迁移到结构化项目/副本表。
6. 迁移必须可重复执行且不覆盖已有全局 ID。

## 14. 分阶段交付

### Phase 0：协议和 Connector 隔离验证

- 建立 `:protocol` 数据模型。
- 建立独立 Windows Connector 原型。
- 复现已通过的 Codex `thread/list`、`thread/read`、流式输出、拒绝审批和中断测试。
- 暂不连接 Android UI。

退出条件：Connector 重启后可以恢复 MASON 管理的测试会话，不重复消息或工具执行。

### Phase 1：Android 身份与缓存迁移

- 增加设备 ID、会话全局 ID和消息事件 ID。
- 引入 outbox 和 cursor。
- 保持现有单端聊天行为不变。

退出条件：现有对话无损迁移，离线创建的新消息可在模拟重连后幂等上传。

### Phase 2：私有组网同步

- 配对、认证和设备撤销。
- 共享对话列表和历史同步。
- 在线状态和执行位置展示。

退出条件：Android 和 Windows 在同一私有网络中可断线重连，并从 cursor 继续同步。

### Phase 3：完整 Codex 远控

- Android 发起/继续 MASON 管理的 Codex 会话。
- 流式消息、计划、命令输出和 Diff。
- 审批和停止。

退出条件：在 Android 完成“发起任务 -> 查看进度 -> 拒绝一次审批 -> 再次批准合法操作 -> 停止任务”的闭环。

### Phase 4：单文件传送

- 文件请求、接受、分块下载、续传和校验。
- Android 预览、分享和导出。

退出条件：网络中断后恢复传送，最终文件哈希一致，未授权文件无法下载。

## 15. V1 验收标准

### 功能

- 两端展示同一共享对话列表。
- `LOCAL_ONLY` 对话不出现在其他设备。
- 手机能继续电脑上的 MASON 管理 Codex 会话。
- 增量输出按顺序且不重复。
- 手机能拒绝审批，电脑不执行对应命令。
- 手机能停止活动回合，最终状态一致为 `interrupted`。
- 电脑离线时手机显示缓存和真实离线状态。
- 单文件可以断点续传并通过哈希校验。

### 安全

- 未配对设备无法读取对话或发送命令。
- 被撤销设备的现有连接立即失效。
- 重放旧命令不会再次执行。
- 过期审批不能补批。
- 手机不能绕过 Codex 沙箱直接执行任意命令。
- 路径穿越和项目根目录逃逸测试全部失败关闭。

### 兼容性

- Connector 能报告 Codex CLI 和协议版本。
- 不兼容版本给出明确错误，不启动执行。
- Android 数据迁移保留现有对话、消息和导出能力。

## 16. 风险与否决条件

| 风险 | 应对 |
| --- | --- |
| Codex App Server 仍处实验成熟度 | 固定兼容版本、生成 Schema、启动探测、适配层隔离 |
| 官方桌面活动会话无法共享实时订阅 | V1 将完整控制限定为 MASON 管理会话 |
| Windows 可执行路径随应用升级变化 | 运行时发现、用户显式选择、版本校验，不硬编码版本哈希 |
| Android 后台连接被系统限制 | 三档后台策略、前台服务、断线恢复 |
| 电脑是个人主节点单点 | Android 保留完整对话缓存，提供加密备份；项目仍由 Git/用户备份 |
| 私有组网设备被攻陷 | 应用层密钥、能力范围、撤销、审计和 fail closed |

出现以下任一情况时暂停主项目集成：

1. 目标 Codex 版本缺少 `thread/start`、`turn/start`、审批或 `turn/interrupt`。
2. Connector 无法在进程重启后安全恢复会话。
3. 审批响应可能被重复应用或跨会话错配。
4. Android 数据迁移不能证明无损和可回退。
5. 未建立应用层认证就需要暴露 Connector 网络端口。

## 17. 已完成的技术证据

在 Windows 本机 Codex `0.146.0-alpha.3.1` 上已通过隔离探针验证：

- App Server `initialize`。
- `thread/list` 列出当前和历史 Codex 对话。
- `thread/read` 读取完整已保存回合。
- 创建/恢复测试会话。
- 接收 `item/agentMessage/delta` 并得到准确最终文本。
- 接收一次 `item/commandExecution/requestApproval`。
- 回复 `decline` 后收到 `serverRequest/resolved`，命令状态为 `declined`。
- `turn/interrupt` 被接受，最终状态为 `interrupted`。
- 被拒绝的探针文件未创建。

官方参考：

- [Codex App Server](https://learn.chatgpt.com/docs/app-server)
- [Remote connections](https://learn.chatgpt.com/docs/remote-connections)

## 18. Phase 0B 实现状态

已完成：

- Connector 本地状态使用带 Schema 版本的原子 JSON 快照。
- 持久化 MASON conversation 与 Codex thread 的唯一绑定。
- 持久化单调递增 sequence、事件和同步 cursor 所需历史。
- 终态 Codex 事件使用规范化 JSON 指纹跨重启去重。
- 命令执行前持久化 `IN_PROGRESS`；重启后不自动重放不确定命令。
- 同一 `commandId` 携带不同内容时返回冲突，不复用旧结果。
- 审批同时绑定 conversation、Codex thread、App Server connection 和 request ID。
- App Server 重启复用 request ID 时不会命中旧连接的审批。
- `recover <state-file> [working-directory]` 通过 `thread/read` 恢复全部 MASON 管理绑定。
- 自动化测试覆盖状态重开、重复事件、重复命令、命令冲突、审批错配和 thread ID 错配。

真实集成验收已通过：复用既有 `MASON_PROBE` 测试 thread，分别启动两次 Connector 进程执行恢复。两次均恢复 1/1 个绑定且失败数为 0；第二次恢复后 sequence、事件数和命令数均未增加，没有重复事件或命令重放。验收只调用 `thread/list` 和 `thread/read`，未发送消息、启动回合或调用模型。

## 19. Phase 1 实现状态

已完成：

- Room 数据库从 v1 升级到 v2，保留原有 `Conversation.id`、`Message.id` 和现有 UI API。
- `Conversation` 增加 `global_id`、`sync_mode` 和 `authority_device_id`。
- `Message` 增加 `event_id`、`source_device_id` 和 `sync_state`。
- 新增 `local_device`、`sync_outbox` 和 `sync_cursors` 表及对应 DAO。
- 本地设备身份、对话全局 ID 和消息事件 ID 使用 UUIDv7。
- `SHARED` 用户消息与 outbox 在同一 Room 事务中写入；`LOCAL_ONLY` 消息不进入 outbox。
- outbox 支持完成项去重、失败退避和中断项恢复；cursor 只允许单调前进。
- v1/v2 对话导入导出保持兼容。

自动化验收已通过：

- Android instrumentation 在随机测试数据库中完成真实 v1 -> v2 迁移，验证旧对话和消息内容、原有 Long 主键及外键关系均保留。
- 模拟重连连续 flush 两次，首次上传 1 条，第二次上传 0 条，证明完成命令不会重复发送。
- cursor 从 5 尝试回退到 3 后仍为 5。
- UUIDv7 JVM 单元测试通过。
- `:sync:testDebugUnitTest`、`:sync:assembleDebug`、`:sync:assembleAndroidTest` 和 `:app:compileDebugKotlin` 通过。

本次验收未安装或启动 MASON 主应用，也未读取或迁移正式 `mason_database.db`。

## 20. Phase 2A 安全核心实现状态

已完成不依赖网络传输和 UI 的安全状态机：

- 协议新增 `PairingOffer`、`PairingRequest`、`PairingResult`、`AuthChallenge`、`AuthProof` 和 `SessionGrant`。
- 设备权限拆分为查看共享对话、发送消息、控制执行、处理审批和请求文件。
- 设备身份使用 ECDSA P-256 / SHA-256；协议显式携带算法，配对请求必须签署规范化载荷，证明持有对应私钥。
- Android 使用 `AndroidKeyStore` 原生生成 P-256 私钥并完成签名，私钥不可导出；协议只传输 X.509 Base64 公钥。
- Windows Connector 使用当前用户范围的 DPAPI 保护 PKCS#8 私钥；身份文件只保存公钥、算法、创建时间和 DPAPI 密文。
- 配对 token 使用 256 bit 随机值、五分钟默认有效期且只能成功使用一次；Connector 仅在内存中保存其 SHA-256。
- 登录使用一次性随机 challenge；challenge 无论验证成功或失败均不能重放。
- 会话 token 使用 256 bit 随机值、十五分钟默认有效期；Connector 仅保存哈希和权限主体。
- 设备撤销写入持久状态，并立即清除该设备的未完成 challenge 和活动会话。
- Connector 状态文件升级到 schema v2；v1 状态原子迁移并保留原设备 ID、会话、事件、命令和审批。
- Connector 重启后保留配对设备及撤销状态，但主动使配对 token、challenge 和会话 token 失效。

自动化测试覆盖：

- 正常配对、ECDSA P-256 challenge 认证和权限范围校验。
- 错误 token、错误签名、过期 offer、过期 challenge 和过期 session。
- 配对 token 复用、challenge 重放和设备 ID 重复注册。
- 撤销后现有会话立即失效，重启后撤销状态仍然有效。
- v1 -> v2 Connector 状态迁移及原有会话恢复回归。

Phase 2A 当时尚未实现：

- HTTPS/WebSocket 网络端点、二维码展示/扫描和真实设备配对。
- 共享对话同步、在线状态和 Android UI。

AndroidKeyStore 设备验收已在 Android 12 测试设备通过：同一 alias 重复读取返回稳定公钥，P-256 签名验证成功，私钥编码为空且无法从应用导出。测试使用随机独立 alias，完成后删除，未读取或替换正式设备身份。

Windows DPAPI 验收已在当前 Windows 用户下通过：临时身份文件重开后公钥和创建时间保持稳定，解密后的 P-256 私钥可以完成签名；篡改 DPAPI 密文后身份加载失败关闭。私钥明文只在签名期间存在于进程内存并在使用后清零，不写入磁盘。测试完成后删除独立临时身份文件，未生成正式 Connector 身份。

## 21. Phase 2B1 Loopback 传输状态

已完成真实 HTTP 回环闭环：

- Windows Connector 使用 Ktor/Netty 提供 `/v1/pairing/complete`、`/v1/auth/challenge`、`/v1/auth/session` 和受 Bearer token 保护的 `/v1/me`。
- 服务端只接受操作系统判定为 loopback 的监听地址，`0.0.0.0` 和私网地址在本阶段均拒绝启动。
- Connector 不提供远程创建 pairing offer 的接口；一次性 offer 仍必须由本地可信入口创建。
- Android `sync` 模块提供基于 OkHttp 的无 UI 配对客户端，并复用 AndroidKeyStore 身份完成配对和 challenge 签名。
- 明文 HTTP 客户端只接受 loopback URL，避免尚未接入 TLS 时误用于真实私网。
- 错误响应使用结构化协议错误；畸形 JSON、错误协议版本和空设备 ID 返回 400。
- 真实 Netty 随机回环端口测试已完成“配对 -> challenge -> session -> `/me` -> 撤销后 session 失效”闭环。
- Android 客户端 MockWebServer 测试验证配对签名、challenge 签名和 Bearer session 请求。

本阶段未修改 Android UI，未开放私网端口，未调整 Windows 防火墙，也未生成正式配对身份或二维码。

## 22. Phase 2B2 TLS 与证书固定状态

当前已完成代码和本机隔离验证：

- Connector 独立生成 TLS 自签名证书，TLS 私钥不复用设备签名身份。
- TLS 身份以 PKCS12 保存，随机密码与 PKCS12 一起组成私有 bundle；整个 bundle 经当前 Windows 用户范围 DPAPI 加密后原子写入单一 JSON 文件。
- 身份加载时校验证书 DER、SHA-256 指纹、证书有效期和私钥条目；密文、证书或指纹损坏时 fail closed。
- `PairingBootstrap` 将一次性 `PairingOffer`、HTTPS endpoint 和 TLS 证书 SHA-256 指纹组成稳定的二维码载荷。
- Connector HTTPS 服务只接受显式的本机地址，拒绝 wildcard、multicast 和不属于本机网卡的地址。
- Android pinned 客户端只接受 HTTPS，不跟随重定向，并以二维码中的 SHA-256 严格固定服务器叶证书。
- 本机随机 HTTPS 端口已完成“配对 -> challenge -> session -> `/me`”闭环；错误指纹必须在 TLS 握手阶段失败。

本阶段仍未修改 Android UI、未生成二维码图片、未开放真实私网端口、未调整 Windows 防火墙、未安装主 APK，也未创建正式 Connector TLS 身份。下一步应在用户确认后接入二维码展示/扫描，并在两台真实设备的私有组网上验证地址选择、Android 后台存活和断线重连。

## 23. Phase 2B3 Android 二维码配对入口

当前已完成 Android 侧代码和构建验证：

- 侧边栏在“新对话”和“最近对话”之间增加“设备扫码配对”入口，复用现有主操作样式。
- 扫码使用 CameraX 和 bundled ML Kit Barcode Scanning，不依赖 Google Play 服务，也不需要运行时下载识别模型。
- 扫描页只识别 QR Code；识别后校验协议版本、一次性 offer 有效期、HTTPS endpoint 和 64 位证书 SHA-256。
- 用户确认后复用 AndroidKeyStore 设备身份与 pinned HTTPS 客户端完成注册、challenge、session 和 `/v1/me` 校验。
- 配对成功后只持久化 Connector 设备 ID、endpoint、证书指纹和配对时间，不持久化短期 session token。
- 注册成功但后续认证因瞬时网络失败时，重新尝试可从 `DEVICE_ALREADY_PAIRED` 继续 challenge 认证。

本阶段未开放真实私网监听或修改防火墙，也未在本任务中安装/启动主 APK。下一步是先用 Connector 本机配对命令验收二维码生成和 HTTPS 服务生命周期，再经用户明确授权后进入真实私有组网的双设备验收。

## 24. Phase 2B4 Windows 本机配对入口

已实现 Connector CLI 本地可信入口：

```text
mason-codex-connector pair-local <port> <qr-output.png> [state-directory]
```

- 命令使用当前 Windows 用户范围 DPAPI 加载或创建 Connector 签名身份与 TLS 身份。
- 未指定 `state-directory` 时，默认使用 `%LOCALAPPDATA%\MASON\connector`，其中保存 `connector-state.json`、`connector-identity.json` 和 `connector-tls-identity.json`。
- 命令创建一个五分钟默认有效、只能成功使用一次的 `PairingOffer`，并将完整 `PairingBootstrap` 写入指定 PNG 二维码。
- 二维码载荷包含 HTTPS endpoint、Connector 配对 offer 和 TLS 叶证书 SHA-256 指纹；测试会反向解码 PNG 并与原始 Bootstrap JSON 精确比对。
- HTTPS 服务与前述 `PairingAuthService` 共用同一组配对、challenge、session 和 `/v1/me` 逻辑，保持到 offer 过期或用户按 Ctrl+C。
- 为避免静默覆盖用户文件，`qr-output.png` 已存在时命令直接失败。

当前命令生成的 endpoint 固定为 `https://127.0.0.1:<port>`，仅用于 Windows 本机 HTTPS 验证。手机扫码后的 `127.0.0.1` 指向手机自身，因此手机不能通过该二维码连接电脑。这一限制是本机验证方案的有意边界，不得将其宣称为已完成真实双设备配对。

本阶段已通过 Connector 二维码编码/解码和服务端自动化测试，但未实际运行 `pair-local`，因此未写入正式 Windows Connector 身份或正式配对二维码。未开放私网端口，未修改 Windows 防火墙，未构建或安装新的 Android APK。

## 25. Phase 2B5 局域网实机配对准备

Connector CLI 已新增局域网配对命令：

```text
mason-codex-connector pair-private <private-ipv4> <port> <qr-output.png> [state-directory]
```

- `private-ipv4` 必须是 `10/8`、`172.16/12` 或 `192.168/16` 中的字面 IPv4 地址。
- 地址必须已分配给当前电脑上处于 up 状态的网卡；域名、公网地址、loopback、wildcard、multicast 和非本机地址均拒绝。
- 二维码 endpoint 使用经校验的局域网 IPv4，Android 仍以二维码中的 SHA-256 固定 Connector TLS 叶证书。
- `pair-local` 与 `pair-private` 共用身份、一次性 offer、二维码和 HTTPS 服务；`pair-local` 仍在 offer 过期后关闭，`pair-private` 在 offer 过期后继续为已配对设备提供服务，直到用户按 Ctrl+C。
- 命令不会自动修改 Windows 防火墙，也不会绑定 `0.0.0.0`。

当前仅完成代码、自动化测试和分发包构建；尚未运行正式 `pair-private`，未创建正式身份或二维码，未开放端口，未修改防火墙。真机验收顺序为：手机与电脑连接同一 Wi-Fi -> 选择电脑当前私网 IPv4 -> 本地运行 `pair-private` -> 用 Android MASON 扫码 -> 验证配对、challenge、session 和 `/v1/me`。

## 26. Phase 2B6 配对后会话浏览与设备管理

当前已完成代码和隔离测试：

- `pair-private` 启动并初始化独立 Codex App Server，通过 `thread/list` 和 `thread/read` 构建稳定的 MASON 只读投影；Codex 原始 JSON 不直接暴露给 Android。
- Connector 新增 `GET /v1/conversations`、`GET /v1/conversations/{threadId}` 和 `POST /v1/me/revoke`，会话接口必须持有 `VIEW_SHARED_CONVERSATIONS` 权限。
- 会话列表按更新时间分页；Android 首次展示 3 条，“展开更多”每次再请求 3 条。MASON 管理会话和外部历史会话在协议中保留 ownership 区分，但本阶段均以历史读取为主。
- 配对成功后，侧栏扫码入口变为可展开的电脑分组；电脑离线时保留历史入口并显示离线状态，不伪造已加载结果。
- 远端会话详情仅投影最近 20 条 user/assistant 文字消息，忽略 reasoning、命令和其他内部 item；有更早消息时显式提示截断。
- 设置首页新增“设备配对”。手机取消配对时先立即清除本地状态，再通过固定证书连接尽力撤销电脑端授权；电脑离线不阻塞手机恢复未配对状态。
- 本机随机 HTTP/HTTPS 自动化测试已覆盖权限会话、分页、详情、证书固定和撤销后 session 失效。

本阶段仍未开放真实私网端口、修改 Windows 防火墙或执行双设备扫码。外部 Codex 历史继续对话、实时事件、审批和远控仍属于 Phase 3。
