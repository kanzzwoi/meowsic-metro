/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.metrolist.music.ui.component.DefaultDialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/** Gaya kartu share lirik: [COLOR] = kartu warna + logo putih, [LIGHT] = kartu putih + logo hitam. */
enum class LyricsShareStyle { COLOR, LIGHT }

/**
 * Render kartu lirik jadi PNG lalu buka share sheet Android.
 * [view] adalah view kartu hasil [onCardView]; bitmap diambil dari view itu sendiri.
 */
@Composable
fun rememberLyricsCardSharer(): (android.view.View, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return { view, lyricsText ->
        scope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.Main) {
                    val bmp = android.graphics.Bitmap.createBitmap(
                        view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888,
                    )
                    view.draw(android.graphics.Canvas(bmp))
                    bmp
                }
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "lyrics_shares").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    File(dir, "lyrics.png").also { f ->
                        FileOutputStream(f).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }.onFailure {
                Timber.e(it, "Failed to share lyrics card")
                // fallback: kirim sebagai teks
                runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, lyricsText)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            }
        }
    }
}

/**
 * Pembungkus yang menyediakan view untuk di-screenshot. [content] menerima Modifier wajib
 * dipasang di root kartu supaya ukurannya terekam.
 */
@Composable
fun LyricsShareComposer(
    style: LyricsShareStyle,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    onCardClick: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val isLight = style == LyricsShareStyle.LIGHT
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCardClick),
        color = if (isLight) Color.White else Color(0xFF1E3A8A),
        shape = RoundedCornerShape(20.dp),
    ) {
        content(Modifier.fillMaxSize())
    }
}

/**
 * Isi kartu: thumbnail + judul/artist di atas, lirik terpilih di tengah, logo + MEOWSIC di bawah.
 */
@Composable
fun LyricsCardBody(
    text: String,
    style: LyricsShareStyle,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    val isLight = style == LyricsShareStyle.LIGHT
    val fg = if (isLight) Color(0xFF111111) else Color.White
    val fgMuted = if (isLight) Color(0xFF666666) else Color.White.copy(alpha = 0.78f)
    val logo = if (isLight) R.drawable.ic_meowsic_logo_black else R.drawable.ic_meowsic_logo_white
    val long = text.length > 120

    Column(modifier = modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLight) Color(0xFFEDEDED) else Color.White.copy(alpha = 0.15f)),
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    color = fgMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = text,
            color = fg,
            fontSize = if (long) 17.sp else 24.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = if (long) 24.sp else 32.sp,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "MEOWSIC",
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Text(text = "by kanz", color = fgMuted, fontSize = 9.sp)
            }
        }
    }
}


/**
 * Dialog pemilih gaya kartu + pilih baris lirik, dengan preview kartu yang bisa langsung dibagikan.
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
    onCardView: (LayoutCoordinates, android.view.View) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val previewText = selectedText.ifBlank {
        lines.take(4).joinToString("\n").ifBlank { stringResource(R.string.lyrics) }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.share_lyrics)) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onShare, enabled = lines.isNotEmpty()) {
                Text(stringResource(R.string.share_as_image))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LyricsShareStyle.entries.forEach { s ->
                val selected = s == style
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStyleChange(s) },
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (s == LyricsShareStyle.COLOR) stringResource(R.string.share_style_color)
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

        val cardHost = LocalView.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(4f / 5f)
                .onGloballyPositioned { onCardView(it, cardHost) },
        ) {
            LyricsCardBody(
                text = previewText,
                style = style,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.share_pick_lines),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            lines.forEachIndexed { index, line ->
                val selected = onSelectionCheck(selectedText, lines, index)
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onLineClick(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun parseSelection(selectedText: String, lines: List<String>): IntRange? {
    if (selectedText.isBlank()) return null
    val sel = selectedText.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (sel.isEmpty()) return null
    val first = lines.indexOf(sel.first())
    if (first < 0) return null
    return first..(first + sel.size - 1).coerceAtMost(lines.lastIndex)
}

private fun onSelectionCheck(selectedText: String, lines: List<String>, index: Int): Boolean {
    val cur = parseSelection(selectedText, lines) ?: return false
    return index in cur
}

/** Lirik mentah (LRC/terformat) → teks polos multi-baris. */
fun plainLyricsOf(lyrics: String): String =
    if (lyrics.startsWith("[")) {
        LyricsUtils.parseLyrics(lyrics).joinToString("\n") { it.text }
    } else {
        lyrics
    }
