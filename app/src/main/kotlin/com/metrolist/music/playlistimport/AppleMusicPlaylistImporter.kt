/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport

import android.util.Base64
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
 * Impor playlist/album Apple Music publik tanpa login, ala Metrolist v14
 * (AppleMusicPlaylistImporter): token developer dicomot dari bundle JS
 * halaman music.apple.com, lalu playlist diambil lewat API katalog resmi
 * Apple (api.music.apple.com). Playlist di atas 100 lagu cuma 100 pertama
 * yang terbawa. Impor dari akun sendiri (library) butuh login WebView
 * seperti di v14 — belum diport.
 */
object AppleMusicPlaylistImporter {
    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_HTML_BYTES = 4 * 1024 * 1024
    private const val MAX_SCRIPT_BYTES = 16 * 1024 * 1024
    private const val MAX_JSON_BYTES = 8 * 1024 * 1024
    const val MAX_TRACKS = 100

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0L
    private val tokenMutex = Mutex()

    @Serializable
    private data class TokenPayload(
        val exp: Long? = null,
    )

    fun canHandle(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower.contains("music.apple.com") || lower.startsWith("pl.")
    }

    suspend fun loadPlaylist(urlOrId: String): Result<ImportedPlaylist> =
        runCatching {
            val (explicitStorefront, id, isAlbum) =
                parseUrl(urlOrId)
                    ?: throw IllegalArgumentException("Tautan Apple Music tidak valid")
            val storefront = explicitStorefront ?: "us"
            val client = HttpClient(OkHttp)
            try {
                val token = getDevToken(client)
                    ?: throw IllegalStateException("Tidak bisa terhubung ke Apple Music, coba lagi")
                if (isAlbum) {
                    loadAlbum(client, token, storefront, id)
                } else {
                    loadPlaylistTracks(client, token, storefront, id)
                }
            } finally {
                client.close()
            }
        }

    private fun parseUrl(input: String): Triple<String?, String, Boolean>? {
        val trimmed = input.trim()
        if (trimmed.startsWith("pl.")) {
            return Triple(null, trimmed, false)
        }
        Regex(
            """music\.apple\.com(?:/([a-z]{2}))?/playlist/(?:[^/]+/)?(pl\.[a-zA-Z0-9_-]+)""",
            RegexOption.IGNORE_CASE,
        ).find(trimmed)?.let {
            return Triple(it.groups[1]?.value?.lowercase(), it.groups[2]?.value ?: return null, false)
        }
        Regex(
            """music\.apple\.com(?:/([a-z]{2}))?/album/(?:[^/]+/)?(\d+)""",
            RegexOption.IGNORE_CASE,
        ).find(trimmed)?.let {
            return Triple(it.groups[1]?.value?.lowercase(), it.groups[2]?.value ?: return null, true)
        }
        Regex("""(pl\.[a-zA-Z0-9_-]+)""").find(trimmed)?.groupValues?.get(1)?.let {
            return Triple(null, it, false)
        }
        return null
    }

    private suspend fun loadPlaylistTracks(
        client: HttpClient,
        token: String,
        storefront: String,
        playlistId: String,
    ): ImportedPlaylist {
        val metaBody =
            getJson(
                client,
                token,
                "https://api.music.apple.com/v1/catalog/$storefront/playlists/$playlistId",
            ) ?: throw IllegalStateException("Playlist Apple Music tidak ditemukan")
        val item =
            metaBody["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IllegalStateException("Data Apple Music kosong")
        val attrs = item["attributes"]?.jsonObject
        val title = attrs?.get("name")?.jsonPrimitive?.content ?: "Playlist Apple Music"
        val coverUrl =
            attrs?.get("artwork")?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull
                ?.replace("{w}", "1000")
                ?.replace("{h}", "1000")

        val tracks = mutableListOf<ImportTrack>()
        var nextUrl: String? =
            "https://api.music.apple.com/v1/catalog/$storefront/playlists/$playlistId/tracks?limit=100"
        while (nextUrl != null && tracks.size < MAX_TRACKS) {
            val root = getJson(client, token, nextUrl) ?: break
            val dataArray = root["data"]?.jsonArray ?: break
            for (trackItem in dataArray) {
                val trackAttrs = trackItem.jsonObject["attributes"]?.jsonObject ?: continue
                val trackName = trackAttrs["name"]?.jsonPrimitive?.content?.trim() ?: continue
                val artistName = trackAttrs["artistName"]?.jsonPrimitive?.content?.trim() ?: ""
                tracks += ImportTrack(trackName, artistName)
            }
            nextUrl = root["next"]?.jsonPrimitive?.content?.let { appleApiUrl(it) }
        }
        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        }
        return ImportedPlaylist(title, coverUrl, tracks.take(MAX_TRACKS))
    }

    private suspend fun loadAlbum(
        client: HttpClient,
        token: String,
        storefront: String,
        albumId: String,
    ): ImportedPlaylist {
        val metaBody =
            getJson(
                client,
                token,
                "https://api.music.apple.com/v1/catalog/$storefront/albums/$albumId",
            ) ?: throw IllegalStateException("Album Apple Music tidak ditemukan")
        val item =
            metaBody["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IllegalStateException("Data Apple Music kosong")
        val attrs = item["attributes"]?.jsonObject
        val title = attrs?.get("name")?.jsonPrimitive?.content ?: "Album Apple Music"
        val coverUrl =
            attrs?.get("artwork")?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull
                ?.replace("{w}", "1000")
                ?.replace("{h}", "1000")

        val tracks = mutableListOf<ImportTrack>()
        var nextUrl: String? =
            "https://api.music.apple.com/v1/catalog/$storefront/albums/$albumId/tracks?limit=100"
        while (nextUrl != null && tracks.size < MAX_TRACKS) {
            val root = getJson(client, token, nextUrl) ?: break
            val dataArray = root["data"]?.jsonArray ?: break
            for (trackItem in dataArray) {
                val trackAttrs = trackItem.jsonObject["attributes"]?.jsonObject ?: continue
                val trackName = trackAttrs["name"]?.jsonPrimitive?.content?.trim() ?: continue
                val artistName = trackAttrs["artistName"]?.jsonPrimitive?.content?.trim() ?: ""
                tracks += ImportTrack(trackName, artistName)
            }
            nextUrl = root["next"]?.jsonPrimitive?.content?.let { appleApiUrl(it) }
        }
        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
        }
        return ImportedPlaylist(title, coverUrl, tracks.take(MAX_TRACKS))
    }

    private suspend fun getJson(
        client: HttpClient,
        token: String,
        url: String,
    ): kotlinx.serialization.json.JsonObject? =
        withTimeoutOrNull(15_000.milliseconds) {
            val response =
                client.get(url) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Origin, "https://music.apple.com")
                    header(HttpHeaders.UserAgent, UA)
                }
            if (!response.status.isSuccess()) return@withTimeoutOrNull null
            val body = response.bodyAsText().take(MAX_JSON_BYTES)
            if (body.isBlank()) null else json.parseToJsonElement(body).jsonObject
        }

    private fun appleApiUrl(path: String): String =
        if (path.startsWith('/')) "https://api.music.apple.com$path" else path

    private suspend fun getDevToken(client: HttpClient): String? {
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
                        client.get("https://music.apple.com/browse") {
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
                        client.get("https://music.apple.com$bundlePath") {
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
}
