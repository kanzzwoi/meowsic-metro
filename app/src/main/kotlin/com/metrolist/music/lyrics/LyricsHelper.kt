/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.constants.PreferWordSyncedLyricsKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

// Total cap dinaikkan 6 -> 10 dtk: pencarian sekuensial 9 provider di data
// seluler yang lambat bisa kehabisan waktu sebelum sampai ke provider yang
// punya liriknya (mis. LrcLib/QQMusic di urutan belakang), sementara Cari
// (paralel, tanpa cap) menemukannya. 10 dtk masih di bawah 12 dtk yang lama.
private const val MAX_LYRICS_FETCH_MS = 10_000L
private const val PER_PROVIDER_TIMEOUT_MS = 10_000L
private const val PROVIDER_NONE = ""

/** Info ringkas hasil fetch terakhir, ditampilkan di panel lirik. */
object LyricsStats {
    @Volatile var last: String? = null
    @Volatile var lastTrackId: String? = null

    /** Catatan tiap provider untuk fetch terakhir: nama, milidetik, hasil. */
    @Volatile var detail: String? = null

    /** Info hanya berlaku untuk lagu yang sama, supaya label tidak menampilkan
     *  hasil fetch lagu sebelumnya. */
    fun forTrack(id: String?): String? = last?.takeIf { id != null && id == lastTrackId }
}

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    val preferred =
        context.dataStore.data
            .map { preferences ->
                resolveLyricsProviders(preferences)
            }.distinctUntilChanged()

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        val startedAt = System.currentTimeMillis()
        currentLyricsJob?.cancel()

        // Port dari v14 (preferWordSyncedLyrics): kalau toggle nyala, yang
        // dicari lirik per kata; kalau mati, cukup per baris. Default Meowsic
        // nyala (beda dari v14) supaya lirik per kata yang dicari dulu.
        val preferWordSynced = context.dataStore.data
            .map { it[PreferWordSyncedLyricsKey] ?: true }
            .first()
        val preferredTiming =
            if (preferWordSynced) LyricsTimingKind.WORD_SYNCED else LyricsTimingKind.LINE_SYNCED

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null &&
            LyricsUtils.lyricsTimingKind(LyricsUtils.parseLyrics(cached.lyrics)) >= preferredTiming
        ) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val orderedProviders = context.dataStore.data
            .map { preferences -> resolveLyricsProviders(preferences) }
            .first()

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        // Hasil terbaik disimpan DI LUAR blok timeout: kalau cap 10 dtk
        // memotong pencarian saat sedang berburu lirik per kata, lirik
        // per baris yang sudah ketemu tetap dikembalikan — bukan NOT_FOUND.
        // (Sebelumnya `found` hidup di dalam blok, jadi ikut mati saat
        // timeout dan player menampilkan "Lirik tidak ditemukan" walau
        // LrcLib sudah mengembalikannya.)
        var found: LyricsWithProvider? = null
        var foundTimingKind = LyricsTimingKind.UNSYNCED
        val notes = mutableListOf<String>()

        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
            val enabledProviders = orderedProviders.filter { it.isEnabled(context) }
            // MediaMetadata.duration SUDAH dalam detik (SongItem.duration dari
            // InnerTube). Sebelumnya dibagi 1000 sehingga provider menerima 0:
            // BetterLyrics menghilangkan parameter d -> cache selalu meleset
            // (401 / lambat 10 dtk), LrcLib & provider lain gagal mencocokkan.
            val durationSec = mediaMetadata.duration
            val artistLine = mediaMetadata.artists.joinToString { it.name }
            val albumTitle = mediaMetadata.album?.title

            // Port dari v14 (LyricsClient AUTO + LyricsRepository.prefersLyrics):
            // provider dicoba SATU PER SATU sesuai urutan pengguna. Hasil
            // pertama yang punya timing langsung dipakai dan pencarian
            // berhenti — kecuali toggle per kata nyala: pencarian lanjut
            // sampai ketemu yang per kata, karena file per kata mengalahkan
            // file per baris tanpa peduli urutan provider.
            Timber.tag("LyricsHelper").d("Sequential fetch for: $cleanedTitle by $artistLine")
            Timber.tag("LyricsHelper").d("Enabled providers in order: ${enabledProviders.joinToString { it.name }}")

            suspend fun fetch(provider: LyricsProvider): String? =
                runCatching {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(
                            context,
                            mediaMetadata.id,
                            cleanedTitle,
                            artistLine,
                            durationSec,
                            albumTitle,
                        )
                    }?.getOrNull()
                }.getOrElse { e ->
                    if (e is CancellationException) throw e
                    Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                    null
                }?.takeIf { it.isNotBlank() }

            for (provider in enabledProviders) {
                val t0 = System.currentTimeMillis()
                val text = fetch(provider)
                val spent = (System.currentTimeMillis() - t0) / 1000.0
                if (text == null) {
                    // Tanda ✗ = provider ini GAGAL (bukan lebih cepat).
                    notes.add("%s %.1fs ✗".format(provider.name, spent))
                    continue
                }
                val parsed = LyricsUtils.parseLyrics(text)
                val timingKind = LyricsUtils.lyricsTimingKind(parsed)
                val timed = timingKind != LyricsTimingKind.UNSYNCED
                notes.add(
                    "%s %.1fs%s".format(
                        provider.name,
                        spent,
                        when (timingKind) {
                            LyricsTimingKind.WORD_SYNCED -> " (per kata)"
                            LyricsTimingKind.LINE_SYNCED -> ""
                            LyricsTimingKind.UNSYNCED -> " (tanpa timing)"
                        },
                    ),
                )
                if (LyricsUtils.usableProviderLyrics(parsed)) {
                    // v14 prefersLyrics: kalau toggle nyala dan tingkat
                    // timing beda, yang lebih tinggi menang; kalau tidak,
                    // yang ber-timing mengalahkan yang tidak. Urutan
                    // provider sudah dijamin loop sekuensial ini.
                    val better = found == null ||
                        (preferWordSynced && timingKind > foundTimingKind) ||
                        (timed && foundTimingKind == LyricsTimingKind.UNSYNCED)
                    if (better) {
                        found = LyricsWithProvider(
                            LyricsUtils.filterLyricsCreditLines(text),
                            provider.name,
                        )
                        foundTimingKind = timingKind
                    }
                }
                if (found != null && foundTimingKind >= preferredTiming) break
            }
            if (found != null) {
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
                LyricsStats.lastTrackId = mediaMetadata.id
                LyricsStats.last =
                    "%s • %.1f dtk • %s".format(
                        found.provider,
                        elapsed,
                        if (LyricsUtils.isWordSynced(found.lyrics)) "per kata" else "per baris",
                    )
                Timber.tag("LyricsHelper").i("RESULT ${LyricsStats.last}")
                return@withTimeoutOrNull found
            }

            Timber.tag("LyricsHelper").w("No lyrics found after checking all providers")
            LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        // Rincian per provider tetap dicatat walau timeout memotong loop,
        // supaya playback.log menunjukkan provider mana yang sempat dicoba.
        LyricsStats.detail = notes.joinToString(", ").takeIf { it.isNotEmpty() }

        // Timeout mengembalikan null — kembalikan hasil terbaik yang sudah
        // ketemu (mis. per baris dari LrcLib), bukan NOT_FOUND.
        return result ?: found ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) return

        val allResult = mutableListOf<LyricsResult>()
        // Samakan satuan: pemanggil mengirim milidetik, provider memakai detik.
        val durationSec = if (duration > 1000) duration / 1000 else duration
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            val allProviders = context.dataStore.data
                .map { preferences -> resolveLyricsProviders(preferences) }
                .first()
            val enabledProviders = allProviders.filter { it.isEnabled(context) }

            val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
            val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }

            val callbackMutex = Any()

            val otherJobs = otherProviders.map { provider ->
                launch {
                    try {
                        provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, durationSec, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(provider.name, filteredLyrics)
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            otherJobs.forEach { it.join() }

            val otherLyricsCount = allResult.count { it.providerName != "LyricsPlus" }
            if (lyricsPlusProvider != null && otherLyricsCount <= 2) {
                launch {
                    try {
                        lyricsPlusProvider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, durationSec, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(lyricsPlusProvider.name, filteredLyrics)
                            synchronized(callbackMutex) {
                                allResult += result
                                callback(result)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }.join()
            }

            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    private fun resolveLyricsProviders(preferences: androidx.datastore.preferences.core.Preferences): List<LyricsProvider> {
        val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (providerOrder.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }

        return LyricsProviderRegistry.getDefaultProviderOrder()
            .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
