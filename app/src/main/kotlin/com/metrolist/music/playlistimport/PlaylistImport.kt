/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport

import androidx.room.withTransaction
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pencocokan lagu impor ke YouTube Music + pembuatan playlist lokal,
 * ala Metrolist v14 (PlaylistImportManager, versi sederhana): tiap lagu
 * dicari "judul artis" di YouTube Music, lalu dipilih yang judulnya sama
 * persis (setelah dinormalisasi) dan artisnya cocok.
 */
object PlaylistImport {
    private const val RESOLVE_CONCURRENCY = 4

    suspend fun resolveTracks(
        tracks: List<ImportTrack>,
        onProgress: (resolved: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Pair<ImportTrack, SongItem>> =
        coroutineScope {
            val completed = AtomicInteger(0)
            tracks.chunked(RESOLVE_CONCURRENCY).flatMap { chunk ->
                chunk.map { track ->
                    async {
                        val match =
                            try {
                                resolveTrack(track)
                            } catch (_: Exception) {
                                null
                            }
                        onProgress(completed.incrementAndGet(), tracks.size)
                        match?.let { track to it }
                    }
                }.awaitAll()
            }.filterNotNull()
        }

    private suspend fun resolveTrack(track: ImportTrack): SongItem? {
        val query =
            listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val result =
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?: return null
        return bestTrackMatch(track, result.items.filterIsInstance<SongItem>())
    }

    private fun bestTrackMatch(
        track: ImportTrack,
        candidates: List<SongItem>,
    ): SongItem? {
        fun normalized(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
        val title = normalized(track.title)
        if (title.isEmpty()) return null
        val artist = normalized(track.artist)
        return candidates.firstOrNull { song ->
            normalized(song.title) == title &&
                (
                    artist.isEmpty() ||
                        normalized(song.artists.joinToString { it.name }) == artist ||
                        track.artist.split(',').any { sourceArtist ->
                            song.artists.any { normalized(it.name) == normalized(sourceArtist) }
                        }
                    )
        }
    }

    /**
     * Buat playlist lokal baru berisi lagu-lagu yang berhasil dicocokkan.
     * Mengembalikan id playlist. Lagu yang tidak ketemu dilewati.
     */
    suspend fun importToNewPlaylist(
        database: MusicDatabase,
        title: String,
        coverUrl: String?,
        matches: List<Pair<ImportTrack, SongItem>>,
        onProgress: (imported: Int, total: Int) -> Unit = { _, _ -> },
    ): String {
        val entity =
            PlaylistEntity(
                name = title,
                bookmarkedAt = LocalDateTime.now(),
                thumbnailUrl = coverUrl,
                isEditable = true,
            )
        database.withTransaction {
            insert(entity)
            matches.forEachIndexed { index, (_, song) ->
                insert(song.toMediaMetadata())
                insert(
                    PlaylistSongMap(
                        songId = song.id,
                        playlistId = entity.id,
                        position = index,
                    ),
                )
                onProgress(index + 1, matches.size)
            }
            updatePlaylistLastUpdated(entity.id)
        }
        return entity.id
    }
}
