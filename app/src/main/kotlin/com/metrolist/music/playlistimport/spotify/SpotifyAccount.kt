/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first

/**
 * Penyimpanan sesi Spotify di DataStore + akses singleton ke
 * [SpotifyImportManager], adaptasi dari v14
 * (android/.../AndroidSpotifyImportManager.kt) yang menyimpan sesi di
 * Settings.shared. Di Meowsic sesi disimpan sebagai JSON di DataStore.
 *
 * Kunci ini didefinisikan di sini (bukan di PreferenceKeys.kt) supaya
 * modul ini mandiri — nilainya adalah string JSON [SpotifySession].
 *
 * KEAMANAN: nilai ini adalah kredensial sesi (setara login). Jangan pernah
 * menampilkannya di log/UI, dan jangan ikutkan dalam file cadangan yang
 * dibagikan (saat restore, sesi ini sebaiknya dihapus seperti
 * LevelTotalExpKey — lihat catatan di laporan).
 */
val SpotifySessionKey = stringPreferencesKey("spotifySession")

/**
 * Akses terpusat ke impor akun Spotify. Manager dibuat sekali per proses
 * dan memakai applicationContext, jadi aman dipanggil dari layar mana pun.
 */
object SpotifyAccount {
    @Volatile
    private var manager: SpotifyImportManager? = null

    /** Manager impor akun (dibuat sekali, dipakai ulang). */
    fun manager(context: Context): SpotifyImportManager {
        manager?.let { return it }
        return synchronized(this) {
            manager?.let { return it }
            val appContext = context.applicationContext
            SpotifyImportManager(
                clientFactory = { SpotifyClient.create() },
                loadStoredSession = { loadSessionJson(appContext) },
                saveStoredSession = { json -> saveSessionJson(appContext, json) },
            ).also { manager = it }
        }
    }

    /**
     * Coba pulihkan sesi tersimpan (dipanggil saat layar impor dibuka).
     * True kalau sesi masih valid dan klien sudah terautentikasi.
     */
    suspend fun restoreSession(context: Context): Boolean =
        manager(context).restorePersistedSession()

    /** Putuskan akun Spotify: hapus klien + sesi tersimpan. */
    fun signOut(context: Context) {
        manager(context).logout()
    }

    /** True kalau ada sesi tersimpan (belum tentu masih valid). */
    suspend fun hasStoredSession(context: Context): Boolean =
        loadSessionJson(context.applicationContext)?.isNotBlank() == true

    internal suspend fun loadSessionJson(context: Context): String? =
        context.dataStore.data.first()[SpotifySessionKey]

    internal suspend fun saveSessionJson(
        context: Context,
        json: String,
    ) {
        context.safeDataStoreEdit { it[SpotifySessionKey] = json }
    }
}
