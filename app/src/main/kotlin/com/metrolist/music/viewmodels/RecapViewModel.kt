/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SongWithStats
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

/** Satu lagu di Raport Meowsic. */
data class RecapSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val playCount: Int,
)

/** Satu artis di Raport Meowsic, beserta lagu teratasnya. */
data class RecapArtist(
    val id: String,
    val name: String,
    // ID channel YTM asli kalau ada di DB (bisa null); id di atas
    // tidak selalu ID YTM — lihat fetchArtistThumbnail.
    val channelId: String?,
    val thumbnailUrl: String?,
    val playCount: Int,
    val topSong: RecapSong,
)

/** Satu album di Raport Meowsic. */
data class RecapAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val playCount: Int,
)

/** Semua data Raport Meowsic untuk satu tahun. */
data class RecapData(
    val topSongs: List<RecapSong>,
    val totalTimeMs: Long,
    // Jumlah lagu BERBEDA yang diputar sepanjang tahun (bukan top 10):
    // dipakai statistik "Total Lagu" di slide lagu. Meowsic tidak
    // mencatat genre per lagu, jadi ini pengganti "Genre Teratas".
    val totalSongs: Int,
    // Jumlah artis BERBEDA yang diputar sepanjang tahun (sebelum
    // take(5)): dipakai statistik "Total Artis" di slide artis.
    val totalArtists: Int,
    val topArtists: List<RecapArtist>,
    val topAlbums: List<RecapAlbum>,
)

sealed interface RecapUiState {
    data object Loading : RecapUiState
    data object Empty : RecapUiState
    data class Ready(val data: RecapData) : RecapUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    // Agregasi Raport 2026 dari tabel Event (lewat query statistik yang
    // sama dengan layar Statistik): semua lagu yang diputar sepanjang
    // 2026, lalu dikelompokkan di memori per lagu/artis/album.
    val recapState: StateFlow<RecapUiState> =
        context.dataStore.data
            .map { it[HideVideoSongsKey] ?: false }
            .distinctUntilChanged()
            .flatMapLatest { hideVideoSongs ->
                database
                    .mostPlayedSongsStats(
                        fromTimeStamp = LocalDateTime.of(2026, 1, 1, 0, 0),
                        limit = -1,
                        toTimeStamp = LocalDateTime.of(2027, 1, 1, 0, 0),
                    ).map { songs ->
                        buildRecap(
                            if (hideVideoSongs) songs.filter { !it.isVideo } else songs,
                        )
                    }.transform { state ->
                        emit(state)
                        // Foto artis yang belum ada di DB diambil dari
                        // halaman artisnya (bukan dari lagu), lalu
                        // disimpan ke DB supaya layar Statistik ikut
                        // kebagian fotonya.
                        if (state is RecapUiState.Ready) {
                            val enriched = enrichArtistThumbnails(state.data)
                            if (enriched != state.data) emit(RecapUiState.Ready(enriched))
                        }
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, RecapUiState.Loading)

    private fun buildRecap(songs: List<SongWithStats>): RecapUiState {
        if (songs.isEmpty()) return RecapUiState.Empty

        val topSongs =
            songs
                .sortedByDescending { it.songCountListened }
                .take(10)
                .map { it.toRecapSong() }

        // Satu lagu yang punya beberapa artis dihitung penuh untuk tiap
        // artisnya, sama seperti statistik artis di layar Statistik.
        // Jumlah grup artis (sebelum take(5)) = total artis berbeda
        // sepanjang tahun, dipakai statistik "Total Artis".
        val artistGroups =
            songs
                .flatMap { song -> song.artists.map { artist -> artist to song } }
                .groupBy({ it.first.id }, { it.second })
        val totalArtists = artistGroups.size

        val topArtists =
            artistGroups
                .map { (artistId, artistSongs) ->
                    val artist = artistSongs.first().artists.first { it.id == artistId }
                    val topSong =
                        artistSongs.maxByOrNull { it.songCountListened } ?: artistSongs.first()
                    RecapArtist(
                        id = artistId,
                        name = artist.name,
                        channelId = artist.channelId,
                        thumbnailUrl = artist.thumbnailUrl,
                        playCount = artistSongs.sumOf { it.songCountListened },
                        topSong = topSong.toRecapSong(),
                    )
                }.sortedByDescending { it.playCount }
                .take(5)

        // Album dihitung dari albumId/albumName lagu (bukan dari baris
        // album + song_album_map), sama seperti statistik album di layar
        // Statistik. Lagu tanpa album dilewati.
        val topAlbums =
            songs
                .mapNotNull { song ->
                    val albumId = song.albumId
                    val albumName = song.albumName
                    if (albumId.isNullOrBlank() || albumName.isNullOrBlank()) null
                    else Triple(albumId, albumName, song)
                }.groupBy({ it.first }, { it })
                .map { (albumId, entries) ->
                    val albumSongs = entries.map { it.third }
                    val topSong =
                        albumSongs.maxByOrNull { it.songCountListened } ?: albumSongs.first()
                    RecapAlbum(
                        id = albumId,
                        title = entries.first().second,
                        artist = topSong.artistName
                            ?: topSong.artists.joinToString(", ") { it.name },
                        thumbnailUrl = topSong.thumbnailUrl,
                        playCount = albumSongs.sumOf { it.songCountListened },
                    )
                }.sortedByDescending { it.playCount }
                .take(5)

        return RecapUiState.Ready(
            RecapData(
                topSongs = topSongs,
                totalTimeMs = songs.sumOf { it.timeListened ?: 0L },
                // Daftar lengkap 2026 (hasil DAO limit -1), bukan top 10.
                totalSongs = songs.size,
                totalArtists = totalArtists,
                topArtists = topArtists,
                topAlbums = topAlbums,
            ),
        )
    }

    // Foto artis yang belum ada di DB diambil dari halaman artis
    // YouTube Music — mekanisme yang sama dengan ArtistViewModel
    // (YouTube.artist -> page.artist.thumbnail) — lalu disimpan ke
    // baris artis di DB. Artis ya foto artisnya, bukan artwork lagu.
    // Kalau fetch gagal, thumbnail dibiarkan kosong seperti
    // sebelumnya; tidak ada fallback lain.
    //
    // ID artis di DB TIDAK selalu ID YTM: kalau data lagu dari YTM
    // tidak membawa id artis, insert lagu membuat id lokal "LA..."
    // (DatabaseDao.insert) yang tidak bisa di-browse. Karena itu
    // urutannya: channelId dulu (id YTM asli kalau ada), lalu id
    // kalau bentuknya ID YTM (UC...), terakhir cari artisnya lewat
    // search nama — cara yang sama dengan YouTube.resolveArtistIds.
    private suspend fun enrichArtistThumbnails(data: RecapData): RecapData =
        withContext(Dispatchers.IO) {
            if (data.topArtists.none { it.thumbnailUrl.isNullOrBlank() }) return@withContext data
            val artists =
                data.topArtists.map { artist ->
                    if (!artist.thumbnailUrl.isNullOrBlank()) return@map artist
                    val (fetchedThumbnail, resolvedChannelId) = fetchArtistThumbnail(artist)
                    if (fetchedThumbnail == null) return@map artist
                    try {
                        val existing = database.artist(artist.id).firstOrNull()?.artist
                        if (existing != null) {
                            database.update(
                                existing.copy(
                                    thumbnailUrl = fetchedThumbnail,
                                    channelId = resolvedChannelId ?: existing.channelId,
                                    lastUpdateTime = LocalDateTime.now(),
                                ),
                            )
                        }
                    } catch (_: Exception) {
                        // DB gagal ditulis: foto tetap dipakai di slide ini.
                    }
                    artist.copy(thumbnailUrl = fetchedThumbnail)
                }
            data.copy(topArtists = artists)
        }

    // Kembalikan (url foto, channel id YTM yang berhasil dipakai).
    // null kalau semua jalur gagal — alasannya ditulis ke playback.log
    // (tag "recap") supaya bisa dibaca, bukan ditebak.
    private suspend fun fetchArtistThumbnail(artist: RecapArtist): Pair<String?, String?> {
        val browseId =
            artist.channelId?.takeIf { it.isNotBlank() }
                ?: artist.id.takeIf { it.startsWith("UC") }
        if (browseId != null) {
            val thumbnail =
                YouTube.artist(browseId).getOrNull()?.artist?.thumbnail
                    ?.takeIf { it.isNotBlank() }
            if (thumbnail != null) return thumbnail to browseId
            com.metrolist.music.playback.PlaybackLog.log(
                "recap",
                "foto artis ${artist.name}: browse $browseId gagal/kosong, coba search nama",
            )
        } else {
            com.metrolist.music.playback.PlaybackLog.log(
                "recap",
                "foto artis ${artist.name}: id=${artist.id} channelId=${artist.channelId} bukan ID YTM, coba search nama",
            )
        }
        // ID lokal (LA...) atau browse gagal: cari artisnya lewat
        // search nama, persis aturan YouTube.resolveArtistIds
        // (judul harus sama persis, abaikan kapital).
        val foundId =
            YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST)
                .getOrNull()?.items
                ?.filterIsInstance<com.metrolist.innertube.models.ArtistItem>()
                ?.firstOrNull { it.title.trim().equals(artist.name.trim(), ignoreCase = true) }
                ?.id
        if (foundId == null) {
            com.metrolist.music.playback.PlaybackLog.log(
                "recap",
                "foto artis ${artist.name}: search nama tidak ketemu",
            )
            return null to null
        }
        if (foundId == browseId) return null to null
        val thumbnail =
            YouTube.artist(foundId).getOrNull()?.artist?.thumbnail
                ?.takeIf { it.isNotBlank() }
        if (thumbnail == null) {
            com.metrolist.music.playback.PlaybackLog.log(
                "recap",
                "foto artis ${artist.name}: browse hasil search $foundId kosong",
            )
        }
        return thumbnail to foundId
    }

    private fun SongWithStats.toRecapSong() =
        RecapSong(
            id = id,
            title = title,
            artist = artistName ?: artists.joinToString(", ") { it.name },
            thumbnailUrl = thumbnailUrl,
            playCount = songCountListened,
        )
}
