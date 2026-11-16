/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Dialog pratinjau bagikan statistik ala Metrolist v14
 * (StatisticsShareDialog): kartu statistik dirender dulu, ditampilkan
 * sebagai pratinjau, baru dibagikan lewat share sheet. Tambahan dari
 * Meowsic: tombol Download di samping Bagikan untuk menyimpan gambar
 * PNG-nya ke folder Download.
 */
@Composable
fun StatsShareDialog(
    sections: List<Pair<String, List<StatsShareItem>>>,
    period: String,
    summary: String,
    plays: String,
    duration: String,
    playsLabel: String,
    timeLabel: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val direction = LocalLayoutDirection.current
    val statsTitle = stringResource(R.string.stats)
    val shareLabel = stringResource(R.string.share)
    val downloadedMessage = stringResource(R.string.stats_share_downloaded)
    val downloadFailedMessage = stringResource(R.string.stats_share_download_failed)
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(sections, period, summary, plays, duration) {
        bitmap = null
        failed = false
        try {
            val artwork = loadStatsShareArtwork(context, sections.flatMap { it.second })
            bitmap =
                withContext(Dispatchers.Default) {
                    renderStatsShareImage(
                        appName = "Meowsic",
                        title = statsTitle,
                        period = period,
                        summary = summary,
                        plays = plays,
                        duration = duration,
                        playsLabel = playsLabel,
                        timeLabel = timeLabel,
                        sections = sections,
                        artwork = artwork,
                        colors = colors,
                        typography = typography,
                        textMeasurer = measurer,
                        layoutDirection = direction,
                    )
                }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(stringResource(R.string.statistics_share)) },
        text = {
            Column {
                bitmap?.let { image ->
                    Image(
                        image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    )
                }
                if ((bitmap == null && !failed) || busy) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                if (failed) {
                    Text(
                        stringResource(R.string.stats_share_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = bitmap != null && !busy,
                    onClick = {
                        val image = bitmap ?: return@TextButton
                        busy = true
                        scope.launch {
                            try {
                                val fileName = "Meowsic-Statistik-${LocalDate.now()}"
                                val ok = downloadStatsImage(context, image, fileName)
                                Toast.makeText(
                                    context,
                                    if (ok) downloadedMessage else downloadFailedMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.download))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = bitmap != null && !busy,
                    onClick = {
                        val image = bitmap ?: return@Button
                        busy = true
                        failed = false
                        scope.launch {
                            try {
                                shareStatsImage(context, image, shareLabel)
                                onDismiss()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(shareLabel)
                }
            }
        },
    )
}
