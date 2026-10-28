/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.utils.stackBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (fadeIn) alpha else 1f,
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
