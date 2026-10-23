
package com.metrolist.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Terjemahan gratis via endpoint Google Translate publik (tanpa API key).
 * Hanya mendukung mode Literal (terjemahan baris per baris).
 */
object GoogleFreeService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun translate(
        text: String,
        targetLanguage: String,
        maxRetries: Int = 3,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success(emptyList())
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=${URLEncoder.encode(targetLanguage, "UTF-8")}&dt=t" +
                    "&q=${URLEncoder.encode(text, "UTF-8")}"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    val body = resp.body?.string() ?: throw Exception("empty body")
                    val arr = json.parseToJsonElement(body).jsonArray
                    val sb = StringBuilder()
                    for (seg in arr[0].jsonArray) {
                        sb.append(seg.jsonArray[0].jsonPrimitive.content)
                    }
                    return@withContext Result.success(listOf(sb.toString()))
                }
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxRetries) {
                    return@withContext Result.failure(e)
                }
                kotlinx.coroutines.delay(1000L * attempt)
            }
        }
        Result.failure(Exception("unreachable"))
    }
}
