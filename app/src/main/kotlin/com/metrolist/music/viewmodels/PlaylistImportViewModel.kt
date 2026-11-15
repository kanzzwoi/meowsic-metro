/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playlistimport.PlaylistImport
import com.metrolist.music.playlistimport.SpotifyPlaylistImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistImportViewModel
@Inject
constructor(
    val database: MusicDatabase,
) : ViewModel() {
    sealed interface Status {
        data object Idle : Status

        data object Fetching : Status

        data class Resolving(
            val done: Int,
            val total: Int,
        ) : Status

        data class Importing(
            val done: Int,
            val total: Int,
        ) : Status

        data class Done(
            val playlistId: String,
            val title: String,
            val matched: Int,
            val total: Int,
        ) : Status

        data class Error(
            val message: String,
        ) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun importSpotify(url: String) {
        val current = _status.value
        if (current is Status.Fetching || current is Status.Resolving || current is Status.Importing) {
            return
        }
        viewModelScope.launch {
            try {
                _status.value = Status.Fetching
                val playlist =
                    SpotifyPlaylistImporter.loadPlaylist(url).getOrElse { throw it }
                val matches =
                    PlaylistImport.resolveTracks(playlist.tracks) { done, total ->
                        _status.value = Status.Resolving(done, total)
                    }
                if (matches.isEmpty()) {
                    _status.value =
                        Status.Error("Tidak ada lagu yang berhasil dicocokkan di YouTube Music")
                    return@launch
                }
                val playlistId =
                    PlaylistImport.importToNewPlaylist(
                        database,
                        playlist.title,
                        playlist.coverUrl,
                        matches,
                    ) { done, total ->
                        _status.value = Status.Importing(done, total)
                    }
                _status.value =
                    Status.Done(playlistId, playlist.title, matches.size, playlist.tracks.size)
            } catch (e: Exception) {
                _status.value = Status.Error(e.message ?: "Gagal mengimpor playlist")
            }
        }
    }

    fun reset() {
        _status.value = Status.Idle
    }
}
