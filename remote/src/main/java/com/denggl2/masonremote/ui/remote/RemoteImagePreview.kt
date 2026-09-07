package com.denggl2.masonremote.ui.remote

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.zIndex
import com.denggl2.masonremote.ui.chat.ChatBackdropBlur
import com.denggl2.masonremote.ui.chat.ChatGlassMaterial
import com.denggl2.masonremote.ui.chat.ChatSurfaceRole
import com.denggl2.masonremote.ui.chat.LocalChatBackdropState
import com.denggl2.masonremote.ui.chat.captureChatBackdrop
import com.denggl2.masonremote.ui.chat.glassClickable
import com.denggl2.masonremote.ui.chat.masonGlassShadow
import com.denggl2.masonremote.ui.chat.rememberChatBackdropState
import com.denggl2.masonremote.ui.theme.LocalInterfaceEffects
import com.denggl2.masonremote.ui.localizedText as Text
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.RemoteStrings
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val MAX_RASTER_PREVIEW_BYTES = 100L * 1024L * 1024L
private const val MAX_SVG_PREVIEW_BYTES = 8L * 1024L * 1024L
private const val MIN_IMAGE_SCALE = 1f
private const val MAX_IMAGE_SCALE = 5f
private const val PREVIEW_MAX_EDGE = 4096

data class RemotePreviewImage(
    val attachmentId: String,
    val name: String,
    val mimeType: String?,
    val bytes: ByteArray,
)

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun RemoteImagePreviewDialog(
    image: RemotePreviewImage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveInProgress: Boolean = false,
) {
    val strings = LocalRemoteStrings.current
    if (isRemoteSvgImage(image)) {
        RemoteSvgImagePreviewDialog(
            image = image,
            onDismiss = onDismiss,
            onShare = onShare,
            onSave = onSave,
            saveInProgress = saveInProgress,
        )
        return
    }
    val bitmapState by rememberRemoteBitmap(image, PREVIEW_MAX_EDGE)
    val saveAction = rememberRemoteImageSaveAction(image)
    var scale by remember(image.attachmentId) { mutableFloatStateOf(MIN_IMAGE_SCALE) }
    var offset by remember(image.attachmentId) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(image.attachmentId) { mutableStateOf(IntSize.Zero) }
    val effects = LocalInterfaceEffects.current
    val backdropState = rememberChatBackdropState(effects.backdropBlurEnabled)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .remotePreviewMaterial()
            .clipToBounds()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
            .zIndex(100f),
    ) {
        CompositionLocalProvider(LocalChatBackdropState provides backdropState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .captureChatBackdrop(backdropState),
                ) {
                    when (val state = bitmapState) {
                RemoteBitmapState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )

                is RemoteBitmapState.Failed -> Text(
                    text = state.message,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center),
                )

                is RemoteBitmapState.Ready -> {
                    val bitmap = state.bitmap
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = image.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size ->
                                viewportSize = size
                                offset = clampRemotePreviewOffset(
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
                                    val zoomed = applyRemotePreviewZoom(
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
                                val zoomed = applyRemotePreviewZoom(
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
                }

            RemoteImagePreviewHeader(
                onDismiss = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RemoteImagePreviewAction(
                    icon = Icons.Outlined.FileDownload,
                    contentDescription = strings.t(if (saveInProgress || saveAction.saving) "正在保存图片" else "保存图片到本地"),
                    enabled = !(saveInProgress || saveAction.saving) && bitmapState !is RemoteBitmapState.Loading,
                    showProgress = saveInProgress || saveAction.saving,
                    onClick = onSave ?: saveAction.onSave,
                )
                RemoteImagePreviewAction(
                    icon = Icons.Outlined.Share,
                    contentDescription = strings.t("分享图片"),
                    enabled = bitmapState !is RemoteBitmapState.Loading,
                    onClick = onShare,
                )
            }
            }
        }
    }
}

@Composable
fun Modifier.remotePreviewMaterial(): Modifier = composed {
    val effects = LocalInterfaceEffects.current
    background(
        MaterialTheme.colorScheme.background.copy(
            alpha = if (effects.glassMaterialEnabled) 0.92f else 1f,
        ),
    )
}

@Composable
fun RemoteImagePreviewAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    showProgress: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    onClick: () -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val effects = LocalInterfaceEffects.current
    Box(
        modifier = Modifier
            .size(size)
            .masonGlassShadow(cornerRadius = size / 2f)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ChatGlassMaterial(
            shape = CircleShape,
            cornerRadius = size / 2f,
            role = ChatSurfaceRole.Compact,
            blur = ChatBackdropBlur.Soft,
            refraction = true,
            blurredAlpha = effects.compactSurfaceAlpha.coerceAtLeast(0.86f),
            fallbackAlpha = 0.92f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassClickable(enabled = enabled, onClick = onClick),
        )
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                color = MaterialTheme.colorScheme.onBackground,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                icon,
                contentDescription = strings.displayText(contentDescription),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.42f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun RemoteImagePreviewHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalRemoteStrings.current
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val headerTop = topInset + 8.dp
    val headerHeight = 48.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = headerTop)
            .height(headerHeight),
    ) {
        Text(
            text = strings.t("图片预览"),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center),
        )
        RemoteBackButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
        )
    }
}

private data class RemotePreviewTransform(val scale: Float, val offset: Offset)

private fun applyRemotePreviewZoom(
    centroid: Offset,
    zoom: Float,
    pan: Offset,
    scale: Float,
    offset: Offset,
    viewportSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): RemotePreviewTransform {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || !zoom.isFinite() || zoom <= 0f) {
        return RemotePreviewTransform(scale, offset)
    }
    val nextScale = (scale * zoom).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
    val ratio = nextScale / scale
    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val proposedOffset = Offset(
        x = offset.x * ratio + (centroid.x - viewportCenter.x) * (1f - ratio) + pan.x,
        y = offset.y * ratio + (centroid.y - viewportCenter.y) * (1f - ratio) + pan.y,
    )
    return RemotePreviewTransform(
        nextScale,
        clampRemotePreviewOffset(
            proposedOffset,
            nextScale,
            viewportSize,
            imageWidth,
            imageHeight,
        ),
    )
}

private fun clampRemotePreviewOffset(
    offset: Offset,
    scale: Float,
    viewportSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): Offset {
    if (
        scale <= 1f || viewportSize.width <= 0 || viewportSize.height <= 0 ||
        imageWidth <= 0 || imageHeight <= 0
    ) return Offset.Zero
    val fitScale = min(
        viewportSize.width.toFloat() / imageWidth,
        viewportSize.height.toFloat() / imageHeight,
    )
    val scaledWidth = imageWidth * fitScale * scale
    val scaledHeight = imageHeight * fitScale * scale
    val maxOffsetX = max(0f, (scaledWidth - viewportSize.width) / 2f)
    val maxOffsetY = max(0f, (scaledHeight - viewportSize.height) / 2f)
    return Offset(
        offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        offset.y.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

private sealed interface RemoteBitmapState {
    data object Loading : RemoteBitmapState
    data class Ready(val bitmap: Bitmap) : RemoteBitmapState
    data class Failed(val message: String) : RemoteBitmapState
}

@Composable
private fun rememberRemoteBitmap(image: RemotePreviewImage, maxEdge: Int): State<RemoteBitmapState> {
    val state = produceState<RemoteBitmapState>(
        initialValue = RemoteBitmapState.Loading,
        key1 = image.attachmentId,
        key2 = image.bytes.size,
        key3 = maxEdge,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeRemoteBitmap(image.bytes, maxEdge) }
                .fold(
                    onSuccess = RemoteBitmapState::Ready,
                    onFailure = { RemoteBitmapState.Failed(it.message ?: "图片无法预览") },
                )
        }
    }
    val bitmap = (state.value as? RemoteBitmapState.Ready)?.bitmap
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    return state
}

internal fun decodeRemoteBitmap(bytes: ByteArray, maxEdge: Int): Bitmap {
    require(bytes.isNotEmpty()) { "图片内容为空" }
    require(bytes.size.toLong() <= MAX_RASTER_PREVIEW_BYTES) { "图片超过 100 MB，无法直接预览" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            require(info.size.width > 0 && info.size.height > 0) { "图片尺寸无效" }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(calculateRemoteSampleSize(info.size.width, info.size.height, maxEdge))
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片尺寸无效" }
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateRemoteSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("图片无法解码")
}

private fun calculateRemoteSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val largestEdge = max(width, height).toLong()
    var sampleSize = 1
    while (largestEdge > maxEdge.toLong() * sampleSize && sampleSize <= Int.MAX_VALUE / 2) {
        sampleSize *= 2
    }
    return sampleSize
}

private data class RemoteImageSaveAction(val saving: Boolean, val onSave: () -> Unit)

@Composable
private fun rememberRemoteImageSaveAction(image: RemotePreviewImage): RemoteImageSaveAction {
    val context = LocalContext.current
    val strings = LocalRemoteStrings.current
    val scope = rememberCoroutineScope()
    var saving by remember(image.attachmentId) { mutableStateOf(false) }
    val performSave = {
        if (!saving) {
            saving = true
            scope.launch {
                val result = saveRemoteImageToGallery(context, image)
                saving = false
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { saved -> strings.displayText("已保存到 ${saved.directory}") },
                        onFailure = { error -> strings.displayText("保存失败：${error.message ?: "无法写入图片"}") },
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) performSave() else Toast.makeText(context, strings.t("需要存储权限才能保存图片"), Toast.LENGTH_SHORT).show()
    }
    return RemoteImageSaveAction(
        saving = saving,
        onSave = {
            val needsLegacyStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
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

private data class RemoteSavedImage(val directory: String)

private suspend fun saveRemoteImageToGallery(
    context: Context,
    image: RemotePreviewImage,
): Result<RemoteSavedImage> = withContext(Dispatchers.IO) {
    runCatching {
        val mimeType = normalizedRemoteMimeType(image)
        val displayName = normalizedRemoteDisplayName(image.name, mimeType)
        val isSvg = mimeType == "image/svg+xml"
        val directoryName = if (isSvg) Environment.DIRECTORY_DOWNLOADS else Environment.DIRECTORY_PICTURES
        val relativeDirectory = "$directoryName/CloudX"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var insertedUri: android.net.Uri? = null
            try {
                insertedUri = context.contentResolver.insert(
                    if (isSvg) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    },
                ) ?: error("无法创建本地图片")
                context.contentResolver.openOutputStream(insertedUri, "w")?.use { output ->
                    output.write(image.bytes)
                } ?: error("无法写入本地图片")
                require(
                    context.contentResolver.update(
                        insertedUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    ) > 0,
                ) { "无法完成本地图片保存" }
            } catch (error: Throwable) {
                insertedUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
                throw error
            }
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(directoryName), "CloudX")
            require(directory.exists() || directory.mkdirs()) { "无法创建 $relativeDirectory" }
            val target = uniqueRemoteImageFile(directory, displayName)
            target.writeBytes(image.bytes)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)
        }
        RemoteSavedImage(relativeDirectory)
    }
}

internal fun shareRemoteImage(context: Context, image: RemotePreviewImage, strings: RemoteStrings) {
    runCatching {
        val shareDirectory = File(context.cacheDir, "remote-share").apply { mkdirs() }
        val file = File(shareDirectory, normalizedRemoteDisplayName(image.name, normalizedRemoteMimeType(image)))
        file.writeBytes(image.bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = normalizedRemoteMimeType(image)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                strings.t("分享图片"),
            ),
        )
    }.onFailure { error ->
        Toast.makeText(context, strings.displayText("分享失败：${error.message ?: "无法准备图片"}"), Toast.LENGTH_SHORT).show()
    }
}

private fun normalizedRemoteMimeType(image: RemotePreviewImage): String =
    image.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        ?.takeIf { it.startsWith("image/") && it != "image/*" }
        ?: if (image.name.substringAfterLast('.', "").lowercase(Locale.ROOT) == "svg") {
            "image/svg+xml"
        } else {
            "image/png"
        }

private fun normalizedRemoteDisplayName(name: String, mimeType: String): String {
    val safeBase = name.substringBeforeLast('.', name)
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim()
        .take(80)
        .ifBlank { "cloudx-image-${System.currentTimeMillis()}" }
    val extension = when (mimeType) {
        "image/jpeg", "image/jpg" -> "jpg"
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

private fun uniqueRemoteImageFile(directory: File, displayName: String): File {
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

private fun isRemoteSvgImage(image: RemotePreviewImage): Boolean =
    normalizedRemoteMimeType(image) == "image/svg+xml" ||
        image.name.substringAfterLast('.', "").equals("svg", ignoreCase = true)

@Composable
private fun RemoteSvgImagePreviewDialog(
    image: RemotePreviewImage,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveInProgress: Boolean = false,
) {
    val svgState by rememberRemoteSvgContent(image)
    val saveAction = rememberRemoteImageSaveAction(image)
    val effects = LocalInterfaceEffects.current
    val backdropState = rememberChatBackdropState(effects.backdropBlurEnabled)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .remotePreviewMaterial()
            .clipToBounds()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
            .zIndex(100f),
    ) {
        CompositionLocalProvider(LocalChatBackdropState provides backdropState) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .captureChatBackdrop(backdropState),
                ) {
            when (val state = svgState) {
                RemoteSvgState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
                is RemoteSvgState.Failed -> Text(state.message, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                is RemoteSvgState.Ready -> RemoteSvgWebView(
                    svg = state.content,
                    enableZoom = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
                }
            RemoteImagePreviewHeader(
                onDismiss = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RemoteImagePreviewAction(
                    Icons.Outlined.FileDownload,
                    if (saveInProgress || saveAction.saving) "正在保存图片" else "保存图片到本地",
                    !(saveInProgress || saveAction.saving) && svgState !is RemoteSvgState.Loading,
                    saveInProgress || saveAction.saving,
                    onClick = onSave ?: saveAction.onSave,
                )
                RemoteImagePreviewAction(Icons.Outlined.Share, "分享图片", svgState !is RemoteSvgState.Loading, onClick = onShare)
            }
            }
        }
    }
}

private sealed interface RemoteSvgState {
    data object Loading : RemoteSvgState
    data class Ready(val content: String) : RemoteSvgState
    data class Failed(val message: String) : RemoteSvgState
}

@Composable
private fun rememberRemoteSvgContent(image: RemotePreviewImage): State<RemoteSvgState> = produceState<RemoteSvgState>(
    initialValue = RemoteSvgState.Loading,
    key1 = image.attachmentId,
    key2 = image.bytes.size,
) {
    value = withContext(Dispatchers.IO) {
        runCatching {
            require(image.bytes.size.toLong() <= MAX_SVG_PREVIEW_BYTES) { "SVG 图片超过 8 MB" }
            val content = image.bytes.toString(Charsets.UTF_8)
            require(!content.contains("<!DOCTYPE", ignoreCase = true) && !content.contains("<!ENTITY", ignoreCase = true)) {
                "SVG 不允许外部实体"
            }
            content
        }.fold(RemoteSvgState::Ready) { RemoteSvgState.Failed(it.message ?: "SVG 图片无法预览") }
    }
}

@Composable
private fun RemoteSvgWebView(svg: String, enableZoom: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val svgKey = remember(svg) { svg.length to svg.hashCode() }
    val webView = remember(context, enableZoom) {
        RemoteSvgPreviewWebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            isHorizontalScrollBarEnabled = enableZoom
            isVerticalScrollBarEnabled = enableZoom
            overScrollMode = View.OVER_SCROLL_NEVER
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
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val scheme = request?.url?.scheme?.lowercase(Locale.ROOT)
                    return if (scheme == null || scheme == "about" || scheme == "data") {
                        super.shouldInterceptRequest(view, request)
                    } else {
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            }
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading(); webView.destroy() }
    }
    DisposableEffect(webView, svgKey) {
        fun loadContent() {
            if (webView.width <= 0 || webView.height <= 0) return
            val contentKey = Triple(svgKey, webView.width, webView.height)
            if (webView.tag == contentKey) return
            val density = webView.resources.displayMetrics.density.coerceAtLeast(1f)
            webView.loadDataWithBaseURL(
                null,
                buildRemoteSvgDocument(svg, ceil(webView.width / density).toInt().coerceAtLeast(1), ceil(webView.height / density).toInt().coerceAtLeast(1)),
                "text/html",
                "UTF-8",
                null,
            )
            webView.tag = contentKey
        }
        val listener = View.OnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (view.width > 0 && view.height > 0 && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) loadContent()
        }
        webView.addOnLayoutChangeListener(listener)
        if (webView.isLaidOut) loadContent()
        onDispose { webView.removeOnLayoutChangeListener(listener) }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private class RemoteSvgPreviewWebView(context: Context) : WebView(context) {
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

private fun buildRemoteSvgDocument(svg: String, viewportWidth: Int, viewportHeight: Int): String = """
    <!doctype html>
    <html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes">
    <style>
      html, body { width: ${viewportWidth}px; height: ${viewportHeight}px; margin: 0; padding: 0; overflow: hidden; background: #fff; }
      body { display: flex; align-items: center; justify-content: center; }
      body > svg { display: block !important; width: ${viewportWidth}px !important; height: ${viewportHeight}px !important; max-width: ${viewportWidth}px !important; max-height: ${viewportHeight}px !important; }
    </style></head><body>$svg</body></html>
""".trimIndent()
