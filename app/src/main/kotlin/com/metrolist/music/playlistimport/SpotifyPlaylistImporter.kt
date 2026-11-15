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

/** Satu lagu dari playlist sumber (judul + artis, tanpa durasi). */
data class ImportTrack(
    val title: String,
    val artist: String,
)

data class ImportedPlaylist(
    val title: String,
    val coverUrl: String?,
    val tracks: List<ImportTrack>,
)

/**
 * Impor playlist Spotify publik tanpa login, ala Metrolist v14
 * (SpotifyUrlPlaylistImporter): ambil halaman embed Spotify, baca payload
 * __NEXT_DATA__-nya. Playlist di atas 100 lagu cuma 100 pertama yang
 * terbawa (paginasi pathfinder belum diport).
 */
object SpotifyPlaylistImporter {
    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_EMBED_HTML_BYTES = 4 * 1024 * 1024
    const val MAX_TRACKS = 100

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun canHandle(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower.contains("spotify.com") ||
            lower.startsWith("spotify:playlist:") ||
            lower.startsWith("spotify:album:")
    }

    suspend fun loadPlaylist(urlOrUri: String): Result<ImportedPlaylist> =
        runCatching {
            val (type, id) =
                parseUrl(urlOrUri)
                    ?: throw IllegalArgumentException("Tautan Spotify tidak valid")
            val client = HttpClient(OkHttp)
            try {
                val html =
                    withTimeoutOrNull(15_000.milliseconds) {
                        val response =
                            client.get("https://open.spotify.com/embed/$type/$id") {
                                header(HttpHeaders.UserAgent, UA)
                                header(HttpHeaders.Accept, "text/html")
                            }
                        if (!response.status.isSuccess()) {
                            throw IllegalStateException(
                                "Spotify $type tidak ditemukan (HTTP ${response.status.value})",
                            )
                        }
                        response.bodyAsText().take(MAX_EMBED_HTML_BYTES)
                    } ?: throw IllegalStateException("Spotify tidak menjawab, coba lagi")
                parseEmbed(type, html)
            } finally {
                client.close()
            }
        }

    private fun parseUrl(input: String): Pair<String, String>? {
        val trimmed = input.trim()
        Regex("""spotify\.com/(?:[a-zA-Z0-9_-]+/)?playlist/([a-zA-Z0-9]+)""")
            .find(trimmed)?.let { return "playlist" to it.groupValues[1] }
        Regex("""spotify\.com/(?:[a-zA-Z0-9_-]+/)?album/([a-zA-Z0-9]+)""")
            .find(trimmed)?.let { return "album" to it.groupValues[1] }
        Regex("""spotify:(playlist|album):([a-zA-Z0-9]+)""")
            .find(trimmed)?.let { return it.groupValues[1] to it.groupValues[2] }
        if (trimmed.matches(Regex("""^[a-zA-Z0-9]{22}$"""))) {
            return "playlist" to trimmed
        }
        return null
    }

    private fun parseEmbed(
        type: String,
        html: String,
    ): ImportedPlaylist {
        val scriptMatch =
            Regex(
                """<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html)
                ?: throw IllegalStateException("Tidak bisa membaca halaman Spotify")
        val root = json.parseToJsonElement(scriptMatch.groupValues[1]).jsonObject
        val entity =
            root["props"]
                ?.jsonObject
                ?.get("pageProps")
                ?.jsonObject
                ?.get("state")
                ?.jsonObject
                ?.get("data")
                ?.jsonObject
                ?.get("entity")
                ?.jsonObject
                ?: throw IllegalStateException("Data Spotify tidak ditemukan di halaman")
        val title =
            entity["name"]?.jsonPrimitive?.content
                ?: entity["title"]?.jsonPrimitive?.content
                ?: if (type == "playlist") "Playlist Spotify" else "Album Spotify"
        val coverUrl =
            entity["coverArt"]
                ?.jsonObject
                ?.get("sources")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.contentOrNull
        val trackList = entity["trackList"]?.jsonArray ?: JsonArray(emptyList())
        val tracks =
            trackList.mapNotNull { item ->
                val obj = item.jsonObject
                val trackTitle =
                    obj["title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                val artist =
                    obj["subtitle"]?.jsonPrimitive?.content?.trim()?.replace('\u00A0', ' ')
                        ?: ""
                ImportTrack(trackTitle, artist)
            }.take(MAX_TRACKS)
        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        }
        return ImportedPlaylist(title, coverUrl, tracks)
    }
}
