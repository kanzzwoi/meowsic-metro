/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import com.metrolist.music.playlistimport.ImportTrack
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pengelola impor akun Spotify, port dari v14
 * (shared/.../spotifyimport/SpotifyImportManager.kt). Bedanya dengan v14:
 * sesi disimpan lewat lambda suspend (DataStore Meowsic itu async,
 * sedangkan Settings v14 sinkron) dan log memakai Timber.
 *
 * Alur: [authenticate] (dengan cookie sp_dc dari WebView) →
 * [loadPlaylists] (daftar playlist akun, termasuk Lagu yang Disukai) →
 * [loadPlaylist]/[loadPlaylistsForImport] (ambil track tiap playlist) →
 * impor lewat [com.metrolist.music.playlistimport.PlaylistImport]
 * (lihat SpotifyPlaylistImport.kt).
 */
class SpotifyImportManager(
    private val clientFactory: () -> SpotifyClient = { SpotifyClient.create() },
    private val loadStoredSession: suspend () -> String? = { null },
    private val saveStoredSession: suspend (String) -> Unit = {},
) : AutoCloseable {
    @Volatile
    private var client: SpotifyClient? = null

    @Volatile
    private var closed = false

    val isAuthenticated: Boolean get() = client != null

    suspend fun authenticate(spDc: String) {
        require(spDc.isNotBlank()) { "Spotify login did not return a session cookie" }
        check(!closed) { "Spotify import is closed" }
        val candidate = clientFactory()
        try {
            candidate.loginWithSession(spDc)
            val previous =
                synchronized(this) {
                    check(!closed) { "Spotify import is closed" }
                    client.also { client = candidate }
                }
            previous?.close()
            persistSession(candidate.session)
        } catch (error: CancellationException) {
            candidate.close()
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Spotify authentication failed")
            candidate.close()
            throw error
        }
    }

    suspend fun loadPlaylists(): List<SpotifyPlaylistMeta> =
        try {
            val active = authenticatedClient()
            playlistsForImport(active.listPlaylists()).also { persistSession(active.session) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Loading Spotify playlists failed")
            invalidateExpiredSession(error)
            throw error
        }

    suspend fun loadPlaylist(
        playlist: SpotifyPlaylistMeta,
        onTrackProgress: ((loaded: Int, total: Int) -> Unit)? = null,
    ): LoadedPlaylist =
        try {
            val active = authenticatedClient()
            val tracks =
                when (playlist.source) {
                    SpotifyPlaylistSource.LIKED_SONGS -> {
                        active.likedSongs(MAX_IMPORT_TRACKS + 1, onProgress = onTrackProgress)
                    }

                    SpotifyPlaylistSource.PLAYLIST -> {
                        active.playlistTracks(
                            playlist.uri,
                            MAX_IMPORT_TRACKS + 1,
                            onProgress = onTrackProgress,
                        )
                    }
                }
            persistSession(active.session)
            val importTracks = tracksForImport(tracks.take(MAX_IMPORT_TRACKS))
            check(importTracks.isNotEmpty()) { "The Spotify playlist has no importable tracks" }
            LoadedPlaylist(
                uri = playlist.uri,
                name = playlist.name,
                image = playlist.image,
                tracks = importTracks,
                reachedTrackLimit = tracks.size > MAX_IMPORT_TRACKS,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Loading Spotify playlist failed")
            invalidateExpiredSession(error)
            throw error
        }

    suspend fun loadPlaylistsForImport(
        playlists: List<SpotifyPlaylistMeta>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        onTrackProgress: ((playlistIndex: Int, totalPlaylists: Int, loaded: Int, total: Int) -> Unit)? = null,
        onError: ((playlist: SpotifyPlaylistMeta, error: Exception) -> Unit)? = null,
    ): LoadedPlaylists {
        val selectedPlaylists = playlistsForImport(playlists)
        require(selectedPlaylists.isNotEmpty()) { "Choose at least one Spotify playlist" }
        val loadedPlaylists = mutableListOf<LoadedPlaylist>()
        var failedCount = 0
        val failures = mutableListOf<PlaylistFailure>()
        selectedPlaylists.forEachIndexed { index, playlist ->
            onProgress(index + 1, selectedPlaylists.size)
            try {
                loadedPlaylists +=
                    loadPlaylist(playlist) { loaded, total ->
                        onTrackProgress?.invoke(index + 1, selectedPlaylists.size, loaded, total)
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SpotifyAuthException) {
                throw error
            } catch (error: Exception) {
                failedCount++
                failures += PlaylistFailure(playlist.name, error.message ?: error::class.simpleName.orEmpty())
                onError?.invoke(playlist, error)
            }
        }
        check(loadedPlaylists.isNotEmpty()) { "The selected Spotify playlists have no importable tracks" }
        return LoadedPlaylists(loadedPlaylists, failedCount, failures)
    }

    suspend fun restorePersistedSession(): Boolean {
        check(!closed) { "Spotify import is closed" }
        if (client != null) return true
        val stored = loadStoredSession()?.takeIf(String::isNotBlank) ?: return false
        val session =
            runCatching { sessionJson.decodeFromString<SpotifySession>(stored) }
                .getOrElse {
                    saveStoredSession("")
                    return false
                }
        if (!session.hasLoginCookie) {
            saveStoredSession("")
            return false
        }
        val candidate = clientFactory()
        try {
            candidate.attachSession(session)
            candidate.ensureAuth()
            val previous =
                synchronized(this) {
                    check(!closed) { "Spotify import is closed" }
                    client.also { client = candidate }
                }
            previous?.close()
            persistSession(candidate.session)
            return true
        } catch (error: CancellationException) {
            candidate.close()
            throw error
        } catch (error: SpotifySessionExpiredException) {
            candidate.close()
            saveStoredSession("")
            return false
        } catch (error: Exception) {
            candidate.close()
            return false
        }
    }

    fun logout() {
        val previous = synchronized(this) { client.also { client = null } }
        previous?.close()
        // Hapus sesi tersimpan di latar (tidak boleh melempar dari logout()).
        logoutScope.launch { runCatching { saveStoredSession("") } }
    }

    override fun close() {
        val closingClient =
            synchronized(this) {
                closed = true
                client.also { client = null }
            }
        closingClient?.close()
    }

    private suspend fun authenticatedClient(): SpotifyClient =
        client ?: restorePersistedSession().let { client } ?: error("Sign in to Spotify first")

    private fun invalidateExpiredSession(error: Throwable) {
        if (error is SpotifySessionExpiredException) {
            val expiredClient = synchronized(this) { client.also { client = null } }
            expiredClient?.close()
            logoutScope.launch { runCatching { saveStoredSession("") } }
        }
    }

    private suspend fun persistSession(session: SpotifySession) {
        saveStoredSession(sessionJson.encodeToString(session))
    }

    /** Satu playlist yang sudah dimuat beserta tracknya (siap diimpor). */
    data class LoadedPlaylist(
        val uri: String,
        val name: String,
        val image: String?,
        val tracks: List<ImportTrack>,
        val reachedTrackLimit: Boolean,
    )

    data class LoadedPlaylists(
        val playlists: List<LoadedPlaylist>,
        val failedCount: Int,
        val failures: List<PlaylistFailure> = emptyList(),
    )

    data class PlaylistFailure(
        val playlistName: String,
        val reason: String,
    )

    companion object {
        private const val TAG = "SpotifyImport"
        private val sessionJson = Json { ignoreUnknownKeys = true }
        private const val MAX_IMPORT_TRACKS = 100_000

        // Scope kecil untuk penghapusan sesi di latar (logout / sesi kedaluwarsa).
        private val logoutScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

        internal fun tracksForImport(tracks: List<SpotifyTrackItem>): List<ImportTrack> =
            tracks
                .filter { track ->
                    track.type.equals("Track", ignoreCase = true) && !track.isLocal
                }.mapNotNull { track ->
                    track.name.trim().takeIf(String::isNotEmpty)?.let { title ->
                        ImportTrack(title, track.artists.joinToString(", ").trim())
                    }
                }

        internal fun playlistsForImport(playlists: List<SpotifyPlaylistMeta>): List<SpotifyPlaylistMeta> =
            playlists.distinctBy(SpotifyPlaylistMeta::uri)
    }
}

/** Gerbang supaya hanya satu operasi impor Spotify yang berjalan. */
object SpotifyImportOperationGate {
    private val active = AtomicBoolean()

    fun tryStart(): Boolean = active.compareAndSet(false, true)

    fun finish() {
        active.set(false)
    }
}
