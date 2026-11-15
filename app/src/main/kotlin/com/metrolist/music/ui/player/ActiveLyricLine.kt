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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.metrolist.music.lyrics.LyricsEntity
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.isActive

/**
 * Baris lirik aktif di full player, ala Metrolist v14: satu baris lirik yang sedang
 * dinyanyikan tampil di bawah artwork, di atas judul. Kata yang sudah lewat tampil
 * terang, kata berikutnya redup; ganti baris pakai fade. Baris sembunyi sendiri saat
 * jeda antar-baris, sebelum baris pertama, dan sesudah baris terakhir — supaya judul
 * tidak loncat, tinggi 2 baris dicadangkan dan visibilitas dianimasikan.
 */
@Composable
fun ActiveLyricLine(
    positionProvider: () -> Long,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
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

    val currentIndex = remember(lines, position) { LyricsUtils.findCurrentLineIndex(lines, position) }
    val currentLine = lines.getOrNull(currentIndex)
    val lineEndTime =
        remember(currentLine, lines, currentIndex) {
            if (currentLine == null) {
                0L
            } else {
                val lastWordEnd =
                    currentLine.words
                        ?.lastOrNull()
                        ?.endTime
                        ?.times(1000)
                        ?.toLong()
                lastWordEnd ?: lines.getOrNull(currentIndex + 1)?.time ?: Long.MAX_VALUE
            }
        }
    val nextLine = lines.getOrNull(currentIndex + 1)
    val isVisible =
        remember(currentLine, currentIndex, position, lineEndTime, nextLine, lines) {
            if (currentLine == null) {
                false
            } else {
                val inSignificantGap =
                    nextLine != null &&
                        position > lineEndTime &&
                        (nextLine.time - lineEndTime) > 5000L
                val afterLastLine =
                    nextLine == null && position > lineEndTime + 2000L
                // currentIndex -1 (sebelum baris pertama) membuat currentLine null,
                // jadi isVisible sudah false lewat pemeriksaan di atas.
                !inSignificantGap && !afterLastLine
            }
        }

    val style =
        MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = currentLine to lineEndTime,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400)))
                },
                contentAlignment = Alignment.Center,
                label = "activeLyricLineSwitch",
                modifier =
                    Modifier
                        .sizeIn(minHeight = reservedLineHeight)
                        .wrapContentHeight(Alignment.CenterVertically),
            ) { (line, _) ->
                if (line != null) {
                    val annotated =
                        buildAnnotatedString {
                            val words = line.words
                            if (words.isNullOrEmpty()) {
                                withStyle(SpanStyle(color = textColor)) {
                                    append(line.text)
                                }
                            } else {
                                for (word in words) {
                                    val sung = position >= (word.startTime * 1000).toLong()
                                    withStyle(
                                        SpanStyle(
                                            color =
                                                if (sung) textColor
                                                else textColor.copy(alpha = 0.4f),
                                        ),
                                    ) {
                                        append(word.text)
                                    }
                                    if (word.hasTrailingSpace) append(" ")
                                }
                            }
                        }
                    Text(
                        text = annotated,
                        style = style,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
