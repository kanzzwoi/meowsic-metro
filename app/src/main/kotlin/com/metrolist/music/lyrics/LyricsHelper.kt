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
private val TIER_1 = setOf("Paxsenix", "BetterLyrics", "Unison")

/** Kubu 2: LyricsPlus — cepat dan sebagian sudah per kata. */
private val TIER_2 = setOf("LyricsPlus")

/** Kubu 3: LrcLib — per baris. */
private val TIER_3 = setOf("LrcLib")

private val ALL_TIER_NAMES = TIER_1 + TIER_2 + TIER_3

/**
 * Tenggat "upgrade" lirik ke kubu 1. Diukur: Paxsenix paling lama 12,78 dtk
 * untuk lagu yang belum ada di cache-nya, jadi 15 dtk sudah ada margin.
 */
private const val UPGRADE_FETCH_TIMEOUT_MS = 15_000L

/** Batas per provider saat upgrade (sedikit di bawah tenggat total). */
private const val UPGRADE_PER_PROVIDER_TIMEOUT_MS = 14_000L

/** Jeda sebelum kubu 2 ikut jalan kalau kubu 1 belum ada hasil. */
private const val TIER_1_HEAD_START_MS = 1_500L

/**
 * Jeda untuk setiap kubu berikutnya (kubu 3, 4, ...).
 * Diukur dari server LyricsPlus: kalau lagunya ada, jawabannya 0,03–0,3 dtk;
 * kalau harus nembak upstream dan tidak ketemu, baru balas 404 di ~8–10 dtk.
 * Jadi menunggu 1,5 dtk sudah cukup — kalau lewat itu hampir pasti LP kosong.
 */
private const val TIER_N_HEAD_START_MS = 1_500L

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

    /**
     * Cuma nanya kubu 1 (Paxsenix / BetterLyrics / Unison) dengan tenggat
     * longgar. Dipakai untuk menaikkan kualitas lirik yang tadinya menang cepat
     * dari kubu bawah (mis. LrcLib), karena Paxsenix bisa butuh 12-13 detik
     * untuk lagu yang belum ada di cache-nya.
     */
    suspend fun getTierOneLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider? {
        val orderedProviders = context.dataStore.data
            .map { preferences -> resolveLyricsProviders(preferences) }
            .first()
        val enabled = orderedProviders.filter { it.isEnabled(context) && it.name in TIER_1 }
        if (enabled.isEmpty()) return null

        val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
        val durationSec = (mediaMetadata.duration / 1000).toInt()
        val artistLine = mediaMetadata.artists.joinToString { it.name }
        val albumTitle = mediaMetadata.album?.title

        return withTimeoutOrNull(UPGRADE_FETCH_TIMEOUT_MS) {
            coroutineScope {
                val winner = CompletableDeferred<LyricsWithProvider>()
                enabled.forEach { provider ->
                    launch(Dispatchers.IO) {
                        val text =
                            runCatching {
                                // Paxsenix pernah 12,8 dtk untuk lagu yang belum
                                // ada di cache-nya, jadi jangan dipotong 10 dtk.
                                withTimeoutOrNull(UPGRADE_PER_PROVIDER_TIMEOUT_MS) {
                                    provider.getLyrics(
                                        context,
                                        mediaMetadata.id,
                                        cleanedTitle,
                                        artistLine,
                                        durationSec,
                                        albumTitle,
                                    )
                                }?.getOrNull()
                            }.getOrNull()?.takeIf { it.isNotBlank() }
                        if (text != null) winner.complete(LyricsWithProvider(text, provider.name))
                    }
                }
                winner.await()
            }
        }
    }

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
            //   Kubu 1: Paxsenix, BetterLyrics, Unison (lirik per suku kata)
            //   Kubu 2: LyricsPlus (cepat, sebagian per kata)
            //   Kubu 3: LrcLib (per baris)
            //   Kubu 4: sisanya (KuGou, YouTube) — paling akhir
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

                    val tiers =
                        listOf(
                            enabledProviders.filter { it.name in TIER_1 },
                            enabledProviders.filter { it.name in TIER_2 },
                            enabledProviders.filter { it.name in TIER_3 },
                            enabledProviders.filter { it.name !in ALL_TIER_NAMES },
                        )

                    val winner = CompletableDeferred<Pair<LyricsProvider, String>>()

                    class Gate(size: Int) {
                        val pending = AtomicInteger(size)
                        val allFailed = CompletableDeferred<Unit>()
                    }

                    val gates = tiers.map { Gate(it.size) }

                    fun spawn(
                        provider: LyricsProvider,
                        gate: Gate,
                    ) = launch(Dispatchers.IO) {
                        val lyrics = fetch(provider)
                        if (lyrics != null) {
                            Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                            winner.complete(provider to lyrics)
                        } else {
                            Timber.tag("LyricsHelper").w("${provider.name} failed")
                            if (gate.pending.decrementAndGet() == 0) gate.allFailed.complete(Unit)
                        }
                    }

                    tiers[0].forEach { spawn(it, gates[0]) }

                    // Kubu berikutnya menyala kalau kubu sebelumnya gagal semua,
                    // atau belum ada hasil dalam jatah waktu yang ditentukan.
                    launch {
                        for (index in 1 until tiers.size) {
                            if (winner.isCompleted) return@launch
                            val headStart =
                                when (index) {
                                    1 -> TIER_1_HEAD_START_MS
                                    else -> TIER_N_HEAD_START_MS
                                }
                            withTimeoutOrNull(headStart) { gates[index - 1].allFailed.await() }
                            if (winner.isCompleted) return@launch
                            if (tiers[index].isEmpty()) continue
                            Timber.tag("LyricsHelper").d("Menyalakan kubu ${index + 1}")
                            tiers[index].forEach { spawn(it, gates[index]) }
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
