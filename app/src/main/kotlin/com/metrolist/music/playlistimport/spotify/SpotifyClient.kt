/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private const val PAGE_SIZE = 50
private const val OPEN_HOST_NAME = "open.spotify.com"

/**
 * Konfigurasi klien web Spotify. Nilai default sama dengan yang dipakai
 * v14 (spotlib Config.kt): user agent Firefox, versi web-player, dan
 * client id web-player Spotify (identifikasi publik, bukan secret).
 */
data class SpotifyConfig(
    val userAgent: String = "Mozilla/5.0 (X11; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0",
    val appVersion: String = "1.2.96.390.gd46a85db",
    val webClientId: String = "d8a5ed958d274c2e8ee717e6a4b0971d",
    val allowAnonymous: Boolean = false,
)

/**
 * Klien web-API Spotify (Pathfinder GraphQL), port dari spotlib v14
 * (shared/.../meow/nyx/spotlib/SpotifyClient.kt) dengan OkHttp sebagai
 * pengganti Ktor. Semua panggilan memakai cookie sesi sp_dc + access token
 * + client token yang ditukar lewat [SpotifyAuth].
 */
class SpotifyClient internal constructor(
    private val config: SpotifyConfig,
    private val http: OkHttpClient,
    internal val jar: SpotifyCookieJar,
    private val auth: SpotifyAuth,
    internal val api: SpotifyApiHttp,
) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val authMutex = Mutex()
    private var currentSession = SpotifySession()

    companion object {
        const val TAG = "SpotifyApi"

        fun create(config: SpotifyConfig = SpotifyConfig()): SpotifyClient {
            val okHttp =
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            val jar = SpotifyCookieJar()
            val api = SpotifyApiHttp(okHttp, jar)
            return SpotifyClient(config, okHttp, jar, SpotifyAuth(api, config, jar), api)
        }
    }

    val session: SpotifySession get() = currentSession

    fun attachSession(session: SpotifySession): SpotifyClient {
        currentSession = session
        jar.clear()
        for ((name, value) in session.cookies) jar.store(OPEN_HOST_NAME, name, value)
        return this
    }

    suspend fun loginWithSession(spDc: String): SpotifySession = loginWithSession(mapOf("sp_dc" to spDc))

    suspend fun loginWithSession(cookies: Map<String, String>): SpotifySession {
        attachSession(SpotifySession(cookies = cookies))
        ensureAuth()
        return currentSession
    }

    suspend fun ensureAuth() {
        authMutex.withLock {
            if (!currentSession.accessTokenValid) {
                val token = auth.fetchAccessToken()
                currentSession =
                    currentSession.copy(
                        accessToken = token.accessToken,
                        accessTokenExpiresAtMs = token.accessTokenExpirationTimestampMs,
                    )
            }
            if (!currentSession.clientTokenValid) {
                currentSession =
                    currentSession.copy(
                        clientToken = auth.fetchClientToken(),
                        clientTokenFetchedAtMs = System.currentTimeMillis(),
                    )
            }
        }
    }

    suspend fun query(
        operation: String,
        hash: String,
        variables: JsonObject,
        maxResponseBytes: Int = 2 * 1024 * 1024,
        rateLimitRetries: Int = 2,
        suppressSessionExpired: Boolean = false,
    ): JsonObject {
        val body =
            buildJsonObject {
                put("variables", variables)
                put("operationName", operation)
                put(
                    "extensions",
                    buildJsonObject {
                        put(
                            "persistedQuery",
                            buildJsonObject {
                                put("version", 1)
                                put("sha256Hash", hash)
                            },
                        )
                    },
                )
            }

        // OkHttp tidak melempar untuk status non-2xx (berbeda dengan Ktor di
        // v14), jadi tidak ada mekanisme "expected status codes" — status
        // 429/401 ditangani manual di bawah.
        suspend fun postQuery(): SpotifyApiHttp.TextResponse {
            ensureAuth()
            val accessToken = currentSession.accessToken ?: throw SpotifyAuthException("not logged in (no access token)")
            val clientToken = currentSession.clientToken ?: throw SpotifyAuthException("not logged in (no client token)")
            return api.post(
                SpotifyQueries.PATHFINDER_URL,
                json.encodeToString(JsonObject.serializer(), body),
                maxBytes = maxResponseBytes,
                headers =
                    mapOf(
                        "Accept" to "application/json",
                        "Content-Type" to "application/json;charset=UTF-8",
                        "Origin" to "https://open.spotify.com",
                        "Referer" to "https://open.spotify.com/",
                        "app-platform" to "WebPlayer",
                        "spotify-app-version" to config.appVersion,
                        "Authorization" to "Bearer $accessToken",
                        "client-token" to clientToken,
                    ),
            )
        }

        var retries = 0
        var response = postQuery()
        while (response.status == 429 && retries++ < rateLimitRetries) {
            val retryAfterSeconds =
                response.headers["Retry-After"]?.toLongOrNull()?.takeIf { it >= 0 } ?: 2L
            delay(retryAfterSeconds.seconds)
            response = postQuery()
        }
        if (response.status == 429) {
            throw SpotifyException("$operation: Spotify rate limited", httpFailureLogged = true)
        }
        if (response.status == 401) {
            throw SpotifySessionExpiredException(
                "$operation: Spotify session expired",
                httpFailureLogged = !suppressSessionExpired,
            )
        }
        if (response.status >= 400) {
            throw SpotifyException("$operation: HTTP ${response.status}", httpFailureLogged = true)
        }
        val data =
            json
                .parseToJsonElement(
                    response.body ?: throw SpotifyException("$operation: response too large"),
                ).jsonObject
        if (data["errors"] != null) throw SpotifyException("$operation: Spotify returned GraphQL errors")
        return data
    }

    suspend fun listPlaylists(includeLikedSongs: Boolean = true): List<SpotifyPlaylistMeta> {
        val result = ArrayList<SpotifyPlaylistMeta>()
        var likedSongs: SpotifyPlaylistMeta? = null
        val visitedFolders = HashSet<String>()

        suspend fun collect(
            folderUri: String?,
            path: List<String>,
        ) {
            var offset = 0
            while (true) {
                val parsed =
                    parseLibraryPage(
                        query("libraryV3", SpotifyQueries.LIBRARY_V3_HASH, libraryV3Variables(folderUri, offset, PAGE_SIZE)),
                        path,
                    )
                result.addAll(parsed.playlists)
                for ((uri, name) in parsed.folders) {
                    if (visitedFolders.add(uri)) collect(uri, path + name)
                }
                if (parsed.likedSongs != null) likedSongs = parsed.likedSongs
                offset += PAGE_SIZE
                if (parsed.totalCount <= 0 || offset >= parsed.totalCount) break
            }
        }

        collect(null, emptyList())
        if (includeLikedSongs) likedSongs?.let { result.add(0, it) }
        Timber.tag(TAG).d("Loaded ${result.size} Spotify playlists")
        return result
    }

    suspend fun playlistTracks(
        uri: String,
        maxTracks: Int = Int.MAX_VALUE,
        onProgress: ((loaded: Int, total: Int) -> Unit)? = null,
    ): List<SpotifyTrackItem> {
        require(maxTracks >= 0) { "maxTracks must not be negative" }
        if (maxTracks == 0) return emptyList()
        val output = ArrayList<SpotifyTrackItem>()
        var offset = 0
        while (true) {
            val page =
                query(
                    "fetchPlaylistContents",
                    SpotifyQueries.PLAYLIST_CONTENTS_HASH,
                    buildJsonObject {
                        put("uri", uri)
                        put("offset", offset)
                        put("limit", PAGE_SIZE)
                        put("includeEpisodeContentRatingsV2", true)
                    },
                )
            val (items, total) = parsePlaylistContentsPage(page)
            output.addAll(items)
            onProgress?.invoke(output.size.coerceAtMost(maxTracks), total)
            offset += PAGE_SIZE
            if (items.isEmpty() || total <= 0 || output.size >= total || output.size >= maxTracks) break
        }
        return reindex(output.take(maxTracks))
    }

    suspend fun likedSongs(
        maxTracks: Int = Int.MAX_VALUE,
        onProgress: ((loaded: Int, total: Int) -> Unit)? = null,
    ): List<SpotifyTrackItem> {
        require(maxTracks >= 0) { "maxTracks must not be negative" }
        if (maxTracks == 0) return emptyList()
        val output = ArrayList<SpotifyTrackItem>()
        var offset = 0
        while (true) {
            val page =
                query(
                    "fetchLibraryTracks",
                    SpotifyQueries.LIBRARY_TRACKS_HASH,
                    buildJsonObject {
                        put("offset", offset)
                        put("limit", PAGE_SIZE)
                    },
                )
            val (items, total) = parseLikedSongsPage(page)
            output.addAll(items)
            onProgress?.invoke(output.size.coerceAtMost(maxTracks), total)
            offset += PAGE_SIZE
            if (items.isEmpty() || total <= 0 || output.size >= total || output.size >= maxTracks) break
        }
        return reindex(output.take(maxTracks))
    }

    suspend fun profile(): SpotifyAccountProfile =
        parseProfile(query("profileAttributes", SpotifyQueries.PROFILE_ATTRIBUTES_HASH, buildJsonObject {}))

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private fun libraryV3Variables(
        folderUri: String?,
        offset: Int,
        limit: Int,
    ): JsonObject =
        buildJsonObject {
            put("filters", buildJsonArray {})
            put("order", JsonNull)
            put("textFilter", "")
            put("features", buildJsonArray { SpotifyQueries.LIBRARY_FEATURES.forEach(::add) })
            put("limit", limit)
            put("offset", offset)
            put("flatten", false)
            put("expandedFolders", buildJsonArray {})
            put("folderUri", folderUri?.let(::JsonPrimitive) ?: JsonNull)
            put("includeFoldersWhenFlattening", true)
        }

    private fun reindex(tracks: List<SpotifyTrackItem>): List<SpotifyTrackItem> =
        tracks.mapIndexed { index, track -> track.copy(position = index + 1) }
}
