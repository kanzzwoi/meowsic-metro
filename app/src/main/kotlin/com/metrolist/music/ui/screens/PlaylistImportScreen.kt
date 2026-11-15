/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.PlaylistImportViewModel

/**
 * Impor playlist dari tautan publik, ala Metrolist v14 — v1 baru mendukung
 * Spotify (tanpa login): tempel tautan playlist/album Spotify publik, lagu
 * dicocokkan ke YouTube Music, hasilnya jadi playlist lokal baru.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    viewModel: PlaylistImportViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val status by viewModel.status.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    val busy =
        status is PlaylistImportViewModel.Status.Fetching ||
            status is PlaylistImportViewModel.Status.Resolving ||
            status is PlaylistImportViewModel.Status.Importing

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                ),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.import_playlist)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.import_playlist_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (status !is PlaylistImportViewModel.Status.Idle) viewModel.reset()
                },
                label = { Text(stringResource(R.string.import_playlist_url_label)) },
                placeholder = { Text(stringResource(R.string.import_playlist_url_hint)) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { viewModel.importSpotify(url.trim()) },
                enabled = url.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_playlist_import))
            }
            Spacer(Modifier.height(16.dp))

            when (val s = status) {
                is PlaylistImportViewModel.Status.Idle -> Unit

                is PlaylistImportViewModel.Status.Fetching -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.import_playlist_fetching),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is PlaylistImportViewModel.Status.Resolving -> {
                    LinearProgressIndicator(
                        progress = { s.done.toFloat() / s.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            "${stringResource(R.string.import_playlist_resolving)} ${s.done}/${s.total}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is PlaylistImportViewModel.Status.Importing -> {
                    LinearProgressIndicator(
                        progress = { s.done.toFloat() / s.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            "${stringResource(R.string.import_playlist_importing)} ${s.done}/${s.total}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is PlaylistImportViewModel.Status.Done -> {
                    Text(
                        text =
                            stringResource(
                                R.string.import_playlist_done,
                                s.matched,
                                s.total,
                                s.title,
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { navController.navigate("local_playlist/${s.playlistId}") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_playlist_open))
                    }
                }

                is PlaylistImportViewModel.Status.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.import_playlist_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
