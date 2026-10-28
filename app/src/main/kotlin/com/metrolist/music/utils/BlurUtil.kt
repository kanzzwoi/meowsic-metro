/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.graphics.Bitmap

/**
 * Gaussian-like blur murni Kotlin (3x box blur) yang berjalan di CPU.
 *
 * Dipakai untuk latar buram mini-player/full player karena Modifier.blur()
 * (RenderEffect GPU) tidak andal saat kontennya AsyncImage/Coil: blur bisa
 * diabaikan total sehingga latar jatuh ke warna polos. Blur di ruang bitmap
 * bersifat deterministik di semua perangkat dan semua versi Android.
 *
 * Sumber idealnya bitmap KECIL (mis. 100x100 dari sampul album); hasilnya
 * nanti di-upscale oleh Compose (ContentScale.Crop), jadi kualitas tetap
 * lembut tanpa biaya komputasi berarti.
 */
fun Bitmap.stackBlur(radius: Int): Bitmap {
    if (radius < 1) return this
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, 0, 0, w, h)

    val div = 2 * radius + 1
    val out = IntArray(w * h)

    repeat(3) {
        blurPass(pixels, out, w, h, radius, div, horizontal = true)
        blurPass(out, pixels, w, h, radius, div, horizontal = false)
    }

    val result = config?.let { copy(it, false) }
        ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

private fun blurPass(
    src: IntArray,
    dst: IntArray,
    w: Int,
    h: Int,
    radius: Int,
    div: Int,
    horizontal: Boolean,
) {
    val outer = if (horizontal) h else w
    val inner = if (horizontal) w else h
    val step = if (horizontal) 1 else w

    for (o in 0 until outer) {
        val base = o * (if (horizontal) w else 1)
        var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0

        // Jendela awal: -radius..radius, di-clamp di tepi.
        for (i in -radius..radius) {
            val idx = base + (i.coerceIn(0, inner - 1)) * step
            val p = src[idx]
            aSum += p ushr 24 and 0xFF
            rSum += p ushr 16 and 0xFF
            gSum += p ushr 8 and 0xFF
            bSum += p and 0xFF
        }

        for (i in 0 until inner) {
            val dstIdx = base + i * step
            val count = minOf(i + radius, inner - 1) - maxOf(i - radius, 0) + 1
            val p = src[dstIdx]
            dst[dstIdx] = ((aSum / count) shl 24) or
                ((rSum / count) shl 16) or
                ((gSum / count) shl 8) or
                (bSum / count)

            // Geser jendela: keluarkan piksel kiri, masukkan piksel kanan.
            val outIdx = base + (i - radius).coerceAtLeast(0) * step
            val inIdx = base + (i + radius + 1).coerceAtMost(inner - 1) * step
            val po = src[outIdx]
            val pi = src[inIdx]
            aSum += (pi ushr 24 and 0xFF) - (po ushr 24 and 0xFF)
            rSum += (pi ushr 16 and 0xFF) - (po ushr 16 and 0xFF)
            gSum += (pi ushr 8 and 0xFF) - (po ushr 8 and 0xFF)
            bSum += (pi and 0xFF) - (po and 0xFF)
        }
        @Suppress("UNUSED_EXPRESSION") div
    }
}
