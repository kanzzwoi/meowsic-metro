/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Terjemahan lirik gratis via KNZAPI (Gemini web di baliknya), tanpa API key.
 * Terbuka hanya untuk pengguna level 50 ke atas (lihat [KNZAPI_MIN_LEVEL]).
 *
 * Endpoint-nya GET sederhana: `chat` = pesan (lirik + instruksi mode),
 * `prompt` = system prompt. Balasan JSON: `{ "success": true,
 * "result": { "response": "...", "session": "..." } }`. Field `session`
 * diabaikan — tidak ada cara yang didokumentasikan untuk mengirimnya
 * kembali, dan tiap panggilan terjemahan membawa lirik lengkapnya sendiri.
 */
object KnzApiService {
    /** Level minimum untuk memakai KNZAPI. */
    const val KNZAPI_MIN_LEVEL = 50

    private const val BASE_URL = "https://www.knzapi.zone.id/api/ai/gemini"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun defaultSystemPrompt(lineCount: Int): String =
        """You are a precise lyrics translation assistant. Your output must ALWAYS be a valid JSON object of the form {"lines": ["line1", "line2", "line3"]}.

CRITICAL RULES:
1. Output ONLY the JSON object: {"lines": ["line1", "line2", "line3"]}
2. NO explanations, NO questions, NO additional text
3. Each input line maps to exactly one entry in the "lines" array
4. Preserve empty lines as empty strings ""
5. The "lines" array must contain EXACTLY $lineCount items
6. If uncertain, provide best approximation but maintain line count"""

    private fun userPrompt(text: String, targetLanguage: String, mode: String): String {
        val lineCount = text.lines().size
        return when (mode) {
            "Romanized" -> {
                """Romanize/transliterate the following $lineCount lines into simple Latin script using ONLY basic English letters (a-z, A-Z).

CRITICAL REQUIREMENTS:
- Use ONLY simple ASCII characters (a-z, A-Z, 0-9, basic punctuation)
- NO special characters like ā, ī, ū, ñ, ç, etc.
- NO diacritics or accent marks
- If text is already in Latin script, return it UNCHANGED
- For non-Latin scripts (Hindi, Chinese, Japanese, Korean, Cyrillic, etc.), provide simple romanization
- DO NOT translate meaning, only convert script to simple English letters
- Keep all punctuation and formatting
- Preserve line-by-line structure exactly

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings using ONLY simple ASCII characters."""
            }

            "Transcribed" -> {
                """Transcribe/transliterate the following $lineCount lines phonetically into $targetLanguage script.

CRITICAL REQUIREMENTS:
- Convert the SOUND/PRONUNCIATION of the original text into $targetLanguage script
- DO NOT translate the meaning - only represent how the original words SOUND
- Use the native script of $targetLanguage (e.g., Devanagari for Hindi, Hangul for Korean, etc.)
- Preserve the original pronunciation as closely as possible in the target script
- Keep punctuation and formatting
- Preserve line-by-line structure exactly
- If text is already in $targetLanguage script, return it UNCHANGED

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings in $targetLanguage script."""
            }

            else -> {
                """Translate the following $lineCount lines to $targetLanguage.

IMPORTANT:
- Provide natural, accurate translation
- Maintain poetic flow and meaning
- Keep punctuation appropriate for target language
- Preserve line-by-line structure exactly
- For song lyrics, prioritize singability

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings."""
            }
        }
    }

    private fun parseResponse(response: String, expectedLineCount: Int): List<String> {
        val cleaned = response.replace("```json", "").replace("```", "").trim()
        // Coba JSON {"lines": [...]} dulu (sesuai system prompt).
        val jsonLines =
            runCatching {
                val obj = json.parseToJsonElement(cleaned).jsonObject
                obj["lines"]?.jsonArray?.map {
                    it.jsonPrimitive.content.trim()
                }
            }.getOrNull()
        val lines =
            if (!jsonLines.isNullOrEmpty()) {
                jsonLines
            } else {
                // Fallback: teks polos, satu terjemahan per baris.
                cleaned
                    .lines()
                    .map { it.trim() }
                    .map { line ->
                        line
                            .replace(Regex("^\\d+\\.\\s*"), "")
                            .replace(Regex("^Line\\s+\\d+:\\s*", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("^-\\s*"), "")
                    }
            }
        // Samakan jumlah baris dengan input (posisi per baris dipertahankan;
        // baris kosong "" dilewati saat render, seperti provider lain).
        return lines.take(expectedLineCount) +
            List((expectedLineCount - lines.size).coerceAtLeast(0)) { "" }
    }

    private suspend fun fetch(text: String, targetLanguage: String, mode: String, customSystemPrompt: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val lineCount = text.lines().size
                val systemPrompt =
                    customSystemPrompt.takeIf(String::isNotBlank)?.replace("{lineCount}", lineCount.toString())
                        ?: defaultSystemPrompt(lineCount)
                val url = BASE_URL +
                    "?chat=${URLEncoder.encode(userPrompt(text, targetLanguage, mode), "UTF-8")}" +
                    "&prompt=${URLEncoder.encode(systemPrompt, "UTF-8")}"
                val req =
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("KNZAPI request failed (${resp.code})"))
                    }
                    val obj = json.parseToJsonElement(body).jsonObject
                    if (obj["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() != true) {
                        val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: "KNZAPI request failed"
                        return@withContext Result.failure(Exception(message))
                    }
                    val responseText =
                        obj["result"]?.jsonObject?.get("response")?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (responseText.isBlank()) {
                        return@withContext Result.failure(Exception("No translation received"))
                    }
                    Result.success(parseResponse(responseText, lineCount))
                }
            } catch (e: Exception) {
                Timber.w(e, "KNZAPI translate failed")
                Result.failure(e)
            }
        }

    /**
     * Terjemahkan text (bisa multi-baris digabung \n). Mengembalikan satu
     * terjemahan per baris input, posisinya dipertahankan.
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
        mode: String,
        customSystemPrompt: String = "",
        maxRetries: Int = 3,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))
            var attempt = 0
            var lastError: Exception? = null
            while (attempt < maxRetries) {
                val result = fetch(text, targetLanguage, mode, customSystemPrompt)
                if (result.isSuccess) return@withContext result
                lastError = result.exceptionOrNull() as? Exception
                attempt++
                if (attempt < maxRetries) delay(500L * attempt)
            }
            Result.failure(lastError ?: Exception("KNZAPI request failed"))
        }
}
