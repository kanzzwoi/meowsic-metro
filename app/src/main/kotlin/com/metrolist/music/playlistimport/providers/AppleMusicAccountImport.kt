/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import android.util.Base64
import com.metrolist.music.playlistimport.ImportTrack
import com.metrolist.music.playlistimport.ImportedPlaylist
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Impor playlist dari AKUN Apple Music (library pengguna), ala Metrolist
 * v14 (AppleMusicPlaylistImporter.loadUserPlaylists /
 * loadUserPlaylistTracks): token pengguna (media-user-token) ditangkap
 * dari cookie WebView music.apple.com, lalu playlist library dimuat
 * lewat API resmi Apple dengan token developer + token pengguna.
 * Token developer TIDAK perlu didaftarkan pengguna — dicomot dari
 * bundle JS halaman music.apple.com, sama seperti impor tautan publik
 * Meowsic (AppleMusicPlaylistImporter.getDevToken, privat di sana
 * sehingga logikanya diduplikasi di sini).
 */
class AppleMusicAccountManager : AccountPlaylistSource {
    private val clientDelegate = lazy { HttpClient(OkHttp) }
    private val httpClient: HttpClient by clientDelegate
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var userToken: String? = null
    private var closed = false

    fun authenticate(userToken: String) {
        require(userToken.isNotBlank()) { "Apple Music login did not return a session token" }
        check(!closed) { "Apple Music import is closed" }
        this.userToken = userToken
    }

    val isAuthenticated: Boolean get() = userToken != null

    override suspend fun loadPlaylists(
        likedSongsName: String,
    ): Result<List<AccountPlaylistChoice>> =
        runCatching {
            val token = userToken ?: throw IllegalStateException("Sign in to Apple Music first")
            val devToken =
                getDevToken()
                    ?: throw IllegalStateException("Failed to obtain Apple Music developer token")
            val playlists = mutableListOf<AccountPlaylist>()
            var nextUrl: String? = "https://api.music.apple.com/v1/me/library/playlists?limit=100"
            while (nextUrl != null && playlists.size < MAX_ACCOUNT_PLAYLISTS) {
                val root = getLibraryJson(nextUrl, devToken, token) ?: break
                val dataArray = root["data"]?.jsonArray ?: break
                for (item in dataArray) {
                    val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                    val attrs = item.jsonObject["attributes"]?.jsonObject ?: continue
                    val name =
                        attrs["name"]?.jsonPrimitive?.content ?: DEFAULT_PLAYLIST_NAME
                    val trackCount =
                        attrs["trackCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val artworkUrl =
                        attrs["artwork"]?.jsonObject
                            ?.get("url")?.jsonPrimitive?.contentOrNull
                            ?.replace("{w}", "300")
                            ?.replace("{h}", "300")
                    playlists +=
                        AccountPlaylist(
                            id = id,
                            name = name,
                            imageUrl = artworkUrl,
                            trackCount = trackCount,
                        )
                }
                nextUrl = root["next"]?.jsonPrimitive?.content
            }
            playlists.map { playlist ->
                AccountPlaylistChoice(playlist) { onProgress ->
                    loadUserPlaylistTracks(token, playlist.id, playlist.name, onProgress)
                }
            }
        }

    private suspend fun loadUserPlaylistTracks(
        token: String,
        playlistId: String,
        playlistName: String,
        onProgress: (loaded: Int, total: Int?) -> Unit = { _, _ -> },
    ): Result<ImportedPlaylist> =
        runCatching {
            val devToken =
                getDevToken()
                    ?: throw IllegalStateException("Failed to obtain Apple Music developer token")
            val tracks = mutableListOf<ImportTrack>()
            var nextUrl: String? =
                "https://api.music.apple.com/v1/me/library/playlists/$playlistId/tracks?limit=100"
            while (nextUrl != null && tracks.size < MAX_ACCOUNT_TRACKS) {
                val root = getLibraryJson(nextUrl, devToken, token) ?: break
                val dataArray = root["data"]?.jsonArray ?: break
                for (trackItem in dataArray) {
                    val attrs = trackItem.jsonObject["attributes"]?.jsonObject ?: continue
                    val trackName = attrs["name"]?.jsonPrimitive?.content?.trim() ?: continue
                    val artistName = attrs["artistName"]?.jsonPrimitive?.content?.trim() ?: ""
                    tracks += ImportTrack(trackName, artistName)
                }
                onProgress(tracks.size, null)
                nextUrl = root["next"]?.jsonPrimitive?.content
            }
            if (tracks.isEmpty()) {
                throw IllegalStateException("No tracks found in this playlist")
            }
            ImportedPlaylist(title = playlistName, coverUrl = null, tracks = tracks)
        }

    private suspend fun getLibraryJson(
        url: String,
        devToken: String,
        token: String,
    ): kotlinx.serialization.json.JsonObject? =
        withTimeoutOrNull(15_000.milliseconds) {
            val response =
                httpClient.get(appleApiUrl(url)) {
                    header(HttpHeaders.Authorization, "Bearer $devToken")
                    header("Music-User-Token", token)
                    header(HttpHeaders.Origin, "https://music.apple.com")
                    header(HttpHeaders.UserAgent, UA)
                }
            if (!response.status.isSuccess()) return@withTimeoutOrNull null
            val body = response.bodyAsText().take(MAX_JSON_BYTES)
            if (body.isBlank()) null else json.parseToJsonElement(body).jsonObject
        }

    private fun appleApiUrl(path: String): String =
        if (path.startsWith('/')) "https://api.music.apple.com$path" else path

    /**
     * Duplikat AppleMusicPlaylistImporter.getDevToken (privat di sana):
     * ambil token developer dari bundle JS halaman music.apple.com.
     */
    private suspend fun getDevToken(): String? {
        val now = System.currentTimeMillis() / 1000
        tokenMutex.withLock {
            cachedToken?.let { token ->
                if (tokenExpiry - now > 300) return token
            }
        }
        return try {
            val browseHtml =
                withTimeoutOrNull(15_000.milliseconds) {
                    val response =
                        httpClient.get("https://music.apple.com/browse") {
                            header(HttpHeaders.UserAgent, UA)
                            header(HttpHeaders.Accept, "text/html")
                        }
                    if (!response.status.isSuccess()) return@withTimeoutOrNull null
                    response.bodyAsText().take(MAX_HTML_BYTES)
                } ?: return null
            val bundlePath =
                Regex("""/assets/index[^"'\\s]*\.js""").find(browseHtml)?.value ?: return null
            val js =
                withTimeoutOrNull(15_000.milliseconds) {
                    val response =
                        httpClient.get("https://music.apple.com$bundlePath") {
                            header(HttpHeaders.UserAgent, UA)
                        }
                    if (!response.status.isSuccess()) return@withTimeoutOrNull null
                    response.bodyAsText().take(MAX_SCRIPT_BYTES)
                } ?: return null
            val jwt =
                Regex("""eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
                    .find(js)?.value ?: return null
            var exp = now + 86400
            runCatching {
                val parts = jwt.split('.')
                if (parts.size >= 2) {
                    var payload = parts[1]
                    while (payload.length % 4 != 0) payload += "="
                    val decoded =
                        Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
                            .decodeToString()
                    exp = json.decodeFromString<TokenPayload>(decoded).exp ?: exp
                }
            }
            tokenMutex.withLock {
                cachedToken = jwt
                tokenExpiry = exp
            }
            jwt
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        closed = true
        userToken = null
        if (clientDelegate.isInitialized()) httpClient.close()
    }

    @Serializable
    private data class TokenPayload(
        val exp: Long? = null,
    )

    companion object {
        private const val UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_HTML_BYTES = 4 * 1024 * 1024
        private const val MAX_SCRIPT_BYTES = 16 * 1024 * 1024
        private const val MAX_JSON_BYTES = 8 * 1024 * 1024

        private var cachedToken: String? = null
        private var tokenExpiry: Long = 0L
        private val tokenMutex = Mutex()

        /** Ambil media-user-token dari header cookie music.apple.com. */
        fun extractUserToken(cookieHeader: String?): String? {
            if (cookieHeader.isNullOrBlank()) return null
            val match = Regex("""(?:^|;\s*)media-user-token=([^;]+)""").find(cookieHeader)
            return match
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
