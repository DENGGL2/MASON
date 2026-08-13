package com.denggl2.mason.ui.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denggl2.mason.protocol.RemoteAttachmentKind
import com.denggl2.mason.protocol.RemoteComposerOptions
import com.denggl2.mason.protocol.RemoteConversationAttachment
import com.denggl2.mason.protocol.RemoteConversationDetail
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.protocol.RemoteMessageRequest
import com.denggl2.mason.protocol.RemoteModelOption
import com.denggl2.mason.protocol.RemoteSkillOption
import com.denggl2.mason.protocol.RemoteSkillSelection
import com.denggl2.mason.sync.SyncManager
import com.denggl2.mason.sync.remote.PairedConnectorStore
import com.denggl2.mason.sync.remote.PinnedConnectorClient
import com.denggl2.mason.sync.remote.RemotePairingException
import com.denggl2.mason.sync.security.AndroidDeviceIdentityStore
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.ArtifactStore
import com.denggl2.mason.ui.chat.AttachmentKind
import com.denggl2.mason.ui.chat.PendingAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RemoteConversationUiState(
    val isLoading: Boolean = false,
    val detail: RemoteConversationDetail? = null,
    val connectorName: String = "电脑",
    val draft: String = "",
    val attachments: List<PendingAttachment> = emptyList(),
    val selectedSkill: RemoteSkillOption? = null,
    val composerOptions: RemoteComposerOptions = RemoteComposerOptions(),
    val selectedModelId: String? = null,
    val selectedReasoningEffort: String? = null,
    val selectedPermissionProfileId: String? = null,
    val isOptionsLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val submittingLabel: String? = null,
    val errorMessage: String? = null,
    val optionsErrorMessage: String? = null,
    val previewArtifact: ArtifactMetadata? = null,
    val previewAttachmentId: String? = null,
)

@HiltViewModel
class RemoteConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val connectorStore: PairedConnectorStore,
    private val syncManager: SyncManager,
    private val readStore: RemoteConversationReadStore,
    private val artifactStore: ArtifactStore,
    private val composerOptionsStore: RemoteComposerOptionsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])
    private val initialConnector = connectorStore.load()
    private val initialOptions = initialConnector?.connectorDeviceId?.let(composerOptionsStore::read)
    private val initialModel = initialOptions?.models?.firstOrNull { it.isDefault }
        ?: initialOptions?.models?.firstOrNull()
    private val _uiState = MutableStateFlow(
        RemoteConversationUiState(
            connectorName = initialConnector?.displayName ?: "电脑",
            composerOptions = initialOptions ?: RemoteComposerOptions(),
            selectedModelId = initialModel?.id,
            selectedReasoningEffort = initialModel?.defaultReasoningEffort
                ?.takeIf { effort ->
                    initialModel.supportedReasoningEfforts.any { it.id == effort }
                }
                ?: initialModel?.supportedReasoningEfforts?.firstOrNull()?.id,
            selectedPermissionProfileId = initialOptions?.permissionProfiles
                ?.firstOrNull { it.allowed }
                ?.id,
        ),
    )
    internal val uiState: StateFlow<RemoteConversationUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var optionsJob: Job? = null
    private var connectorClient: PinnedConnectorClient? = null
    private var deviceId: String? = null
    private var modelSelectedByUser = false
    private var reasoningSelectedByUser = false
    private var permissionSelectedByUser = false

    init {
        load()
        loadComposerOptions(force = initialOptions != null)
        viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                if (_uiState.value.detail != null) refresh(showLoading = false)
            }
        }
    }

    fun load() {
        refresh(showLoading = _uiState.value.detail == null)
        loadComposerOptions()
    }

    fun updateDraft(value: String) {
        _uiState.value = _uiState.value.copy(draft = value, errorMessage = null)
    }

    internal fun addAttachment(kind: AttachmentKind, uriValue: String) {
        val state = _uiState.value
        if (state.attachments.size >= MAX_ATTACHMENTS) {
            _uiState.value = state.copy(errorMessage = "每次最多添加 $MAX_ATTACHMENTS 个附件")
            return
        }
        if (state.attachments.any { it.uri == uriValue }) return
        val uri = Uri.parse(uriValue)
        val metadata = queryAttachmentMetadata(uri)
        if (metadata.sizeBytes != null && metadata.sizeBytes > MAX_ATTACHMENT_BYTES) {
            _uiState.value = state.copy(errorMessage = "单个附件不能超过 20 MB")
            return
        }
        _uiState.value = state.copy(
            attachments = state.attachments + PendingAttachment(
                kind = kind,
                name = metadata.name,
                uri = uriValue,
            ),
            errorMessage = null,
        )
    }

    fun removeAttachment(index: Int) {
        _uiState.value = _uiState.value.copy(
            attachments = _uiState.value.attachments.filterIndexed { itemIndex, _ -> itemIndex != index },
            errorMessage = null,
        )
    }

    fun selectSkill(skill: RemoteSkillOption?) {
        _uiState.value = _uiState.value.copy(selectedSkill = skill, errorMessage = null)
    }

    fun selectModel(modelId: String) {
        val state = _uiState.value
        val model = state.composerOptions.models.firstOrNull { it.id == modelId } ?: return
        if (!shouldSelectRemoteModel(state.selectedModelId, model.id)) {
            if (state.errorMessage != null) {
                _uiState.value = state.copy(errorMessage = null)
            }
            return
        }
        _uiState.value = state.copy(
            selectedModelId = model.id,
            selectedReasoningEffort = model.defaultReasoningEffort
                .takeIf { default -> model.supportedReasoningEfforts.any { it.id == default } }
                ?: model.supportedReasoningEfforts.firstOrNull()?.id,
            errorMessage = null,
        )
        modelSelectedByUser = true
        reasoningSelectedByUser = true
    }

    fun selectReasoningEffort(effort: String) {
        val model = selectedModel() ?: return
        if (model.supportedReasoningEfforts.none { it.id == effort }) return
        _uiState.value = _uiState.value.copy(selectedReasoningEffort = effort, errorMessage = null)
        reasoningSelectedByUser = true
    }

    fun selectPermissionProfile(profileId: String?) {
        if (
            profileId != null &&
            _uiState.value.composerOptions.permissionProfiles.none { it.id == profileId && it.allowed }
        ) return
        _uiState.value = _uiState.value.copy(
            selectedPermissionProfileId = profileId,
            errorMessage = null,
        )
        permissionSelectedByUser = true
    }

    fun refreshComposerOptions() {
        loadComposerOptions(force = true)
    }

    fun previewAttachment(attachment: RemoteConversationAttachment) {
        if (_uiState.value.previewAttachmentId != null) return
        _uiState.value = _uiState.value.copy(
            previewAttachmentId = attachment.attachmentId,
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching {
                val (client, localDeviceId) = remoteClient()
                val bytes = client.downloadConversationAttachment(
                    deviceId = localDeviceId,
                    threadId = threadId,
                    attachmentId = attachment.attachmentId,
                )
                ByteArrayInputStream(bytes).use { input ->
                    artifactStore.saveRemoteConversationArtifact(
                        cacheKey = "${threadId}_${attachment.attachmentId}",
                        fileName = attachment.name,
                        input = input,
                        mimeType = attachment.mimeType ?: "application/octet-stream",
                        expectedBytes = attachment.sizeBytes,
                        maxBytes = MAX_REMOTE_PREVIEW_BYTES,
                    )
                }
            }.onSuccess { artifact ->
                _uiState.value = _uiState.value.copy(
                    previewArtifact = artifact,
                    previewAttachmentId = null,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    previewAttachmentId = null,
                    errorMessage = error.displayMessage("无法从电脑读取文件"),
                )
            }
        }
    }

    fun dismissPreview() {
        _uiState.value = _uiState.value.copy(previewArtifact = null)
    }

    fun sendMessage() {
        val state = _uiState.value
        val hasInput = state.draft.isNotBlank() || state.attachments.isNotEmpty() || state.selectedSkill != null
        if (
            !hasInput ||
            state.isSubmitting ||
            state.detail?.executionStatus == RemoteExecutionStatus.RUNNING
        ) return
        _uiState.value = state.copy(
            isSubmitting = true,
            submittingLabel = if (state.attachments.isEmpty()) "正在发送" else "正在上传附件",
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching {
                val (client, localDeviceId) = remoteClient()
                val uploaded = state.attachments.map { attachment ->
                    val uri = Uri.parse(attachment.uri)
                    val bytes = readAttachmentBytes(uri)
                    client.uploadAttachment(
                        deviceId = localDeviceId,
                        kind = when (attachment.kind) {
                            AttachmentKind.Image -> RemoteAttachmentKind.IMAGE
                            AttachmentKind.File -> RemoteAttachmentKind.FILE
                        },
                        name = attachment.name,
                        mimeType = context.contentResolver.getType(uri),
                        bytes = bytes,
                    )
                }
                client.sendMessage(
                    deviceId = localDeviceId,
                    threadId = threadId,
                    request = RemoteMessageRequest(
                        text = state.draft.trim(),
                        attachmentIds = uploaded.map { it.attachmentId },
                        skill = state.selectedSkill?.let {
                            RemoteSkillSelection(name = it.name, path = it.path)
                        },
                        modelId = state.selectedModelId,
                        reasoningEffort = state.selectedReasoningEffort,
                        permissionProfileId = state.selectedPermissionProfileId,
                    ),
                )
            }.onSuccess { execution ->
                _uiState.value = _uiState.value.copy(
                    draft = "",
                    attachments = emptyList(),
                    selectedSkill = null,
                    isSubmitting = false,
                    submittingLabel = null,
                    detail = _uiState.value.detail?.copy(
                        executionStatus = execution.status,
                        activeTurnId = execution.turnId,
                    ),
                )
                refresh(showLoading = false)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    submittingLabel = null,
                    errorMessage = error.displayMessage("无法发送到电脑"),
                )
            }
        }
    }

    fun interrupt() {
        val state = _uiState.value
        if (
            state.isSubmitting ||
            state.detail?.executionStatus != RemoteExecutionStatus.RUNNING
        ) return
        _uiState.value = state.copy(
            isSubmitting = true,
            submittingLabel = "正在停止",
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching {
                val (client, localDeviceId) = remoteClient()
                client.interrupt(localDeviceId, threadId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false, submittingLabel = null)
                refresh(showLoading = false)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    submittingLabel = null,
                    errorMessage = error.displayMessage("无法停止电脑任务"),
                )
            }
        }
    }

    private fun refresh(showLoading: Boolean) {
        if (refreshJob?.isActive == true) return
        if (showLoading) _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        refreshJob = viewModelScope.launch {
            runCatching {
                val (client, localDeviceId) = remoteClient()
                var lastError: Throwable? = null
                repeat(if (showLoading && _uiState.value.detail == null) INITIAL_READ_ATTEMPTS else 1) { attempt ->
                    runCatching {
                        client.readConversation(deviceId = localDeviceId, threadId = threadId)
                    }.onSuccess { return@runCatching it }
                        .onFailure { lastError = it }
                    if (attempt < INITIAL_READ_ATTEMPTS - 1) delay(INITIAL_READ_RETRY_MILLIS)
                }
                throw lastError ?: IllegalStateException("Unable to read the computer conversation")
            }.onSuccess { detail ->
                if (detail.executionStatus == RemoteExecutionStatus.COMPLETED) {
                    connectorStore.load()?.connectorDeviceId?.let { connectorDeviceId ->
                        readStore.markCompletionSeen(
                            connectorDeviceId = connectorDeviceId,
                            conversation = detail.conversation,
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    detail = detail,
                    errorMessage = null,
                )
            }.onFailure { error ->
                val state = _uiState.value
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = if (state.detail == null) {
                        error.displayMessage("无法加载电脑对话")
                    } else {
                        state.errorMessage
                    },
                )
            }
        }
    }

    private fun loadComposerOptions(force: Boolean = false) {
        if (!force && (_uiState.value.isOptionsLoading || _uiState.value.composerOptions.models.isNotEmpty())) return
        if (optionsJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(isOptionsLoading = true)
        optionsJob = viewModelScope.launch {
            runCatching {
                val (client, localDeviceId) = remoteClient()
                client.composerOptions(deviceId = localDeviceId, threadId = threadId)
            }.onSuccess { freshOptions ->
                val options = connectorStore.load()?.connectorDeviceId?.let { connectorDeviceId ->
                    composerOptionsStore.mergeAndWrite(connectorDeviceId, freshOptions)
                } ?: freshOptions
                val state = _uiState.value
                val selectedModel = resolveRemoteModel(
                    options = options,
                    authoritativeModelId = freshOptions.currentModelId,
                    selectedModelId = state.selectedModelId,
                    preserveUserSelection = modelSelectedByUser,
                )
                val selectedEffort = resolveRemoteReasoningEffort(
                    model = selectedModel,
                    authoritativeEffort = freshOptions.currentReasoningEffort,
                    selectedEffort = state.selectedReasoningEffort,
                    preserveUserSelection = reasoningSelectedByUser,
                )
                val selectedPermission = resolveRemotePermissionProfile(
                    options = options,
                    authoritativePermissionId = freshOptions.currentPermissionProfileId,
                    selectedPermissionId = state.selectedPermissionProfileId,
                    preserveUserSelection = permissionSelectedByUser,
                )
                _uiState.value = state.copy(
                    composerOptions = options,
                    selectedModelId = selectedModel?.id,
                    selectedReasoningEffort = selectedEffort,
                    selectedPermissionProfileId = selectedPermission,
                    selectedSkill = state.selectedSkill?.let { selected ->
                        options.skills.firstOrNull { it.path == selected.path }
                    },
                    isOptionsLoading = false,
                    optionsErrorMessage = null,
                    errorMessage = null,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isOptionsLoading = false,
                    optionsErrorMessage = error.displayMessage("无法读取电脑端 Codex 配置"),
                )
            }
        }
    }

    private fun selectedModel(): RemoteModelOption? = _uiState.value.composerOptions.models
        .firstOrNull { it.id == _uiState.value.selectedModelId }

    private suspend fun remoteClient(): Pair<PinnedConnectorClient, String> {
        val client = connectorClient ?: run {
            val connector = connectorStore.load() ?: error("设备已取消配对")
            _uiState.value = _uiState.value.copy(connectorName = connector.displayName)
            PinnedConnectorClient(connector, AndroidDeviceIdentityStore()).also {
                connectorClient = it
            }
        }
        val localDeviceId = deviceId ?: syncManager.getLocalDeviceId().also { deviceId = it }
        return client to localDeviceId
    }

    private fun queryAttachmentMetadata(uri: Uri): AttachmentMetadata {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        return AttachmentMetadata(
            name = name?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "附件",
            sizeBytes = size,
        )
    }

    private suspend fun readAttachmentBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取附件")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_ATTACHMENT_BYTES) { "单个附件不能超过 20 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun Throwable.displayMessage(fallback: String): String = when (
        (this as? RemotePairingException)?.errorCode
    ) {
        "CONVERSATION_ACTIVE_WRITER" -> "此对话正在被电脑端 Codex 占用，当前只能查看"
        "CONVERSATION_BUSY" -> "此对话已有任务正在执行"
        "NO_ACTIVE_EXECUTION" -> "电脑任务已经结束"
        "ATTACHMENT_TOO_LARGE" -> "单个附件不能超过 20 MB"
        "ATTACHMENT_QUOTA_EXCEEDED" -> "电脑端附件暂存空间已满"
        "ATTACHMENT_NOT_FOUND" -> "电脑上的文件已移动或不可用"
        else -> message?.takeIf(String::isNotBlank) ?: fallback
    }

    private data class AttachmentMetadata(
        val name: String,
        val sizeBytes: Long?,
    )

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1_500L
        const val INITIAL_READ_ATTEMPTS = 5
        const val INITIAL_READ_RETRY_MILLIS = 300L
        const val MAX_ATTACHMENTS = 4
        const val MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L
        const val MAX_REMOTE_PREVIEW_BYTES = 25L * 1024L * 1024L
    }
}
