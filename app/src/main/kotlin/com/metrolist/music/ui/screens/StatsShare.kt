/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Satu baris di gambar statistik yang dibagikan (ala Metrolist v14).
 */
data class StatsShareItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
)

/** Muat sampul lagu/album untuk gambar share (256px, sekali jalan per item). */
suspend fun loadStatsShareArtwork(
    context: Context,
    items: List<StatsShareItem>,
): Map<String, ImageBitmap> {
    val artwork = mutableMapOf<String, ImageBitmap>()
    for (item in items) {
        val url = item.thumbnailUrl ?: continue
        val bmp =
            runCatching {
                val request =
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(256, 256)
                        .allowHardware(false)
                        .build()
                context.imageLoader.execute(request).image?.toBitmap()
            }.getOrNull()
        bmp?.let { artwork[item.id] = it.asImageBitmap() }
    }
    return artwork
}

/**
 * Render kartu statistik 1080px untuk dibagikan, port dari Metrolist v14
 * (StatisticsImage.kt): branding aplikasi di atas, judul + periode, ringkasan,
 * dua metrik besar (total pemutaran + total waktu), lalu tiap seksi berisi 3
 * baris peringkat (peringkat, sampul, judul, subjudul).
 */
fun renderStatsShareImage(
    appName: String,
    title: String,
    period: String,
    summary: String,
    plays: String,
    duration: String,
    playsLabel: String,
    timeLabel: String,
    sections: List<Pair<String, List<StatsShareItem>>>,
    artwork: Map<String, ImageBitmap>,
    colors: ColorScheme,
    typography: Typography,
    textMeasurer: TextMeasurer,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val width = 1080
    val padding = 48f
    val contentWidth = width - padding.toInt() * 2
    val density = Density(2f)
    val gap = 16f
    val inset = 24f
    val artworkSize = 128
    val rankWidth = 60
    val textWidth = contentWidth - (inset * 2).toInt() - rankWidth - artworkSize - 48
    val heroShape: Shape = RoundedCornerShape(24.dp)
    val sectionShape: Shape = RoundedCornerShape(20.dp)
    val artworkShape: Shape = RoundedCornerShape(12.dp)

    fun measure(
        text: String,
        style: TextStyle,
        color: Color = colors.onSurface,
        maxWidth: Int = contentWidth,
        lines: Int = 1,
    ) = textMeasurer.measure(
        text,
        style = style.copy(color = color),
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = maxWidth),
        density = density,
        layoutDirection = layoutDirection,
    )

    val branding = measure(appName, typography.labelLarge, colors.primary)
    val heading = measure(title, typography.headlineLarge, lines = 2)
    val range = measure(period, typography.titleMedium, colors.primary, lines = 2)
    val description = measure(summary, typography.bodyMedium, colors.onSurfaceVariant, lines = 3)
    val metricWidth = (contentWidth - inset * 3).toInt() / 2
    val metrics =
        listOf(
            measure(plays, typography.displayLarge, colors.onPrimaryContainer, metricWidth, 2) to
                measure(playsLabel, typography.labelLarge, colors.onPrimaryContainer, metricWidth, 2),
            measure(duration, typography.displaySmall, colors.onPrimaryContainer, metricWidth, 2) to
                measure(timeLabel, typography.labelLarge, colors.onPrimaryContainer, metricWidth, 2),
        )
    val metricHeight = metrics.maxOf { (value, label) -> value.size.height + label.size.height } + inset * 2
    val labels = sections.map { (label, _) -> measure(label, typography.titleLarge, colors.primary) }
    val rankings =
        sections.map { (_, items) ->
            items.take(3).mapIndexed { index, item ->
                Triple(
                    measure((index + 1).toString(), typography.headlineSmall, colors.primary, rankWidth),
                    measure(item.title, typography.titleLarge, maxWidth = textWidth, lines = 2),
                    measure(item.subtitle, typography.bodyMedium, colors.onSurfaceVariant, textWidth),
                )
            }
        }
    val rowHeights =
        rankings.map { rows ->
            rows.map { (_, name, subtitle) -> maxOf(artworkSize, name.size.height + subtitle.size.height + 8) }
        }
    val sectionHeights =
        labels.mapIndexed { index, label ->
            inset * 2 + label.size.height + gap + rowHeights[index].sum() + gap * (rowHeights[index].size - 1).coerceAtLeast(0)
        }
    val headerHeight = branding.size.height + heading.size.height + range.size.height + description.size.height + gap * 4 + metricHeight
    val height = (padding * 2 + headerHeight + sectionHeights.sum() + gap * sections.size).roundToInt()
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawRect(colors.surface)
        val rtl = layoutDirection == LayoutDirection.Rtl

        fun x(
            start: Float,
            elementWidth: Int,
        ): Float = if (rtl) width - start - elementWidth else start

        fun textAt(
            text: TextLayoutResult,
            start: Float,
            top: Float,
        ) {
            drawText(text, topLeft = Offset(x(start, text.size.width), top))
        }
        var y = padding
        for (text in listOf(branding, heading, range, description)) {
            textAt(text, padding, y)
            y += text.size.height + gap
        }
        translate(padding, y) {
            drawOutline(heroShape.createOutline(Size(contentWidth.toFloat(), metricHeight), layoutDirection, this), colors.primaryContainer)
        }
        metrics.forEachIndexed { index, (value, label) ->
            val start = padding + inset + index * (metricWidth + inset)
            textAt(value, start, y + inset)
            textAt(label, start, y + inset + value.size.height)
        }
        y += metricHeight + gap
        sections.forEachIndexed { sectionIndex, (_, items) ->
            translate(padding, y) {
                drawOutline(
                    sectionShape.createOutline(
                        Size(contentWidth.toFloat(), sectionHeights[sectionIndex]),
                        layoutDirection,
                        this,
                    ),
                    colors.surfaceContainer,
                )
            }
            y += inset
            textAt(labels[sectionIndex], padding + inset, y)
            y += labels[sectionIndex].size.height + gap
            rankings[sectionIndex].forEachIndexed { index, (rank, name, subtitle) ->
                val rowHeight = rowHeights[sectionIndex][index]
                val imageStart = padding + inset + rankWidth + 16
                val imageTop = y + (rowHeight - artworkSize) / 2f
                textAt(rank, padding + inset, y + (rowHeight - rank.size.height) / 2f)
                translate(x(imageStart, artworkSize), imageTop) {
                    val outline =
                        artworkShape.createOutline(
                            Size(artworkSize.toFloat(), artworkSize.toFloat()),
                            layoutDirection,
                            this,
                        )
                    val path = Path().apply { addOutline(outline) }
                    clipPath(path) {
                        drawRect(colors.secondaryContainer)
                        artwork[items[index].thumbnailUrl]?.let { image ->
                            val crop = minOf(image.width, image.height)
                            drawImage(
                                image,
                                srcOffset = IntOffset((image.width - crop) / 2, (image.height - crop) / 2),
                                srcSize = IntSize(crop, crop),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(artworkSize, artworkSize),
                            )
                        }
                    }
                }
                val textStart = imageStart + artworkSize + 24
                val textTop = y + (rowHeight - name.size.height - subtitle.size.height - 8) / 2f
                textAt(name, textStart, textTop)
                textAt(subtitle, textStart, textTop + name.size.height + 8)
                y += rowHeight + gap
            }
            y += inset
        }
    }
    return bitmap
}

/** Total waktu (ms) jadi "20 mnt" atau "1 j 20 mnt". */
fun formatStatsDuration(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000
    return if (totalMinutes >= 60) "${totalMinutes / 60} j ${totalMinutes % 60} mnt"
    else "$totalMinutes mnt"
}

/** Simpan gambar ke cache lalu bagikan lewat share sheet Android. */
suspend fun shareStatsImage(
    context: Context,
    bitmap: ImageBitmap,
    chooserTitle: String,
) {
    withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "stats-share.png")
        FileOutputStream(file).use { out ->
            bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                file,
            )
        val share =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(share, chooserTitle))
    }
}
