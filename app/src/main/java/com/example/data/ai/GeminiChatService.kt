package com.example.data.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiChatService {

    // Using gemini-2.5-flash for general & support tasks with high free tier limits
    private val modelName = "gemini-2.5-flash"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun sendChat(
        history: List<ChatMessage>,
        systemInstructionText: String,
        fallbackDataSummary: String,
        apiKeyOverride: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = when {
            !apiKeyOverride.isNullOrBlank() -> apiKeyOverride.trim()
            else -> try {
                BuildConfig.GEMINI_API_KEY
            } catch (_: Exception) {
                ""
            }
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "**Live Gemini AI requires an API Key**\n\n" +
                    "Folio connects directly to Google's `gemini-2.5-flash` model. However, an API key is required to make live calls.\n\n" +
                    "Tap the API Key action in the top right of this chat or the banner below to configure your Gemini API Key.\n\n" +
                    "**Your Current Accounts Snapshot:**\n$fallbackDataSummary"
        }

        try {
            val root = JSONObject()

            // System Instruction
            val systemPart = JSONObject().put("text", systemInstructionText)
            val systemContent = JSONObject().put("parts", JSONArray().put(systemPart))
            root.put("systemInstruction", systemContent)

            // Contents (multi-turn conversation)
            val contentsArray = JSONArray()
            // Keep recent turns (up to 12 turns) to keep context compact and fast
            val recentTurns = history.takeLast(12)
            for (msg in recentTurns) {
                val part = JSONObject().put("text", msg.text)
                val contentObj = JSONObject()
                    .put("role", if (msg.isUser) "user" else "model")
                    .put("parts", JSONArray().put(part))
                contentsArray.put(contentObj)
            }
            root.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject()
                .put("temperature", 0.4)
                .put("topP", 0.95)
            root.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = root.toString().toRequestBody(mediaType)

            val url = "$baseUrl?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}"
                }
                return@withContext "**Gemini Service Notice** ($errorMsg)\n\n" +
                        "Here is what your local data shows:\n\n$fallbackDataSummary"
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val replyText = parts?.optJSONObject(0)?.optString("text")

            if (!replyText.isNullOrBlank()) {
                replyText.trim()
            } else {
                "I was unable to process that query. Here is a summary of your accounts:\n\n$fallbackDataSummary"
            }
        } catch (e: Exception) {
            "**Connection Notice**: Could not reach Gemini servers (${e.localizedMessage ?: "Network error"}).\n\n" +
                    "Your financial data summary:\n\n$fallbackDataSummary"
        }
    }
}
