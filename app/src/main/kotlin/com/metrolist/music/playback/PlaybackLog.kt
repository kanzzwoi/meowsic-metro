/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log ringan ke file untuk mendiagnosa playback yang berhenti sendiri.
 * Ditulis ke cacheDir/playback.log (bisa dibagikan lewat Settings).
 */
object PlaybackLog {
    private const val FILE_NAME = "playback.log"
    private const val MAX_BYTES = 256 * 1024

    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var appContext: Context? = null

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun file(context: Context): File = File(context.cacheDir, FILE_NAME)

    fun log(tag: String, message: String) {
        val context = appContext ?: return
        val line = "${formatter.format(Date())} [$tag] $message\n"
        runCatching { executor.execute { write(context, line) } }
    }

    private fun write(
        context: Context,
        line: String,
    ) {
        synchronized(this) {
            runCatching {
                val f = file(context)
                if (f.exists() && f.length() > MAX_BYTES) {
                    val old = File(context.cacheDir, "$FILE_NAME.old")
                    if (old.exists()) old.delete()
                    f.renameTo(old)
                }
                f.appendText(line)
            }
        }
    }
}
