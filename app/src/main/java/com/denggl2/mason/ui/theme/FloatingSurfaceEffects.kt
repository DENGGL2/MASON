package com.denggl2.mason.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GlassBaseEdgeWidth = 0.75.dp
private val GlassSpecularEdgeWidth = 1.15.dp

@Composable
internal fun floatingSurfaceShadowColor(): Color {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glass = LocalInterfaceEffects.current.glassMaterialEnabled
    return when {
        darkTheme && glass -> Color.Black.copy(alpha = 0.52f)
        darkTheme -> Color.Black.copy(alpha = 0.34f)
        glass -> Color(0xFF7F8794).copy(alpha = 0.24f)
        else -> Color(0xFF9EA4AF).copy(alpha = 0.22f)
    }
}

internal fun Modifier.floatingSurfaceEdge(
    shape: Shape,
    nonGlassWidth: Dp = 0.5.dp,
    nonGlassColor: Color? = null,
): Modifier = composed {
    val interfaceEffects = LocalInterfaceEffects.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (!interfaceEffects.glassMaterialEnabled) {
        val resolvedColor = nonGlassColor ?: MaterialTheme.colorScheme.outline
        return@composed if (nonGlassWidth > 0.dp) {
            border(nonGlassWidth, resolvedColor, shape)
        } else {
            this
        }
    }

    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val edgePath = Path().apply {
            when (outline) {
                is Outline.Generic -> addPath(outline.path)
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
            }
        }
        val baseBrush = Brush.linearGradient(
            colorStops = if (darkTheme) {
                arrayOf(
                    0f to Color.White.copy(alpha = 0.24f),
                    0.42f to Color.White.copy(alpha = 0.12f),
                    0.72f to Color.White.copy(alpha = 0.06f),
                    1f to Color.Black.copy(alpha = 0.30f),
                )
            } else {
                arrayOf(
                    0f to Color.White.copy(alpha = 0.78f),
                    0.40f to Color.White.copy(alpha = 0.48f),
                    0.72f to Color(0xFF89919D).copy(alpha = 0.22f),
                    1f to Color(0xFF4F5661).copy(alpha = 0.28f),
                )
            },
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val specularBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = if (darkTheme) 0.46f else 0.82f),
                0.20f to Color.White.copy(alpha = if (darkTheme) 0.30f else 0.58f),
                0.45f to Color.White.copy(alpha = if (darkTheme) 0.10f else 0.22f),
                0.68f to Color.Transparent,
                1f to Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(size.width * 0.88f, size.height * 0.88f),
        )
        onDrawWithContent {
            drawContent()
            drawPath(
                path = edgePath,
                brush = baseBrush,
                style = Stroke(width = GlassBaseEdgeWidth.toPx()),
            )
            drawPath(
                path = edgePath,
                brush = specularBrush,
                style = Stroke(width = GlassSpecularEdgeWidth.toPx()),
            )
        }
    }
}
