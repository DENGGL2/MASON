package com.denggl2.mason.connector

import com.denggl2.mason.protocol.RemoteAttachmentDescriptor
import com.denggl2.mason.protocol.RemoteAttachmentKind
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isRegularFile

internal data class StoredRemoteAttachment(
    val descriptor: RemoteAttachmentDescriptor,
    val deviceId: String,
    val path: Path,
)

internal class RemoteAttachmentStore(
    root: Path,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
    private val maxDeviceBytes: Long = MAX_DEVICE_BYTES,
) {
    private val root = root.toAbsolutePath().normalize()
    private val records = ConcurrentHashMap<String, StoredRemoteAttachment>()

    fun store(
        deviceId: String,
        kind: RemoteAttachmentKind,
        originalName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): RemoteAttachmentDescriptor {
        require(deviceId.isNotBlank()) { "Device ID is required" }
        require(bytes.isNotEmpty()) { "Attachment is empty" }
        if (bytes.size.toLong() > maxFileBytes) throw RemoteAttachmentTooLargeException(maxFileBytes)

        val safeName = safeFileName(originalName)
        val safeDeviceId = safePathSegment(deviceId)
        val deviceRoot = root.resolve(safeDeviceId).normalize()
        require(deviceRoot.startsWith(root)) { "Invalid attachment device directory" }
        Files.createDirectories(deviceRoot)
        val usedBytes = Files.list(deviceRoot).use { paths ->
            paths.filter { it.isRegularFile() }.mapToLong(Files::size).sum()
        }
        if (usedBytes + bytes.size > maxDeviceBytes) {
            throw RemoteAttachmentQuotaExceededException(maxDeviceBytes)
        }

        val attachmentId = UUID.randomUUID().toString()
        val destination = deviceRoot.resolve("$attachmentId-$safeName").normalize()
        require(destination.startsWith(deviceRoot)) { "Invalid attachment destination" }
        Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)

        val descriptor = RemoteAttachmentDescriptor(
            attachmentId = attachmentId,
            kind = kind,
            name = safeName,
            mimeType = mimeType?.trim()?.takeIf(String::isNotBlank)?.take(MAX_MIME_LENGTH),
            sizeBytes = bytes.size.toLong(),
        )
        records[attachmentId] = StoredRemoteAttachment(
            descriptor = descriptor,
            deviceId = deviceId,
            path = destination,
        )
        return descriptor
    }

    fun resolve(deviceId: String, attachmentIds: List<String>): List<StoredRemoteAttachment> {
        require(attachmentIds.size <= MAX_ATTACHMENTS_PER_TURN) {
            "A message can include at most $MAX_ATTACHMENTS_PER_TURN attachments"
        }
        require(attachmentIds.distinct().size == attachmentIds.size) {
            "Attachment IDs must be unique"
        }
        return attachmentIds.map { attachmentId ->
            records[attachmentId]
                ?.takeIf { it.deviceId == deviceId && it.path.isRegularFile() }
                ?: throw RemoteAttachmentNotFoundException(attachmentId)
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 20L * 1024L * 1024L
        const val MAX_DEVICE_BYTES = 200L * 1024L * 1024L
        const val MAX_ATTACHMENTS_PER_TURN = 4
        private const val MAX_FILE_NAME_LENGTH = 96
        private const val MAX_MIME_LENGTH = 128
    }

    private fun safeFileName(value: String): String {
        val normalized = value
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim('.', ' ')
            .take(MAX_FILE_NAME_LENGTH)
        return normalized.ifBlank { "attachment" }
    }

    private fun safePathSegment(value: String): String = value
        .map { character ->
            if (character.isLetterOrDigit() || character in "-_.") character else '_'
        }
        .joinToString("")
        .take(96)
        .ifBlank { "device" }
}

class RemoteAttachmentTooLargeException(maxFileBytes: Long) :
    IllegalArgumentException("Attachment exceeds the ${maxFileBytes / 1024 / 1024} MiB limit")

class RemoteAttachmentQuotaExceededException(maxDeviceBytes: Long) :
    IllegalStateException("Attachment storage exceeds the ${maxDeviceBytes / 1024 / 1024} MiB device limit")

class RemoteAttachmentNotFoundException(attachmentId: String) :
    IllegalArgumentException("Attachment is unavailable: $attachmentId")
