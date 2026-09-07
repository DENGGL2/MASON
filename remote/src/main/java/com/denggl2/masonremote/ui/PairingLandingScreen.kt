package com.denggl2.masonremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denggl2.masonremote.transport.TransportMode
import com.denggl2.masonremote.ui.remote.RemoteBackButton
import com.denggl2.masonremote.ui.localizedText as Text

@Composable
fun PairingLandingScreen(
    selectedMode: TransportMode?,
    onModeSelected: (TransportMode) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalRemoteStrings.current
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val pairingBackground = MaterialTheme.colorScheme.background
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pairingBackground),
    ) {
        RemoteBackButton(
            onClick = onBack,
            contentDescription = strings.t("返回"),
            modifier = Modifier.padding(start = 8.dp, top = topInset + 8.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(800.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-50).dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                ) {
                    FigmaSvgAsset(
                        assetPath = "figma/codex_logo.svg",
                        modifier = Modifier
                            .size(30.dp)
                            .align(Alignment.Center),
                        darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                    )
                }
                Spacer(Modifier.height(17.dp))
                Text(
                    text = strings.t("电脑端启动后扫码配对"),
                    color = secondaryTextColor,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 68.dp)
                    .width(232.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = strings.t("请选择连接方式"),
                        color = secondaryTextColor,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    )
                }
                PairingModeOption(
                    title = strings.t("Cloudflare 隧道"),
                    description = strings.text("中转，电脑端重启需要重新配对", "Relay; re-pair after restart"),
                    titleWidth = if (strings.isEnglish) 128.dp else 108.dp,
                    rowHeight = 33.dp,
                    titleSize = 14.sp,
                    titleLineHeight = 17.sp,
                    descriptionLineHeight = 14.sp,
                    selected = selectedMode == TransportMode.CLOUDFLARE_TUNNEL,
                    onClick = { onModeSelected(TransportMode.CLOUDFLARE_TUNNEL) },
                )
                PairingModeOption(
                    title = strings.t("WebRTC 直连"),
                    description = strings.text("手机直连，信令服务器配对", "Direct; pair via signaling server"),
                    titleWidth = if (strings.isEnglish) 108.dp else 96.dp,
                    rowHeight = 34.dp,
                    titleSize = 15.sp,
                    titleLineHeight = 18.sp,
                    descriptionLineHeight = 14.sp,
                    selected = selectedMode == TransportMode.WEBRTC_DIRECT,
                    onClick = { onModeSelected(TransportMode.WEBRTC_DIRECT) },
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .offset(y = 44.dp),
                    enabled = selectedMode != null,
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(strings.t("开始"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun PairingModeOption(
    title: String,
    description: String,
    titleWidth: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    titleSize: androidx.compose.ui.unit.TextUnit,
    titleLineHeight: androidx.compose.ui.unit.TextUnit,
    descriptionLineHeight: androidx.compose.ui.unit.TextUnit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = MaterialTheme.colorScheme.onSurface
    val descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .width(titleWidth),
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
            Text(
                text = description,
                color = descriptionColor,
                fontSize = 12.sp,
                lineHeight = descriptionLineHeight,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                )
            }
        }
    }
}
