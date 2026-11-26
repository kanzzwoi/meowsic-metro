package com.metrolist.music.utils

/**
 * Tautan share bermerek Meowsic (App Links: www.meowsic.web.id).
 * Yang punya app: link langsung buka lagu/playlist/channel di app.
 * Yang belum punya: mendarat di landing page web buat download dulu.
 */
object MeowsicLinks {
    const val SHARE_HOST = "https://www.meowsic.web.id"

    fun songUrl(videoId: String) = "$SHARE_HOST/s/$videoId"

    fun playlistUrl(playlistId: String) = "$SHARE_HOST/p/$playlistId"

    fun channelUrl(channelId: String) = "$SHARE_HOST/c/$channelId"
}
