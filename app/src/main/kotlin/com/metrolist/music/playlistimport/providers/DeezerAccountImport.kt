/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import android.net.Uri
import com.metrolist.music.playlistimport.ImportTrack
import com.metrolist.music.playlistimport.ImportedPlaylist
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Impor playlist dari AKUN Deezer, ala Metrolist v14
 * (DeezerImportManager): cookie `arl` ditangkap dari WebView
 * deezer.com, lalu playlist pengguna dimuat lewat API resmi Deezer
 * dengan access_token=arl. Entri "Liked Songs" sintetis di urutan
 * pertama memuat lagu yang disukai lewat /user/me/tracks.
 */
class DeezerAccountManager : AccountPlaylistSource {
    private val clientDelegate = lazy { HttpClient(OkHttp) }
    private val httpClient: HttpClient by clientDelegate
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var accessToken: String? = null
    private var closed = false

    fun authenticate(arl: String) {
        require(arl.isNotBlank()) { "Deezer login did not return a session token" }
        check(!closed) { "Deezer import is closed" }
        accessToken = arl
    }

    val isAuthenticated: Boolean get() = accessToken != null

    override suspend fun loadPlaylists(
        likedSongsName: String,
    ): Result<List<AccountPlaylistChoice>> =
        runCatching {
            val token = accessToken ?: throw IllegalStateException("Sign in to Deezer first")
            val playlists = mutableListOf<AccountPlaylist>()
            var nextUrl: String? = "https://api.deezer.com/user/me/playlists"
            while (nextUrl != null && playlists.size < MAX_ACCOUNT_PLAYLISTS) {
                val root = getJson(deezerApiUrl(nextUrl), token) ?: break
                if (root.containsKey("error")) break
                val dataArray = root["data"]?.jsonArray ?: break
                for (item in dataArray) {
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: continue
                    val title = obj["title"]?.jsonPrimitive?.content ?: continue
                    val trackCount = obj["nb_tracks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val picture =
                        obj["picture_medium"]?.jsonPrimitive?.contentOrNull
                            ?: obj["picture"]?.jsonPrimitive?.contentOrNull
                    playlists +=
                        AccountPlaylist(
                            id = id,
                            name = title,
                            imageUrl = picture,
                            trackCount = trackCount,
                        )
                }
                nextUrl = root["next"]?.jsonPrimitive?.contentOrNull
            }
            val choices =
                playlists.map { playlist ->
                    AccountPlaylistChoice(playlist) { onProgress ->
                        loadPlaylistTracks(playlist.id, playlist.name, token, onProgress)
                    }
                }
            // "Liked Songs" sintetis di urutan pertama, ala v14.
            val liked =
                AccountPlaylistChoice(
                    AccountPlaylist(
                        id = LIKED_SONGS_ID,
                        name = likedSongsName,
                        imageUrl = null,
                        trackCount = 0,
                    ),
                ) { onProgress ->
                    loadLikedSongs(token, likedSongsName, onProgress)
                }
            listOf(liked) + choices
        }

    private suspend fun loadPlaylistTracks(
        playlistId: String,
        playlistName: String,
        token: String,
        onProgress: (loaded: Int, total: Int?) -> Unit = { _, _ -> },
    ): Result<ImportedPlaylist> =
        runCatching {
            val tracks = mutableListOf<ImportTrack>()
            var nextUrl: String? = "https://api.deezer.com/playlist/$playlistId/tracks"
            while (nextUrl != null && tracks.size < MAX_ACCOUNT_TRACKS) {
                val root = getJson(deezerApiUrl(nextUrl), token) ?: break
                if (root.containsKey("error")) break
                val dataArray = root["data"]?.jsonArray ?: break
                tracks += collectTracks(dataArray)
                onProgress(tracks.size, null)
                nextUrl = root["next"]?.jsonPrimitive?.contentOrNull
            }
            if (tracks.isEmpty()) throw IllegalStateException("No tracks to import")
            ImportedPlaylist(title = playlistName, coverUrl = null, tracks = tracks)
        }

    private suspend fun loadLikedSongs(
        token: String,
        likedSongsName: String,
        onProgress: (loaded: Int, total: Int?) -> Unit = { _, _ -> },
    ): Result<ImportedPlaylist> =
        runCatching {
            val tracks = mutableListOf<ImportTrack>()
            var nextUrl: String? = "https://api.deezer.com/user/me/tracks"
            while (nextUrl != null && tracks.size < MAX_ACCOUNT_TRACKS) {
                val root = getJson(deezerApiUrl(nextUrl), token) ?: break
                if (root.containsKey("error")) break
                val dataArray = root["data"]?.jsonArray ?: break
                tracks += collectTracks(dataArray)
                onProgress(tracks.size, null)
                nextUrl = root["next"]?.jsonPrimitive?.contentOrNull
            }
            if (tracks.isEmpty()) throw IllegalStateException("No tracks to import")
            ImportedPlaylist(title = likedSongsName, coverUrl = null, tracks = tracks)
        }

    private fun collectTracks(dataArray: kotlinx.serialization.json.JsonArray): List<ImportTrack> {
        val tracks = mutableListOf<ImportTrack>()
        for (item in dataArray) {
            val obj = item.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content?.trim() ?: continue
            val artistName =
                obj["artist"]
                    ?.jsonObject
                    ?.get("name")
                    ?.jsonPrimitive
                    ?.content
                    ?.trim() ?: ""
            tracks += ImportTrack(title, artistName)
        }
        return tracks
    }

    private suspend fun getJson(
        url: String,
        token: String,
    ): kotlinx.serialization.json.JsonObject? =
        withTimeoutOrNull(15_000.milliseconds) {
            val response =
                httpClient.get(url) {
                    header(HttpHeaders.UserAgent, UA)
                    parameter("access_token", token)
                }
            if (!response.status.isSuccess()) return@withTimeoutOrNull null
            val body = response.bodyAsText().take(MAX_JSON_BYTES)
            if (body.isBlank()) null else json.parseToJsonElement(body).jsonObject
        }

    override fun close() {
        closed = true
        accessToken = null
        if (clientDelegate.isInitialized()) httpClient.close()
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_JSON_BYTES = 8 * 1024 * 1024
        private const val LIKED_SONGS_ID = "liked"

        /** Ambil cookie `arl` dari header cookie deezer.com. */
        fun extractArl(cookieHeader: String?): String? {
            if (cookieHeader.isNullOrBlank()) return null
            val match = Regex("""(?:^|;\s*)arl=([^;]+)""").find(cookieHeader)
            return match
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        private fun deezerApiUrl(value: String): String {
            if (value.startsWith('/')) return "https://api.deezer.com$value"
            val host = runCatching { Uri.parse(value).host?.lowercase() }.getOrNull()
            require(host == "api.deezer.com") { "Unexpected Deezer API host: $host" }
            return value
        }
    }
}
