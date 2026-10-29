/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

// Tinggi nav bar yang BENAR-BENAR terlihat (pil). Tinggi ini dipakai induk
// untuk menghitung posisi mini player, jadi nilainya harus sama dengan yang
// digambar di AppNavigation.kt.
val NavigationBarHeight = 58.dp
val SlimNavBarHeight = 50.dp

// Jarak pil nav bar dari dasar area navigasi (di atas tombol sistem HP).
// Ditambahkan di luar NavigationBarHeight sehingga tidak memotong pil.
val FloatingNavBarGap = 12.dp

// Total ruang vertikal yang dipakai nav bar: tinggi pil + jarak mengapung.
// Induk WAJIB memakai nilai ini, bukan NavigationBarHeight mentah, kalau
// tidak pil akan terpotong.
val NavigationBarTotalHeight = NavigationBarHeight + FloatingNavBarGap
val SlimNavBarTotalHeight = SlimNavBarHeight + FloatingNavBarGap
val MiniPlayerHeight = 64.dp
val MinMiniPlayerHeight = 16.dp
val MiniPlayerBottomSpacing = 10.dp // Space between MiniPlayer and NavigationBar
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SuggestionItemHeight = 56.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 48.dp
val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 128.dp
val AlbumThumbnailSize = 144.dp

val ThumbnailCornerRadius = 3.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

val BottomSheetAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

val BottomSheetSoftAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
