/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hasil impor playlist YouTube / YouTube Music: judul playlist + lagu yang
 * berhasil dicocokkan + jumlah video yang dilewati.
 */
data class YouTubeImportResult(
    val title: String,
    val coverUrl: String?,
    /**
     * Pasangan (track sumber, lagu YouTube Music) — bentuk yang sama dengan
     * yang dipakai PlaylistImport.importToNewPlaylist, jadi hasilnya bisa
     * langsung disimpan sebagai playlist baru tanpa pencocokan ulang.
     */
    val matches: List<Pair<ImportTrack, SongItem>>,
    /** Jumlah video di playlist menurut header (bila tersedia). */
    val totalVideos: Int,
    /** Jumlah video yang tidak bisa diimpor (tidak bisa dibaca / tidak tersedia). */
    val skipped: Int,
)

/**
 * Impor playlist YouTube / YouTube Music publik tanpa login, ala Metrolist v14
 * (PlaylistImportManager.loadYouTubePlaylist + parsePlaylistId).
 *
 * Bedanya dengan importer Spotify/Apple/dll: video playlist YouTube SUDAH
 * berupa lagu (SongItem dengan videoId), jadi tidak perlu dicari ulang lewat
 * judul+artis — videonya langsung jadi lagunya, persis seperti v14 (yang
 * mengembalikan SongItem dari getPlaylist + playlistSongOccurrences).
 * Pencarian ulang justru bisa salah cocok atau menghilangkan video.
 */
object YouTubePlaylistImporter {
    fun canHandle(input: String): Boolean {
        val lower = input.lowercase().trim()
        return lower.contains("youtube.com") ||
            lower.contains("youtu.be") ||
            parsePlaylistId(input) != null
    }

    /**
     * Ambil ID playlist dari URL YouTube / YouTube Music atau ID mentah,
     * verbatim dari v14 (PlaylistImportManager.parsePlaylistId): parameter
     * `list=` di URL, atau string alfanumerik 10+ karakter; awalan "VL"
     * dibuang karena YouTube.playlist() menambahkannya sendiri.
     */
    fun parsePlaylistId(input: String): String? {
        val trimmed = input.trim()
        val fromQuery = Regex("[?&]list=([^&#]+)").find(trimmed)?.groupValues?.get(1)
        return (fromQuery ?: trimmed.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,}")) })?.removePrefix("VL")
    }

    suspend fun loadPlaylist(input: String): Result<YouTubeImportResult> =
        try {
            withContext(Dispatchers.IO) {
                val playlistId = parsePlaylistId(input)
                    ?: throw IllegalArgumentException("Tautan YouTube tidak valid")
                val page = YouTube.playlist(playlistId).completed().getOrThrow()
                val title = page.playlist.title.trim().ifBlank { playlistId }
                val coverUrl = page.playlist.thumbnail?.takeIf { it.isNotBlank() }
                    ?: page.songs.firstOrNull()?.thumbnail
                val songs = page.songs
                if (songs.isEmpty()) {
                    throw IllegalStateException("Tidak ada lagu yang bisa diimpor dari tautan ini")
                }
                val totalVideos = parseSongCount(page.playlist.songCountText) ?: songs.size
                val skipped = (totalVideos - songs.size).coerceAtLeast(0)
                val matches = songs.map { song ->
                    ImportTrack(
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                    ) to song
                }
                Result.success(
                    YouTubeImportResult(
                        title = title,
                        coverUrl = coverUrl,
                        matches = matches,
                        totalVideos = totalVideos,
                        skipped = skipped,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Ambil angka pertama dari teks jumlah lagu header ("123 songs", "1,234 lagu"). */
    private fun parseSongCount(songCountText: String?): Int? {
        if (songCountText.isNullOrBlank()) return null
        val digits = Regex("""[\d,]+""").find(songCountText)?.value?.replace(",", "") ?: return null
        return digits.toIntOrNull()
    }
}
