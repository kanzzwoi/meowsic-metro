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
const val MAX_LEVEL = 2_000

/** Total EXP tertinggi yang bisa disimpan (level 2000). */
const val MAX_TOTAL_EXP = (MAX_LEVEL - 1) * EXP_PER_LEVEL

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
        LevelTier("Groove", 10, 19, R.drawable.tier_rhythm, Color(0xFF5B8DEF)),
        LevelTier("Monarch", 20, 29, R.drawable.tier_monarch, Color(0xFFD9A441)),
        LevelTier("Phantom", 30, 39, R.drawable.tier_phantom, Color(0xFFA66CFF)),
        LevelTier("Aqua", 40, 49, R.drawable.tier_aqua, Color(0xFF4FC3E8)),
        LevelTier("Amethyst", 50, 59, R.drawable.tier_amethyst, Color(0xFFB388FF)),
        LevelTier("Frost", 60, 69, R.drawable.tier_frost, Color(0xFF8FD3F4)),
        LevelTier("Galaxy", 70, 79, R.drawable.tier_galaxy, Color(0xFF5B6CFF)),
        LevelTier("Inferno", 80, 89, R.drawable.tier_inferno, Color(0xFFFF5252)),
        LevelTier("Sovereign", 90, 99, R.drawable.tier_sovereign, Color(0xFFFFB300)),
        LevelTier("Infinity", 100, 998, R.drawable.tier_infinity, Color(0xFFEDEFF7)),
        LevelTier("Omniverse", 999, null, R.drawable.tier_omniverse, Color(0xFFC084FC)),
    )

/** Level 1 di 0 EXP; level 2 di 100 EXP; level N di (N-1)*100 EXP. */
fun levelForTotalExp(totalExp: Long): Int =
    ((totalExp.coerceAtLeast(0L) / EXP_PER_LEVEL).toInt() + 1).coerceAtMost(MAX_LEVEL)

/**
 * EXP yang boleh disimpan: 0 sampai [MAX_TOTAL_EXP] (level 2000). Nilai di
 * luar itu tidak mungkin didapat lewat jalur resmi (ticker +5/menit, cheat
 * menu di-clamp) — jadi dianggap hasil ubahan dan dikembalikan ke 0.
 * Dipakai di tiap titik yang membaca/menulis EXP (ticker, cheat menu, room).
 */
fun sanitizeTotalExp(totalExp: Long): Long =
    if (totalExp in 0..MAX_TOTAL_EXP) totalExp else 0L

/** Label yang tampil sebagai pengganti angka level kalau level-nya tidak valid
 *  (di atas 2000 / bukan angka) — dipakai server + aplikasi untuk menandai cheater. */
const val SHAME_LEVEL_LABEL = "pro🇮🇱🏳️‍🌈"

/** true kalau level-nya angka valid (1..2000): badge + border boleh tampil. */
fun isValidLevel(level: String): Boolean {
    val n = level.toIntOrNull()
    return n != null && n in 1..MAX_LEVEL
}

/**
 * Angka level yang tampil di Together (1..2000), atau [SHAME_LEVEL_LABEL]
 * kalau level-nya tidak valid. Null kalau level belum diketahui ("").
 */
fun displayLevel(level: String): String? {
    if (level.isBlank()) return null
    return if (isValidLevel(level)) level.toIntOrNull().toString() else SHAME_LEVEL_LABEL
}

/** EXP yang sudah terkumpul di dalam level saat ini (0-99). */
fun expIntoLevel(totalExp: Long): Long = totalExp.coerceAtLeast(0L) % EXP_PER_LEVEL

fun tierForLevel(level: Int): LevelTier =
    LEVEL_TIERS.firstOrNull { level >= it.minLevel && (it.maxLevel == null || level <= it.maxLevel) }
        ?: LEVEL_TIERS.last()
