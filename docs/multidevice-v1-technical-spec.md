# MASON 多端统一对话 V1 技术规格

状态：Draft 0.2

日期：2026-07-28

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

## 20. 下一实施决策

Phase 1 退出条件已满足。下一步进入 Phase 2：先定义并实现私有组网之上的应用层配对、设备认证和撤销，再接共享对话同步。Phase 2 开始前需要确定 Connector 的网络服务边界、密钥存储方式和首个配对流程，不同时修改 Android 对话 UI。
