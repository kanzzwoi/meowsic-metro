/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.backup.ForeignBackupImporter
import com.metrolist.music.backup.ForeignImportResult
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.playlistimport.PlaylistImport
import com.metrolist.music.playlistimport.YouTubePlaylistImporter
import com.metrolist.music.playlistimport.providers.AccountPlaylistChoice
import com.metrolist.music.playlistimport.providers.AccountPlaylistPickerDialog
import com.metrolist.music.playlistimport.providers.AccountPlaylistSource
import com.metrolist.music.playlistimport.providers.AmazonMusicImportManager
import com.metrolist.music.playlistimport.providers.AmazonMusicLoginScreen
import com.metrolist.music.playlistimport.providers.AppleMusicAccountManager
import com.metrolist.music.playlistimport.providers.AppleMusicLoginScreen
import com.metrolist.music.playlistimport.providers.DeezerAccountManager
import com.metrolist.music.playlistimport.providers.DeezerLoginScreen
import com.metrolist.music.playlistimport.providers.ProviderSessions
import com.metrolist.music.playlistimport.providers.TidalAccountManager
import com.metrolist.music.playlistimport.providers.TidalLoginScreen
import com.metrolist.music.playlistimport.spotify.SpotifyAccount
import com.metrolist.music.playlistimport.spotify.SpotifyImportManager
import com.metrolist.music.playlistimport.spotify.SpotifyLoginScreen
import com.metrolist.music.playlistimport.spotify.SpotifyPlaylistMeta
import com.metrolist.music.playlistimport.spotify.SpotifyPlaylistPickerSheet
import com.metrolist.music.playlistimport.spotify.SpotifyPlaylistPreviewSheet
import com.metrolist.music.playlistimport.spotify.importSpotifyAccountPlaylist
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.menu.AddToPlaylistDialogOnline
import com.metrolist.music.ui.menu.CsvColumnMappingDialog
import com.metrolist.music.ui.menu.CsvImportProgressDialog
import com.metrolist.music.ui.menu.LoadingScreen
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.BackupPreviewInfo
import com.metrolist.music.viewmodels.BackupRestoreViewModel
import com.metrolist.music.viewmodels.ConvertedSongLog
import com.metrolist.music.viewmodels.CsvImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestore(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    var importedTitle by remember { mutableStateOf("") }
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by rememberSaveable {
        mutableStateOf(false)
    }

    var currentImportSong by rememberSaveable { mutableStateOf("") }
    var isProgressStarted by rememberSaveable {
        mutableStateOf(false)
    }

    var progressPercentage by rememberSaveable {
        mutableIntStateOf(0)
    }

    // CSV column mapping state
    var csvImportState by remember { mutableStateOf<CsvImportState?>(null) }
    var showCsvColumnMapping by rememberSaveable { mutableStateOf(false) }
    var showCsvImportProgress by rememberSaveable { mutableStateOf(false) }
    var csvImportProgress by rememberSaveable { mutableIntStateOf(0) }
    val csvRecentLogs = remember { mutableStateListOf<ConvertedSongLog>() }
    var pendingCsvUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Restore confirmation dialog state
    var showRestoreConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var backupPreviewInfo by remember { mutableStateOf<BackupPreviewInfo?>(null) }
    var isLoadingAccountInfo by remember { mutableStateOf(false) }
    var accountCheckFailed by remember { mutableStateOf(false) }

    // Status impor Spotify
    var showSpotifyUrlDialog by rememberSaveable { mutableStateOf(false) }
    var isSpotifyImporting by rememberSaveable { mutableStateOf(false) }

    // Status impor YouTube Music
    var showYouTubeUrlDialog by rememberSaveable { mutableStateOf(false) }
    var isYouTubeImporting by rememberSaveable { mutableStateOf(false) }

    // Status impor dari app lain
    var isForeignImporting by rememberSaveable { mutableStateOf(false) }

    // Dialog pilih cara impor (akun / tempel tautan) per provider
    var providerDialog by rememberSaveable { mutableStateOf<String?>(null) }

    // Login akun provider
    var accountLoginProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authenticationError by remember { mutableStateOf<String?>(null) }

    // Picker playlist akun (Apple/Deezer/Tidal/Amazon)
    var accountPickerProvider by remember { mutableStateOf<String?>(null) }
    var accountPlaylists by remember { mutableStateOf<List<AccountPlaylistChoice>?>(null) }
    var accountPlaylistsLoading by remember { mutableStateOf(false) }
    var accountPlaylistsError by remember { mutableStateOf<String?>(null) }
    var accountManager by remember { mutableStateOf<AccountPlaylistSource?>(null) }
    var isAccountImporting by remember { mutableStateOf(false) }

    // Spotify akun
    var spotifyManager by remember { mutableStateOf<SpotifyImportManager?>(null) }
    var spotifyPlaylists by remember { mutableStateOf<List<SpotifyPlaylistMeta>?>(null) }
    var selectedSpotifyUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSpotifyPicker by remember { mutableStateOf(false) }
    var isSpotifyLoadingPlaylists by remember { mutableStateOf(false) }
    var spotifyPlaylistsError by remember { mutableStateOf<String?>(null) }
    var loadedSpotifyPlaylists by remember { mutableStateOf<List<SpotifyImportManager.LoadedPlaylist>?>(null) }
    var spotifyPreviewUri by remember { mutableStateOf<String?>(null) }
    var showSpotifyPreview by remember { mutableStateOf(false) }
    var isSpotifyAccountImporting by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun closeAccountFlow() {
        accountManager?.close()
        accountManager = null
        accountPickerProvider = null
        accountPlaylists = null
        accountPlaylistsError = null
        accountLoginProvider = null
        isAuthenticating = false
        authenticationError = null
    }

    fun loadAccountPlaylists(provider: String, manager: AccountPlaylistSource) {
        accountManager = manager
        accountPickerProvider = provider
        accountPlaylists = null
        accountPlaylistsError = null
        accountPlaylistsLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val result = manager.loadPlaylists()
            withContext(Dispatchers.Main) {
                accountPlaylistsLoading = false
                result.onSuccess { accountPlaylists = it }
                    .onFailure { accountPlaylistsError = it.message ?: "" }
            }
        }
    }

    fun loadSpotifyPlaylists(manager: SpotifyImportManager) {
        isSpotifyLoadingPlaylists = true
        spotifyPlaylistsError = null
        coroutineScope.launch(Dispatchers.IO) {
            runCatching { manager.loadPlaylists() }
                .onSuccess { lists ->
                    withContext(Dispatchers.Main) {
                        isSpotifyLoadingPlaylists = false
                        spotifyPlaylists = lists
                        selectedSpotifyUris = emptySet()
                        showSpotifyPicker = true
                    }
                }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        isSpotifyLoadingPlaylists = false
                        spotifyPlaylistsError = e.message ?: ""
                        Toast.makeText(context, e.message ?: "", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    fun openAccountProvider(provider: String) {
        providerDialog = null
        isAuthenticating = false
        authenticationError = null
        coroutineScope.launch(Dispatchers.IO) {
            when (provider) {
                "spotify" -> {
                    val manager = SpotifyAccount.manager(context)
                    spotifyManager = manager
                    if (manager.restorePersistedSession()) {
                        loadSpotifyPlaylists(manager)
                    } else {
                        withContext(Dispatchers.Main) { accountLoginProvider = "spotify" }
                    }
                }

                "apple_music" -> {
                    val token = ProviderSessions.readAppleMusicSession(context)
                    if (token != null) {
                        val manager = AppleMusicAccountManager()
                        manager.authenticate(token)
                        withContext(Dispatchers.Main) { loadAccountPlaylists("apple_music", manager) }
                    } else {
                        withContext(Dispatchers.Main) { accountLoginProvider = "apple_music" }
                    }
                }

                "deezer" -> {
                    val arl = ProviderSessions.readDeezerSession(context)
                    if (arl != null) {
                        val manager = DeezerAccountManager()
                        manager.authenticate(arl)
                        withContext(Dispatchers.Main) { loadAccountPlaylists("deezer", manager) }
                    } else {
                        withContext(Dispatchers.Main) { accountLoginProvider = "deezer" }
                    }
                }

                "tidal" -> {
                    val session = ProviderSessions.readTidalSession(context)
                    if (session != null) {
                        val manager = TidalAccountManager()
                        manager.authenticate(session.first, session.second)
                        withContext(Dispatchers.Main) { loadAccountPlaylists("tidal", manager) }
                    } else {
                        withContext(Dispatchers.Main) { accountLoginProvider = "tidal" }
                    }
                }

                "amazon" -> {
                    val session = ProviderSessions.readAmazonSession(context)
                    if (session != null) {
                        val manager = AmazonMusicImportManager()
                        manager.authenticate(session.first, session.second)
                        withContext(Dispatchers.Main) { loadAccountPlaylists("amazon", manager) }
                    } else {
                        withContext(Dispatchers.Main) { accountLoginProvider = "amazon" }
                    }
                }
            }
        }
    }


    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri)
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pendingRestoreUri = uri
                val preview = viewModel.previewBackup(context, uri)
                backupPreviewInfo = preview
                showRestoreConfirmDialog = true

                // Fetch account info asynchronously if backup has auth data
                accountCheckFailed = false
                if (preview.hasAuthData && preview.cookie != null) {
                    isLoadingAccountInfo = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val accountInfo = viewModel.fetchAccountInfoFromBackup(preview.cookie)
                        if (accountInfo != null) {
                            backupPreviewInfo = accountInfo
                        } else {
                            accountCheckFailed = true
                        }
                        isLoadingAccountInfo = false
                    }
                }
            }
        }

    // Pemilih file tunggal ala v14 ("File atau teks"): satu kartu untuk CSV
    // dan M3U — diarahkan berdasarkan ekstensi nama file, bukan dua kartu.
    val foreignImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            isForeignImporting = true
            coroutineScope.launch(Dispatchers.IO) {
                val message = try {
                    val importer = ForeignBackupImporter(viewModel.database)
                    val result = context.contentResolver.openInputStream(uri)?.use { input ->
                        importer.importForeignBackup(input, File(context.cacheDir, "foreign-import"))
                    } ?: ForeignImportResult.Failed(IllegalStateException("Could not open backup"))
                    when (result) {
                        is ForeignImportResult.Success -> context.getString(
                            R.string.foreign_import_success,
                            result.summary.songs,
                            result.summary.likes,
                            result.summary.playlists,
                            result.summary.plays,
                        )
                        ForeignImportResult.Unsupported -> context.getString(R.string.foreign_import_unsupported)
                        is ForeignImportResult.Failed -> context.getString(
                            R.string.foreign_import_failed,
                            result.error.message ?: "",
                        )
                    }
                } catch (e: Exception) {
                    context.getString(R.string.foreign_import_failed, e.message ?: "")
                }
                withContext(Dispatchers.Main) {
                    isForeignImporting = false
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    val importPlaylistFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val displayName = getDisplayName(context, uri).orEmpty().lowercase()
            if (displayName.endsWith(".m3u") || displayName.endsWith(".m3u8")) {
                val result = viewModel.loadM3UOnline(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)
                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            } else {
                pendingCsvUri = uri
                val previewState = viewModel.previewCsvFile(context, uri)
                csvImportState = previewState
                showCsvColumnMapping = true
            }
        }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )

        val appName = stringResource(R.string.app_name)
        // ---- seksi 1: cadangkan dan impor (ala v14) ----
        Material3SettingsGroup(
            title = stringResource(R.string.backup_restore_title),
            items =
                listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.action_backup)) },
                        description = { Text(stringResource(R.string.backup_restore_export_desc)) },
                        onClick = {
                            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                            backupLauncher.launch(
                                "${appName}_${
                                    LocalDateTime.now().format(formatter)
                                }.backup",
                            )
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.action_restore)) },
                        description = { Text(stringResource(R.string.backup_restore_import_desc)) },
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/octet-stream"))
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.foreign_import_title)) },
                        description = { Text(stringResource(R.string.foreign_import_desc)) },
                        onClick = {
                            foreignImportLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream", "*/*"),
                            )
                        },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        // ---- seksi 2: import playlist (ala v14) ----
        Material3SettingsGroup(
            title = stringResource(R.string.playlist_imports_title),
            items =
                listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.import_provider_spotify)) },
                        description = { Text(stringResource(R.string.import_provider_spotify_desc)) },
                        onClick = {
                            providerDialog = "spotify"
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.import_provider_apple_music)) },
                        description = { Text(stringResource(R.string.import_provider_apple_music_desc)) },
                        onClick = {
                            providerDialog = "apple_music"
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.import_provider_youtube)) },
                        description = { Text(stringResource(R.string.import_provider_youtube_desc)) },
                        onClick = {
                            showYouTubeUrlDialog = true
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.import_provider_deezer)) },
                        description = { Text(stringResource(R.string.import_provider_deezer_desc)) },
                        onClick = {
                            providerDialog = "deezer"
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.import_provider_tidal)) },
                        description = { Text(stringResource(R.string.import_provider_tidal_desc)) },
                        onClick = {
                            providerDialog = "tidal"
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.import_provider_amazon)) },
                        description = { Text(stringResource(R.string.import_provider_amazon_desc)) },
                        onClick = {
                            providerDialog = "amazon"
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.import_provider_file)) },
                        description = { Text(stringResource(R.string.import_provider_file_desc)) },
                        onClick = {
                            importPlaylistFileLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/tab-separated-values",
                                    "text/plain",
                                    "application/json",
                                    "audio/x-mpegurl",
                                    "audio/mpegurl",
                                ),
                            )
                        },
                    ),
                ),
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.backup_restore_title)) },
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

    // Dialog impor Spotify
    if (showSpotifyUrlDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.import_spotify)) },
            placeholder = {
                Text(stringResource(R.string.import_spotify_hint))
            },
            keyboardType = KeyboardType.Uri,
            onDismiss = { showSpotifyUrlDialog = false },
            onDone = { url ->
                showSpotifyUrlDialog = false
                isSpotifyImporting = true
                coroutineScope.launch(Dispatchers.IO) {
                    val result = viewModel.importSpotifyPlaylist(context, url.trim())
                    withContext(Dispatchers.Main) {
                        isSpotifyImporting = false
                        if (result != null && result.songs.isNotEmpty()) {
                            importedTitle = result.playlistName
                            importedSongs.clear()
                            importedSongs.addAll(result.songs)
                            showChoosePlaylistDialogOnline = true
                        } else {
                            Toast.makeText(
                                context,
                                R.string.import_spotify_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
        )
    }

    LoadingScreen(
        isVisible = isSpotifyImporting,
        value = 0,
        songTitle = "",
    )

    // Dialog impor YouTube Music
    if (showYouTubeUrlDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.import_provider_youtube)) },
            placeholder = {
                Text(stringResource(R.string.import_youtube_hint))
            },
            keyboardType = KeyboardType.Uri,
            onDismiss = { showYouTubeUrlDialog = false },
            onDone = { url ->
                showYouTubeUrlDialog = false
                isYouTubeImporting = true
                coroutineScope.launch(Dispatchers.IO) {
                    val result = YouTubePlaylistImporter.loadPlaylist(url.trim())
                    withContext(Dispatchers.Main) {
                        isYouTubeImporting = false
                        val importResult = result.getOrNull()
                        if (importResult != null && importResult.matches.isNotEmpty()) {
                            importedTitle = importResult.title
                            importedSongs.clear()
                            importedSongs.addAll(
                                importResult.matches.map { (_, songItem) ->
                                    Song(
                                        song = SongEntity(
                                            id = songItem.id,
                                            title = songItem.title,
                                            duration = songItem.duration ?: -1,
                                            thumbnailUrl = songItem.thumbnail,
                                        ),
                                        artists = songItem.artists.map { ArtistEntity(id = "", name = it.name) },
                                    )
                                },
                            )
                            showChoosePlaylistDialogOnline = true
                        } else {
                            Toast.makeText(
                                context,
                                result.exceptionOrNull()?.message ?: context.getString(R.string.import_youtube_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
        )
    }

    LoadingScreen(
        isVisible = isYouTubeImporting,
        value = 0,
        songTitle = "",
    )

    LoadingScreen(
        isVisible = isForeignImporting,
        value = 0,
        songTitle = "",
    )

    // Dialog pilih cara impor: akun atau tempel tautan
    providerDialog?.let { provider ->
        val providerName = when (provider) {
            "spotify" -> stringResource(R.string.import_provider_spotify)
            "apple_music" -> stringResource(R.string.import_provider_apple_music)
            "deezer" -> stringResource(R.string.import_provider_deezer)
            "tidal" -> stringResource(R.string.import_provider_tidal)
            "amazon" -> stringResource(R.string.import_provider_amazon)
            else -> provider
        }
        val connectLabelRes = when (provider) {
            "spotify" -> R.string.import_connect_spotify_account
            "apple_music" -> R.string.import_connect_apple_music_account
            "deezer" -> R.string.import_connect_deezer_account
            "tidal" -> R.string.import_connect_tidal_account
            "amazon" -> R.string.import_connect_amazon_music_account
            else -> R.string.import_connect_spotify_account
        }
        DefaultDialog(
            onDismiss = { providerDialog = null },
            title = { Text(providerName) },
            horizontalAlignment = Alignment.Start,
            buttons = {
                TextButton(onClick = { providerDialog = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            TextButton(
                onClick = { openAccountProvider(provider) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(connectLabelRes),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
            }
            TextButton(
                onClick = {
                    providerDialog = null
                    when (provider) {
                        "spotify" -> showSpotifyUrlDialog = true
                        "apple_music" -> navController.navigate("playlist_import/apple_music")
                        "deezer" -> navController.navigate("playlist_import/deezer")
                        "tidal" -> navController.navigate("playlist_import/tidal")
                        "amazon" -> navController.navigate("playlist_import/amazon")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.import_paste_link),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
            }
        }
    }

    // Layar login akun (menutupi layar penuh, ala v14)
    when (accountLoginProvider) {
        "spotify" -> {
            SpotifyLoginScreen(
                isAuthenticating = isAuthenticating,
                authenticationError = authenticationError,
                onClearAuthenticationError = { authenticationError = null },
                onSessionCookie = { spDc ->
                    isAuthenticating = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val manager = SpotifyAccount.manager(context)
                        spotifyManager = manager
                        runCatching { manager.authenticate(spDc) }
                            .onSuccess {
                                withContext(Dispatchers.Main) {
                                    accountLoginProvider = null
                                    isAuthenticating = false
                                }
                                loadSpotifyPlaylists(manager)
                            }.onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    isAuthenticating = false
                                    authenticationError = e.message ?: ""
                                }
                            }
                    }
                },
                onNavigateBack = { accountLoginProvider = null },
            )
        }

        "apple_music" -> {
            AppleMusicLoginScreen(
                isAuthenticating = isAuthenticating,
                authenticationError = authenticationError,
                onClearAuthenticationError = { authenticationError = null },
                onSessionToken = { token ->
                    isAuthenticating = true
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            ProviderSessions.saveAppleMusicSession(context, token)
                            val manager = AppleMusicAccountManager()
                            manager.authenticate(token)
                            manager
                        }.onSuccess { manager ->
                            withContext(Dispatchers.Main) {
                                accountLoginProvider = null
                                isAuthenticating = false
                                loadAccountPlaylists("apple_music", manager)
                            }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                isAuthenticating = false
                                authenticationError = e.message ?: ""
                            }
                        }
                    }
                },
                onNavigateBack = { accountLoginProvider = null },
            )
        }

        "deezer" -> {
            DeezerLoginScreen(
                isAuthenticating = isAuthenticating,
                authenticationError = authenticationError,
                onClearAuthenticationError = { authenticationError = null },
                onSessionToken = { arl ->
                    isAuthenticating = true
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            ProviderSessions.saveDeezerSession(context, arl)
                            val manager = DeezerAccountManager()
                            manager.authenticate(arl)
                            manager
                        }.onSuccess { manager ->
                            withContext(Dispatchers.Main) {
                                accountLoginProvider = null
                                isAuthenticating = false
                                loadAccountPlaylists("deezer", manager)
                            }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                isAuthenticating = false
                                authenticationError = e.message ?: ""
                            }
                        }
                    }
                },
                onNavigateBack = { accountLoginProvider = null },
            )
        }

        "tidal" -> {
            TidalLoginScreen(
                isAuthenticating = isAuthenticating,
                authenticationError = authenticationError,
                onClearAuthenticationError = { authenticationError = null },
                onSessionAuthenticated = { sessionId, userId ->
                    isAuthenticating = true
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            ProviderSessions.saveTidalSession(context, sessionId, userId)
                            val manager = TidalAccountManager()
                            manager.authenticate(sessionId, userId)
                            manager
                        }.onSuccess { manager ->
                            withContext(Dispatchers.Main) {
                                accountLoginProvider = null
                                isAuthenticating = false
                                loadAccountPlaylists("tidal", manager)
                            }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                isAuthenticating = false
                                authenticationError = e.message ?: ""
                            }
                        }
                    }
                },
                onNavigateBack = { accountLoginProvider = null },
            )
        }

        "amazon" -> {
            AmazonMusicLoginScreen(
                isAuthenticating = isAuthenticating,
                authenticationError = authenticationError,
                onClearAuthenticationError = { authenticationError = null },
                onSessionToken = { cookies, host ->
                    isAuthenticating = true
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            ProviderSessions.saveAmazonSession(context, cookies, host)
                            val manager = AmazonMusicImportManager()
                            manager.authenticate(cookies, host)
                            manager
                        }.onSuccess { manager ->
                            withContext(Dispatchers.Main) {
                                accountLoginProvider = null
                                isAuthenticating = false
                                loadAccountPlaylists("amazon", manager)
                            }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                                isAuthenticating = false
                                authenticationError = e.message ?: ""
                            }
                        }
                    }
                },
                onNavigateBack = { accountLoginProvider = null },
            )
        }
    }

    // Picker playlist akun (Apple/Deezer/Tidal/Amazon)
    accountPickerProvider?.let { provider ->
        val providerName = when (provider) {
            "apple_music" -> stringResource(R.string.import_provider_apple_music)
            "deezer" -> stringResource(R.string.import_provider_deezer)
            "tidal" -> stringResource(R.string.import_provider_tidal)
            "amazon" -> stringResource(R.string.import_provider_amazon)
            else -> provider
        }
        AccountPlaylistPickerDialog(
            providerName = providerName,
            playlists = accountPlaylists,
            isLoading = accountPlaylistsLoading,
            error = accountPlaylistsError,
            onPick = { choice ->
                isAccountImporting = true
                coroutineScope.launch(Dispatchers.IO) {
                    val result = runCatching {
                        val imported = choice.loadTracks { _, _ -> }.getOrThrow()
                        val matches = PlaylistImport.resolveTracks(imported.tracks)
                        if (matches.isEmpty()) {
                            throw IllegalStateException("Tidak ada lagu yang berhasil dicocokkan di YouTube Music")
                        }
                        PlaylistImport.importToNewPlaylist(
                            viewModel.database,
                            imported.title,
                            imported.coverUrl,
                            matches,
                        )
                    }
                    withContext(Dispatchers.Main) {
                        isAccountImporting = false
                        result.onSuccess {
                            Toast.makeText(
                                context,
                                context.getString(R.string.import_account_success, choice.playlist.name),
                                Toast.LENGTH_LONG,
                            ).show()
                            closeAccountFlow()
                        }.onFailure { e ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.import_account_failed, e.message ?: ""),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
            onRetry = {
                accountManager?.let { loadAccountPlaylists(provider, it) }
            },
            onDismiss = { closeAccountFlow() },
        )
    }

    // Picker + pratinjau playlist akun Spotify
    if (showSpotifyPicker) {
        SpotifyPlaylistPickerSheet(
            playlists = spotifyPlaylists ?: emptyList(),
            selectedUris = selectedSpotifyUris,
            onSelectionChange = { selectedSpotifyUris = it },
            onReview = {
                val manager = spotifyManager ?: return@SpotifyPlaylistPickerSheet
                val selected = (spotifyPlaylists ?: emptyList()).filter { it.uri in selectedSpotifyUris }
                isSpotifyLoadingPlaylists = true
                coroutineScope.launch(Dispatchers.IO) {
                    runCatching { manager.loadPlaylistsForImport(selected) }
                        .onSuccess { loaded ->
                            withContext(Dispatchers.Main) {
                                isSpotifyLoadingPlaylists = false
                                loadedSpotifyPlaylists = loaded.playlists
                                spotifyPreviewUri = loaded.playlists.firstOrNull()?.uri
                                showSpotifyPicker = false
                                showSpotifyPreview = true
                            }
                        }
                        .onFailure { e ->
                            withContext(Dispatchers.Main) {
                                isSpotifyLoadingPlaylists = false
                                spotifyPlaylistsError = e.message ?: ""
                            }
                        }
                }
            },
            onDismiss = { showSpotifyPicker = false },
        )
    }

    if (showSpotifyPreview) {
        SpotifyPlaylistPreviewSheet(
            playlists = loadedSpotifyPlaylists ?: emptyList(),
            selectedPlaylistUri = spotifyPreviewUri,
            onPlaylistSelected = { spotifyPreviewUri = it },
            onBack = {
                showSpotifyPreview = false
                showSpotifyPicker = true
            },
            onContinue = {
                val loaded = loadedSpotifyPlaylists ?: return@SpotifyPlaylistPreviewSheet
                isSpotifyAccountImporting = true
                coroutineScope.launch(Dispatchers.IO) {
                    var imported = 0
                    var failed = 0
                    for (playlist in loaded) {
                        runCatching { importSpotifyAccountPlaylist(viewModel.database, playlist) }
                            .onSuccess { imported++ }
                            .onFailure { failed++ }
                    }
                    withContext(Dispatchers.Main) {
                        isSpotifyAccountImporting = false
                        showSpotifyPreview = false
                        val message = if (failed > 0) {
                            context.getString(R.string.import_spotify_partial, imported, failed)
                        } else {
                            context.getString(R.string.import_spotify_success, imported)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    LoadingScreen(
        isVisible = isAccountImporting || isSpotifyAccountImporting || isSpotifyLoadingPlaylists,
        value = 0,
        songTitle = "",
    )

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        initialTextFieldValue = importedTitle,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { newVal -> isProgressStarted = newVal },
        onPercentageChange = { newPercentage -> progressPercentage = newPercentage },
        onSongChange = { currentImportSong = it },
    )

    LoadingScreen(
        isVisible = isProgressStarted,
        value = progressPercentage,
        songTitle = currentImportSong,
    )

    // CSV column mapping dialog
    csvImportState?.let { state ->
        CsvColumnMappingDialog(
            isVisible = showCsvColumnMapping,
            csvState = state,
            onDismiss = {
                showCsvColumnMapping = false
                csvImportState = null
            },
            onConfirm = { mappingState ->
                showCsvColumnMapping = false
                csvImportState = mappingState
                pendingCsvUri?.let { uri ->
                    showCsvImportProgress = true
                    coroutineScope.launch(Dispatchers.Default) {
                        val result =
                            viewModel.importPlaylistFromCsv(
                                context,
                                uri,
                                mappingState,
                                onProgress = { progress ->
                                    csvImportProgress = progress
                                },
                                onLogUpdate = { logs ->
                                    csvRecentLogs.clear()
                                    csvRecentLogs.addAll(logs)
                                },
                            )
                        importedSongs.clear()
                        importedSongs.addAll(result)
                        if (result.isNotEmpty()) {
                            showCsvImportProgress = false
                            csvImportProgress = 0
                            csvRecentLogs.clear()
                            showChoosePlaylistDialogOnline = true
                        }
                    }
                }
            },
        )
    }

    // CSV import progress dialog
    CsvImportProgressDialog(
        isVisible = showCsvImportProgress,
        progress = csvImportProgress,
        recentLogs = csvRecentLogs.toList(),
        onDismiss = {
            // Cannot dismiss while importing
        },
    )

    // Restore confirmation dialog
    if (showRestoreConfirmDialog) {
        DefaultDialog(
            onDismiss = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
                backupPreviewInfo = null
                accountCheckFailed = false
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.restore),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            buttons = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                        backupPreviewInfo = null
                        accountCheckFailed = false
                    },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri?.let { uri ->
                            viewModel.restore(context, uri, clearAuthData = false)
                        }
                        pendingRestoreUri = null
                        backupPreviewInfo = null
                        accountCheckFailed = false
                    },
                ) {
                    Text(stringResource(R.string.restore))
                }
            },
        ) {
            // Supporting text
            Text(
                text = stringResource(R.string.restore_confirm_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            // Show warning about account sign out if account found
            if (backupPreviewInfo?.accountName != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.restore_account_warning),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            // Show loading or account info
            if (isLoadingAccountInfo) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.checking_previous_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Show "No account found" if check failed OR backup has no auth data
            val hasNoAccount =
                backupPreviewInfo?.let {
                    !it.hasAuthData || (it.hasAuthData && it.accountName == null && !isLoadingAccountInfo)
                } ?: false
            if (!isLoadingAccountInfo && (accountCheckFailed || hasNoAccount)) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.no_account_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Show account info if backup contains auth data and we have account details
            backupPreviewInfo?.let { preview ->
                if (!isLoadingAccountInfo && preview.hasAuthData && preview.accountName != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (preview.accountImageUrl != null) {
                            AsyncImage(
                                model = preview.accountImageUrl,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.person),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(16.dp))

                        Text(
                            text = preview.accountEmail ?: preview.accountName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/** Nama tampilan file dari Uri (ala v14) — dipakai untuk mengarahkan CSV vs M3U. */
private fun getDisplayName(context: Context, uri: android.net.Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) cursor.getString(index) else cursor.getString(0)
                } else {
                    null
                }
            }
        }.getOrNull()?.let { return it }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
