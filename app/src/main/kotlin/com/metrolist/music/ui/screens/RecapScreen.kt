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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.metrolist.music.viewmodels.RecapData
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
private const val RECAP_WATERMARK_BRAND = "meowsic"
private const val RECAP_COVER_YEAR_PILL = "RAPORT TAHUN 2026"
private const val RECAP_WEBSITE = "meowsic.web.id"
private const val RECAP_TOP_SONGS_TITLE = "5 Lagu Favorit"
private const val RECAP_TOP_SONGS_SUBTITLE = "lagu yang paling sering kamu putar"
private const val RECAP_TOP_ARTISTS_TITLE = "5 Artis Favorit"
private const val RECAP_TOP_ARTISTS_SUBTITLE = "artis yang paling sering kamu dengarkan"
// Judul tengah slide album radial, ditumpuk 2 baris seperti
// "7 DEADLY SONGS" di referensi (header atas slide dihapus).
private const val RECAP_TOP_ALBUMS_CENTER_TITLE = "5 ALBUM\nFAVORIT"
private const val RECAP_CLOSING_TEXT =
    "RAPORT ANDA TELAH USAI. AYOK PUSH LAGI LAGUNYA UNTUK TAHUN DEPAN."
private const val RECAP_MINUTES_LABEL = "Menit Mendengarkan"
private const val RECAP_TOTAL_SONGS_LABEL = "Total Lagu"
private const val RECAP_TOTAL_ARTISTS_LABEL = "Total Artis"
// Label kolom slide ringkasan: mengikuti kata-kata pengguna secara
// literal (sama seperti referensi Spotify Wrapped).
private const val RECAP_SUMMARY_TOP_ARTISTS = "Top Artists"
private const val RECAP_SUMMARY_TOP_SONGS = "Top Songs"
private const val RECAP_EMPTY_TEXT = "Belum ada data 2026"
private const val RECAP_SAVED_MESSAGE = "Tersimpan di galeri"
private const val RECAP_SAVE_FAILED_MESSAGE = "Gagal menyimpan"

private const val SLIDE_DURATION_MS = 5000
private const val SLIDE_COUNT = 6

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
        Color(0xFFDB2777) to Color(0xFF6D28D9), // 2 artis: pink -> ungu
        Color(0xFF4338CA) to Color(0xFF1E3A8A), // 3 album: indigo -> biru
        Color(0xFFDC2626) to Color(0xFF4C1D95), // 4 ringkasan: merah -> ungu gelap
        Color(0xFF3B0764) to Color(0xFF1E1B4B), // 5 penutup: ungu gelap
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
        if (!saving) {
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
                            // Rekam isi slide ke GraphicsLayer untuk
                            // unduhan, lalu gambar normal ke layar.
                            // (DrawScope.drawLayer sudah dihapus di
                            // Compose 1.12 — pola record + drawContent
                            // ini penggantinya.)
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
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
        1 -> RecapTopSongsSlide(data = data)
        2 -> RecapTopArtistsSlide(data = data)
        3 -> RecapTopAlbumsSlide(data = data)
        4 -> RecapSummarySlide(data = data)
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
 * Slide 0 — cover. Konten dipusatkan vertikal, total ~370dp
 * (logo 200 + celah 20 + MEOWSIC ~54 + celah 16 + pill 40 +
 * footer 40), jauh di bawah ruang konten 584dp.
 * Logo pakai meowsic_logo (kepala kucing transparan) APA ADANYA
 * tanpa tint — BUKAN ic_launcher_foreground yang background-nya
 * putih. Footer watermark di bawah (logo + "meowsic" +
 * meowsic.web.id) menggantikan pill web supaya URL tidak dobel.
 */
@Composable
private fun RecapCoverSlide() {
    RecapSlideBackground(slide = 0) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.meowsic_logo),
            contentDescription = null,
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
        Spacer(Modifier.weight(1f))
        RecapWatermarkFooter()
    }
}

/**
 * Slide 5 — penutup. Konten dipusatkan vertikal, total ~252dp
 * (logo 96 + celah 20 + teks ~96 + footer 40). Logo APA ADANYA
 * tanpa tint. Footer watermark di bawah menggantikan pill web
 * supaya URL tidak dobel.
 */
@Composable
private fun RecapClosingSlide() {
    RecapSlideBackground(slide = 5) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.meowsic_logo),
            contentDescription = null,
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
        Spacer(Modifier.weight(1f))
        RecapWatermarkFooter()
    }
}

/**
 * Slide 1 — 5 lagu gaya kartu "My Top Songs" Spotify Wrapped: angka
 * rank besar, artwork persegi 64dp dengan border putih tipis, judul +
 * artis, tanpa jumlah putar per baris (referensi tidak punya, dan
 * baris ketiga tidak muat). Kartu statistik Menit/Total Lagu tetap
 * (Total Lagu = semua lagu berbeda 2026, bukan 5). Footer watermark
 * meniru logo + URL di bawah referensi.
 * Anggaran (ruang konten 584dp): judul 56 + celah 8 + 5 baris x 64
 * + 4 x 8 = 352 + celah 12 + kartu statistik 86 + Spacer weight
 * (sisa ~30dp) + footer watermark 40 = 554dp. Muat tanpa scroll.
 * Catatan: artwork referensi ~100dp tidak muat (5 x 100 + 4 x 8 =
 * 532, + judul 56 + celah 8 + celah 12 + kartu 86 + footer 40 =
 * 734dp > 584dp), jadi 64dp.
 */
@Composable
private fun RecapTopSongsSlide(data: RecapData) {
    RecapSlideBackground(slide = 1) {
        RecapSlideTitle(
            title = RECAP_TOP_SONGS_TITLE,
            subtitle = RECAP_TOP_SONGS_SUBTITLE,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.topSongs.take(5).forEachIndexed { index, song ->
                RecapTopListRow(
                    rank = index + 1,
                    thumbnailUrl = song.thumbnailUrl,
                    title = song.title,
                    subtitle = song.artist,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        RecapSongStatsCard(
            totalTimeMs = data.totalTimeMs,
            totalSongs = data.totalSongs,
        )
        Spacer(Modifier.weight(1f))
        RecapWatermarkFooter()
    }
}

/**
 * Baris daftar gaya kartu "My Top Songs" Spotify Wrapped: angka rank
 * besar di kiri, artwork persegi 64dp dengan border putih tipis,
 * lalu judul tebal + subjudul di bawahnya, terpusat vertikal
 * terhadap artwork. Dipakai slide lagu dan slide artis.
 * Border digambar sebagai overlay Box di atas artwork (bukan
 * Modifier.border yang digabung dengan clip) supaya garis 1dp tampil
 * penuh — border yang digabung dengan clip hanya menyisakan setengah
 * garis (separuhnya tertutup gambar / terpotong clip).
 */
@Composable
private fun RecapTopListRow(
    rank: Int,
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp),
    ) {
        RecapRankLarge(rank = rank)
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.size(64.dp)) {
            RecapArtwork(
                url = thumbnailUrl,
                size = 64.dp,
                shape = RoundedCornerShape(8.dp),
            )
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Angka rank besar (~30sp) gaya referensi Wrapped, dipakai baris lagu/artis. */
@Composable
private fun RecapRankLarge(rank: Int) {
    Text(
        text = "$rank",
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(36.dp),
    )
}

/**
 * Watermark footer ala referensi Wrapped (logo + URL di bawah):
 * logo Meowsic ASLI tanpa tint 40dp + "meowsic" tebal di kiri,
 * "meowsic.web.id" kecil di kanan. Ada di SEMUA slide (0-5).
 * Tinggi ~40dp.
 */
@Composable
private fun RecapWatermarkFooter() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(R.drawable.meowsic_logo),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = RECAP_WATERMARK_BRAND,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = RECAP_WEBSITE,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

/**
 * Kartu dua statistik di bawah daftar lagu: "Menit Mendengarkan"
 * (total menit, pemisah ribuan gaya Indonesia) dan "Total Lagu"
 * (jumlah lagu berbeda yang diputar 2026). Tinggi ~74dp
 * (padding 12 atas-bawah + label ~16 + celah 2 + angka ~32).
 */
@Composable
private fun RecapSongStatsCard(
    totalTimeMs: Long,
    totalSongs: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecapStat(
            label = RECAP_MINUTES_LABEL,
            value = formatRecapTotalMinutes(totalTimeMs),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color.White.copy(alpha = 0.2f)),
        )
        RecapStat(
            label = RECAP_TOTAL_SONGS_LABEL,
            value = formatIndonesianNumber(totalSongs.toLong()),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecapStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Slide 2 — 5 artis, semua baris seragam gaya "My Top Songs" (hero
 * #1 DIHAPUS — #1 sekarang tampil sebagai baris biasa seperti #2-#5).
 * Tiap baris: rank besar + artwork persegi 64dp border putih tipis +
 * nama + "Lagu teratas: <judul>". Kartu Total Artis tetap di bawah
 * daftar, footer watermark sama seperti slide lagu.
 * Anggaran (ruang konten 584dp): judul 56 + celah 8 + 5 baris x 64
 * + 4 x 8 = 352 + celah 12 + kartu statistik 86 + Spacer weight
 * (sisa ~30dp) + footer watermark 40 = 554dp. Muat tanpa scroll.
 */
@Composable
private fun RecapTopArtistsSlide(data: RecapData) {
    RecapSlideBackground(slide = 2) {
        RecapSlideTitle(
            title = RECAP_TOP_ARTISTS_TITLE,
            subtitle = RECAP_TOP_ARTISTS_SUBTITLE,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.topArtists.take(5).forEachIndexed { index, artist ->
                RecapTopListRow(
                    rank = index + 1,
                    thumbnailUrl = artist.thumbnailUrl,
                    title = artist.name,
                    subtitle = "Lagu teratas: ${artist.topSong.title}",
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        RecapArtistStatsCard(totalArtists = data.totalArtists)
        Spacer(Modifier.weight(1f))
        RecapWatermarkFooter()
    }
}

/**
 * Kartu satu statistik di bawah daftar artis: "Total Artis" (jumlah
 * artis berbeda yang diputar 2026), full-width, gaya sama seperti
 * kartu statistik slide lagu. Tinggi ~86dp (sama seperti kartu lagu).
 */
@Composable
private fun RecapArtistStatsCard(totalArtists: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecapStat(
            label = RECAP_TOTAL_ARTISTS_LABEL,
            value = formatIndonesianNumber(totalArtists.toLong()),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Pusat 5 lingkaran slide album radial (Box 320x360, koordinat
 * lokal): pentagon dengan #1 di atas, sudut -90/-18/54/126/162
 * terhadap pusat radial C=(160,188), jari-jari elips Rx=116/Ry=108.
 */
private val RECAP_RADIAL_ALBUM_CENTERS =
    listOf(
        160.dp to 80.dp, // #1 atas
        270.dp to 155.dp, // #2 kanan atas
        228.dp to 275.dp, // #3 kanan bawah
        92.dp to 275.dp, // #4 kiri bawah
        50.dp to 155.dp, // #5 kiri atas
    )

/**
 * Slide 3 — 5 album, layout radial seperti referensi
 * "7 Deadly Songs": 5 artwork LINGKARAN mengelilingi judul tengah,
 * garis tipis dari tengah ke tiap lingkaran. Tiap item HANYA nama
 * album (tebal) + artis — tanpa deskripsi, tanpa jumlah putar.
 * Header atas dihapus, judul tengah yang jadi judul slide.
 *
 * Geometri (Box 320x360; lingkaran 88dp, jari-jari 44dp):
 * pusat lingkaran di atas; judul tengah "5 ALBUM / FAVORIT" 26sp
 * tebal, lebar 124dp di (98,156) — 2 baris x 32dp = 64dp, tengahnya
 * di y=188 = pusat radial. Celah: 32dp ke lingkaran atas (bawahnya
 * 124), 11dp ke lingkaran bawah (atasnya 231), ~19dp ke lingkaran
 * samping (tepi 79/241 di y=188, judul 98..222). Label: #1 di ATAS
 * lingkaran (y 4..34, lebar 110dp); #2-#5 di BAWAH lingkaran
 * (y 201..231 samping, 321..351 bawah; lebar 92dp) — tepi label
 * samping (96/224) tidak menyentuh judul tengah (98..222).
 * Total blok 360dp + footer 40 = 400dp ≤ 584dp, sisa diserap
 * Spacer weight atas-bawah supaya blok terpusat vertikal
 * (footer watermark menempel di bawah).
 */
@Composable
private fun RecapTopAlbumsSlide(data: RecapData) {
    RecapSlideBackground(slide = 3) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(360.dp),
        ) {
            // Garis tipis dari tengah ke tiap lingkaran, digambar
            // dulu supaya berada di belakang artwork dan judul.
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(160.dp.toPx(), 188.dp.toPx())
                RECAP_RADIAL_ALBUM_CENTERS.forEach { (x, y) ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = center,
                        end = Offset(x.toPx(), y.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            data.topAlbums.take(5).forEachIndexed { index, album ->
                val (cx, cy) = RECAP_RADIAL_ALBUM_CENTERS[index]
                // Artwork lingkaran; kotak putih 14% di belakangnya
                // jadi placeholder kalau thumbnail null.
                Box(
                    modifier =
                        Modifier
                            .offset(x = cx - 44.dp, y = cy - 44.dp)
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    RecapArtwork(
                        url = album.thumbnailUrl,
                        size = 88.dp,
                        shape = CircleShape,
                    )
                }
                // Label: #1 di atas lingkaran, sisanya di bawah.
                // Tinggi label 30dp (13sp/16 + 11sp/14).
                val (labelX, labelY, labelWidth) =
                    if (index == 0) {
                        Triple(cx - 55.dp, cy - 44.dp - 32.dp, 110.dp)
                    } else {
                        Triple(cx - 46.dp, cy + 44.dp + 2.dp, 92.dp)
                    }
                RecapRadialAlbumLabel(
                    title = album.title,
                    artist = album.artist,
                    modifier =
                        Modifier
                            .offset(x = labelX, y = labelY)
                            .width(labelWidth),
                )
            }
            // Judul tengah (di atas garis radial).
            Text(
                text = RECAP_TOP_ALBUMS_CENTER_TITLE,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier =
                    Modifier
                        .offset(x = 98.dp, y = 156.dp)
                        .width(124.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        RecapWatermarkFooter()
    }
}

/** Label satu album radial: nama album tebal + artis, rata tengah. */
@Composable
private fun RecapRadialAlbumLabel(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = artist,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Slide 4 — ringkasan (baru): dua thumbnail #1 berdampingan di atas
 * (kiri lagu #1, kanan artis #1), lalu dua kolom "Top Songs" /
 * "Top Artists" masing-masing 5 baris (lagu di kiri di bawah
 * thumbnail lagu, artis di kanan di bawah foto artis), lalu kartu
 * seperti slide lagu (Menit Mendengarkan + Total Lagu — duplikasi
 * dengan slide lagu ini disengaja, meniru kartu referensi Spotify
 * Wrapped). Slide 0-3 dan penutup tidak diubah, hanya indeks
 * penutup yang geser ke 5.
 * Anggaran (ruang konten 584dp, tanpa scroll): thumbnail 154 +
 * celah 20 + dua kolom ~204 (header ~24 + celah 8 + 5 baris x 28
 * + 4 x 8) + celah 20 + kartu statistik 86 = ~484dp, lalu Spacer
 * weight + footer watermark 40 = ~524dp. Muat, sisa ~60dp — blok
 * dibesarkan dari ~396dp supaya slide tidak kelihatan hampa di
 * layar panjang (ruang kosong atas-bawah tinggal ~22dp di
 * frame 9:16).
 */
@Composable
private fun RecapSummarySlide(data: RecapData) {
    RecapSlideBackground(slide = 4) {
        // Blok ringkasan (~396dp) jauh lebih pendek dari ruang konten
        // (584dp) — Spacer weight atas-bawah supaya blok terpusat
        // vertikal, bukan nempel di atas dengan bawah kosong.
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RecapSummaryThumbnail(
                url = data.topSongs.firstOrNull()?.thumbnailUrl,
                modifier = Modifier.weight(1f),
            )
            RecapSummaryThumbnail(
                url = data.topArtists.firstOrNull()?.thumbnailUrl,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RecapSummaryRankColumn(
                header = RECAP_SUMMARY_TOP_SONGS,
                items = data.topSongs.take(5).map { it.title },
                modifier = Modifier.weight(1f),
            )
            RecapSummaryRankColumn(
                header = RECAP_SUMMARY_TOP_ARTISTS,
                items = data.topArtists.take(5).map { it.name },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        RecapSongStatsCard(
            totalTimeMs = data.totalTimeMs,
            totalSongs = data.totalSongs,
        )
        Spacer(Modifier.weight(1f))
        RecapWatermarkFooter()
    }
}

/**
 * Satu thumbnail ringkasan: persegi sudut membulat (bukan lingkaran,
 * untuk lagu #1 maupun artis #1), ukuran sedang — lebar mengikuti
 * Row (154dp di layar 360dp) dan tinggi = lebar (aspectRatio 1).
 * Kalau URL null, yang tampil kotak placeholder warna tembus pandang.
 * allowHardware(false) sama seperti RecapArtwork: supaya artwork
 * tidak kosong di hasil unduhan GraphicsLayer.
 */
@Composable
private fun RecapSummaryThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request =
        remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
        }
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.14f)),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Satu kolom peringkat ringkasan: header + 5 baris "rank nama". */
@Composable
private fun RecapSummaryRankColumn(
    header: String,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
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

/**
 * Angka gaya Indonesia: kelompokkan per 3 digit dengan titik
 * ("720" tetap "720", 12000 jadi "12.000", 94222 jadi "94.222").
 */
private fun formatIndonesianNumber(value: Long): String {
    val digits = value.toString()
    val out = StringBuilder()
    var count = 0
    for (i in digits.indices.reversed()) {
        if (count == 3) {
            out.append('.')
            count = 0
        }
        out.append(digits[i])
        count++
    }
    return out.reverse().toString()
}

/** Total waktu (ms) jadi total menit, mis. "720" atau "12.000". */
private fun formatRecapTotalMinutes(totalMs: Long): String =
    formatIndonesianNumber(totalMs / 60_000)

/**
 * Potong bitmap slide ke rasio 9:16 (tengah) supaya hasil unduhan
 * langsung pas buat story WA/IG. Tampilan di app tetap full-layar
 * (kayak story IG); yang dipendekin cuma file unduhannya.
 * Hanya dipotong kalau bitmap lebih panjang dari 9:16 (HP portrait
 * ~20:9). Layar yang lebih pendek dari 9:16 (landscape/tablet)
 * dibiarkan utuh supaya konten tidak kepotong samping.
 * Blok konten slide terpusat vertikal, jadi crop tengah tidak
 * memotong isi (ruang atas-bawah yang terpotong hanya gradient).
 */
private fun cropRecapToNineSixteen(bitmap: ImageBitmap): ImageBitmap {
    val androidBitmap = bitmap.asAndroidBitmap()
    val targetHeight = (androidBitmap.width * 16f / 9f).toInt()
    if (androidBitmap.height <= targetHeight) return bitmap
    val y = (androidBitmap.height - targetHeight) / 2
    return Bitmap
        .createBitmap(androidBitmap, 0, y, androidBitmap.width, targetHeight)
        .asImageBitmap()
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
        // Hasil unduhan dipotong ke 9:16 (tengah) biar pas buat story.
        val cropped = cropRecapToNineSixteen(bitmap)
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
                    cropped.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
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
                    cropped.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
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
