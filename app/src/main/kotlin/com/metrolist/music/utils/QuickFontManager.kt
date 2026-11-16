/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Port dari Metrolist v14 (androidApp/.../utils/QuickFontManager.kt):
 * 5 font siap unduh dari Google Fonts (regular + bold), disimpan di
 * filesDir/fonts/<id>/. Unduhan lewat HttpURLConnection (tanpa Play
 * Services, jadi jalan di flavor foss) dengan alur staging/backup
 * yang sama seperti v14: file dicek dulu (Typeface.createFromFile)
 * baru diaktifkan, dan instalasi yang terpotong dipulihkan.
 */

package com.metrolist.music.utils

import android.content.Context
import android.graphics.Typeface
import android.system.Os
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object QuickFontManager {
    const val GOOGLE_SANS_ID = "google_sans"
    const val LATO_ID = "lato"
    const val ROBOTO_ID = "roboto"
    const val COMIC_NEUE_ID = "comic_neue"
    const val ATKINSON_HYPERLEGIBLE_NEXT_ID = "atkinson_hyperlegible_next"
    const val SYSTEM_ID = "SYSTEM"
    const val CUSTOM_ID = "CUSTOM"

    private const val FONT_DIRECTORY = "fonts"
    private const val MAX_FONT_BYTES = 20L * 1024L * 1024L

    data class QuickFont(
        val id: String,
        val label: String,
        val regularUrl: String,
        val boldUrl: String,
        val directoryName: String = id,
        val regularFileName: String = "$id-Regular.ttf",
        val boldFileName: String = "$id-Bold.ttf",
    )

    val fonts: List<QuickFont> =
        listOf(
            QuickFont(
                GOOGLE_SANS_ID,
                "Google Sans",
                regularUrl =
                    "https://fonts.gstatic.com/s/googlesans/v70/4Ua_rENHsxJlGDuGo1OIlJfC6l_24rlCK1Yo_Iqcsih3SAyH6cAwhX9RFD48TE63OOYKtrwEIKlirS7iEw.ttf",
                boldUrl =
                    "https://fonts.gstatic.com/s/googlesans/v70/4Ua_rENHsxJlGDuGo1OIlJfC6l_24rlCK1Yo_Iqcsih3SAyH6cAwhX9RFD48TE63OOYKtrzjJ6lirS7iEw.ttf",
                regularFileName = "GoogleSans-Regular.ttf",
                boldFileName = "GoogleSans-Bold.ttf",
            ),
            QuickFont(
                LATO_ID,
                "Lato",
                regularUrl = "https://fonts.gstatic.com/s/lato/v25/S6uyw4BMUTPHvxk6WQev.ttf",
                boldUrl = "https://fonts.gstatic.com/s/lato/v25/S6u9w4BMUTPHh6UVew-FHi_o.ttf",
                regularFileName = "Lato-Regular.ttf",
                boldFileName = "Lato-Bold.ttf",
            ),
            QuickFont(
                ROBOTO_ID,
                "Roboto",
                regularUrl = "https://fonts.gstatic.com/s/roboto/v51/KFOMCnqEu92Fr1ME7kSn66aGLdTylUAMQXC89YmC2DPNWubEbWmTgg3Wlg.ttf",
                boldUrl = "https://fonts.gstatic.com/s/roboto/v51/KFOMCnqEu92Fr1ME7kSn66aGLdTylUAMQXC89YmC2DPNWuYjammTgg3Wlg.ttf",
                regularFileName = "Roboto-Regular.ttf",
                boldFileName = "Roboto-Bold.ttf",
            ),
            QuickFont(
                COMIC_NEUE_ID,
                "Comic Neue",
                regularUrl = "https://fonts.gstatic.com/s/comicneue/v9/4UaHrEJDsxBrF37olUeDx63j4JN0.ttf",
                boldUrl = "https://fonts.gstatic.com/s/comicneue/v9/4UaErEJDsxBrF37olUeD_xHMwpteKQtG.ttf",
                regularFileName = "ComicNeue-Regular.ttf",
                boldFileName = "ComicNeue-Bold.ttf",
            ),
            QuickFont(
                ATKINSON_HYPERLEGIBLE_NEXT_ID,
                "Atkinson Hyperlegible Next",
                regularUrl =
                    "https://fonts.gstatic.com/s/atkinsonhyperlegiblenext/v7/NaP4cYPdHfdVxJw0IfIP0lvYFqijb-UxCtm5_wdGscKFt4tOOfV4ZmW37LQhtNl73T0.ttf",
                boldUrl =
                    "https://fonts.gstatic.com/s/atkinsonhyperlegiblenext/v7/NaP4cYPdHfdVxJw0IfIP0lvYFqijb-UxCtm5_wdGscKFt4tOOfV4ZmW3C7MhtNl73T0.ttf",
                regularFileName = "AtkinsonHyperlegibleNext-Regular.ttf",
                boldFileName = "AtkinsonHyperlegibleNext-Bold.ttf",
            ),
        )

    private val fontsById = fonts.associateBy(QuickFont::id)

    fun find(id: String): QuickFont? = fontsById[id]

    fun isSupportedId(id: String): Boolean = id in fontsById

    fun isDownloaded(
        context: Context,
        id: String,
    ): Boolean {
        val font = find(id) ?: return false
        val directory = fontDirectory(context, font)
        val regular = File(directory, font.regularFileName)
        val bold = File(directory, font.boldFileName)
        if (!regular.isFile || !bold.isFile) return false
        return runCatching {
            Typeface.createFromFile(regular)
            Typeface.createFromFile(bold)
            true
        }.getOrDefault(false)
    }

    fun loadFontFamily(
        context: Context,
        id: String,
    ): FontFamily {
        val font = find(id) ?: return FontFamily.Default
        if (!isDownloaded(context, id)) return FontFamily.Default
        val directory = fontDirectory(context, font)
        val regular = File(directory, font.regularFileName)
        val bold = File(directory, font.boldFileName)

        return runCatching {
            FontFamily(
                Font(regular, FontWeight.Normal, FontStyle.Normal),
                Font(bold, FontWeight.Bold, FontStyle.Normal),
            )
        }.getOrDefault(FontFamily.Default)
    }

    suspend fun downloadedFontIds(context: Context): Set<String> =
        withContext(Dispatchers.IO) {
            fonts.mapNotNullTo(mutableSetOf()) { font ->
                recoverInterruptedInstall(context, font)
                font.id.takeIf { isDownloaded(context, it) }
            }
        }

    suspend fun downloadFonts(
        context: Context,
        id: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val font = find(id) ?: return@withContext Result.failure(IllegalArgumentException("Unknown quick font"))
            recoverInterruptedInstall(context, font)
            val directory = fontDirectory(context, font)
            val staging =
                stagingDirectory(context, font).apply {
                    deleteRecursively()
                    mkdirs()
                }
            val backup = backupDirectory(context, font)
            val temporaryRegular = File(staging, font.regularFileName)
            val temporaryBold = File(staging, font.boldFileName)

            runCatching {
                downloadToFile(font.regularUrl, temporaryRegular)
                downloadToFile(font.boldUrl, temporaryBold)
                Typeface.createFromFile(temporaryRegular)
                Typeface.createFromFile(temporaryBold)
                activate(directory, staging, backup)
            }.also {
                staging.deleteRecursively()
                if (directory.exists()) backup.deleteRecursively()
            }
        }

    private fun fontDirectory(
        context: Context,
        font: QuickFont,
    ): File = File(context.filesDir, "$FONT_DIRECTORY/${font.directoryName}")

    private fun stagingDirectory(
        context: Context,
        font: QuickFont,
    ): File = File(context.filesDir, "$FONT_DIRECTORY/.${font.directoryName}.installing")

    private fun backupDirectory(
        context: Context,
        font: QuickFont,
    ): File = File(context.filesDir, "$FONT_DIRECTORY/.${font.directoryName}.previous")

    private fun downloadToFile(
        url: String,
        destination: File,
    ) {
        val trustedUrl =
            runCatching { URL(url) }.getOrNull()
                ?.takeIf { it.protocol == "https" && it.host == "fonts.gstatic.com" }
                ?: error("Google Fonts returned an untrusted font URL")
        val connection =
            (trustedUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                requestMethod = "GET"
                connect()
            }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Font download returned ${connection.responseCode}"
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_FONT_BYTES) error("Font is larger than 20 MB")
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_FONT_BYTES) { "Font is larger than 20 MB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun replaceAtomically(
        source: File,
        destination: File,
    ) {
        Os.rename(source.absolutePath, destination.absolutePath)
    }

    private fun activate(
        directory: File,
        staging: File,
        backup: File,
    ) {
        backup.deleteRecursively()
        var movedPrevious = false
        try {
            if (directory.exists()) {
                replaceAtomically(directory, backup)
                movedPrevious = true
            }
            replaceAtomically(staging, directory)
            backup.deleteRecursively()
        } catch (failure: Throwable) {
            if (!directory.exists() && movedPrevious && backup.exists()) {
                replaceAtomically(backup, directory)
            }
            throw failure
        }
    }

    private fun recoverInterruptedInstall(
        context: Context,
        font: QuickFont,
    ) {
        val directory = fontDirectory(context, font)
        val backup = backupDirectory(context, font)
        if (!directory.exists() && backup.exists()) {
            runCatching { replaceAtomically(backup, directory) }
        }
        if (directory.exists()) backup.deleteRecursively()
    }
}
