package com.metrolist.kugou

import com.metrolist.kugou.models.DownloadLyricsResponse
import com.metrolist.kugou.models.Keyword
import com.metrolist.kugou.models.SearchLyricsResponse
import com.metrolist.kugou.models.SearchSongResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import java.lang.Integer.min
import kotlin.math.abs

@OptIn(ExperimentalSerializationApi::class, ExperimentalEncodingApi::class)
private val client = HttpClient {
    expectSuccess = true

    install(ContentNegotiation) {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        json(json)
        json(json, ContentType.Text.Html)
        json(json, ContentType.Text.Plain)
    }

    install(ContentEncoding) {
        gzip()
        deflate()
    }
}

private const val PAGE_SIZE = 8
private const val HEAD_CUT_LIMIT = 30

/**
 * KuGou Lyrics Library
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object KuGou {
    var useTraditionalChinese: Boolean = false

    /** KRC (per kata) dulu; kalau gagal baru LRC biasa (per baris). */
    private suspend fun fetchLyricsText(candidate: SearchLyricsResponse.Candidate): String {
        val fromKrc =
            runCatching {
                val raw = downloadLyrics(candidate.id, candidate.accesskey, fmt = "krc").content
                krcToLrc(decryptKrc(Base64.Default.decode(raw))).takeIf { it.isNotBlank() }
            }.getOrNull()
        return fromKrc
            ?: Base64.Default.decode(downloadLyrics(candidate.id, candidate.accesskey).content)
                .decodeToString()
                .normalize()
    }

    suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null): Result<String> =
        runCatching {
            val keyword = generateKeyword(title, artist, album)
            getLyricsCandidate(keyword, duration)?.let { candidate ->
                fetchLyricsText(candidate)
            } ?: throw IllegalStateException("No lyrics candidate")
        }

    suspend fun getAllPossibleLyricsOptions(
        title: String, artist: String, duration: Int, album: String? = null, callback: (String) -> Unit
    ) {
        val keyword = generateKeyword(title, artist, album)
        searchSongs(keyword).data.info.forEach {
            val durationOk = duration == -1 || abs(it.duration - duration) <= DURATION_TOLERANCE
            if (durationOk && looksLikeSameSong(keyword, it.songname, it.singername)) {
                searchLyricsByHash(it.hash).candidates.firstOrNull()?.let { candidate ->
                    fetchLyricsText(candidate).let(callback)
                }
            }
        }
        searchLyricsByKeyword(keyword, duration).candidates.forEach { candidate ->
            fetchLyricsText(candidate).let(callback)
        }
    }

    suspend fun getLyricsCandidate(
        keyword: Keyword, duration: Int
    ): SearchLyricsResponse.Candidate? {
        // KuGou suka mencocokkan lagu China yang durasinya kebetulan mirip.
        // Jadi judul & artis wajib dicek dulu; kalau tidak yakin, lebih baik
        // tidak ada lirik daripada lirik lagu lain.
        searchSongs(keyword).data.info.forEach { song ->
            val durationOk = duration == -1 || abs(song.duration - duration) <= DURATION_TOLERANCE
            if (!durationOk) return@forEach
            if (!looksLikeSameSong(keyword, song.songname, song.singername)) return@forEach
            val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull()
            if (candidate != null) return candidate
        }
        return searchLyricsByKeyword(keyword, duration)
            .candidates
            .firstOrNull { duration == -1 || abs(it.duration - duration) <= DURATION_TOLERANCE }
    }

    private fun normalizeForMatch(text: String?): String =
        (text ?: "")
            .lowercase()
            .replace(Regex("\\(.*?\\)|（.*?）|\\[.*?]"), " ")
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Cocokkan judul + artis secara longgar tapi cukup ketat untuk menolak
     *  lagu lain yang cuma mirip durasinya. */
    private fun looksLikeSameSong(keyword: Keyword, songName: String?, singerName: String?): Boolean {
        val wantTitle = normalizeForMatch(keyword.title)
        val gotTitle = normalizeForMatch(songName)
        if (wantTitle.isEmpty() || gotTitle.isEmpty()) return false
        val titleMatch =
            gotTitle == wantTitle ||
                gotTitle.contains(wantTitle) ||
                wantTitle.contains(gotTitle) ||
                similarity(wantTitle, gotTitle) >= 0.6
        if (!titleMatch) return false
        val wantArtist = normalizeForMatch(keyword.artist)
        val gotArtist = normalizeForMatch(singerName)
        if (wantArtist.isEmpty() || gotArtist.isEmpty()) return titleMatch
        return gotArtist.contains(wantArtist) || wantArtist.contains(gotArtist) || similarity(wantArtist, gotArtist) >= 0.5
    }

    private fun similarity(a: String, b: String): Double {
        val ta = a.split(" ").filter { it.isNotBlank() }.toSet()
        val tb = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / maxOf(ta.size, tb.size)
    }

    suspend fun searchSongs(keyword: Keyword) =
        client.get("https://mobileservice.kugou.com/api/v3/search/song") {
            parameter("version", 9108)
            parameter("plat", 0)
            parameter("pagesize", PAGE_SIZE)
            parameter("showtype", 0)
            val searchQuery = buildString {
                append(keyword.title)
                append(" - ")
                append(keyword.artist)
                if (!keyword.album.isNullOrBlank()) {
                    append(" ")
                    append(keyword.album)
                }
            }
            url.encodedParameters.append(
                "keyword",
                searchQuery.encodeURLParameter(spaceToPlus = false)
            )
        }.body<SearchSongResponse>()

    private suspend fun searchLyricsByKeyword(keyword: Keyword, duration: Int) =
        client.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter(
                "duration", duration.takeIf { it != -1 }?.times(1000)
            ) // if duration == -1, we don't care duration
            val searchQuery = buildString {
                append(keyword.title)
                append(" - ")
                append(keyword.artist)
                if (!keyword.album.isNullOrBlank()) {
                    append(" ")
                    append(keyword.album)
                }
            }
            url.encodedParameters.append(
                "keyword",
                searchQuery.encodeURLParameter(spaceToPlus = false)
            )
        }.body<SearchLyricsResponse>()

    private suspend fun searchLyricsByHash(hash: String) =
        client.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter("hash", hash)
        }.body<SearchLyricsResponse>()

    private suspend fun downloadLyrics(id: Long, accessKey: String, fmt: String = "lrc") =
        client.get("https://lyrics.kugou.com/download") {
            parameter("fmt", fmt)
            parameter("charset", "utf8")
            parameter("client", "pc")
            parameter("ver", 1)
            parameter("id", id)
            parameter("accesskey", accessKey)
        }.body<DownloadLyricsResponse>()

    /** KRC dienkripsi: 4 byte header, sisanya XOR per byte lalu zlib. */
    private fun decryptKrc(raw: ByteArray): String {
        if (raw.size < 8) return ""
        val key =
            byteArrayOf(
                0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
                0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
            )
        val data = raw.copyOf()
        for (i in 4 until data.size) {
            data[i] = (data[i].toInt() xor key[(i - 4) % key.size].toInt()).toByte()
        }
        val inflater = java.util.zip.Inflater()
        inflater.setInput(data, 4, data.size - 4)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        return out.toString("UTF-8")
    }

    /** KRC: [mulai,durasi]<offset,durasi,0>kata  ->  format app per kata. */
    private fun krcToLrc(krc: String): String {
        val lineRegex = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
        val wordRegex = Regex("<(\\d+),(\\d+),\\d+>([^<]*)")
        val sb = StringBuilder()
        krc.lineSequence().forEach { rawLine ->
            val m = lineRegex.find(rawLine.trim()) ?: return@forEach
            val lineStart = m.groupValues[1].toLongOrNull() ?: return@forEach
            val words =
                wordRegex.findAll(m.groupValues[3]).map { hit ->
                    val offset = hit.groupValues[1].toLongOrNull() ?: 0L
                    val dur = hit.groupValues[2].toLongOrNull() ?: 0L
                    Triple(hit.groupValues[3], lineStart + offset, lineStart + offset + dur)
                }.toList()
            if (words.isEmpty()) return@forEach
            val text = words.joinToString("") { it.first }.trim()
            if (text.isBlank()) return@forEach
            sb.appendLine(formatLrcTime(lineStart, text))
            val wordData =
                words
                    .filter { it.first.isNotBlank() }
                    .joinToString("|") { "${it.first.trim()}:${it.second / 1000.0}:${it.third / 1000.0}" }
            if (wordData.isNotBlank()) sb.appendLine("<$wordData>")
        }
        return sb.toString()
    }

    private fun formatLrcTime(ms: Long, text: String): String {
        val totalSeconds = ms / 1000
        return String.format(
            java.util.Locale.US,
            "[%02d:%02d.%02d]%s",
            totalSeconds / 60,
            totalSeconds % 60,
            (ms % 1000) / 10,
            text,
        )
    }

    private fun normalizeTitle(title: String) =
        title.replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")
            .replace("「.*」".toRegex(), "").replace("『.*』".toRegex(), "")
            .replace("<.*>".toRegex(), "").replace("《.*》".toRegex(), "")
            .replace("〈.*〉".toRegex(), "").replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String) =
        artist.replace(", ", "、").replace(" & ", "、").replace(".", "").replace("和", "、")
            .replace("\\(.*\\)".toRegex(), "").replace("（.*）".toRegex(), "")

    fun generateKeyword(title: String, artist: String, album: String? = null) =
        Keyword(normalizeTitle(title), normalizeArtist(artist), album)

    private fun String.normalize(): String =
        lines().filter { line -> line.matches(ACCEPTED_REGEX) }
            .let { lines ->
                // Remove useless information such as singer, writer, composer, guitar, etc.
                var headCutLine = 0
                for (i in min(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[i].matches(BANNED_REGEX)) {
                        headCutLine = i + 1
                        break
                    }
                }
                val filteredLines = lines.drop(headCutLine)

                var tailCutLine = 0
                for (i in min(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[lines.lastIndex - i].matches(BANNED_REGEX)) {
                        tailCutLine = i + 1
                        break
                    }
                }
                val finalLines = filteredLines.dropLast(tailCutLine)

                return@let finalLines.joinToString("\n")
            }

    @Suppress("RegExpRedundantEscape")
    private val ACCEPTED_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
    private val BANNED_REGEX = ".+].+[:：].+".toRegex()

    private const val DURATION_TOLERANCE = 8
}
