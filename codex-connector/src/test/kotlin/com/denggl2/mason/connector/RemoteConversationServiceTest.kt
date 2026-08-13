package com.denggl2.mason.connector

import com.denggl2.mason.protocol.CodexOwnership
import com.denggl2.mason.protocol.CodexThreadBinding
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.RemoteAttachmentKind
import com.denggl2.mason.protocol.RemoteConversationActivityKind
import com.denggl2.mason.protocol.RemoteConversationActivityStatus
import com.denggl2.mason.protocol.RemoteConversationCreateRequest
import com.denggl2.mason.protocol.RemoteConversationRole
import com.denggl2.mason.protocol.RemoteExecutionStatus
import com.denggl2.mason.protocol.RemoteMessageRequest
import com.denggl2.mason.protocol.RemoteSkillSelection
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class RemoteConversationServiceTest {
    @Test
    fun projectsPagedThreadListWithoutLeakingRawCodexJson() = withStore { store ->
        store.register(
            CodexThreadBinding(
                conversationId = "conversation-1",
                deviceId = store.deviceId,
                codexThreadId = "thread-1",
                ownership = CodexOwnership.MASON_MANAGED,
                protocolVersion = "1",
            ),
        )
        val api = FakeThreadHistoryApi(
            listResponse = json(
                """{
                    "data": [
                        {"id":"thread-1","name":"计划","preview":"第一条\n预览","updatedAt":100,"cwd":"D:/Work/One","isPinned":true,"status":{"type":"active"}},
                        {"id":"thread-2","preview":"第二条会话","updated_at":"2026-07-30T00:00:00Z","turns":[{"status":"completed"}]},
                        {"id":"thread-3","preview":"历史会话","status":{"type":"notLoaded"}}
                    ],
                    "nextCursor":"cursor-2"
                }""",
            ),
            readResponse = json("{}"),
        )

        val page = runBlocking {
            RemoteConversationService(api, store).listConversations(limit = 3, cursor = "cursor-1")
        }

        assertEquals(3, api.lastLimit)
        assertEquals("cursor-1", api.lastCursor)
        assertEquals("cursor-2", page.nextCursor)
        assertEquals("计划", page.conversations[0].title)
        assertEquals("第一条 预览", page.conversations[0].preview)
        assertEquals(100_000, page.conversations[0].updatedAt)
        assertEquals(CodexOwnership.MASON_MANAGED, page.conversations[0].ownership)
        assertTrue(page.conversations[0].isPinned)
        assertFalse(page.conversations[1].isPinned)
        assertEquals(CodexOwnership.EXTERNAL_HISTORY_ONLY, page.conversations[1].ownership)
        assertEquals(RemoteExecutionStatus.RUNNING, page.conversations[0].executionStatus)
        assertEquals(RemoteExecutionStatus.COMPLETED, page.conversations[1].executionStatus)
        assertEquals(RemoteExecutionStatus.IDLE, page.conversations[2].executionStatus)
        assertTrue(page.conversations[1].updatedAt > 0)
    }

    @Test
    fun liveRuntimeStatusOverridesThreadListUntilCompletion() = withStore { store ->
        val api = FakeThreadHistoryApi(
            listResponse = json(
                """{"data":[{"id":"thread-1","preview":"测试会话","status":"idle"}]}""",
            ),
            readResponse = json("{}"),
        )
        val service = RemoteConversationService(api, store)

        service.record(notification("turn/started", "thread-1", "turn-1", ""))
        assertEquals(
            RemoteExecutionStatus.RUNNING,
            runBlocking { service.listConversations(limit = 3, cursor = null) }
                .conversations.single().executionStatus,
        )

        service.record(
            CodexNotification(
                method = "turn/completed",
                params = jsonObject(
                    """{"threadId":"thread-1","turn":{"id":"turn-1","status":"completed"}}""",
                ),
            ),
        )
        assertEquals(
            RemoteExecutionStatus.COMPLETED,
            runBlocking { service.listConversations(limit = 3, cursor = null) }
                .conversations.single().executionStatus,
        )
    }

    @Test
    fun executionEventsExposeStartedAndCompletedTurnVersions() = withStore { store ->
        val service = RemoteConversationService(
            api = FakeThreadHistoryApi(
                listResponse = json("{}"),
                readResponse = json("{}"),
            ),
            store = store,
        )

        service.record(notification("turn/started", "thread-1", "turn-1", ""))
        val started = runBlocking { service.conversationEvents(afterRevision = 0, waitMillis = 0) }
        assertEquals(1, started.changes.size)
        assertEquals(RemoteExecutionStatus.RUNNING, started.changes.single().status)
        assertEquals("turn-1", started.changes.single().turnId)

        service.record(
            CodexNotification(
                method = "turn/completed",
                params = jsonObject(
                    """{"threadId":"thread-1","turn":{"id":"turn-1","status":"completed"}}""",
                ),
            ),
        )
        val completed = runBlocking {
            service.conversationEvents(afterRevision = started.revision, waitMillis = 0)
        }
        assertEquals(1, completed.changes.size)
        assertEquals(RemoteExecutionStatus.COMPLETED, completed.changes.single().status)
        assertEquals("turn-1", completed.changes.single().turnId)
        assertTrue(completed.revision > started.revision)
    }

    @Test
    fun readsOnlyRecentUserAndAssistantMessagesAcrossKnownShapes() = withStore { store ->
        val api = FakeThreadHistoryApi(
            listResponse = json("{}"),
            readResponse = json(
                """{
                    "thread": {
                        "id":"thread-1",
                        "preview":"测试会话",
                        "turns":[
                            {"items":[
                                {"type":"userMessage","content":[{"type":"text","text":"问题一"}]},
                                {"type":"reasoning","summary":["hidden"]},
                                {"type":"agentMessage","text":"回答一"},
                                {"role":"user","content":"问题二"}
                            ]}
                        ]
                    }
                }""",
            ),
        )

        val detail = runBlocking {
            RemoteConversationService(api, store, messageLimit = 2).readConversation("thread-1")
        }

        assertTrue(detail.hasEarlierMessages)
        assertEquals(listOf(RemoteConversationRole.ASSISTANT, RemoteConversationRole.USER), detail.messages.map { it.role })
        assertEquals(listOf("回答一", "问题二"), detail.messages.map { it.text })
        assertFalse(detail.messages.any { it.text.contains("hidden") })
    }

    @Test
    fun sendsStreamsAndInterruptsAnExistingThread() = withStore { store ->
        val api = FakeRemoteControlApi(
            readResponse = json(
                """{
                    "thread": {
                        "id":"thread-1",
                        "preview":"测试会话",
                        "turns":[{"id":"turn-old","status":"completed","items":[]}]
                    }
                }""",
            ),
        )
        val service = RemoteConversationService(api, store)

        val started = runBlocking { service.sendMessage("thread-1", " 继续处理 ") }
        service.record(notification("item/agentMessage/delta", "thread-1", "turn-new", "正在"))
        service.record(notification("item/agentMessage/delta", "thread-1", "turn-new", "处理"))
        val running = runBlocking { service.readConversation("thread-1") }
        val interrupted = runBlocking { service.interrupt("thread-1") }
        service.record(
            CodexNotification(
                method = "turn/completed",
                params = jsonObject(
                    """{
                        "threadId":"thread-1",
                        "turn":{"id":"turn-new","status":"interrupted"}
                    }""",
                ),
            ),
        )
        val completed = runBlocking { service.readConversation("thread-1") }

        assertEquals("thread-1", api.resumedThreadId)
        assertEquals("继续处理", api.startedText)
        assertEquals("turn-new", started.turnId)
        assertEquals(RemoteExecutionStatus.RUNNING, running.executionStatus)
        assertEquals("正在处理", running.messages.single().text)
        assertEquals("turn-new", api.interruptedTurnId)
        assertEquals(RemoteExecutionStatus.RUNNING, interrupted.status)
        assertEquals(RemoteExecutionStatus.INTERRUPTED, completed.executionStatus)
    }

    @Test
    fun exposesCurrentRunningActivityFromAppServerItems() = withStore { store ->
        val api = FakeRemoteControlApi(
            readResponse = json(
                """{
                    "thread": {
                        "id":"thread-1",
                        "preview":"测试会话",
                        "turns":[{"id":"turn-1","status":"inProgress","items":[]}]
                    }
                }""",
            ),
        )
        val service = RemoteConversationService(api, store)

        service.record(notification("turn/started", "thread-1", "turn-1", ""))
        service.record(
            CodexNotification(
                method = "item/started",
                params = jsonObject(
                    """{
                        "threadId":"thread-1",
                        "turnId":"turn-1",
                        "item":{"id":"item-1","type":"commandExecution","command":"npm test"}
                    }""",
                ),
            ),
        )

        val detail = runBlocking { service.readConversation("thread-1") }

        assertEquals(RemoteExecutionStatus.RUNNING, detail.executionStatus)
        assertEquals("执行代码", detail.activeActivityTitle)
        assertEquals("npm test", detail.activeActivityText)
    }

    @Test
    fun retainsMultipleLiveActivitiesAndCompletesThemByItemId() = withStore { store ->
        val service = RemoteConversationService(
            api = FakeThreadHistoryApi(
                listResponse = json("{}"),
                readResponse = json(
                    """{
                        "thread": {
                            "id":"thread-1",
                            "preview":"测试会话",
                            "turns":[{"id":"turn-1","status":"inProgress","items":[]}]
                        }
                    }""",
                ),
            ),
            store = store,
        )

        service.record(notification("turn/started", "thread-1", "turn-1", ""))
        service.record(itemNotification(
            method = "item/started",
            item = """{"id":"reasoning-1","type":"reasoning","summary":[],"content":[]}""",
        ))
        service.record(itemProgressNotification(
            method = "item/reasoning/summaryTextDelta",
            itemId = "reasoning-1",
            key = "delta",
            value = "检查现有实现",
        ))
        service.record(itemNotification(
            method = "item/completed",
            item = """{
                "id":"reasoning-1","type":"reasoning",
                "summary":["检查现有实现"],"content":[]
            }""",
        ))
        service.record(itemNotification(
            method = "item/started",
            item = """{
                "id":"command-1","type":"commandExecution","command":"npm test",
                "status":"inProgress","commandActions":[],"cwd":"D:/Work"
            }""",
        ))
        service.record(itemProgressNotification(
            method = "item/commandExecution/outputDelta",
            itemId = "command-1",
            key = "delta",
            value = "1 test passed",
        ))

        val running = runBlocking { service.readConversation("thread-1") }
        assertEquals(2, running.activities.size)
        assertEquals(RemoteConversationActivityKind.THINKING, running.activities[0].kind)
        assertEquals(RemoteConversationActivityStatus.COMPLETED, running.activities[0].status)
        assertEquals(RemoteConversationActivityKind.COMMAND, running.activities[1].kind)
        assertEquals(RemoteConversationActivityStatus.RUNNING, running.activities[1].status)
        assertTrue(running.activities[1].text.contains("npm test"))
        assertTrue(running.activities[1].text.contains("1 test passed"))

        service.record(itemNotification(
            method = "item/completed",
            item = """{
                "id":"command-1","type":"commandExecution","command":"npm test",
                "aggregatedOutput":"1 test passed","status":"completed",
                "commandActions":[],"cwd":"D:/Work"
            }""",
        ))
        service.record(
            CodexNotification(
                method = "turn/completed",
                params = jsonObject(
                    """{"threadId":"thread-1","turn":{"id":"turn-1","status":"completed"}}""",
                ),
            ),
        )

        val completed = runBlocking { service.readConversation("thread-1") }
        assertEquals(RemoteExecutionStatus.COMPLETED, completed.executionStatus)
        assertEquals(2, completed.activities.size)
        assertTrue(completed.activities.all {
            it.status == RemoteConversationActivityStatus.COMPLETED
        })
    }

    @Test
    fun rebuildsCompletedActivitiesFromHistoryAndKeepsCommentaryOutOfMessages() = withStore { store ->
        val service = RemoteConversationService(
            api = FakeThreadHistoryApi(
                listResponse = json("{}"),
                readResponse = json(
                    """{
                        "thread": {
                            "id":"thread-1",
                            "preview":"测试会话",
                            "turns":[{
                                "id":"turn-1",
                                "status":"completed",
                                "items":[
                                    {"id":"user-1","type":"userMessage","content":[{"type":"text","text":"开始"}]},
                                    {"id":"comment-1","type":"agentMessage","phase":"commentary","text":"我先检查项目"},
                                    {"id":"search-1","type":"webSearch","query":"Kotlin serialization defaults"},
                                    {"id":"file-1","type":"fileChange","status":"completed","changes":[
                                        {"path":"RemoteConversationProtocol.kt","kind":{"type":"update"},"diff":"patch"}
                                    ]},
                                    {"id":"answer-1","type":"agentMessage","phase":"final_answer","text":"已完成"}
                                ]
                            }]
                        }
                    }""",
                ),
            ),
            store = store,
        )

        val detail = runBlocking { service.readConversation("thread-1") }

        assertEquals(listOf("开始", "已完成"), detail.messages.map { it.text })
        assertFalse(detail.messages.any { it.text == "我先检查项目" })
        assertEquals(
            listOf(
                RemoteConversationActivityKind.COMMENTARY,
                RemoteConversationActivityKind.WEB_SEARCH,
                RemoteConversationActivityKind.FILE_CHANGE,
            ),
            detail.activities.map { it.kind },
        )
        assertTrue(detail.activities.all {
            it.status == RemoteConversationActivityStatus.COMPLETED
        })
    }

    @Test
    fun historyMarksEarlierActivityCompletedWhenFinalAnswerHasStarted() = withStore { store ->
        val service = RemoteConversationService(
            api = FakeThreadHistoryApi(
                listResponse = json("{}"),
                readResponse = json(
                    """{
                        "thread": {
                            "id":"thread-1",
                            "preview":"测试会话",
                            "turns":[{
                                "id":"turn-1",
                                "status":"inProgress",
                                "items":[
                                    {"id":"reasoning-1","type":"reasoning","summary":["已经想好"],"content":[]},
                                    {"id":"answer-1","type":"agentMessage","phase":"final_answer","text":"正在回答"}
                                ]
                            }]
                        }
                    }""",
                ),
            ),
            store = store,
        )

        val detail = runBlocking { service.readConversation("thread-1") }

        assertEquals(RemoteConversationActivityStatus.COMPLETED, detail.activities.single().status)
        assertEquals(listOf("正在回答"), detail.messages.map { it.text })
    }

    @Test
    fun sendsValidatedComputerSkillAttachmentsAndTurnOptions() {
        val attachmentRoot = Files.createTempDirectory("mason-remote-inputs")
        val deviceRoot = attachmentRoot.resolve("phone-1")
        var imagePath: Path? = null
        var filePath: Path? = null
        try {
            withStore { store ->
                val api = FakeRemoteControlApi(
                    readResponse = json(
                        """{
                            "thread": {
                                "id":"thread-1",
                                "preview":"测试会话",
                                "cwd":"D:/Work/Mason",
                                "turns":[{"id":"turn-old","status":"completed","items":[]}]
                            }
                        }""",
                    ),
                    modelResponse = json(
                        """{"data":[{
                            "id":"gpt-codex","model":"gpt-codex","displayName":"GPT Codex",
                            "description":"Coding model","hidden":false,"isDefault":true,
                            "defaultReasoningEffort":"high",
                            "supportedReasoningEfforts":[{"reasoningEffort":"high","description":"Deep"}]
                        }]}""",
                    ),
                    skillResponse = json(
                        """{"data":[{"cwd":"D:/Work/Mason","errors":[],"skills":[{
                            "name":"how-to","description":"Guide","enabled":true,
                            "path":"C:/Skills/how-to/SKILL.md","scope":"user",
                            "interface":{"displayName":"How To","shortDescription":"Guide tasks"}
                        }]}]}""",
                    ),
                    permissionResponse = json(
                        """{"data":[{"id":"workspace-write","description":"Workspace","allowed":true}]}""",
                    ),
                )
                val service = RemoteConversationService(
                    api = api,
                    store = store,
                    attachmentRoot = attachmentRoot,
                )
                val image = runBlocking {
                    service.uploadAttachment(
                        deviceId = "phone-1",
                        kind = RemoteAttachmentKind.IMAGE,
                        name = "screen.png",
                        mimeType = "image/png",
                        bytes = byteArrayOf(1, 2, 3),
                    )
                }
                val file = runBlocking {
                    service.uploadAttachment(
                        deviceId = "phone-1",
                        kind = RemoteAttachmentKind.FILE,
                        name = "notes.txt",
                        mimeType = "text/plain",
                        bytes = "notes".encodeToByteArray(),
                    )
                }
                imagePath = deviceRoot.resolve("${image.attachmentId}-${image.name}")
                filePath = deviceRoot.resolve("${file.attachmentId}-${file.name}")

                val started = runBlocking {
                    service.sendMessage(
                        deviceId = "phone-1",
                        threadId = "thread-1",
                        request = RemoteMessageRequest(
                            text = "分析附件",
                            attachmentIds = listOf(image.attachmentId, file.attachmentId),
                            skill = RemoteSkillSelection("how-to", "C:/Skills/how-to/SKILL.md"),
                            modelId = "gpt-codex",
                            reasoningEffort = "high",
                            permissionProfileId = "workspace-write",
                        ),
                    )
                }

                assertEquals("turn-new", started.turnId)
                assertEquals("gpt-codex", api.startedModel)
                assertEquals("high", api.startedEffort)
                assertEquals("workspace-write", api.startedPermissions)
                val inputText = api.startedInput.orEmpty().joinToString("\n")
                assertTrue(inputText.contains("\"type\":\"localImage\""))
                assertTrue(inputText.contains("\"type\":\"skill\""))
                assertTrue(inputText.contains("C:/Skills/how-to/SKILL.md"))
                assertTrue(inputText.contains("notes.txt"))
                assertTrue(inputText.contains("$" + "how-to"))
            }
        } finally {
            imagePath?.let(Files::deleteIfExists)
            filePath?.let(Files::deleteIfExists)
            Files.deleteIfExists(deviceRoot)
            Files.deleteIfExists(attachmentRoot)
        }
    }

    @Test
    fun composerOptionsUseCurrentSettingsFromReadOnlyThread() = withStore { store ->
        val api = FakeRemoteControlApi(
            readResponse = json(
                """{
                    "thread":{
                        "id":"thread-1",
                        "model":"gpt-5.6-sol",
                        "reasoningEffort":"high",
                        "cwd":"D:/Work/Mason",
                        "activePermissionProfile":{"id":":workspace"}
                    }
                }""",
            ),
            modelResponse = modelOptionsResponse(),
            skillResponse = json("""{"data":[]}"""),
            permissionResponse = permissionProfilesResponse(),
        )

        val options = runBlocking { RemoteConversationService(api, store).composerOptions("thread-1") }

        assertEquals(null, api.resumedThreadId)
        assertEquals(1, api.readThreadCalls)
        assertEquals("selected-option", options.currentModelId)
        assertEquals("high", options.currentReasoningEffort)
        assertEquals(":workspace", options.currentPermissionProfileId)
        assertEquals("D:/Work/Mason", options.cwd)
        assertEquals(listOf("D:/Work/Mason"), api.lastSkillCwds)
        assertEquals("D:/Work/Mason", api.lastPermissionCwd)
    }

    @Test
    fun composerOptionsDoNotRequestWriterWhenDesktopOwnsThread() = withStore { store ->
        val api = FakeRemoteControlApi(
            readResponse = json(
                """{
                    "thread":{
                        "id":"thread-1",
                        "cwd":"D:/Work/Mason",
                        "model":"gpt-5.6-sol",
                        "reasoningEffort":"high",
                        "activePermissionProfile":{"id":":workspace"}
                    }
                }""",
            ),
            resumeError = CodexRpcException(-32600, "thread thread-1 already has an active writer"),
            modelResponse = modelOptionsResponse(),
            skillResponse = json("""{"data":[]}"""),
            permissionResponse = permissionProfilesResponse(),
        )

        val options = runBlocking { RemoteConversationService(api, store).composerOptions("thread-1") }

        assertEquals(null, api.resumedThreadId)
        assertEquals(1, api.readThreadCalls)
        assertEquals("selected-option", options.currentModelId)
        assertEquals("high", options.currentReasoningEffort)
        assertEquals(":workspace", options.currentPermissionProfileId)
        assertEquals("D:/Work/Mason", options.cwd)
    }

    @Test
    fun composerOptionsUseStoredThreadSelectionWhenAppServerOmitsIt() = withStore { store ->
        store.recordRemoteComposerSelection(
            threadId = "thread-1",
            selection = StoredRemoteComposerSelection(
                model = "gpt-5.6-sol",
                reasoningEffort = "high",
                permissionProfileId = ":workspace",
                cwd = "D:/Work/Mason",
            ),
        )
        val api = FakeRemoteControlApi(
            readResponse = json(
                """{"thread":{"id":"thread-1","cwd":"D:/Work/Mason"}}""",
            ),
            modelResponse = modelOptionsResponse(),
            skillResponse = json("""{"data":[]}"""),
            permissionResponse = permissionProfilesResponse(),
            configResponse = json(
                """{"config":{"model":"different-model","model_reasoning_effort":"medium","default_permissions":":read-only"}}""",
            ),
        )

        val options = runBlocking { RemoteConversationService(api, store).composerOptions("thread-1") }

        assertEquals("selected-option", options.currentModelId)
        assertEquals("high", options.currentReasoningEffort)
        assertEquals(":workspace", options.currentPermissionProfileId)
        assertEquals("D:/Work/Mason", options.cwd)
    }

    @Test
    fun composerOptionsUseStoredSelectionWhileNewThreadRolloutIsTemporarilyEmpty() = withStore { store ->
        store.recordRemoteComposerSelection(
            threadId = "thread-1",
            selection = StoredRemoteComposerSelection(
                model = "gpt-5.6-sol",
                reasoningEffort = "high",
                permissionProfileId = ":workspace",
                cwd = "D:/Work/Mason",
            ),
        )
        val api = FakeRemoteControlApi(
            readResponse = json("{}"),
            readError = CodexRpcException(
                -32603,
                "failed to read thread: failed to read session metadata rollout.jsonl: rollout is empty",
            ),
            modelResponse = modelOptionsResponse(),
            skillResponse = json("""{"data":[]}"""),
            permissionResponse = permissionProfilesResponse(),
        )

        val options = runBlocking { RemoteConversationService(api, store).composerOptions("thread-1") }

        assertEquals(1, api.readThreadCalls)
        assertEquals("selected-option", options.currentModelId)
        assertEquals("high", options.currentReasoningEffort)
        assertEquals(":workspace", options.currentPermissionProfileId)
        assertEquals("D:/Work/Mason", options.cwd)
        assertEquals(listOf("D:/Work/Mason"), api.lastSkillCwds)
        assertEquals("D:/Work/Mason", api.lastPermissionCwd)
    }

    @Test
    fun composerOptionsDoNotHideEmptyRolloutWithoutStoredSelection() = withStore { store ->
        val expected = CodexRpcException(
            -32603,
            "failed to read thread: failed to read session metadata rollout.jsonl: rollout is empty",
        )
        val api = FakeRemoteControlApi(
            readResponse = json("{}"),
            readError = expected,
        )

        val actual = assertFailsWith<CodexRpcException> {
            runBlocking { RemoteConversationService(api, store).composerOptions("thread-1") }
        }

        assertEquals(expected.code, actual.code)
        assertEquals(expected.message, actual.message)
    }

    @Test
    fun composerOptionsDoNotHideEmptyRolloutWithUnexpectedRpcCode() = withStore { store ->
        store.recordRemoteComposerSelection(
            threadId = "thread-1",
            selection = StoredRemoteComposerSelection(
                model = "gpt-5.6-sol",
                reasoningEffort = "high",
                permissionProfileId = ":workspace",
                cwd = "D:/Work/Mason",
            ),
        )
        val expected = CodexRpcException(
            -32600,
            "failed to read thread: failed to read session metadata rollout.jsonl: rollout is empty",
        )
        val api = FakeRemoteControlApi(
            readResponse = json("{}"),
            readError = expected,
        )

        val actual = assertFailsWith<CodexRpcException> {
            runBlocking { RemoteConversationService(api, store).composerOptions("thread-1") }
        }

        assertEquals(expected.code, actual.code)
        assertEquals(expected.message, actual.message)
    }

    @Test
    fun composerOptionsDoNotHideUnrelatedMetadataReadFailure() = withStore { store ->
        store.recordRemoteComposerSelection(
            threadId = "thread-1",
            selection = StoredRemoteComposerSelection(
                model = "gpt-5.6-sol",
                reasoningEffort = "high",
                permissionProfileId = ":workspace",
                cwd = "D:/Work/Mason",
            ),
        )
        val expected = CodexRpcException(
            -32603,
            "failed to read session metadata because the index is empty",
        )
        val api = FakeRemoteControlApi(
            readResponse = json("{}"),
            readError = expected,
        )

        val actual = assertFailsWith<CodexRpcException> {
            runBlocking { RemoteConversationService(api, store).composerOptions("thread-1") }
        }

        assertEquals(expected.code, actual.code)
        assertEquals(expected.message, actual.message)
    }

    @Test
    fun conversationAttachmentsStayInsideThreadProjectOrManagedRoot() {
        val root = Files.createTempDirectory("mason-remote-download")
        val project = Files.createDirectory(root.resolve("project"))
        val outside = Files.createDirectory(root.resolve("outside"))
        val attachmentRoot = Files.createDirectory(root.resolve("managed"))
        val projectFile = Files.writeString(project.resolve("inside.md"), "inside")
        val outsideFile = Files.writeString(outside.resolve("secret.txt"), "secret")
        val managedFile = Files.writeString(attachmentRoot.resolve("managed.txt"), "managed")
        try {
            withStore { store ->
                val api = FakeThreadHistoryApi(
                    listResponse = json("{}"),
                    readResponse = buildJsonObject {
                        put("thread", buildJsonObject {
                            put("id", "thread-1")
                            put("cwd", project.toString())
                            put("turns", buildJsonArray {
                                add(buildJsonObject {
                                    put("items", buildJsonArray {
                                        listOf(projectFile, outsideFile, managedFile).forEach { path ->
                                            add(buildJsonObject {
                                                put("type", "agentMessage")
                                                put("text", "文件：$path")
                                            })
                                        }
                                    })
                                })
                            })
                        })
                    },
                )
                val service = RemoteConversationService(
                    api = api,
                    store = store,
                    attachmentRoot = attachmentRoot,
                    workingDirectory = project,
                )

                val detail = runBlocking { service.readConversation("thread-1") }
                val attachments = detail.messages.flatMap { it.attachments }
                assertEquals(setOf("inside.md", "managed.txt"), attachments.map { it.name }.toSet())
                assertFalse(attachments.any { it.name == "secret.txt" })
                attachments.forEach { attachment ->
                    val download = runBlocking {
                        service.downloadConversationAttachment("thread-1", attachment.attachmentId)
                    }
                    assertTrue(download.bytes.isNotEmpty())
                }
            }
        } finally {
            Files.deleteIfExists(managedFile)
            Files.deleteIfExists(outsideFile)
            Files.deleteIfExists(projectFile)
            Files.deleteIfExists(attachmentRoot)
            Files.deleteIfExists(outside)
            Files.deleteIfExists(project)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun newConversationOptionsDoNotUseFirstPermissionAsDefault() = withStore { store ->
        val api = FakeRemoteControlApi(
            readResponse = json("{}"),
            modelResponse = modelOptionsResponse(),
            permissionResponse = permissionProfilesResponse(),
            configResponse = json("""{"config":{}}"""),
        )

        val options = runBlocking { RemoteConversationService(api, store).newConversationOptions() }

        assertEquals(":workspace", options.currentPermissionProfileId)
        assertFalse(options.currentPermissionProfileId == options.permissionProfiles.first().id)
        assertEquals(System.getProperty("user.dir"), options.cwd)
        assertEquals(options.cwd, api.lastConfigCwd)
        assertEquals(options.cwd, api.lastPermissionCwd)
    }

    @Test
    fun newConversationOptionsDefaultsToConnectorProjectAndFiltersRecentDirectories() {
        val root = Files.createTempDirectory("mason-remote-projects")
        val connectorProject = Files.createDirectory(root.resolve("connector-project"))
        val recentProject = Files.createDirectory(root.resolve("recent-project"))
        val nonDirectory = Files.createFile(root.resolve("not-a-project.txt"))
        val missingProject = root.resolve("missing-project")
        try {
            withStore { store ->
                val api = FakeRemoteControlApi(
                    readResponse = json("{}"),
                    listResponse = threadListResponse(
                        recentProject.toString(),
                        connectorProject.resolve(".").toString(),
                        recentProject.resolve(".").toString(),
                        nonDirectory.toString(),
                        missingProject.toString(),
                    ),
                    modelResponse = modelOptionsResponse(),
                    permissionResponse = permissionProfilesResponse(),
                )
                val options = runBlocking {
                    RemoteConversationService(
                        api = api,
                        store = store,
                        workingDirectory = connectorProject,
                    ).newConversationOptions()
                }
                val connectorRealPath = connectorProject.toRealPath().toString()
                val recentRealPath = recentProject.toRealPath().toString()

                assertEquals(connectorRealPath, options.cwd)
                assertEquals(
                    listOf(connectorRealPath, recentRealPath),
                    options.projects.map { it.path },
                )
                assertEquals(
                    listOf("connector-project", "recent-project"),
                    options.projects.map { it.displayName },
                )
            }
        } finally {
            Files.deleteIfExists(nonDirectory)
            Files.deleteIfExists(recentProject)
            Files.deleteIfExists(connectorProject)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun newConversationOptionsReadsConfigAndPermissionsForSelectedProject() {
        val root = Files.createTempDirectory("mason-selected-project")
        val connectorProject = Files.createDirectory(root.resolve("connector-project"))
        val selectedProject = Files.createDirectory(root.resolve("selected-project"))
        try {
            withStore { store ->
                val api = FakeRemoteControlApi(
                    readResponse = json("{}"),
                    listResponse = threadListResponse(selectedProject.toString()),
                    modelResponse = modelOptionsResponse(),
                    permissionResponse = permissionProfilesResponse(),
                    configResponse = json(
                        """{
                            "config": {
                                "model":"gpt-5.6-sol",
                                "model_reasoning_effort":"high",
                                "default_permissions":":workspace"
                            }
                        }""",
                    ),
                )
                val selectedRealPath = selectedProject.toRealPath().toString()
                val options = runBlocking {
                    RemoteConversationService(
                        api = api,
                        store = store,
                        workingDirectory = connectorProject,
                    ).newConversationOptions(selectedProject.resolve(".").toString())
                }

                assertEquals(selectedRealPath, options.cwd)
                assertEquals(selectedRealPath, api.lastConfigCwd)
                assertEquals(selectedRealPath, api.lastPermissionCwd)
                assertEquals("selected-option", options.currentModelId)
                assertEquals("high", options.currentReasoningEffort)
                assertEquals(":workspace", options.currentPermissionProfileId)
            }
        } finally {
            Files.deleteIfExists(selectedProject)
            Files.deleteIfExists(connectorProject)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun newConversationOptionsRejectsExistingDirectoryOutsideProjectCandidates() {
        val root = Files.createTempDirectory("mason-untrusted-project")
        val connectorProject = Files.createDirectory(root.resolve("connector-project"))
        val untrustedProject = Files.createDirectory(root.resolve("untrusted-project"))
        try {
            withStore { store ->
                val api = FakeRemoteControlApi(
                    readResponse = json("{}"),
                    modelResponse = modelOptionsResponse(),
                    permissionResponse = permissionProfilesResponse(),
                )
                val service = RemoteConversationService(
                    api = api,
                    store = store,
                    workingDirectory = connectorProject,
                )

                val error = assertFailsWith<IllegalArgumentException> {
                    runBlocking { service.newConversationOptions(untrustedProject.toString()) }
                }

                assertEquals("Selected project is unavailable on the computer", error.message)
                assertEquals(null, api.lastConfigCwd)
                assertEquals(null, api.lastPermissionCwd)
            }
        } finally {
            Files.deleteIfExists(untrustedProject)
            Files.deleteIfExists(connectorProject)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun createConversationRejectsExistingDirectoryOutsideProjectCandidatesBeforeStartingThread() {
        val root = Files.createTempDirectory("mason-create-untrusted-project")
        val connectorProject = Files.createDirectory(root.resolve("connector-project"))
        val untrustedProject = Files.createDirectory(root.resolve("untrusted-project"))
        try {
            withStore { store ->
                val api = FakeRemoteControlApi(
                    readResponse = json("{}"),
                    modelResponse = modelOptionsResponse(),
                    permissionResponse = permissionProfilesResponse(),
                )
                val service = RemoteConversationService(
                    api = api,
                    store = store,
                    workingDirectory = connectorProject,
                )

                val error = assertFailsWith<IllegalArgumentException> {
                    runBlocking {
                        service.createConversation(
                            RemoteConversationCreateRequest(
                                text = "Build the feature",
                                projectPath = untrustedProject.toString(),
                                modelId = "selected-option",
                                reasoningEffort = "high",
                                permissionProfileId = ":workspace",
                            ),
                        )
                    }
                }

                assertEquals("Selected project is unavailable on the computer", error.message)
                assertFalse(api.callOrder.any { it.endsWith("/start") })
            }
        } finally {
            Files.deleteIfExists(untrustedProject)
            Files.deleteIfExists(connectorProject)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun createConversationStartsThreadInSelectedProjectBeforeTurn() {
        val root = Files.createTempDirectory("mason-create-project")
        val connectorProject = Files.createDirectory(root.resolve("connector-project"))
        val selectedProject = Files.createDirectory(root.resolve("selected-project"))
        try {
            withStore { store ->
                val api = FakeRemoteControlApi(
                    readResponse = json("{}"),
                    listResponse = threadListResponse(selectedProject.toString()),
                    modelResponse = modelOptionsResponse(),
                    permissionResponse = permissionProfilesResponse(),
                    configResponse = json("""{"config":{}}"""),
                    startThreadResponse = json("""{"thread":{"id":"thread-created"}}"""),
                    startTurnResponse = json(
                        """{"turn":{"id":"turn-created","threadId":"thread-created","status":"inProgress"}}""",
                    ),
                )
                val service = RemoteConversationService(
                    api = api,
                    store = store,
                    workingDirectory = connectorProject,
                )

                val execution = runBlocking {
                    service.createConversation(
                        RemoteConversationCreateRequest(
                            text = "  Build the feature  ",
                            projectPath = selectedProject.toString(),
                            modelId = "selected-option",
                            reasoningEffort = "high",
                            permissionProfileId = ":workspace",
                        ),
                    )
                }

                assertEquals(
                    listOf("thread/start", "turn/start"),
                    api.callOrder.filter { it.endsWith("/start") },
                )
                assertEquals(selectedProject.toRealPath().toString(), api.startedThreadCwd)
                assertEquals("gpt-5.6-sol", api.startedThreadModel)
                assertEquals(":workspace", api.startedThreadPermissions)
                assertEquals("thread-created", api.startedTurnThreadId)
                assertEquals("gpt-5.6-sol", api.startedModel)
                assertEquals("high", api.startedEffort)
                assertEquals(":workspace", api.startedPermissions)
                assertEquals("Build the feature", api.startedText)
                assertEquals("thread-created", execution.threadId)
                assertEquals("turn-created", execution.turnId)
                assertEquals(RemoteExecutionStatus.RUNNING, execution.status)
                assertEquals(
                    StoredRemoteComposerSelection(
                        model = "gpt-5.6-sol",
                        reasoningEffort = "high",
                        permissionProfileId = ":workspace",
                        cwd = selectedProject.toRealPath().toString(),
                    ),
                    store.remoteComposerSelection("thread-created"),
                )
            }
        } finally {
            Files.deleteIfExists(selectedProject)
            Files.deleteIfExists(connectorProject)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun ignoresUnrelatedNotificationsForPersistedExecutionStatus() = withStore { store ->
        val api = FakeThreadHistoryApi(
            listResponse = json("{}"),
            readResponse = json(
                """{
                    "thread": {
                        "id":"thread-1",
                        "preview":"测试会话",
                        "turns":[{"id":"turn-1","status":"completed","items":[]}]
                    }
                }""",
            ),
        )
        val service = RemoteConversationService(api, store)

        service.record(
            CodexNotification(
                method = "thread/name/updated",
                params = jsonObject("""{"threadId":"thread-1"}"""),
            ),
        )

        assertEquals(
            RemoteExecutionStatus.COMPLETED,
            runBlocking { service.readConversation("thread-1") }.executionStatus,
        )
    }

    @Test
    fun pinAndArchiveForwardToAppServerAndReturnConversationSummary() = withStore { store ->
        val api = FakeRemoteControlApi(
            readResponse = json(
                """{"thread":{"id":"thread-1","name":"Remote thread","preview":"Latest","isPinned":false}}""",
            ),
            metadataResponse = json(
                """{"thread":{"id":"thread-1","name":"Remote thread","preview":"Latest","isPinned":true}}""",
            ),
        )
        val service = RemoteConversationService(api, store)

        val pinned = runBlocking { service.setPinned("thread-1", true) }
        val archived = runBlocking { service.archive("thread-1") }

        assertTrue(pinned.isPinned)
        assertEquals("thread-1", archived.threadId)
        assertEquals("thread-1" to true, api.updatedMetadata)
        assertEquals("thread-1", api.archivedThreadId)
    }

    @Test
    fun pinDoesNotReportSuccessWhenAppServerOmitsRequestedState() = withStore { store ->
        val api = FakeRemoteControlApi(
            readResponse = json(
                """{"thread":{"id":"thread-1","name":"Remote thread","isPinned":false}}""",
            ),
            metadataResponse = json("{}"),
        )
        val service = RemoteConversationService(api, store)

        assertFailsWith<IllegalStateException> {
            runBlocking { service.setPinned("thread-1", true) }
        }
    }
}

private class FakeThreadHistoryApi(
    private val listResponse: JsonElement,
    private val readResponse: JsonElement,
) : CodexThreadHistoryApi {
    var lastLimit: Int? = null
    var lastCursor: String? = null

    override suspend fun listThreads(limit: Int, cursor: String?): JsonElement {
        lastLimit = limit
        lastCursor = cursor
        return listResponse
    }

    override suspend fun readThread(threadId: String, includeTurns: Boolean): JsonElement = readResponse
}

private class FakeRemoteControlApi(
    private val readResponse: JsonElement,
    private val readError: Throwable? = null,
    private val listResponse: JsonElement = json("{}"),
    private val resumeResponse: JsonElement? = null,
    private val resumeError: Throwable? = null,
    private val modelResponse: JsonElement = json("{}"),
    private val skillResponse: JsonElement = json("{}"),
    private val permissionResponse: JsonElement = json("{}"),
    private val configResponse: JsonElement = json("""{"config":{}}"""),
    private val startThreadResponse: JsonElement = json("""{"thread":{"id":"thread-new"}}"""),
    private val startTurnResponse: JsonElement = json(
        """{"turn":{"id":"turn-new","threadId":"thread-new","status":"inProgress"}}""",
    ),
    private val metadataResponse: JsonElement = json("{}"),
) : CodexRemoteControlApi {
    val callOrder = mutableListOf<String>()
    var resumedThreadId: String? = null
    var startedText: String? = null
    var interruptedTurnId: String? = null
    var startedInput: JsonArray? = null
    var startedModel: String? = null
    var startedEffort: String? = null
    var startedPermissions: String? = null
    var startedThreadCwd: String? = null
    var startedThreadModel: String? = null
    var startedThreadPermissions: String? = null
    var startedTurnThreadId: String? = null
    var lastSkillCwds: List<String>? = null
    var lastPermissionCwd: String? = null
    var lastConfigCwd: String? = null
    var readThreadCalls: Int = 0
    var updatedMetadata: Pair<String, Boolean>? = null
    var archivedThreadId: String? = null

    override suspend fun listThreads(limit: Int, cursor: String?): JsonElement = listResponse

    override suspend fun readThread(threadId: String, includeTurns: Boolean): JsonElement {
        readThreadCalls += 1
        readError?.let { throw it }
        return readResponse
    }

    override suspend fun resumeThread(threadId: String): JsonElement {
        callOrder += "thread/resume"
        resumedThreadId = threadId
        resumeError?.let { throw it }
        return resumeResponse ?: json("""{"thread":{"id":"$threadId"}}""")
    }

    override suspend fun startThread(
        cwd: String?,
        model: String?,
        permissions: String?,
    ): JsonElement {
        callOrder += "thread/start"
        startedThreadCwd = cwd
        startedThreadModel = model
        startedThreadPermissions = permissions
        return startThreadResponse
    }

    override suspend fun startTextTurn(threadId: String, text: String): JsonElement {
        startedText = text
        return json("""{"turn":{"id":"turn-new","threadId":"$threadId","status":"inProgress"}}""")
    }

    override suspend fun listModels(): JsonElement {
        callOrder += "model/list"
        return modelResponse
    }

    override suspend fun listSkills(cwds: List<String>): JsonElement {
        callOrder += "skills/list"
        lastSkillCwds = cwds
        return skillResponse
    }

    override suspend fun listPermissionProfiles(cwd: String?): JsonElement {
        callOrder += "permissionProfile/list"
        lastPermissionCwd = cwd
        return permissionResponse
    }

    override suspend fun readConfig(cwd: String?): JsonElement {
        callOrder += "config/read"
        lastConfigCwd = cwd
        return configResponse
    }

    override suspend fun updateThreadMetadata(threadId: String, isPinned: Boolean): JsonElement {
        updatedMetadata = threadId to isPinned
        return metadataResponse
    }

    override suspend fun archiveThread(threadId: String): JsonElement {
        archivedThreadId = threadId
        return json("{}")
    }

    override suspend fun startTurn(
        threadId: String,
        input: JsonArray,
        model: String?,
        effort: String?,
        permissions: String?,
    ): JsonElement {
        callOrder += "turn/start"
        startedTurnThreadId = threadId
        startedInput = input
        startedModel = model
        startedEffort = effort
        startedPermissions = permissions
        startedText = (input.firstOrNull() as? JsonObject)
            ?.get("text")
            ?.jsonPrimitive
            ?.content
        return startTurnResponse
    }

    override suspend fun interruptTurn(threadId: String, turnId: String): JsonElement {
        interruptedTurnId = turnId
        return json("{}")
    }
}

private fun modelOptionsResponse(): JsonElement = json(
    """{"data":[
        {
            "id":"first-option","model":"different-model","displayName":"First",
            "description":"Not selected","hidden":false,"isDefault":true,
            "defaultReasoningEffort":"medium",
            "supportedReasoningEfforts":[{"reasoningEffort":"medium","description":"Medium"}]
        },
        {
            "id":"selected-option","model":"gpt-5.6-sol","displayName":"Selected",
            "description":"Selected model","hidden":false,"isDefault":false,
            "defaultReasoningEffort":"high",
            "supportedReasoningEfforts":[{"reasoningEffort":"high","description":"High"}]
        }
    ]}""",
)

private fun permissionProfilesResponse(): JsonElement = json(
    """{"data":[
        {"id":":read-only","description":"Read only","allowed":true},
        {"id":":workspace","description":"Workspace","allowed":true}
    ]}""",
)

private fun threadListResponse(vararg projectPaths: String): JsonElement = buildJsonObject {
    put("data", buildJsonArray {
        projectPaths.forEachIndexed { index, projectPath ->
            add(buildJsonObject {
                put("id", "thread-$index")
                put("cwd", projectPath)
            })
        }
    })
}

private fun json(value: String): JsonElement = MasonProtocolJson.format.parseToJsonElement(value)

private fun jsonObject(value: String): JsonObject = json(value) as JsonObject

private fun notification(
    method: String,
    threadId: String,
    turnId: String,
    delta: String,
): CodexNotification = CodexNotification(
    method = method,
    params = jsonObject(
        """{"threadId":"$threadId","turnId":"$turnId","delta":"$delta"}""",
    ),
)

private fun itemNotification(
    method: String,
    item: String,
): CodexNotification = CodexNotification(
    method = method,
    params = jsonObject(
        """{"threadId":"thread-1","turnId":"turn-1","item":$item}""",
    ),
)

private fun itemProgressNotification(
    method: String,
    itemId: String,
    key: String,
    value: String,
): CodexNotification = CodexNotification(
    method = method,
    params = buildJsonObject {
        put("threadId", "thread-1")
        put("turnId", "turn-1")
        put("itemId", itemId)
        put(key, value)
    },
)

private fun withStore(block: (ConnectorStateStore) -> Unit) {
    val path = Files.createTempFile("mason-remote-conversations", ".json")
    Files.deleteIfExists(path)
    try {
        block(
            ConnectorStateStore(
                statePath = path,
                newOwnerId = { "owner-1" },
                newDeviceId = { "connector-1" },
            ),
        )
    } finally {
        Files.deleteIfExists(path)
    }
}
