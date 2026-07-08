package com.denggl2.mason.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.llm.model.ChatMessage
import com.denggl2.mason.ui.theme.MasonAccent
import com.denggl2.mason.ui.theme.MasonAssistantBubble
import com.denggl2.mason.ui.theme.MasonUserBubble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
private const val MAX_COLLAPSED_LENGTH = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val visibleMessages = uiState.messages.filterNot { message ->
        message.role == "assistant" &&
            message.content.isNullOrBlank() &&
            !message.tool_calls.isNullOrEmpty()
    }

    val hasMessages = visibleMessages.isNotEmpty() ||
            uiState.streamingContent.isNotEmpty() ||
            uiState.toolCallStatus != null

    LaunchedEffect(uiState.messages.size, uiState.streamingContent, uiState.toolCallStatus) {
        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (onBack != null) {
                        var isEditingTitle by remember { mutableStateOf(false) }
                        var editedTitle by remember(uiState.conversationTitle) {
                            mutableStateOf(uiState.conversationTitle)
                        }

                        if (isEditingTitle) {
                            OutlinedTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MasonAccent,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = MasonAccent,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                            LaunchedEffect(Unit) {
                                // Wait for composition then handle commit on next recomposition
                            }
                        } else {
                            Text(
                                uiState.conversationTitle,
                                color = Color.White,
                                modifier = Modifier.padding(0.dp),
                            )
                        }
                    } else {
                        Text("Mason", color = Color.White)
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!hasMessages) {
                EmptyChatState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onPromptSelected = { inputText = it },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(visibleMessages) { message ->
                        MessageBubble(message)
                    }

                    // Tool call status card
                    uiState.toolCallStatus?.let { status ->
                        item {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(300)),
                            ) {
                                ToolCallStatusCard(status)
                            }
                        }
                    }

                    if (uiState.isStreaming && uiState.streamingContent.isNotEmpty()) {
                        item {
                            MessageBubble(
                                ChatMessage(role = "assistant", content = uiState.streamingContent),
                                isStreaming = true,
                            )
                        }
                    }
                }
            }

            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                    focusManager.clearFocus()
                },
                enabled = !uiState.isStreaming,
            )
        }
    }
}

@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
    onPromptSelected: (String) -> Unit,
) {
    val prompts = listOf(
        "我的手机配置怎么样？",
        "看看当前电量和网络",
        "帮我检查存储空间",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MasonAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "M",
                    color = MasonAccent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Mason",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "想先问什么？",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                prompts.forEach { prompt ->
                    PromptChip(
                        text = prompt,
                        onClick = { onPromptSelected(prompt) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptChip(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun TimestampLabel(timestamp: Long?) {
    if (timestamp == null) return
    val timeText = remember(timestamp) {
        TIME_FORMAT.format(Date(timestamp))
    }
    Text(
        text = timeText,
        color = Color.Gray.copy(alpha = 0.6f),
        fontSize = 10.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ToolCallStatusCard(toolName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "tool_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation_angle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MasonAccent.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                tint = MasonAccent,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(
                    color = MasonAssistantBubble,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = toolName,
                color = MasonAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ExpandableMessageContent(
    content: String,
    isTool: Boolean,
    isStreaming: Boolean,
) {
    val isLong = content.length > MAX_COLLAPSED_LENGTH
    var expanded by remember(content) { mutableStateOf(false) }
    val displayText = if (isLong && !expanded) content.take(MAX_COLLAPSED_LENGTH) + "…" else content

    Column {
        Text(
            text = displayText + if (isStreaming) "▊" else "",
            color = if (isTool) Color(0xFFB0BEC5) else Color.White,
            fontSize = if (isTool) 12.sp else 15.sp,
            lineHeight = if (isTool) 18.sp else 22.sp,
        )

        if (isLong) {
            Text(
                text = if (expanded) "收起" else "展开全文",
                color = MasonAccent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isStreaming: Boolean = false) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isTool) Color(0xFF37474F) else MasonAccent
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isTool) "T" else "M",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .background(
                        color = when {
                            isUser -> MasonUserBubble
                            isTool -> Color(0xFF263238)
                            else -> MasonAssistantBubble
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .let { if (isUser) it else Modifier.fillMaxWidth(0.85f).then(it) },
            ) {
                Column {
                    if (isTool && message.name != null) {
                        Text(
                            "工具结果: ${message.name}",
                            color = Color(0xFF80CBC4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                    } else if (isTool) {
                        Text(
                            "工具结果:",
                            color = Color(0xFF80CBC4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    val content = message.content ?: ""
                    if (isTool) {
                        Text(
                            text = content,
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    } else {
                        ExpandableMessageContent(
                            content = content,
                            isTool = false,
                            isStreaming = isStreaming,
                        )
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("U", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Timestamp below bubble, aligned with the bubble
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .then(
                    if (isUser) Modifier.padding(end = 40.dp)
                    else Modifier.padding(start = 40.dp)
                ),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            TimestampLabel(message.timestamp)
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151515))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入消息...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MasonAccent,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MasonAccent,
            ),
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
        )

        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && text.isNotBlank()) MasonAccent else Color(0xFF252525)
                ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (text.isNotBlank() && enabled) Color.Black else Color.Gray,
            )
        }
    }
}
