package com.denggl2.masonremote.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

@Composable
internal fun rememberProgressiveEdgeBlurState(enabled: Boolean): HazeState? =
    if (enabled) remember { HazeState() } else null

internal fun Modifier.captureProgressiveEdgeBlur(state: HazeState?): Modifier = this

internal fun Modifier.progressiveEdgeBlur(
    state: HazeState?,
    edge: ProgressiveBlurEdge,
    backgroundColor: Color,
    blurRadius: Dp = 15.dp,
    smoothBoundary: Boolean = false,
    gradientStartY: Dp? = null,
    gradientEndY: Dp? = null,
): Modifier = this
