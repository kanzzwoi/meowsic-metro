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
import io.ktor.client.statement.HttpResponse
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
 * Impor playlist dari AKUN Amazon Music, ala Metrolist v14
 * (AmazonMusicImportManager): pengguna memilih marketplace dulu, lalu
 * login di situs marketplace itu lewat WebView; seluruh header cookie
 * sesi ditangkap dan dipakai untuk memanggil API library Amazon
 * (https://{host}/api/library/...). Entri "Liked Songs" sintetis di
 * urutan pertama memuat lagu yang disukai lewat /api/library/tracks.
 */
class AmazonMusicImportManager : AccountPlaylistSource {
    private val clientDelegate = lazy { HttpClient(OkHttp) }
    private val httpClient: HttpClient by clientDelegate
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var sessionCookies: String? = null
    private var marketplaceHost: String? = null
    private var closed = false

    fun authenticate(
        cookies: String,
        host: String,
    ) {
        require(host in marketplaceHosts) { "Unsupported Amazon Music marketplace" }
        require(cookies.isNotBlank()) { "Amazon Music login did not return session cookies" }
        check(!closed) { "Amazon Music import is closed" }
        sessionCookies = cookies
        marketplaceHost = host
    }

    val isAuthenticated: Boolean get() = sessionCookies != null

    override suspend fun loadPlaylists(
        likedSongsName: String,
    ): Result<List<AccountPlaylistChoice>> =
        runCatching {
            val cookies = sessionCookies ?: throw IllegalStateException("Sign in to Amazon Music first")
            val host = marketplaceHost ?: throw IllegalStateException("Sign in to Amazon Music first")
            val body =
                withTimeoutOrNull(15_000.milliseconds) {
                    val response =
                        httpClient.get(
                            "https://$host/api/library/playlists?adjustedTracks=true&maxResults=500&sortColumn=default&sortDirection=ASC",
                        ) {
                            header(HttpHeaders.UserAgent, UA)
                            header(HttpHeaders.Cookie, cookies)
                            header(HttpHeaders.Accept, "application/json")
                        }
                    response.importBody()
                } ?: throw IllegalStateException("Amazon Music request failed")
            val root = json.parseToJsonElement(body).jsonObject
            val playlistsArray =
                root["playlists"]?.jsonArray
                    ?: throw IllegalStateException("Amazon Music playlist data empty")
            val playlists = mutableListOf<AccountPlaylist>()
            for (item in playlistsArray) {
                val obj = item.jsonObject
                val id = obj["playlistId"]?.jsonPrimitive?.content ?: continue
                val name = obj["title"]?.jsonPrimitive?.content ?: continue
                val trackCount = obj["trackCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val imageUrl = obj["imageUrl"]?.jsonPrimitive?.contentOrNull
                playlists +=
                    AccountPlaylist(
                        id = id,
                        name = name,
                        imageUrl = imageUrl,
                        trackCount = trackCount,
                    )
            }
            val choices =
                playlists.map { playlist ->
                    AccountPlaylistChoice(playlist) { onProgress ->
                        loadPlaylist(playlist, likedSongsName, cookies, host, onProgress)
                    }
                }
            // "Liked Songs" sintetis di urutan pertama, ala v14.
            val likedPlaylist =
                AccountPlaylist(
                    id = LIKED_SONGS_ID,
                    name = likedSongsName,
                    imageUrl = null,
                    trackCount = 0,
                )
            val liked =
                AccountPlaylistChoice(likedPlaylist) { onProgress ->
                    loadPlaylist(likedPlaylist, likedSongsName, cookies, host, onProgress)
                }
            listOf(liked) + choices
        }

    private suspend fun loadPlaylist(
        playlist: AccountPlaylist,
        likedSongsName: String,
        cookies: String,
        host: String,
        onProgress: (loaded: Int, total: Int?) -> Unit = { _, _ -> },
    ): Result<ImportedPlaylist> =
        runCatching {
            val likedSongs = playlist.id == LIKED_SONGS_ID
            val playlistName = if (likedSongs) likedSongsName else playlist.name
            val path =
                if (likedSongs) {
                    "tracks?maxResults=500&sortColumn=default&sortDirection=ASC"
                } else {
                    "playlists/${playlist.id}/tracks?maxResults=500"
                }
            val body =
                withTimeoutOrNull(15_000.milliseconds) {
                    val response =
                        httpClient.get("https://$host/api/library/$path") {
                            header(HttpHeaders.UserAgent, UA)
                            header(HttpHeaders.Cookie, cookies)
                            header(HttpHeaders.Accept, "application/json")
                        }
                    response.importBody()
                } ?: throw IllegalStateException("Amazon Music request failed")
            val root = json.parseToJsonElement(body).jsonObject
            val tracksArray =
                root["tracks"]?.jsonArray
                    ?: throw IllegalStateException("Amazon Music track data empty")
            val tracks = mutableListOf<ImportTrack>()
            for (item in tracksArray) {
                val obj = item.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content?.trim() ?: continue
                val artistName = obj["artist"]?.jsonPrimitive?.content?.trim() ?: ""
                tracks += ImportTrack(title, artistName)
            }
            onProgress(tracks.size, tracks.size)
            if (playlist.trackCount > MAX_ACCOUNT_TRACKS) {
                throw IllegalStateException("Playlist has too many tracks")
            }
            if (tracks.size < playlist.trackCount ||
                (playlist.trackCount == 0 && tracksArray.size >= 500) ||
                !root["nextToken"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
            ) {
                throw IllegalStateException("Incomplete playlist data from Amazon Music")
            }
            if (tracks.isEmpty()) throw IllegalStateException("No tracks to import")
            ImportedPlaylist(title = playlistName, coverUrl = null, tracks = tracks)
        }

    private suspend fun HttpResponse.importBody(): String {
        if (!status.isSuccess()) {
            throw IllegalStateException("Amazon Music request failed (${status.value})")
        }
        return bodyAsText().take(MAX_JSON_BYTES)
    }

    override fun close() {
        closed = true
        sessionCookies = null
        marketplaceHost = null
        if (clientDelegate.isInitialized()) httpClient.close()
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_JSON_BYTES = 4 * 1024 * 1024
        private const val LIKED_SONGS_ID = "liked"

        /** Daftar marketplace Amazon Music, ala v14 (AmazonMusicPlaylistImporter). */
        val marketplaceHosts =
            setOf(
                "music.amazon.com",
                "music.amazon.co.uk",
                "music.amazon.de",
                "music.amazon.fr",
                "music.amazon.it",
                "music.amazon.es",
                "music.amazon.co.jp",
                "music.amazon.ca",
                "music.amazon.com.au",
                "music.amazon.com.br",
                "music.amazon.com.mx",
                "music.amazon.ie",
                "music.amazon.in",
                "music.amazon.nl",
                "music.amazon.se",
                "music.amazon.pl",
                "music.amazon.com.tr",
                "music.amazon.ae",
                "music.amazon.sa",
                "music.amazon.sg",
                "music.amazon.co.za",
                "music.amazon.com.be",
                "music.amazon.com.eg",
            )

        /**
         * Ambil seluruh header cookie sesi dari cookie marketplace Amazon.
         * Mengembalikan header utuh (bukan satu cookie) karena API library
         * butuh beberapa cookie sekaligus (session-token/session-id/at-main/
         * ubid-main) — persis seperti v14.
         */
        fun extractSessionCookie(cookieHeader: String?): String? {
            if (cookieHeader.isNullOrBlank()) return null
            val hasSessionToken =
                cookieHeader.contains("session-token=") ||
                    cookieHeader.contains("session-id=") ||
                    cookieHeader.contains("at-main=") ||
                    cookieHeader.contains("ubid-main=")
            return if (hasSessionToken) cookieHeader else null
        }
    }
}
