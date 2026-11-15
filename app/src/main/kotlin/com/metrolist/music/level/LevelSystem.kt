/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.level

import androidx.compose.ui.graphics.Color
import com.metrolist.music.R

/** 1 menit putar = 5 EXP, diakumulasi per 2 menit (10 EXP). */
const val EXP_PER_MINUTE = 5L

/** Naik 1 level tiap 100 EXP. */
const val EXP_PER_LEVEL = 100L

/** Level tertinggi yang ditampilkan. */
const val MAX_LEVEL = 99_999

data class LevelTier(
    val name: String,
    val minLevel: Int,
    /** null = tanpa batas atas (tier tertinggi). */
    val maxLevel: Int?,
    val badgeRes: Int,
    val accent: Color,
)

val LEVEL_TIERS =
    listOf(
        LevelTier("Rookie", 1, 9, R.drawable.tier_rookie, Color(0xFFC7CDD6)),
        LevelTier("Rhythm", 10, 19, R.drawable.tier_rhythm, Color(0xFF5B8DEF)),
        LevelTier("Monarch", 20, 29, R.drawable.tier_monarch, Color(0xFFD9A441)),
        LevelTier("Phantom", 30, 39, R.drawable.tier_phantom, Color(0xFFA66CFF)),
        LevelTier("Aqua", 40, 49, R.drawable.tier_aqua, Color(0xFF4FC3E8)),
        LevelTier("Amethyst", 50, 59, R.drawable.tier_amethyst, Color(0xFFB388FF)),
        LevelTier("Frost", 60, 69, R.drawable.tier_frost, Color(0xFF8FD3F4)),
        LevelTier("Galaxy", 70, 79, R.drawable.tier_galaxy, Color(0xFF5B6CFF)),
        LevelTier("Inferno", 80, 89, R.drawable.tier_inferno, Color(0xFFFF5252)),
        LevelTier("Sovereign", 90, 99, R.drawable.tier_sovereign, Color(0xFFFFB300)),
        LevelTier("Infinity", 100, null, R.drawable.tier_infinity, Color(0xFFEDEFF7)),
    )

/** Level 1 di 0 EXP; level 2 di 100 EXP; level N di (N-1)*100 EXP. */
fun levelForTotalExp(totalExp: Long): Int =
    ((totalExp.coerceAtLeast(0L) / EXP_PER_LEVEL).toInt() + 1).coerceAtMost(MAX_LEVEL)

/** EXP yang sudah terkumpul di dalam level saat ini (0-99). */
fun expIntoLevel(totalExp: Long): Long = totalExp.coerceAtLeast(0L) % EXP_PER_LEVEL

fun tierForLevel(level: Int): LevelTier =
    LEVEL_TIERS.firstOrNull { level >= it.minLevel && (it.maxLevel == null || level <= it.maxLevel) }
        ?: LEVEL_TIERS.last()
