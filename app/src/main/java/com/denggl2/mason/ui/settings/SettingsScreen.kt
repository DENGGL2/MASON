package com.denggl2.mason.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.denggl2.mason.data.ApiConfig
import com.denggl2.mason.ui.theme.MasonAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    var url by remember(config) { mutableStateOf(config.apiUrl) }
    var key by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var keyVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("API 设置", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
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
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            Text("API 端点 URL", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.deepseek.com/v1/chat/completions") },
                colors = darkFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            Spacer(Modifier.height(20.dp))

            Text("API Key", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
                colors = darkFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "隐藏" else "显示",
                            tint = Color.Gray,
                        )
                    }
                },
            )

            Spacer(Modifier.height(20.dp))

            Text("模型名称", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("deepseek-chat") },
                colors = darkFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.save(ApiConfig(apiUrl = url, apiKey = key, model = model))
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MasonAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("保存", color = Color.Black)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MasonAccent,
    unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = MasonAccent,
    focusedLabelColor = MasonAccent,
    unfocusedLabelColor = Color.Gray,
)
