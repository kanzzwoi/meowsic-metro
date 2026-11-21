/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.SecretPfpUnlockedKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.rememberPreference
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Menu Cheat rahasia muncul di paling bawah (di bawah Tentang) setelah
    // easter egg 10x ketuk di Tentang kebuka.
    val secretPfpUnlocked by rememberPreference(SecretPfpUnlockedKey, false)
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo(
                "com.google.android.projection.gearhead", 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    var query by rememberSaveable { mutableStateOf("") }

    // Dua aksi di grup System & About ini juga dipakai sebagai hasil
    // pencarian, jadi didefinisikan sekali di sini (dulu inline di item).
    val openDefaultLinks: () -> Unit = {
        try {
            val intent = Intent(
                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                "package:${context.packageName}".toUri()
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                R.string.open_app_settings_error,
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val sharePlaybackLog: () -> Unit = {
        try {
            val file = com.metrolist.music.playback.PlaybackLog.file(context)
            if (!file.exists()) {
                Toast.makeText(context, "Belum ada log", Toast.LENGTH_SHORT).show()
            } else {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.FileProvider",
                    file,
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(share, "Bagikan log pemutaran"),
                )
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Daftar tujuan yang bisa dicari. Metrolist mengindeks tiap halaman dan
    // tiap item lewat registry; di sini item-itemnya datang dari
    // rememberSettingsSearchItems (index dari item pengaturan beneran, lihat
    // SettingsSearchIndex.kt), dan halaman-halaman yang tidak punya item
    // terindeks (backup, impor, tentang, dsb.) tetap sebagai hasil
    // tingkat-halaman dengan search terms-nya, sama seperti Metrolist.
    val searchEntries = rememberSettingsSearchItems(navController) + buildList {
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.appearance),
                subtitle = stringResource(R.string.settings_desc_appearance),
                keywords = listOf(
                    "tema", "font", "pemutar", "gelap", "terang", "dinamis",
                    "warna", "lirik", "theme", "appearance", "display",
                    "dark", "light", "lyrics",
                ),
                icon = painterResource(R.drawable.palette),
                onClick = { navController.navigate("settings/appearance") },
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.player_and_audio),
                subtitle = stringResource(R.string.settings_desc_player),
                keywords = listOf(
                    "audio", "transisi", "antrean", "kualitas", "volume",
                    "crossfade", "loudness", "repeat", "shuffle", "putar",
                    "quality", "transitions", "queue", "playback",
                ),
                icon = painterResource(R.drawable.play),
                onClick = { navController.navigate("settings/player") },
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.content),
                subtitle = stringResource(R.string.settings_desc_content),
                keywords = listOf(
                    "bahasa", "feed", "jelajah", "lirik", "lyrics", "proxy",
                    "proksi", "negara", "country", "language", "home",
                    "beranda", "explore",
                ),
                icon = painterResource(R.drawable.language),
                onClick = { navController.navigate("settings/content") },
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.ai_lyrics_translation),
                subtitle = stringResource(R.string.settings_desc_ai_translation),
                keywords = listOf(
                    "terjemahan", "translate", "ai", "lirik", "lyrics",
                    "knzapi", "gemini", "openrouter", "provider", "penyedia",
                    "model",
                ),
                icon = painterResource(R.drawable.translate),
                onClick = { navController.navigate("settings/ai") },
            )
        )
        if (hasAndroidAuto) {
            add(
                SettingsSearchEntry(
                    title = stringResource(R.string.android_auto),
                    subtitle = stringResource(R.string.settings_desc_android_auto),
                    keywords = listOf("android auto", "mobil", "car"),
                    icon = painterResource(R.drawable.ic_android_auto),
                    onClick = { navController.navigate("settings/android_auto") },
                )
            )
        }
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.privacy),
                subtitle = stringResource(R.string.settings_desc_privacy),
                keywords = listOf(
                    "riwayat", "history", "tangkapan layar", "screenshot",
                    "privasi", "privacy", "dengarkan", "listen", "cari",
                    "search",
                ),
                icon = painterResource(R.drawable.security),
                onClick = { navController.navigate("settings/privacy") },
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.storage),
                subtitle = stringResource(R.string.settings_desc_storage),
                keywords = listOf(
                    "penyimpanan", "storage", "cache", "unduhan", "download",
                    "hapus", "clear", "lagu", "gambar",
                ),
                icon = painterResource(R.drawable.storage),
                onClick = { navController.navigate("settings/storage") },
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.backup_restore),
                subtitle = stringResource(R.string.settings_desc_backup_restore),
                keywords = listOf(
                    "cadangan", "backup", "pulihkan", "restore", "impor",
                    "import", "playlist", "spotify", "youtube", "ekspor",
                    "export",
                ),
                icon = painterResource(R.drawable.restore),
                onClick = { navController.navigate("settings/backup_restore") },
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.import_playlist),
                subtitle = stringResource(R.string.settings_desc_import_playlist),
                keywords = listOf(
                    "impor", "import", "playlist", "spotify", "apple",
                    "deezer", "tidal", "amazon", "youtube",
                ),
                icon = painterResource(R.drawable.download),
                onClick = { navController.navigate("playlist_import") },
            )
        )
        if (isAndroid12OrLater) {
            add(
                SettingsSearchEntry(
                    title = stringResource(R.string.default_links),
                    subtitle = stringResource(R.string.settings_desc_default_links),
                    keywords = listOf(
                        "tautan", "link", "default", "buka", "open",
                    ),
                    icon = painterResource(R.drawable.link),
                    onClick = openDefaultLinks,
                )
            )
        }
        add(
            SettingsSearchEntry(
                title = "Bagikan log pemutaran",
                subtitle = stringResource(R.string.settings_desc_share_log),
                keywords = listOf(
                    "log", "pemutaran", "playback", "bagikan", "share",
                    "debug", "diagnosis",
                ),
                icon = painterResource(R.drawable.info),
                onClick = sharePlaybackLog,
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(R.string.about),
                subtitle = stringResource(R.string.settings_desc_about),
                keywords = listOf(
                    "tentang", "about", "versi", "version", "meowsic",
                ),
                icon = painterResource(R.drawable.info),
                onClick = { navController.navigate("settings/about") },
            )
        )
        if (secretPfpUnlocked) {
            add(
                SettingsSearchEntry(
                    title = "Menu Cheat",
                    subtitle = stringResource(R.string.settings_desc_cheat),
                    keywords = listOf("cheat", "exp", "level"),
                    icon = painterResource(R.drawable.key),
                    onClick = { navController.navigate("settings/cheat") },
                )
            )
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Spacer ini cuma buat status bar: header Pengaturan ada DI DALAM
        // kolom scroll (bukan overlay), jadi tidak ada bar 64dp yang perlu
        // dihindari. Sebelumnya pakai playerAwareWindowInsets yang ikut
        // menghitung AppBarHeight 64dp, makanya ada space kosong gede di atas.
        Spacer(
            Modifier.windowInsetsPadding(
                WindowInsets.statusBars.only(
                    WindowInsetsSides.Top
                )
            )
        )

        // Header (tombol back + judul) ikut scroll bersama konten, bukan
        // menempel di atas layar. Balok hitamnya sudah tidak ada, jadi
        // header yang mengambang di atas kartu yang lewat bakal kelihatan
        // aneh. TopAppBar yang sama, tanpa warna latar dan tanpa insets
        // (status bar sudah ditangani Spacer di atas).
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        SettingsSearchField(
            query = query,
            onQueryChange = { query = it },
        )

        Spacer(modifier = Modifier.height(if (query.isBlank()) 16.dp else 12.dp))

        if (query.isBlank()) {
        // User Interface Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_ui),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.appearance)) },
                    onClick = { navController.navigate("settings/appearance") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Player & Content Section (moved up and combined with content)
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text(stringResource(R.string.player_and_audio)) },
                    onClick = { navController.navigate("settings/player") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content)) },
                    onClick = { navController.navigate("settings/content") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                    onClick = { navController.navigate("settings/ai") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Android Auto Section — only shown if Android Auto is installed
        if (hasAndroidAuto) {
            Material3SettingsGroup(
                title = "Android Auto",
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.ic_android_auto),
                        title = { Text(stringResource(R.string.android_auto)) },
                        onClick = { navController.navigate("settings/android_auto") }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Privacy & Security Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_privacy),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text(stringResource(R.string.privacy)) },
                    onClick = { navController.navigate("settings/privacy") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Storage & Data Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_storage),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text(stringResource(R.string.storage)) },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.restore),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    onClick = { navController.navigate("settings/backup_restore") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.download),
                    title = { Text(stringResource(R.string.import_playlist)) },
                    onClick = { navController.navigate("playlist_import") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System & About Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_system),
            items = buildList {
                if (isAndroid12OrLater) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.link),
                            title = { Text(stringResource(R.string.default_links)) },
                            onClick = openDefaultLinks
                        )
                    )
                }
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text("Bagikan log pemutaran") },
                        onClick = sharePlaybackLog,
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text(stringResource(R.string.about)) },
                        onClick = { navController.navigate("settings/about") }
                    )
                )
                if (secretPfpUnlocked) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.key),
                            title = { Text("Menu Cheat") },
                            onClick = { navController.navigate("settings/cheat") }
                        )
                    )
                }
            }
        )
        } else {
            val results = searchSettingsEntries(searchEntries, query)
            if (results.isEmpty()) {
                SettingsSearchEmptyState(query)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    results.forEachIndexed { index, entry ->
                        SettingsSearchResultRow(
                            entry = entry,
                            query = query,
                            first = index == 0,
                            last = index == results.lastIndex,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
