/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Impor playlist/album Tidal publik tanpa login, ala Metrolist v14
 * (TidalPlaylistImporter): ambil halaman embed Tidal, baca payload
 * __NEXT_DATA__-nya. Playlist di atas 100 lagu cuma 100 pertama yang
 * terbawa.
 */
object TidalPlaylistImporter {
    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_HTML_BYTES = 4 * 1024 * 1024
    const val MAX_TRACKS = 100

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun canHandle(input: String): Boolean = input.lowercase().trim().contains("tidal.com")

    suspend fun loadPlaylist(urlOrId: String): Result<ImportedPlaylist> =
        runCatching {
            val (type, id) = parseUrl(urlOrId)
                ?: throw IllegalArgumentException("Tautan Tidal tidak valid")
            val client = HttpClient(OkHttp)
            try {
                if (type == "album") loadAlbum(client, id) else loadPlaylistTracks(client, id)
            } finally {
                client.close()
            }
        }

    private fun parseUrl(input: String): Pair<String, String>? {
        val trimmed = input.trim()
        Regex("""tidal\.com(?:/browse)?/playlist/([a-f0-9-]+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.let { return "playlist" to it.groupValues[1] }
        Regex("""tidal\.com(?:/browse)?/album/(\d+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.let { return "album" to it.groupValues[1] }
        return null
    }

    private suspend fun loadPlaylistTracks(
        client: HttpClient,
        playlistId: String,
    ): ImportedPlaylist {
        val html = getHtml(client, "https://embed.tidal.com/playlists/$playlistId")
            ?: throw IllegalStateException("Playlist Tidal tidak ditemukan")
        return parseEmbed(html, "Playlist Tidal")
    }

    private suspend fun loadAlbum(
        client: HttpClient,
        albumId: String,
    ): ImportedPlaylist {
        val html = getHtml(client, "https://embed.tidal.com/albums/$albumId")
            ?: throw IllegalStateException("Album Tidal tidak ditemukan")
        return parseEmbed(html, "Album Tidal")
    }

    private fun parseEmbed(html: String, fallbackTitle: String): ImportedPlaylist {
        val title =
            Regex(
                """<meta\s+property="og:title"\s+content="([^"]+)"""",
                RegexOption.IGNORE_CASE,
            ).find(html)?.groupValues?.get(1)
                ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.trim()
                    ?.replace(" | TIDAL", "")?.trim()
                ?: fallbackTitle
        val coverUrl =
            Regex(
                """<meta\s+property="og:image"\s+content="([^"]+)"""",
                RegexOption.IGNORE_CASE,
            ).find(html)?.groupValues?.get(1)

        val tracks = mutableListOf<ImportTrack>()
        val nextDataMatch =
            Regex(
                """<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html)
        if (nextDataMatch != null) {
            val root = json.parseToJsonElement(nextDataMatch.groupValues[1]).jsonObject
            val pageProps = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
            val trackList =
                pageProps?.get("playlist")?.jsonObject?.get("tracks")?.jsonArray
                    ?: pageProps?.get("album")?.jsonObject?.get("tracks")?.jsonArray
                    ?: pageProps?.get("tracks")?.jsonArray
                    ?: JsonArray(emptyList())
            for (item in trackList) {
                val obj = item.jsonObject
                val trackTitle = obj["title"]?.jsonPrimitive?.content?.trim() ?: continue
                val artistName =
                    obj["artists"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("name")?.jsonPrimitive?.content?.trim()
                        ?: obj["artist"]?.jsonObject?.get("name")
                            ?.jsonPrimitive?.content?.trim()
                        ?: ""
                tracks += ImportTrack(trackTitle, artistName)
            }
        }
        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        }
        return ImportedPlaylist(title, coverUrl, tracks.take(MAX_TRACKS))
    }

    private suspend fun getHtml(client: HttpClient, url: String): String? =
        withTimeoutOrNull(15_000.milliseconds) {
            val response =
                client.get(url) {
                    header(HttpHeaders.UserAgent, UA)
                }
            if (!response.status.isSuccess()) return@withTimeoutOrNull null
            response.bodyAsText().take(MAX_HTML_BYTES).takeIf { it.isNotBlank() }
        }
}
