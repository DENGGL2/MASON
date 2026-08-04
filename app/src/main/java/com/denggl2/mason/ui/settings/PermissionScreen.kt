package com.denggl2.mason.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val items by viewModel.permissions.collectAsState()
    val context = LocalContext.current
    var advancedPermission by remember { mutableStateOf<PermissionItem?>(null) }
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "权限管理",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val grouped = items.groupBy { it.group }

            PermissionGroup.entries.forEach { group ->
                val groupItems = grouped[group] ?: return@forEach
                Spacer(Modifier.height(20.dp))
                SectionHeader(group.label)

                groupItems.forEach { item ->
                    PermissionRow(
                        label = item.label,
                        isGranted = item.isGranted,
                        onClick = {
                            when (item.requestKind) {
                                PermissionRequestKind.Runtime -> runtimePermissionLauncher.launch(item.permission)
                                PermissionRequestKind.AdvancedSettings -> advancedPermission = item
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    advancedPermission?.let { item ->
        AlertDialog(
            onDismissRequest = { advancedPermission = null },
            title = { Text(item.label) },
            text = { Text(item.guidance ?: "请在系统设置中为 Mason 开启这项高级权限。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        advancedPermission = null
                        item.settingsIntent?.let(context::startActivity)
                    },
                ) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { advancedPermission = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun PermissionRow(
    label: String,
    isGranted: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFEF5350)
    val statusText = if (isGranted) "已授权" else "未授权"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGranted) Modifier else Modifier.clickable(onClick = onClick)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = statusText,
                color = tint,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                ),
            )
            if (!isGranted) {
                Text(
                    text = "设置>",
                    color = Color.Gray.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                )
            }
        }
    }

}
