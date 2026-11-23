/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.viewmodels.RecapArtist
import com.metrolist.music.viewmodels.RecapData
import com.metrolist.music.viewmodels.RecapSong
import com.metrolist.music.viewmodels.RecapUiState
import com.metrolist.music.viewmodels.RecapViewModel

// Teks-teks Raport Meowsic (konten berbahasa Indonesia sesuai permintaan
// pengguna), sengaja ditulis sebagai konstanta di sini, bukan string
// resource — raport ini memang berbahasa Indonesia.
private const val RECAP_COVER_TITLE = "RAPORT MEOWSIC TAHUN 2026"
private const val RECAP_WEBSITE = "meowsic.web.id"
private const val RECAP_TOP_SONGS_TITLE = "10 Lagu Favorit"
private const val RECAP_TOP_ARTISTS_TITLE = "5 Artis Favorit"
private const val RECAP_TOP_ALBUMS_TITLE = "5 Album Favorit"
private const val RECAP_CLOSING_TEXT =
    "RAPORT ANDA TELAH USAI. AYOK PUSH LAGI LAGUNYA UNTUK TAHUN DEPAN."
private const val RECAP_EMPTY_TEXT = "Belum ada data 2026"

private const val SLIDE_DURATION_MS = 5000
private const val SLIDE_COUNT = 7

private val RECAP_BACKGROUND_TOP = Color(0xFF0C1E40)
private val RECAP_BACKGROUND_BOTTOM = Color(0xFF030710)

/**
 * Raport tahunan Meowsic ala story (Spotify Wrapped): layar penuh,
 * segmen progres di atas, maju otomatis tiap 5 detik, ketuk kanan =
 * slide berikut, ketuk kiri = slide sebelumnya, tahan = jeda timer.
 * Dibuka dari tombol Bagikan di layar Statistik.
 */
@Composable
fun RecapScreen(
    onDismiss: () -> Unit,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val recapState by viewModel.recapState.collectAsStateWithLifecycle()
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(RECAP_BACKGROUND_TOP, RECAP_BACKGROUND_BOTTOM),
                        ),
                    ),
        ) {
            when (val state = recapState) {
                RecapUiState.Loading -> RecapLoading()
                RecapUiState.Empty -> RecapEmpty(onDismiss)
                is RecapUiState.Ready -> RecapStory(data = state.data, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun RecapLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun RecapEmpty(onDismiss: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = RECAP_EMPTY_TEXT,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDismiss) {
            Text(text = "Tutup", color = Color.White)
        }
    }
}

@Composable
private fun RecapStory(
    data: RecapData,
    onDismiss: () -> Unit,
) {
    var currentSlide by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    // Animatable baru tiap slide, jadi progres selalu mulai dari 0.
    // Nilainya dipertahankan saat dijeda (tombol pause hanya menghentikan
    // LaunchedEffect di bawah, bukan mereset animasi).
    val progress = remember(currentSlide) { Animatable(0f) }

    val goNext: () -> Unit = {
        if (currentSlide < SLIDE_COUNT - 1) currentSlide++ else onDismiss()
    }
    val goPrev: () -> Unit = {
        if (currentSlide > 0) currentSlide--
    }

    // Timer slide: maju otomatis tiap 5 detik, berhenti di slide terakhir.
    LaunchedEffect(currentSlide, paused) {
        if (paused) return@LaunchedEffect
        if (currentSlide == SLIDE_COUNT - 1) return@LaunchedEffect
        val remaining =
            ((1f - progress.value) * SLIDE_DURATION_MS).toInt().coerceAtLeast(0)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = remaining, easing = LinearEasing),
        )
        currentSlide++
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offset.x > size.width / 2f) goNext() else goPrev()
                        },
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                    )
                },
    ) {
        AnimatedContent(
            targetState = currentSlide,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
        ) { slide ->
            RecapSlideContent(slide = slide, data = data)
        }

        // Segmen progres + tombol tutup, di atas konten slide.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (i in 0 until SLIDE_COUNT) {
                    val segmentProgress =
                        when {
                            i < currentSlide -> 1f
                            i == currentSlide -> progress.value
                            else -> 0f
                        }
                    RecapProgressSegment(
                        progress = segmentProgress,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapProgressSegment(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(Color.White),
        )
    }
}

@Composable
private fun RecapSlideContent(
    slide: Int,
    data: RecapData,
) {
    when (slide) {
        0 -> RecapCoverSlide()
        1 -> RecapTopSongsSlide(data = data, showCounts = false)
        2 -> RecapTopSongsSlide(data = data, showCounts = true)
        3 -> RecapTopArtistsSlide(data = data, showTopSong = false)
        4 -> RecapTopArtistsSlide(data = data, showTopSong = true)
        5 -> RecapTopAlbumsSlide(data = data)
        else -> RecapClosingSlide()
    }
}

/** Isi slide: padding atas dicadangkan untuk segmen progres + tombol tutup. */
@Composable
private fun RecapSlideColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 100.dp, bottom = 20.dp),
    ) {
        content()
    }
}

@Composable
private fun RecapCoverSlide() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = RECAP_COVER_TITLE,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = RECAP_WEBSITE,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecapClosingSlide() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = RECAP_CLOSING_TEXT,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecapTopSongsSlide(
    data: RecapData,
    showCounts: Boolean,
) {
    RecapSlideColumn {
        Text(
            text = RECAP_TOP_SONGS_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(data.topSongs) { index, song ->
                RecapSongRow(
                    rank = index + 1,
                    song = song,
                    showCount = showCounts,
                )
            }
        }
        if (showCounts) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = formatRecapTotalTime(data.totalTimeMs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "total mendengarkan di 2026",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun RecapTopArtistsSlide(
    data: RecapData,
    showTopSong: Boolean,
) {
    RecapSlideColumn {
        Text(
            text = RECAP_TOP_ARTISTS_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(data.topArtists) { index, artist ->
                RecapArtistRow(
                    rank = index + 1,
                    artist = artist,
                    showTopSong = showTopSong,
                )
            }
        }
    }
}

@Composable
private fun RecapTopAlbumsSlide(data: RecapData) {
    RecapSlideColumn {
        Text(
            text = RECAP_TOP_ALBUMS_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(data.topAlbums) { index, album ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RecapRank(rank = index + 1)
                    AsyncImage(
                        model = album.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${album.playCount}x",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapSongRow(
    rank: Int,
    song: RecapSong,
    showCount: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RecapRank(rank = rank)
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showCount) {
            Text(
                text = "${song.playCount}x diputar",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RecapArtistRow(
    rank: Int,
    artist: RecapArtist,
    showTopSong: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RecapRank(rank = rank)
        AsyncImage(
            model = artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (showTopSong) "Lagu teratas: ${artist.topSong.title}"
                    else "${artist.playCount}x diputar",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showTopSong) {
            Text(
                text = "${artist.playCount}x",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RecapRank(rank: Int) {
    Text(
        text = "$rank",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.width(32.dp),
    )
}

/** Total waktu (ms) jadi "123 jam", atau "45 menit" kalau belum sejam. */
private fun formatRecapTotalTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    return if (totalMinutes >= 60) "${totalMinutes / 60} jam" else "$totalMinutes menit"
}
