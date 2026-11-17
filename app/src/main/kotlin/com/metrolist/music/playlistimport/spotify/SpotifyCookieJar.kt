/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import java.net.CookieManager
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI

/**
 * Penyimpanan cookie untuk klien Spotify, port dari spotlib v14
 * (shared/.../meow/nyx/spotlib/CookieJar.kt). Cookie dikelola manual
 * (bukan CookieJar OkHttp) supaya sama persis dengan perilaku v14:
 * cookie hanya dikirim ke host Spotify, kecuali host yang dikecualikan
 * (api-partner + clienttoken tidak menerima cookie).
 *
 * Keamanan: jangan pernah me-log isi cookie. Nilai cookie hanya dibaca
 * lewat [cookieValue] untuk keperluan internal (mis. device id sp_t).
 */
class SpotifyCookieJar {
    private val store: CookieStore = CookieManager().cookieStore
    private val cookieExemptHosts = setOf("api-partner.spotify.com", "clienttoken.spotify.com")

    /** Header Cookie untuk request ke host ini, atau null kalau tidak ada / dikecualikan. */
    fun requestCookies(host: String): String? {
        if (host in cookieExemptHosts) return null
        return store
            .get(uri(host))
            .takeIf(List<HttpCookie>::isNotEmpty)
            ?.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun cookieValue(
        host: String,
        name: String,
    ): String? = store.get(uri(host)).firstOrNull { it.name == name }?.value

    /** Tangkap cookie dari header Set-Cookie sebuah respons. */
    fun capture(
        host: String,
        setCookieHeaders: List<String>,
    ) {
        for (raw in setCookieHeaders) {
            for (cookie in HttpCookie.parse(raw)) {
                if (cookie.path.isNullOrEmpty()) cookie.path = "/"
                if (cookie.domain.isNullOrBlank()) cookie.domain = host
                try {
                    store.add(uri(host), cookie)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    fun store(
        host: String,
        name: String,
        value: String,
    ) {
        val cookie = HttpCookie(name, value)
        cookie.path = "/"
        cookie.domain = host
        store.add(uri(host), cookie)
    }

    fun all(): List<HttpCookie> = store.cookies

    fun clear() = store.removeAll()

    private fun uri(host: String): URI = URI("https://$host")
}
