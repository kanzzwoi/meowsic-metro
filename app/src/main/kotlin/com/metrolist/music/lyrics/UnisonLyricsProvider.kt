/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.betterlyrics.TTMLParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Unison — database lirik komunitas milik Better Lyrics.
 *
 * Tidak butuh API key (beda dari /getLyrics Better Lyrics yang menolak cache miss),
 * dan bisa dicari lewat videoId YouTube yang sudah dipegang app. Kalau video itu
 * belum ada di Unison, dicoba lewat judul + artis.
 */
object UnisonLyricsProvider : LyricsProvider {
    override val name = "Unison"

    private const val BASE_URL = "https://unison.boidu.dev"
    private const val TAG = "Unison"

    override fun isEnabled(context: Context): Boolean = true

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 12_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 12_000
            }
            expectSuccess = false
        }
    }

    @Serializable
    private data class UnisonData(
        val lyrics: String? = null,
        val format: String? = null,
        val syncType: String? = null,
        val confidence: String? = null,
    )

    @Serializable
    private data class UnisonResponse(
        val success: Boolean = false,
        val data: UnisonData? = null,
    )

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        // 1) Cari per videoId YouTube (paling akurat, hasil submit komunitas).
        if (id.isNotBlank()) {
            fetch { parameter("v", id) }?.let { return@runCatching it }
        }
        // 2) Fallback ke judul + artis (+ album/durasi) bila video itu belum ada.
        fetch {
            parameter("song", title)
            parameter("artist", artist)
            if (!album.isNullOrBlank()) parameter("album", album)
            if (duration > 0) parameter("duration", duration)
        } ?: throw IllegalStateException("Unison: lyrics not found")
    }

    private suspend fun fetch(block: HttpRequestBuilder.() -> Unit): String? = runCatching {
        val response = client.get("$BASE_URL/lyrics", block)
        if (response.status != HttpStatusCode.OK) {
            Timber.tag(TAG).d("no lyrics (status ${response.status})")
            return@runCatching null
        }
        val body = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }.decodeFromString(UnisonResponse.serializer(), response.bodyAsText())

        val data = body.data
        val raw = data?.lyrics?.takeIf { it.isNotBlank() } ?: return@runCatching null
        Timber.tag(TAG).d("got ${data.format} (${data.syncType}, ${data.confidence})")

        when (data.format?.lowercase()) {
            "ttml" -> {
                val lines = TTMLParser.parseTTML(raw)
                if (lines.isEmpty()) null else TTMLParser.toLRC(lines)
            }
            // lrc & plain bisa dipakai apa adanya.
            else -> raw
        }
    }.getOrElse { e ->
        Timber.tag(TAG).w(e, "fetch failed")
        null
    }
}
