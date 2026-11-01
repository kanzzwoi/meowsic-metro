/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

private const val MAX_LYRICS_FETCH_MS = 20_000L
private const val PER_PROVIDER_TIMEOUT_MS = 10_000L
private const val PROVIDER_NONE = ""

/** Kubu 1: lirik per suku kata (paling presisi), langsung dijalankan. */
private val TIER_1 = setOf("Paxsenix", "Unison", "BetterLyrics")

/** Kubu 2: cadangan luas (sebagian per kata/baris). */
private val TIER_2 = setOf("LyricsPlus", "LrcLib", "KuGou")

/** Jeda sebelum kubu 2 ikut jalan kalau kubu 1 belum ada hasil. */
private const val TIER_1_HEAD_START_MS = 1_500L

/** Jeda sebelum kubu 3 (YouTube) dipakai. */
private const val TIER_2_HEAD_START_MS = 2_000L

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
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
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

        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
            val enabledProviders = orderedProviders.filter { it.isEnabled(context) }
            // MediaMetadata menyimpan durasi dalam milidetik, sedangkan semua
            // provider (BetterLyrics, LrcLib, Paxsenix, KuGou) memakai detik.
            // Mengirim ms membuat cache BetterLyrics selalu meleset (401) dan
            // pencocokan durasi provider lain gagal.
            val durationSec = (mediaMetadata.duration / 1000).toInt()

            // Bertingkat bertahap (staggered):
            //  - Kubu 1 (lirik per suku kata) langsung jalan
            //  - Kubu 2 menyusul kalau 1.5 dtk kubu 1 belum kasih hasil
            //  - Kubu 3 (YouTube) baru kalau 1 & 2 sama-sama kosong
            // Tujuannya: request hemat + kualitas dijaga, tanpa nunggu 10 dtk
            // kalau lagunya cuma ada di kubu 2.
            Timber.tag("LyricsHelper").d("Staggered fetch for: $cleanedTitle by ${mediaMetadata.artists.joinToString { it.name }}")
            Timber.tag("LyricsHelper").d("Enabled providers in order: ${enabledProviders.joinToString { it.name }}")

            val found: LyricsWithProvider? =
                coroutineScope {
                    val artistLine = mediaMetadata.artists.joinToString { it.name }
                    val albumTitle = mediaMetadata.album?.title

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
                            Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                            null
                        }?.takeIf { it.isNotBlank() }

                    val tier1 = enabledProviders.filter { it.name in TIER_1 }
                    val tier2 = enabledProviders.filter { it.name in TIER_2 }
                    val tier3 = enabledProviders.filter { it.name !in TIER_1 && it.name !in TIER_2 }

                    val winner = CompletableDeferred<Pair<LyricsProvider, String>>()
                    val tier1Pending = AtomicInteger(tier1.size)
                    val tier1AllFailed = CompletableDeferred<Unit>()
                    val tier2Pending = AtomicInteger(tier2.size)
                    val tier2AllFailed = CompletableDeferred<Unit>()

                    fun spawn(provider: LyricsProvider) =
                        launch(Dispatchers.IO) {
                            val lyrics = fetch(provider)
                            if (lyrics != null) {
                                Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                                winner.complete(provider to lyrics)
                            } else {
                                Timber.tag("LyricsHelper").w("${provider.name} failed")
                                if (provider.name in TIER_1 && tier1Pending.decrementAndGet() == 0) {
                                    tier1AllFailed.complete(Unit)
                                }
                                if (provider.name in TIER_2 && tier2Pending.decrementAndGet() == 0) {
                                    tier2AllFailed.complete(Unit)
                                }
                            }
                        }

                    tier1.forEach { spawn(it) }

                    // Kubu 2 menyusul kalau kubu 1 belum berhasil dalam 1.5 dtk,
                    // atau lebih cepat lagi begitu semua anggota kubu 1 gagal.
                    launch {
                        withTimeoutOrNull(TIER_1_HEAD_START_MS) { tier1AllFailed.await() }
                        if (!winner.isCompleted && tier2.isNotEmpty()) {
                            Timber.tag("LyricsHelper").d("Kubu 1 belum ada hasil, menyalakan kubu 2")
                            tier2.forEach { spawn(it) }
                            withTimeoutOrNull(TIER_2_HEAD_START_MS) { tier2AllFailed.await() }
                        }
                        // Kubu 3 hanya kalau kubu 1 & 2 tetap kosong.
                        if (!winner.isCompleted && tier3.isNotEmpty()) {
                            Timber.tag("LyricsHelper").d("Kubu 1 & 2 kosong, memakai kubu 3")
                            tier3.forEach { spawn(it) }
                        }
                    }

                    val result =
                        withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
                            winner.await()
                        }
                    result?.let { (provider, lyrics) ->
                        LyricsWithProvider(
                            LyricsUtils.filterLyricsCreditLines(lyrics),
                            provider.name,
                        )
                    }
                }
            if (found != null) return@withTimeoutOrNull found

            Timber.tag("LyricsHelper").w("No lyrics found after checking all providers")
            LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        return result ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
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
