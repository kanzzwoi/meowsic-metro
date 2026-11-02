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
import kotlinx.coroutines.selects.select

private const val MAX_LYRICS_FETCH_MS = 25_000L
private const val PER_PROVIDER_TIMEOUT_MS = 10_000L

/** Jatah per provider di kubu 1 — Paxsenix pernah 17,8 dtk untuk lagu baru. */
private const val RACE_A_PROVIDER_TIMEOUT_MS = 20_000L
private const val PROVIDER_NONE = ""

/** Kubu 1 (berlomba bersamaan): siapa cepat & punya timing per kata, dia menang. */
private val TIER_1 = setOf("Paxsenix", "BetterLyrics", "Unison", "LyricsPlus")

/** Kubu 2: cadangan per baris. */
private val TIER_2 = setOf("LrcLib", "Kugou")

/** Kubu 3: sisa (YouTube) — paling akhir. */
private val TIER_3 = setOf<String>()

private val ALL_TIER_NAMES = TIER_1 + TIER_2 + TIER_3

/** Penanda timestamp per kata di LRC, contoh: <00:12.34>kata */
private val WORD_LEVEL_REGEX = Regex("(?m)^\\s*<[^>\\n]+:\\d")

/**
 * Tenggat "upgrade" lirik ke kubu 1. Diukur: Paxsenix paling lama 12,78 dtk
 * untuk lagu yang belum ada di cache-nya, jadi 15 dtk sudah ada margin.
 */
private const val UPGRADE_FETCH_TIMEOUT_MS = 20_000L

/** Batas per provider saat upgrade (sedikit di bawah tenggat total). */
private const val UPGRADE_PER_PROVIDER_TIMEOUT_MS = 19_000L

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
                        // Upgrade cuma boleh ke yang punya timing per kata; kalau
                        // tidak, lirik per baris yang sudah ada malah turun kualitas.
                        if (text != null && WORD_LEVEL_REGEX.containsMatchIn(text)) {
                            winner.complete(LyricsWithProvider(text, provider.name))
                        }
                    }
                }
                winner.await()
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

                    suspend fun fetch(
                        provider: LyricsProvider,
                        timeoutMs: Long = PER_PROVIDER_TIMEOUT_MS,
                    ): String? =
                        runCatching {
                            withTimeoutOrNull(timeoutMs) {
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

                    val tierA = enabledProviders.filter { it.name in TIER_1 }
                    val tierB = enabledProviders.filter { it.name in TIER_2 }
                    val tierC = enabledProviders.filter { it.name !in ALL_TIER_NAMES }

                    // Kubu 1 berlomba bersamaan: yang per kata langsung dipakai.
                    // Kalau yang datang duluan cuma per baris, hasilnya disimpan
                    // dan tetap menunggu provider lain sampai semuanya selesai —
                    // baru pakai per baris kalau tidak ada satu pun yang per kata.
                    val wordResult = CompletableDeferred<Pair<LyricsProvider, String>>()
                    val lineResult = CompletableDeferred<Pair<LyricsProvider, String>>()
                    val allADone = CompletableDeferred<Unit>()
                    val pendingA = AtomicInteger(tierA.size)

                    fun spawnA(provider: LyricsProvider) =
                        launch(Dispatchers.IO) {
                            val lyrics = fetch(provider, RACE_A_PROVIDER_TIMEOUT_MS)
                            if (lyrics != null) {
                                if (WORD_LEVEL_REGEX.containsMatchIn(lyrics)) {
                                    Timber.tag("LyricsHelper").i("${provider.name}: lirik per kata")
                                    wordResult.complete(provider to lyrics)
                                } else {
                                    Timber.tag("LyricsHelper").i("${provider.name}: lirik per baris (cadangan)")
                                    if (!lineResult.isCompleted) lineResult.complete(provider to lyrics)
                                }
                            } else {
                                Timber.tag("LyricsHelper").w("${provider.name} gagal")
                            }
                            if (pendingA.decrementAndGet() == 0) allADone.complete(Unit)
                        }

                    tierA.forEach { spawnA(it) }

                    suspend fun awaitTierA(): Pair<LyricsProvider, String>? {
                        if (tierA.isEmpty()) return null
                        // Tunggu sampai salah satu kasih lirik per kata, ATAU semua
                        // provider kubu 1 selesai (baru pakai yang per baris).
                        val firstKind =
                            withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
                                select<String> {
                                    wordResult.onAwait { "word" }
                                    allADone.onAwait { "done" }
                                }
                            }
                        return when (firstKind) {
                            "word" -> wordResult.getCompleted()
                            "done" -> if (lineResult.isCompleted) lineResult.getCompleted() else null
                            else ->
                                when {
                                    wordResult.isCompleted -> wordResult.getCompleted()
                                    lineResult.isCompleted -> lineResult.getCompleted()
                                    else -> null
                                }
                        }
                    }

                    suspend fun raceOthers(providers: List<LyricsProvider>): Pair<LyricsProvider, String>? {
                        if (providers.isEmpty()) return null
                        val winner = CompletableDeferred<Pair<LyricsProvider, String>>()
                        providers.forEach { provider ->
                            launch(Dispatchers.IO) {
                                val lyrics = fetch(provider)
                                if (lyrics != null) winner.complete(provider to lyrics)
                            }
                        }
                        return withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) { winner.await() }
                    }

                    val found: Pair<LyricsProvider, String>? =
                        awaitTierA()
                            ?: raceOthers(tierB)?.also {
                                Timber.tag("LyricsHelper").i("cadangan: ${it.first.name}")
                            }
                            ?: raceOthers(tierC)?.also {
                                Timber.tag("LyricsHelper").i("terakhir: ${it.first.name}")
                            }

                    found?.let { (provider, lyrics) ->
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
