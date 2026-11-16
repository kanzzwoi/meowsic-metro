/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalMenuState
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.AlbumListItem
import com.metrolist.music.ui.component.ArtistListItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.viewmodels.StatsViewModel

/**
 * Layar detail ala Metrolist v14 (StatisticsDetailScreen): daftar lengkap
 * satu seksi statistik (lagu/album/artis) yang dibuka dari tombol panah
 * di header carousel. "Putar semua" hanya ada untuk lagu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailScreen(
    navController: NavController,
    type: String,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val mostPlayedSongsStats by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val mostPlayedArtists by viewModel.filteredArtists.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.filteredAlbums.collectAsStateWithLifecycle()

    val orderedMostPlayedSongs =
        remember(mostPlayedSongsStats, mostPlayedSongs) {
            val songsById = mostPlayedSongs.associateBy { it.song.id }
            mostPlayedSongsStats.mapNotNull { statsSong -> songsById[statsSong.id] }
        }
    val statsBySongId = remember(mostPlayedSongsStats) { mostPlayedSongsStats.associateBy { it.id } }

    val title =
        when (type) {
            "albums" -> "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}"
            "artists" -> "${mostPlayedArtists.size} ${stringResource(id = R.string.artists)}"
            else -> "${mostPlayedSongsStats.size} ${stringResource(id = R.string.songs)}"
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.dismiss),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (type == "tracks" && orderedMostPlayedSongs.isNotEmpty()) {
                item(key = "play_all") {
                    Button(
                        onClick = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.most_played_songs),
                                    items = orderedMostPlayedSongs.map { it.toMediaMetadata().toMediaItem() },
                                ),
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.play_all))
                    }
                }
            }

            when (type) {
                "albums" -> {
                    itemsIndexed(
                        items = mostPlayedAlbums,
                        key = { _, album -> album.id },
                    ) { index, album ->
                        AlbumListItem(
                            album = album,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("album/${album.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = album,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ),
                        )
                    }
                }

                "artists" -> {
                    itemsIndexed(
                        items = mostPlayedArtists,
                        key = { _, artist -> artist.id },
                    ) { index, artist ->
                        ArtistListItem(
                            artist = artist,
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("artist/${artist.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ),
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = orderedMostPlayedSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        val stats = statsBySongId[song.id]
                        val artistName =
                            stats?.artistName
                                ?: stats?.artists?.joinToString(", ") { it.name }
                                ?: song.orderedArtists.joinToString(", ") { it.name }
                        SongListItem(
                            song = song,
                            subtitleOverride =
                                joinByBullet(
                                    artistName,
                                    pluralStringResource(
                                        R.plurals.n_time,
                                        stats?.songCountListened ?: 0,
                                        stats?.songCountListened ?: 0,
                                    ),
                                    makeTimeString(stats?.timeListened),
                                ),
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        endpoint = WatchEndpoint(song.id),
                                                        preloadItem = song.toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ),
                        )
                    }
                }
            }
        }
    }
}
