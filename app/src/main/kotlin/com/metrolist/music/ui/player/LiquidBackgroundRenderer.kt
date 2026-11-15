/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Salinan langsung dari Metrolist v14.0.0
 * (shared/src/androidMain/.../PlatformLiquidBackgroundCanvas.android.kt):
 * jalur fallback CPU untuk background animasi ala Apple Music — artwork
 * mentah digambar 3 lapis yang berputar pelan ke kanvas kecil, lalu
 * SELURUH komposit di-blur. Blur sesudah komposit inilah yang melelehkan
 * ketiga lapis jadi gradasi halus; tanpa langkah ini tepinya tetap tajam.
 */

package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt
import android.graphics.Canvas as AndroidCanvas

private const val SCRIM_COLOR_1 = 0x80000000.toInt()
private const val SCRIM_COLOR_2 = 0x0DFFFFFF

internal class FastStackBlurBuffers(
    width: Int,
    height: Int,
    radius: Int,
) {
    val wh = width * height
    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    val div = radius + radius + 1
    val divsum = ((div + 1) shr 1) * ((div + 1) shr 1)
    val temp = IntArray(256 * divsum) { it / divsum }
    val stack = IntArray(div * 3)
}

internal fun fastStackBlurInPlace(
    bitmap: Bitmap,
    radius: Int,
    pix: IntArray,
    buffers: FastStackBlurBuffers,
) {
    if (radius < 1) return
    val w = bitmap.width
    val h = bitmap.height
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val div = radius + radius + 1

    val r = buffers.r
    val g = buffers.g
    val b = buffers.b
    val temp = buffers.temp
    val stack = buffers.stack

    var yi = 0

    for (y in 0 until h) {
        val rowStart = y * w
        var rinsum = 0
        var ginsum = 0
        var binsum = 0
        var routsum = 0
        var goutsum = 0
        var boutsum = 0
        var rsum = 0
        var gsum = 0
        var bsum = 0

        for (i in -radius..radius) {
            val p = pix[rowStart + minOf(wm, maxOf(i, 0))]
            val si = (i + radius) * 3
            val pr = (p shr 16) and 0xff
            val pg = (p shr 8) and 0xff
            val pb = p and 0xff
            stack[si] = pr
            stack[si + 1] = pg
            stack[si + 2] = pb
            val rbs = radius + 1 - if (i < 0) -i else i
            rsum += pr * rbs
            gsum += pg * rbs
            bsum += pb * rbs
            if (i > 0) {
                rinsum += pr
                ginsum += pg
                binsum += pb
            } else {
                routsum += pr
                goutsum += pg
                boutsum += pb
            }
        }
        var stackpointer = radius

        for (x in 0 until w) {
            r[yi] = temp[rsum]
            g[yi] = temp[gsum]
            b[yi] = temp[bsum]

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            var stackstart = stackpointer - radius + div
            if (stackstart >= div) stackstart -= div
            val so = stackstart * 3

            routsum -= stack[so]
            goutsum -= stack[so + 1]
            boutsum -= stack[so + 2]

            val p = pix[rowStart + minOf(x + radius + 1, wm)]
            val pr = (p shr 16) and 0xff
            val pg = (p shr 8) and 0xff
            val pb = p and 0xff
            stack[so] = pr
            stack[so + 1] = pg
            stack[so + 2] = pb

            rinsum += pr
            ginsum += pg
            binsum += pb

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackpointer = if (stackpointer + 1 == div) 0 else stackpointer + 1
            val sp = stackpointer * 3

            routsum += stack[sp]
            goutsum += stack[sp + 1]
            boutsum += stack[sp + 2]

            rinsum -= stack[sp]
            ginsum -= stack[sp + 1]
            binsum -= stack[sp + 2]

            yi++
        }
    }

    for (x in 0 until w) {
        var rinsum = 0
        var ginsum = 0
        var binsum = 0
        var routsum = 0
        var goutsum = 0
        var boutsum = 0
        var rsum = 0
        var gsum = 0
        var bsum = 0

        for (i in -radius..radius) {
            val idx = x + maxOf(0, i) * w
            val si = (i + radius) * 3
            val pr = r[idx]
            val pg = g[idx]
            val pb = b[idx]
            stack[si] = pr
            stack[si + 1] = pg
            stack[si + 2] = pb
            val rbs = radius + 1 - if (i < 0) -i else i
            rsum += pr * rbs
            gsum += pg * rbs
            bsum += pb * rbs
            if (i > 0) {
                rinsum += pr
                ginsum += pg
                binsum += pb
            } else {
                routsum += pr
                goutsum += pg
                boutsum += pb
            }
        }

        yi = x
        var stackpointer = radius
        for (y in 0 until h) {
            pix[yi] = (0xFF shl 24) or (temp[rsum] shl 16) or (temp[gsum] shl 8) or temp[bsum]

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            var stackstart = stackpointer - radius + div
            if (stackstart >= div) stackstart -= div
            val so = stackstart * 3

            routsum -= stack[so]
            goutsum -= stack[so + 1]
            boutsum -= stack[so + 2]

            val p = x + minOf(y + radius + 1, hm) * w
            val pr = r[p]
            val pg = g[p]
            val pb = b[p]
            stack[so] = pr
            stack[so + 1] = pg
            stack[so + 2] = pb

            rinsum += pr
            ginsum += pg
            binsum += pb

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackpointer = if (stackpointer + 1 == div) 0 else stackpointer + 1
            val sp = stackpointer * 3

            routsum += stack[sp]
            goutsum += stack[sp + 1]
            boutsum += stack[sp + 2]

            rinsum -= stack[sp]
            ginsum -= stack[sp + 1]
            binsum -= stack[sp + 2]

            yi += w
        }
    }

    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
}

private class LiquidBlurCache(
    val width: Int,
    val height: Int,
    val blurRadius: Int,
) {
    private val bitmapA: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val bitmapB: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val imageA: ImageBitmap = bitmapA.asImageBitmap()
    private val imageB: ImageBitmap = bitmapB.asImageBitmap()
    private var useA = true

    private val canvasA: AndroidCanvas = AndroidCanvas(bitmapA)
    private val canvasB: AndroidCanvas = AndroidCanvas(bitmapB)

    private val pixels = IntArray(width * height)
    private val blurBuffers = FastStackBlurBuffers(width, height, blurRadius)

    private val workMatrix = Matrix()

    private val layerPaint =
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.DITHER_FLAG)
    private val scrimPaint =
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.DITHER_FLAG).apply {
            style = AndroidPaint.Style.FILL
        }
    private val colorMatrix = ColorMatrix()
    private var cachedSaturation: Float = -1f

    private val w = width.toFloat()
    private val h = height.toFloat()
    private val maxDim = (max(w, h) * 1.3f).roundToInt().toFloat()
    private val pivot = maxDim / 2f
    private val offsetX = -(maxDim - w) / 2f
    private val offsetY = -(maxDim - h) / 2f

    private var lastResult: ImageBitmap? = null
    private var lastActive: Bitmap? = null
    private var lastPrevious: Bitmap? = null
    private var lastAlpha: Float = -1f
    private var lastRotation: Long = Long.MIN_VALUE

    fun render(
        activeBitmap: Bitmap?,
        previousBitmap: Bitmap?,
        crossfadeAlpha: Float,
        rotationTimeMs: Long,
        saturation: Float,
    ): ImageBitmap {
        val cached = lastResult
        if (cached != null &&
            activeBitmap === lastActive &&
            previousBitmap === lastPrevious &&
            crossfadeAlpha == lastAlpha &&
            rotationTimeMs == lastRotation &&
            saturation == cachedSaturation
        ) {
            return cached
        }
        lastActive = activeBitmap
        lastPrevious = previousBitmap
        lastAlpha = crossfadeAlpha
        lastRotation = rotationTimeMs

        useA = !useA
        val targetBmp = if (useA) bitmapA else bitmapB
        val targetCanvas = if (useA) canvasA else canvasB

        targetBmp.eraseColor(0)

        if (cachedSaturation != saturation) {
            cachedSaturation = saturation
            colorMatrix.setSaturation(saturation)
            layerPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        val curRotM = -360f * (rotationTimeMs % 130_000L) / 130_000f
        val curRotN = 360f * (rotationTimeMs % 90_000L) / 90_000f
        val curRotO = 360f * (rotationTimeMs % 70_000L) / 70_000f

        if (previousBitmap != null && crossfadeAlpha < 1f) {
            drawLayers(targetCanvas, previousBitmap, 1f - crossfadeAlpha, curRotM, curRotN, curRotO)
        }
        drawLayers(targetCanvas, activeBitmap, crossfadeAlpha, curRotM, curRotN, curRotO)

        scrimPaint.color = SCRIM_COLOR_1
        targetCanvas.drawPaint(scrimPaint)
        scrimPaint.color = SCRIM_COLOR_2
        targetCanvas.drawPaint(scrimPaint)

        fastStackBlurInPlace(targetBmp, blurRadius, pixels, blurBuffers)

        val result = if (useA) imageA else imageB
        lastResult = result
        return result
    }

    private fun drawLayers(
        targetCanvas: AndroidCanvas,
        srcBitmap: Bitmap?,
        alpha: Float,
        curRotM: Float,
        curRotN: Float,
        curRotO: Float,
    ) {
        if (alpha <= 0f || srcBitmap == null || srcBitmap.isRecycled) return
        val srcH = srcBitmap.height.toFloat()
        if (srcBitmap.width <= 0 || srcH <= 0f) return

        layerPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)

        val scale = maxDim / srcH

        workMatrix.setScale(scale, scale)
        workMatrix.postRotate(curRotM, pivot, pivot)
        workMatrix.postTranslate(offsetX, offsetY)
        targetCanvas.drawBitmap(srcBitmap, workMatrix, layerPaint)

        workMatrix.setScale(scale, scale)
        workMatrix.postRotate(curRotN, pivot, pivot)
        workMatrix.postTranslate(offsetX - 0.95f * w, offsetY - 0.7f * h)
        targetCanvas.drawBitmap(srcBitmap, workMatrix, layerPaint)

        workMatrix.setScale(scale, scale)
        workMatrix.postRotate(curRotO, pivot, pivot)
        workMatrix.postTranslate(offsetX - 0.5f * w, offsetY + 0.7f * h)
        workMatrix.postRotate(curRotO, w / 2f, h / 2f)
        targetCanvas.drawBitmap(srcBitmap, workMatrix, layerPaint)
    }
}

internal class LiquidBackgroundRenderer {
    private var cache: LiquidBlurCache? = null

    fun draw(
        scope: DrawScope,
        activeBitmap: Bitmap,
        previousBitmap: Bitmap?,
        crossfadeAlpha: Float,
        rotationTimeMs: Long,
    ) {
        val width = scope.size.width
        val height = scope.size.height
        if (width <= 0f || height <= 0f) return

        val maxTargetDim = 128f
        val aspect = width / height
        val scaledW: Int
        val scaledH: Int
        if (aspect >= 1f) {
            scaledW = maxTargetDim.roundToInt()
            scaledH = (maxTargetDim / aspect).roundToInt().coerceAtLeast(1)
        } else {
            scaledW = (maxTargetDim * aspect).roundToInt().coerceAtLeast(1)
            scaledH = maxTargetDim.roundToInt()
        }

        val blurRadius = 20
        val saturation = 2.5f

        var current = cache
        if (current == null || current.width != scaledW || current.height != scaledH || current.blurRadius != blurRadius) {
            current = LiquidBlurCache(scaledW, scaledH, blurRadius)
            cache = current
        }

        val renderedBitmap =
            current.render(
                activeBitmap = activeBitmap,
                previousBitmap = previousBitmap,
                crossfadeAlpha = crossfadeAlpha,
                rotationTimeMs = rotationTimeMs,
                saturation = saturation,
            )

        val bw = (width * 1.3f).roundToInt()
        val bh = (height * 1.3f).roundToInt()
        val tx = -(bw - width.roundToInt()) / 2
        val ty = -(bh - height.roundToInt()) / 2

        scope.drawImage(
            image = renderedBitmap,
            dstOffset = IntOffset(tx, ty),
            dstSize = IntSize(bw, bh),
            filterQuality = FilterQuality.Low,
        )
    }
}
