package com.denggl2.masonremote.ui

import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

private const val DefaultSvgWidth = 133.333f
private const val DefaultSvgHeight = 133.333f
private val SvgNumberPattern = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")

private sealed interface SvgShape {
    data class PathShape(
        val path: Path,
        val fillColor: Color?,
        val strokeColor: Color?,
        val strokeWidth: Float,
    ) : SvgShape

    data class CircleShape(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val fillColor: Color?,
        val strokeColor: Color?,
        val strokeWidth: Float,
    ) : SvgShape

    data class LineShape(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val strokeColor: Color?,
        val strokeWidth: Float,
    ) : SvgShape

    data class RectShape(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val fillColor: Color?,
        val strokeColor: Color?,
        val strokeWidth: Float,
    ) : SvgShape
}

private data class SvgDocument(
    val width: Float,
    val height: Float,
    val shapes: List<SvgShape>,
)

@Composable
internal fun FigmaSvgAsset(
    assetPath: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
) {
    val context = LocalContext.current
    val svg = remember(assetPath) {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }
    val document = remember(svg, darkTheme) { parseSvgDocument(svg, darkTheme) }

    Canvas(modifier = modifier) {
        if (document.shapes.isEmpty() || document.width <= 0f || document.height <= 0f) {
            return@Canvas
        }
        scale(
            scaleX = size.width / document.width,
            scaleY = size.height / document.height,
            pivot = Offset.Zero,
        ) {
            document.shapes.forEach { shape ->
                when (shape) {
                    is SvgShape.PathShape -> {
                        shape.fillColor?.let { color ->
                            drawPath(path = shape.path, color = color)
                        }
                        shape.strokeColor?.let { color ->
                            drawPath(
                                path = shape.path,
                                color = color,
                                style = Stroke(width = shape.strokeWidth),
                            )
                        }
                    }

                    is SvgShape.CircleShape -> {
                        shape.fillColor?.let { color ->
                            drawCircle(
                                color = color,
                                radius = shape.radius,
                                center = Offset(shape.cx, shape.cy),
                            )
                        }
                        shape.strokeColor?.let { color ->
                            drawCircle(
                                color = color,
                                radius = shape.radius,
                                center = Offset(shape.cx, shape.cy),
                                style = Stroke(width = shape.strokeWidth),
                            )
                        }
                    }

                    is SvgShape.LineShape -> {
                        shape.strokeColor?.let { color ->
                            drawLine(
                                color = color,
                                start = Offset(shape.x1, shape.y1),
                                end = Offset(shape.x2, shape.y2),
                                strokeWidth = shape.strokeWidth,
                            )
                        }
                    }

                    is SvgShape.RectShape -> {
                        val rectSize = Size(
                            width = shape.right - shape.left,
                            height = shape.bottom - shape.top,
                        )
                        shape.fillColor?.let { color ->
                            drawRect(
                                color = color,
                                topLeft = Offset(shape.left, shape.top),
                                size = rectSize,
                            )
                        }
                        shape.strokeColor?.let { color ->
                            drawRect(
                                color = color,
                                topLeft = Offset(shape.left, shape.top),
                                size = rectSize,
                                style = Stroke(width = shape.strokeWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseSvgDocument(svg: String, darkTheme: Boolean): SvgDocument {
    var width = DefaultSvgWidth
    var height = DefaultSvgHeight
    var inheritedFill: Color? = Color.Black
    val shapes = mutableListOf<SvgShape>()

    runCatching {
        val parser = Xml.newPullParser().apply { setInput(StringReader(svg)) }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "svg" -> {
                        val viewBox = parser.getAttributeValue(null, "viewBox")?.let(::parseNumbers)
                        if (viewBox != null && viewBox.size >= 4) {
                            width = viewBox[2]
                            height = viewBox[3]
                        } else {
                            width = parser.getAttributeValue(null, "width").toSvgFloat() ?: width
                            height = parser.getAttributeValue(null, "height").toSvgFloat() ?: height
                        }
                        inheritedFill = parser.paintColor("fill", default = inheritedFill, darkTheme = darkTheme)
                    }

                    "path" -> {
                        val data = parser.getAttributeValue(null, "d")
                        val path = data?.let {
                            runCatching { PathParser().parsePathString(it).toPath(Path()) }.getOrNull()
                        }
                        if (path != null) {
                            shapes += SvgShape.PathShape(
                                path = path,
                                fillColor = parser.paintColor("fill", default = inheritedFill, darkTheme = darkTheme),
                                strokeColor = parser.paintColor("stroke", default = null, darkTheme = darkTheme),
                                strokeWidth = parser.strokeWidth(),
                            )
                        }
                    }

                    "circle" -> {
                        val radius = parser.getAttributeValue(null, "r").toSvgFloat() ?: 0f
                        if (radius > 0f) {
                            shapes += SvgShape.CircleShape(
                                cx = parser.getAttributeValue(null, "cx").toSvgFloat() ?: 0f,
                                cy = parser.getAttributeValue(null, "cy").toSvgFloat() ?: 0f,
                                radius = radius,
                                fillColor = parser.paintColor("fill", default = inheritedFill, darkTheme = darkTheme),
                                strokeColor = parser.paintColor("stroke", default = null, darkTheme = darkTheme),
                                strokeWidth = parser.strokeWidth(),
                            )
                        }
                    }

                    "line" -> {
                        shapes += SvgShape.LineShape(
                            x1 = parser.getAttributeValue(null, "x1").toSvgFloat() ?: 0f,
                            y1 = parser.getAttributeValue(null, "y1").toSvgFloat() ?: 0f,
                            x2 = parser.getAttributeValue(null, "x2").toSvgFloat() ?: 0f,
                            y2 = parser.getAttributeValue(null, "y2").toSvgFloat() ?: 0f,
                            strokeColor = parser.paintColor("stroke", default = null, darkTheme = darkTheme),
                            strokeWidth = parser.strokeWidth(),
                        )
                    }

                    "rect" -> {
                        val left = parser.getAttributeValue(null, "x").toSvgFloat() ?: 0f
                        val top = parser.getAttributeValue(null, "y").toSvgFloat() ?: 0f
                        val rectWidth = parser.getAttributeValue(null, "width").toSvgFloat() ?: 0f
                        val rectHeight = parser.getAttributeValue(null, "height").toSvgFloat() ?: 0f
                        if (rectWidth > 0f && rectHeight > 0f) {
                            shapes += SvgShape.RectShape(
                                left = left,
                                top = top,
                                right = left + rectWidth,
                                bottom = top + rectHeight,
                                fillColor = parser.paintColor("fill", default = inheritedFill, darkTheme = darkTheme),
                                strokeColor = parser.paintColor("stroke", default = null, darkTheme = darkTheme),
                                strokeWidth = parser.strokeWidth(),
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
    }

    return SvgDocument(width = width, height = height, shapes = shapes)
}

private fun XmlPullParser.paintColor(
    attribute: String,
    default: Color?,
    darkTheme: Boolean,
): Color? {
    val value = getAttributeValue(null, attribute)?.trim()?.lowercase() ?: return default
    if (value == "none" || value == "transparent") return null
    val opacity = getAttributeValue(null, "$attribute-opacity")
        ?.toSvgFloat()
        ?.coerceIn(0f, 1f)
        ?: 1f
    return parseSvgColor(value)
        ?.let { adaptSvgColor(it, darkTheme).copy(alpha = it.alpha * opacity) }
        ?: default
}

private fun XmlPullParser.strokeWidth(): Float =
    getAttributeValue(null, "stroke-width").toSvgFloat()?.coerceAtLeast(0f) ?: 1f

private fun String?.toSvgFloat(): Float? =
    this?.let { SvgNumberPattern.find(it)?.value?.toFloatOrNull() }

private fun parseSvgColor(value: String): Color? {
    if (!value.startsWith("#")) return null
    val hex = value.removePrefix("#")
    val argb = when (hex.length) {
        3 -> {
            val r = hex[0].toString().repeat(2).toIntOrNull(16) ?: return null
            val g = hex[1].toString().repeat(2).toIntOrNull(16) ?: return null
            val b = hex[2].toString().repeat(2).toIntOrNull(16) ?: return null
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        6 -> hex.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() } ?: return null
        8 -> hex.toLongOrNull(16)?.toInt() ?: return null
        else -> return null
    }
    return Color(argb)
}

private fun adaptSvgColor(color: Color, darkTheme: Boolean): Color {
    if (!darkTheme) return color
    return when {
        color.luminance() < 0.18f -> Color(0xFFF5F5F7)
        color.luminance() < 0.60f -> Color(0xFFA9A9B1)
        else -> color.copy(alpha = 0.82f)
    }
}

private fun parseNumbers(value: String): List<Float> =
    SvgNumberPattern.findAll(value).mapNotNull { it.value.toFloatOrNull() }.toList()
