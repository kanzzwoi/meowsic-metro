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
import android.text.Layout
import android.text.StaticLayout
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
import kotlin.math.min

/** Gaya kartu share lirik: [COLOR] = kartu biru dusty + teks navy, [LIGHT] = kartu off-white + teks gelap. */
enum class LyricsShareStyle { COLOR, LIGHT }

/** Palet warna per gaya kartu. Warna disimpan sebagai ARGB int. */
private data class CardPalette(
    val outer: Int,
    val card: Int,
    val fg: Int,
    val muted: Int,
    val shadow: Boolean,
)

private fun paletteFor(style: LyricsShareStyle): CardPalette = when (style) {
    LyricsShareStyle.COLOR -> CardPalette(
        outer = 0xFF6E91AF.toInt(),
        card = 0xFF7A9EBB.toInt(),
        fg = 0xFF18384D.toInt(),
        muted = 0x9918384D.toInt(),
        shadow = false,
    )

    LyricsShareStyle.LIGHT -> CardPalette(
        outer = 0xFFEBEBEB.toInt(),
        card = 0xFFF7F7F7.toInt(),
        fg = 0xFF111111.toInt(),
        muted = 0x99111111.toInt(),
        shadow = true,
    )
}

/**
 * Merender kartu lirik menjadi [Bitmap] persegi (default 1080x1080) memakai [Canvas],
 * sehingga preview di dialog identik dengan hasil unduhan/share (WYSIWYG) dan penempatan
 * elemen bisa dikontrol persis, tanpa bergantung pada screenshot view.
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
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = paletteFor(style)

        val margin = size * 0.055f
        val cardWidth = size - margin * 2f
        val cardRadius = cardWidth * 0.055f
        val card = RectF(margin, margin, margin + cardWidth, margin + cardWidth)

        canvas.drawColor(palette.outer)

        if (palette.shadow) {
            val shadow = RectF(card).apply { offset(0f, size * 0.010f) }
            canvas.drawRoundRect(
                shadow,
                cardRadius,
                cardRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F000000 },
            )
        }
        canvas.drawRoundRect(
            card,
            cardRadius,
            cardRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.card },
        )

        val padding = cardWidth * 0.075f
        val left = margin + padding
        val right = margin + cardWidth - padding
        val contentWidth = right - left
        val top = margin + padding

        // Header: cover kiri-atas + judul/artis di kanannya.
        val coverSize = contentWidth * 0.16f
        val coverRect = RectF(left, top, left + coverSize, top + coverSize)
        drawCover(canvas, context, cover, coverRect, palette)

        val titleSize = coverSize * 0.40f
        val artistSize = coverSize * 0.28f
        val textX = coverRect.right + contentWidth * 0.05f
        val maxTextWidth = right - textX

        // Font kartu lirik: Plus Jakarta Sans (regular + bold), fallback ke SANS_SERIF sistem
        // kalau font gagal dimuat.
        val jakartaRegular = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_regular)
        val jakartaBold = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold)

        val titlePaint = textPaint(palette.fg, titleSize, bold = true, jakartaRegular, jakartaBold)
        val artistPaint = textPaint(palette.muted, artistSize, bold = false, jakartaRegular, jakartaBold)

        val blockHeight = titleSize + artistSize * 1.45f
        val blockTop = coverRect.centerY() - blockHeight / 2f
        canvas.drawText(
            ellipsize(title, titlePaint, maxTextWidth),
            textX,
            blockTop + titleSize,
            titlePaint,
        )
        canvas.drawText(
            ellipsize(artist, artistPaint, maxTextWidth),
            textX,
            blockTop + titleSize + artistSize * 1.45f,
            artistPaint,
        )

        // Footer: logo Meowsic (ikon vektor + wordmark) di kiri-bawah.
        val iconSize = contentWidth * 0.085f
        val logoBottom = margin + cardWidth - padding
        val iconRect = RectF(left, logoBottom - iconSize, left + iconSize, logoBottom)
        drawMeowsicMark(canvas, context, iconRect, palette)
        val wordPaint = textPaint(palette.fg, iconSize * 0.82f, bold = true, jakartaRegular, jakartaBold).apply {
            letterSpacing = 0.06f
        }
        val wordX = iconRect.right + contentWidth * 0.03f
        val wordBaseline = iconRect.centerY() - (wordPaint.ascent() + wordPaint.descent()) / 2f
        canvas.drawText("MEOWSIC", wordX, wordBaseline, wordPaint)

        // Lirik: mengisi area antara header dan footer, auto-fit, semua baris tampil tanpa dipotong.
        val lyricsTop = coverRect.bottom + contentWidth * 0.10f
        val lyricsBottom = iconRect.top - contentWidth * 0.06f
        drawLyrics(
            canvas = canvas,
            text = lyrics,
            x = left,
            yTop = lyricsTop,
            widthPx = contentWidth.toInt(),
            heightPx = (lyricsBottom - lyricsTop).toInt(),
            palette = palette,
            jakartaRegular = jakartaRegular,
            jakartaBold = jakartaBold,
        )

        return bitmap
    }

    private fun textPaint(
        color: Int,
        size: Float,
        bold: Boolean,
        jakartaRegular: Typeface? = null,
        jakartaBold: Typeface? = null,
    ): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) {
                jakartaBold ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else {
                jakartaRegular ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
        }

    private fun drawCover(
        canvas: Canvas,
        context: Context,
        cover: Bitmap?,
        rect: RectF,
        palette: CardPalette,
    ) {
        val radius = rect.width() * 0.06f
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        if (cover != null) {
            canvas.drawBitmap(
                cover,
                null,
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true },
            )
        } else {
            canvas.drawRect(
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(palette.fg, 0.14f) },
            )
            val inset = rect.width() * 0.24f
            val inner = RectF(rect).apply { inset(inset, inset) }
            drawMeowsicMark(canvas, context, inner, palette)
        }
        canvas.restore()
    }

    private fun drawMeowsicMark(
        canvas: Canvas,
        context: Context,
        rect: RectF,
        palette: CardPalette,
    ) {
        val drawable = ResourcesCompat.getDrawable(
            context.resources,
            R.drawable.small_icon,
            context.theme,
        ) ?: return
        drawable.setTint(palette.fg)
        drawable.setBounds(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt(),
        )
        drawable.draw(canvas)
    }

    private fun drawLyrics(
        canvas: Canvas,
        text: String,
        x: Float,
        yTop: Float,
        widthPx: Int,
        heightPx: Int,
        palette: CardPalette,
        jakartaRegular: Typeface? = null,
        jakartaBold: Typeface? = null,
    ) {
        val content = text.trim()
        if (content.isEmpty() || widthPx <= 0 || heightPx <= 0) return

        val paint = textPaint(palette.fg, widthPx * 0.085f, bold = true, jakartaRegular, jakartaBold)
        val minSize = widthPx * 0.038f
        var size = widthPx * 0.085f
        paint.textSize = size
        var layout = buildLayout(content, paint, widthPx)
        var guard = 0
        while (layout.height > heightPx && size > minSize && guard < 80) {
            size *= 0.94f
            paint.textSize = size
            layout = buildLayout(content, paint, widthPx)
            guard++
        }

        canvas.save()
        canvas.translate(x, yTop)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildLayout(text: String, paint: TextPaint, widthPx: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.12f)
            .build()

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
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
                            text = if (candidate == LyricsShareStyle.COLOR)
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
