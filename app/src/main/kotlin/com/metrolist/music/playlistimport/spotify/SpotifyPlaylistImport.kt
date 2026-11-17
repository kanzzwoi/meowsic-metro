/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playlistimport.PlaylistImport

/** Hasil impor satu playlist akun Spotify ke pustaka Meowsic. */
data class SpotifyAccountImportResult(
    val playlistId: String,
    val title: String,
    val matched: Int,
    val total: Int,
)

/**
 * Impor satu playlist akun Spotify yang sudah dimuat ([SpotifyImportManager.LoadedPlaylist])
 * ke pustaka Meowsic: cocokkan tiap track ke lagu YouTube Music lewat
 * [PlaylistImport.resolveTracks] (logika pencocokan yang sama dengan impor
 * Spotify publik), lalu simpan sebagai playlist baru lewat
 * [PlaylistImport.importToNewPlaylist].
 *
 * Dipanggil setelah pengguna menekan Continue di pratinjau. Untuk beberapa
 * playlist, panggil satu per satu (satu playlist baru per playlist Spotify).
 *
 * @throws IllegalStateException kalau tidak ada lagu yang berhasil dicocokkan.
 */
suspend fun importSpotifyAccountPlaylist(
    database: MusicDatabase,
    loaded: SpotifyImportManager.LoadedPlaylist,
    onResolving: (done: Int, total: Int) -> Unit = { _, _ -> },
    onImporting: (done: Int, total: Int) -> Unit = { _, _ -> },
): SpotifyAccountImportResult {
    val matches = PlaylistImport.resolveTracks(loaded.tracks, onResolving)
    if (matches.isEmpty()) {
        throw IllegalStateException("Tidak ada lagu yang berhasil dicocokkan di YouTube Music")
    }
    val playlistId =
        PlaylistImport.importToNewPlaylist(
            database,
            loaded.name,
            loaded.image,
            matches,
            onImporting,
        )
    return SpotifyAccountImportResult(playlistId, loaded.name, matches.size, loaded.tracks.size)
}
