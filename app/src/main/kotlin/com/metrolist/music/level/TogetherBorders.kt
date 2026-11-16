/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.level

import com.metrolist.music.R

/**
 * Border profil Listen Together. Satu border per tier level, unlock di level
 * minimal tiernya; nama file PNG kiriman user (1.png, 10.png, ... 100.png)
 * adalah level unlock-nya. Aset sudah dikompres ke WebP 512px
 * di res/drawable-nodpi sebagai border_level_<level>.webp.
 *
 * Infinity (100) berlaku sampai level 998; Omniverse (999) untuk level 999+.
 *
 * Border disimpan/dikirim sebagai String level unlock-nya ("10"), "" = tanpa border.
 */

/** Level minimal tiap border, urut dari Rookie sampai Omniverse. */
val TOGETHER_BORDER_LEVELS = listOf(1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 999)

/** Border yang sudah keunlock di level ini. */
fun unlockedBorders(level: Int): List<Int> = TOGETHER_BORDER_LEVELS.filter { it <= level }

/** Drawable border untuk level unlock tertentu, atau null kalau bukan border yang dikenal. */
fun borderDrawableRes(borderLevel: Int): Int? =
    when (borderLevel) {
        1 -> R.drawable.border_level_1
        10 -> R.drawable.border_level_10
        20 -> R.drawable.border_level_20
        30 -> R.drawable.border_level_30
        40 -> R.drawable.border_level_40
        50 -> R.drawable.border_level_50
        60 -> R.drawable.border_level_60
        70 -> R.drawable.border_level_70
        80 -> R.drawable.border_level_80
        90 -> R.drawable.border_level_90
        100 -> R.drawable.border_level_100
        999 -> R.drawable.border_level_999
        else -> null
    }

/** Drawable border dari String yang dikirim lewat profil ("10"), atau null kalau kosong/tak dikenal. */
fun borderDrawableRes(border: String): Int? = border.toIntOrNull()?.let { borderDrawableRes(it) }

/** Nama tier dari border ini, mis. "Rhythm" untuk border level 10. */
fun borderTierName(borderLevel: Int): String = tierForLevel(borderLevel).name
