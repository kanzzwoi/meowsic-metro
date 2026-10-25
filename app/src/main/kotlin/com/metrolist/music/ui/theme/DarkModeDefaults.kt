/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.utils.rememberEnumPreference

/**
 * The dark mode preference used when the user has never picked one.
 *
 * Every screen that reads [DarkModeKey] MUST use this same default. When two
 * screens disagree, a fresh install renders half of the UI with light-mode
 * colors on top of a dark theme: the player would draw a black seek bar and a
 * black play button on a dark background. Toggling the theme hides the bug
 * because that writes an explicit value to the data store, but the defaults
 * still have to agree.
 */
val DefaultDarkMode = DarkMode.ON

/**
 * Whether the dark theme is active, honouring the user's choice.
 *
 * [DarkMode.AUTO] follows the system setting; any explicit choice wins.
 */
@Composable
fun rememberIsDarkTheme(): Boolean {
    val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DefaultDarkMode)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    return remember(darkMode, isSystemInDarkTheme) {
        if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
    }
}
