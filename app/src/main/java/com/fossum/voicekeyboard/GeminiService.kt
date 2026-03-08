package com.fossum.voicekeyboard

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends raw voice-recognition transcriptions to the Gemini API and returns a
 * polished, grammatically complete sentence.
 *
 * Authentication uses the OAuth2 access token stored by [TokenStore] so that
 * API quota is billed to the user's own Google account — no hardcoded API key
 * is required.
 *
 * REST endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent
 * Auth header:   Authorization: Bearer <oauth2_token>
 */
class GeminiService(private val accessToken: String) {

    companion object {
        private const val TAG = "GeminiService"
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * System instruction sent with every request so Gemini knows its role.
         */
        private const val SYSTEM_PROMPT =
            "You are an AI keyboard assistant. The user has spoken and the speech recognizer " +
            "produced the following raw transcript. Your task is to convert that transcript " +
            "into a single, clear, grammatically correct, and complete sentence (or multiple " +
            "sentences if necessary). Preserve the user's intent. Return ONLY the polished " +
            "text with no extra commentary, no quotation marks, and no explanations."
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends [rawTranscript] to Gemini and returns the AI-enhanced text.
     *
     * @throws GeminiException if the API returns an error or the network fails.
     */
    @Throws(GeminiException::class)
    suspend fun enhance(rawTranscript: String): String = withContext(Dispatchers.IO) {
        val requestBody = buildRequestBody(rawTranscript)
        val request = Request.Builder()
            .url(BASE_URL)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        Log.d(TAG, "Sending transcript to Gemini: \"$rawTranscript\"")

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw GeminiException("Network error contacting Gemini API", e)
        }

        response.use { resp ->
            val bodyString = resp.body?.string()
                ?: throw GeminiException("Empty response from Gemini API (HTTP ${resp.code})")

            if (!resp.isSuccessful) {
                Log.e(TAG, "Gemini API error ${resp.code}: $bodyString")
                val errorMsg = parseErrorMessage(bodyString) ?: "HTTP ${resp.code}"
                throw GeminiException("Gemini API returned an error: $errorMsg")
            }

            parseResponse(bodyString)
        }
    }

    /**
     * Builds the JSON request body for the Gemini generateContent endpoint.
     */
    private fun buildRequestBody(transcript: String): String {
        val systemPart = JSONObject().apply {
            put("text", SYSTEM_PROMPT)
        }
        val systemContent = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(systemPart))
        }

        val userPart = JSONObject().apply {
            put("text", transcript)
        }
        val userContent = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(userPart))
        }

        val generationConfig = JSONObject().apply {
            put("temperature", 0.3)
            put("maxOutputTokens", 256)
        }

        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(systemContent)
                put(userContent)
            })
            put("generationConfig", generationConfig)
        }.toString()
    }

    /**
     * Parses the Gemini API response JSON and extracts the generated text.
     */
    private fun parseResponse(json: String): String {
        return try {
            val root = JSONObject(json)
            val candidates = root.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            parts.getJSONObject(0).getString("text").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: $json", e)
            throw GeminiException("Unexpected response format from Gemini API", e)
        }
    }

    /**
     * Attempts to extract a human-readable error message from a failed API response.
     */
    private fun parseErrorMessage(json: String): String? {
        return try {
            JSONObject(json)
                .optJSONObject("error")
                ?.optString("message")
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Thrown when the Gemini API call cannot be completed successfully.
 */
class GeminiException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
