package com.denggl2.mason.data

data class ApiConfig(
    val apiUrl: String = "https://api.deepseek.com/v1/chat/completions",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
)
