/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.DefaultDialog

/**
 * Dialog pemilih playlist akun, ala Metrolist v14 (bagian picker dari
 * BackupRestoreScreen: setelah login, daftar playlist akun ditampilkan
 * dan pengguna memilih satu untuk diimpor). v14 memakai bottom sheet
 * dengan multi-pilih untuk Apple/Spotify; di sini satu pilih untuk
 * keempat provider supaya alurnya seragam — impor satu playlist per
 * sekali jalan, sama seperti impor tautan publik.
 *
 * Komponen ini presentasional saja: pemanggil (ViewModel/UI) yang
 * memuat daftar lewat manager (loadPlaylists), lalu memanggil
 * choice.loadTracks untuk playlist yang dipilih dan meneruskannya ke
 * PlaylistImport.resolveTracks + importToNewPlaylist.
 */
@Composable
fun AccountPlaylistPickerDialog(
    providerName: String,
    playlists: List<AccountPlaylistChoice>?,
    isLoading: Boolean,
    error: String?,
    onPick: (AccountPlaylistChoice) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(providerName) },
        horizontalAlignment = Alignment.Start,
    ) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.import_loading_playlists),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }

            playlists.isNullOrEmpty() -> {
                Text(
                    text = stringResource(R.string.import_no_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                ) {
                    items(playlists, key = { it.playlist.id }) { choice ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(choice) }
                                    .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = choice.playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (choice.playlist.trackCount > 0) {
                                Text(
                                    text = stringResource(R.string.import_track_count, choice.playlist.trackCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
