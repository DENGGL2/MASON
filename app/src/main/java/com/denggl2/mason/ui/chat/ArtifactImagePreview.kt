package com.denggl2.mason.ui.chat

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.denggl2.mason.data.ArtifactMetadata
import com.denggl2.mason.ui.theme.LocalInterfaceEffects
import com.denggl2.mason.ui.theme.floatingSurfaceEdge
import androidx.compose.ui.graphics.luminance
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private const val ARTIFACT_THUMBNAIL_MAX_EDGE = 1440
private const val ARTIFACT_PREVIEW_MAX_EDGE = 4096
private const val MAX_RASTER_PREVIEW_BYTES = 100L * 1024L * 1024L
private const val MAX_SVG_PREVIEW_BYTES = 8L * 1024L * 1024L
private const val MIN_IMAGE_SCALE = 1f
private const val MAX_IMAGE_SCALE = 5f

@Composable
internal fun ArtifactImageThumbnail(
    artifact: ArtifactMetadata,
    onClick: () -> Unit,
) {
    if (isSvgImageArtifact(artifact)) {
        ArtifactSvgImageThumbnail(artifact = artifact, onClick = onClick)
        return
    }
    val bitmapState by rememberArtifactBitmap(artifact, ARTIFACT_THUMBNAIL_MAX_EDGE)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                .clickable(enabled = bitmapState !is ArtifactBitmapState.Loading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = bitmapState) {
                ArtifactBitmapState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )

                is ArtifactBitmapState.Ready -> Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "预览 ${artifact.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                is ArtifactBitmapState.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.ImageIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            ArtifactImageInfoOverlay(
                artifact = artifact,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun ArtifactSvgImageThumbnail(
    artifact: ArtifactMetadata,
    onClick: () -> Unit,
) {
    val svgState by rememberSvgArtifactContent(artifact)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = svgState) {
                ArtifactSvgState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )

                is ArtifactSvgState.Ready -> {
                    ArtifactSvgWebView(
                        svg = state.content,
                        enableZoom = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is ArtifactSvgState.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.ImageIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            if (svgState !is ArtifactSvgState.Loading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClick = onClick),
                )
            }
            ArtifactImageInfoOverlay(
                artifact = artifact,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun ArtifactImageInfoOverlay(
    artifact: ArtifactMetadata,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.56f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ImageIcon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = formatArtifactImageSize(artifact.bytes),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 10.sp,
        )
    }
}

private fun Modifier.artifactPreviewMaterial(): Modifier = composed {
    val surface = MaterialTheme.colorScheme.background
    // Image preview is a full-screen reading surface. Keep it opaque so the
    // underlying page never bleeds through, while still following light/dark
    // theme colors.
    background(surface)
}

@Composable
private fun ArtifactSvgImagePreviewDialog(
    artifact: ArtifactMetadata,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    val svgState by rememberSvgArtifactContent(artifact)
    val saveAction = rememberArtifactImageSaveAction(artifact)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .artifactPreviewMaterial(),
        ) {
            ArtifactPreviewWindowEffects()
            when (val state = svgState) {
                ArtifactSvgState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )

                is ArtifactSvgState.Failed -> Text(
                    text = state.message,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center),
                )

                is ArtifactSvgState.Ready -> ArtifactSvgWebView(
                    svg = state.content,
                    enableZoom = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 60.dp, bottom = 88.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtifactImagePreviewAction(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "\u8fd4\u56de",
                    onClick = onDismiss,
                    enabled = true,
                    size = 44.dp,
                )
                Text(
                    text = "图片预览",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Spacer(Modifier.size(44.dp))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ArtifactImagePreviewAction(
                    icon = Icons.Outlined.FileDownload,
                    contentDescription = if (saveAction.saving) "正在保存图片" else "保存图片到本地",
                    enabled = !saveAction.saving && svgState !is ArtifactSvgState.Loading,
                    showProgress = saveAction.saving,
                    onClick = saveAction.onSave,
                )
                ArtifactImagePreviewAction(
                    icon = Icons.Outlined.Share,
                    contentDescription = "分享图片",
                    enabled = svgState !is ArtifactSvgState.Loading,
                    onClick = onShare,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun ArtifactImagePreviewDialog(
    artifact: ArtifactMetadata,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    if (isSvgImageArtifact(artifact)) {
        ArtifactSvgImagePreviewDialog(
            artifact = artifact,
            onDismiss = onDismiss,
            onShare = onShare,
        )
        return
    }
    val bitmapState by rememberArtifactBitmap(artifact, ARTIFACT_PREVIEW_MAX_EDGE)
    val saveAction = rememberArtifactImageSaveAction(artifact)
    var scale by remember(artifact.path) { mutableFloatStateOf(MIN_IMAGE_SCALE) }
    var offset by remember(artifact.path) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(artifact.path) { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .artifactPreviewMaterial()
                .clipToBounds(),
        ) {
            ArtifactPreviewWindowEffects()
            when (val state = bitmapState) {
                ArtifactBitmapState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )

                is ArtifactBitmapState.Failed -> Text(
                    text = state.message,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center),
                )

                is ArtifactBitmapState.Ready -> {
                    val bitmap = state.bitmap
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = artifact.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size ->
                                viewportSize = size
                                offset = clampPreviewOffset(
                                    offset = offset,
                                    scale = scale,
                                    viewportSize = size,
                                    imageWidth = bitmap.width,
                                    imageHeight = bitmap.height,
                                )
                            }
                            .pointerInput(bitmap.width, bitmap.height, viewportSize) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    if (viewportSize.width <= 0 || viewportSize.height <= 0) {
                                        return@detectTransformGestures
                                    }
                                    val zoomed = applyPreviewZoom(
                                        centroid = centroid,
                                        zoom = zoom,
                                        pan = pan,
                                        scale = scale,
                                        offset = offset,
                                        viewportSize = viewportSize,
                                        imageWidth = bitmap.width,
                                        imageHeight = bitmap.height,
                                    )
                                    scale = zoomed.scale
                                    offset = zoomed.offset
                                }
                            }
                            .pointerInteropFilter { event ->
                                if (event.actionMasked != MotionEvent.ACTION_SCROLL) return@pointerInteropFilter false
                                val wheelDelta = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                if (wheelDelta == 0f) return@pointerInteropFilter false
                                val zoomed = applyPreviewZoom(
                                    centroid = Offset(event.x, event.y),
                                    zoom = if (wheelDelta > 0f) 1.12f else 0.89f,
                                    pan = Offset.Zero,
                                    scale = scale,
                                    offset = offset,
                                    viewportSize = viewportSize,
                                    imageWidth = bitmap.width,
                                    imageHeight = bitmap.height,
                                )
                                scale = zoomed.scale
                                offset = zoomed.offset
                                true
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtifactImagePreviewAction(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "\u8fd4\u56de",
                    onClick = onDismiss,
                    enabled = true,
                    size = 44.dp,
                )
                Text(
                    text = "图片预览",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Spacer(Modifier.size(44.dp))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ArtifactImagePreviewAction(
                    icon = Icons.Outlined.FileDownload,
                    contentDescription = if (saveAction.saving) "正在保存图片" else "保存图片到本地",
                    enabled = !saveAction.saving && bitmapState !is ArtifactBitmapState.Loading,
                    showProgress = saveAction.saving,
                    onClick = saveAction.onSave,
                )
                ArtifactImagePreviewAction(
                    icon = Icons.Outlined.Share,
                    contentDescription = "分享图片",
                    enabled = bitmapState !is ArtifactBitmapState.Loading,
                    onClick = onShare,
                )
            }
        }
    }
}

@Composable
private fun ArtifactPreviewWindowEffects() {
    val view = LocalView.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dialogWindow = generateSequence(view as android.view.ViewParent?) { current ->
        (current as? android.view.View)?.parent
    }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    DisposableEffect(dialogWindow, darkTheme) {
        val window = dialogWindow
        val decorView = window?.decorView
        val previousStatusBarColor = window?.statusBarColor
        val previousNavigationBarColor = window?.navigationBarColor
        val previousNavigationBarDividerColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.navigationBarDividerColor
        } else {
            null
        }
        val previousContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced
        } else {
            null
        }
        val previousSystemUiVisibility = decorView?.systemUiVisibility
        if (window != null && decorView != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            WindowInsetsControllerCompat(window, decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose {
            if (previousStatusBarColor != null) window?.statusBarColor = previousStatusBarColor
            if (previousNavigationBarColor != null) window?.navigationBarColor = previousNavigationBarColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && previousNavigationBarDividerColor != null) {
                window?.navigationBarDividerColor = previousNavigationBarDividerColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrastEnforced != null) {
                window?.isNavigationBarContrastEnforced = previousContrastEnforced
            }
            if (previousSystemUiVisibility != null) {
                decorView?.systemUiVisibility = previousSystemUiVisibility
            }
        }
    }
}

@Composable
private fun ArtifactImagePreviewAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    showProgress: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    onClick: () -> Unit,
) {
    val effects = LocalInterfaceEffects.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .masonGlassShadow(cornerRadius = size / 2f)
            .clip(CircleShape)
            // The preview itself already owns the backdrop material. Keep its
            // actions on a single, denser surface instead of sampling the same
            // background a second time (glass-on-glass).
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = if (effects.glassMaterialEnabled) {
                        effects.compactSurfaceAlpha.coerceAtLeast(0.86f)
                    } else {
                        0.92f
                    },
                ),
            )
            .floatingSurfaceEdge(
                shape = CircleShape,
                nonGlassWidth = 0.5.dp,
                emphasizeDarkGlass = true,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = if (effects.glassMaterialEnabled) null else LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                color = MaterialTheme.colorScheme.onBackground,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.42f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private data class ArtifactPreviewTransform(
    val scale: Float,
    val offset: Offset,
)

private fun applyPreviewZoom(
    centroid: Offset,
    zoom: Float,
    pan: Offset,
    scale: Float,
    offset: Offset,
    viewportSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): ArtifactPreviewTransform {
    if (
        viewportSize.width <= 0 ||
            viewportSize.height <= 0 ||
            !zoom.isFinite() ||
            zoom <= 0f
    ) {
        return ArtifactPreviewTransform(scale = scale, offset = offset)
    }
    val nextScale = (scale * zoom).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
    val ratio = nextScale / scale
    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val proposedOffset = Offset(
        x = offset.x * ratio + (centroid.x - viewportCenter.x) * (1f - ratio) + pan.x,
        y = offset.y * ratio + (centroid.y - viewportCenter.y) * (1f - ratio) + pan.y,
    )
    return ArtifactPreviewTransform(
        scale = nextScale,
        offset = clampPreviewOffset(
            offset = proposedOffset,
            scale = nextScale,
            viewportSize = viewportSize,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        ),
    )
}


private data class ArtifactImageSaveAction(
    val saving: Boolean,
    val onSave: () -> Unit,
)

@Composable
private fun rememberArtifactImageSaveAction(artifact: ArtifactMetadata): ArtifactImageSaveAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember(artifact.path) { mutableStateOf(false) }
    val performSave = {
        if (!saving) {
            saving = true
            scope.launch {
                val result = saveImageArtifactToGallery(context, artifact)
                saving = false
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { saved -> "已保存到 ${saved.directory}" },
                        onFailure = { error -> "保存失败：${error.message ?: "无法写入图片"}" },
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            performSave()
        } else {
            Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
        }
    }
    return ArtifactImageSaveAction(
        saving = saving,
        onSave = {
            val needsLegacyStoragePermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
            if (needsLegacyStoragePermission) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                performSave()
            }
        },
    )
}

private sealed interface ArtifactSvgState {
    data object Loading : ArtifactSvgState
    data class Ready(val content: String) : ArtifactSvgState
    data class Failed(val message: String) : ArtifactSvgState
}

@Composable
private fun rememberSvgArtifactContent(artifact: ArtifactMetadata): State<ArtifactSvgState> {
    val context = LocalContext.current
    return produceState<ArtifactSvgState>(
        initialValue = ArtifactSvgState.Loading,
        key1 = artifact.path,
        key2 = artifact.bytes,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val validated = validateImageArtifact(context, artifact).getOrThrow()
                require(validated.mimeType == "image/svg+xml") { "文件不是 SVG 图片" }
                require(validated.file.length() <= MAX_SVG_PREVIEW_BYTES) { "SVG 图片超过 8 MB" }
                normalizeSvgForPreview(validated.file.readBytes())
            }.fold(
                onSuccess = ArtifactSvgState::Ready,
                onFailure = { ArtifactSvgState.Failed(it.message ?: "SVG 图片无法预览") },
            )
        }
    }
}

@Composable
private fun ArtifactSvgWebView(
    svg: String,
    enableZoom: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val svgKey = remember(svg) { svg.length to svg.hashCode() }
    val webView = remember(context, enableZoom) {
        ArtifactSvgPreviewWebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            isHorizontalScrollBarEnabled = enableZoom
            isVerticalScrollBarEnabled = enableZoom
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = false
                javaScriptCanOpenWindowsAutomatically = false
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                builtInZoomControls = enableZoom
                displayZoomControls = false
                setSupportZoom(enableZoom)
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val scheme = request?.url?.scheme?.lowercase(Locale.ROOT)
                    return if (scheme == null || scheme == "about" || scheme == "data") {
                        super.shouldInterceptRequest(view, request)
                    } else {
                        blockedSvgResourceResponse()
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = true
            }
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    DisposableEffect(webView, svgKey) {
        fun loadContent() {
            if (webView.width <= 0 || webView.height <= 0) return
            val contentKey = Triple(svgKey, webView.width, webView.height)
            if (webView.tag == contentKey) return
            val density = webView.resources.displayMetrics.density.coerceAtLeast(1f)
            val document = buildSvgPreviewDocument(
                svg = svg,
                viewportWidthCssPx = ceil(webView.width / density).toInt().coerceAtLeast(1),
                viewportHeightCssPx = ceil(webView.height / density).toInt().coerceAtLeast(1),
            )
            webView.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
            webView.tag = contentKey
        }

        val layoutListener = View.OnLayoutChangeListener { view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (view.width > 0 && view.height > 0 && sizeChanged) loadContent()
        }
        webView.addOnLayoutChangeListener(layoutListener)
        if (webView.isLaidOut && webView.width > 0 && webView.height > 0) {
            loadContent()
        }

        onDispose {
            webView.removeOnLayoutChangeListener(layoutListener)
        }
    }
    AndroidView(
        factory = { webView },
        modifier = modifier,
    )
}

private class ArtifactSvgPreviewWebView(context: Context) : WebView(context) {
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val wheelDelta = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (wheelDelta != 0f && settings.supportZoom()) {
                if (wheelDelta > 0f) zoomIn() else zoomOut()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}

private fun blockedSvgResourceResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "UTF-8",
    ByteArrayInputStream(ByteArray(0)),
)

private sealed interface ArtifactBitmapState {
    data object Loading : ArtifactBitmapState
    data class Ready(val bitmap: Bitmap) : ArtifactBitmapState
    data class Failed(val message: String) : ArtifactBitmapState
}

@Composable
private fun rememberArtifactBitmap(
    artifact: ArtifactMetadata,
    maxEdge: Int,
): State<ArtifactBitmapState> {
    val context = LocalContext.current
    val state = produceState<ArtifactBitmapState>(
        initialValue = ArtifactBitmapState.Loading,
        key1 = artifact.path,
        key2 = artifact.bytes,
        key3 = maxEdge,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeArtifactBitmap(context, artifact, maxEdge) }
                .fold(
                    onSuccess = ArtifactBitmapState::Ready,
                    onFailure = { ArtifactBitmapState.Failed(it.message ?: "图片无法预览") },
                )
        }
    }
    val bitmap = (state.value as? ArtifactBitmapState.Ready)?.bitmap
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    return state
}

private fun decodeArtifactBitmap(
    context: Context,
    artifact: ArtifactMetadata,
    maxEdge: Int,
): Bitmap {
    val validated = validateImageArtifact(context, artifact).getOrThrow()
    require(validated.mimeType != "image/svg+xml") { "SVG 图片需要使用矢量预览" }
    require(validated.file.length() <= MAX_RASTER_PREVIEW_BYTES) { "图片超过 100 MB，无法直接预览" }
    val source = ImageDecoder.createSource(validated.file)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        require(width > 0 && height > 0) { "图片尺寸无效" }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSampleSize(calculateArtifactPreviewSampleSize(width, height, maxEdge))
    }
}

internal data class ValidatedImageArtifact(
    val file: File,
    val mimeType: String,
)

internal data class SavedImageArtifact(
    val displayName: String,
    val directory: String,
)

internal fun validateImageArtifact(
    context: Context,
    artifact: ArtifactMetadata,
): Result<ValidatedImageArtifact> = runCatching {
    val file = File(artifact.path).canonicalFile
    val allowedRoots = buildList {
        add(File(context.filesDir, "artifacts"))
        add(File(context.filesDir, "remote-previews"))
        context.getExternalFilesDirs(null).filterNotNull().forEach(::add)
        add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mason"))
        add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mason"))
        add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Mason"))
        add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Mason"))
    }.mapNotNull { root -> runCatching { root.canonicalFile }.getOrNull() }
    require(
        allowedRoots.any { root -> file.path.startsWith(root.path + File.separator) },
    ) { "图片路径不受信任" }
    require(file.isFile) { "图片不存在或已被移动" }
    require(file.length() > 0L) { "图片大小无效" }
    val header = file.inputStream().use { input ->
        val bytes = ByteArray(64 * 1024)
        val count = input.read(bytes)
        if (count <= 0) ByteArray(0) else bytes.copyOf(count)
    }
    val mimeType = detectPreviewableImageMimeType(header, artifact.mimeType.takeIf { it.isNotBlank() })
        ?: error("文件不是支持的图片格式")
    ValidatedImageArtifact(file = file, mimeType = mimeType)
}

internal suspend fun saveImageArtifactToGallery(
    context: Context,
    artifact: ArtifactMetadata,
): Result<SavedImageArtifact> = withContext(Dispatchers.IO) {
    runCatching {
        val validated = validateImageArtifact(context, artifact).getOrThrow()
        val displayName = normalizedImageDisplayName(artifact.name, validated.mimeType)
        val isSvg = validated.mimeType == "image/svg+xml"
        val directoryName = if (isSvg) Environment.DIRECTORY_DOWNLOADS else Environment.DIRECTORY_PICTURES
        val relativeDirectory = "$directoryName/Mason"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var insertedUri: android.net.Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, validated.mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        relativeDirectory,
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                insertedUri = context.contentResolver.insert(
                    if (isSvg) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    },
                    values,
                ) ?: error("无法创建本地图片")
                context.contentResolver.openOutputStream(insertedUri, "w")?.use { output ->
                    validated.file.inputStream().buffered().use { input -> input.copyTo(output) }
                } ?: error("无法写入本地图片")
                val updatedRows = context.contentResolver.update(
                    insertedUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
                require(updatedRows > 0) { "无法完成本地图片保存" }
            } catch (error: Throwable) {
                insertedUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
                throw error
            }
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(directoryName),
                "Mason",
            )
            require(directory.exists() || directory.mkdirs()) { "无法创建 $relativeDirectory" }
            val target = uniqueImageFile(directory, displayName)
            validated.file.copyTo(target)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf(validated.mimeType),
                null,
            )
        }
        SavedImageArtifact(displayName = displayName, directory = relativeDirectory)
    }
}

private fun clampPreviewOffset(
    offset: Offset,
    scale: Float,
    viewportSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): Offset {
    val clamped = clampArtifactImageOffset(
        offsetX = offset.x,
        offsetY = offset.y,
        scale = scale,
        viewportWidth = viewportSize.width.toFloat(),
        viewportHeight = viewportSize.height.toFloat(),
        contentWidth = imageWidth.toFloat(),
        contentHeight = imageHeight.toFloat(),
    )
    return Offset(clamped.x, clamped.y)
}

private fun normalizedImageDisplayName(name: String, mimeType: String): String {
    val safeBase = name
        .substringBeforeLast('.', name)
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim()
        .take(80)
        .ifBlank { "mason-image-${System.currentTimeMillis()}" }
    val extension = when (mimeType.lowercase(Locale.ROOT)) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/svg+xml" -> "svg"
        "image/bmp" -> "bmp"
        "image/heif" -> "heif"
        "image/heic" -> "heic"
        "image/avif" -> "avif"
        "image/x-icon", "image/vnd.microsoft.icon" -> "ico"
        else -> "png"
    }
    return "$safeBase.$extension"
}

private fun uniqueImageFile(directory: File, displayName: String): File {
    val first = File(directory, displayName)
    if (!first.exists()) return first
    val base = first.nameWithoutExtension
    val extension = first.extension
    var index = 2
    while (true) {
        val candidate = File(directory, "$base-$index.$extension")
        if (!candidate.exists()) return candidate
        index += 1
    }
}

private fun formatArtifactImageSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}
