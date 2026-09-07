package com.denggl2.masonremote.ui.pairing

import android.Manifest
import android.text.format.DateFormat as AndroidDateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denggl2.masonremote.data.PairingStore
import com.denggl2.masonremote.transport.PairingOffer
import com.denggl2.masonremote.transport.PairingResult
import com.denggl2.masonremote.transport.PairedConnector
import com.denggl2.masonremote.transport.RealPairingTransport
import com.denggl2.masonremote.transport.RemoteAgentKind
import com.denggl2.masonremote.transport.TransportMode
import com.denggl2.masonremote.transport.displayName
import com.denggl2.masonremote.transport.parsePairingOffer
import com.denggl2.masonremote.ui.FigmaSvgAsset
import com.denggl2.masonremote.ui.chat.masonGlassShadow
import com.denggl2.masonremote.ui.theme.MASON_SHEET_SCRIM_ALPHA
import com.denggl2.masonremote.ui.theme.MasonSheetShape
import com.denggl2.masonremote.ui.theme.masonOverlayWindowInsets
import com.denggl2.masonremote.ui.theme.masonSheetContainerColor
import com.denggl2.masonremote.ui.theme.masonSheetSurface
import com.denggl2.masonremote.ui.localizedText as Text
import com.denggl2.masonremote.ui.LocalRemoteStrings
import com.denggl2.masonremote.ui.WithRemoteMaterialResources
import com.denggl2.masonremote.ui.localizedPairingError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PairingStep { SCAN, CONFIRM, CONNECTING, DONE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairingSheet(
    onDismiss: () -> Unit,
    debugRawPayload: String? = null,
    requestedTransportMode: TransportMode? = null,
    onPaired: (PairedConnector) -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalRemoteStrings.current
    val scope = rememberCoroutineScope()
    val pairingStore = remember(context.applicationContext) { PairingStore(context.applicationContext) }
    val transport = remember(pairingStore, context.applicationContext) {
        RealPairingTransport(pairingStore, context.applicationContext)
    }
    var step by remember { mutableStateOf(PairingStep.SCAN) }
    var offer by remember { mutableStateOf<PairingOffer?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var connectedDeviceName by remember { mutableStateOf("") }
    var pairedConnector by remember { mutableStateOf<PairedConnector?>(null) }
    var permissionGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    var dismissing by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = (LocalConfiguration.current.screenHeightDp.dp - 400.dp)
        .coerceAtLeast(400.dp)

    fun isExpectedMode(candidate: PairingOffer): Boolean =
        requestedTransportMode == null || candidate.bootstrap.transportMode == requestedTransportMode

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionGranted = it }

    LaunchedEffect(debugRawPayload, requestedTransportMode) {
        val debugOffer = debugRawPayload?.let(::parsePairingOffer)
        if (debugRawPayload != null) {
            if (debugOffer != null && isExpectedMode(debugOffer)) {
                offer = debugOffer
                step = PairingStep.CONFIRM
            } else if (debugOffer != null) {
                errorMessage = strings.localizedPairingError(
                    "请扫描 ${requestedTransportMode?.displayName() ?: "对应方式"}二维码",
                )
                step = PairingStep.ERROR
            } else {
                errorMessage = strings.t("测试二维码内容无效")
                step = PairingStep.ERROR
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(step, offer?.bootstrap?.expiresAt) {
        if (step != PairingStep.CONFIRM) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    fun dismissSheet(afterDismiss: () -> Unit = onDismiss) {
        if (dismissing) return
        dismissing = true
        scope.launch {
            sheetState.hide()
            afterDismiss()
        }
    }

    WithRemoteMaterialResources {
    ModalBottomSheet(
        onDismissRequest = { dismissSheet() },
        sheetState = sheetState,
        containerColor = masonSheetContainerColor(),
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = MASON_SHEET_SCRIM_ALPHA),
        tonalElevation = 0.dp,
        shape = MasonSheetShape,
        dragHandle = null,
        contentWindowInsets = { masonOverlayWindowInsets() },
    ) {
        PairingSheetContent(
            sheetHeight = sheetHeight,
            step = step,
            offer = offer,
            connectedDeviceName = connectedDeviceName,
            errorMessage = errorMessage,
            permissionGranted = permissionGranted,
            nowMillis = nowMillis,
            onBackToScan = { step = PairingStep.SCAN },
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOfferFound = { scannedOffer ->
                if (scannedOffer.bootstrap.expiresAt <= System.currentTimeMillis()) {
                    errorMessage = strings.localizedPairingError("二维码已过期，请让电脑端重新生成")
                    step = PairingStep.ERROR
                } else if (isExpectedMode(scannedOffer)) {
                    offer = scannedOffer
                    step = PairingStep.CONFIRM
                } else {
                    errorMessage = strings.localizedPairingError(
                        "请扫描 ${requestedTransportMode?.displayName() ?: "对应方式"}二维码",
                    )
                    step = PairingStep.ERROR
                }
            },
            onConfirm = {
                val currentOffer = offer
                if (currentOffer == null) {
                    errorMessage = strings.t("二维码信息无效")
                    step = PairingStep.ERROR
                } else if (currentOffer.bootstrap.expiresAt <= System.currentTimeMillis()) {
                    errorMessage = strings.localizedPairingError("二维码已过期，请让电脑端重新生成")
                    step = PairingStep.ERROR
                } else {
                    step = PairingStep.CONNECTING
                    scope.launch {
                        when (val result = transport.pair(currentOffer)) {
                            is PairingResult.Connected -> {
                                connectedDeviceName = result.deviceName
                                pairedConnector = result.connector
                                step = PairingStep.DONE
                            }
                            is PairingResult.Failed -> {
                                errorMessage = strings.localizedPairingError(result.message)
                                step = PairingStep.ERROR
                            }
                        }
                    }
                }
            },
            onEnterConversation = {
                pairedConnector?.let { connector ->
                    dismissSheet { onPaired(connector) }
                }
            },
            onRetry = { step = PairingStep.SCAN },
        )
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingSheetContent(
    sheetHeight: androidx.compose.ui.unit.Dp,
    step: PairingStep,
    offer: PairingOffer?,
    connectedDeviceName: String,
    errorMessage: String,
    permissionGranted: Boolean,
    nowMillis: Long,
    onBackToScan: () -> Unit,
    onRequestPermission: () -> Unit,
    onOfferFound: (PairingOffer) -> Unit,
    onConfirm: () -> Unit,
    onEnterConversation: () -> Unit,
    onRetry: () -> Unit,
) {
    val strings = LocalRemoteStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .masonSheetSurface(
                RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                includeNavigationBarPadding = false,
                drawEdge = false,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 20.dp)
                .size(width = 30.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PairingSheetColors.Handle),
        )
        Text(
            text = when (step) {
                PairingStep.SCAN -> strings.t("扫码配对")
                PairingStep.CONFIRM -> strings.t("确认配对")
                PairingStep.CONNECTING -> strings.t("正在配对")
                PairingStep.DONE -> strings.t("已完成配对")
                PairingStep.ERROR -> strings.t("配对失败")
            },
            color = PairingSheetColors.Text,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 52.dp),
        )

        when (step) {
            PairingStep.SCAN -> {
                Text(
                    text = strings.t("扫描电脑端项目生成的二维码"),
                    color = PairingSheetColors.Secondary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 83.dp),
                )
                if (permissionGranted) {
                    QrScanner(
                        frameSize = 238.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 116.dp),
                        onOfferFound = onOfferFound,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = 116.dp)
                            .size(238.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PairingSheetColors.Scanner),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = PairingSheetColors.ScannerContent,
                            )
                            PairingPrimaryButton(
                                onClick = onRequestPermission,
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(44.dp),
                                label = strings.t("允许相机权限"),
                            )
                        }
                    }
                }
            }
            PairingStep.CONFIRM -> {
                Text(
                    text = offer?.deviceName ?: strings.t("电脑"),
                    color = PairingSheetColors.Text,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 94.dp),
                )
                Text(
                    text = buildString {
                        append(strings.t("服务器")); append("\n")
                        append(offer?.serverUrl ?: "")
                        append("\n"); append(strings.t("设备指纹")); append("\n")
                        append(offer?.fingerprint ?: "")
                        append("\n"); append(strings.t("二维码有效期")); append("\n")
                        append(formatPairingExpiry(offer?.bootstrap?.expiresAt, nowMillis, strings.isEnglish))
                    },
                    color = PairingSheetColors.Secondary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 128.dp)
                        .width(210.dp),
                )
                PairingButtons(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 288.dp),
                    onCancel = onBackToScan,
                    onConfirm = onConfirm,
                    confirmLabel = strings.t("确认并配对"),
                )
            }
            PairingStep.CONNECTING -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).offset(y = 35.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = PairingSheetColors.Text)
                    Text(strings.t("正在建立安全连接…"), color = PairingSheetColors.Secondary, fontSize = 14.sp)
                }
            }
            PairingStep.DONE -> {
                FigmaSvgAsset(
                    assetPath = "figma/pairing_done.svg",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 158.dp)
                        .size(30.dp),
                    darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                )
                Text(
                    text = strings.t("已连接到电脑端"),
                    color = PairingSheetColors.Text,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 197.dp),
                )
                Text(
                    text = connectedDeviceName,
                    color = PairingSheetColors.Secondary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 226.dp),
                )
                PairingPrimaryButton(
                    onClick = onEnterConversation,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 310.dp)
                        .width(232.dp)
                        .height(44.dp),
                    label = strings.t("进入对话"),
                )
            }
            PairingStep.ERROR -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 5.dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = PairingSheetColors.Text)
                    Text(errorMessage, color = PairingSheetColors.Secondary, textAlign = TextAlign.Center, fontSize = 14.sp)
                    PairingPrimaryButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .width(160.dp)
                            .height(44.dp),
                        label = strings.t("重新扫码"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingButtons(
    modifier: Modifier,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PairingPrimaryButton(
            onClick = onCancel,
            modifier = Modifier
                .size(width = 110.dp, height = 44.dp),
            label = LocalRemoteStrings.current.t("返回"),
        )
        PairingPrimaryButton(
            onClick = onConfirm,
            modifier = Modifier
                .size(width = 110.dp, height = 44.dp),
            label = confirmLabel,
        )
    }
}

@Composable
private fun PairingPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.masonGlassShadow(cornerRadius = 22.dp),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatPairingExpiry(expiresAt: Long?, nowMillis: Long, english: Boolean = false): String {
    if (expiresAt == null) return if (english) "Unknown" else "未知"
    val remainingSeconds = ((expiresAt - nowMillis) / 1_000L).coerceAtLeast(0L)
    if (remainingSeconds == 0L) return if (english) "Expired; generate a new code" else "已过期，请重新生成"
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val clock = AndroidDateFormat.format("HH:mm:ss", expiresAt)
    return if (english) {
        "Valid until $clock (${minutes}m ${seconds.toString().padStart(2, '0')}s remaining)"
    } else {
        "有效至 $clock（剩余 ${minutes}分${seconds.toString().padStart(2, '0')}秒）"
    }
}

private object PairingSheetColors {
    val Handle: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val Scanner: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val ScannerContent: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Secondary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Text: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface
}
