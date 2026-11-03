/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity

/**
 * Simpan lirik tanpa menghapus terjemahan yang sudah ada.
 *
 * Sebelumnya setiap fetch/upgrade lirik menulis LyricsEntity baru tanpa
 * translatedLyrics, sehingga terjemahan yang sudah dibuat hilang begitu
 * keluar-masuk pemutar (atau saat lirik ditukar diam-diam ke versi per kata).
 */
suspend fun MusicDatabase.upsertLyricsKeepingTranslation(entity: LyricsEntity) {
    query {
        val previous = lyricsByIdBlocking(entity.id)
        val merged =
            if (previous != null && entity.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                entity.copy(
                    translatedLyrics = previous.translatedLyrics,
                    translationLanguage = previous.translationLanguage,
                    translationMode = previous.translationMode,
                )
            } else {
                entity
            }
        upsert(merged)
    }
}
