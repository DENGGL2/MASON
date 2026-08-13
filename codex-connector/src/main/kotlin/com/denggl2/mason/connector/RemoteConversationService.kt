package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.RemoteAttachmentDescriptor
import com.denggl2.mason.protocol.RemoteAttachmentKind
import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteConversationActivity
import com.denggl2.mason.protocol.RemoteConversationActivityKind
import com.denggl2.mason.protocol.RemoteConversationActivityStatus
import com.denggl2.mason.protocol.RemoteConversationAttachment
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteConversationCreateRequest
import com.denggl2.mason.protocol.RemoteConversationEventPage
import com.denggl2.mason.protocol.RemoteConversationExecutionChange
import com.denggl2.mason.protocol.RemoteExecutionResult
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.protocol.RemoteConversationMessage
import com.denggl2.mason.protocol.RemoteConversationPage
import com.denggl2.mason.protocol.RemoteConversationRole
import com.denggl2.mason.protocol.RemoteConversationSummary
import com.denggl2.mason.protocol.RemoteMessageRequest
import com.denggl2.mason.protocol.RemoteModelOption
import com.denggl2.mason.protocol.RemotePermissionProfileOption
import com.denggl2.mason.protocol.RemoteProjectOption
import com.denggl2.mason.protocol.RemoteReasoningEffortOption
import com.denggl2.mason.protocol.RemoteSkillOption
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

interface RemoteConversationProvider {
    suspend fun listConversations(limit: Int, cursor: String?): RemoteConversationPage
    suspend fun readConversation(threadId: String): RemoteConversationDetail
    suspend fun conversationEvents(afterRevision: Long, waitMillis: Long): RemoteConversationEventPage =
        RemoteConversationEventPage()
    suspend fun downloadConversationAttachment(
        threadId: String,
        attachmentId: String,
    ): RemoteConversationAttachmentDownload = throw RemoteConversationAttachmentNotFoundException()
}

data class RemoteConversationAttachmentDownload(
    val descriptor: RemoteConversationAttachment,
    val bytes: ByteArray,
)

interface RemoteConversationController {
    suspend fun composerOptions(threadId: String): RemoteComposerOptions = RemoteComposerOptions()
    suspend fun newConversationOptions(projectPath: String? = null): RemoteComposerOptions =
        RemoteComposerOptions()
    suspend fun createConversation(request: RemoteConversationCreateRequest): RemoteExecutionResult =
        throw RemoteConversationControlUnavailableException()
    suspend fun uploadAttachment(
        deviceId: String,
        kind: RemoteAttachmentKind,
        name: String,
        mimeType: String?,
        bytes: ByteArray,
    ): RemoteAttachmentDescriptor = throw RemoteConversationControlUnavailableException()
    suspend fun sendMessage(threadId: String, text: String): RemoteExecutionResult =
        throw RemoteConversationControlUnavailableException()
    suspend fun sendMessage(
        deviceId: String,
        threadId: String,
        request: RemoteMessageRequest,
    ): RemoteExecutionResult {
        require(request.attachmentIds.isEmpty() && request.skill == null) {
            "Enhanced remote messages are unavailable"
        }
        return sendMessage(threadId, request.text)
    }
    suspend fun interrupt(threadId: String): RemoteExecutionResult
    suspend fun setPinned(threadId: String, isPinned: Boolean): RemoteConversationSummary =
        throw RemoteConversationControlUnavailableException()
    suspend fun archive(threadId: String): RemoteConversationSummary =
        throw RemoteConversationControlUnavailableException()
}

class RemoteConversationService(
    private val api: CodexThreadHistoryApi,
    private val store: ConnectorStateStore,
    private val messageLimit: Int = DEFAULT_MESSAGE_LIMIT,
    attachmentRoot: Path = Path.of(System.getProperty("java.io.tmpdir"), "mason-connector-attachments"),
    workingDirectory: Path = Path.of(System.getProperty("user.dir")),
) : RemoteConversationProvider, RemoteConversationController {
    private val runtime = RemoteConversationRuntime()
    private val attachmentStore = RemoteAttachmentStore(attachmentRoot)
    private val connectorWorkingDirectory = workingDirectory.toAbsolutePath().normalize()
    private val managedAttachmentRoot = attachmentRoot
        .toAbsolutePath()
        .normalize()
        .let { root -> runCatching(root::toRealPath).getOrDefault(root) }

    init {
        require(messageLimit > 0) { "Remote conversation message limit must be positive" }
    }

    override suspend fun listConversations(limit: Int, cursor: String?): RemoteConversationPage {
        require(limit in 1..MAX_PAGE_SIZE) { "Conversation page size must be between 1 and $MAX_PAGE_SIZE" }
        // Capture before the RPC so any completion racing this list request remains observable.
        val pageRevision = runtime.revision()
        val response = api.listThreads(limit = limit, cursor = cursor).asObject()
        val conversations = response["data"]
            .asArrayOrEmpty()
            .mapNotNull { element -> element.asObjectOrNull()?.toSummary() }
        return RemoteConversationPage(
            conversations = conversations,
            nextCursor = response.string("nextCursor") ?: response.string("next_cursor"),
            revision = pageRevision,
        )
    }

    override suspend fun conversationEvents(
        afterRevision: Long,
        waitMillis: Long,
    ): RemoteConversationEventPage {
        require(afterRevision >= 0) { "Conversation event revision cannot be negative" }
        require(waitMillis in 0..MAX_EVENT_WAIT_MILLIS) {
            "Conversation event wait must be between 0 and $MAX_EVENT_WAIT_MILLIS milliseconds"
        }
        val deadline = System.currentTimeMillis() + waitMillis
        while (true) {
            runtime.changesAfter(afterRevision)?.let { return it }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return RemoteConversationEventPage(revision = runtime.revision())
            delay(minOf(EVENT_WAIT_SLICE_MILLIS, remaining))
        }
    }

    override suspend fun readConversation(threadId: String): RemoteConversationDetail {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val response = api.readThread(threadId = threadId, includeTurns = true).asObject()
        val thread = response["thread"].asObjectOrNull() ?: response
        val summary = thread.toSummary()
            ?: throw RemoteConversationNotFoundException(threadId)
        val turns = thread["turns"].asArrayOrEmpty()
        val persistedExecution = turns.lastOrNull()
            ?.asObjectOrNull()
            ?.toExecutionSnapshot()
        val liveExecution = runtime.snapshot(threadId)
        val execution = liveExecution ?: persistedExecution ?: RemoteExecutionSnapshot()
        val persistedActivities = persistedExecution?.activities.orEmpty()
        val activities = when {
            liveExecution == null -> persistedActivities
            liveExecution.turnId == persistedExecution?.turnId -> mergeActivities(
                persistedActivities,
                liveExecution.activities,
            )
            else -> liveExecution.activities
        }
        val visibleActivity = activities.lastOrNull {
            it.status == RemoteConversationActivityStatus.RUNNING
        } ?: activities.lastOrNull()
        val projectRoot = thread.string("cwd")
            ?.let(::normalizeExistingDirectory)
        val projectedAttachments = linkedMapOf<String, Path>()
        val allMessages = turns
            .flatMap { turn -> projectTurnMessages(turn, projectedAttachments, projectRoot) }
            .let { messages ->
                val partialText = liveExecution?.partialAssistantText.orEmpty().trim()
                if (
                    execution.status == RemoteExecutionStatus.RUNNING &&
                    partialText.isNotBlank() &&
                    messages.lastOrNull()?.text != partialText
                ) {
                    messages + RemoteConversationMessage(
                        role = RemoteConversationRole.ASSISTANT,
                        text = partialText,
                    )
                } else {
                    messages
                }
            }
        return RemoteConversationDetail(
            conversation = summary,
            messages = allMessages.takeLast(messageLimit),
            hasEarlierMessages = allMessages.size > messageLimit,
            executionStatus = execution.status,
            activeTurnId = execution.turnId.takeIf { execution.status == RemoteExecutionStatus.RUNNING },
            activeActivityTitle = visibleActivity?.title
                ?: execution.activeActivityTitle.takeIf(String::isNotBlank),
            activeActivityText = (visibleActivity?.text ?: execution.activeActivityText)
                ?.cleanPreview()
                ?.take(MAX_PREVIEW_LENGTH)
                ?.takeIf(String::isNotBlank),
            activities = activities,
        )
    }

    override suspend fun downloadConversationAttachment(
        threadId: String,
        attachmentId: String,
    ): RemoteConversationAttachmentDownload {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        require(attachmentId.isNotBlank()) { "Attachment ID is required" }
        val response = api.readThread(threadId = threadId, includeTurns = true).asObject()
        val thread = response["thread"].asObjectOrNull() ?: response
        if ((thread.string("id") ?: thread.string("threadId")) != threadId) {
            throw RemoteConversationNotFoundException(threadId)
        }
        val projectRoot = thread.string("cwd")
            ?.let(::normalizeExistingDirectory)
        val projectedAttachments = linkedMapOf<String, Path>()
        thread["turns"]
            .asArrayOrEmpty()
            .forEach { turn -> projectTurnMessages(turn, projectedAttachments, projectRoot) }
        val path = projectedAttachments[attachmentId]
            ?: throw RemoteConversationAttachmentNotFoundException()
        val descriptor = path.toRemoteConversationAttachment()
            ?: throw RemoteConversationAttachmentNotFoundException()
        val bytes = path.readBoundedBytes(MAX_DOWNLOAD_ATTACHMENT_BYTES)
        return RemoteConversationAttachmentDownload(
            descriptor = descriptor,
            bytes = bytes,
        )
    }

    override suspend fun composerOptions(threadId: String): RemoteComposerOptions {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val controlApi = api as? CodexRemoteControlApi
            ?: throw RemoteConversationControlUnavailableException()
        val storedSelection = store.remoteComposerSelection(threadId)
        // Composer options are read-only. Reading the thread directly avoids competing with the
        // desktop client's active writer, while the independent option requests can run together.
        val response = try {
            controlApi.readThread(threadId, includeTurns = false).asObject()
        } catch (error: CodexRpcException) {
            if (storedSelection == null || !error.isTemporarilyEmptyThreadRead()) throw error
            JsonObject(emptyMap())
        }
        val thread = response["thread"].asObjectOrNull() ?: response
        val cwd = thread.string("cwd")?.takeIf(String::isNotBlank)
            ?: storedSelection?.cwd
        return coroutineScope {
            val configRequest = async {
                controlApi.readConfig(cwd).asObject()["config"].asObjectOrNull()
            }
            val modelRequest = async { controlApi.listModels().asObject().toModelOptions() }
            val permissionRequest = async {
                controlApi.listPermissionProfiles(cwd).asObject().toPermissionProfiles()
            }
            val skillRequest = async {
                controlApi.listSkills(listOfNotNull(cwd)).asObject().toSkillOptions()
            }
            val config = configRequest.await()
            val models = modelRequest.await()
            val permissionProfiles = permissionRequest.await()
            val currentModel = thread.string("model")
                ?: storedSelection?.model
                ?: config?.string("model")
            val currentPermission = thread["activePermissionProfile"]
                .asObjectOrNull()
                ?.string("id")
                ?: storedSelection?.permissionProfileId
                ?: config?.string("default_permissions")
                ?: config?.string("defaultPermissions")
            RemoteComposerOptions(
                models = models,
                skills = skillRequest.await(),
                permissionProfiles = permissionProfiles,
                currentModelId = models.firstOrNull { it.model == currentModel }?.id,
                currentReasoningEffort = thread.string("reasoningEffort")
                    ?: storedSelection?.reasoningEffort
                    ?: config?.string("model_reasoning_effort"),
                currentPermissionProfileId = currentPermission
                    ?.takeIf { id -> permissionProfiles.any { it.id == id && it.allowed } },
                cwd = cwd,
            )
        }
    }

    private fun CodexRpcException.isTemporarilyEmptyThreadRead(): Boolean =
        code == -32603 &&
            message.contains("failed to read session metadata", ignoreCase = true) &&
            message.contains("rollout", ignoreCase = true) &&
            message.contains("is empty", ignoreCase = true)

    override suspend fun newConversationOptions(projectPath: String?): RemoteComposerOptions {
        val controlApi = api as? CodexRemoteControlApi
            ?: throw RemoteConversationControlUnavailableException()
        val projects = recentProjectOptions()
        val cwd = when {
            projectPath == null -> projects.firstOrNull()?.path
            else -> {
                val requested = normalizeExistingDirectory(projectPath)
                projects.firstOrNull { option ->
                    requested != null && Path.of(option.path) == requested
                }?.path ?: throw IllegalArgumentException(
                    "Selected project is unavailable on the computer",
                )
            }
        }
        return coroutineScope {
            val modelRequest = async { controlApi.listModels().asObject().toModelOptions() }
            val configRequest = async {
                controlApi.readConfig(cwd).asObject()["config"].asObjectOrNull()
            }
            val permissionRequest = async {
                controlApi.listPermissionProfiles(cwd).asObject().toPermissionProfiles()
            }
            val models = modelRequest.await()
            val config = configRequest.await()
            val permissionProfiles = permissionRequest.await()
            val configuredModel = config?.string("model")
            val configuredPermission = config?.string("default_permissions")
                ?: config?.string("defaultPermissions")
            val currentPermission = configuredPermission
                ?.takeIf { id -> permissionProfiles.any { it.id == id && it.allowed } }
                ?: ":workspace".takeIf { id -> permissionProfiles.any { it.id == id && it.allowed } }
            RemoteComposerOptions(
                projects = projects,
                models = models,
                permissionProfiles = permissionProfiles,
                currentModelId = models.firstOrNull { it.model == configuredModel }?.id
                    ?: models.firstOrNull(RemoteModelOption::isDefault)?.id,
                currentReasoningEffort = config?.string("model_reasoning_effort"),
                currentPermissionProfileId = currentPermission,
                cwd = cwd,
            )
        }
    }

    override suspend fun createConversation(
        request: RemoteConversationCreateRequest,
    ): RemoteExecutionResult {
        val text = request.text.trim()
        require(text.isNotEmpty()) { "Message text is required" }
        require(text.length <= MAX_MESSAGE_LENGTH) {
            "Message text must not exceed $MAX_MESSAGE_LENGTH characters"
        }
        val controlApi = api as? CodexRemoteControlApi
            ?: throw RemoteConversationControlUnavailableException()
        val options = newConversationOptions(request.projectPath)
        val projectPath = options.cwd
            ?: throw IllegalArgumentException("Selected project is unavailable on the computer")
        val model = options.models.firstOrNull { it.id == request.modelId }
            ?: throw IllegalArgumentException("Selected model is unavailable on the computer")
        request.reasoningEffort?.let { effort ->
            require(model.supportedReasoningEfforts.any { it.id == effort }) {
                "Selected reasoning effort is unavailable for this model"
            }
        }
        val permission = options.permissionProfiles.firstOrNull {
            it.id == request.permissionProfileId && it.allowed
        } ?: throw IllegalArgumentException("Selected permission profile is unavailable")
        val startedThread = controlApi.startThread(
            cwd = projectPath,
            model = model.model,
            permissions = permission.id,
        ).asObject()
        val thread = startedThread["thread"].asObjectOrNull() ?: startedThread
        val threadId = thread.string("id")
            ?: throw IllegalStateException("Codex thread/start did not return a thread ID")
        val startedTurn = controlApi.startTurn(
            threadId = threadId,
            input = buildTurnInput(text, emptyList(), null),
            model = model.model,
            effort = request.reasoningEffort,
            permissions = permission.id,
        ).asObject()
        val turn = startedTurn["turn"].asObjectOrNull() ?: startedTurn
        val turnId = turn.string("id")
            ?: throw IllegalStateException("Codex turn/start did not return a turn ID")
        store.recordRemoteComposerSelection(
            threadId = threadId,
            selection = StoredRemoteComposerSelection(
                model = model.model,
                reasoningEffort = request.reasoningEffort,
                permissionProfileId = permission.id,
                cwd = projectPath,
            ),
        )
        runtime.markStarted(threadId, turnId)
        return RemoteExecutionResult(
            threadId = threadId,
            turnId = turnId,
            status = RemoteExecutionStatus.RUNNING,
        )
    }

    private suspend fun recentProjectOptions(): List<RemoteProjectOption> {
        val recentPaths = api.listThreads(limit = MAX_PAGE_SIZE, cursor = null)
            .asObject()["data"]
            .asArrayOrEmpty()
            .mapNotNull { element ->
                element.asObjectOrNull()?.string("cwd")?.takeIf(String::isNotBlank)
            }
        return (listOf(connectorWorkingDirectory.toString()) + recentPaths)
            .mapNotNull(::normalizeExistingDirectory)
            .distinct()
            .map { path ->
                RemoteProjectOption(
                    path = path.toString(),
                    displayName = path.fileName?.toString()?.takeIf(String::isNotBlank)
                        ?: path.toString(),
                )
            }
    }

    private fun normalizeExistingDirectory(value: String): Path? = runCatching {
        Path.of(value)
            .toAbsolutePath()
            .normalize()
            .takeIf(Files::isDirectory)
            ?.toRealPath()
    }.getOrNull()

    override suspend fun uploadAttachment(
        deviceId: String,
        kind: RemoteAttachmentKind,
        name: String,
        mimeType: String?,
        bytes: ByteArray,
    ): RemoteAttachmentDescriptor = attachmentStore.store(
        deviceId = deviceId,
        kind = kind,
        originalName = name,
        mimeType = mimeType,
        bytes = bytes,
    )

    override suspend fun sendMessage(threadId: String, text: String): RemoteExecutionResult = sendMessage(
        deviceId = store.deviceId,
        threadId = threadId,
        request = RemoteMessageRequest(text = text),
    )

    override suspend fun sendMessage(
        deviceId: String,
        threadId: String,
        request: RemoteMessageRequest,
    ): RemoteExecutionResult {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val normalizedText = request.text.trim()
        require(
            normalizedText.isNotEmpty() ||
                request.attachmentIds.isNotEmpty() ||
                request.skill != null,
        ) { "Message text, attachment, or Skill is required" }
        require(normalizedText.length <= MAX_MESSAGE_LENGTH) {
            "Message text must not exceed $MAX_MESSAGE_LENGTH characters"
        }
        val controlApi = api as? CodexRemoteControlApi
            ?: throw RemoteConversationControlUnavailableException()
        val currentExecution = runtime.snapshot(threadId)
            ?: readLatestExecution(threadId)
        if (currentExecution.status == RemoteExecutionStatus.RUNNING) {
            throw RemoteConversationBusyException(threadId)
        }

        val attachments = attachmentStore.resolve(deviceId, request.attachmentIds)
        val needsOptions = request.skill != null ||
            request.modelId != null ||
            request.reasoningEffort != null ||
            request.permissionProfileId != null
        val options = if (needsOptions) composerOptions(threadId) else RemoteComposerOptions()
        val skill = request.skill?.let { selected ->
            options.skills.firstOrNull { it.name == selected.name && it.path == selected.path }
                ?: throw IllegalArgumentException("Selected Skill is unavailable on the computer")
        }
        val model = request.modelId?.let { modelId ->
            options.models.firstOrNull { it.id == modelId }
                ?: throw IllegalArgumentException("Selected model is unavailable on the computer")
        }
        request.reasoningEffort?.let { effort ->
            require(model != null && model.supportedReasoningEfforts.any { it.id == effort }) {
                "Selected reasoning effort is unavailable for this model"
            }
        }
        val permissionProfile = request.permissionProfileId?.let { profileId ->
            options.permissionProfiles.firstOrNull { it.id == profileId && it.allowed }
                ?: throw IllegalArgumentException("Selected permission profile is unavailable")
        }
        val input = buildTurnInput(
            text = normalizedText,
            attachments = attachments,
            skill = skill,
        )

        controlApi.resumeThread(threadId)
        val response = controlApi.startTurn(
            threadId = threadId,
            input = input,
            model = model?.model,
            effort = request.reasoningEffort,
            permissions = permissionProfile?.id,
        ).asObject()
        val turn = response["turn"].asObjectOrNull() ?: response
        val turnId = turn.string("id")
            ?: throw IllegalStateException("Codex turn/start did not return a turn ID")
        val previousSelection = store.remoteComposerSelection(threadId)
        val selectedModel = model?.model ?: previousSelection?.model
        val selectedPermission = permissionProfile?.id ?: previousSelection?.permissionProfileId
        val selectedCwd = options.cwd ?: previousSelection?.cwd
        if (selectedModel != null && selectedPermission != null && selectedCwd != null) {
            store.recordRemoteComposerSelection(
                threadId = threadId,
                selection = StoredRemoteComposerSelection(
                    model = selectedModel,
                    reasoningEffort = request.reasoningEffort ?: previousSelection?.reasoningEffort,
                    permissionProfileId = selectedPermission,
                    cwd = selectedCwd,
                ),
            )
        }
        runtime.markStarted(threadId, turnId)
        return RemoteExecutionResult(
            threadId = threadId,
            turnId = turnId,
            status = RemoteExecutionStatus.RUNNING,
        )
    }

    override suspend fun interrupt(threadId: String): RemoteExecutionResult {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val controlApi = api as? CodexRemoteControlApi
            ?: throw RemoteConversationControlUnavailableException()
        val execution = runtime.snapshot(threadId)
            ?.takeIf { it.status == RemoteExecutionStatus.RUNNING }
            ?: readLatestExecution(threadId).takeIf { it.status == RemoteExecutionStatus.RUNNING }
            ?: throw RemoteConversationNotRunningException(threadId)
        val turnId = execution.turnId ?: throw RemoteConversationNotRunningException(threadId)
        controlApi.interruptTurn(threadId, turnId)
        return RemoteExecutionResult(
            threadId = threadId,
            turnId = turnId,
            status = RemoteExecutionStatus.RUNNING,
        )
    }

    override suspend fun setPinned(threadId: String, isPinned: Boolean): RemoteConversationSummary {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val controlApi = api as? CodexRemoteControlApi
            ?: throw RemoteConversationControlUnavailableException()
        val response = controlApi.updateThreadMetadata(threadId, isPinned).asObject()
        val thread = response["thread"].asObjectOrNull() ?: response
        val summary = thread.toSummary() ?: readConversation(threadId).conversation
        if (summary.isPinned != isPinned) {
            throw RemoteConversationControlUnavailableException()
        }
        return summary
    }

    override suspend fun archive(threadId: String): RemoteConversationSummary {
        require(threadId.isNotBlank()) { "Thread ID is required" }
        val controlApi = api as? CodexRemoteControlApi
            ?: throw RemoteConversationControlUnavailableException()
        val summary = readConversation(threadId).conversation
        controlApi.archiveThread(threadId)
        return summary
    }

    fun record(notification: CodexNotification) {
        runtime.record(notification)
    }

    private suspend fun readLatestExecution(threadId: String): RemoteExecutionSnapshot {
        val response = api.readThread(threadId = threadId, includeTurns = true).asObject()
        val thread = response["thread"].asObjectOrNull() ?: response
        return thread["turns"]
            .asArrayOrEmpty()
            .lastOrNull()
            ?.asObjectOrNull()
            ?.toExecutionSnapshot()
            ?: RemoteExecutionSnapshot()
    }

    private fun JsonObject.toSummary(): RemoteConversationSummary? {
        val threadId = string("id") ?: string("threadId") ?: return null
        val preview = string("preview").orEmpty().cleanPreview()
        val explicitTitle = string("name").orEmpty().trim()
        val liveExecution = runtime.snapshot(threadId)
        val persistedExecution = this["turns"]
            .asArrayOrEmpty()
            .lastOrNull()
            ?.asObjectOrNull()
            ?.toExecutionSnapshot()
        return RemoteConversationSummary(
            threadId = threadId,
            title = explicitTitle.ifBlank { preview.lineSequence().firstOrNull().orEmpty() }
                .ifBlank { "未命名对话" }
                .take(MAX_TITLE_LENGTH),
            preview = preview.take(MAX_PREVIEW_LENGTH),
            updatedAt = epochMillis("updatedAt") ?: epochMillis("updated_at") ?: 0,
            projectPath = string("cwd")?.takeIf(String::isNotBlank),
            ownership = store.sessionForThread(threadId)?.binding?.ownership
                ?: CodexOwnership.EXTERNAL_HISTORY_ONLY,
            isPinned = boolean("isPinned") ?: boolean("is_pinned") ?: false,
            executionStatus = liveExecution?.status
                ?: persistedExecution?.status
                ?: listExecutionStatus(),
            latestCompletionId = liveExecution?.latestCompletionId
                ?: persistedExecution?.latestCompletionId,
        )
    }

    private fun JsonObject.listExecutionStatus(): RemoteExecutionStatus {
        val explicitStatus = sequenceOf("executionStatus", "execution_status", "status")
            .mapNotNull { key -> this[key].statusText() }
            .map(String::toRemoteExecutionStatus)
            .firstOrNull { it != RemoteExecutionStatus.IDLE }
        if (explicitStatus != null) return explicitStatus

        return this["turns"]
            .asArrayOrEmpty()
            .lastOrNull()
            ?.asObjectOrNull()
            ?.toExecutionSnapshot()
            ?.status
            ?: RemoteExecutionStatus.IDLE
    }

    private fun projectTurnMessages(
        turn: JsonElement,
        attachmentPaths: MutableMap<String, Path>,
        projectRoot: Path?,
    ): List<RemoteConversationMessage> {
        val turnObject = turn.asObjectOrNull() ?: return emptyList()
        val pendingAttachments = mutableListOf<RemoteConversationAttachment>()
        val messages = mutableListOf<RemoteConversationMessage>()
        turnObject["items"].asArrayOrEmpty().forEach { element ->
            val item = element.asObjectOrNull() ?: return@forEach
            val itemAttachments = item.remoteConversationAttachments(
                attachmentPaths = attachmentPaths,
                projectRoot = projectRoot,
            )
            val message = projectMessage(item)
            when {
                message != null -> {
                    messages += message.copy(
                        attachments = (pendingAttachments + itemAttachments)
                            .distinctBy(RemoteConversationAttachment::attachmentId),
                    )
                    pendingAttachments.clear()
                }
                itemAttachments.isNotEmpty() -> pendingAttachments += itemAttachments
            }
        }
        if (pendingAttachments.isNotEmpty()) {
            val lastAssistantIndex = messages.indexOfLast { it.role == RemoteConversationRole.ASSISTANT }
            if (lastAssistantIndex >= 0) {
                val message = messages[lastAssistantIndex]
                messages[lastAssistantIndex] = message.copy(
                    attachments = (message.attachments + pendingAttachments)
                        .distinctBy(RemoteConversationAttachment::attachmentId),
                )
            }
        }
        return messages
    }

    private fun projectMessage(item: JsonObject): RemoteConversationMessage? {
        val type = item.string("type").orEmpty().lowercase()
        if (
            (type == "agentmessage" || type.contains("assistant")) &&
            item.string("phase").equals("commentary", ignoreCase = true)
        ) {
            return null
        }
        val role = when {
            type.contains("user") -> RemoteConversationRole.USER
            type.contains("agentmessage") || type.contains("assistant") -> RemoteConversationRole.ASSISTANT
            item.string("role").equals("user", ignoreCase = true) -> RemoteConversationRole.USER
            item.string("role").equals("assistant", ignoreCase = true) -> RemoteConversationRole.ASSISTANT
            else -> return null
        }
        val text = extractText(item).trim()
        if (text.isBlank()) return null
        return RemoteConversationMessage(role = role, text = text)
    }

    private fun JsonObject.remoteConversationAttachments(
        attachmentPaths: MutableMap<String, Path>,
        projectRoot: Path?,
    ): List<RemoteConversationAttachment> {
        val type = string("type").orEmpty().lowercase()
        val candidatePaths = buildList {
            when (type) {
                "imagegeneration" -> string("savedPath")?.let(::add)
                "dynamictoolcall" -> this@remoteConversationAttachments["contentItems"]
                    .asArrayOrEmpty()
                    .mapNotNull { content ->
                        content.asObjectOrNull()
                            ?.takeIf { it.string("type").equals("inputImage", ignoreCase = true) }
                            ?.string("imageUrl")
                            ?.removePrefix("file://")
                    }
                    .forEach(::add)
            }
            extractText(this@remoteConversationAttachments)
                .let(::extractExplicitAttachmentPaths)
                .forEach(::add)
        }
        return candidatePaths
            .mapNotNull { value -> normalizeConversationAttachmentPath(value, projectRoot) }
            .distinct()
            .mapNotNull { path ->
                path.toRemoteConversationAttachment()?.also { descriptor ->
                    attachmentPaths[descriptor.attachmentId] = path
                }
            }
    }

    private fun extractExplicitAttachmentPaths(text: String): List<String> =
        REMOTE_ATTACHMENT_MARKER.findAll(text)
            .map { match -> match.groupValues[1].trim().trim('"', '\'') }
            .filter(String::isNotBlank)
            .toList()

    private fun normalizeConversationAttachmentPath(value: String, projectRoot: Path?): Path? = runCatching {
        val base = projectRoot ?: connectorWorkingDirectory
        Path.of(value.trim())
            .let { path -> if (path.isAbsolute) path else base.resolve(path) }
            .toAbsolutePath()
            .normalize()
            .takeIf(Files::isRegularFile)
            ?.toRealPath()
            ?.takeIf { path ->
                (projectRoot != null && path.startsWith(projectRoot)) ||
                    path.startsWith(managedAttachmentRoot)
            }
    }.getOrNull()

    private fun Path.toRemoteConversationAttachment(): RemoteConversationAttachment? = runCatching {
        val size = Files.size(this)
        val name = fileName?.toString()?.takeIf(String::isNotBlank) ?: return null
        RemoteConversationAttachment(
            attachmentId = conversationAttachmentId(this),
            kind = if (name.isRemoteImageFile()) RemoteAttachmentKind.IMAGE else RemoteAttachmentKind.FILE,
            name = name.take(MAX_ATTACHMENT_NAME_LENGTH),
            mimeType = Files.probeContentType(this) ?: name.remoteMimeType(),
            sizeBytes = size,
        )
    }.getOrNull()

    private fun extractText(item: JsonObject): String {
        item.string("text")?.let { return it }
        val content = item["content"] ?: return ""
        content.asPrimitiveString()?.let { return it }
        return content.asArrayOrEmpty()
            .mapNotNull { part ->
                part.asPrimitiveString()
                    ?: part.asObjectOrNull()?.string("text")
            }
            .filter(String::isNotBlank)
            .joinToString("\n")
    }

    private fun JsonObject.epochMillis(key: String): Long? {
        val value = this[key]?.jsonPrimitive ?: return null
        val numeric = value.longOrNull ?: value.contentOrNull?.toLongOrNull()
        if (numeric != null) return if (numeric in 1..999_999_999_999L) numeric * 1_000 else numeric
        return value.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }

    private fun String.cleanPreview(): String = replace(Regex("\\s+"), " ").trim()

    private fun JsonObject.toModelOptions(): List<RemoteModelOption> = this["data"]
        .asArrayOrEmpty()
        .mapNotNull { element ->
            val model = element.asObjectOrNull() ?: return@mapNotNull null
            if (model.boolean("hidden") == true) return@mapNotNull null
            val id = model.string("id") ?: return@mapNotNull null
            val modelName = model.string("model") ?: id
            val efforts = model["supportedReasoningEfforts"]
                .asArrayOrEmpty()
                .mapNotNull { effortElement ->
                    val effort = effortElement.asObjectOrNull() ?: return@mapNotNull null
                    val effortId = effort.string("reasoningEffort") ?: return@mapNotNull null
                    RemoteReasoningEffortOption(
                        id = effortId,
                        description = effort.string("description").orEmpty(),
                    )
                }
            RemoteModelOption(
                id = id,
                model = modelName,
                displayName = model.string("displayName") ?: modelName,
                description = model.string("description").orEmpty(),
                isDefault = model.boolean("isDefault") ?: false,
                defaultReasoningEffort = model.string("defaultReasoningEffort")
                    ?: efforts.firstOrNull()?.id.orEmpty(),
                supportedReasoningEfforts = efforts,
            )
        }

    private fun JsonObject.toSkillOptions(): List<RemoteSkillOption> = this["data"]
        .asArrayOrEmpty()
        .flatMap { entry -> entry.asObjectOrNull()?.get("skills").asArrayOrEmpty() }
        .mapNotNull { element ->
            val skill = element.asObjectOrNull() ?: return@mapNotNull null
            if (skill.boolean("enabled") == false) return@mapNotNull null
            val name = skill.string("name") ?: return@mapNotNull null
            val path = skill.string("path") ?: return@mapNotNull null
            val interfaceMetadata = skill["interface"].asObjectOrNull()
            RemoteSkillOption(
                name = name,
                displayName = interfaceMetadata?.string("displayName")?.takeIf(String::isNotBlank) ?: name,
                description = interfaceMetadata?.string("shortDescription")
                    ?: skill.string("shortDescription")
                    ?: skill.string("description").orEmpty(),
                path = path,
                scope = skill.string("scope").orEmpty(),
            )
        }
        .distinctBy(RemoteSkillOption::path)

    private fun JsonObject.toPermissionProfiles(): List<RemotePermissionProfileOption> = this["data"]
        .asArrayOrEmpty()
        .mapNotNull { element ->
            val profile = element.asObjectOrNull() ?: return@mapNotNull null
            val id = profile.string("id") ?: return@mapNotNull null
            RemotePermissionProfileOption(
                id = id,
                description = profile.string("description"),
                allowed = profile.boolean("allowed") ?: false,
            )
        }

    private fun buildTurnInput(
        text: String,
        attachments: List<StoredRemoteAttachment>,
        skill: RemoteSkillOption?,
    ): JsonArray {
        val fileAttachments = attachments.filter { it.descriptor.kind == RemoteAttachmentKind.FILE }
        val composedText = buildString {
            skill?.let { append('$').append(it.name).append('\n') }
            if (text.isNotBlank()) append(text)
            if (fileAttachments.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("电脑本地附件路径：\n")
                fileAttachments.forEach { attachment ->
                    append("- ").append(attachment.path).append('\n')
                }
            }
        }.trim()
        val inputs = buildList<JsonElement> {
            if (composedText.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", composedText)
                })
            }
            attachments
                .filter { it.descriptor.kind == RemoteAttachmentKind.IMAGE }
                .forEach { attachment ->
                    add(buildJsonObject {
                        put("type", "localImage")
                        put("path", attachment.path.toString())
                    })
                }
            skill?.let {
                add(buildJsonObject {
                    put("type", "skill")
                    put("name", it.name)
                    put("path", it.path)
                })
            }
        }
        require(inputs.isNotEmpty()) { "Message has no usable input" }
        return JsonArray(inputs)
    }

    companion object {
        const val MAX_PAGE_SIZE = 30
        const val DEFAULT_MESSAGE_LIMIT = 20
        const val MAX_MESSAGE_LENGTH = 16_000
        private const val MAX_TITLE_LENGTH = 80
        private const val MAX_PREVIEW_LENGTH = 160
        const val MAX_DOWNLOAD_ATTACHMENT_BYTES = 25L * 1024L * 1024L
        private const val MAX_ATTACHMENT_NAME_LENGTH = 180
        private const val MAX_EVENT_WAIT_MILLIS = 30_000L
        private const val EVENT_WAIT_SLICE_MILLIS = 100L
    }
}

private val REMOTE_ATTACHMENT_MARKER = Regex(
    """(?im)^(?:图片|文件|产出|附件|image|file|artifact|attachment)\s*[：:]\s*(.+)$""",
)

private fun conversationAttachmentId(path: Path): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(path.toString().lowercase().toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

private fun String.isRemoteImageFile(): Boolean = substringAfterLast('.', "")
    .lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp", "heic", "heif", "avif", "ico")

private fun String.remoteMimeType(): String = when (substringAfterLast('.', "").lowercase()) {
    "txt", "log" -> "text/plain"
    "md", "markdown" -> "text/markdown"
    "json" -> "application/json"
    "yaml", "yml" -> "application/x-yaml"
    "xml" -> "application/xml"
    "html", "htm" -> "text/html"
    "csv" -> "text/csv"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "bmp" -> "image/bmp"
    "heif" -> "image/heif"
    "heic" -> "image/heic"
    "avif" -> "image/avif"
    "ico" -> "image/x-icon"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}

private fun Path.readBoundedBytes(maxBytes: Long): ByteArray {
    if (Files.size(this) > maxBytes) throw RemoteAttachmentTooLargeException(maxBytes)
    return Files.newInputStream(this).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw RemoteAttachmentTooLargeException(maxBytes)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

class RemoteConversationAttachmentNotFoundException :
    IllegalArgumentException("Conversation attachment is unavailable")

private data class RemoteExecutionSnapshot(
    val turnId: String? = null,
    val status: RemoteExecutionStatus = RemoteExecutionStatus.IDLE,
    val partialAssistantText: String = "",
    val activeActivityTitle: String = "",
    val activeActivityText: String = "",
    val activities: List<RemoteConversationActivity> = emptyList(),
    val latestCompletionId: String? = null,
)

private class RemoteConversationRuntime {
    private val states = mutableMapOf<String, RemoteExecutionSnapshot>()
    private val executionRevision = AtomicLong(0)
    private val changes = ArrayDeque<RemoteConversationExecutionChange>()

    @Synchronized
    fun markStarted(threadId: String, turnId: String) {
        states[threadId] = (states[threadId] ?: RemoteExecutionSnapshot()).copy(
            turnId = turnId,
            status = RemoteExecutionStatus.RUNNING,
            partialAssistantText = "",
            activeActivityTitle = "正在处理",
            activeActivityText = "正在处理请求",
            activities = emptyList(),
        )
        recordChange(threadId, turnId, RemoteExecutionStatus.RUNNING)
    }

    @Synchronized
    fun snapshot(threadId: String): RemoteExecutionSnapshot? = states[threadId]

    fun revision(): Long = executionRevision.get()

    @Synchronized
    fun changesAfter(afterRevision: Long): RemoteConversationEventPage? {
        val latestRevision = executionRevision.get()
        if (latestRevision <= afterRevision) return null
        return RemoteConversationEventPage(
            revision = latestRevision,
            changes = changes.filter { it.revision > afterRevision },
        )
    }

    @Synchronized
    fun record(notification: CodexNotification) {
        if (notification.method !in TRACKED_NOTIFICATION_METHODS) return
        val turnId = notification.params.turnId()
        val threadId = notification.params.threadId()
            ?: turnId?.let { id -> states.entries.firstOrNull { it.value.turnId == id }?.key }
            ?: return
        val current = states[threadId] ?: RemoteExecutionSnapshot(turnId = turnId)
        states[threadId] = when (notification.method) {
            "turn/started" -> current.copy(
                turnId = turnId ?: current.turnId,
                status = RemoteExecutionStatus.RUNNING,
                partialAssistantText = "",
                activeActivityTitle = "正在处理",
                activeActivityText = "正在处理请求",
                activities = emptyList(),
            )
            "item/agentMessage/delta" -> current.withAgentMessageDelta(notification.params)
            "item/reasoning/summaryTextDelta",
            "item/reasoning/textDelta",
            "item/commandExecution/outputDelta",
            "item/fileChange/outputDelta",
            "item/mcpToolCall/progress",
            -> current.withActivityProgress(notification.method, notification.params)
            "item/started" -> current.withItem(notification.params, completed = false)
            "item/completed" -> current.withItem(notification.params, completed = true)
            "turn/completed" -> current.copy(
                turnId = turnId ?: current.turnId,
                status = notification.params["turn"]
                    .asObjectOrNull()
                    ?.string("status")
                    .toRemoteExecutionStatus(),
                activities = current.activities.map { activity ->
                    if (activity.status == RemoteConversationActivityStatus.RUNNING) {
                        activity.copy(status = notification.params["turn"]
                            .asObjectOrNull()
                            ?.string("status")
                            .toActivityStatus())
                    } else {
                        activity
                    }
                },
                latestCompletionId = turnId ?: current.turnId,
            )
            else -> error("Unreachable notification method: ${notification.method}")
        }
        val currentTurnId = turnId ?: current.turnId
        when (notification.method) {
            "turn/started" -> if (current.status != RemoteExecutionStatus.RUNNING) {
                recordChange(threadId, currentTurnId, RemoteExecutionStatus.RUNNING)
            }
            "turn/completed" -> if (
                currentTurnId == null || current.latestCompletionId != currentTurnId
            ) {
                recordChange(threadId, currentTurnId, states.getValue(threadId).status)
            }
        }
    }

    private fun recordChange(threadId: String, turnId: String?, status: RemoteExecutionStatus) {
        val change = RemoteConversationExecutionChange(
            revision = executionRevision.incrementAndGet(),
            threadId = threadId,
            turnId = turnId,
            status = status,
        )
        changes.addLast(change)
        while (changes.size > MAX_EXECUTION_CHANGES) changes.removeFirst()
    }

    private companion object {
        val TRACKED_NOTIFICATION_METHODS = setOf(
            "turn/started",
            "item/started",
            "item/completed",
            "item/agentMessage/delta",
            "item/reasoning/summaryTextDelta",
            "item/reasoning/textDelta",
            "item/commandExecution/outputDelta",
            "item/fileChange/outputDelta",
            "item/mcpToolCall/progress",
            "turn/completed",
        )
        const val MAX_EXECUTION_CHANGES = 256
    }
}

private data class RemoteActivitySummary(
    val kind: RemoteConversationActivityKind,
    val title: String,
    val text: String,
)

private fun RemoteExecutionSnapshot.withAgentMessageDelta(params: JsonObject): RemoteExecutionSnapshot {
    val delta = params.string("delta").orEmpty()
    val itemId = params.string("itemId")
    val existing = itemId?.let { id -> activities.firstOrNull { it.id == id } }
    if (existing?.kind == RemoteConversationActivityKind.COMMENTARY) {
        val activity = existing.copy(
            text = appendActivityText(existing.text, delta),
            status = RemoteConversationActivityStatus.RUNNING,
        )
        return copy(
            turnId = params.turnId() ?: turnId,
            status = RemoteExecutionStatus.RUNNING,
            activities = activities.upsertActivity(activity),
            activeActivityTitle = activity.title,
            activeActivityText = activity.text,
        )
    }
    return copy(
        turnId = params.turnId() ?: turnId,
        status = RemoteExecutionStatus.RUNNING,
        partialAssistantText = partialAssistantText + delta,
        activeActivityTitle = "正在组织回复",
        activeActivityText = delta.cleanActivityText().ifBlank { activeActivityText },
    )
}

private fun RemoteExecutionSnapshot.withActivityProgress(
    method: String,
    params: JsonObject,
): RemoteExecutionSnapshot {
    val itemId = params.string("itemId") ?: return this
    val existing = activities.firstOrNull { it.id == itemId }
    val seed = existing ?: method.progressActivity(itemId)
    val progress = params.string("delta") ?: params.string("message").orEmpty()
    val activity = seed.copy(
        text = appendActivityText(seed.text, progress),
        status = RemoteConversationActivityStatus.RUNNING,
    )
    return copy(
        turnId = params.turnId() ?: turnId,
        status = RemoteExecutionStatus.RUNNING,
        activities = activities.upsertActivity(activity),
        activeActivityTitle = activity.title,
        activeActivityText = activity.text,
    )
}

private fun RemoteExecutionSnapshot.withItem(
    params: JsonObject,
    completed: Boolean,
): RemoteExecutionSnapshot {
    val activity = params["item"]
        .asObjectOrNull()
        ?.toRemoteActivity(completed)
        ?: return copy(
            turnId = params.turnId() ?: turnId,
            status = if (completed) status else RemoteExecutionStatus.RUNNING,
        )
    val existing = activities.firstOrNull { it.id == activity.id }
    val merged = activity.copy(
        text = mergeActivityText(existing?.text.orEmpty(), activity.text),
    )
    return copy(
        turnId = params.turnId() ?: turnId,
        status = if (completed) status else RemoteExecutionStatus.RUNNING,
        activities = activities.upsertActivity(merged),
        activeActivityTitle = merged.title,
        activeActivityText = merged.text,
    )
}

private fun String.progressActivity(itemId: String): RemoteConversationActivity = when (this) {
    "item/reasoning/summaryTextDelta", "item/reasoning/textDelta" -> RemoteConversationActivity(
        id = itemId,
        kind = RemoteConversationActivityKind.THINKING,
        title = "思考",
        status = RemoteConversationActivityStatus.RUNNING,
    )
    "item/commandExecution/outputDelta" -> RemoteConversationActivity(
        id = itemId,
        kind = RemoteConversationActivityKind.COMMAND,
        title = "执行代码",
        status = RemoteConversationActivityStatus.RUNNING,
    )
    "item/fileChange/outputDelta" -> RemoteConversationActivity(
        id = itemId,
        kind = RemoteConversationActivityKind.FILE_CHANGE,
        title = "修改文件",
        status = RemoteConversationActivityStatus.RUNNING,
    )
    "item/mcpToolCall/progress" -> RemoteConversationActivity(
        id = itemId,
        kind = RemoteConversationActivityKind.TOOL,
        title = "调用工具",
        status = RemoteConversationActivityStatus.RUNNING,
    )
    else -> RemoteConversationActivity(
        id = itemId,
        kind = RemoteConversationActivityKind.OTHER,
        title = "正在处理",
        status = RemoteConversationActivityStatus.RUNNING,
    )
}

private fun List<RemoteConversationActivity>.upsertActivity(
    activity: RemoteConversationActivity,
): List<RemoteConversationActivity> {
    val index = indexOfFirst { it.id == activity.id }
    val updated = if (index < 0) {
        this + activity
    } else {
        toMutableList().apply { this[index] = activity }
    }
    return updated.takeLast(MAX_REMOTE_ACTIVITIES)
}

private fun mergeActivities(
    persisted: List<RemoteConversationActivity>,
    live: List<RemoteConversationActivity>,
): List<RemoteConversationActivity> = live.fold(persisted) { result, activity ->
    result.upsertActivity(activity)
}

private fun appendActivityText(current: String, addition: String): String {
    val next = addition.cleanActivityText()
    if (next.isBlank()) return current
    if (current.isBlank()) return next
    if (current.endsWith(next)) return current
    return "$current\n$next".takeLast(MAX_ACTIVITY_TEXT_LENGTH)
}

private fun mergeActivityText(current: String, replacement: String): String {
    val previous = current.cleanActivityText()
    val next = replacement.cleanActivityText()
    return when {
        next.isBlank() -> previous
        previous.isBlank() -> next
        previous == next -> next
        next.contains(previous) -> next
        previous.contains(next) -> previous
        else -> "$next\n$previous".takeLast(MAX_ACTIVITY_TEXT_LENGTH)
    }
}

private fun JsonObject.activitySummary(): RemoteActivitySummary? = when (string("type")?.lowercase()) {
    "commandexecution" -> RemoteActivitySummary(
        RemoteConversationActivityKind.COMMAND,
        "执行代码",
        listOfNotNull(string("command"), string("aggregatedOutput"))
            .joinToString("\n")
            .cleanActivityText(),
    )
    "websearch" -> RemoteActivitySummary(
        RemoteConversationActivityKind.WEB_SEARCH,
        "搜索网页",
        string("query")?.cleanActivityText().orEmpty(),
    )
    "mcptoolcall" -> RemoteActivitySummary(
        RemoteConversationActivityKind.TOOL,
        "调用工具",
        listOfNotNull(string("server"), string("tool") ?: string("name"))
            .joinToString(" · ")
            .cleanActivityText(),
    )
    "dynamictoolcall" -> RemoteActivitySummary(
        RemoteConversationActivityKind.TOOL,
        "调用工具",
        listOfNotNull(string("namespace"), string("tool"))
            .joinToString(" · ")
            .cleanActivityText(),
    )
    "collabagenttoolcall", "subagentactivity" -> RemoteActivitySummary(
        RemoteConversationActivityKind.TOOL,
        "协同处理",
        (string("tool") ?: string("agentPath") ?: string("prompt"))
            ?.cleanActivityText()
            .orEmpty(),
    )
    "filechange" -> RemoteActivitySummary(
        RemoteConversationActivityKind.FILE_CHANGE,
        "修改文件",
        this["changes"].asArrayOrEmpty()
            .mapNotNull { it.asObjectOrNull()?.string("path") }
            .distinct()
            .joinToString("、")
            .cleanActivityText(),
    )
    "reasoning" -> RemoteActivitySummary(
        RemoteConversationActivityKind.THINKING,
        "思考",
        listOf("summary", "content")
            .flatMap { key -> this[key].asArrayOrEmpty() }
            .joinToString(" ") { it.asPrimitiveString().orEmpty() }
            .cleanActivityText(),
    )
    "agentmessage" -> if (string("phase").equals("commentary", ignoreCase = true)) {
        RemoteActivitySummary(
            RemoteConversationActivityKind.COMMENTARY,
            "执行说明",
            string("text")?.cleanActivityText().orEmpty(),
        )
    } else {
        null
    }
    "plan" -> RemoteActivitySummary(
        RemoteConversationActivityKind.PLAN,
        "更新计划",
        string("text")?.cleanActivityText().orEmpty(),
    )
    "imagegeneration" -> RemoteActivitySummary(
        RemoteConversationActivityKind.IMAGE,
        "生成图片",
        (string("savedPath") ?: string("revisedPrompt") ?: string("result"))
            ?.cleanActivityText()
            .orEmpty(),
    )
    "imageview" -> RemoteActivitySummary(
        RemoteConversationActivityKind.IMAGE,
        "查看图片",
        string("path")?.cleanActivityText().orEmpty(),
    )
    else -> null
}

private fun JsonObject.toRemoteActivity(completed: Boolean): RemoteConversationActivity? {
    val summary = activitySummary() ?: return null
    val id = string("id") ?: return null
    return RemoteConversationActivity(
        id = id,
        kind = summary.kind,
        title = summary.title,
        text = summary.text,
        status = string("status").toActivityStatus(completed),
    )
}

private fun String.cleanActivityText(): String = replace(Regex("\\s+"), " ")
    .trim()
    .take(MAX_ACTIVITY_TEXT_LENGTH)

private fun JsonObject.toExecutionSnapshot(): RemoteExecutionSnapshot {
    val executionStatus = string("status").toRemoteExecutionStatus()
    val turnItems = this["items"]
        .asArrayOrEmpty()
        .mapNotNull(JsonElement::asObjectOrNull)
    val activities = turnItems.mapIndexedNotNull { index, item ->
        item.toRemoteActivity(
            completed = executionStatus != RemoteExecutionStatus.RUNNING || index < turnItems.lastIndex,
        )
    }
    val activeActivity = activities.lastOrNull()
    return RemoteExecutionSnapshot(
        turnId = string("id"),
        status = executionStatus,
        latestCompletionId = string("id").takeIf { executionStatus in TERMINAL_EXECUTION_STATUSES },
        activeActivityText = activeActivity?.text.orEmpty(),
        activeActivityTitle = activeActivity?.title.orEmpty(),
        activities = activities,
    )
}

private fun String?.toActivityStatus(completed: Boolean = true): RemoteConversationActivityStatus =
    when (this?.lowercase()) {
        "active", "inprogress", "in_progress", "running" -> RemoteConversationActivityStatus.RUNNING
        "interrupted", "cancelled", "canceled" -> RemoteConversationActivityStatus.INTERRUPTED
        "failed", "declined" -> RemoteConversationActivityStatus.FAILED
        "completed" -> RemoteConversationActivityStatus.COMPLETED
        else -> if (completed) {
            RemoteConversationActivityStatus.COMPLETED
        } else {
            RemoteConversationActivityStatus.RUNNING
        }
    }

private const val MAX_REMOTE_ACTIVITIES = 100
private const val MAX_ACTIVITY_TEXT_LENGTH = 4_000

private fun String?.toRemoteExecutionStatus(): RemoteExecutionStatus = when (this?.lowercase()) {
    "active", "inprogress", "in_progress", "running" -> RemoteExecutionStatus.RUNNING
    "completed" -> RemoteExecutionStatus.COMPLETED
    "interrupted" -> RemoteExecutionStatus.INTERRUPTED
    "failed" -> RemoteExecutionStatus.FAILED
    else -> RemoteExecutionStatus.IDLE
}

private val TERMINAL_EXECUTION_STATUSES = setOf(
    RemoteExecutionStatus.COMPLETED,
    RemoteExecutionStatus.INTERRUPTED,
    RemoteExecutionStatus.FAILED,
)

private fun JsonElement?.statusText(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonObject -> string("type") ?: string("status") ?: string("state")
    else -> null
}

private fun JsonObject.threadId(): String? =
    string("threadId")
        ?: this["thread"].asObjectOrNull()?.string("id")
        ?: this["turn"].asObjectOrNull()?.string("threadId")
        ?: this["item"].asObjectOrNull()?.string("threadId")

private fun JsonObject.turnId(): String? =
    string("turnId")
        ?: this["turn"].asObjectOrNull()?.string("id")
        ?: this["item"].asObjectOrNull()?.string("turnId")

class RemoteConversationNotFoundException(threadId: String) :
    IllegalStateException("Codex thread was not found: $threadId")

class RemoteConversationControlUnavailableException :
    IllegalStateException("Codex remote control is unavailable")

class RemoteConversationBusyException(threadId: String) :
    IllegalStateException("Codex thread already has an active turn: $threadId")

class RemoteConversationNotRunningException(threadId: String) :
    IllegalStateException("Codex thread does not have an active turn: $threadId")

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement.asObject(): JsonObject = this as? JsonObject
    ?: throw IllegalArgumentException("Codex response must be a JSON object")

private fun JsonElement?.asArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

private fun JsonElement.asPrimitiveString(): String? =
    runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
