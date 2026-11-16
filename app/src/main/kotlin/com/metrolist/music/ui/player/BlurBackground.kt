/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.utils.stackBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds

/**
 * Latar buram dari sampul lagu sebagai COMPOSABLE, bukan state di pemanggil.
 *
 * Alasan: memuat bitmap lewat LaunchedEffect di dalam blok `when` membuat
 * state mudah hilang saat recomposition / container di-clip, dan efeknya
 * tidak pernah benar-benar tampil. Composable ini memegang state-nya sendiri
 * sehingga siklus hidupnya ikut komposisi, bukan cabang `when`.
 *
 * Blur dikerjakan di CPU (stackBlur) sehingga tidak bergantung RenderEffect.
 */
@Composable
fun BlurBackground(
    thumbnailUrl: String?,
    songId: String?,
    radius: Int,
    scrimAlpha: Float,
    modifier: Modifier = Modifier,
    fadeIn: Boolean = false,
    fadeDurationMillis: Int = 400,
    // True = background animasi ala Metrolist v14 (LiquidBackgroundRenderer,
    // salinan jalur fallback CPU v14). Dipakai full player.
    animated: Boolean = false,
    // Rotasi v14 hanya jalan saat lagu diputar; diam saat pause.
    isPlaying: Boolean = true,
) {
    val context = LocalContext.current
    var bitmap by remember(songId, thumbnailUrl) { mutableStateOf<Bitmap?>(null) }

    // Muat bitmap MENTAH setiap kali lagu berganti. Jalur statis yang
    // mem-blur-nya sendiri; jalur animasi butuh yang mentah karena
    // LiquidBackgroundRenderer me-render + mem-blur kompositnya sendiri.
    LaunchedEffect(songId, thumbnailUrl) {
        if (thumbnailUrl.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val bmp =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .size(100, 100)
                            .allowHardware(false)
                            .build()
                    context.imageLoader.execute(request)
                        .image
                        ?.toBitmap()
                }.getOrNull()
            }
        bitmap = bmp
    }

    // Jalur statis (mini player): blur sekali per lagu.
    val blurredBitmap = remember(bitmap) { bitmap?.stackBlur(radius) }

    // Crossfade antar lagu ala v14 (tween 1000ms): artwork lama digambar
    // di bawah artwork baru selama transisi.
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previousBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val crossfadeAlpha = remember { Animatable(1f) }
    LaunchedEffect(bitmap) {
        val newBitmap = bitmap ?: return@LaunchedEffect
        if (newBitmap !== currentBitmap) {
            previousBitmap = currentBitmap
            currentBitmap = newBitmap
            crossfadeAlpha.snapTo(0f)
            crossfadeAlpha.animateTo(1f, tween(1000))
            previousBitmap = null
        }
    }

    // Penggerak rotasi ala v14 (LiquidBackground.kt): nilainya WAKTU NYATA
    // — frame time dikurangi origin — bukan akumulasi delta frame. Versi
    // lama mengakumulasi delta antar-frame (~16ms) padahal tiap iterasi
    // menunggu frame + delay 50ms (~66ms), jadi putaran berjalan ~4x lebih
    // lambat dari v14. Karena ketiga lapis berputar dengan periode beda
    // (130/90/70 detik), putaran yang lambat itu juga yang membuat susunan
    // warnanya tampak beda dari Metrolist setelah lagu berjalan sebentar.
    // Origin menjaga putaran lanjut dari posisi terakhir saat resume
    // dari pause (v14 menyimpannya di globalLiquidRotationTimeMs).
    var rotationTimeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val origin = withFrameMillis { it } - rotationTimeMs
        while (true) {
            delay(50)
            withFrameMillis { now -> rotationTimeMs = now - origin }
        }
    }

    val alpha by
        animateFloatAsState(
            targetValue = if (bitmap != null) 1f else 0f,
            animationSpec =
                androidx.compose.animation.core.tween(
                    durationMillis = if (fadeIn) fadeDurationMillis else 0,
                ),
            label = "blurAlpha",
        )

    val liquidRenderer = remember { LiquidBackgroundRenderer() }

    Box(modifier = modifier.clipToBounds()) {
        if (animated) {
            currentBitmap?.let { bmp ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    liquidRenderer.draw(
                        scope = this,
                        activeBitmap = bmp,
                        previousBitmap = previousBitmap,
                        crossfadeAlpha = crossfadeAlpha.value,
                        rotationTimeMs = rotationTimeMs,
                    )
                }
                // Scrim sudah di dalam render (v14: hitam 50% + putih 5%),
                // jadi tidak ada lagi lapisan scrim luar di jalur animasi.
            }
        } else {
            blurredBitmap?.let { bmp ->
                val contentAlpha = if (fadeIn) alpha else 1f
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = contentAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha)),
                )
            }
        }
    }
}
