/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import com.metrolist.music.playlistimport.ImportedPlaylist

/**
 * Model bersama untuk impor playlist dari AKUN provider (Apple Music,
 * Deezer, Tidal, Amazon Music), ala Metrolist v14: layar login WebView
 * menangkap token sesi, lalu daftar playlist akun dimuat dan dipilih
 * pengguna. Beda dengan impor tautan publik (ImportProvider): di sini
 * yang diimpor adalah playlist milik akun yang login, termasuk "Lagu
 * yang disukai" (Liked Songs) sebagai entri sintetis di urutan pertama.
 */
data class AccountPlaylist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
)

/**
 * Satu playlist akun yang bisa dipilih. [loadTracks] memuat lagu-lagunya
 * (judul + artis) dari provider — pemanggil lalu mencocokkannya ke
 * YouTube Music lewat PlaylistImport.resolveTracks dan menyimpannya
 * lewat PlaylistImport.importToNewPlaylist, sama seperti impor tautan
 * publik. Manager yang membuat choice ini harus tetap hidup (belum
 * close()) sampai loadTracks selesai, karena choice ini menangkapnya.
 */
data class AccountPlaylistChoice(
    val playlist: AccountPlaylist,
    val loadTracks: suspend (onProgress: (loaded: Int, total: Int?) -> Unit) -> Result<ImportedPlaylist>,
)

/**
 * Sumber playlist akun untuk satu provider. Implementasi menahan token
 * sesi (authenticate) dan client HTTP-nya sendiri; [close] membebaskan
 * client. Polanya sama dengan v14 (DeezerImportManager /
 * TidalImportManager / AmazonMusicImportManager): authenticate dulu,
 * lalu loadPlaylists, lalu loadTracks per playlist yang dipilih.
 */
interface AccountPlaylistSource {
    suspend fun loadPlaylists(likedSongsName: String = DEFAULT_LIKED_SONGS_NAME): Result<List<AccountPlaylistChoice>>

    fun close()
}

/** Batas ala v14 (PlaylistImportManager.MAX_PLAYLIST_TRACKS). */
const val MAX_ACCOUNT_PLAYLISTS = 500
const val MAX_ACCOUNT_TRACKS = 100_000

/** Nama default entri sintetis lagu yang disukai (lihat laporan: butuh string). */
const val DEFAULT_LIKED_SONGS_NAME = "Liked Songs"

/** Judul fallback kalau provider tidak mengembalikan nama playlist. */
const val DEFAULT_PLAYLIST_NAME = "Playlist"
