package com.denggl2.masonremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denggl2.masonremote.transport.displayName
import com.denggl2.masonremote.ui.chat.masonGlassShadow
import com.denggl2.masonremote.ui.theme.MasonAlertDialog
import com.denggl2.masonremote.ui.theme.masonDialogConfirmButtonColor
import com.denggl2.masonremote.ui.theme.masonDialogDismissButtonColors
import com.denggl2.masonremote.ui.localizedText as Text
import com.denggl2.masonremote.ui.LocalRemoteStrings

@Composable
internal fun PairingDisconnectDialog(
    stage: Int,
    state: RemoteConversationListUiState,
    onDismiss: () -> Unit,
    onFirstConfirm: () -> Unit,
    onFinalConfirm: () -> Unit,
    onBackToFirst: () -> Unit,
) {
    if (stage !in 1..2) return

    val isFinalConfirmation = stage == 2
    val strings = LocalRemoteStrings.current
    val connectionName = state.connector?.displayName()?.let(strings::displayText) ?: strings.t("当前连接")
    val body = if (isFinalConfirmation) {
        strings.t("请确认断开连接")
    } else {
        "Connection method: $connectionName. Disconnect?".let { if (strings.isEnglish) it else "当前连接方式为${connectionName}，是否断开？" }
    }

    MasonRemoteActionDialog(
        onDismissRequest = { if (!state.isDisconnecting) onDismiss() },
        title = strings.t("信息确认"),
        body = {
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        },
        dismissLabel = strings.t("取消"),
        confirmLabel = strings.t("确认"),
        confirmColor = MaterialTheme.colorScheme.primary,
        confirmContentColor = MaterialTheme.colorScheme.onPrimary,
        busy = state.isDisconnecting,
        onDismiss = if (isFinalConfirmation) onBackToFirst else onDismiss,
        onConfirm = if (isFinalConfirmation) onFinalConfirm else onFirstConfirm,
    )
}

@Composable
internal fun MasonRemoteActionDialog(
    onDismissRequest: () -> Unit,
    title: String,
    body: @Composable () -> Unit,
    dismissLabel: String,
    confirmLabel: String,
    confirmColor: Color,
    confirmContentColor: Color,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MasonAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(328.dp),
        shape = RoundedCornerShape(30.dp),
        scrimAlpha = 0.32f,
        customContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(198.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(19.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(46.dp))
                Box(
                    modifier = Modifier
                        .width(250.dp)
                        .height(50.dp),
                    contentAlignment = Alignment.Center,
                ) { body() }
                Spacer(Modifier.height(23.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDismiss,
                        enabled = !busy,
                        modifier = Modifier
                            .masonGlassShadow(cornerRadius = 22.dp)
                            .size(width = 130.dp, height = 44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = masonDialogDismissButtonColors(),
                    ) { Text(dismissLabel, fontSize = 14.sp) }
                    Spacer(Modifier.width(20.dp))
                    if (confirmColor == Color.Black && confirmContentColor == Color.White) {
                        MasonBlackConfirmButton(
                            label = confirmLabel,
                            enabled = !busy,
                            busy = busy,
                            onClick = onConfirm,
                            modifier = Modifier.size(width = 130.dp, height = 44.dp),
                            contentColor = confirmContentColor,
                            containerColor = masonDialogConfirmButtonColor(Color.Black),
                        )
                    } else Button(
                        onClick = onConfirm,
                        enabled = !busy,
                        modifier = Modifier
                            .masonGlassShadow(cornerRadius = 22.dp)
                            .size(width = 130.dp, height = 44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = masonDialogConfirmButtonColor(confirmColor),
                            contentColor = confirmContentColor,
                        ),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = confirmContentColor,
                                strokeWidth = 1.5.dp,
                            )
                        } else {
                            Text(confirmLabel, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
internal fun MasonBlackConfirmButton(
    label: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    containerColor: Color = Color.Black,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.masonGlassShadow(cornerRadius = 22.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = contentColor,
                strokeWidth = 1.5.dp,
            )
        } else {
            Text(label, fontSize = 13.sp)
        }
    }
}
