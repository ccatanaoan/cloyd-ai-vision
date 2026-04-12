package com.cloyd.cloydaivision

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.util.Base64
import androidx.core.net.toUri
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VisionActionRunner : TaskerPluginRunnerAction<CloydVisionInput, CloydVisionOutput>() {

    override fun run(
        context: Context,
        input: TaskerInput<CloydVisionInput>
    ): TaskerPluginResult<CloydVisionOutput> {

        val data = input.regular
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CloydVision:WakeLock")

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return try {
            wakeLock.acquire(2 * 60 * 1000L)

            val base64Image = getBase64FromUri(context, data.imagePath)

            if (base64Image.isEmpty() && data.imagePath.isNotBlank()) {
                throw Exception("Failed to read image file. Check path or permissions.")
            }

            val userContent = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", data.userPrompt)
                })
                if (base64Image.isNotEmpty()) {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$base64Image")
                        })
                    })
                }
            }

            val jsonPayload = JSONObject().apply {
                put("model", data.model.trim())
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", data.systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContent)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${data.apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://cloyd.ai/vision")
                .addHeader("X-Title", "Cloyd AI Vision")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val rawContent = JSONObject(responseBody)
                        .getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")

                    val sanitized = rawContent
                        .replace(Regex("\\*\\*|__|\\*|_"), "")
                        .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()

                    TaskerPluginResultSucess(CloydVisionOutput(response = sanitized))
                } else {
                    val msg = when (response.code) {
                        401 -> "Invalid API Key."
                        402 -> "Insufficient credits on OpenRouter."
                        429 -> "Rate limit reached. Try again later."
                        else -> {
                            try {
                                JSONObject(responseBody).getJSONObject("error").getString("message")
                            } catch (_: Exception) {
                                "Request failed (HTTP ${response.code})"
                            }
                        }
                    }
                    throw Exception(msg)
                }
            }
        } catch (e: Exception) {
            TaskerPluginResultErrorWithOutput(e)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun getBase64FromUri(context: Context, path: String): String {
        if (path.isBlank()) return ""
        return try {
            val uri = when {
                path.startsWith("content://") || path.startsWith("file://") -> path.toUri()
                else -> Uri.fromFile(java.io.File(path))
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } ?: ""
        } catch (_: Exception) { "" }
    }
}