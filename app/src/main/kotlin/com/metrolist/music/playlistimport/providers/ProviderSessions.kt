/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Penyimpanan token sesi impor akun di DataStore (bukan file polos, bukan
 * log). Satu key per provider, sesuai permintaan. Token sesi di sini
 * adalah kredensial (cookie/token sesi akun provider) — jangan pernah
 * menampilkannya di UI atau menulisnya ke log. v14 sendiri tidak
 * menyimpan sesi ini (login ulang tiap impor); penyimpanan ini tambahan
 * Meowsic supaya pengguna tidak perlu login setiap kali membuka impor
 * akun. Sesi bisa kedaluwarsa — kalau pemakaian token gagal (401),
 * hapus sesi dan minta login ulang.
 *
 * Kunci-kunci ini sengaja didefinisikan di sini, bukan di PreferenceKeys,
 * karena tugas ini hanya boleh menambah file di paket ini.
 */
val AppleMusicSessionTokenKey = stringPreferencesKey("import_apple_music_session_token")
val DeezerArlKey = stringPreferencesKey("import_deezer_arl")
val TidalSessionIdKey = stringPreferencesKey("import_tidal_session_id")
val TidalUserIdKey = stringPreferencesKey("import_tidal_user_id")
val AmazonMusicCookiesKey = stringPreferencesKey("import_amazon_music_cookies")
val AmazonMusicHostKey = stringPreferencesKey("import_amazon_music_host")

object ProviderSessions {
    // ---- Apple Music: media-user-token dari cookie music.apple.com ----
    suspend fun saveAppleMusicSession(context: Context, token: String): Boolean =
        context.safeDataStoreEdit { it[AppleMusicSessionTokenKey] = token }

    suspend fun readAppleMusicSession(context: Context): String? =
        context.dataStore.data.first()[AppleMusicSessionTokenKey]?.takeIf { it.isNotBlank() }

    suspend fun clearAppleMusicSession(context: Context): Boolean =
        context.safeDataStoreEdit { it.remove(AppleMusicSessionTokenKey) }

    fun appleMusicSessionFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { it[AppleMusicSessionTokenKey] }.distinctUntilChanged()

    // ---- Deezer: cookie arl dari deezer.com ----
    suspend fun saveDeezerSession(context: Context, arl: String): Boolean =
        context.safeDataStoreEdit { it[DeezerArlKey] = arl }

    suspend fun readDeezerSession(context: Context): String? =
        context.dataStore.data.first()[DeezerArlKey]?.takeIf { it.isNotBlank() }

    suspend fun clearDeezerSession(context: Context): Boolean =
        context.safeDataStoreEdit { it.remove(DeezerArlKey) }

    fun deezerSessionFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { it[DeezerArlKey] }.distinctUntilChanged()

    // ---- Tidal: cookie sid + userId dari listen.tidal.com ----
    suspend fun saveTidalSession(context: Context, sessionId: String, userId: String): Boolean =
        context.safeDataStoreEdit {
            it[TidalSessionIdKey] = sessionId
            it[TidalUserIdKey] = userId
        }

    suspend fun readTidalSession(context: Context): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val sid = prefs[TidalSessionIdKey]?.takeIf { it.isNotBlank() } ?: return null
        val uid = prefs[TidalUserIdKey]?.takeIf { it.isNotBlank() } ?: return null
        return sid to uid
    }

    suspend fun clearTidalSession(context: Context): Boolean =
        context.safeDataStoreEdit {
            it.remove(TidalSessionIdKey)
            it.remove(TidalUserIdKey)
        }

    fun tidalSessionFlow(context: Context): Flow<Pair<String, String>?> =
        context.dataStore.data.map {
            val sid = it[TidalSessionIdKey]?.takeIf { s -> s.isNotBlank() }
            val uid = it[TidalUserIdKey]?.takeIf { s -> s.isNotBlank() }
            if (sid != null && uid != null) sid to uid else null
        }.distinctUntilChanged()

    // ---- Amazon Music: seluruh cookie header + host marketplace ----
    suspend fun saveAmazonSession(context: Context, cookies: String, host: String): Boolean =
        context.safeDataStoreEdit {
            it[AmazonMusicCookiesKey] = cookies
            it[AmazonMusicHostKey] = host
        }

    suspend fun readAmazonSession(context: Context): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val cookies = prefs[AmazonMusicCookiesKey]?.takeIf { it.isNotBlank() } ?: return null
        val host = prefs[AmazonMusicHostKey]?.takeIf { it.isNotBlank() } ?: return null
        return cookies to host
    }

    suspend fun clearAmazonSession(context: Context): Boolean =
        context.safeDataStoreEdit {
            it.remove(AmazonMusicCookiesKey)
            it.remove(AmazonMusicHostKey)
        }

    fun amazonSessionFlow(context: Context): Flow<Pair<String, String>?> =
        context.dataStore.data.map {
            val cookies = it[AmazonMusicCookiesKey]?.takeIf { s -> s.isNotBlank() }
            val host = it[AmazonMusicHostKey]?.takeIf { s -> s.isNotBlank() }
            if (cookies != null && host != null) cookies to host else null
        }.distinctUntilChanged()
}
