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
 * Impor playlist/album Deezer publik tanpa login, ala Metrolist v14
 * (DeezerPlaylistImporter): API publik Deezer (api.deezer.com) tanpa
 * kunci apa pun. Playlist di atas 100 lagu cuma 100 pertama yang terbawa.
 */
object DeezerPlaylistImporter {
    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_JSON_BYTES = 8 * 1024 * 1024
    const val MAX_TRACKS = 100

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun canHandle(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower.contains("deezer.com") || lower.contains("deezer.page.link")
    }

    suspend fun loadPlaylist(urlOrId: String): Result<ImportedPlaylist> =
        runCatching {
            val client = HttpClient(OkHttp)
            try {
                val (type, id) = parseUrl(client, urlOrId)
                    ?: throw IllegalArgumentException("Tautan Deezer tidak valid")
                if (type == "album") loadAlbum(client, id) else loadPlaylistTracks(client, id)
            } finally {
                client.close()
            }
        }

    private suspend fun parseUrl(client: HttpClient, input: String): Pair<String, String>? {
        var trimmed = input.trim()
        if (trimmed.contains("deezer.page.link")) {
            // Tautan pendek: ikuti redirect tanpa mengikuti otomatis,
            // baca Location-nya.
            val resolveClient = HttpClient(OkHttp) { followRedirects = false }
            try {
                val location =
                    withTimeoutOrNull(15_000.milliseconds) {
                        val response =
                            resolveClient.get(trimmed) {
                                header(HttpHeaders.UserAgent, UA)
                            }
                        response.headers["Location"]
                    } ?: return null
                trimmed = location
            } finally {
                resolveClient.close()
            }
        }
        Regex("""deezer\.com(?:/[a-z]{2})?/playlist/(\d+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.let { return "playlist" to it.groupValues[1] }
        Regex("""deezer\.com(?:/[a-z]{2})?/album/(\d+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.let { return "album" to it.groupValues[1] }
        if (trimmed.matches(Regex("""^\d+$"""))) {
            return "playlist" to trimmed
        }
        return null
    }

    private suspend fun loadPlaylistTracks(
        client: HttpClient,
        playlistId: String,
    ): ImportedPlaylist {
        val root = getJson(client, "https://api.deezer.com/playlist/$playlistId")
            ?: throw IllegalStateException("Playlist Deezer tidak ditemukan")
        if (root.containsKey("error")) {
            val message =
                root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "Deezer API error"
            throw IllegalStateException(message)
        }
        val title = root["title"]?.jsonPrimitive?.content ?: "Playlist Deezer"
        val coverUrl = root["picture_xl"]?.jsonPrimitive?.contentOrNull
        val tracks = mutableListOf<ImportTrack>()
        collectTracks(root, tracks)
        var nextUrl =
            root["tracks"]?.jsonObject?.get("next")?.jsonPrimitive?.contentOrNull
        while (nextUrl != null && tracks.size < MAX_TRACKS) {
            val page = getJson(client, nextUrl) ?: break
            val data = page["data"]?.jsonArray ?: break
            for (item in data) {
                collectTrack(item, tracks)
            }
            nextUrl = page["next"]?.jsonPrimitive?.contentOrNull
        }
        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        }
        return ImportedPlaylist(title, coverUrl, tracks.take(MAX_TRACKS))
    }

    private suspend fun loadAlbum(
        client: HttpClient,
        albumId: String,
    ): ImportedPlaylist {
        val root = getJson(client, "https://api.deezer.com/album/$albumId")
            ?: throw IllegalStateException("Album Deezer tidak ditemukan")
        if (root.containsKey("error")) {
            val message =
                root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "Deezer API error"
            throw IllegalStateException(message)
        }
        val title = root["title"]?.jsonPrimitive?.content ?: "Album Deezer"
        val coverUrl = root["cover_xl"]?.jsonPrimitive?.contentOrNull
        val tracks = mutableListOf<ImportTrack>()
        collectTracks(root, tracks)
        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        }
        return ImportedPlaylist(title, coverUrl, tracks.take(MAX_TRACKS))
    }

    private fun collectTracks(
        root: kotlinx.serialization.json.JsonObject,
        out: MutableList<ImportTrack>,
    ) {
        val data = root["tracks"]?.jsonObject?.get("data")?.jsonArray ?: JsonArray(emptyList())
        for (item in data) {
            collectTrack(item, out)
        }
    }

    private fun collectTrack(
        item: kotlinx.serialization.json.JsonElement,
        out: MutableList<ImportTrack>,
    ) {
        val obj = item.jsonObject
        val trackTitle = obj["title"]?.jsonPrimitive?.content?.trim() ?: return
        val artistName =
            obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.trim() ?: ""
        out += ImportTrack(trackTitle, artistName)
    }

    private suspend fun getJson(
        client: HttpClient,
        url: String,
    ): kotlinx.serialization.json.JsonObject? =
        withTimeoutOrNull(15_000.milliseconds) {
            val response =
                client.get(url) {
                    header(HttpHeaders.UserAgent, UA)
                }
            if (!response.status.isSuccess()) return@withTimeoutOrNull null
            val body = response.bodyAsText().take(MAX_JSON_BYTES)
            if (body.isBlank()) null else json.parseToJsonElement(body).jsonObject
        }
}
