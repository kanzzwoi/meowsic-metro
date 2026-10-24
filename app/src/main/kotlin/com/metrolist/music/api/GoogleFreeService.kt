package com.metrolist.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Terjemahan gratis via endpoint Google Translate publik (tanpa API key).
 * Strategi (terbukti kebal rate-limit):
 * 1. client=at (klien Android) dengan UA Android Translate
 * 2. Fallback client=gtx
 * Batch multi-baris digabung dengan \n; baris kosong direpresentasikan '•'.
 */
object GoogleFreeService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private const val ANDROID_UA =
        "AndroidTranslate/5.3.0.RC02.130475354-53000263 5.1 phone TRANSLATE_OPM5_TEST_1"

    private suspend fun fetch(clientId: String, text: String, targetLanguage: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=$clientId&sl=auto&tl=${URLEncoder.encode(targetLanguage, "UTF-8")}&dt=t" +
                    "&dj=1&q=${URLEncoder.encode(text, "UTF-8")}"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", ANDROID_UA)
                    .header("Accept", "*/*")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: return@withContext null
                    val obj = json.parseToJsonElement(body).jsonObject
                    val sentences = obj["sentences"]?.jsonArray ?: return@withContext null
                    val sb = StringBuilder()
                    for (seg in sentences) {
                        sb.append(seg.jsonObject["trans"]?.jsonPrimitive?.content ?: "")
                    }
                    sb.toString()
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Terjemahkan text (bisa multi-baris digabung \n). Mengembalikan hasil mentah
     * yang masih terpisah per \n.
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
        maxRetries: Int = 3,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success(emptyList())
        // Baris kosong -> '•' agar jumlah baris hasil tetap sama
        val prepared = text.split("\n").joinToString("\n") { if (it.isBlank()) "\u2022" else it }
        var attempt = 0
        while (attempt < maxRetries) {
            val translated = fetch("at", prepared, targetLanguage)
                ?: fetch("gtx", prepared, targetLanguage)
            if (translated != null) {
                val lines = translated.split("\n")
                return@withContext Result.success(lines.map { if (it.trim() == "\u2022") "" else it.trim() })
            }
            attempt++
            kotlinx.coroutines.delay(500L * attempt)
        }
        Result.failure(Exception("translate unavailable"))
    }
}
