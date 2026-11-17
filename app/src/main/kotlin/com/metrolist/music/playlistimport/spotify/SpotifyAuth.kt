/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.UUID

private const val TOKEN_URL = "https://open.spotify.com/api/token"
private const val CLIENT_TOKEN_URL = "https://clienttoken.spotify.com/v1/clienttoken"
private const val OPEN_HOST = "open.spotify.com"

/**
 * Pertukaran token Spotify (access token + client token), port dari spotlib
 * v14 (shared/.../meow/nyx/spotlib/Auth.kt) dengan OkHttp. Access token
 * diambil lewat /api/token memakai kode TOTP; client token lewat
 * clienttoken.spotify.com memakai device id dari cookie sp_t (atau UUID
 * acak kalau cookie itu tidak ada).
 */
internal class SpotifyAuth(
    private val http: SpotifyApiHttp,
    private val config: SpotifyConfig,
    private val jar: SpotifyCookieJar,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAccessToken(): TokenResponse {
        val code = SpotifyTotp.code()
        val url = "$TOKEN_URL?reason=init&productType=web-player&totp=$code&totpServer=$code&totpVer=${SpotifyTotp.version()}"
        val response =
            http.get(
                url,
                headers =
                    mapOf(
                        "Accept" to "*/*",
                        "Referer" to "https://open.spotify.com/",
                    ),
            )
        if (response.status >= 400) {
            val message = "/api/token failed: HTTP ${response.status}"
            if (response.status == 401 || response.status == 403) {
                throw SpotifySessionExpiredException(message, httpFailureLogged = true)
            }
            throw SpotifyAuthException(message, httpFailureLogged = true)
        }
        val body =
            json
                .parseToJsonElement(
                    response.body ?: throw SpotifyAuthException("token response too large"),
                ).jsonObject
        val token = json.decodeFromJsonElement(TokenResponse.serializer(), body)
        if (token.isAnonymous && !config.allowAnonymous) {
            throw SpotifySessionExpiredException("Spotify reports the session is anonymous. Sign in to Spotify and try again.")
        }
        if (token.accessToken == null) throw SpotifyAuthException("/api/token returned no access token")
        Timber.tag(TAG).d("Access token exchange succeeded")
        return token
    }

    suspend fun fetchClientToken(): String {
        val deviceId = jar.cookieValue(OPEN_HOST, "sp_t") ?: UUID.randomUUID().toString()
        val payload =
            buildJsonObject {
                put(
                    "client_data",
                    buildJsonObject {
                        put("client_version", config.appVersion)
                        put("client_id", config.webClientId)
                        put(
                            "js_sdk_data",
                            buildJsonObject {
                                put("device_brand", "unknown")
                                put("device_model", "unknown")
                                put("os", "android")
                                put("os_version", "unknown")
                                put("device_id", deviceId)
                                put("device_type", "computer")
                            },
                        )
                    },
                )
            }
        val response =
            http.post(
                CLIENT_TOKEN_URL,
                json.encodeToString(JsonObject.serializer(), payload),
                headers =
                    mapOf(
                        "Accept" to "application/json",
                        "Origin" to "https://open.spotify.com",
                        "Referer" to "https://open.spotify.com/",
                    ),
            )
        if (response.status >= 400) {
            throw SpotifyAuthException("clienttoken failed: HTTP ${response.status}", httpFailureLogged = true)
        }
        val body =
            json
                .parseToJsonElement(
                    response.body ?: throw SpotifyAuthException("token response too large"),
                ).jsonObject
        val token =
            json.decodeFromJsonElement(ClientTokenResponse.serializer(), body).granted_token?.token
                ?: throw SpotifyAuthException("clienttoken returned no token")
        Timber.tag(TAG).d("Client token exchange succeeded")
        return token
    }

    private companion object {
        const val TAG = "SpotifyApi"
    }
}

@Serializable
internal data class TokenResponse(
    val accessToken: String? = null,
    val accessTokenExpirationTimestampMs: Long? = null,
    val isAnonymous: Boolean = true,
)

@Serializable
internal data class ClientTokenResponse(
    val response_type: String? = null,
    val granted_token: GrantedToken? = null,
)

@Serializable
internal data class GrantedToken(
    val token: String? = null,
)
