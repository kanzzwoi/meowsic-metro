/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.graphics.Bitmap

/**
 * Blur kotak berulang (3 pass) untuk latar mini-player dan full player.
 *
 * Menggantikan Modifier.blur()/RenderEffect yang tidak andal untuk konten
 * AsyncImage/Coil. Dikerjakan di ruang bitmap sehingga hasilnya sama di semua
 * perangkat dan versi Android.
 *
 * Akumulator jendela geser hanya boleh memakai piksel dalam rentang citra;
 * nilai di luar tepi diganti piksel tepi terdekat dan pembaginya selalu
 * diameter penuh (2*radius+1). Versi sebelumnya mencampur jumlah pembagi
 * yang berbeda dengan akumulator penuh, sehingga nilai meluber keluar
 * rentang 0..255 dan gambarnya rusak.
 *
 * Sumber sebaiknya bitmap kecil (mis. 100x100); Compose meng-upscale-nya
 * lewat ContentScale.Crop sehingga hasilnya tetap terlihat lembut.
 */
fun Bitmap.stackBlur(radius: Int): Bitmap {
    if (radius < 1 || width < 1 || height < 1) return this

    val w = width
    val h = height
    val src = IntArray(w * h)
    getPixels(src, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)

    // 3 pass bolak-balik memberi hasil mendekati Gaussian.
    repeat(3) {
        boxBlurHorizontal(src, tmp, w, h, radius)
        boxBlurVertical(tmp, src, w, h, radius)
    }

    val out =
        if (config != null && config != Bitmap.Config.HARDWARE) {
            Bitmap.createBitmap(w, h, config!!)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    out.setPixels(src, 0, w, 0, 0, w, h)
    return out
}

/** Rata-rata arah horizontal: src -> dst. */
private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val diameter = radius * 2 + 1
    for (y in 0 until h) {
        val row = y * w
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0

        for (i in -radius..radius) {
            val p = src[row + i.coerceIn(0, w - 1)]
            sumA += (p ushr 24) and 0xFF
            sumR += (p ushr 16) and 0xFF
            sumG += (p ushr 8) and 0xFF
            sumB += p and 0xFF
        }

        for (x in 0 until w) {
            dst[row + x] =
                ((sumA / diameter) shl 24) or
                ((sumR / diameter) shl 16) or
                ((sumG / diameter) shl 8) or
                (sumB / diameter)

            val po = src[row + (x - radius).coerceIn(0, w - 1)]
            val pi = src[row + (x + radius + 1).coerceIn(0, w - 1)]
            sumA += ((pi ushr 24) and 0xFF) - ((po ushr 24) and 0xFF)
            sumR += ((pi ushr 16) and 0xFF) - ((po ushr 16) and 0xFF)
            sumG += ((pi ushr 8) and 0xFF) - ((po ushr 8) and 0xFF)
            sumB += (pi and 0xFF) - (po and 0xFF)
        }
    }
}

/** Rata-rata arah vertikal: src -> dst. */
private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val diameter = radius * 2 + 1
    for (x in 0 until w) {
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0

        for (i in -radius..radius) {
            val p = src[i.coerceIn(0, h - 1) * w + x]
            sumA += (p ushr 24) and 0xFF
            sumR += (p ushr 16) and 0xFF
            sumG += (p ushr 8) and 0xFF
            sumB += p and 0xFF
        }

        for (y in 0 until h) {
            dst[y * w + x] =
                ((sumA / diameter) shl 24) or
                ((sumR / diameter) shl 16) or
                ((sumG / diameter) shl 8) or
                (sumB / diameter)

            val po = src[(y - radius).coerceIn(0, h - 1) * w + x]
            val pi = src[(y + radius + 1).coerceIn(0, h - 1) * w + x]
            sumA += ((pi ushr 24) and 0xFF) - ((po ushr 24) and 0xFF)
            sumR += ((pi ushr 16) and 0xFF) - ((po ushr 16) and 0xFF)
            sumG += ((pi ushr 8) and 0xFF) - ((po ushr 8) and 0xFF)
            sumB += (pi and 0xFF) - (po and 0xFF)
        }
    }
}
