/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.ui.component.DefaultDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Gaya kartu share lirik.
 * - [GLASS] = glassmorph: background cover di-zoom + blur tipis (tidak dihitamkan), kartu kaca putih tembus.
 * - [FLAT]  = kartu polos dengan warna dominan thumbnail, teks gelap.
 */
enum class LyricsShareStyle { GLASS, FLAT }

/** Palet yang diturunkan dari cover + gaya kartu. Warna berupa ARGB int. */
private data class CardPalette(
    val fg: Int,
    val muted: Int,
    val base: Int,
    val glass: Boolean,
)

/**
 * Merender kartu lirik menjadi [Bitmap] persegi (default 1080x1080) memakai [Canvas].
 * Geometri mengikuti mock yang di-approve: judul boleh 2 baris, lirik auto-fit,
 * divider tipis sebelum watermark, watermark "MEOWSIC / by kanz".
 */
private object LyricsCardRenderer {

    fun render(
        context: Context,
        style: LyricsShareStyle,
        title: String,
        artist: String,
        cover: Bitmap?,
        lyrics: String,
        size: Int = 1080,
    ): Bitmap {
        val S = size.toFloat()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val reg = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_regular)
            ?: Typeface.SANS_SERIF
        val bold = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold)
            ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val extra = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_extrabold) ?: bold

        val base = cover?.let { dominantColor(it) } ?: 0xFF7A9EBB.toInt()
        val useGlass = style == LyricsShareStyle.GLASS && cover != null
        val palette = if (useGlass) {
            CardPalette(fg = 0xFF181C24.toInt(), muted = 0xAF181C24.toInt(), base = base, glass = true)
        } else {
            CardPalette(fg = 0xFF181E26.toInt(), muted = 0xA5181E26.toInt(), base = base, glass = false)
        }

        // ---- background ----
        var bgBlur: Bitmap? = null
        var zoomed: Bitmap? = null
        if (useGlass) {
            zoomed = zoomCover(cover!!, size, 1.35f)
            bgBlur = blurDownscale(zoomed, 0.10f)
            canvas.drawBitmap(bgBlur, 0f, 0f, null)
        } else {
            canvas.drawColor(shade(base, 0.66f))
        }

        // ---- geometry ----
        val Wc = S * 0.86f
        val x0 = (S - Wc) / 2f
        val p = Wc * 0.06f
        val CW = Wc - 2f * p
        val left = x0 + p
        val right = x0 + Wc - p

        val thumb = CW * 0.155f
        val textX = left + thumb + CW * 0.05f
        val maxTW = right - textX

        val titlePaint = textPaint(palette.fg, thumb * 0.40f, bold)
        val minTitleSize = thumb * 0.24f
        var titleLines = wrapWords(titlePaint, title, maxTW)
        while (titleLines.size > 2 && titlePaint.textSize > minTitleSize) {
            titlePaint.textSize -= 2f
            titleLines = wrapWords(titlePaint, title, maxTW)
        }
        if (titleLines.size > 2) {
            // Baris ke-2 yang terpotong diberi ellipsis supaya jelas teksnya dipangkas.
            titleLines = listOf(
                titleLines[0],
                ellipsize(titleLines[1], titlePaint, maxTW),
            )
        }
        val titleLineH = titlePaint.descent() - titlePaint.ascent()
        val gapT = titlePaint.textSize * 0.16f

        val artistPaint = textPaint(palette.muted, thumb * 0.33f, reg)
        val artistH = artistPaint.descent() - artistPaint.ascent()

        val titleBlockH = titleLines.size * titleLineH + gapT * (titleLines.size - 1).coerceAtLeast(0)
        val blockH = titleBlockH + artistH * 0.55f + artistH
        val headerH = max(thumb, blockH)

        // ---- lyrics autofit (lebar + tinggi) ----
        var lyricLines = lyrics.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val lyricsPaint = textPaint(palette.fg, CW * 0.095f, extra)
        val minLyricSize = CW * 0.05f
        var lyricSize = CW * 0.095f
        var lineH = lyricSize * 1.16f

        val fixedH = p + headerH + CW * 0.075f + CW * 0.06f +
            max(2f, CW * 0.003f) + CW * 0.05f + CW * 0.09f + p

        fun cardHeight(): Float = fixedH + lineH * lyricLines.size

        lyricsPaint.textSize = lyricSize
        var guard = 0
        while (
            (lyricLines.any { lyricsPaint.measureText(it) > CW } || cardHeight() > S * 0.98f) &&
            lyricSize > minLyricSize && guard < 200
        ) {
            lyricSize -= 2f
            lyricsPaint.textSize = lyricSize
            lineH = lyricSize * 1.16f
            guard++
        }

        // Kalau masih tidak muat di ukuran minimum, buang baris paling akhir supaya
        // kartu tidak pernah tergambar keluar kanvas.
        val maxLines = (((S * 0.98f) - fixedH) / lineH).toInt().coerceAtLeast(1)
        if (lyricLines.size > maxLines) lyricLines = lyricLines.take(maxLines)

        val lyricsH = lineH * lyricLines.size
        val gapH = CW * 0.075f
        val gapDiv = CW * 0.06f
        val gapDivWm = CW * 0.05f
        val wmH = CW * 0.09f
        val divH = max(2f, CW * 0.003f)
        var cardH = p + headerH + gapH + lyricsH + gapDiv + divH + gapDivWm + wmH + p
        val y0 = ((S - cardH) / 2f).coerceAtLeast(p * 0.5f)
        val cardRect = RectF(x0, y0, x0 + Wc, y0 + cardH)
        val radius = Wc * 0.05f

        // ---- card surface ----
        var region: Bitmap? = null
        var frost: Bitmap? = null
        if (useGlass && bgBlur != null) {
            val cw = cardRect.width().toInt().coerceAtMost(bgBlur.width)
            val ch = cardRect.height().toInt().coerceAtMost(bgBlur.height)
            val cx = cardRect.left.toInt().coerceIn(0, (bgBlur.width - cw).coerceAtLeast(0))
            val cy = cardRect.top.toInt().coerceIn(0, (bgBlur.height - ch).coerceAtLeast(0))
            region = Bitmap.createBitmap(bgBlur, cx, cy, cw, ch)
            frost = blurDownscale(region, 0.18f)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(cardRect, radius, radius, Path.Direction.CW) })
            canvas.drawBitmap(frost, cardRect.left, cardRect.top, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
            canvas.drawRect(cardRect, Paint().apply { color = 0x66FFFFFF })
            canvas.drawRect(cardRect, Paint().apply { color = 0x10FFFFFF })
            canvas.restore()
            canvas.drawRoundRect(
                cardRect, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x82FFFFFF.toInt()
                    this.style = Paint.Style.STROKE
                    strokeWidth = max(2f, Wc * 0.004f)
                },
            )
        } else {
            var cardCol = mix(base, 0xFFFFFFFF.toInt(), 0.82f)
            if (luma(cardCol) > 205f) cardCol = mix(base, 0xFFFFFFFF.toInt(), 0.72f)
            canvas.drawRoundRect(cardRect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardCol })
            canvas.drawRoundRect(
                cardRect, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x10000000
                    this.style = Paint.Style.STROKE
                    strokeWidth = max(2f, Wc * 0.004f)
                },
            )
        }

        // ---- header ----
        val tx = left
        val ty = y0 + p + (headerH - thumb) / 2f
        drawCoverThumb(canvas, context, cover, RectF(tx, ty, tx + thumb, ty + thumb), palette)

        val by = y0 + p + (headerH - blockH) / 2f
        var yy = by
        for (lineText in titleLines) {
            canvas.drawText(lineText, textX, yy - titlePaint.ascent(), titlePaint)
            yy += titleLineH + gapT
        }
        yy += artistH * 0.55f
        canvas.drawText(
            ellipsize(artist, artistPaint, maxTW),
            textX,
            yy - artistPaint.ascent(),
            artistPaint,
        )

        // ---- lyrics ----
        val lyTop = y0 + p + headerH + gapH
        for (i in lyricLines.indices) {
            canvas.drawText(lyricLines[i], left, lyTop + i * lineH - lyricsPaint.ascent(), lyricsPaint)
        }

        // ---- divider ----
        val divY = lyTop + lyricsH + gapDiv
        canvas.drawRect(left, divY, right, divY + divH, Paint().apply { color = withAlpha(palette.fg, 60f / 255f) })

        // ---- watermark lockup: MEOWSIC / by kanz ----
        val wy = divY + divH + gapDivWm
        val wmRect = RectF(left, wy, left + wmH, wy + wmH)
        drawMeowsicMark(canvas, context, wmRect, palette.fg)

        val wmX = left + wmH + CW * 0.035f
        val wmPaint = textPaint(palette.fg, wmH * 0.52f, bold).apply { letterSpacing = 0.086f }
        val bkPaint = textPaint(palette.muted, wmH * 0.30f, reg)
        val wt = wmPaint.descent() - wmPaint.ascent()
        val bt = bkPaint.descent() - bkPaint.ascent()
        val lockH = wt + bt * 0.35f + bt
        val lockY = wy + (wmH - lockH) / 2f
        canvas.drawText("MEOWSIC", wmX, lockY - wmPaint.ascent(), wmPaint)
        canvas.drawText("by kanz", wmX, lockY + wt + bt * 0.35f - bkPaint.ascent(), bkPaint)

        // Lepas bitmap antara supaya ganti gaya berulang tidak menumpuk memori.
        zoomed?.recycle()
        bgBlur?.recycle()
        region?.recycle()
        frost?.recycle()

        return bitmap
    }

    private fun textPaint(color: Int, size: Float, typeface: Typeface): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
        }

    private fun drawCoverThumb(
        canvas: Canvas,
        context: Context,
        cover: Bitmap?,
        rect: RectF,
        palette: CardPalette,
    ) {
        val radius = rect.width() * 0.18f
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        if (cover != null) {
            val side = min(cover.width, cover.height)
            val src = if (cover.width != side || cover.height != side) {
                Bitmap.createBitmap(cover, (cover.width - side) / 2, (cover.height - side) / 2, side, side)
            } else cover
            canvas.drawBitmap(src, null, rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
        } else {
            canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(palette.fg, 0.14f) })
            val inset = rect.width() * 0.24f
            val inner = RectF(rect).apply { inset(inset, inset) }
            drawMeowsicMark(canvas, context, inner, palette.fg)
        }
        canvas.restore()
    }

    private fun drawMeowsicMark(canvas: Canvas, context: Context, rect: RectF, tint: Int) {
        val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.small_icon, context.theme) ?: return
        drawable.setTint(tint)
        drawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        drawable.draw(canvas)
    }

    private fun wrapWords(paint: Paint, text: String, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf(text.trim())
        val lines = ArrayList<String>()
        var cur = ""
        for (w in words) {
            val candidate = if (cur.isEmpty()) w else "$cur $w"
            if (cur.isEmpty() || paint.measureText(candidate) <= maxWidth) {
                cur = candidate
            } else {
                lines.add(cur)
                cur = w
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || text.isEmpty()) return text
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "\u2026"
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            val candidate = text.substring(0, mid) + ellipsis
            if (paint.measureText(candidate) <= maxWidth) low = mid else high = mid - 1
        }
        return text.substring(0, low) + ellipsis
    }

    /** Warna dominan cover: hitung bucket warna, skor berdasarkan frekuensi x saturasi x value. */
    private fun dominantColor(cover: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(cover, 80, 80, true)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val counts = HashMap<Int, Int>()
        for (pix in px) {
            // Lewati pixel transparan (PNG cover) supaya warna dominan tidak jadi hitam.
            if (((pix ushr 24) and 0xFF) < 32) continue
            val r = (pix shr 16) and 0xFF
            val g = (pix shr 8) and 0xFF
            val b = pix and 0xFF
            val q = ((r and 0xF8) shl 16) or ((g and 0xF8) shl 8) or (b and 0xF8)
            counts[q] = (counts[q] ?: 0) + 1
        }
        var best = 0xFF7A9EBB.toInt()
        var bestScore = -1.0
        for ((c, n) in counts) {
            val rf = (c shr 16 and 0xFF) / 255f
            val gf = (c shr 8 and 0xFF) / 255f
            val bf = (c and 0xFF) / 255f
            val maxc = max(rf, max(gf, bf))
            val minc = min(rf, min(gf, bf))
            val s = if (maxc == 0f) 0f else (maxc - minc) / maxc
            val score = n * (0.15 + s) * (0.28 + maxc)
            if (score > bestScore) {
                bestScore = score
                best = (0xFF shl 24) or (c and 0xFFFFFF)
            }
        }
        if (small !== cover) small.recycle()
        return best
    }

    private fun zoomCover(cover: Bitmap, size: Int, zoom: Float): Bitmap {
        val side = min(cover.width, cover.height)
        val src = if (cover.width != side || cover.height != side) {
            Bitmap.createBitmap(cover, (cover.width - side) / 2, (cover.height - side) / 2, side, side)
        } else cover
        val target = (size * zoom).toInt()
        val scaled = Bitmap.createScaledBitmap(src, target, target, true)
        val off = (target - size) / 2
        return Bitmap.createBitmap(scaled, off, off, size, size)
    }

    /** Blur murah & mulus: perkecil lalu perbesar lagi (bilinear). */
    private fun blurDownscale(bmp: Bitmap, factor: Float): Bitmap {
        val sw = max(1, (bmp.width * factor).toInt())
        val sh = max(1, (bmp.height * factor).toInt())
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        val out = Bitmap.createScaledBitmap(small, bmp.width, bmp.height, true)
        if (small !== out) small.recycle()
        return out
    }

    private fun shade(c: Int, f: Float): Int {
        val r = (((c shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = ((((a shr 16) and 0xFF) * (1 - t) + ((b shr 16) and 0xFF) * t).toInt()).coerceIn(0, 255)
        val g = ((((a shr 8) and 0xFF) * (1 - t) + ((b shr 8) and 0xFF) * t).toInt()).coerceIn(0, 255)
        val bl = (((a and 0xFF) * (1 - t) + (b and 0xFF) * t).toInt()).coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun luma(c: Int): Float =
        0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}

/** Bagikan bitmap kartu lewat share sheet Android. */
private suspend fun shareLyricsImage(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "lyrics_shares").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "meowsic_lyrics.png").also { target ->
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        context.startActivity(Intent.createChooser(intent, null))
    }
}

/** Simpan bitmap kartu ke galeri (Pictures/Meowsic). Mengembalikan true bila berhasil. */
private suspend fun saveLyricsImage(context: Context, bitmap: Bitmap): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val name = "meowsic_lirik_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Meowsic",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: context.filesDir
                val dir = File(base, "Meowsic").apply { mkdirs() }
                FileOutputStream(File(dir, name)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        }.getOrElse {
            Timber.e(it, "Failed to save lyrics image")
            false
        }
    }

/**
 * Dialog pemilih gaya kartu + pilih baris lirik, dengan preview kartu yang sudah dirender ke
 * bitmap (preview = hasil akhir), tombol Unduh PNG, dan tombol Bagikan.
 */
@Composable
fun LyricsShareDialog(
    lines: List<String>,
    style: LyricsShareStyle,
    onStyleChange: (LyricsShareStyle) -> Unit,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    selectedText: String,
    onLineClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lyricsToRender = remember(selectedText, lines) {
        selectedText.ifBlank { lines.joinToString("\n") }.trim()
    }

    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(thumbnailUrl) {
        cover = withContext(Dispatchers.IO) {
            val url = thumbnailUrl ?: return@withContext null
            runCatching {
                val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                ImageLoader(context).execute(request).image?.toBitmap()
            }.getOrNull()
        }
    }

    LaunchedEffect(lyricsToRender, style, cover, title, artist) {
        preview = withContext(Dispatchers.Default) {
            LyricsCardRenderer.render(
                context = context,
                style = style,
                title = title,
                artist = artist,
                cover = cover,
                lyrics = lyricsToRender,
                size = 720,
            )
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.share_lyrics)) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.width(4.dp))
            TextButton(
                enabled = preview != null && !saving,
                onClick = {
                    preview?.let { bitmap ->
                        saving = true
                        scope.launch {
                            val saved = saveLyricsImage(context, bitmap)
                            saving = false
                            Toast.makeText(
                                context,
                                context.getString(
                                    if (saved) R.string.share_saved_to_gallery
                                    else R.string.share_save_failed,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.share_download_png))
            }
            TextButton(
                enabled = preview != null,
                onClick = {
                    preview?.let { bitmap ->
                        scope.launch { shareLyricsImage(context, bitmap) }
                    }
                },
            ) {
                Text(stringResource(R.string.share))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LyricsShareStyle.entries.forEach { candidate ->
                val selected = candidate == style
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStyleChange(candidate) },
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (candidate == LyricsShareStyle.GLASS)
                                stringResource(R.string.share_style_color)
                            else stringResource(R.string.share_style_light),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            val bitmap = preview
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.share_pick_lines),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            lines.forEachIndexed { index, line ->
                val selected = onSelectionCheck(selectedText, lines, index)
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                        )
                        .clickable { onLineClick(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun parseSelection(selectedText: String, lines: List<String>): IntRange? {
    if (selectedText.isBlank()) return null
    val selection = selectedText.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (selection.isEmpty()) return null
    val first = lines.indexOf(selection.first())
    if (first < 0) return null
    return first..min(first + selection.size - 1, lines.lastIndex)
}

private fun onSelectionCheck(selectedText: String, lines: List<String>, index: Int): Boolean {
    val selection = parseSelection(selectedText, lines) ?: return false
    return index in selection
}

/** Lirik mentah (LRC/terformat) -> teks polos multi-baris. */
fun plainLyricsOf(lyrics: String): String =
    if (lyrics.startsWith("[")) {
        LyricsUtils.parseLyrics(lyrics).joinToString("\n") { it.text }
    } else {
        lyrics
    }
