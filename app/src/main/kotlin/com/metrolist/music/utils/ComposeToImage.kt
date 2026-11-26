/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.ui.component.LyricsBackgroundStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

object ComposeToImage {
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.BLUR,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        lyricsAlignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
        // Tinggi output dalam px; lebar = tinggi x aspectRatio. Export penuh
        // pakai 2160 (1:1 -> 2160x2160, 4:3 -> 2880x2160); preview di dialog
        // pakai nilai kecil (mis. 720) supaya rendernya cepat — semua ukuran
        // di dalam fungsi ini diturunkan dari imageWidth/imageHeight, jadi
        // proporsinya sama persis di ukuran berapa pun.
        outputHeight: Int = 2160,
        aspectRatio: Float = 1f,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            // Resolusi tetap memastikan kualitas konsisten apa pun ukuran layar HP.
            val imageHeight = outputHeight
            val imageWidth = (outputHeight * aspectRatio).roundToInt()

            val bitmap = createBitmap(imageWidth, imageHeight)
            val canvas = Canvas(bitmap)
            val backgroundRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())

            // Base scale on width relative to the reference design (340dp)
            // (mis. 2160 / 340 ≈ 6.35 untuk export penuh)
            val scale = imageWidth / 340f
            val cornerRadius = 20f * scale
            val exportPath =
                Path().apply {
                    addRoundRect(backgroundRect, cornerRadius, cornerRadius, Path.Direction.CW)
                }
            canvas.clipPath(exportPath)

            val defaultBackgroundColor = 0xFF121212.toInt()
            val defaultTextColor = 0xFFFFFFFF.toInt()
            val defaultSecondaryTextColor = 0xB3FFFFFF.toInt()

            val bgColor = backgroundColor ?: defaultBackgroundColor
            val mainTextColor = textColor ?: defaultTextColor
            val secondaryTxtColor = secondaryTextColor ?: defaultSecondaryTextColor

            // Pre-load cover art if needed for Blur/Gradient or just for the header
            var coverArtBitmap: Bitmap? = null
            if (coverArtUrl != null) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverArtUrl)
                            .size(1440)
                            .allowHardware(false)
                            .build()
                    val result = imageLoader.execute(request)
                    coverArtBitmap = result.image?.toBitmap()
                } catch (_: Exception) {
                }
            }

            // Draw Background
            val backgroundPaint =
                Paint().apply {
                    isAntiAlias = true
                }

            when (backgroundStyle) {
                LyricsBackgroundStyle.SOLID -> {
                    backgroundPaint.color = bgColor
                    canvas.drawRect(backgroundRect, backgroundPaint)
                }

                LyricsBackgroundStyle.BLUR -> {
                    // Kanvas imageWidth x imageHeight. Sampul harus di-blur pada resolusi yang
                    // setara, bukan pada bitmap kecil.
                    //
                    // Versi lama memperkecil sampul ke imageWidth/10 (216px)
                    // lalu mem-blur radius 20 di ukuran itu, kemudian
                    // meregangkannya kembali ke 2160. Blur 20px pada gambar
                    // 216px menghapus hampir seluruh detail, sehingga hasilnya
                    // tampak seperti satu warna pekat - bug "Buram tampil
                    // Pekat".
                    val blurRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
                    var drewBlur = false

                    if (coverArtBitmap != null) {
                        try {
                            // Blur pada resolusi sedang (1/3 kanvas = 720px):
                            // cukup detail untuk terlihat seperti sampul, dan
                            // cukup murah untuk diproses.
                            val targetW = (imageWidth / 3).coerceAtLeast(64)
                            val targetH = (imageHeight / 3).coerceAtLeast(64)
                            val scaledBitmap =
                                Bitmap.createScaledBitmap(coverArtBitmap, targetW, targetH, true)
                            // Blur dinaikkan supaya yang tersisa gradasi warna
                            // sampul, bukan bentuk gambar yang masih kebaca.
                            val radius = (targetW / 15).coerceIn(8, 60)
                            val blurredBitmap = fastBlur(scaledBitmap, 1f, radius)

                            if (blurredBitmap != null) {
                                canvas.drawBitmap(blurredBitmap, null, blurRect, null)
                                drewBlur = true
                            }
                        } catch (_: Exception) {
                            drewBlur = false
                        }
                    }

                    if (drewBlur) {
                        // Lapisan gelap tipis agar teks tetap terbaca.
                        val overlayPaint = Paint().apply { color = 0x33000000 }
                        canvas.drawRect(blurRect, overlayPaint)
                    } else {
                        // Sampul benar-benar tidak tersedia: pakai warna latar
                        // pilihan user, bukan hitam.
                        backgroundPaint.color = bgColor
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    }
                }

                LyricsBackgroundStyle.GRADIENT -> {
                    if (coverArtBitmap != null) {
                        val palette = Palette.from(coverArtBitmap).generate()
                        val vibrant = palette.getVibrantColor(bgColor)
                        val darkVibrant = palette.getDarkVibrantColor(bgColor)

                        val gradient =
                            LinearGradient(
                                0f,
                                0f,
                                imageWidth.toFloat(),
                                imageHeight.toFloat(),
                                intArrayOf(vibrant, darkVibrant),
                                null,
                                Shader.TileMode.CLAMP,
                            )
                        backgroundPaint.shader = gradient
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    } else {
                        backgroundPaint.color = bgColor
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    }
                }
            }

            // Draw inner border
            val borderPaint =
                Paint().apply {
                    color = mainTextColor
                    alpha = (255 * 0.09).toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1f * scale
                    isAntiAlias = true
                }
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, borderPaint)

            val padding = 28f * scale

            // --- Header Section ---
            val coverArtSize = 64f * scale
            val headerBottomPadding = 12f * scale

            val coverCornerRadius = 3f * scale
            coverArtBitmap?.let {
                val rect = RectF(padding, padding, padding + coverArtSize, padding + coverArtSize)
                val path =
                    Path().apply {
                        addRoundRect(rect, coverCornerRadius, coverCornerRadius, Path.Direction.CW)
                    }

                // Draw border for cover art
                val coverBorderPaint =
                    Paint().apply {
                        color = mainTextColor
                        alpha = (255 * 0.16).toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1f * scale
                        isAntiAlias = true
                    }

                canvas.withClip(path) {
                    drawBitmap(it, null, rect, null)
                }
                canvas.drawRoundRect(rect, coverCornerRadius, coverCornerRadius, coverBorderPaint)
            }

            val textStartX = padding + coverArtSize + (16f * scale)
            val textMaxWidth = imageWidth - textStartX - padding

            val titlePaint =
                TextPaint().apply {
                    color = mainTextColor
                    textSize = 20f * scale
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }

            val artistPaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = 16f * scale
                    typeface = Typeface.DEFAULT
                    isAntiAlias = true
                }

            val titleLayout =
                StaticLayout.Builder
                    .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    // Judul boleh 2 baris (sama seperti preview dulu): judul panjang
                    // tidak lagi kepotong "..." di satu baris. Ellipsis hanya kalau
                    // 2 baris pun tetap tidak muat.
                    .setMaxLines(2)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()

            val artistLayout =
                StaticLayout.Builder
                    .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(1)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()

            // Vertically align text block with cover art
            val headerTextHeight = titleLayout.height + artistLayout.height + (2f * scale) // +2dp padding between title and artist
            val headerCenterY = padding + coverArtSize / 2f
            val titleY = headerCenterY - headerTextHeight / 2f

            canvas.save()
            canvas.translate(textStartX, titleY)
            titleLayout.draw(canvas)
            canvas.translate(0f, titleLayout.height.toFloat() + (2f * scale))
            artistLayout.draw(canvas)
            canvas.restore()

            // --- Footer Section ---
            // Logo mengisi seluruh kotaknya. Sebelumnya kotak 22f*scale tetapi
            // ikon hanya 13f*scale sehingga logo tampak kecil dan terkurung
            // ruang kosong. PNG-nya sudah punya padding transparan sendiri,
            // jadi tidak perlu dikurangi lagi.
            val logoBoxSize = 24f * scale
            // Footer diturunkan 4dp, sama seperti preview Compose.
            val footerY = imageHeight - padding - logoBoxSize + (4f * scale)

            // Draw Logo Icon
            val rawLogo = ContextCompat.getDrawable(context, R.drawable.meowsic_logo)?.toBitmap()
            rawLogo?.let {
                // Tanpa tint: logo digambar sesuai warnanya sendiri.
                val logoPaint = Paint().apply { isAntiAlias = true }
                val logoRect =
                    RectF(
                        padding,
                        footerY,
                        padding + logoBoxSize,
                        footerY + logoBoxSize,
                    )
                canvas.drawBitmap(it, null, logoRect, logoPaint)
            }

            // Draw App Name
            val appName = context.getString(R.string.app_name)
            val appNamePaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = 11f * scale
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }

            val appNameX = padding + logoBoxSize + (4f * scale)
            // Center text vertically relative to logo box, lalu digeser 2dp ke
            // bawah supaya sejajar pusat visual kucing (PNG punya padding).
            val appNameY =
                footerY + logoBoxSize / 2f - (appNamePaint.descent() + appNamePaint.ascent()) / 2f + (2f * scale)
            canvas.drawText(appName, appNameX, appNameY, appNamePaint)

            // --- Lyrics Section ---
            // Calculate available space
            val lyricsTop = padding + coverArtSize + headerBottomPadding
            val lyricsBottom = footerY - (12f * scale) // Add some padding above footer
            val lyricsHeight = lyricsBottom - lyricsTop
            val lyricsWidth = imageWidth - (padding * 2)

            val lyricsPaint =
                TextPaint().apply {
                    color = mainTextColor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    letterSpacing = 0.005f
                }

            // Adaptive font size calculation
            // Start with a large size (e.g. 50sp equivalent) and scale down until it fits
            var lyricsTextSize = 50f * scale
            val minLyricsSize = 13f * scale
            var lyricsLayout: StaticLayout
            var balancedLyrics = lyrics

            while (lyricsTextSize > minLyricsSize) {
                lyricsPaint.textSize = lyricsTextSize
                // Seimbangkan tiap baris sumber pada ukuran font ini supaya
                // tidak ada kata yatim di baris terakhir.
                balancedLyrics = lyrics.split("\n").joinToString("\n") { balanceLyricsLine(it, lyricsPaint, lyricsWidth.toInt()) }
                lyricsLayout =
                    StaticLayout.Builder
                        .obtain(balancedLyrics, 0, balancedLyrics.length, lyricsPaint, lyricsWidth.toInt())
                        .setAlignment(lyricsAlignment)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()

                if (lyricsLayout.height <= lyricsHeight) {
                    break
                }

                lyricsTextSize -= 1f * scale // Decrease by ~1sp equivalent steps
            }

            // One final rebuild with the determined size
            lyricsPaint.textSize = lyricsTextSize
            lyricsLayout =
                StaticLayout.Builder
                    .obtain(balancedLyrics, 0, balancedLyrics.length, lyricsPaint, lyricsWidth.toInt())
                    .setAlignment(lyricsAlignment)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(false)
                    .build()

            // Center vertically in the available space
            val lyricsContentHeight = lyricsLayout.height
            val lyricsY =
                if (lyricsContentHeight < lyricsHeight) {
                    lyricsTop + (lyricsHeight - lyricsContentHeight) / 2f
                } else {
                    lyricsTop
                }

            canvas.withTranslation(padding, lyricsY) {
                lyricsLayout.draw(this)
            }

            return@withContext bitmap
        }

    // Bagi satu baris lirik jadi beberapa baris dengan panjang yang seimbang
    // (ala text-wrap: balance): cari jumlah baris minimal yang muat, lalu bagi
    // kata supaya baris terpanjang seminimal mungkin. Tanpa ini, wrap serakah
    // StaticLayout meninggalkan 1-2 kata yatim di baris terakhir ("cat").
    private fun balanceLyricsLine(line: String, paint: TextPaint, maxWidth: Int): String {
        val words = line.split(" ").filter { it.isNotEmpty() }
        if (words.size <= 1) return line
        if (paint.measureText(line) <= maxWidth) return line
        val n = words.size
        val inf = Float.MAX_VALUE
        for (lineCount in 2..n) {
            val cost = Array(lineCount + 1) { FloatArray(n + 1) { inf } }
            val cut = Array(lineCount + 1) { IntArray(n + 1) }
            cost[0][0] = 0f
            for (k in 1..lineCount) {
                for (j in 1..n) {
                    for (i in k - 1 until j) {
                        val seg = words.subList(i, j).joinToString(" ")
                        val w = paint.measureText(seg)
                        if (w > maxWidth) continue
                        val c = maxOf(cost[k - 1][i], w)
                        if (c < cost[k][j]) {
                            cost[k][j] = c
                            cut[k][j] = i
                        }
                    }
                }
            }
            if (cost[lineCount][n] < inf) {
                val out = ArrayList<String>(lineCount)
                var j = n
                for (k in lineCount downTo 1) {
                    val i = cut[k][j]
                    out.add(words.subList(i, j).joinToString(" "))
                    j = i
                }
                return out.reversed().joinToString("\n")
            }
        }
        return line
    }

    // Stack Blur v1.0 from http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
    // Java Author: Mario Klingemann <mario at quasimondo.com>
    // http://incubator.quasimondo.com
    //
    // created Feburary 29, 2004
    // Android port : Yahel Bouaziz <yahel at kayenko.com>
    // http://www.kayenko.com
    // ported to Kotlin and adapted
    private fun fastBlur(
        sentBitmap: Bitmap,
        scale: Float,
        radius: Int,
    ): Bitmap? {
        val width = (sentBitmap.width * scale).roundToInt()
        val height = (sentBitmap.height * scale).roundToInt()

        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }
        yw = 0
        yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        var r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = Math.max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun saveBitmapAsFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    // Nama folder galeri mengikuti nama aplikasi (Meowsic),
                    // bukan nama proyek upstream (Metrolist).
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + context.getString(R.string.app_name),
                    )
                }
            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: throw IllegalStateException("Failed to create new MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                imageFile,
            )
        }
}
