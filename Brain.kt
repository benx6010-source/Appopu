package com.axter.opu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Gemini over plain HTTPS. If the saved model is retired, it asks Google for a working one. */
object Brain {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/"

    private class ApiError(msg: String, val code: Int) : Exception(msg) {
        val gone get() = code == 404 || Regex("no longer available|not found|not supported|deprecated", RegexOption.IGNORE_CASE).containsMatchIn(message ?: "")
    }

    private fun request(method: String, url: String, key: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("x-goog-api-key", key)
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code !in 200..299) {
            val msg = try { JSONObject(text).getJSONObject("error").getString("message") } catch (e: Exception) { "Gemini error $code" }
            throw ApiError(msg, code)
        }
        return text
    }

    private fun listModels(key: String): List<String> {
        val j = JSONObject(request("GET", BASE + "models?pageSize=200", key, null))
        val arr = j.optJSONArray("models") ?: return emptyList()
        val out = ArrayList<Pair<String, Int>>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val name = m.getString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            if (!methods.contains("generateContent") || !name.contains("gemini")) continue
            if (Regex("tts|image|embed|live|audio|robotics|computer|learnlm|aqa").containsMatchIn(name)) continue
            val v = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(name)?.groupValues?.get(1)?.toDouble() ?: 0.0
            var s = (v * 100).toInt()
            if (name.contains("flash")) s += 20
            if (name.contains("lite")) s -= 10
            if (name.contains("preview") || name.contains("exp")) s -= 3
            out.add(name to s)
        }
        return out.sortedByDescending { it.second }.map { it.first }
    }

    private fun generate(model: String, key: String, system: String, contents: JSONArray, json: Boolean): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.8).put("maxOutputTokens", 1200).apply {
                if (json) put("responseMimeType", "application/json")
            })
        val j = JSONObject(request("POST", BASE + "models/$model:generateContent", key, body.toString()))
        val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw Exception("Gemini sent no text")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    /** Blocking. Call from a background thread. */
    fun ask(ctx: Context, system: String, contents: JSONArray, json: Boolean = true): String {
        val key = Store.geminiKey(ctx)
        if (key.isBlank()) throw Exception("Add your Gemini key in the Opu app first.")
        try {
            return generate(Store.model(ctx), key, system, contents, json)
        } catch (e: ApiError) {
            if (!e.gone) throw e
            var last: Exception = e
            for (m in listModels(key).take(6)) {
                if (m == Store.model(ctx)) continue
                try {
                    val t = generate(m, key, system, contents, json)
                    Store.put(ctx, "gemini_model", m)
                    return t
                } catch (x: ApiError) {
                    last = x
                    if (!x.gone && x.code != 429) throw x
                }
            }
            throw last
        }
    }

    fun userContent(text: String, jpegBase64: String? = null): JSONObject {
        val parts = JSONArray().put(JSONObject().put("text", text))
        if (jpegBase64 != null) parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", jpegBase64)))
        return JSONObject().put("role", "user").put("parts", parts)
    }

    fun modelContent(text: String) =
        JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", text)))
}
