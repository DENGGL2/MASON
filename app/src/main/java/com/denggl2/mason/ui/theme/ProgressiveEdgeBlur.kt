package com.denggl2.mason.ui.theme

import android.os.Build
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

internal enum class ProgressiveBlurEdge {
    Top,
    Bottom,
}

internal fun preferPerformanceProgressiveBlur(sdkInt: Int): Boolean =
    sdkInt < Build.VERSION_CODES.TIRAMISU

@Composable
internal fun rememberProgressiveEdgeBlurState(enabled: Boolean): HazeState? {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return remember { HazeState() }
}

internal fun Modifier.captureProgressiveEdgeBlur(state: HazeState?): Modifier =
    if (state == null) this else haze(state)

internal fun Modifier.progressiveEdgeBlur(
    state: HazeState?,
    edge: ProgressiveBlurEdge,
    backgroundColor: Color,
    blurRadius: Dp = 15.dp,
    smoothBoundary: Boolean = false,
    gradientStartY: Dp? = null,
    gradientEndY: Dp? = null,
): Modifier = composed {
    state ?: return@composed this
    val density = LocalDensity.current
    val interfaceEffects = LocalInterfaceEffects.current
    val effectiveBlurRadius = interfaceEffects.resolveBackdropBlurRadius(
        nonGlassRadius = blurRadius,
    )
    hazeChild(
        state = state,
        style = HazeStyle(
            backgroundColor = backgroundColor,
            tint = HazeTint(Color.Transparent),
            blurRadius = effectiveBlurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(Color.Transparent),
        ),
    ) {
        blurEnabled = true
        progressive = HazeProgressive.verticalGradient(
            easing = if (smoothBoundary && edge == ProgressiveBlurEdge.Top) EaseOut else EaseIn,
            startY = gradientStartY?.let { with(density) { it.toPx() } } ?: 0f,
            startIntensity = if (edge == ProgressiveBlurEdge.Top) 1f else 0f,
            endY = gradientEndY?.let { with(density) { it.toPx() } }
                ?: Float.POSITIVE_INFINITY,
            endIntensity = if (edge == ProgressiveBlurEdge.Top) 0f else 1f,
            // Short layered gradients visibly step on API 31-32; use Haze's continuous mask there.
            preferPerformance = preferPerformanceProgressiveBlur(Build.VERSION.SDK_INT),
        )
    }
}
