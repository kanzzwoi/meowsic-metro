/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.level

import com.metrolist.music.R

/**
 * Foto profil rahasia Listen Together. Lima foto kucing meme kiriman user,
 * unlock lewat easter egg (ketuk "TERIMAKASIH SUDAH BERKUNJUNG" di
 * Pengaturan > Tentang sebanyak 10x). Aset sudah dikompres ke WebP 512px
 * (total ~100 KB) di res/drawable-nodpi sebagai pfp_<n>_<nama>.webp.
 *
 * Foto profil disimpan/dikirim sebagai String id-nya ("1".."5"), "" = tanpa
 * foto profil. Kalau foto profil aktif, warna dan emoji tidak berlaku.
 */

/** Foto profil rahasia: id, drawable, label pendek untuk picker. */
val SECRET_PFPS =
    listOf(
        Triple("1", R.drawable.pfp_1_orange, "Oren"),
        Triple("2", R.drawable.pfp_2_kitten_dance, "Joget"),
        Triple("3", R.drawable.pfp_3_propeller_hat, "Topi"),
        Triple("4", R.drawable.pfp_4_watermelon, "Semangka"),
        Triple("5", R.drawable.pfp_5_fist_kitten, "Tinju"),
    )

/** Drawable foto profil untuk id tertentu, atau null kalau bukan foto yang dikenal. */
fun pfpDrawableRes(pfpId: String): Int? =
    SECRET_PFPS.firstOrNull { it.first == pfpId }?.second
