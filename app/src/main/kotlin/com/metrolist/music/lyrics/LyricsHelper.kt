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
import kotlinx.coroutines.channels.Channel
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

/** Provider yang dilombakan: siapa cepat memberi lirik, dia dipakai. */
private val RACE_PROVIDERS = setOf("Paxsenix", "Unison", "BetterLyrics")

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

            // Semua provider dijalankan PARALEL, lalu hasilnya dipilih sesuai
            // urutan prioritas. Secara berurutan, provider lambat (Paxsenix
            // retry, KuGou 3 request) menghabiskan jatah waktu sehingga KuGou
            // tidak pernah sempat dicoba dan lagu jatuh ke lirik YouTube.
            Timber.tag("LyricsHelper").d("Starting parallel fetch for: $cleanedTitle by ${mediaMetadata.artists.joinToString { it.name }}")
            Timber.tag("LyricsHelper").d("Enabled providers in order: ${enabledProviders.joinToString { it.name }}")

            val found: LyricsWithProvider? =
                coroutineScope {
                    val artistLine = mediaMetadata.artists.joinToString { it.name }
                    val albumTitle = mediaMetadata.album?.title
                    val fetches =
                        enabledProviders.associateWith { provider ->
                            async(Dispatchers.IO) {
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
                            }
                        }

                    // Paxsenix vs Unison: dijalankan bersamaan, yang lebih dulu
                    // mengembalikan lirik dipakai. Provider lain tetap mengikuti
                    // urutan prioritas seperti biasa.
                    val racers = enabledProviders.filter { it.name in RACE_PROVIDERS }
                    val racerNames = racers.map { it.name }.toSet()
                    var winner: Pair<LyricsProvider, String>? = null
                    if (racers.size > 1) {
                        val results = Channel<Pair<LyricsProvider, String>>(Channel.UNLIMITED)
                        val jobs =
                            racers.map { provider ->
                                launch {
                                    fetches[provider]?.await()?.let { results.send(provider to it) }
                                }
                            }
                        launch {
                            jobs.forEach { it.join() }
                            results.close()
                        }
                        winner = results.receiveCatching().getOrNull()
                    }

                    if (winner != null) {
                        Timber.tag("LyricsHelper").i("Got lyrics from ${winner.first.name} (pacu cepat)")
                        return@coroutineScope LyricsWithProvider(
                            LyricsUtils.filterLyricsCreditLines(winner.second),
                            winner.first.name,
                        )
                    }

                    for ((provider, job) in fetches) {
                        if (provider.name in racerNames) continue
                        val lyrics = job.await()
                        if (lyrics != null) {
                            Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                            return@coroutineScope LyricsWithProvider(
                                LyricsUtils.filterLyricsCreditLines(lyrics),
                                provider.name,
                            )
                        }
                        Timber.tag("LyricsHelper").w("${provider.name} failed")
                    }
                    null
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
