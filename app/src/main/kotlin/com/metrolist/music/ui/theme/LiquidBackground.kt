/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import com.metrolist.music.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.utils.stackBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Latar bergelombang lembut: biru tua -> hitam, sedikit biru muda di puncak.
 *
 * Prinsip desain: TENANG. Hanya beberapa pita lebar yang saling menumpuk
 * dengan peralihan halus, bukan pola ramai. Percobaan sebelumnya membuat
 * marmer berbintik (banyak pita kecil bertumpuk) sehingga terlihat seperti
 * kaca pecah - itu dibuang.
 *
 * Setiap pita adalah Path tertutup yang diisi gradien vertikal dari
 * transparan -> warna -> transparan, sehingga tepinya melebur sendiri tanpa
 * perlu blur GPU. Ringan: satu Canvas, animasi hanya menggeser fase kurva,
 * bukan ukuran layout, jadi tidak memicu re-layout.
 */
@Composable
fun LiquidGradientBackground(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    seedColor: Color = DefaultThemeColor,
    // null = hitam polos (tanpa gambar wallpaper).
    @DrawableRes wallpaperRes: Int? = R.drawable.meowsic_background,
) {
    // Latar STATIS: animasi gelombangnya dimatikan total.
    // Sebelumnya Canvas menggambar ulang 6 pita × 40 segmen tiap frame
    // (60fps), dan itu bikin patah-patah di HP kelas menengah. Dengan nilai
    // tetap, Canvas hanya digambar sekali — nol biaya per frame.
    val drift = 0.5f
    val swell = 0.5f

    val baseTop = Color(0xFF0C1E40)
    val baseBottom = Color(0xFF030710)

    // Wallpaper di-blur beneran (stackBlur CPU, sekali per ganti wallpaper)
    // supaya garis-garis tipis di gambarnya melebur dan latar terbaca buram
    // — bukan cuma gelap. Blur-behind sistem (API 31+) tidak dipakai, jadi
    // ini jalan juga di Android 10. Gambar kecil (180x400) biar murah.
    val context = LocalContext.current
    var blurredWallpaper by remember(wallpaperRes) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wallpaperRes) {
        if (wallpaperRes == null) {
            blurredWallpaper = null
            return@LaunchedEffect
        }
        blurredWallpaper =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest.Builder(context)
                            .data(wallpaperRes)
                            .size(180, 400)
                            .allowHardware(false)
                            .build()
                    context.imageLoader.execute(request)
                        .image
                        ?.toBitmap()
                        ?.stackBlur(20)
                }.getOrNull()
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (wallpaperRes == null) {
            // Pilihan "hitam pekat": satu drawRect, paling ringan.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.Black)
            }
        } else {
            // 1) Dasar gradient (statis, sekali gambar) — juga jadi fallback kalau
            //    gambar background gagal di-decode di perangkat tertentu.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush = Brush.verticalGradient(colors = listOf(baseTop, baseBottom)))
            }

            // 2) Gambar background dari desain (statis, tanpa animasi).
            // Kalau versi blur-nya sudah jadi, pakai itu; kalau belum / gagal,
            // tampilkan gambar aslinya supaya latar tidak pernah kosong.
            val blurred = blurredWallpaper
            if (blurred != null) {
                Image(
                    bitmap = blurred.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(wallpaperRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Menggambar satu pita gelombang yang melebur.
 *
 * Pita dibentuk dua kurva (atas & bawah) yang dipisahkan [thickness], lalu
 * diisi gradien vertikal sehingga tepinya tidak pernah terlihat keras.
 */
private fun DrawScope.band(
    verticalFraction: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    alpha: Float,
    thickness: Float,
    drift: Float,
    swell: Float,
    frequency: Float,
) {
    val w = size.width
    val h = size.height
    val baseY = h * verticalFraction
    val amp = h * amplitude * (0.75f + swell * 0.5f)
    val half = h * thickness / 2f

    val shift = (drift + phase) * 2f * Math.PI.toFloat()
    val startX = -w * 0.2f
    val endX = w * 1.2f
    val steps = 40

    fun yAt(t: Float): Float =
        baseY +
            kotlin.math.sin(t * frequency * Math.PI.toFloat() + shift) * amp +
            kotlin.math.sin(t * 0.55f * Math.PI.toFloat() + shift * 1.7f) * amp * 0.42f

    val path = Path()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = startX + (endX - startX) * t
        val y = yAt(t) - half
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in steps downTo 0) {
        val t = i / steps.toFloat()
        val x = startX + (endX - startX) * t
        path.lineTo(x, yAt(t) + half)
    }
    path.close()

    val topY = baseY - amp - half
    val bottomY = baseY + amp + half
    drawPath(
        path = path,
        brush =
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        color.copy(alpha = alpha * 0.85f),
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.85f),
                        Color.Transparent,
                    ),
                startY = topY,
                endY = bottomY,
            ),
    )

    // Kilau tipis di puncak pita.
    val glow = Path()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = startX + (endX - startX) * t
        val y = yAt(t) - half * 0.35f
        if (i == 0) glow.moveTo(x, y) else glow.lineTo(x, y)
    }
    drawPath(
        path = glow,
        brush =
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        Color(0xFFCFE4FA).copy(alpha = alpha * 0.55f),
                        Color.Transparent,
                    ),
                startX = 0f,
                endX = w,
            ),
        style = Stroke(width = 2f),
    )
}
