package com.denggl2.masonremote.ui.theme

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
    // Dark glass keeps a directional rim light, but it should remain a
    // material cue rather than becoming a visible outline.
    emphasizeDarkGlass: Boolean = true,
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
                if (emphasizeDarkGlass) {
                    // Keep the lower-right edge slightly darker so the rim
                    // reads as glass without becoming a white ring.
                    arrayOf(
                        0f to Color.White.copy(alpha = 0.067f),
                        0.38f to Color.White.copy(alpha = 0.030f),
                        0.72f to Color.White.copy(alpha = 0.012f),
                        1f to Color.Black.copy(alpha = 0.067f),
                    )
                } else {
                    arrayOf(
                        0f to Color.White.copy(alpha = 0.053f),
                        0.42f to Color.White.copy(alpha = 0.023f),
                        0.72f to Color.White.copy(alpha = 0.008f),
                        1f to Color.Black.copy(alpha = 0.053f),
                    )
                }
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
        val topSpecularBrush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = if (darkTheme && emphasizeDarkGlass) 0.100f else if (darkTheme) 0.080f else 0.68f),
                0.18f to Color.White.copy(alpha = if (darkTheme && emphasizeDarkGlass) 0.047f else if (darkTheme) 0.037f else 0.42f),
                0.48f to Color.Transparent,
                1f to Color.Transparent,
            ),
            startY = 0f,
            endY = size.height,
        )
        val leftSpecularBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = if (darkTheme && emphasizeDarkGlass) 0.083f else if (darkTheme) 0.067f else 0.62f),
                0.18f to Color.White.copy(alpha = if (darkTheme && emphasizeDarkGlass) 0.040f else if (darkTheme) 0.033f else 0.36f),
                0.48f to Color.Transparent,
                1f to Color.Transparent,
            ),
            startX = 0f,
            endX = size.width,
        )
        onDrawWithContent {
            drawContent()
            drawPath(
                path = edgePath,
                brush = baseBrush,
                style = Stroke(
                    width = (if (darkTheme) {
                        if (emphasizeDarkGlass) 0.85.dp else GlassBaseEdgeWidth
                    } else {
                        GlassBaseEdgeWidth
                    }).toPx(),
                ),
            )
            drawPath(
                path = edgePath,
                brush = topSpecularBrush,
                style = Stroke(
                    width = (if (darkTheme) {
                        if (emphasizeDarkGlass) 1.25.dp else GlassSpecularEdgeWidth
                    } else {
                        GlassSpecularEdgeWidth
                    }).toPx(),
                ),
            )
            drawPath(
                path = edgePath,
                brush = leftSpecularBrush,
                style = Stroke(
                    width = (if (darkTheme) {
                        if (emphasizeDarkGlass) 1.25.dp else GlassSpecularEdgeWidth
                    } else {
                        GlassSpecularEdgeWidth
                    }).toPx(),
                ),
            )
        }
    }
}
