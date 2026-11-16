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
 * Impor playlist/album Amazon Music publik tanpa login, ala Metrolist v14
 * (AmazonMusicPlaylistImporter): ambil halaman playlist/album, baca
 * JSON-LD-nya (trackList). Playlist di atas 100 lagu cuma 100 pertama
 * yang terbawa.
 */
object AmazonMusicPlaylistImporter {
    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_HTML_BYTES = 4 * 1024 * 1024
    const val MAX_TRACKS = 100

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun canHandle(input: String): Boolean =
        input.lowercase().trim().contains("music.amazon.")

    suspend fun loadPlaylist(urlOrId: String): Result<ImportedPlaylist> =
        runCatching {
            val url = parseUrl(urlOrId)
                ?: throw IllegalArgumentException("Tautan Amazon Music tidak valid")
            val client = HttpClient(OkHttp)
            try {
                loadFromPage(client, url)
            } finally {
                client.close()
            }
        }

    private fun parseUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("""^[A-Za-z0-9]{10,}$""")) &&
            !trimmed.contains(".") && !trimmed.contains("/")
        ) {
            return "https://music.amazon.com/albums/$trimmed"
        }
        val absolute =
            if (trimmed.startsWith("music.amazon.", ignoreCase = true)) "https://$trimmed"
            else trimmed
        val lower = absolute.lowercase()
        if (!lower.contains("music.amazon.")) return null
        if (!lower.contains("/playlists/") && !lower.contains("/albums/")) return null
        return absolute
    }

    private suspend fun loadFromPage(client: HttpClient, url: String): ImportedPlaylist {
        val html =
            withTimeoutOrNull(15_000.milliseconds) {
                val response =
                    client.get(url) {
                        header(HttpHeaders.UserAgent, UA)
                    }
                if (!response.status.isSuccess()) return@withTimeoutOrNull null
                response.bodyAsText().take(MAX_HTML_BYTES).takeIf { it.isNotBlank() }
            } ?: throw IllegalStateException("Playlist Amazon Music tidak ditemukan")

        val jsonLdMatch =
            Regex(
                """<script type="application/ld\+json">(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html)
                ?: throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        val root = json.parseToJsonElement(jsonLdMatch.groupValues[1]).jsonObject
        val trackList =
            root["trackList"]?.jsonArray
                ?: root["tracks"]?.jsonArray
                ?: root["itemListElement"]?.jsonArray
                ?: JsonArray(emptyList())
        val title = root["name"]?.jsonPrimitive?.content ?: "Playlist Amazon Music"
        val coverUrl =
            root["image"]?.let { image ->
                when (image) {
                    is JsonArray ->
                        image.firstOrNull()?.jsonPrimitive?.contentOrNull
                    is kotlinx.serialization.json.JsonObject ->
                        image["url"]?.jsonPrimitive?.contentOrNull
                    else -> image.jsonPrimitive.contentOrNull
                }
            }

        val tracks = mutableListOf<ImportTrack>()
        for (item in trackList) {
            val obj = item.jsonObject
            val trackTitle = obj["name"]?.jsonPrimitive?.content?.trim() ?: continue
            val artistName =
                obj["byArtist"]?.jsonObject?.get("name")
                    ?.jsonPrimitive?.content?.trim()
                    ?: obj["artist"]?.jsonPrimitive?.content?.trim()
                    ?: ""
            tracks += ImportTrack(trackTitle, artistName)
        }
        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        }
        return ImportedPlaylist(title, coverUrl, tracks.take(MAX_TRACKS))
    }
}
