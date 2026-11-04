/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Locale

/**
 * QQ Music (Portato) — lirik karaoke QRC lewat api Better Lyrics.
 *
 * Format isinya `[awal,panjang]kata(awal,panjang)…`, timestamps dalam milidetik
 * dan sudah per suku kata/kata. Sumbernya berbeda dari Apple, jadi sering
 * menutup lagu yang tidak ada di lrc.red/BetterLyrics.
 */
object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"

    private const val BASE_URL = "https://lyrics-api.boidu.dev"
    private const val TAG = "QQMusic"

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
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 6_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    @Serializable
    private data class QqResponse(
        val lyrics: String? = null,
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val response = client.get("$BASE_URL/qq/getLyrics") {
            parameter("s", title)
            parameter("a", artist)
            parameter("al", album.orEmpty())
            if (duration > 0) parameter("d", duration)
        }
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("QQMusic: status ${response.status}")
        }
        val body = json.decodeFromString(QqResponse.serializer(), response.bodyAsText())
        val raw = body.lyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("QQMusic: empty")
        val lrc = qrcToLrc(raw)
        if (lrc.isBlank()) throw IllegalStateException("QQMusic: no lines")
        Timber.tag(TAG).d("lines=${lrc.lineSequence().count()}")
        lrc
    }

    private fun qrcToLrc(qrc: String): String {
        val lineRegex = Regex("^\\[(\\d+),(\\d+)](.*)$")
        val tokenRegex = Regex("([^()]*)\\((\\d+),(\\d+)\\)")
        val sb = StringBuilder()
        qrc.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val m = lineRegex.find(line) ?: return@forEach
            val lineStart = m.groupValues[1].toLongOrNull() ?: return@forEach
            val tokens = tokenRegex.findAll(m.groupValues[3]).map { hit ->
                val text = hit.groupValues[1]
                val start = hit.groupValues[2].toLongOrNull() ?: 0L
                val length = hit.groupValues[3].toLongOrNull() ?: 0L
                Triple(text, start, start + length)
            }.toList()
            val text = tokens.joinToString("") { it.first }.trim()
            if (text.isBlank()) return@forEach
            sb.appendLine(formatLrcTime(lineStart, text))
            val wordData = tokens
                .filter { it.first.isNotBlank() }
                .joinToString("|") { "${it.first.trim()}:${it.second / 1000.0}:${it.third / 1000.0}" }
            if (wordData.isNotBlank()) sb.appendLine("<$wordData>")
        }
        return sb.toString()
    }

    private fun formatLrcTime(ms: Long, text: String): String {
        val totalSeconds = ms / 1000
        return String.format(
            Locale.US,
            "[%02d:%02d.%02d]%s",
            totalSeconds / 60,
            totalSeconds % 60,
            (ms % 1000) / 10,
            text,
        )
    }
}
