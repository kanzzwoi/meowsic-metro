/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.listentogether.RoomChatClient

/**
 * Kotak antrian lagu yang disarankan member.
 *
 * Berbeda dengan pending suggestion bawaan server Together (hanya terlihat
 * oleh host), daftar ini disimpan di relay sehingga semua anggota room
 * melihat lagu yang sama.
 */
@Composable
fun RoomSuggestionBox(
    suggestions: List<RoomChatClient.RoomSuggestion>,
    isHost: Boolean,
    queueMode: String,
    timerRemainingMs: Long,
    onPlayNow: (RoomChatClient.RoomSuggestion) -> Unit,
    onAddToQueue: (RoomChatClient.RoomSuggestion) -> Unit,
    onRemove: (RoomChatClient.RoomSuggestion) -> Unit,
    onToggleRepeat: () -> Unit,
    onSetTimer: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.room_suggestions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                // Tombol ulang: seperti repeat playlist, bukan repeat 1 lagu.
                TextButton(onClick = onToggleRepeat) {
                    Icon(
                        painter =
                            painterResource(
                                if (queueMode == "repeat") R.drawable.repeat_on else R.drawable.repeat,
                            ),
                        contentDescription = stringResource(R.string.room_suggestion_repeat),
                        tint =
                            if (queueMode == "repeat") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.room_suggestion_repeat),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Timer room: berhenti bareng di semua anggota.
            RoomTimerRow(
                timerRemainingMs = timerRemainingMs,
                onSetTimer = onSetTimer,
            )

            Spacer(Modifier.height(8.dp))

            if (suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.room_suggestions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.room_suggestions_empty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                suggestions.forEach { suggestion ->
                    RoomSuggestionRow(
                        suggestion = suggestion,
                        isHost = isHost,
                        onPlayNow = { onPlayNow(suggestion) },
                        onAddToQueue = { onAddToQueue(suggestion) },
                        onRemove = { onRemove(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomSuggestionRow(
    suggestion: RoomChatClient.RoomSuggestion,
    isHost: Boolean,
    onPlayNow: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            if (suggestion.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = suggestion.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestion.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.room_suggestion_by, suggestion.fromUsername),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isHost) {
            // Host bisa langsung memutar sekarang (skip lagu yang berjalan).
            TextButton(onClick = onPlayNow) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.room_suggestion_play_now),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.room_suggestion_play_now),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onAddToQueue) {
                Icon(
                    painter = painterResource(R.drawable.playlist_add),
                    contentDescription = stringResource(R.string.room_suggestion_queue),
                    modifier = Modifier.size(18.dp),
                )
            }
            TextButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.room_suggestion_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Baris timer room: tombol durasi + sisa waktu berjalan. */
@Composable
private fun RoomTimerRow(
    timerRemainingMs: Long,
    onSetTimer: (Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(R.drawable.timer),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (timerRemainingMs > 0L) {
            Text(
                text = formatTimerRemaining(timerRemainingMs),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSetTimer(0L) }) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.room_suggestion_timer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            RoomTimerPresetChip(stringResource(R.string.room_timer_5m), 5L * 60_000L, onSetTimer)
            RoomTimerPresetChip(stringResource(R.string.room_timer_15m), 15L * 60_000L, onSetTimer)
            RoomTimerPresetChip(stringResource(R.string.room_timer_30m), 30L * 60_000L, onSetTimer)
        }
    }
}

@Composable
private fun RoomTimerPresetChip(
    label: String,
    durationMs: Long,
    onSetTimer: (Long) -> Unit,
) {
    TextButton(
        onClick = { onSetTimer(durationMs) },
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Format sisa waktu timerroom jadi mm:ss. */
private fun formatTimerRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
