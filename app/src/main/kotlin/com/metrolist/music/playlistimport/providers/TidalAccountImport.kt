/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import com.metrolist.music.playlistimport.ImportTrack
import com.metrolist.music.playlistimport.ImportedPlaylist
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Impor playlist dari AKUN Tidal, ala Metrolist v14
 * (TidalImportManager): cookie `sid` + `userId` ditangkap dari WebView
 * listen.tidal.com, lalu playlist pengguna dimuat lewat API web Tidal
 * v1 dengan header x-tidal-session-id. Entri "Favorites" sintetis di
 * urutan pertama memuat lagu favorit lewat /users/{id}/favorites/tracks.
 */
class TidalAccountManager : AccountPlaylistSource {
    private val clientDelegate = lazy { HttpClient(OkHttp) }
    private val httpClient: HttpClient by clientDelegate
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var sessionId: String? = null
    private var userId: String? = null
    private var closed = false

    fun authenticate(
        sessionId: String,
        userId: String,
    ) {
        require(sessionId.isNotBlank()) { "Tidal login did not return a session ID" }
        check(!closed) { "Tidal import is closed" }
        this.sessionId = sessionId
        this.userId = userId
    }

    val isAuthenticated: Boolean get() = sessionId != null

    override suspend fun loadPlaylists(
        likedSongsName: String,
    ): Result<List<AccountPlaylistChoice>> =
        runCatching {
            val sid = sessionId ?: throw IllegalStateException("Sign in to Tidal first")
            val uid = userId ?: throw IllegalStateException("Sign in to Tidal first")
            val playlists = mutableListOf<AccountPlaylist>()
            var offset = 0
            val limit = 50
            while (playlists.size < MAX_ACCOUNT_PLAYLISTS) {
                val root =
                    getJson(
                        "https://api.tidal.com/v1/users/$uid/playlists?offset=$offset&limit=$limit",
                        sid,
                    ) ?: break
                if (root.containsKey("errorCode")) break
                val items = root["items"]?.jsonArray ?: break
                if (items.isEmpty()) break
                for (item in items) {
                    val obj = item.jsonObject
                    val id = obj["uuid"]?.jsonPrimitive?.content ?: continue
                    val title = obj["title"]?.jsonPrimitive?.content ?: continue
                    val numberOfTracks =
                        obj["numberOfTracks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    // v14 meneruskan nilai mentahnya (bukan URL lengkap).
                    val imageUrl =
                        obj["image"]?.jsonPrimitive?.content
                            ?: obj["squareImage"]?.jsonPrimitive?.content
                    playlists +=
                        AccountPlaylist(
                            id = id,
                            name = title,
                            imageUrl = imageUrl,
                            trackCount = numberOfTracks,
                        )
                }
                offset += limit
            }
            val choices =
                playlists.map { playlist ->
                    AccountPlaylistChoice(playlist) { onProgress ->
                        loadPlaylist(playlist, likedSongsName, sid, uid, onProgress)
                    }
                }
            // "Favorites" sintetis di urutan pertama, ala v14.
            val favoritesPlaylist =
                AccountPlaylist(
                    id = FAVORITES_ID,
                    name = likedSongsName,
                    imageUrl = null,
                    trackCount = 0,
                )
            val favorites =
                AccountPlaylistChoice(favoritesPlaylist) { onProgress ->
                    loadPlaylist(favoritesPlaylist, likedSongsName, sid, uid, onProgress)
                }
            listOf(favorites) + choices
        }

    private suspend fun loadPlaylist(
        playlist: AccountPlaylist,
        likedSongsName: String,
        sid: String,
        uid: String,
        onProgress: (loaded: Int, total: Int?) -> Unit = { _, _ -> },
    ): Result<ImportedPlaylist> =
        runCatching {
            val favorites = playlist.id == FAVORITES_ID
            val path =
                if (favorites) "users/$uid/favorites/tracks"
                else "playlists/${playlist.id}/tracks"
            val playlistName = if (favorites) likedSongsName else playlist.name
            val tracks = mutableListOf<ImportTrack>()
            var offset = 0
            val limit = 50
            while (true) {
                val root =
                    getJson(
                        "https://api.tidal.com/v1/$path?offset=$offset&limit=$limit",
                        sid,
                    ) ?: break
                if (root.containsKey("errorCode")) break
                val items = root["items"]?.jsonArray ?: break
                if (items.isEmpty()) break
                if (tracks.size >= MAX_ACCOUNT_TRACKS) {
                    throw IllegalStateException("Playlist has too many tracks")
                }
                for (item in items) {
                    val obj = item.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content?.trim() ?: continue
                    val artistName =
                        obj["artist"]
                            ?.jsonObject
                            ?.get("name")
                            ?.jsonPrimitive
                            ?.content
                            ?.trim() ?: ""
                    if (tracks.size >= MAX_ACCOUNT_TRACKS) {
                        throw IllegalStateException("Playlist has too many tracks")
                    }
                    tracks += ImportTrack(title, artistName)
                }
                onProgress(tracks.size, null)
                offset += limit
            }
            if (tracks.isEmpty()) throw IllegalStateException("No tracks to import")
            ImportedPlaylist(title = playlistName, coverUrl = null, tracks = tracks)
        }

    private suspend fun getJson(
        url: String,
        sessionId: String,
    ): kotlinx.serialization.json.JsonObject? =
        withTimeoutOrNull(15_000.milliseconds) {
            val response =
                httpClient.get(url) {
                    header(HttpHeaders.UserAgent, UA)
                    header("x-tidal-session-id", sessionId)
                }
            if (!response.status.isSuccess()) return@withTimeoutOrNull null
            val body = response.bodyAsText().take(MAX_JSON_BYTES)
            if (body.isBlank()) null else json.parseToJsonElement(body).jsonObject
        }

    override fun close() {
        closed = true
        sessionId = null
        userId = null
        if (clientDelegate.isInitialized()) httpClient.close()
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_JSON_BYTES = 4 * 1024 * 1024
        private const val FAVORITES_ID = "favorites"

        /** Ambil cookie `sid` + `userId` dari header cookie listen.tidal.com. */
        fun extractSessionCredentials(cookieHeader: String?): Pair<String, String>? {
            if (cookieHeader.isNullOrBlank()) return null
            val sid =
                Regex("""(?:^|;\s*)sid=([^;]+)""").find(cookieHeader)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: return null
            val uid =
                Regex("""(?:^|;\s*)userId=([^;]+)""").find(cookieHeader)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: return null
            return sid to uid
        }
    }
}
