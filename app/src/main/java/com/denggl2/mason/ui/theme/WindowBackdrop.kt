package com.denggl2.mason.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.roundToInt
import kotlin.coroutines.resume

private const val BACKDROP_FADE_IN_MILLIS = 120
private const val BACKDROP_RELEASE_FALLBACK_MILLIS = 1_000L

internal class WindowBackdropSnapshot internal constructor(
    val image: ImageBitmap,
    val windowSize: IntSize,
    private val backingBitmap: Bitmap? = null,
) {
    private val lifecycleLock = Any()
    private var activeReferences = 0
    private var recycleGeneration = 0L
    private var releaseFenceView: View? = null

    internal fun retain(): Boolean = synchronized(lifecycleLock) {
        if (backingBitmap?.isRecycled == true) {
            false
        } else {
            activeReferences += 1
            recycleGeneration += 1
            true
        }
    }

    internal fun release(renderView: View? = null) {
        synchronized(lifecycleLock) {
            if (renderView != null) releaseFenceView = renderView
            if (activeReferences > 0) activeReferences -= 1
        }
        recycleWhenIdle()
    }

    internal fun recycleWhenIdle() {
        val bitmap = backingBitmap ?: return
        val recycleRequest = synchronized(lifecycleLock) {
            if (activeReferences != 0 || bitmap.isRecycled) return
            recycleGeneration += 1
            recycleGeneration to releaseFenceView
        }
        recycleAfterRenderCommit(
            bitmap = bitmap,
            generation = recycleRequest.first,
            renderView = recycleRequest.second,
        )
    }

    private fun recycleAfterRenderCommit(
        bitmap: Bitmap,
        generation: Long,
        renderView: View?,
    ) {
        val schedule = Runnable {
            if (!isRecycleRequestCurrent(bitmap, generation)) return@Runnable
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                renderView?.isAttachedToWindow == true &&
                renderView.isShown &&
                renderView.isHardwareAccelerated
            ) {
                val observer = renderView.viewTreeObserver
                if (observer.isAlive) {
                    observer.registerFrameCommitCallback {
                        recycleIfRequestCurrent(bitmap, generation)
                    }
                    Handler(Looper.getMainLooper()).postDelayed(
                        { recycleIfRequestCurrent(bitmap, generation) },
                        BACKDROP_RELEASE_FALLBACK_MILLIS,
                    )
                    renderView.invalidate()
                    return@Runnable
                }
            }
            // No attached hardware renderer means this Bitmap cannot still be
            // referenced by an in-flight hardware frame from this consumer.
            recycleIfRequestCurrent(bitmap, generation)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            schedule.run()
        } else {
            Handler(Looper.getMainLooper()).post(schedule)
        }
    }

    private fun isRecycleRequestCurrent(bitmap: Bitmap, generation: Long): Boolean =
        synchronized(lifecycleLock) {
            activeReferences == 0 &&
                recycleGeneration == generation &&
                !bitmap.isRecycled
        }

    private fun recycleIfRequestCurrent(bitmap: Bitmap, generation: Long) {
        synchronized(lifecycleLock) {
            if (
                activeReferences == 0 &&
                recycleGeneration == generation &&
                !bitmap.isRecycled
            ) {
                bitmap.recycle()
                recycleGeneration += 1
            }
        }
    }
}

internal data class WindowBackdropSampleGeometry(
    val sourceOffset: IntOffset,
    val sourceSize: IntSize,
    val destinationOffset: IntOffset,
    val destinationSize: IntSize,
)

internal fun calculateWindowBackdropSampleGeometry(
    windowPosition: IntOffset,
    layerSize: IntSize,
    windowSize: IntSize,
    imageSize: IntSize,
    bleedPixels: Int,
): WindowBackdropSampleGeometry? {
    if (
        layerSize.width <= 0 || layerSize.height <= 0 ||
        windowSize.width <= 0 || windowSize.height <= 0 ||
        imageSize.width <= 0 || imageSize.height <= 0
    ) {
        return null
    }

    val requestedLeft = windowPosition.x - bleedPixels
    val requestedTop = windowPosition.y - bleedPixels
    val destinationLeft = (-requestedLeft).coerceIn(0, layerSize.width)
    val destinationTop = (-requestedTop).coerceIn(0, layerSize.height)
    val destinationRight = (windowSize.width - requestedLeft).coerceIn(0, layerSize.width)
    val destinationBottom = (windowSize.height - requestedTop).coerceIn(0, layerSize.height)
    if (destinationRight <= destinationLeft || destinationBottom <= destinationTop) return null

    val scaleX = imageSize.width.toFloat() / windowSize.width.toFloat()
    val scaleY = imageSize.height.toFloat() / windowSize.height.toFloat()
    val sourceLeft = ((requestedLeft + destinationLeft) * scaleX)
        .roundToInt()
        .coerceIn(0, imageSize.width - 1)
    val sourceTop = ((requestedTop + destinationTop) * scaleY)
        .roundToInt()
        .coerceIn(0, imageSize.height - 1)
    val sourceRight = ((requestedLeft + destinationRight) * scaleX)
        .roundToInt()
        .coerceIn(sourceLeft + 1, imageSize.width)
    val sourceBottom = ((requestedTop + destinationBottom) * scaleY)
        .roundToInt()
        .coerceIn(sourceTop + 1, imageSize.height)

    return WindowBackdropSampleGeometry(
        sourceOffset = IntOffset(sourceLeft, sourceTop),
        sourceSize = IntSize(sourceRight - sourceLeft, sourceBottom - sourceTop),
        destinationOffset = IntOffset(destinationLeft, destinationTop),
        destinationSize = IntSize(
            destinationRight - destinationLeft,
            destinationBottom - destinationTop,
        ),
    )
}

@Composable
internal fun rememberWindowBackdropSnapshot(enabled: Boolean): WindowBackdropSnapshot? {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<WindowBackdropSnapshot?>(null) }
    LaunchedEffect(enabled, context) {
        snapshot = null
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        withFrameNanos { }
        snapshot = captureWindowBackdropSnapshot(context)
    }
    DisposableEffect(snapshot) {
        val currentSnapshot = snapshot
        val retained = currentSnapshot?.retain() == true
        onDispose {
            if (retained) currentSnapshot?.release() else currentSnapshot?.recycleWhenIdle()
        }
    }
    return snapshot
}

internal fun Modifier.windowBackdrop(
    snapshot: WindowBackdropSnapshot?,
    windowPosition: IntOffset,
    blurRadius: Dp,
    allowZeroPosition: Boolean = false,
): Modifier = composed {
    val canDrawBackdrop = snapshot != null &&
        (allowZeroPosition || windowPosition != IntOffset.Zero) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backdropAlpha by animateFloatAsState(
        targetValue = if (canDrawBackdrop) 1f else 0f,
        animationSpec = tween(durationMillis = BACKDROP_FADE_IN_MILLIS),
        label = "window_backdrop_alpha",
    )
    if (!canDrawBackdrop) {
        return@composed this
    }
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    val renderView = LocalView.current
    val blurredLayer = remember(graphicsContext, density.density, blurRadius, snapshot) {
        graphicsContext.createGraphicsLayer().also { layer ->
            val blurPx = with(density) { blurRadius.toPx() }
            layer.renderEffect = BlurEffect(
                radiusX = blurPx,
                radiusY = blurPx,
                edgeTreatment = TileMode.Clamp,
            )
        }
    }
    DisposableEffect(graphicsContext, blurredLayer, snapshot, renderView) {
        val retained = snapshot.retain()
        onDispose {
            graphicsContext.releaseGraphicsLayer(blurredLayer)
            if (retained) snapshot.release(renderView) else snapshot.recycleWhenIdle()
        }
    }
    drawWithContent {
        val bleedPixels = blurRadius.toPx().roundToInt()
        val layerSize = IntSize(
            width = (size.width.roundToInt() + bleedPixels * 2).coerceAtLeast(1),
            height = (size.height.roundToInt() + bleedPixels * 2).coerceAtLeast(1),
        )
        val geometry = calculateWindowBackdropSampleGeometry(
            windowPosition = windowPosition,
            layerSize = layerSize,
            windowSize = snapshot.windowSize,
            imageSize = IntSize(snapshot.image.width, snapshot.image.height),
            bleedPixels = bleedPixels,
        )
        if (geometry != null) {
            blurredLayer.record(geometry.destinationSize) {
                drawImage(
                    image = snapshot.image,
                    srcOffset = geometry.sourceOffset,
                    srcSize = geometry.sourceSize,
                    dstOffset = IntOffset.Zero,
                    dstSize = geometry.destinationSize,
                )
            }
            blurredLayer.alpha = backdropAlpha
            clipRect {
                translate(
                    left = (geometry.destinationOffset.x - bleedPixels).toFloat(),
                    top = (geometry.destinationOffset.y - bleedPixels).toFloat(),
                ) {
                    drawLayer(blurredLayer)
                }
            }
        }
        drawContent()
    }
}

internal fun Modifier.windowBackdropMaterial(
    enabled: Boolean,
    blurRadius: Dp,
    fallbackColor: Color,
): Modifier = composed {
    val snapshot = rememberWindowBackdropSnapshot(enabled)
    var windowPosition by remember { mutableStateOf(IntOffset.Zero) }
    val fallbackAlpha by animateFloatAsState(
        targetValue = if (enabled && snapshot == null) 1f else 0f,
        animationSpec = tween(durationMillis = BACKDROP_FADE_IN_MILLIS),
        label = "window_backdrop_fallback_alpha",
    )
    this
        .onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            windowPosition = IntOffset(position.x.roundToInt(), position.y.roundToInt())
        }
        .windowBackdrop(
            snapshot = snapshot,
            windowPosition = windowPosition,
            blurRadius = blurRadius,
            allowZeroPosition = true,
        )
        .background(fallbackColor.copy(alpha = fallbackColor.alpha * fallbackAlpha))
}

internal suspend fun captureWindowBackdropSnapshot(context: Context): WindowBackdropSnapshot? {
    val activity = context.findComponentActivity() ?: return null
    val rootView = activity.window.decorView
    val windowWidth = rootView.width
    val windowHeight = rootView.height
    if (windowWidth <= 0 || windowHeight <= 0) return null

    // Half-resolution capture keeps transient popup blur inexpensive while the
    // local GPU layer performs the final blur at display resolution.
    val bitmap = Bitmap.createBitmap(
        (windowWidth * 0.5f).roundToInt().coerceAtLeast(1),
        (windowHeight * 0.5f).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                activity.window,
                bitmap,
                { result ->
                    if (!continuation.isActive) {
                        recycleBitmapOnMainThread(bitmap)
                    } else if (result == PixelCopy.SUCCESS) {
                        val snapshot = WindowBackdropSnapshot(
                            image = bitmap.asImageBitmap(),
                            windowSize = IntSize(windowWidth, windowHeight),
                            backingBitmap = bitmap,
                        )
                        continuation.resume(snapshot) { _, unusedSnapshot, _ ->
                            unusedSnapshot.recycleWhenIdle()
                        }
                    } else {
                        recycleBitmapOnMainThread(bitmap)
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: IllegalArgumentException) {
            recycleBitmapOnMainThread(bitmap)
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

private fun recycleBitmapOnMainThread(bitmap: Bitmap) {
    val recycle = Runnable {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        recycle.run()
    } else {
        Handler(Looper.getMainLooper()).post(recycle)
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
