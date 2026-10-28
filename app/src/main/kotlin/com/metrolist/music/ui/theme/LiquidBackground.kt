/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Latar "liquid" berupa gradien yang bergerak pelan.
 *
 * Dipilih sebagai pendekatan paling ringan: tidak ada bitmap, tidak ada blur
 * per-frame, hanya interpolasi warna. GPU hanya menggambar ulang gradien,
 * sehingga scroll dan animasi lain tetap lancar.
 *
 * Gerakan dibuat SANGAT lambat (20-30 detik per siklus) dan amplitudonya
 * kecil, supaya terasa "hidup" tanpa menarik perhatian atau mengganggu
 * keterbacaan. Nilai animasi juga dipakai untuk posisi titik gradien saja,
 * bukan untuk ukuran layout, jadi tidak memicu re-layout.
 */
@Composable
fun LiquidGradientBackground(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    seedColor: Color = DefaultThemeColor,
) {
    val transition = rememberInfiniteTransition(label = "liquidBackground")

    // Tiga fase berbeda agar pergerakan tidak terlihat seragam/mekanis.
    val phaseA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(24000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "phaseA",
    )
    val phaseB by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(31000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "phaseB",
    )
    val phaseC by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(19000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "phaseC",
    )

    // Palet diturunkan dari warna tema supaya tetap menyatu dengan aksen app.
    val base1 = lerp(Color(0xFF0A1F44), seedColor, 0.22f)
    val base2 = lerp(Color(0xFF123A6B), seedColor, 0.12f)
    val base3 = lerp(Color(0xFF1B2A56), seedColor, 0.28f)

    val c1 = if (darkTheme) base1 else lerp(base1, Color.White, 0.55f)
    val c2 = if (darkTheme) base2 else lerp(base2, Color.White, 0.45f)
    val c3 = if (darkTheme) base3 else lerp(base3, Color.White, 0.62f)
    val c4 = if (darkTheme) Color(0xFF050B18) else Color(0xFFEAF0FA)

    // Titik gradien bergerak dalam rentang kecil di sekitar tengah.
    val x1 = 0.15f + phaseA * 0.35f
    val y1 = 0.10f + phaseB * 0.30f
    val x2 = 0.85f - phaseB * 0.35f
    val y2 = 0.20f + phaseC * 0.40f
    val x3 = 0.30f + phaseC * 0.40f
    val y3 = 0.90f - phaseA * 0.35f

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(c4)
                .background(
                    Brush.radialGradient(
                        colors = listOf(c1.copy(alpha = 0.95f), Color.Transparent),
                        center = Offset(x1, y1),
                        radius = 900f,
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(c2.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(x2, y2),
                        radius = 1100f,
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(c3.copy(alpha = 0.80f), Color.Transparent),
                        center = Offset(x3, y3),
                        radius = 1000f,
                    ),
                ),
    )
}
