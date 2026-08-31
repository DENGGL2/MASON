package com.denggl2.mason.connector

import com.denggl2.mason.protocol.AuthChallengeRequest
import com.denggl2.mason.protocol.AuthProof
import com.denggl2.mason.protocol.DevicePermission
import com.denggl2.mason.protocol.DeviceRevocationResult
import com.denggl2.mason.protocol.MasonProtocolJson
import com.denggl2.mason.protocol.PairingRequest
import com.denggl2.mason.protocol.ProtocolErrorResponse
import com.denggl2.mason.protocol.RemoteAttachmentKind
import com.denggl2.mason.protocol.RemoteApprovalResolutionRequest
import com.denggl2.mason.protocol.RemoteConversationCreateRequest
import com.denggl2.mason.protocol.RemoteMessageRequest
import com.denggl2.mason.protocol.SessionInfo
import com.denggl2.mason.protocol.validate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.InetAddress
import kotlinx.serialization.Serializable

class ConnectorLoopbackServer(
    private val authService: PairingAuthService,
    val host: String = DEFAULT_LOOPBACK_HOST,
    val port: Int,
    private val conversationProvider: RemoteConversationProvider? = null,
    private val conversationController: RemoteConversationController? =
        conversationProvider as? RemoteConversationController,
) : AutoCloseable {
    private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    init {
        require(port in 1..65535) { "Loopback server port must be between 1 and 65535" }
        val address = runCatching { InetAddress.getByName(host) }.getOrElse {
            throw IllegalArgumentException("Loopback server host cannot be resolved: $host", it)
        }
        require(address.isLoopbackAddress) { "Phase 2B loopback server cannot bind non-loopback address: $host" }
        engine = embeddedServer(Netty, host = host, port = port) {
            configurePairingHttpApi(
                authService = authService,
                transport = "loopback",
                conversationProvider = conversationProvider,
                conversationController = conversationController,
            )
        }
    }

    fun start(): ConnectorLoopbackServer = apply { engine.start(wait = false) }

    override fun close() {
        engine.stop(gracePeriodMillis = 250, timeoutMillis = 2_000)
    }

    companion object {
        const val DEFAULT_LOOPBACK_HOST = "127.0.0.1"
    }
}

internal fun Application.configurePairingHttpApi(
    authService: PairingAuthService,
    transport: String,
    conversationProvider: RemoteConversationProvider? = null,
    conversationController: RemoteConversationController? =
        conversationProvider as? RemoteConversationController,
) {
    install(ContentNegotiation) {
        json(MasonProtocolJson.format)
    }
    routing {
        get("/v1/health") {
            call.respond(ConnectorHealthResponse(transport = transport))
        }
        post("/v1/pairing/complete") {
            call.respondSafely {
                authService.pair(call.receive<PairingRequest>())
            }
        }
        post("/v1/auth/challenge") {
            call.respondSafely {
                val request = call.receive<AuthChallengeRequest>()
                require(request.validate().isEmpty()) { "Invalid auth challenge request" }
                authService.createAuthChallenge(request.deviceId)
            }
        }
        post("/v1/auth/session") {
            call.respondSafely {
                authService.authenticate(call.receive<AuthProof>())
            }
        }
        get("/v1/me") {
            call.respondSafely {
                val token = call.bearerToken()
                val principal = authService.authenticateSession(token)
                SessionInfo(
                    deviceId = principal.deviceId,
                    permissions = principal.permissions,
                    expiresAt = principal.expiresAt,
                )
            }
        }
        post("/v1/me/revoke") {
            call.respondSafely {
                val principal = authService.authenticateSession(call.bearerToken())
                val revoked = authService.revokeDevice(principal.deviceId)
                DeviceRevocationResult(
                    deviceId = principal.deviceId,
                    revokedAt = requireNotNull(revoked.device.revokedAt),
                )
            }
        }
        get("/v1/conversations") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.VIEW_SHARED_CONVERSATIONS,
                )
                val provider = conversationProvider ?: throw RemoteConversationUnavailableException()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 3
                provider.listConversations(
                    limit = limit,
                    cursor = call.request.queryParameters["cursor"]?.takeIf(String::isNotBlank),
                )
            }
        }
        get("/v1/conversation-events") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.VIEW_SHARED_CONVERSATIONS,
                )
                val provider = conversationProvider ?: throw RemoteConversationUnavailableException()
                provider.conversationEvents(
                    afterRevision = call.request.queryParameters["after"]
                        ?.toLongOrNull()
                        ?: 0,
                    waitMillis = call.request.queryParameters["waitMillis"]
                        ?.toLongOrNull()
                        ?: DEFAULT_CONVERSATION_EVENT_WAIT_MILLIS,
                )
            }
        }
        get("/v1/conversations/new/options") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.VIEW_SHARED_CONVERSATIONS,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.newConversationOptions(
                    call.request.queryParameters["projectPath"]?.takeIf(String::isNotBlank),
                )
            }
        }
        post("/v1/conversations") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.SEND_MESSAGES,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.createConversation(call.receive<RemoteConversationCreateRequest>())
            }
        }
        get("/v1/conversations/{threadId}") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.VIEW_SHARED_CONVERSATIONS,
                )
                val provider = conversationProvider ?: throw RemoteConversationUnavailableException()
                provider.readConversation(
                    call.parameters["threadId"]?.takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("Thread ID is required"),
                )
            }
        }
        get("/v1/conversations/{threadId}/attachments/{attachmentId}") {
            try {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.VIEW_SHARED_CONVERSATIONS,
                )
                val provider = conversationProvider ?: throw RemoteConversationUnavailableException()
                val attachment = provider.downloadConversationAttachment(
                    threadId = call.requiredThreadId(),
                    attachmentId = call.parameters["attachmentId"]?.takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("Attachment ID is required"),
                )
                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    "inline; filename=\"${attachment.descriptor.name.replace("\"", "_")}\"",
                )
                call.respondBytes(
                    bytes = attachment.bytes,
                    contentType = attachment.descriptor.mimeType
                        ?.let { value -> runCatching { io.ktor.http.ContentType.parse(value) }.getOrNull() },
                )
            } catch (error: PairingAuthException) {
                call.respond(
                    status = error.code.httpStatus(),
                    message = ProtocolErrorResponse(
                        code = error.code.name,
                        message = error.message ?: "Pairing or authentication failed",
                    ),
                )
            } catch (_: RemoteConversationAttachmentNotFoundException) {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = ProtocolErrorResponse(
                        code = "ATTACHMENT_NOT_FOUND",
                        message = "Conversation attachment is unavailable",
                    ),
                )
            } catch (error: RemoteAttachmentTooLargeException) {
                call.respond(
                    status = HttpStatusCode.PayloadTooLarge,
                    message = ProtocolErrorResponse(
                        code = "ATTACHMENT_TOO_LARGE",
                        message = error.message ?: "Attachment is too large",
                    ),
                )
            } catch (_: IllegalArgumentException) {
                call.respondBadRequest()
            }
        }
        get("/v1/conversations/{threadId}/composer-options") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.VIEW_SHARED_CONVERSATIONS,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.composerOptions(call.requiredThreadId())
            }
        }
        post("/v1/attachments") {
            call.respondSafely {
                val principal = authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.SEND_MESSAGES,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                val declaredSize = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declaredSize != null && declaredSize > RemoteAttachmentStore.MAX_FILE_BYTES) {
                    throw RemoteAttachmentTooLargeException(RemoteAttachmentStore.MAX_FILE_BYTES)
                }
                controller.uploadAttachment(
                    deviceId = principal.deviceId,
                    kind = call.request.queryParameters["kind"]
                        ?.uppercase()
                        ?.let(RemoteAttachmentKind::valueOf)
                        ?: throw IllegalArgumentException("Attachment kind is required"),
                    name = call.request.queryParameters["name"]
                        ?.takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("Attachment name is required"),
                    mimeType = call.request.queryParameters["mimeType"]?.takeIf(String::isNotBlank),
                    bytes = call.receive<ByteArray>(),
                )
            }
        }
        post("/v1/conversations/{threadId}/messages") {
            call.respondSafely {
                val principal = authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.SEND_MESSAGES,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.sendMessage(
                    deviceId = principal.deviceId,
                    threadId = call.requiredThreadId(),
                    request = call.receive<RemoteMessageRequest>(),
                )
            }
        }
        get("/v1/conversations/{threadId}/approvals") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.RESOLVE_APPROVALS,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.pendingApprovals(call.requiredThreadId())
            }
        }
        post("/v1/conversations/{threadId}/approvals/{requestId}") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.RESOLVE_APPROVALS,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.resolveApproval(
                    threadId = call.requiredThreadId(),
                    requestId = call.parameters["requestId"]?.takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("Approval request ID is required"),
                    decision = call.receive<RemoteApprovalResolutionRequest>().decision,
                )
            }
        }
        post("/v1/conversations/{threadId}/interrupt") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.CONTROL_EXECUTION,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.interrupt(call.requiredThreadId())
            }
        }
        post("/v1/conversations/{threadId}/pin") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.CONTROL_EXECUTION,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.setPinned(call.requiredThreadId(), isPinned = true)
            }
        }
        post("/v1/conversations/{threadId}/unpin") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.CONTROL_EXECUTION,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.setPinned(call.requiredThreadId(), isPinned = false)
            }
        }
        post("/v1/conversations/{threadId}/archive") {
            call.respondSafely {
                authService.authenticateSession(
                    sessionToken = call.bearerToken(),
                    requiredPermission = DevicePermission.CONTROL_EXECUTION,
                )
                val controller = conversationController
                    ?: throw RemoteConversationControlUnavailableException()
                controller.archive(call.requiredThreadId())
            }
        }
    }
}

private suspend fun ApplicationCall.respondSafely(block: suspend () -> Any) {
    try {
        respond(block())
    } catch (error: PairingAuthException) {
        respond(
            status = error.code.httpStatus(),
            message = ProtocolErrorResponse(
                code = error.code.name,
                message = error.message ?: "Pairing or authentication failed",
            ),
        )
    } catch (_: BadRequestException) {
        respondBadRequest()
    } catch (error: RemoteAttachmentTooLargeException) {
        respond(
            status = HttpStatusCode.PayloadTooLarge,
            message = ProtocolErrorResponse(
                code = "ATTACHMENT_TOO_LARGE",
                message = error.message ?: "Attachment is too large",
            ),
        )
    } catch (error: RemoteAttachmentQuotaExceededException) {
        respond(
            status = HttpStatusCode.InsufficientStorage,
            message = ProtocolErrorResponse(
                code = "ATTACHMENT_QUOTA_EXCEEDED",
                message = error.message ?: "Attachment storage quota exceeded",
            ),
        )
    } catch (_: IllegalArgumentException) {
        respondBadRequest()
    } catch (_: RemoteConversationNotFoundException) {
        respond(
            status = HttpStatusCode.NotFound,
            message = ProtocolErrorResponse(
                code = "CONVERSATION_NOT_FOUND",
                message = "Conversation was not found",
            ),
        )
    } catch (_: RemoteConversationUnavailableException) {
        respond(
            status = HttpStatusCode.ServiceUnavailable,
            message = ProtocolErrorResponse(
                code = "CONVERSATIONS_UNAVAILABLE",
                message = "Codex conversation history is unavailable",
            ),
        )
    } catch (_: RemoteConversationControlUnavailableException) {
        respond(
            status = HttpStatusCode.ServiceUnavailable,
            message = ProtocolErrorResponse(
                code = "REMOTE_CONTROL_UNAVAILABLE",
                message = "Codex remote control is unavailable",
            ),
        )
    } catch (_: RemoteConversationBusyException) {
        respond(
            status = HttpStatusCode.Conflict,
            message = ProtocolErrorResponse(
                code = "CONVERSATION_BUSY",
                message = "Conversation already has an active turn",
            ),
        )
    } catch (_: RemoteConversationNotRunningException) {
        respond(
            status = HttpStatusCode.Conflict,
            message = ProtocolErrorResponse(
                code = "NO_ACTIVE_EXECUTION",
                message = "Conversation does not have an active turn",
            ),
        )
    } catch (error: CodexRpcException) {
        System.err.println("Codex App Server error ${error.code}: ${error.message}")
        if (error.message.contains("active writer", ignoreCase = true)) {
            respond(
                status = HttpStatusCode.Conflict,
                message = ProtocolErrorResponse(
                    code = "CONVERSATION_ACTIVE_WRITER",
                    message = "Conversation is currently controlled by another Codex writer",
                ),
            )
        } else {
            respond(
                status = HttpStatusCode.BadGateway,
                message = ProtocolErrorResponse(
                    code = "CODEX_APP_SERVER_ERROR",
                    message = "Codex App Server request failed",
                ),
            )
        }
    }
}

private fun ApplicationCall.requiredThreadId(): String =
    parameters["threadId"]?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("Thread ID is required")

private fun ApplicationCall.bearerToken(): String =
    request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
        ?.substring(BEARER_PREFIX.length)
        ?.trim()
        .orEmpty()

private suspend fun ApplicationCall.respondBadRequest() {
        respond(
            status = HttpStatusCode.BadRequest,
            message = ProtocolErrorResponse(
                code = PairingAuthErrorCode.INVALID_REQUEST.name,
                message = "Request body is invalid",
            ),
        )
}

private fun PairingAuthErrorCode.httpStatus(): HttpStatusCode = when (this) {
    PairingAuthErrorCode.INVALID_REQUEST,
    PairingAuthErrorCode.CONNECTOR_MISMATCH,
    PairingAuthErrorCode.INVALID_PUBLIC_KEY,
    -> HttpStatusCode.BadRequest

    PairingAuthErrorCode.PAIRING_NOT_FOUND,
    PairingAuthErrorCode.CHALLENGE_NOT_FOUND,
    PairingAuthErrorCode.DEVICE_NOT_PAIRED,
    -> HttpStatusCode.NotFound

    PairingAuthErrorCode.PAIRING_EXPIRED,
    PairingAuthErrorCode.CHALLENGE_EXPIRED,
    PairingAuthErrorCode.SESSION_EXPIRED,
    -> HttpStatusCode.Gone

    PairingAuthErrorCode.INVALID_PAIRING_TOKEN,
    PairingAuthErrorCode.INVALID_SIGNATURE,
    PairingAuthErrorCode.SESSION_INVALID,
    -> HttpStatusCode.Unauthorized

    PairingAuthErrorCode.PERMISSION_DENIED,
    PairingAuthErrorCode.DEVICE_REVOKED,
    -> HttpStatusCode.Forbidden

    PairingAuthErrorCode.DEVICE_ALREADY_PAIRED -> HttpStatusCode.Conflict
}

@Serializable
private data class ConnectorHealthResponse(
    val status: String = "ok",
    val transport: String,
)

private const val BEARER_PREFIX = "Bearer "
private const val DEFAULT_CONVERSATION_EVENT_WAIT_MILLIS = 25_000L

private class RemoteConversationUnavailableException :
    IllegalStateException("Remote conversation provider is unavailable")
