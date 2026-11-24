package com.example.asistan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Llm {
    fun call(c: Context, system: String, hist: List<Pair<String, String>>, user: String, img: String?): String {
        val sp = c.getSharedPreferences("a", 0)
        return if (sp.getString("prov", "claude") == "claude") claude(sp, system, hist, user, img)
        else deepseek(sp, system, hist, user)
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.connectTimeout = 30000
        con.readTimeout = 90000
        con.doOutput = true
        con.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> con.setRequestProperty(k, v) }
        con.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = con.responseCode in 200..299
        val s = (if (ok) con.inputStream else con.errorStream).bufferedReader().readText()
        if (!ok) throw Exception("HTTP ${con.responseCode}: ${s.take(300)}")
        return s
    }

    private fun msg(role: String, content: Any) = JSONObject().put("role", role).put("content", content)

    private fun claude(sp: android.content.SharedPreferences, system: String, hist: List<Pair<String, String>>, user: String, img: String?): String {
        val msgs = JSONArray()
        hist.forEach { (u, a) -> msgs.put(msg("user", u)); msgs.put(msg("assistant", a.ifBlank { "Tamam." })) }
        val parts = JSONArray()
        if (img != null) parts.put(JSONObject().put("type", "image").put("source",
            JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", img)))
        parts.put(JSONObject().put("type", "text").put("text", user))
        msgs.put(msg("user", parts))
        val body = JSONObject().put("model", sp.getString("claude_model", "claude-sonnet-4-5"))
            .put("max_tokens", 1024).put("system", system).put("messages", msgs)
        val r = post("https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to (sp.getString("claude_key", "") ?: ""), "anthropic-version" to "2023-06-01"), body)
        return JSONObject(r).getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun deepseek(sp: android.content.SharedPreferences, system: String, hist: List<Pair<String, String>>, user: String): String {
        val msgs = JSONArray()
        msgs.put(msg("system", system))
        hist.forEach { (u, a) -> msgs.put(msg("user", u)); msgs.put(msg("assistant", a.ifBlank { "Tamam." })) }
        msgs.put(msg("user", user))
        val body = JSONObject().put("model", sp.getString("ds_model", "deepseek-chat"))
            .put("messages", msgs).put("max_tokens", 1024)
            .put("response_format", JSONObject().put("type", "json_object"))
        val r = post("https://api.deepseek.com/chat/completions",
            mapOf("Authorization" to "Bearer " + (sp.getString("ds_key", "") ?: "")), body)
        return JSONObject(r).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}
