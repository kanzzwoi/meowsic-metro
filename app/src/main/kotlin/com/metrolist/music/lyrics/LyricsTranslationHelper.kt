/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.api.DeepLService
import com.metrolist.music.api.DeeplFreeService
import com.metrolist.music.api.GoogleFreeService
import com.metrolist.music.api.KnzApiService
import com.metrolist.music.api.OpenRouterService
import com.metrolist.music.api.OpenRouterStreamingService
import com.metrolist.music.constants.LevelTotalExpKey
import com.metrolist.music.extensions.isInternetConnected
import com.metrolist.music.level.levelForTotalExp
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A helper class that provides AI-powered translation for lyrics.
 */
object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private var translationJob: kotlinx.coroutines.Job? = null
    private var isCompositionActive = true

    // Cache translations in memory to avoid redundant API calls during a session
    private val translationCache = ConcurrentHashMap<String, List<String>>()

    // Map of language codes to full names for better AI understanding
    private val LanguageCodeToName = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "bn" to "Bengali",
        "pa" to "Punjabi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "th" to "Thai",
        "id" to "Indonesian",
        "pl" to "Polish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "uk" to "Ukrainian"
    )

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun triggerManualTranslation() {
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _clearTranslationsTrigger.tryEmit(Unit)
        _hasActiveTranslations.value = false
        // Hapus juga cache memori: kalau tidak, terjemahan berikutnya (atau
        // "Muat ulang") langsung memakai cache lama tanpa fetch ulang — dan
        // translateLyrics malah menulis cache itu kembali ke DB. Sampah dari
        // satu respons model yang gagal pun jadi hidup selamanya selama sesi.
        translationCache.clear()
    }

    fun clearTranslations(lyrics: LyricsEntity): LyricsEntity {
        return lyrics.copy(
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = ""
        )
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        // View lirik sudah tidak ada: reset juga status Success supaya
        // chip "Lirik diterjemahkan" tidak beku. Sebelumnya hanya
        // Translating yang di-reset, jadi kalau job ke-cancel di dalam
        // jendela delay(3000) auto-hide, status tertahan di Success
        // dan chip nyangkut selamanya saat lirik dibuka lagi.
        // Error ikut di-reset: kartu merah punya masalah yang sama —
        // tanpa ini, error dari lagu sebelumnya tetap nongol saat
        // lirik dibuka lagi, karena tidak ada yang mengubah status
        // Error menjadi Idle.
        if (_status.value is TranslationStatus.Translating ||
            _status.value is TranslationStatus.Success ||
            _status.value is TranslationStatus.Error
        ) {
            _status.value = TranslationStatus.Idle
        }
    }

    private fun getCacheKey(text: String, mode: String, targetLanguage: String): String {
        return "${text.hashCode()}_${mode}_${targetLanguage}"
    }

    /**
     * True kalau kegagalan ini soal konektivitas (offline, DNS gagal, dsb),
     * bukan soal kredensial/kuota — yang seperti itu tidak perlu ditampilkan
     * sebagai kartu error merah di atas lirik.
     */
    private fun isNetworkError(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is IOException) return true
            cause = cause.cause
        }
        val message = error.message.orEmpty()
        return NETWORK_ERROR_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    private val NETWORK_ERROR_MARKERS = listOf(
        "Unable to resolve host",
        "No address associated with hostname",
        "Network is unreachable",
        "Connection refused",
        "Connection reset",
        "Failed to connect",
        "timed out",
    )

    /**
     * Attempts to parse partial translation content from the AI.
     * This allows updating the UI progressively during streaming.
     */
    private fun tryParsePartialTranslation(content: String, expectedLines: Int): List<String> {
        // AI usually returns lines separated by newlines or numbered lists
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            // Remove common AI formatting like "1. ", "Line 1: ", etc.
            .map { line ->
                line.replace(Regex("^\\d+\\.\\s*"), "")
                    .replace(Regex("^Line\\s+\\d+:\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^-\\s*"), "")
            }
        
        return lines
    }

    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String
    ) {
        if (lyricsEntity == null || lyricsEntity.translatedLyrics.isNullOrBlank()) {
            _hasActiveTranslations.value = false
            lyrics.forEach { it.translatedTextFlow.value = null }
            return
        }
        
        // Only load if language and mode match
        if (lyricsEntity.translationLanguage != targetLanguage || lyricsEntity.translationMode != mode) {
            _hasActiveTranslations.value = false
            lyrics.forEach { it.translatedTextFlow.value = null }
            return
        }
        
        val translatedLines = lyricsEntity.translatedLyrics.split("\n")
        val nonEmptyEntries = lyrics.filter { it.text.isNotBlank() }
        
        if (translatedLines.size >= nonEmptyEntries.size) {
            var transIndex = 0
            var applied = false
            lyrics.forEach { entry ->
                if (entry.text.isNotBlank() && transIndex < translatedLines.size) {
                    // Terjemahan kosong ("") jangan dipasang: UI menampilkannya
                    // sebagai "()" — baris tanpa terjemahan harus tetap null.
                    val translation = translatedLines[transIndex]
                    if (translation.isNotBlank()) {
                        entry.translatedTextFlow.value = translation
                        applied = true
                    }
                    transIndex++
                }
            }

            // Also cache them
            val fullText = nonEmptyEntries.joinToString("\n") { it.text }
            val cacheKey = getCacheKey(fullText, mode, targetLanguage)
            translationCache[cacheKey] = translatedLines
            _hasActiveTranslations.value = applied
        }
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
        database: MusicDatabase? = null,
        systemPrompt: String = "",
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating

        // Clear existing translations to indicate re-translation
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob =
            scope.launch(Dispatchers.IO) {
                try {
                    // Validate inputs — provider gratis (Google/DeepLFree/KNZAPI) tidak butuh key
                    val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
                    val isKeylessProvider = provider == "Google" || provider == "DeepLFree" || provider == "KNZAPI"
                    if (!isKeylessProvider && effectiveApiKey.isBlank()) {
                        _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_api_key_required))
                        return@launch
                    }

                    if (lyrics.isEmpty()) {
                        _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_no_lyrics))
                        return@launch
                    }

                    // Filter out empty lines and keep track of their indices
                    val nonEmptyEntries =
                        lyrics.mapIndexedNotNull { index, entry ->
                            if (entry.text.isNotBlank()) index to entry else null
                        }

                    if (nonEmptyEntries.isEmpty()) {
                        _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_lyrics_empty))
                        return@launch
                    }

                    // Create text from non-empty lines only
                    val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }

                    // Check cache first
                    val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                    val cachedTranslations = translationCache[cacheKey]
                    if (cachedTranslations != null && cachedTranslations.size >= nonEmptyEntries.size) {
                        // Use cached translations
                        var applied = false
                        nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                            if (idx < cachedTranslations.size) {
                                val translation = cachedTranslations[idx]
                                if (translation.isNotBlank()) {
                                    lyrics[originalIndex].translatedTextFlow.value = translation
                                    applied = true
                                }
                            }
                        }
                        _hasActiveTranslations.value = applied
                        _status.value = TranslationStatus.Success

                        // Persist cached translations to DB so loadTranslationsFromDatabase can't
                        // overwrite them with a stale empty entity (e.g. after an untranslate race).
                        if (songId.isNotBlank() && database != null) {
                            try {
                                val currentLyrics = database.lyrics(songId).first()
                                if (currentLyrics != null && currentLyrics.translatedLyrics.isNullOrBlank()) {
                                    database.query {
                                        upsert(
                                            currentLyrics.copy(
                                                translatedLyrics = cachedTranslations.joinToString("\n"),
                                                translationLanguage = targetLanguage,
                                                translationMode = mode,
                                            ),
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to persist cached translations to database")
                            }
                        }

                        delay(3000)
                        if (_status.value is TranslationStatus.Success) {
                            _status.value = TranslationStatus.Idle
                        }
                        return@launch
                    }

                    // Offline: jangan panggil provider sama sekali. Terjemahan
                    // itu best-effort; lirik asli tetap tampil tanpa kartu error.
                    // (Cek di sini, setelah cache — terjemahan yang sudah
                    // tersimpan tetap dipakai saat offline.)
                    if (!context.isInternetConnected()) {
                        _status.value = TranslationStatus.Idle
                        return@launch
                    }

                    // Validate language for all modes
                    if (targetLanguage.isBlank()) {
                        _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_language_required))
                        return@launch
                    }

                    // Convert language code to full language name for better AI understanding
                    val fullLanguageName =
                        LanguageCodeToName[targetLanguage]
                            ?: try {
                                Locale.forLanguageTag(targetLanguage).displayLanguage.takeIf { it.isNotBlank() && it != targetLanguage }
                            } catch (e: Exception) {
                                null
                            }
                            ?: targetLanguage

                    val result =
                        if (provider == "DeepLFree") {
                            Timber.d("Using DeepL free (mobile endpoint), fallback Google")
                            DeeplFreeService.translate(
                                text = fullText,
                                targetLanguage = targetLanguage,
                            )
                        } else if (provider == "KNZAPI") {
                            // KNZAPI hanya untuk level 50 ke atas — dicek di sini
                            // juga (bukan cuma di picker) supaya level yang turun
                            // (mis. habis restore backup) langsung menghentikan
                            // pemakaian provider ini.
                            val level = levelForTotalExp(context.dataStore.data.first()[LevelTotalExpKey] ?: 0L)
                            if (level < KnzApiService.KNZAPI_MIN_LEVEL) {
                                _status.value = TranslationStatus.Error(context.getString(com.metrolist.music.R.string.ai_error_knzapi_level_locked))
                                return@launch
                            }
                            Timber.d("Using KNZAPI for translation")
                            KnzApiService.translate(
                                text = fullText,
                                targetLanguage = fullLanguageName,
                                mode = mode,
                                customSystemPrompt = systemPrompt,
                            )
                        } else if (provider == "Google") {
                            Timber.d("Using Google free translation")
                            GoogleFreeService.translate(
                                text = fullText,
                                targetLanguage = targetLanguage,
                            )
                        } else if (provider == "DeepL") {
                            Timber.d("Using DeepL for translation")
                            // DeepL only supports translation mode
                            DeepLService.translate(
                                text = fullText,
                                targetLanguage = targetLanguage,
                                apiKey = deeplApiKey,
                                formality = deeplFormality,
                            )
                        } else if (useStreaming && provider != "Custom") {
                            Timber.d("Using streaming for translation with provider: $provider")
                            // Use streaming for supported providers
                            var translatedLines: List<String>? = null
                            var hasError = false
                            var errorMessage = ""
                            val contentAccumulator = StringBuilder()

                            OpenRouterStreamingService
                                .streamTranslation(
                                    text = fullText,
                                    targetLanguage = fullLanguageName,
                                    apiKey = apiKey,
                                    baseUrl = baseUrl,
                                    model = model,
                                    mode = mode,
                                    customSystemPrompt = systemPrompt,
                                ).collect { chunk ->
                                    Timber.v("Received streaming chunk: $chunk")
                                    when (chunk) {
                                        is OpenRouterStreamingService.StreamChunk.Content -> {
                                            // Accumulate content for progressive parsing
                                            contentAccumulator.append(chunk.text)

                                            // Try to parse partial content and update UI progressively
                                            val partialContent = contentAccumulator.toString()
                                            val partialResult = tryParsePartialTranslation(partialContent, nonEmptyEntries.size)
                                            if (partialResult.isNotEmpty()) {
                                                // Update lyrics with partial translations as they become available
                                                partialResult.forEachIndexed { idx, translation ->
                                                    if (idx < nonEmptyEntries.size && translation.isNotBlank()) {
                                                        val originalIndex = nonEmptyEntries[idx].first
                                                        lyrics[originalIndex].translatedTextFlow.value = translation
                                                    }
                                                }
                                                _status.value = TranslationStatus.Translating
                                            }
                                        }

                                        is OpenRouterStreamingService.StreamChunk.Complete -> {
                                            Timber.d("Streaming complete with ${chunk.translatedLines.size} lines")
                                            translatedLines = chunk.translatedLines
                                        }

                                        is OpenRouterStreamingService.StreamChunk.Error -> {
                                            Timber.e("Streaming error: ${chunk.message}")
                                            hasError = true
                                            errorMessage = chunk.message
                                        }
                                    }
                                }

                            Timber.d("Streaming collection complete. hasError=$hasError, translatedLines=${translatedLines?.size}")
                            if (hasError) {
                                Result.failure(Exception(errorMessage))
                            } else if (translatedLines != null) {
                                Result.success(translatedLines)
                            } else {
                                Result.failure(Exception("No translation received"))
                            }
                        } else {
                            Timber.d("Using non-streaming for translation")
                            // Use non-streaming for Custom provider or when streaming is disabled
                            OpenRouterService.translate(
                                text = fullText,
                                targetLanguage = fullLanguageName,
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                model = model,
                                mode = mode,
                                customSystemPrompt = systemPrompt,
                            )
                        }

                    result
                        .onSuccess { translatedLines ->
                            // Check if composition is still active before updating state
                            if (!isCompositionActive) {
                                return@onSuccess
                            }

                            // Cache the translations
                            val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                            translationCache[cacheKey] = translatedLines

                            // Save to database if songId is provided
                            if (songId.isNotBlank() && database != null) {
                                try {
                                    val currentLyrics = database.lyrics(songId).first()
                                    if (currentLyrics != null) {
                                        database.query {
                                            upsert(
                                                currentLyrics.copy(
                                                    translatedLyrics = translatedLines.joinToString("\n"),
                                                    translationLanguage = targetLanguage,
                                                    translationMode = mode,
                                                ),
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to save translated lyrics to database")
                                }
                            }

                            // Map translations back to original non-empty entries only
                            val expectedCount = nonEmptyEntries.size

                            when {
                                translatedLines.size >= expectedCount -> {
                                    // Perfect match or more - map to non-empty entries
                                    var applied = false
                                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                        val translation = translatedLines[idx]
                                        if (translation.isNotBlank()) {
                                            lyrics[originalIndex].translatedTextFlow.value = translation
                                            applied = true
                                        }
                                    }
                                    _hasActiveTranslations.value = applied
                                    _status.value = TranslationStatus.Success
                                }

                                translatedLines.size < expectedCount -> {
                                    // Fewer translations than expected - map what we have
                                    var applied = false
                                    translatedLines.forEachIndexed { idx, translation ->
                                        if (idx < nonEmptyEntries.size && translation.isNotBlank()) {
                                            val originalIndex = nonEmptyEntries[idx].first
                                            lyrics[originalIndex].translatedTextFlow.value = translation
                                            applied = true
                                        }
                                    }
                                    _hasActiveTranslations.value = applied
                                    _status.value = TranslationStatus.Success
                                }
                            }

                            // Auto-hide success message after 3 seconds.
                            // Tanpa guard isCompositionActive: flag itu milik
                            // bersama dua lyrics view, jadi bisa false saat
                            // delay berakhir padahal chip masih tampil.
                            delay(3000)
                            if (_status.value is TranslationStatus.Success) {
                                _status.value = TranslationStatus.Idle
                            }
                        }
                        .onFailure { error ->
                            if (!isCompositionActive) {
                                return@onFailure
                            }

                            // Gagal koneksi (mis. offline): diam saja, jangan
                            // tampilkan pesan error mentah di atas lirik.
                            if (isNetworkError(error)) {
                                _status.value = TranslationStatus.Idle
                                return@onFailure
                            }

                            val errorMessage = error.message ?: context.getString(com.metrolist.music.R.string.ai_error_unknown)

                            // Show error in UI
                            _status.value = TranslationStatus.Error(errorMessage)

                            // Kartu error merah jangan nyangkut: sembunyikan
                            // sendiri setelah 5 detik, seperti Success (3 detik).
                            // Sebelumnya tidak ada yang mengubah status Error
                            // menjadi Idle, jadi kartunya nempel selamanya —
                            // bahkan sampai lagu berikutnya — sampai user
                            // menekan terjemah lagi secara manual.
                            delay(5000)
                            if (_status.value is TranslationStatus.Error) {
                                _status.value = TranslationStatus.Idle
                            }
                        }
                } catch (e: Exception) {
                    // Ignore cancellation exceptions or if composition is no longer active
                    if (e !is kotlinx.coroutines.CancellationException && isCompositionActive) {
                        // Gagal koneksi (mis. jaringan putus di tengah jalan):
                        // diam saja, jangan tampilkan pesan error mentah.
                        if (isNetworkError(e)) {
                            _status.value = TranslationStatus.Idle
                        } else {
                            val errorMessage = e.message ?: context.getString(com.metrolist.music.R.string.ai_error_translation_failed)
                            _status.value = TranslationStatus.Error(errorMessage)

                            // Sama seperti di onFailure: jangan biarkan kartu
                            // error nyangkut selamanya.
                            delay(5000)
                            if (_status.value is TranslationStatus.Error) {
                                _status.value = TranslationStatus.Idle
                            }
                        }
                    }
                }
            }
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}
