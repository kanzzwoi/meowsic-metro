/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.utils.stackBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.max
import kotlin.math.roundToInt

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
    // True = sampul yang sudah di-blur digambar 3 lapis yang berputar pelan
    // (ala Metrolist v14, jalur fallback CPU tanpa shader). Dipakai full player.
    animated: Boolean = false,
) {
    val context = LocalContext.current
    var bitmap by remember(songId, thumbnailUrl) { mutableStateOf<Bitmap?>(null) }

    // Muat + blur setiap kali lagu berganti.
    androidx.compose.runtime.LaunchedEffect(songId, thumbnailUrl) {
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
                        ?.stackBlur(radius)
                }.getOrNull()
            }
        bitmap = bmp
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

    Box(modifier = modifier.clipToBounds()) {
        bitmap?.let { bmp ->
            val contentAlpha = if (fadeIn) alpha else 1f
            if (animated) {
                AnimatedArtworkLayers(
                    bitmap = bmp,
                    modifier = Modifier.fillMaxSize().alpha(contentAlpha),
                )
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = contentAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }
    }
}

/**
 * Lapisan sampul beranimasi ala Metrolist v14 (jalur fallback CPU, tanpa shader):
 * sampul yang sudah di-blur digambar 3 lapis, masing-masing diputar pelan dengan
 * kecepatan berbeda (putaran penuh 130/90/70 detik) dari posisi berbeda, lalu
 * saturasi dinaikkan 2.5x. Hasilnya warna sampul mengalir pelan seperti Apple Music.
 * Murah: bitmap sumber cuma 100px, jadi 3 drawImage per frame tidak berat.
 */
@Composable
private fun AnimatedArtworkLayers(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val liquidTransition = rememberInfiniteTransition(label = "liquidDrift")
    val frac by liquidTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 130_000, easing = LinearEasing),
            ),
        label = "liquidFrac",
    )
    val saturationMatrix =
        remember {
            ColorMatrix().apply { setToSaturation(2.5f) }
        }

    Canvas(modifier = modifier) {
        val maxDim = max(size.width, size.height) * 1.3f
        val scale = maxDim / image.width.toFloat()
        val dstW = (image.width * scale).roundToInt()
        val dstH = (image.height * scale).roundToInt()
        val dstOffset =
            IntOffset(
                x = -((dstW - size.width.roundToInt()) / 2),
                y = -((dstH - size.height.roundToInt()) / 2),
            )
        val pivot = Offset(size.width / 2f, size.height / 2f)
        // v14: -360 derajat per 130 detik, +360 per 90 detik, +360 per 70 detik.
        val layers =
            listOf(
                Triple(-360f * frac, 0f, 0f),
                Triple(360f * frac * (130f / 90f), -0.95f * size.width, -0.7f * size.height),
                Triple(360f * frac * (130f / 70f), -0.5f * size.width, 0.7f * size.height),
            )
        for ((rotation, offsetX, offsetY) in layers) {
            withTransform({
                translate(left = offsetX, top = offsetY)
                rotate(degrees = rotation, pivot = pivot)
            }) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = dstOffset,
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = FilterQuality.Low,
                    colorFilter = ColorFilter.colorMatrix(saturationMatrix),
                )
            }
        }
    }
}
