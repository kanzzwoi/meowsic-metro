/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import kotlinx.serialization.Serializable

/**
 * Model Spotify untuk impor akun, diadaptasi dari spotlib v14
 * (shared/.../meow/nyx/spotlib/Models.kt). Dipakai OkHttp + Room, bukan Ktor/KMP.
 */
@Serializable
enum class SpotifyPlaylistSource { PLAYLIST, LIKED_SONGS }

/** Satu playlist dari akun Spotify (metadata saja, tanpa track). */
@Serializable
data class SpotifyPlaylistMeta(
    val uri: String,
    val name: String,
    val owner: String? = null,
    val description: String = "",
    val image: String? = null,
    val folderPath: List<String> = emptyList(),
    val source: SpotifyPlaylistSource = SpotifyPlaylistSource.PLAYLIST,
    val trackCount: Int = 0,
)

/** Satu track dari playlist Spotify. */
@Serializable
data class SpotifyTrackItem(
    val uri: String,
    val name: String,
    val type: String = "Track",
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumUri: String? = null,
    val albumArtists: List<String> = emptyList(),
    val releaseDate: String? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val explicit: Boolean? = null,
    val isLocal: Boolean = false,
    val isPlayable: Boolean? = null,
    val addedAt: String? = null,
    val addedBy: String? = null,
    val position: Int = 0,
)

/** Profil akun Spotify (opsional, untuk menampilkan nama akun). */
@Serializable
data class SpotifyAccountProfile(
    val name: String? = null,
    val username: String? = null,
    val uri: String? = null,
    val avatar: String? = null,
)

/**
 * Sesi Spotify yang disimpan. Hanya berisi cookie sesi (sp_dc) + token
 * turunan (access token + client token). TIDAK pernah berisi password.
 * Disimpan sebagai JSON di DataStore (lihat SpotifyAccount).
 */
@Serializable
data class SpotifySession(
    val cookies: Map<String, String> = emptyMap(),
    val accessToken: String? = null,
    val accessTokenExpiresAtMs: Long? = null,
    val clientToken: String? = null,
    val clientTokenFetchedAtMs: Long? = null,
) {
    val hasLoginCookie: Boolean get() = cookies.containsKey("sp_dc")

    val accessTokenValid: Boolean
        get() =
            accessToken != null &&
                (accessTokenExpiresAtMs == null || System.currentTimeMillis() < accessTokenExpiresAtMs - 60_000)

    val clientTokenValid: Boolean
        get() =
            clientToken != null &&
                (clientTokenFetchedAtMs == null || System.currentTimeMillis() - clientTokenFetchedAtMs < 13L * 24 * 3600_000)
}

/** Galat umum Spotify. */
open class SpotifyException(
    message: String,
    cause: Throwable? = null,
    internal val httpFailureLogged: Boolean = false,
) : Exception(message, cause)

/** Galat autentikasi Spotify (login gagal / sesi tidak valid). */
open class SpotifyAuthException(
    message: String,
    cause: Throwable? = null,
    httpFailureLogged: Boolean = false,
) : SpotifyException(message, cause, httpFailureLogged)

/** Sesi Spotify kedaluwarsa — pengguna harus login ulang. */
class SpotifySessionExpiredException(
    message: String,
    cause: Throwable? = null,
    httpFailureLogged: Boolean = false,
) : SpotifyAuthException(message, cause, httpFailureLogged)
