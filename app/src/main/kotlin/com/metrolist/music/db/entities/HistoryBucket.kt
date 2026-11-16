/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable

/** Satu bucket hitungan pemutaran dari tabel event, ala v14 (HistoryBucket). */
@Immutable
data class HistoryBucket(
    val bucket: String,
    val count: Int,
)
