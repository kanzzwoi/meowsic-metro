/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

object WrappedConstants {
    // Tahun berjalan, supaya recap selalu memakai data tahun ini.
    val YEAR: Int = java.time.LocalDate.now().year
    val PLAYLIST_NAME: String get() = "Meowsic $YEAR"
}
