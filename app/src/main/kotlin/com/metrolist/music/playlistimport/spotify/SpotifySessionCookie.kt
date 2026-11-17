/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

/**
 * Ambil nilai cookie `sp_dc` dari header Cookie, port verbatim dari v14
 * (shared/.../spotifyimport/SpotifySessionCookie.kt). Hanya nilai sp_dc
 * yang diambil — header lengkapnya tidak pernah disimpan atau di-log.
 */
fun spotifySessionCookie(cookieHeader: String?): String? =
    cookieHeader
        ?.split(';')
        ?.firstNotNullOfOrNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0 || part.substring(0, separator).trim() != "sp_dc") {
                null
            } else {
                part.substring(separator + 1).trim().takeIf(String::isNotEmpty)
            }
        }
