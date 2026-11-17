/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Pembungkus OkHttp untuk API Spotify, adaptasi dari spotlib v14
 * (shared/.../meow/nyx/spotlib/CookieJar.kt: ApiHttp). Ktor diganti OkHttp
 * (sudah dipakai Meowsic); perilaku dipertahankan: cookie dipasang manual
 * dari [SpotifyCookieJar], Set-Cookie ditangkap, respons dibatasi ukuran,
 * tidak mengikuti redirect.
 */
internal class SpotifyApiHttp(
    private val client: OkHttpClient,
    private val jar: SpotifyCookieJar,
) {
    /** Respons teks dengan batas ukuran; body null kalau respons melebihi batas. */
    data class TextResponse(
        val status: Int,
        val headers: Headers,
        val body: String?,
    )

    suspend fun get(
        url: String,
        maxBytes: Int = 256 * 1024,
        headers: Map<String, String> = emptyMap(),
    ): TextResponse =
        send(
            Request.Builder().url(url).get().applyHeaders(url, headers).build(),
            maxBytes,
        )

    suspend fun post(
        url: String,
        body: String,
        maxBytes: Int = 256 * 1024,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): TextResponse =
        send(
            Request.Builder()
                .url(url)
                .post(body.toRequestBody(contentType.toMediaType()))
                .applyHeaders(url, headers)
                .build(),
            maxBytes,
        )

    private fun Request.Builder.applyHeaders(
        url: String,
        headers: Map<String, String>,
    ): Request.Builder {
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        jar.requestCookies(host)?.let { header("Cookie", it) }
        for ((name, value) in headers) header(name, value)
        return this
    }

    private suspend fun send(
        request: Request,
        maxBytes: Int,
    ): TextResponse =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                response.headers("Set-Cookie").let { setCookie ->
                    if (setCookie.isNotEmpty()) {
                        val host = request.url.host
                        jar.capture(host, setCookie)
                    }
                }
                // Jangan log header sensitif (Cookie / Authorization / token).
                Timber.tag(TAG).d(
                    "%s %s%s -> %d",
                    request.method,
                    request.url.host,
                    request.url.encodedPath,
                    response.code,
                )
                val body = response.body?.byteStream()?.use { readBounded(it, maxBytes) }?.toString(Charsets.UTF_8)
                TextResponse(response.code, response.headers, body)
            }
        }

    /**
     * Baca stream sampai maxBytes + 1. Kembalikan null kalau isinya melebihi
     * maxBytes (respons terlalu besar). Ditulis manual karena
     * InputStream.readNBytes butuh API 33, sedangkan minSdk Meowsic 26.
     */
    private fun readBounded(
        input: java.io.InputStream,
        maxBytes: Int,
    ): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "SpotifyApi"
    }
}
