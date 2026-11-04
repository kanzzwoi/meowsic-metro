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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.math.abs

/**
 * lrc.red — lirik Apple Music (TTML) yang dicari lewat ISRC rekaman.
 *
 * Alurnya dua langkah: cari ISRC dari judul + artis, lalu ambil TTML-nya.
 * Kualitas per kata mengikuti isi Apple Music: sebagian per baris, sebagian
 * per kata/suku kata. Tidak butuh API key.
 */
object LrcRedLyricsProvider : LyricsProvider {
    override val name = "LrcRed"

    private const val BASE_URL = "https://lrc.red"
    private const val TAG = "LrcRed"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

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
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 8_000
            }
            expectSuccess = false
        }
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class Hit(
        val isrc: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val duration: Int? = null,
    )

    @Serializable
    private data class SearchResponse(
        val hits: List<Hit> = emptyList(),
    )

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val search = client.get("$BASE_URL/search.json") {
            header("User-Agent", UA)
            parameter("q", listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "))
        }
        if (search.status != HttpStatusCode.OK) {
            throw IllegalStateException("LrcRed: search status ${search.status}")
        }
        val hits = json.decodeFromString(SearchResponse.serializer(), search.bodyAsText()).hits
            .filter { !it.isrc.isNullOrBlank() }
        if (hits.isEmpty()) throw IllegalStateException("LrcRed: no isrc")

        val best = if (duration > 0) {
            hits.minByOrNull { abs((it.duration ?: 0) - duration) }
        } else {
            hits.firstOrNull()
        } ?: throw IllegalStateException("LrcRed: no isrc")

        val ttmlResponse = client.get("$BASE_URL/s/${best.isrc}.ttml") {
            header("User-Agent", UA)
        }
        if (ttmlResponse.status != HttpStatusCode.OK) {
            throw IllegalStateException("LrcRed: ttml status ${ttmlResponse.status}")
        }
        val ttml = ttmlResponse.bodyAsText()
        val lines = TTMLParser.parseTTML(ttml)
        if (lines.isEmpty()) throw IllegalStateException("LrcRed: empty ttml")
        Timber.tag(TAG).d("isrc=${best.isrc} lines=${lines.size}")
        TTMLParser.toLRC(lines)
    }
}
