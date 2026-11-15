/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.RecentPlayedSearchSongsKey
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Lagu yang diputar dari hasil pencarian, ala Metrolist v14. */
@Serializable
data class RecentSearchSong(
    val id: String,
    val title: String,
    val artists: List<RecentSearchArtist> = emptyList(),
    val thumbnailUrl: String? = null,
) {
    val artistNames: String
        get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class RecentSearchArtist(
    val id: String?,
    val name: String,
)

private val recentSearchSongsJson = Json { ignoreUnknownKeys = true }
private const val MAX_RECENT_SEARCH_SONGS = 12

fun parseRecentSearchSongs(json: String?): List<RecentSearchSong> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching { recentSearchSongsJson.decodeFromString<List<RecentSearchSong>>(json) }
        .getOrDefault(emptyList())
}

/** Catat lagu yang baru diputar dari pencarian (paling baru di depan, maks 12). */
suspend fun recordRecentSearchSong(
    context: Context,
    song: SongItem,
) {
    val current =
        parseRecentSearchSongs(context.dataStore.data.first()[RecentPlayedSearchSongsKey])
    val updated =
        (
            listOf(
                RecentSearchSong(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.map { RecentSearchArtist(id = it.id, name = it.name) },
                    thumbnailUrl = song.thumbnail,
                ),
            ) + current.filterNot { it.id == song.id }
            ).take(MAX_RECENT_SEARCH_SONGS)
    context.dataStore.edit { it[RecentPlayedSearchSongsKey] = recentSearchSongsJson.encodeToString(updated) }
}

suspend fun removeRecentSearchSong(
    context: Context,
    songId: String,
) {
    val current =
        parseRecentSearchSongs(context.dataStore.data.first()[RecentPlayedSearchSongsKey])
    context.dataStore.edit {
        it[RecentPlayedSearchSongsKey] =
            recentSearchSongsJson.encodeToString(current.filterNot { song -> song.id == songId })
    }
}

suspend fun clearRecentSearchSongs(context: Context) {
    context.dataStore.edit { it.remove(RecentPlayedSearchSongsKey) }
}

fun RecentSearchSong.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
        duration = -1,
        thumbnailUrl = thumbnailUrl,
    )

/** Kartu lagu di baris "Dari pencarian terbaru": sampul, judul, artis • jumlah putar. */
@Composable
fun RecentSearchSongCard(
    song: RecentSearchSong,
    playCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(130.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(4.dp),
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(122.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val subtitle =
            if (playCount > 0) "${song.artistNames} • $playCount×"
            else song.artistNames
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
