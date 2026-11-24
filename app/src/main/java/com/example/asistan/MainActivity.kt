package com.example.asistan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class AiProvider(val id: String) {
    DEEPSEEK("deepseek"),
    CHATGPT("chatgpt")
}

data class AiConfig(
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val deepseekApiKey: String = "",
    val chatgptApiKey: String = "",
    val deepseekModel: String = "deepseek-chat",
    val chatgptModel: String = "gpt-4o-mini"
)

class Lim(private val config: AiConfig) {

    suspend fun sendMessage(prompt: String, systemPrompt: String = "Sen yardımcı bir asistansın."): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val (endpoint, apiKey, model) = when (config.provider) {
                    AiProvider.DEEPSEEK -> Triple(
                        "https://api.deepseek.com/chat/completions",
                        config.deepseekApiKey.trim(),
                        config.deepseekModel
                    )
                    AiProvider.CHATGPT -> Triple(
                        "https://api.openai.com/v1/chat/completions",
                        config.chatgptApiKey.trim(),
                        config.chatgptModel
                    )
                }

                if (apiKey.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("${config.provider.name} API anahtarı boş.")
                    )
                }

                val payload = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()

                if (code !in 200..299) {
                    return@withContext Result.failure(
                        RuntimeException("HTTP $code: $responseText")
                    )
                }

                val json = JSONObject(responseText)
                val content = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
