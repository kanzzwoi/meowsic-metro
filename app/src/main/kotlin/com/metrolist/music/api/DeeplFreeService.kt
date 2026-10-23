package com.metrolist.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Terjemahan via endpoint DeepL mobile gratis (tanpa API key).
 * Fallback ke GoogleFreeService kalau DeepL gagal.
 */
object DeeplFreeService {
    private val ENDPOINTS = listOf(
        "https://oneshot.fal.free.deepl.com/v1/translate",
        "https://oneshot.kef.free.deepl.com/v1/translate",
    )
    private const val USER_AGENT = "DeepL/26.41.0 (com.deepl.mobiletranslator; build:264100; Android 14)"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        text: String,
        targetLanguage: String,
        maxRetries: Int = 2,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success(emptyList())

        val lines = text.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return@withContext Result.success(emptyList())

        // 1) coba DeepL
        for (attempt in 0 until maxRetries) {
            try {
                val payload = json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("text", JsonArray(lines.map { JsonPrimitive(it) }))
                        put("target_lang", JsonPrimitive(normalizeLang(targetLanguage)))
                    },
                )
                for (endpoint in ENDPOINTS) {
                    try {
                        val req = Request.Builder()
                            .url(endpoint)
                            .header("Content-Type", "application/json")
                            .header("User-Agent", USER_AGENT)
                            .post(payload.toRequestBody(JSON_MEDIA))
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            val body = resp.body?.string() ?: throw Exception("empty body")
                            val root = json.parseToJsonElement(body).jsonObject
                            val translations = root["translations"]?.jsonArray ?: throw Exception("no translations")
                            val out = translations.map { it.jsonObject["text"]!!.jsonPrimitive.content }
                            // susun kembali sesuai urutan baris input
                            if (out.size == lines.size) return@withContext Result.success(out)
                            return@withContext Result.success(out)
                        }
                    } catch (e: Exception) {
                        // lanjut ke endpoint berikutnya
                    }
                }
            } catch (e: Exception) {
                // retry
            }
            kotlinx.coroutines.delay(500L * (attempt + 1))
        }

        // 2) Fallback: Google gratis
        return@withContext GoogleFreeService.translate(text, targetLanguage, maxRetries)
    }

    private val aliases = mapOf(
        "zh" to "zh-Hans", "zh-cn" to "zh-Hans", "zh-tw" to "zh-Hant",
        "pt" to "pt-PT", "no" to "nb", "en-us" to "en-US", "en-gb" to "en-GB",
        "pt-br" to "pt-BR", "pt-pt" to "pt-PT", "zh-hans" to "zh-Hans", "zh-hant" to "zh-Hant",
    )

    private fun normalizeLang(lang: String): String {
        val lower = lang.trim().lowercase()
        aliases[lower]?.let { return it }
        return lang.trim()
    }
}
