/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Matahari/bulan switch (styling from Uiverse.io by anandita-3217, fresh-parrot-42).
 * checked == mode gelap (bulan), unchecked == mode terang (matahari).
 */
@Composable
fun ThemeSwitch(checked: Boolean, onCheckedChange: () -> Unit) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF28096B) else Color(0xFF2185D6),
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "track",
    )
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "knob",
    )

    val trackWidth = 46.dp
    val trackHeight = 26.dp
    val knobSize = 22.dp
    val travel = trackWidth - knobSize // 24dp

    Box(
        modifier = Modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(30.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCheckedChange,
            ),
    ) {
        // bintang (muncul pas gelap)
        if (checked) {
            Canvas(Modifier.fillMaxSize()) {
                val white = Color.White
                val ys = listOf(0.28f, 0.62f, 0.45f)
                val xs = listOf(0.68f, 0.8f, 0.55f)
                for (i in xs.indices) {
                    drawCircle(
                        color = white,
                        radius = this.size.minDimension * 0.02f,
                        center = Offset(size.width * xs[i], size.height * ys[i]),
                    )
                }
            }
        } else {
            // awan (pas terang)
            Canvas(Modifier.fillMaxSize()) {
                val white = Color.White.copy(alpha = 0.9f)
                val cy = this.size.height * 0.5f
                val cx = this.size.width * 0.75f
                val r = this.size.minDimension * 0.09f
                drawCircle(white, r, Offset(cx, cy))
                drawCircle(white, r * 0.8f, Offset(cx - r * 1.3f, cy + r * 0.25f))
                drawCircle(white, r * 0.8f, Offset(cx + r * 1.3f, cy + r * 0.25f))
            }
        }

        // knob (matahari / bulan)
        Box(
            modifier = Modifier
                .padding(all = 2.dp)
                .size(knobSize)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = travel.toPx() * progress
                }
                .clip(CircleShape)
                .background(if (checked) Color(0xFFB0AFFF) else Color(0xFFFDF906)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                MoonKnob(Modifier.size(14.dp))
            } else {
                SunKnob(Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SunKnob(modifier: Modifier = Modifier) {
    var rotation by remember { mutableStateOf(0f) }
    Canvas(modifier) {
        val c = Color(0xFFF9A825)
        rotate(
            degrees = rotation,
            pivot = center,
        ) {
            for (i in 0 until 8) {
                val angle = i * (Math.PI / 4)
                val r0 = size.minDimension * 0.4f
                val r1 = size.minDimension * 0.72f
                drawLine(
                    color = c,
                    start = Offset(
                        center.x + cos(angle).toFloat() * r0,
                        center.y + sin(angle).toFloat() * r0,
                    ),
                    end = Offset(
                        center.x + cos(angle).toFloat() * r1,
                        center.y + sin(angle).toFloat() * r1,
                    ),
                    strokeWidth = size.minDimension * 0.09f,
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(Color(0xFFF9A825), radius = size.minDimension * 0.3f)
    }
}

@Composable
private fun MoonKnob(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val moon = Color(0xFFFFEB3B)
        drawCircle(moon, radius = size.minDimension * 0.42f)
        // bayangan bulan sabit
        drawCircle(
            color = Color(0xFF28096B),
            radius = size.minDimension * 0.36f,
            center = Offset(size.width * 0.58f, size.height * 0.42f),
        )
    }
}
