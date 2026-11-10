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
import kotlinx.coroutines.channels.Channel
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

private const val MAX_LYRICS_FETCH_MS = 12_000L
private const val PER_PROVIDER_TIMEOUT_MS = 10_000L

/** Jatah per provider saat balapan. */
private const val RACE_PROVIDER_TIMEOUT_MS = 10_000L

/**
 * Jeda tunggu lirik PER KATA sebelum jatuh ke versi per baris.
 * Kalau yang datang duluan masih per baris, tahan dulu sampai jeda ini
 * untuk memberi kesempatan provider mana pun yang punya lirik per kata/huruf.
 * Dihitung sejak hasil pertama datang. Kalau dalam jeda ini tidak ada yang
 * per kata, pakai lirik per baris yang paling awal muncul (tanpa penukaran
 * diam-diam). Semua provider setara; tidak ada yang diprioritaskan
 * berdasarkan nama.
 */
private const val WORD_GRACE_MS = 3_000L
private const val PROVIDER_NONE = ""

/** Kubu 1 (berlomba bersamaan): siapa cepat & punya timing per kata, dia menang. */
private val TIER_1 = setOf("BetterLyrics", "Unison", "LyricsPlus", "KuGou", "QQMusic", "LrcRed", "Paxsenix")

/** Kubu 2: cadangan per baris. */
private val TIER_2 = setOf("LrcLib")

/** Kubu 3: sisa (YouTube) — paling akhir. */
private val TIER_3 = setOf<String>()

private val ALL_TIER_NAMES = TIER_1 + TIER_2 + TIER_3

/** Penanda timestamp per kata di LRC, contoh: <00:12.34>kata */
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

/** Ada timestamp sama sekali (per baris maupun per kata). */
private val HAS_TIMESTAMP_REGEX = Regex("\\[\\d{1,2}:\\d{2}|(?m)^\\s*<[^>\\n]+:\\d")

private val WORD_LEVEL_REGEX = Regex("(?m)^\\s*(<[^>\\n]+:\\d|<\\d+,\\d+,\\d+>)")

/**
 * Tenggat "upgrade" lirik ke kubu 1. Diukur: Paxsenix paling lama 12,78 dtk
 * untuk lagu yang belum ada di cache-nya, jadi 15 dtk sudah ada margin.
 */
private const val UPGRADE_FETCH_TIMEOUT_MS = 12_000L

/** Batas per provider saat upgrade (sedikit di bawah tenggat total). */
private const val UPGRADE_PER_PROVIDER_TIMEOUT_MS = 10_000L

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
        val durationSec = mediaMetadata.duration
        val artistLine = mediaMetadata.artists.joinToString { it.name }
        val albumTitle = mediaMetadata.album?.title

        return withTimeoutOrNull(UPGRADE_FETCH_TIMEOUT_MS) {
            coroutineScope {
                val winner = CompletableDeferred<LyricsWithProvider>()
                val jobs = mutableListOf<Job>()
                enabled.forEach { provider ->
                    jobs += launch(Dispatchers.IO) {
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
                withTimeoutOrNull(UPGRADE_FETCH_TIMEOUT_MS) { winner.await() }?.also {
                    jobs.forEach { job -> job.cancel() }
                } ?: run {
                    jobs.forEach { job -> job.cancel() }
                    null
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        val startedAt = System.currentTimeMillis()
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
            // MediaMetadata.duration SUDAH dalam detik (SongItem.duration dari
            // InnerTube). Sebelumnya dibagi 1000 sehingga provider menerima 0:
            // BetterLyrics menghilangkan parameter d -> cache selalu meleset
            // (401 / lambat 10 dtk), LrcLib & provider lain gagal mencocokkan.
            val durationSec = mediaMetadata.duration

            // Bertingkat bertahap (staggered):
            //   Kubu 1: BetterLyrics, Unison, LyricsPlus, KuGou, QQMusic,
            //           LrcRed, Paxsenix (diadu bersamaan; per kata/huruf menang)
            //   Kubu 2: LrcLib (cadangan per baris)
            //   Kubu 3: sisanya (YouTube) — paling akhir
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

                    // Semua provider setara. Yang diutamakan hanya jenis liriknya:
                    //   per kata/huruf  >  per baris
                    // Provider mana pun yang lebih dulu membawa per kata/huruf, itu
                    // yang dipakai. Kalau sampai jeda habis tidak ada, pakai hasil
                    // pertama yang masuk (per baris). Tanpa penukaran diam-diam.
                    suspend fun race(
                        providers: List<LyricsProvider>,
                        graceMs: Long = 0L,
                    ): Pair<LyricsProvider, String>? {
                        if (providers.isEmpty()) return null
                        val results = Channel<Pair<LyricsProvider, String>>(Channel.UNLIMITED)
                        val notes = java.util.concurrent.CopyOnWriteArrayList<String>()
                        val jobs = mutableListOf<Job>()
                        val done = java.util.concurrent.atomic.AtomicInteger(0)
                        providers.forEach { provider ->
                            jobs += launch(Dispatchers.IO) {
                              try {
                                val t0 = System.currentTimeMillis()
                                val lyrics = fetch(provider, RACE_PROVIDER_TIMEOUT_MS)
                                val spent = (System.currentTimeMillis() - t0) / 1000.0
                                val usable = lyrics?.takeIf { HAS_TIMESTAMP_REGEX.containsMatchIn(it) }
                                // Tanda ✗ = provider ini GAGAL (bukan lebih cepat).
                                notes.add(
                                    "%s %.1fs%s".format(
                                        provider.name,
                                        spent,
                                        if (usable == null) " ✗" else "",
                                    ),
                                )
                                LyricsStats.detail = notes.joinToString(", ")
                                if (usable == null) {
                                    Timber.tag("LyricsHelper").w("${provider.name}: gagal / tanpa timestamp")
                                    return@launch
                                }
                                Timber.tag("LyricsHelper").i(
                                    "${provider.name}: " +
                                        if (WORD_LEVEL_REGEX.containsMatchIn(usable)) "per kata" else "per baris",
                                )
                                results.trySend(provider to usable)
                              } finally {
                                // Semua provider selesai (berhasil/gagal) -> jangan
                                // menunggu batas waktu penuh; langsung lanjut.
                                if (done.incrementAndGet() == providers.size) results.close()
                              }
                            }
                        }

                        var firstAny: Pair<LyricsProvider, String>? = null
                        var firstAt = 0L
                        val hardDeadline = System.currentTimeMillis() + RACE_PROVIDER_TIMEOUT_MS

                        while (true) {
                            // Jeda seleksi dihitung dari hasil pertama; sebelum itu
                            // tunggu sampai batas waktu provider.
                            val limit =
                                if (firstAt == 0L || graceMs <= 0L) {
                                    hardDeadline
                                } else {
                                    minOf(hardDeadline, firstAt + graceMs)
                                }
                            val remaining = limit - System.currentTimeMillis()
                            if (remaining <= 0) break
                            val item =
                                try {
                                    withTimeoutOrNull(remaining) { results.receive() }
                                } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                                    null
                                } ?: break

                            if (firstAt == 0L) firstAt = System.currentTimeMillis()
                            if (firstAny == null) firstAny = item
                            if (WORD_LEVEL_REGEX.containsMatchIn(item.second)) {
                                // Provider mana pun, asal per kata/huruf: pakai ini.
                                results.close()
                                jobs.forEach { it.cancel() }
                                return item
                            }
                        }

                        results.close()
                        jobs.forEach { it.cancel() }
                        return firstAny
                    }

                    val found =
                        race(tierA, WORD_GRACE_MS)
                            ?: race(tierB)?.also {
                                Timber.tag("LyricsHelper").i("cadangan: ${it.first.name}")
                            }
                            ?: race(tierC)?.also {
                                Timber.tag("LyricsHelper").i("terakhir: ${it.first.name}")
                            }

                    found?.let { (provider, lyrics) ->
                        LyricsWithProvider(
                            LyricsUtils.filterLyricsCreditLines(lyrics),
                            provider.name,
                        )
                    }
                }
            if (found != null) {
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
                LyricsStats.lastTrackId = mediaMetadata.id
                LyricsStats.last =
                    "%s • %.1f dtk • %s".format(
                        found.provider,
                        elapsed,
                        if (WORD_LEVEL_REGEX.containsMatchIn(found.lyrics)) "per kata" else "per baris",
                    )
                Timber.tag("LyricsHelper").i("RESULT ${LyricsStats.last}")
                return@withTimeoutOrNull found
            }

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
