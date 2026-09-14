package com.denggl2.mason.ui.chat

import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.data.detectImageMimeType
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.max
import kotlin.math.min
import org.xml.sax.InputSource
import org.xml.sax.SAXException

internal data class ArtifactImageOffset(
    val x: Float,
    val y: Float,
)

internal fun isPreviewableImageArtifact(
    artifact: ArtifactMetadata,
    platformSupportsAvif: Boolean = true,
): Boolean {
    val mimeType = artifact.mimeType
        .substringBefore(';')
        .trim()
        .lowercase()
    val extension = artifact.name
        .substringAfterLast('.', missingDelimiterValue = "")
        .trim()
        .lowercase()
    val supported = mimeType in PREVIEWABLE_IMAGE_MIME_TYPES ||
        extension in PREVIEWABLE_IMAGE_EXTENSIONS
    if (!supported) return false
    return platformSupportsAvif || (mimeType !in AVIF_MIME_TYPES && extension != "avif")
}

internal fun isSvgImageArtifact(artifact: ArtifactMetadata): Boolean {
    val mimeType = artifact.mimeType
        .substringBefore(';')
        .trim()
        .lowercase()
    if (mimeType in SVG_IMAGE_MIME_TYPES) return true
    return artifact.name.substringAfterLast('.', "").trim().lowercase() == "svg"
}

internal fun detectPreviewableImageMimeType(
    bytes: ByteArray,
    declaredMimeType: String?,
): String? {
    val detected = detectImageMimeType(bytes, null)
        ?: detectAdditionalRasterMimeType(bytes)
        ?: detectSvgMimeType(bytes)
        ?: return null
    val declared = declaredMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)
    return detected.takeIf {
        declared == null ||
            declared == "application/octet-stream" ||
            declared == "*/*" ||
            declared == "image/*" ||
            declared == detected ||
            declared in PNG_MIME_TYPES && detected == "image/png" ||
            declared in JPEG_MIME_TYPES && detected == "image/jpeg" ||
            declared in BMP_MIME_TYPES && detected == "image/bmp" ||
            declared in ICO_MIME_TYPES && detected in ICO_MIME_TYPES ||
            declared in SVG_COMPATIBLE_MIME_TYPES && detected == "image/svg+xml" ||
            declared in HEIF_MIME_TYPES && detected in HEIF_MIME_TYPES ||
            declared in AVIF_MIME_TYPES && detected == "image/avif"
    }
}

internal fun normalizeSvgForPreview(content: String): String {
    rejectUnsafeSvgXml(content)
    return normalizeSvgForPreview(InputSource(StringReader(content)))
}

internal fun normalizeSvgForPreview(bytes: ByteArray): String {
    rejectUnsafeSvgXml(decodeXmlPrefix(bytes))
    return ByteArrayInputStream(bytes).use { input ->
        normalizeSvgForPreview(InputSource(input))
    }
}

private fun normalizeSvgForPreview(inputSource: InputSource): String {
    val documentFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
    }
    val documentBuilder = documentFactory.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> throw SAXException("SVG 不允许外部实体") }
    }
    val document = documentBuilder.parse(inputSource)
    val root = requireNotNull(document.documentElement) { "SVG 缺少根节点" }
    require((root.localName ?: root.nodeName.substringAfter(':')).equals("svg", ignoreCase = true)) {
        "文件根节点不是 SVG"
    }

    if (!root.hasAttribute("viewBox")) {
        val width = root.getAttribute("width").svgNumericLength()
        val height = root.getAttribute("height").svgNumericLength()
        if (width != null && height != null) {
            root.setAttribute("viewBox", "0 0 $width $height")
        }
    }
    if (!root.hasAttribute("preserveAspectRatio")) {
        root.setAttribute("preserveAspectRatio", "xMidYMid meet")
    }

    val transformerFactory = TransformerFactory.newInstance().apply {
        setURIResolver { _, _ -> throw TransformerException("SVG 不允许外部样式") }
    }
    val writer = StringWriter()
    transformerFactory.newTransformer().apply {
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    }.transform(DOMSource(document), StreamResult(writer))
    return writer.toString()
}

internal fun buildSvgPreviewDocument(
    svg: String,
    viewportWidthCssPx: Int,
    viewportHeightCssPx: Int,
): String {
    require(viewportWidthCssPx > 0 && viewportHeightCssPx > 0) { "SVG 预览视口尺寸无效" }
    return """
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes">
      <style>
        html, body {
          width: ${viewportWidthCssPx}px;
          height: ${viewportHeightCssPx}px;
          margin: 0;
          padding: 0;
          overflow: hidden;
          background: #fff;
        }
        body {
          display: flex;
          align-items: center;
          justify-content: center;
        }
        body > svg {
          display: block !important;
          width: ${viewportWidthCssPx}px !important;
          height: ${viewportHeightCssPx}px !important;
          max-width: ${viewportWidthCssPx}px !important;
          max-height: ${viewportHeightCssPx}px !important;
        }
      </style>
    </head>
    <body>$svg</body>
    </html>
    """.trimIndent()
}

internal fun calculateArtifactPreviewSampleSize(
    width: Int,
    height: Int,
    maxEdge: Int,
): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1

    val largestEdge = max(width, height).toLong()
    val targetEdge = maxEdge.toLong()
    var sampleSize = 1
    while (largestEdge > targetEdge * sampleSize && sampleSize <= Int.MAX_VALUE / 2) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun clampArtifactImageOffset(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    contentWidth: Float,
    contentHeight: Float,
): ArtifactImageOffset {
    if (
        !offsetX.isFinite() ||
        !offsetY.isFinite() ||
        !scale.isFinite() ||
        !viewportWidth.isFinite() ||
        !viewportHeight.isFinite() ||
        !contentWidth.isFinite() ||
        !contentHeight.isFinite() ||
        scale <= 1f ||
        viewportWidth <= 0f ||
        viewportHeight <= 0f ||
        contentWidth <= 0f ||
        contentHeight <= 0f
    ) {
        return ArtifactImageOffset(0f, 0f)
    }

    val fitScale = min(viewportWidth / contentWidth, viewportHeight / contentHeight)
    val scaledWidth = contentWidth * fitScale * scale
    val scaledHeight = contentHeight * fitScale * scale
    if (!scaledWidth.isFinite() || !scaledHeight.isFinite()) {
        return ArtifactImageOffset(0f, 0f)
    }

    val maxOffsetX = max(0f, (scaledWidth - viewportWidth) / 2f)
    val maxOffsetY = max(0f, (scaledHeight - viewportHeight) / 2f)
    return ArtifactImageOffset(
        x = offsetX.coerceIn(-maxOffsetX, maxOffsetX),
        y = offsetY.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

private val PREVIEWABLE_IMAGE_MIME_TYPES = setOf(
    "image/png",
    "image/x-png",
    "image/jpeg",
    "image/jpg",
    "image/pjpeg",
    "image/webp",
    "image/gif",
    "image/svg+xml",
    "image/svg",
    "image/bmp",
    "image/x-bmp",
    "image/x-ms-bmp",
    "image/heif",
    "image/heif-sequence",
    "image/heic",
    "image/heic-sequence",
    "image/avif",
    "image/avif-sequence",
    "image/x-icon",
    "image/vnd.microsoft.icon",
)

private val PREVIEWABLE_IMAGE_EXTENSIONS = setOf(
    "png",
    "jpg",
    "jpeg",
    "webp",
    "gif",
    "svg",
    "bmp",
    "heif",
    "heic",
    "avif",
    "ico",
)

private val PNG_MIME_TYPES = setOf("image/png", "image/x-png")
private val JPEG_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/pjpeg")
private val BMP_MIME_TYPES = setOf("image/bmp", "image/x-bmp", "image/x-ms-bmp")
private val ICO_MIME_TYPES = setOf("image/x-icon", "image/vnd.microsoft.icon")
private val HEIF_MIME_TYPES = setOf(
    "image/heif",
    "image/heif-sequence",
    "image/heic",
    "image/heic-sequence",
)
private val AVIF_MIME_TYPES = setOf("image/avif", "image/avif-sequence")
private val SVG_IMAGE_MIME_TYPES = setOf("image/svg+xml", "image/svg")
private val SVG_COMPATIBLE_MIME_TYPES = setOf("application/xml", "text/xml", "image/svg")
private val UNSAFE_SVG_XML_DECLARATION = Regex("""<!\s*(?:DOCTYPE|ENTITY)\b""", RegexOption.IGNORE_CASE)

private fun rejectUnsafeSvgXml(content: String) {
    require(!UNSAFE_SVG_XML_DECLARATION.containsMatchIn(content)) {
        "SVG 不允许 DOCTYPE 或 ENTITY 声明"
    }
}

private fun String.svgNumericLength(): String? {
    val match = Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)(?:px)?\\s*$", RegexOption.IGNORE_CASE)
        .matchEntire(this)
        ?: return null
    return match.groupValues[1].takeIf { it.toFloatOrNull()?.let { value -> value > 0f } == true }
}

private fun detectAdditionalRasterMimeType(bytes: ByteArray): String? = when {
    hasBmpHeader(bytes) -> "image/bmp"
    hasIcoHeader(bytes) -> "image/x-icon"
    else -> detectIsoBaseMediaImageMimeType(bytes)
}

private fun hasBmpHeader(bytes: ByteArray): Boolean {
    if (bytes.size < 14 || bytes[0] != 'B'.code.toByte() || bytes[1] != 'M'.code.toByte()) return false
    val pixelOffset = bytes.readLittleEndianUInt32(offset = 10)
    return pixelOffset >= 14L
}

private fun hasIcoHeader(bytes: ByteArray): Boolean =
    bytes.size >= 6 &&
        bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
        bytes[2] == 1.toByte() && bytes[3] == 0.toByte() &&
        (bytes[4].toInt() and 0xFF or ((bytes[5].toInt() and 0xFF) shl 8)) > 0

private fun ByteArray.readLittleEndianUInt32(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

private fun detectIsoBaseMediaImageMimeType(bytes: ByteArray): String? {
    if (bytes.size < 16 || bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) != "ftyp") return null

    val declaredBoxSize = bytes.take(4).fold(0L) { value, byte ->
        (value shl 8) or (byte.toLong() and 0xFF)
    }
    val compatibleBrandsStart = if (declaredBoxSize == 1L) 24 else 16
    if (bytes.size < compatibleBrandsStart) return null
    val boxEnd = when {
        declaredBoxSize == 0L || declaredBoxSize == 1L -> bytes.size
        declaredBoxSize < compatibleBrandsStart -> return null
        else -> min(declaredBoxSize, bytes.size.toLong()).toInt()
    }
    val brands = buildSet {
        add(bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII).lowercase())
        var offset = compatibleBrandsStart
        while (offset + 4 <= boxEnd) {
            add(bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII).lowercase())
            offset += 4
        }
    }
    return when {
        brands.any { it in AVIF_BRANDS } -> "image/avif"
        brands.any { it in HEIC_BRANDS } -> "image/heic"
        brands.any { it in HEIF_BRANDS } -> "image/heif"
        else -> null
    }
}

private val AVIF_BRANDS = setOf("avif", "avis")
private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis")
private val HEIF_BRANDS = setOf("mif1", "msf1")

private val SVG_ROOT_PREFIX = Regex(
    pattern = """(?is)^\s*(?:(?:<\?.*?\?>)|(?:<!--.*?-->))*\s*<(?:[A-Z_][A-Z0-9_.-]*:)?svg(?=\s|/?>)""",
    option = RegexOption.IGNORE_CASE,
)

private fun decodeXmlPrefix(bytes: ByteArray): String = when {
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
        bytes.toString(Charsets.UTF_16LE)
    bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
        bytes.toString(Charsets.UTF_16BE)
    bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == '<'.code.toByte() ->
        bytes.toString(Charsets.UTF_16BE)
    bytes.size >= 4 && bytes[0] == '<'.code.toByte() && bytes[1] == 0.toByte() ->
        bytes.toString(Charsets.UTF_16LE)
    else -> null
} ?: bytes.toString(Charsets.UTF_8)

private fun detectSvgMimeType(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val prefix = decodeXmlPrefix(bytes).trimStart('\uFEFF')
    return "image/svg+xml".takeIf { SVG_ROOT_PREFIX.containsMatchIn(prefix) }
}
