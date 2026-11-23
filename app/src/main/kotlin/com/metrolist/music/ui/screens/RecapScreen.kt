/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.metrolist.music.R
import com.metrolist.music.viewmodels.RecapAlbum
import com.metrolist.music.viewmodels.RecapArtist
import com.metrolist.music.viewmodels.RecapData
import com.metrolist.music.viewmodels.RecapSong
import com.metrolist.music.viewmodels.RecapUiState
import com.metrolist.music.viewmodels.RecapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// Teks-teks Raport Meowsic (konten berbahasa Indonesia sesuai permintaan
// pengguna), sengaja ditulis sebagai konstanta di sini, bukan string
// resource — raport ini memang berbahasa Indonesia.
private const val RECAP_COVER_BRAND = "MEOWSIC"
private const val RECAP_COVER_YEAR_PILL = "RAPORT TAHUN 2026"
private const val RECAP_WEBSITE = "meowsic.web.id"
private const val RECAP_TOP_SONGS_TITLE = "10 Lagu Favorit"
private const val RECAP_TOP_SONGS_SUBTITLE = "lagu yang paling sering kamu putar"
private const val RECAP_TOP_ARTISTS_TITLE = "5 Artis Favorit"
private const val RECAP_TOP_ARTISTS_SUBTITLE = "artis yang paling sering kamu dengarkan"
private const val RECAP_TOP_ALBUMS_TITLE = "5 Album Favorit"
private const val RECAP_TOP_ALBUMS_SUBTITLE = "album yang paling sering kamu putar"
private const val RECAP_CLOSING_TEXT =
    "RAPORT ANDA TELAH USAI. AYOK PUSH LAGI LAGUNYA UNTUK TAHUN DEPAN."
private const val RECAP_TOTAL_TIME_LABEL = "total mendengarkan di 2026"
private const val RECAP_EMPTY_TEXT = "Belum ada data 2026"
private const val RECAP_SAVED_MESSAGE = "Tersimpan di galeri"
private const val RECAP_SAVE_FAILED_MESSAGE = "Gagal menyimpan"

private const val SLIDE_DURATION_MS = 5000
private const val SLIDE_COUNT = 7

// Jarak atas konten slide: dicadangkan untuk segmen progres + tombol
// (status bar ~24dp + padding 4 + segmen 4 + tombol 40 + padding 4).
private val RECAP_CONTENT_TOP_PADDING = 76.dp

/**
 * Anggaran tinggi tiap slide (viewport 360x720dp, TANPA scroll):
 * atas 76dp + bawah = navigation bar (gestur ~16dp / tombol ~48dp) +
 * 12dp, jadi ruang konten terburuk = 720 - 76 - 48 - 12 = 584dp.
 * Semua slide di bawah dihitung supaya totalnya <= 584dp; kalau ada
 * yang mepet, yang diperkecil artwork/jaraknya, bukan dibiarkan
 * kepotong. Angka per slide ada di komentar masing-masing composable.
 */
private val RECAP_SLIDE_GRADIENTS =
    listOf(
        Color(0xFF5B21B6) to Color(0xFF1E1040), // 0 cover: ungu gelap
        Color(0xFFEA580C) to Color(0xFF991B1B), // 1 lagu: oranye -> merah
        Color(0xFF0F766E) to Color(0xFF064E3B), // 2 lagu+jumlah: teal -> hijau gelap
        Color(0xFFDB2777) to Color(0xFF6D28D9), // 3 artis: pink -> ungu
        Color(0xFF7C3AED) to Color(0xFF4C1D95), // 4 artis+lagu: violet
        Color(0xFF4338CA) to Color(0xFF1E3A8A), // 5 album: indigo -> biru
        Color(0xFF3B0764) to Color(0xFF1E1B4B), // 6 penutup: ungu gelap
    )

/**
 * Raport tahunan Meowsic ala story (Spotify Wrapped): layar penuh,
 * segmen progres di atas, maju otomatis tiap 5 detik, ketuk kanan =
 * slide berikut, ketuk kiri = slide sebelumnya, tahan = jeda timer.
 * Tombol unduh di kanan atas menyimpan slide yang sedang tampil
 * sebagai PNG ke galeri (Pictures/Meowsic).
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
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                // Biar dialog menutup SELURUH jendela sampai balik
                // navigation bar (tanpa ini, strip bawah layar malah
                // menampilkan konten layar Statistik di belakangnya).
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Lapisan dasar (gradient cover) kalau-kalau ada celah
                    // transparan saat transisi antar slide.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                RECAP_SLIDE_GRADIENTS[0].first,
                                RECAP_SLIDE_GRADIENTS[0].second,
                            ),
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
    RecapSlideBackground(slide = 0) {
        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RecapEmpty(onDismiss: () -> Unit) {
    RecapSlideBackground(slide = 0) {
        Spacer(Modifier.weight(1f))
        Text(
            text = RECAP_EMPTY_TEXT,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = "Tutup", color = Color.White)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RecapStory(
    data: RecapData,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentSlide by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // Animatable baru tiap slide, jadi progres selalu mulai dari 0.
    // Nilainya dipertahankan saat dijeda (tombol pause hanya menghentikan
    // LaunchedEffect di bawah, bukan mereset animasi).
    val progress = remember(currentSlide) { Animatable(0f) }
    // Layer penangkap gambar slide (tombol unduh): direkam di
    // drawWithContent tiap slide, jadi yang tersimpan hanya konten
    // slide — bukan segmen progres / tombol atas.
    val graphicsLayer = rememberGraphicsLayer()

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

    // Unduh slide yang sedang tampil sebagai PNG ke galeri. Timer
    // dijeda dulu supaya slide tidak keburu pindah saat menyimpan.
    val onDownload: () -> Unit = {
        if (saving) return
        saving = true
        scope.launch {
            paused = true
            try {
                // Tunggu transisi fade (300ms) selesai supaya yang
                // terekam benar-benar slide sekarang, bukan sisa slide
                // sebelumnya.
                delay(350)
                val bitmap = graphicsLayer.toImageBitmap()
                val fileName = "meowsic-raport-2026-slide-${currentSlide + 1}"
                val ok = downloadRecapImage(context, bitmap, fileName)
                Toast.makeText(
                    context,
                    if (ok) RECAP_SAVED_MESSAGE else RECAP_SAVE_FAILED_MESSAGE,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (_: Exception) {
                Toast.makeText(context, RECAP_SAVE_FAILED_MESSAGE, Toast.LENGTH_SHORT).show()
            } finally {
                paused = false
                saving = false
            }
        }
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
                            if (!saving) paused = false
                        },
                    )
                },
    ) {
        AnimatedContent(
            targetState = currentSlide,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
        ) { slide ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        },
            ) {
                RecapSlideContent(slide = slide, data = data)
            }
        }

        // Segmen progres + tombol unduh/tutup, di atas konten slide.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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
                IconButton(
                    onClick = onDownload,
                    enabled = !saving,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                ) {
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

/**
 * Latar tiap slide: gradient sendiri + dua lingkaran dekoratif
 * tembus pandang (sebagian di luar layar) supaya latar tidak datar.
 * Konten di dalamnya sudah diberi padding atas 76dp (ruang tombol
 * atas) dan navigationBarsPadding supaya tidak ketutup gesture bar.
 */
@Composable
private fun RecapSlideBackground(
    slide: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (top, bottom) = RECAP_SLIDE_GRADIENTS[slide]
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        Box(
            modifier =
                Modifier
                    .size(280.dp)
                    .offset(x = 190.dp, y = (-90).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f)),
        )
        Box(
            modifier =
                Modifier
                    .size(220.dp)
                    .offset(x = (-110).dp, y = 480.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = RECAP_CONTENT_TOP_PADDING,
                    ).navigationBarsPadding()
                    .padding(bottom = 12.dp),
            content = content,
        )
    }
}

/** Pill kecil tembus pandang untuk label (tahun, website, jumlah). */
@Composable
private fun RecapPill(
    text: String,
    modifier: Modifier = Modifier,
    alpha: Float = 0.15f,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = alpha))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** Judul besar + subjudul kecil tiap slide daftar. Tinggi 56dp. */
@Composable
private fun RecapSlideTitle(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.75f),
    )
}

/**
 * Slide 0 — cover. Konten dipusatkan vertikal, total ~380dp
 * (logo 200 + celah 20 + MEOWSIC ~54 + celah 16 + pill 40 +
 * celah 12 + pill web 36), jauh di bawah ruang konten 584dp.
 * Logo pakai meowsic_logo (kepala kucing transparan) yang di-tint
 * putih — BUKAN ic_launcher_foreground yang background-nya putih.
 */
@Composable
private fun RecapCoverSlide() {
    RecapSlideBackground(slide = 0) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.meowsic_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier =
                Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = RECAP_COVER_BRAND,
            style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 6.sp),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        RecapPill(
            text = RECAP_COVER_YEAR_PILL,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        RecapPill(
            text = RECAP_WEBSITE,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            alpha = 0.10f,
        )
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Slide 6 — penutup. Konten dipusatkan vertikal, total ~280dp
 * (logo 96 + celah 20 + teks ~96 + celah 16 + pill web 36).
 */
@Composable
private fun RecapClosingSlide() {
    RecapSlideBackground(slide = 6) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.meowsic_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier =
                Modifier
                    .size(96.dp)
                    .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = RECAP_CLOSING_TEXT,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        RecapPill(
            text = RECAP_WEBSITE,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            alpha = 0.10f,
        )
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Slide 1/2 — 10 lagu. Anggaran (ruang konten 584dp):
 * judul 56 + celah 8 + hero 72 + celah 10 + baris #2-#10
 * 9 x 44 + 8 x 4 = 428 (slide 1) = 574dp; slide 2 pakai baris
 * 36dp (9 x 36 + 8 x 4 = 356) + celah 8 + kartu total 68 = 578dp.
 * Keduanya muat tanpa scroll.
 */
@Composable
private fun RecapTopSongsSlide(
    data: RecapData,
    showCounts: Boolean,
) {
    RecapSlideBackground(slide = if (showCounts) 2 else 1) {
        RecapSlideTitle(
            title = RECAP_TOP_SONGS_TITLE,
            subtitle = RECAP_TOP_SONGS_SUBTITLE,
        )
        Spacer(Modifier.height(8.dp))
        val topSong = data.topSongs.firstOrNull()
        if (topSong != null) {
            RecapSongHero(song = topSong, showCount = showCounts)
        }
        Spacer(Modifier.height(10.dp))
        val rowHeight = if (showCounts) 38.dp else 44.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            data.topSongs.drop(1).forEachIndexed { index, song ->
                RecapSongRow(
                    rank = index + 2,
                    song = song,
                    showCount = showCounts,
                    rowHeight = rowHeight,
                )
            }
        }
        if (showCounts) {
            Spacer(Modifier.height(8.dp))
            RecapTotalTimeCard(totalTimeMs = data.totalTimeMs)
        }
    }
}

/** Lagu #1 sebagai hero: artwork 72dp + badge #1 + judul/artis. */
@Composable
private fun RecapSongHero(
    song: RecapSong,
    showCount: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            RecapArtwork(
                url = song.thumbnailUrl,
                size = 72.dp,
                shape = RoundedCornerShape(12.dp),
            )
            RecapRankBadge(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showCount) {
                Text(
                    text = "${song.playCount}x diputar",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RecapSongRow(
    rank: Int,
    song: RecapSong,
    showCount: Boolean,
    rowHeight: Dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(rowHeight),
    ) {
        RecapRank(rank = rank)
        RecapArtwork(
            url = song.thumbnailUrl,
            size = rowHeight,
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showCount) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${song.playCount}x diputar",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/** Kartu total waktu di slide 2: nilai besar + label sebaris. Tinggi 68dp. */
@Composable
private fun RecapTotalTimeCard(totalTimeMs: Long) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatRecapTotalTime(totalTimeMs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = RECAP_TOTAL_TIME_LABEL,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Slide 3/4 — 5 artis. Anggaran: judul 56 + celah 8 + hero 180
 * (artwork 120 + celah 8 + nama ~28 + celah 4 + sub ~20) + celah 12
 * + baris #2-#5 4 x 48 + 3 x 6 = 210 (slide 3) = 466dp; slide 4
 * pakai baris 52dp (226) = 482dp. Sisa ruang dibagi rata atas-bawah
 * lewat Spacer weight supaya blok konten terpusat.
 */
@Composable
private fun RecapTopArtistsSlide(
    data: RecapData,
    showTopSong: Boolean,
) {
    RecapSlideBackground(slide = if (showTopSong) 4 else 3) {
        Spacer(Modifier.weight(1f))
        RecapSlideTitle(
            title = RECAP_TOP_ARTISTS_TITLE,
            subtitle = RECAP_TOP_ARTISTS_SUBTITLE,
        )
        Spacer(Modifier.height(8.dp))
        val topArtist = data.topArtists.firstOrNull()
        if (topArtist != null) {
            RecapTopItemHero(
                thumbnailUrl = topArtist.thumbnailUrl,
                title = topArtist.name,
                subtitle =
                    if (showTopSong) "Lagu teratas: ${topArtist.topSong.title}"
                    else "${topArtist.playCount}x diputar",
                circular = true,
            )
        }
        Spacer(Modifier.height(12.dp))
        val rowHeight = if (showTopSong) 52.dp else 48.dp
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.topArtists.drop(1).forEachIndexed { index, artist ->
                RecapArtistRow(
                    rank = index + 2,
                    artist = artist,
                    showTopSong = showTopSong,
                    rowHeight = rowHeight,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RecapArtistRow(
    rank: Int,
    artist: RecapArtist,
    showTopSong: Boolean,
    rowHeight: Dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(rowHeight),
    ) {
        RecapRank(rank = rank)
        RecapArtwork(
            url = artist.thumbnailUrl,
            size = 44.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (showTopSong) "Lagu teratas: ${artist.topSong.title}"
                    else "${artist.playCount}x diputar",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showTopSong) {
            Spacer(Modifier.width(8.dp))
            RecapPill(
                text = "${artist.playCount}x",
                modifier = Modifier,
                alpha = 0.15f,
            )
        }
    }
}

/**
 * Slide 5 — 5 album. Anggaran sama seperti slide 3: judul 56 +
 * celah 8 + hero 180 + celah 12 + baris 4 x 48 + 3 x 6 = 210, total
 * 466dp; sisa ruang dibagi rata atas-bawah lewat Spacer weight.
 */
@Composable
private fun RecapTopAlbumsSlide(data: RecapData) {
    RecapSlideBackground(slide = 5) {
        Spacer(Modifier.weight(1f))
        RecapSlideTitle(
            title = RECAP_TOP_ALBUMS_TITLE,
            subtitle = RECAP_TOP_ALBUMS_SUBTITLE,
        )
        Spacer(Modifier.height(8.dp))
        val topAlbum = data.topAlbums.firstOrNull()
        if (topAlbum != null) {
            RecapTopItemHero(
                thumbnailUrl = topAlbum.thumbnailUrl,
                title = topAlbum.title,
                subtitle = topAlbum.artist,
                circular = false,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.topAlbums.drop(1).forEachIndexed { index, album ->
                RecapAlbumRow(rank = index + 2, album = album)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RecapAlbumRow(
    rank: Int,
    album: RecapAlbum,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
    ) {
        RecapRank(rank = rank)
        RecapArtwork(
            url = album.thumbnailUrl,
            size = 48.dp,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${album.playCount}x",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** Item #1 sebagai hero besar di tengah: artwork 120dp + nama + sub. */
@Composable
private fun RecapTopItemHero(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    circular: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            RecapArtwork(
                url = thumbnailUrl,
                size = 120.dp,
                shape = if (circular) CircleShape else RoundedCornerShape(16.dp),
            )
            RecapRankBadge(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Badge "#1" putih menempel di sudut artwork hero. */
@Composable
private fun RecapRankBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "#1",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
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

/**
 * Sampul lagu/artis/album. allowHardware(false) sengaja: bitmap
 * hardware tidak bisa digambar ke canvas software saat tombol unduh
 * menangkap slide lewat GraphicsLayer — tanpa ini artwork di hasil
 * unduhan bisa kosong.
 */
@Composable
private fun RecapArtwork(
    url: String?,
    size: Dp,
    shape: Shape,
) {
    val context = LocalContext.current
    val request =
        remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(size)
                .clip(shape),
    )
}

/** Total waktu (ms) jadi "123 jam", atau "45 menit" kalau belum sejam. */
private fun formatRecapTotalTime(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    return if (totalMinutes >= 60) "${totalMinutes / 60} jam" else "$totalMinutes menit"
}

/**
 * Simpan bitmap slide Raport sebagai PNG ke Pictures/Meowsic
 * (masuk galeri), mengikuti pola downloadStatsImage di StatsShare.kt
 * (MediaStore + IS_PENDING di API 29+), tapi ke koleksi Images
 * bukan Downloads. Di bawah API 29, file dipindai manual supaya
 * muncul di galeri.
 */
suspend fun downloadRecapImage(
    context: Context,
    bitmap: ImageBitmap,
    fileName: String,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/Meowsic",
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                val uri =
                    context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values,
                    ) ?: return@runCatching false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return@runCatching false
                val complete =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                context.contentResolver.update(uri, complete, null, null)
                true
            } else {
                val dir =
                    File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES,
                        ),
                        "Meowsic",
                    )
                dir.mkdirs()
                val file = File(dir, "$fileName.png")
                FileOutputStream(file).use { out ->
                    bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("image/png"),
                    null,
                )
                true
            }
        }.getOrDefault(false)
    }
