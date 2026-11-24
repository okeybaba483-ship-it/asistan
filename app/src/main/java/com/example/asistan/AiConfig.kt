package com.example.asistan

data class AiConfig(
    val provider: AiProvider,
    val deepseekApiKey: String,
    val chatgptApiKey: String
)
