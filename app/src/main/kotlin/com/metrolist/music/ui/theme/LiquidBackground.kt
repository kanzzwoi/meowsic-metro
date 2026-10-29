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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

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
) {
    val transition = rememberInfiniteTransition(label = "liquidBackground")

    // Sangat lambat (38-47 detik) supaya terasa hidup tanpa mengganggu.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(38000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "drift",
    )
    val swell by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(47000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "swell",
    )

    val baseTop = Color(0xFF0C1E40)
    val baseBottom = Color(0xFF030710)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(colors = listOf(baseTop, baseBottom)))

        band(0.18f, 0.075f, 0.00f, Color(0xFF6092CE), 0.30f, 0.30f, drift, swell, 1.15f)
        band(0.36f, 0.095f, 0.35f, Color(0xFF3464A2), 0.42f, 0.34f, drift, swell, 1.30f)
        band(0.52f, 0.085f, 0.70f, Color(0xFF1C3E74), 0.55f, 0.32f, drift, swell, 1.10f)
        band(0.66f, 0.100f, 0.20f, Color(0xFF4A7CBA), 0.34f, 0.26f, drift, swell, 1.40f)
        band(0.79f, 0.080f, 0.55f, Color(0xFFA6CAF0), 0.20f, 0.18f, drift, swell, 1.20f)
        band(0.91f, 0.070f, 0.90f, Color(0xFF24487C), 0.45f, 0.28f, drift, swell, 1.05f)
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
                start = Offset(0f, 0f),
                end = Offset(w, 0f),
            ),
        style = Stroke(width = 2f),
    )
}
