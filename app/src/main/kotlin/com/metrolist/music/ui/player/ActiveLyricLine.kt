/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.isActive

/**
 * Baris lirik aktif di full player, ala Metrolist v14: satu baris lirik yang sedang
 * dinyanyikan tampil di bawah artwork, di atas judul.
 *
 * Posisi liriknya SAMA dengan tampilan lirik penuh dan v14: posisi putar +
 * offset lirik per lagu (song.lyricsOffset, yang bisa diubah dari menu lirik).
 * Dulu baris ini memakai posisi mentah tanpa offset, jadi kalau offset lagu
 * itu negatif, baris ini mendahului yang sedang dinyanyikan — mis. menampilkan
 * "(Hooo)" padahal yang dinyanyikan masih "Ada cinta seperti cintamu kepadaku".
 * v14 memakai satu state posisi yang sama (lyricsPositionState, sudah termasuk
 * timingOffsetMs) untuk baris ini dan tampilan lirik penuh; di sini disamakan.
 *
 * Akhir baris juga mengikuti v14 (LyricsUtils.getLineEndTime): akhir kata
 * terlambat, lalu awal baris berikut, lalu +5 detik untuk baris terakhir.
 *
 * Cara v14 menyalakan kata: bukan pop perkata, tapi sapuan — batas terang/redup
 * berjalan mengikuti posisi putar dan menembus kata yang sedang dinyanyikan,
 * dengan tepi yang lembut selebar ~1,25x ukuran font (SimpleWordAnimation v14).
 * Di sini disederhanakan jadi alpha per karakter (smoothstep di sekitar batas
 * yang sedang dinyanyikan), tanpa mesin syllable/cluster v14 yang ribuan baris.
 * Ganti baris: fade + scale kecil ala LineLook v14 (baris baru masuk redup dan
 * sedikit mengecil dulu, lalu tajam) — efek "burem dulu" yang diminta.
 * Ukuran teks 0.8x dari titleMedium.
 *
 * Baris sembunyi sendiri saat jeda antar-baris, sebelum baris pertama, dan
 * sesudah baris terakhir — supaya judul tidak loncat, tinggi 2 baris
 * dicadangkan dan visibilitas dianimasikan.
 */
@Composable
fun ActiveLyricLine(
    positionProvider: () -> Long,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val lyricsOffset = (currentSong?.song?.lyricsOffset ?: 0).toLong()
    val lines =
        remember(currentLyrics) {
            currentLyrics
                ?.lyrics
                ?.takeUnless { it == LyricsEntity.LYRICS_NOT_FOUND }
                ?.let { LyricsUtils.filterLyricsCreditLines(it) }
                ?.trim()
                ?.let { LyricsUtils.parseLyrics(it) }
                ?: emptyList()
        }

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            position = positionProvider()
            withFrameMillis { }
        }
    }
    // Posisi lirik = posisi putar + offset lirik lagu ini (sama seperti
    // tampilan lirik penuh dan v14). Semua logika baris di bawah memakai
    // ini, bukan posisi mentah.
    val lyricsPosition = position + lyricsOffset

    val currentIndex = remember(lines, lyricsPosition) { LyricsUtils.findCurrentLineIndex(lines, lyricsPosition) }
    val currentLine = lines.getOrNull(currentIndex)
    val nextLine = lines.getOrNull(currentIndex + 1)
    val lineEndTime =
        remember(currentLine, nextLine) {
            if (currentLine == null) {
                0L
            } else {
                // ala v14 (LyricsUtils.getLineEndTime): akhir kata
                // terlambat (bukan kata terakhir di daftar), lalu awal
                // baris berikut, lalu +5 detik untuk baris terakhir.
                val wordsEnd =
                    currentLine.words?.maxOfOrNull { (it.endTime * 1000).toLong() }
                when {
                    wordsEnd != null -> wordsEnd
                    nextLine != null -> nextLine.time
                    else -> (currentLine.time + 5000L).coerceAtMost(Long.MAX_VALUE - 2000L)
                }.coerceAtMost(Long.MAX_VALUE - 2000L)
            }
        }
    val isVisible =
        remember(currentLine, currentIndex, lyricsPosition, lineEndTime, nextLine, lines) {
            if (currentLine == null) {
                false
            } else {
                val inSignificantGap =
                    nextLine != null &&
                        lyricsPosition > lineEndTime &&
                        (nextLine.time - lineEndTime) > 5000L
                val afterLastLine =
                    nextLine == null && lyricsPosition > lineEndTime + 2000L
                val isBeforeFirstLine =
                    currentIndex == -1 &&
                        (lines.firstOrNull()?.time ?: 0L) > 5000L &&
                        lyricsPosition < (lines.firstOrNull()?.time ?: 0L)
                !inSignificantGap && !afterLastLine && !isBeforeFirstLine
            }
        }

    val baseStyle = MaterialTheme.typography.titleMedium
    val style =
        baseStyle.copy(
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = baseStyle.fontSize * 0.8f,
            lineHeight = baseStyle.lineHeight * 0.8f,
        )
    val reservedLineHeight =
        with(LocalDensity.current) { style.lineHeight.toDp() * 2 }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 16.dp, bottom = 12.dp)
                    // Naik 8dp lagi TANPA mengubah tinggi blok: offset hanya
                    // menggeser tampilan, jadi ukuran layout tetap dan
                    // artwork di atas (Box weight 1f) tidak ikut naik.
                    // Padding bawah 12dp sebelumnya sengaja dipertahankan
                    // supaya artwork tidak turun kembali.
                    .offset(y = -8.dp)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = currentLine to lineEndTime,
                transitionSpec = {
                    // Baris baru masuk redup + sedikit mengecil, lalu tajam.
                    (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.96f, animationSpec = tween(400))) togetherWith
                        (fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.96f, animationSpec = tween(400)))
                },
                contentAlignment = Alignment.Center,
                label = "activeLyricLineSwitch",
                modifier =
                    Modifier
                        .sizeIn(minHeight = reservedLineHeight)
                        .wrapContentHeight(Alignment.CenterVertically),
            ) { (line, _) ->
                if (line != null) {
                    Text(
                        text = rememberSweptLyrics(line, lyricsPosition, textColor),
                        style = style,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * Teks baris lirik dengan sapuan terang mengikuti posisi putar.
 *
 * Batas terang dihitung sebagai posisi karakter (bisa pecahan) di dalam kata
 * yang sedang dinyanyikan; tiap karakter diberi alpha lewat smoothstep di
 * sekitar batas itu (jendela ~3 karakter, meniru tepi lembut gradient v14
 * yang selebar 1,25x ukuran font). Kata yang sudah lewat terang penuh,
 * kata berikutnya redup (alpha 0.4).
 */
@Composable
private fun rememberSweptLyrics(
    line: com.metrolist.music.lyrics.LyricsEntry,
    position: Long,
    textColor: Color,
) = remember(line, position, textColor) {
    buildAnnotatedString {
        val words = line.words
        if (words.isNullOrEmpty()) {
            withStyle(SpanStyle(color = textColor)) {
                append(line.text)
            }
            return@buildAnnotatedString
        }

        // Teks utuh + posisi awal tiap kata (termasuk spasi sesudah kata).
        val fullText = StringBuilder()
        val wordStartChars = IntArray(words.size)
        for ((index, word) in words.withIndex()) {
            wordStartChars[index] = fullText.length
            fullText.append(word.text)
            if (word.hasTrailingSpace) fullText.append(" ")
        }
        val text = fullText.toString()

        // Batas terang (posisi karakter, bisa pecahan) dari kata yang dinyanyikan.
        var boundary = 0f
        for ((index, word) in words.withIndex()) {
            val startMs = (word.startTime * 1000).toLong()
            val endMs = (word.endTime * 1000).toLong()
            val wordEndChar = wordStartChars[index] + word.text.length
            if (position < startMs) {
                boundary = wordStartChars[index].toFloat()
                break
            }
            if (position >= endMs || endMs <= startMs) {
                boundary = wordEndChar.toFloat()
                continue
            }
            val fraction = (position - startMs).toFloat() / (endMs - startMs).toFloat()
            boundary = wordStartChars[index] + fraction * word.text.length
            break
        }
        if (words.isNotEmpty() && position >= ((words.last().endTime) * 1000).toLong()) {
            boundary = text.length.toFloat()
        }

        // Alpha per karakter; karakter berurutan dengan alpha sama digabung.
        var runStart = 0
        var runAlpha = charAlpha(0, boundary)
        for (i in 1..text.length) {
            val alpha = if (i < text.length) charAlpha(i, boundary) else -1f
            if (alpha != runAlpha || i == text.length) {
                withStyle(SpanStyle(color = textColor.copy(alpha = runAlpha))) {
                    append(text.substring(runStart, i))
                }
                runStart = i
                runAlpha = alpha
            }
        }
    }
}

/** Alpha karakter ke-[index] terhadap batas terang [boundary]. */
private fun charAlpha(index: Int, boundary: Float): Float {
    val d = boundary - (index + 0.5f)
    val t = ((d + 1.5f) / 3f).coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    return (0.4f + 0.6f * smooth).let {
        // Kuantisasi supaya karakter berurutan gampang digabung jadi satu span.
        (it * 20f).toInt() / 20f
    }
}
