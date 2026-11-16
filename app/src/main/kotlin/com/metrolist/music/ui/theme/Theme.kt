/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import com.metrolist.music.constants.ActiveFontIdKey
import com.metrolist.music.constants.CustomFontFileKey
import com.metrolist.music.utils.AppFontManager
import com.metrolist.music.utils.QuickFontManager
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Determine if system dynamic colors should be used (Android S+ and default theme color)
    val useSystemDynamicColor = (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useSystemDynamicColor) {
        // Use standard Material 3 dynamic color functions for system wallpaper colors
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Use materialKolor only when a specific seed color is provided
        rememberDynamicColorScheme(
            seedColor = themeColor, // themeColor is guaranteed non-default here
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot // Keep existing style
        )
    }

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = getAppTypography(),
        content = content,
    )
}

/**
 * Tipografi aplikasi dengan font pilihan pengguna, ala Metrolist v14
 * (androidApp/.../ui/theme/Type.kt): font di-resolve dari file (bukan
 * dari resource) di IO, lalu diterapkan ke semua 15 gaya teks. Tanpa
 * font pilihan, hasilnya sama dengan Typography() bawaan.
 */
@Composable
fun getAppTypography(): Typography {
    val context = LocalContext.current
    val (activeFontId) = rememberPreference(ActiveFontIdKey, QuickFontManager.SYSTEM_ID)
    val (customFontFile) = rememberPreference(CustomFontFileKey, "")
    val font by produceState<FontFamily>(FontFamily.Default, activeFontId, customFontFile) {
        value = withContext(Dispatchers.IO) { resolveFontFamily(context, activeFontId, customFontFile) }
    }
    val typography = Typography()

    return Typography(
        fontFamily = font,
        displayLarge = typography.displayLarge.copy(fontFamily = font),
        displayMedium = typography.displayMedium.copy(fontFamily = font),
        displaySmall = typography.displaySmall.copy(fontFamily = font),
        headlineLarge = typography.headlineLarge.copy(fontFamily = font),
        headlineMedium = typography.headlineMedium.copy(fontFamily = font),
        headlineSmall = typography.headlineSmall.copy(fontFamily = font),
        titleLarge = typography.titleLarge.copy(fontFamily = font),
        titleMedium = typography.titleMedium.copy(fontFamily = font),
        titleSmall = typography.titleSmall.copy(fontFamily = font),
        bodyLarge = typography.bodyLarge.copy(fontFamily = font),
        bodyMedium = typography.bodyMedium.copy(fontFamily = font),
        bodySmall = typography.bodySmall.copy(fontFamily = font),
        labelLarge = typography.labelLarge.copy(fontFamily = font),
        labelMedium = typography.labelMedium.copy(fontFamily = font),
        labelSmall = typography.labelSmall.copy(fontFamily = font),
    )
}

private fun resolveFontFamily(
    context: android.content.Context,
    activeFontId: String,
    customFontFile: String,
): FontFamily {
    when (activeFontId) {
        QuickFontManager.SYSTEM_ID -> {
            return FontFamily.Default
        }

        QuickFontManager.CUSTOM_ID -> {
            return AppFontManager.loadFontFamily(context, customFontFile)
        }

        else -> {
            if (QuickFontManager.isSupportedId(activeFontId)) {
                return QuickFontManager.loadFontFamily(context, activeFontId)
            }
        }
    }

    return FontFamily.Default
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
