/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Port of Metrolist v14's foreign backup import
 * (shared/.../backup/ForeignLibraryImport.kt, ForeignBackupArchive.kt
 *  and PrismBackupImport.kt), adapted to Meowsic's Android-only Room stack.
 *
 * Reads a backup zip (or a bare SQLite file) from another music app
 * (InnerTune, OuterTune, ViMusic, RiMusic, Kreate, SimpMusic, Prism),
 * detects the family from the database schema, and MERGES its liked
 * songs, playlists and listening history into Meowsic's library. The
 * user's existing collection is never wiped; duplicates are skipped.
 *
 * Differences from v14, all forced by Meowsic's pre-KMP Room stack:
 * - The foreign database is opened with the framework
 *   android.database.sqlite.SQLiteDatabase instead of the KMP
 *   androidx.sqlite driver.
 * - v14 records imported plays in a HistoryProvenance table so a
 *   re-import never duplicates history. Meowsic has no such table
 *   (and the schema must not change), so plays are deduplicated by
 *   checking for an existing event with the same song id + timestamp.
 * - v14 reports new likes through a SongLikeState callback. Meowsic
 *   derives the Liked playlist from the song.liked column, so merging
 *   the flag is enough and there is no callback.
 * - Meowsic's PlaylistEntity has no description column, so foreign
 *   playlist descriptions are dropped.
 */

package com.metrolist.music.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongAlbumMap
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.cancellation.CancellationException

enum class ForeignLibraryFamily {
    INNERTUNE,

    VIMUSIC,

    KREATE,

    SIMPMUSIC,

    PRISM,
}

data class ForeignArtist(
    val id: String?,
    val name: String,
)

data class ForeignSong(
    val id: String,
    val title: String,
    val artists: List<ForeignArtist> = emptyList(),
    val albumId: String? = null,
    val albumName: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val inLibraryAt: Long? = null,
    val totalPlayTimeMs: Long = 0,
    val isLocal: Boolean = false,
    val isVideo: Boolean = false,
    val isUploaded: Boolean = false,
    val isEpisode: Boolean = false,
    val isLive: Boolean = false,
)

data class ForeignPlaylist(
    val name: String,
    val browseId: String?,
    val songIds: List<String>,
    val isEditable: Boolean = false,
    val description: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val thumbnailUrl: String? = null,
)

data class ForeignPlay(
    val songId: String,
    val playedAt: Long,
    val playTimeMs: Long,
)

data class ForeignBookmark(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val bookmarkedAt: Long,
    val year: Int? = null,
)

data class ForeignLibrary(
    val family: ForeignLibraryFamily,
    val songs: List<ForeignSong>,
    val playlists: List<ForeignPlaylist>,
    val plays: List<ForeignPlay>,
    val artists: List<ForeignBookmark>,
    val albums: List<ForeignBookmark>,
)

data class ForeignImportSummary(
    val family: ForeignLibraryFamily,
    val songs: Int,
    val likes: Int,
    val playlists: Int,
    val plays: Int,
    val skippedSongs: Int,
)

class UnsupportedForeignLibraryException :
    IllegalArgumentException("The selected file is not a supported music app backup")

/**
 * Result of a foreign backup import. The caller formats the user-visible
 * message from [Success.summary] / [Failed.error] using string resources;
 * no user-visible text is produced here.
 */
sealed interface ForeignImportResult {
    data class Success(val summary: ForeignImportSummary) : ForeignImportResult

    data object Unsupported : ForeignImportResult

    data class Failed(val error: Throwable) : ForeignImportResult
}

/**
 * Imports a backup from another music app into Meowsic's library.
 *
 * Usage (mirrors the v14 screen call):
 * ```
 * val importer = ForeignBackupImporter(database)
 * when (val result = importer.importForeignBackup(input, File(context.cacheDir, "foreign-import"))) {
 *     is ForeignImportResult.Success -> { /* result.summary counts */ }
 *     ForeignImportResult.Unsupported -> { /* not a supported backup */ }
 *     is ForeignImportResult.Failed -> { /* result.error */ }
 * }
 * ```
 * Must be called from a background thread / IO dispatcher. All database
 * writes run inside a single Room transaction.
 */
class ForeignBackupImporter(
    private val database: MusicDatabase,
) {
    suspend fun importForeignBackup(
        input: InputStream,
        stagingDirectory: File,
    ): ForeignImportResult {
        stagingDirectory.deleteRecursively()
        check(stagingDirectory.mkdirs()) { "Could not create import staging directory" }
        return try {
            val library =
                stageForeignDatabases(input, stagingDirectory).firstNotNullOfOrNull { candidate ->
                    // The staged copy is disposable, so it is opened read-write:
                    // a WAL database needs its -wal/-shm sidecars recovered at open.
                    SQLiteDatabase.openDatabase(candidate.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                        db.takeIf { it.detectForeignLibraryFamily() != null }?.readForeignLibrary()
                    }
                } ?: return ForeignImportResult.Unsupported
            ForeignImportResult.Success(importForeignLibrary(library.withPrismArchive(stagingDirectory)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsupportedForeignLibraryException) {
            ForeignImportResult.Unsupported
        } catch (e: Exception) {
            ForeignImportResult.Failed(e)
        } finally {
            stagingDirectory.deleteRecursively()
        }
    }

    private suspend fun importForeignLibrary(library: ForeignLibrary): ForeignImportSummary {
        val now = System.currentTimeMillis()
        val sourceSongs = library.songs.filter { !it.isLocal && !it.isLive && isYouTubeVideoId(it.id) }.associateBy(ForeignSong::id)
        val wanted =
            buildSet {
                library.songs.forEach { if (it.liked || it.inLibraryAt != null || it.totalPlayTimeMs > 0) add(it.id) }
                library.playlists.forEach { addAll(it.songIds) }
                library.plays.forEach { add(it.songId) }
            }
        val imported = wanted.filter(sourceSongs::containsKey).toSet()
        // No HistoryProvenance table in Meowsic: preload the (song, time)
        // pairs already in the event table so a re-import does not duplicate
        // history. Scoped to the imported plays' time range.
        val existingPlayKeys = loadExistingPlayKeys(library.plays)
        var likeCount = 0
        var playCount = 0
        var playlistCount = 0
        database.withTransaction {
            imported.forEach { id ->
                val source = sourceSongs.getValue(id)
                if (getSongById(id) == null) this@ForeignBackupImporter.insertSongWithRelations(source)
                val stored = checkNotNull(getSongById(id)).song
                val likedAt = source.likedAt?.takeIf { it > 0 } ?: now
                val merged =
                    stored.copy(
                        liked = stored.liked || source.liked,
                        likedDate = stored.likedDate ?: likedAt.takeIf { source.liked }?.toLocalDateTime(),
                        inLibrary = stored.inLibrary ?: (source.inLibraryAt ?: likedAt.takeIf { source.liked })?.toLocalDateTime(),
                        totalPlayTime = maxOf(stored.totalPlayTime, source.totalPlayTimeMs),
                    )
                if (merged != stored) update(merged)
                if (source.liked && !stored.liked) likeCount++
            }
            library.plays.forEach { play ->
                if (play.songId !in imported || play.playedAt <= 0) return@forEach
                if (play.songId to play.playedAt in existingPlayKeys) return@forEach
                insert(
                    Event(songId = play.songId, timestamp = play.playedAt.toLocalDateTime(), playTime = play.playTimeMs.coerceAtLeast(0)),
                )
                playCount++
            }
            library.playlists.forEach { source ->
                val name = source.name.trim()
                if (name.isEmpty() && source.browseId == null) return@forEach
                val songIds = source.songIds.filter(imported::contains)
                val existing =
                    if (source.browseId != null) {
                        playlistByBrowseId(source.browseId).first()?.playlist
                    } else {
                        playlistEntitiesByNameAsc().firstOrNull { it.name == name && it.browseId == null && it.isEditable }
                    }
                if (existing != null && existing.browseId != null) return@forEach
                val target =
                    existing ?: PlaylistEntity(
                        name = name.ifEmpty { checkNotNull(source.browseId) },
                        browseId = source.browseId,
                        isEditable = source.browseId == null || source.isEditable,
                        isLocal = source.browseId == null,
                        remoteSongCount = songIds.size.takeIf { source.browseId != null },
                        thumbnailUrl = source.thumbnailUrl ?: songIds.firstNotNullOfOrNull { sourceSongs[it]?.thumbnailUrl },
                        createdAt = (source.createdAt ?: now).toLocalDateTime(),
                        lastUpdateTime = (source.updatedAt ?: now).toLocalDateTime(),
                    ).also { insert(it) }
                var position = nextPlaylistSongPosition(target.id)
                val existingCounts = playlistSongMaps(target.id, 0).groupingBy { it.songId }.eachCount()
                val sourceCounts = mutableMapOf<String, Int>()
                songIds.forEach { songId ->
                    val occurrence = (sourceCounts[songId] ?: 0) + 1
                    sourceCounts[songId] = occurrence
                    if (occurrence > (existingCounts[songId] ?: 0)) {
                        insert(PlaylistSongMap(playlistId = target.id, songId = songId, position = position++))
                    }
                }
                playlistCount++
            }
            library.artists.forEach { artist ->
                if (!artist.id.startsWith("UC")) return@forEach
                val stored = getArtistById(artist.id)
                if (stored == null) {
                    insert(
                        ArtistEntity(
                            id = artist.id,
                            name = artist.name,
                            thumbnailUrl = artist.thumbnailUrl,
                            bookmarkedAt = artist.bookmarkedAt.toLocalDateTime(),
                        ),
                    )
                } else if (stored.bookmarkedAt == null) {
                    update(stored.copy(bookmarkedAt = artist.bookmarkedAt.toLocalDateTime()))
                }
            }
            library.albums.forEach { album ->
                if (!album.id.startsWith("MPRE")) return@forEach
                val stored = album(album.id).first()?.album
                if (stored == null) {
                    insert(
                        AlbumEntity(
                            id = album.id,
                            title = album.name,
                            year = album.year,
                            thumbnailUrl = album.thumbnailUrl,
                            songCount = 0,
                            duration = 0,
                            bookmarkedAt = album.bookmarkedAt.toLocalDateTime(),
                        ),
                    )
                } else if (stored.bookmarkedAt == null) {
                    update(stored.copy(bookmarkedAt = album.bookmarkedAt.toLocalDateTime()))
                }
            }
        }
        return ForeignImportSummary(
            family = library.family,
            songs = imported.size,
            likes = likeCount,
            playlists = playlistCount,
            plays = playCount,
            skippedSongs = (wanted - imported).size,
        )
    }

    /**
     * Inserts a foreign song with its artist/album relations, following the
     * same pattern as DatabaseDao.insert(MediaMetadata): artists and album
     * first (foreign-key targets), then the song, then the maps. The song
     * is inserted unliked; the merge step in [importForeignLibrary] applies
     * the liked / in-library / play-time flags afterwards.
     */
    private fun insertSongWithRelations(source: ForeignSong) {
        val artistIds =
            source.artists.mapIndexed { index, artist ->
                val artistId =
                    artist.id?.takeIf { it.isNotBlank() }
                        ?: database.artistByName(artist.name)?.id
                        ?: ArtistEntity.generateArtistId()
                database.insert(
                    ArtistEntity(
                        id = artistId,
                        name = artist.name,
                        channelId = artist.id?.takeIf { it.startsWith("UC") },
                    ),
                )
                artistId to index
            }
        val albumId = source.albumId?.takeIf { it.isNotBlank() }
        if (albumId != null) {
            database.insert(
                AlbumEntity(
                    id = albumId,
                    title = source.albumName?.takeIf { it.isNotBlank() } ?: albumId,
                    songCount = 0,
                    duration = 0,
                ),
            )
        }
        database.insert(
            SongEntity(
                id = source.id,
                title = source.title,
                duration = source.durationSeconds ?: -1,
                thumbnailUrl = source.thumbnailUrl,
                albumId = albumId,
                albumName = source.albumName,
                explicit = source.explicit,
                isUploaded = source.isUploaded,
                isVideo = source.isVideo,
                isEpisode = source.isEpisode,
            ),
        )
        artistIds.forEach { (artistId, index) ->
            database.insert(SongArtistMap(songId = source.id, artistId = artistId, position = index))
        }
        if (albumId != null) {
            database.insert(SongAlbumMap(songId = source.id, albumId = albumId, index = 0))
        }
    }

    private fun loadExistingPlayKeys(plays: List<ForeignPlay>): Set<Pair<String, Long>> {
        if (plays.isEmpty()) return emptySet()
        val minPlayedAt = plays.minOf { it.playedAt }
        val maxPlayedAt = plays.maxOf { it.playedAt }
        return database.openHelper.readableDatabase.query(
            "SELECT songId, timestamp FROM event WHERE timestamp >= ? AND timestamp <= ?",
            arrayOf(minPlayedAt, maxPlayedAt),
        ).use { cursor ->
            buildSet {
                val songIdIdx = cursor.getColumnIndexOrThrow("songId")
                val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
                while (cursor.moveToNext()) {
                    add(cursor.getString(songIdIdx) to cursor.getLong(tsIdx))
                }
            }
        }
    }
}

// ---- Archive staging (port of v14 ForeignBackupArchive.kt) ----

private val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()

private const val MAX_FOREIGN_ARCHIVE_ENTRIES = 4096

private object BackupArchiveLimits {
    const val DATABASE_BYTES = 512L * 1024L * 1024L
    const val SETTINGS_BYTES = 10L * 1024L * 1024L
    const val UNKNOWN_ENTRY_BYTES = 10L * 1024L * 1024L
    const val TOTAL_UNCOMPRESSED_BYTES = 600L * 1024L * 1024L
}

internal fun stageForeignDatabases(
    input: InputStream,
    directory: File,
): List<File> {
    val buffered = BufferedInputStream(input)
    buffered.mark(SQLITE_HEADER.size)
    val header = buffered.readHeader()
    buffered.reset()
    if (header.contentEquals(SQLITE_HEADER)) {
        val target = File(directory, "source.db")
        target.outputStream().buffered().use { buffered.copyLimited(it, BackupArchiveLimits.DATABASE_BYTES) }
        return listOf(target)
    }
    val databases = mutableListOf<File>()
    var total = 0L
    var entries = 0
    ZipInputStream(buffered).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            check(++entries <= MAX_FOREIGN_ARCHIVE_ENTRIES) { "Backup contains too many entries" }
            val prismFile = prismArchiveFile(directory, entry.name)
            if (!entry.isDirectory && prismFile != null) {
                if (prismFile.exists()) throw UnsupportedForeignLibraryException()
                val parent = checkNotNull(prismFile.parentFile)
                check(parent.mkdirs() || parent.isDirectory)
                total += prismFile.outputStream().buffered().use { zip.copyLimited(it, BackupArchiveLimits.SETTINGS_BYTES) }
                check(total <= BackupArchiveLimits.TOTAL_UNCOMPRESSED_BYTES) { "Backup is too large" }
                continue
            }
            val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
            val isSidecar = name.endsWith("-wal") || name.endsWith("-shm")
            val target = File(directory, name)
            if (entry.isDirectory || name.isBlank() || name.startsWith(".") || target.exists() || "exoplayer" in name.lowercase()) {
                total += zip.copyLimited(DiscardingOutputStream, BackupArchiveLimits.UNKNOWN_ENTRY_BYTES)
            } else {
                total += target.outputStream().buffered().use { zip.copyLimited(it, BackupArchiveLimits.DATABASE_BYTES) }
                val isDatabase = !isSidecar && target.inputStream().use { it.readHeader() }.contentEquals(SQLITE_HEADER)
                if (isDatabase) {
                    databases += target
                } else if (!isSidecar) {
                    target.delete()
                }
            }
            check(total <= BackupArchiveLimits.TOTAL_UNCOMPRESSED_BYTES) { "Backup is too large" }
        }
    }
    if (databases.isEmpty()) throw UnsupportedForeignLibraryException()
    return databases
}

private object DiscardingOutputStream : OutputStream() {
    override fun write(value: Int) = Unit

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) = Unit
}

private fun InputStream.readHeader(): ByteArray {
    val header = ByteArray(SQLITE_HEADER.size)
    var read = 0
    while (read < header.size) {
        val count = read(header, read, header.size - read)
        if (count < 0) break
        read += count
    }
    return header.copyOf(read)
}

private fun InputStream.copyLimited(
    output: OutputStream,
    maxBytes: Long,
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return total
        total += read
        check(total <= maxBytes) { "Backup entry is too large" }
        output.write(buffer, 0, read)
    }
}

// ---- Reading the foreign database (port of v14 ForeignLibraryImport.kt) ----

/** Cursor row with case-insensitive column lookup, like v14's ForeignRow. */
private class ForeignCursor(private val cursor: Cursor) {
    private val columns: Map<String, Int> =
        cursor.columnNames.mapIndexed { index, name -> name.lowercase() to index }.toMap()

    private fun index(column: String): Int? =
        columns[column.lowercase()]?.takeUnless { cursor.isNull(it) }

    fun text(column: String): String? = index(column)?.let { cursor.getString(it) }

    fun long(column: String): Long? {
        val idx = index(column) ?: return null
        return when (cursor.getType(idx)) {
            Cursor.FIELD_TYPE_STRING -> cursor.getString(idx)?.toLongOrNull()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(idx).toLong()
            Cursor.FIELD_TYPE_NULL -> null
            else -> cursor.getLong(idx)
        }
    }
}

private fun <T> SQLiteDatabase.rows(
    sql: String,
    map: (ForeignCursor) -> T,
): List<T> =
    rawQuery(sql, null).use { cursor ->
        val row = ForeignCursor(cursor)
        buildList { while (cursor.moveToNext()) add(map(row)) }
    }

private fun SQLiteDatabase.foreignTableColumns(): Map<String, Set<String>> {
    val names =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'") { it.text("name").orEmpty() }
    return names.associate { name ->
        name.lowercase() to
            rows("PRAGMA table_info(\"${name.replace("\"", "\"\"")}\")") { it.text("name").orEmpty().lowercase() }.toSet()
    }
}

private fun SQLiteDatabase.detectForeignLibraryFamily(): ForeignLibraryFamily? {
    val tables = foreignTableColumns()

    fun has(
        table: String,
        vararg columns: String,
    ) = tables[table.lowercase()]?.containsAll(columns.map(String::lowercase)) == true
    return when {
        has("song", "id", "title", "liked") && has("playlist", "id", "name") &&
            has("playlist_song_map", "playlistId", "songId", "position") -> ForeignLibraryFamily.INNERTUNE

        has("song", "id", "title", "likedAt", "totalPlayTimeMs") && has("playlist", "id", "name") &&
            has("songplaylistmap", "songId", "playlistId", "position") -> ForeignLibraryFamily.VIMUSIC

        has("songs", "id", "title", "liked_at", "total_playtime") && has("playlists", "id", "name") &&
            has("song_playlist_map", "song_id", "playlist_id", "position") -> ForeignLibraryFamily.KREATE

        has("song", "videoId", "title", "liked") && has("local_playlist", "id", "title") &&
            has("pair_song_local_playlist", "playlistId", "songId", "position") -> ForeignLibraryFamily.SIMPMUSIC

        has("songs", "id", "title", "artistName", "durationSec", "liked", "likedAt", "lastPlayed") &&
            has("play_events", "id", "songId", "timestamp", "playedMs") -> ForeignLibraryFamily.PRISM

        else -> null
    }
}

private fun SQLiteDatabase.readForeignLibrary(): ForeignLibrary {
    execSQL("PRAGMA query_only = ON")
    val family = detectForeignLibraryFamily() ?: throw UnsupportedForeignLibraryException()
    val tables = foreignTableColumns()
    return when (family) {
        ForeignLibraryFamily.INNERTUNE -> readInnerTune(tables)
        ForeignLibraryFamily.VIMUSIC -> readViMusic(ViMusicNames.VIMUSIC, tables)
        ForeignLibraryFamily.KREATE -> readViMusic(ViMusicNames.KREATE, tables)
        ForeignLibraryFamily.SIMPMUSIC -> readSimpMusic(tables)
        ForeignLibraryFamily.PRISM -> readPrism()
    }
}

private fun SQLiteDatabase.readInnerTune(tables: Map<String, Set<String>>): ForeignLibrary {
    val artists = mutableMapOf<String, MutableList<ForeignArtist>>()
    if ("song_artist_map" in tables && "artist" in tables) {
        rows(
            "SELECT m.songId AS songId, a.id AS id, a.name AS name FROM song_artist_map m " +
                "JOIN artist a ON a.id = m.artistId ORDER BY m.songId, m.position",
        ) { row ->
            val name = row.text("name") ?: return@rows
            artists.getOrPut(row.text("songId").orEmpty()) { mutableListOf() } += ForeignArtist(row.text("id")?.youtubeChannelId(), name)
        }
    }
    val songs =
        rows("SELECT * FROM song") { row ->
            val id = row.text("id") ?: return@rows null
            ForeignSong(
                id = id,
                title = row.text("title").orEmpty(),
                artists = artists[id].orEmpty(),
                albumId = row.text("albumId"),
                albumName = row.text("albumName"),
                durationSeconds = row.long("duration")?.toInt()?.takeIf { it > 0 },
                thumbnailUrl = row.text("thumbnailUrl"),
                explicit = row.long("explicit") == 1L,
                liked = row.long("liked") == 1L,
                likedAt = row.long("likedDate"),
                inLibraryAt = row.long("inLibrary"),
                totalPlayTimeMs = row.long("totalPlayTime") ?: 0,
                isLocal = row.long("isLocal") == 1L,
            )
        }.filterNotNull()
    val members = mutableMapOf<String, MutableList<String>>()
    rows("SELECT playlistId, songId FROM playlist_song_map ORDER BY playlistId, position, id") { row ->
        members.getOrPut(row.text("playlistId").orEmpty()) { mutableListOf() } += row.text("songId").orEmpty()
    }
    val playlists =
        rows("SELECT * FROM playlist") { row ->
            ForeignPlaylist(
                name = row.text("name").orEmpty(),
                browseId = row.text("browseId")?.takeIf { row.long("isLocal") != 1L },
                songIds = members[row.text("id")].orEmpty(),
                isEditable = row.long("isEditable") == 1L,
            )
        }
    val plays =
        if ("event" in tables) {
            rows("SELECT songId, timestamp, playTime FROM event ORDER BY timestamp, id") { row ->
                ForeignPlay(row.text("songId").orEmpty(), row.long("timestamp") ?: 0, row.long("playTime") ?: 0)
            }
        } else {
            emptyList()
        }
    return ForeignLibrary(
        family = ForeignLibraryFamily.INNERTUNE,
        songs = songs,
        playlists = playlists,
        plays = plays,
        artists = bookmarks(tables, "artist", "name", "thumbnailUrl", "bookmarkedAt"),
        albums = bookmarks(tables, "album", "title", "thumbnailUrl", "bookmarkedAt"),
    )
}

private enum class ViMusicNames(
    val song: String,
    val artistsText: String,
    val duration: String,
    val thumbnail: String,
    val likedAt: String,
    val totalPlayTime: String,
    val explicit: String,
    val songArtistMap: String,
    val artist: String,
    val songAlbumMap: String,
    val album: String,
    val playlist: String,
    val browseId: String,
    val songPlaylistMap: String,
    val songId: String,
    val artistId: String,
    val albumId: String,
    val playlistId: String,
    val event: String,
    val eventTimestamp: String,
    val eventPlayTime: String,
    val bookmarkedAt: String,
) {
    VIMUSIC(
        "Song",
        "artistsText",
        "durationText",
        "thumbnailUrl",
        "likedAt",
        "totalPlayTimeMs",
        "explicit",
        "SongArtistMap",
        "Artist",
        "SongAlbumMap",
        "Album",
        "Playlist",
        "browseId",
        "SongPlaylistMap",
        "songId",
        "artistId",
        "albumId",
        "playlistId",
        "Event",
        "timestamp",
        "playTime",
        "bookmarkedAt",
    ),
    KREATE(
        "songs",
        "artists",
        "duration",
        "thumbnail_url",
        "liked_at",
        "total_playtime",
        "is_explicit",
        "song_artist_map",
        "artists",
        "song_album_map",
        "albums",
        "playlists",
        "browse_id",
        "song_playlist_map",
        "song_id",
        "artist_id",
        "album_id",
        "playlist_id",
        "playback_history",
        "created_at",
        "time_spent",
        "bookmarked_at",
    ),
}

private fun SQLiteDatabase.readViMusic(
    n: ViMusicNames,
    tables: Map<String, Set<String>>,
): ForeignLibrary {
    val family = if (n == ViMusicNames.KREATE) ForeignLibraryFamily.KREATE else ForeignLibraryFamily.VIMUSIC
    val artists = mutableMapOf<String, MutableList<ForeignArtist>>()
    if (n.songArtistMap.lowercase() in tables && n.artist.lowercase() in tables) {
        // The map has no position column; insertion order is the source's display order.
        rows(
            "SELECT m.${n.songId} AS songId, a.id AS id, a.name AS name FROM ${n.songArtistMap} m " +
                "JOIN ${n.artist} a ON a.id = m.${n.artistId} ORDER BY m.rowid",
        ) { row ->
            val name = row.text("name")?.takeIf(String::isNotBlank) ?: return@rows
            artists.getOrPut(row.text("songId").orEmpty()) { mutableListOf() } += ForeignArtist(row.text("id")?.youtubeChannelId(), name)
        }
    }
    val albums = mutableMapOf<String, Pair<String, String?>>()
    if (n.songAlbumMap.lowercase() in tables && n.album.lowercase() in tables) {
        rows(
            "SELECT m.${n.songId} AS songId, a.id AS id, a.title AS title FROM ${n.songAlbumMap} m " +
                "JOIN ${n.album} a ON a.id = m.${n.albumId}",
        ) { row -> albums[row.text("songId").orEmpty()] = row.text("id").orEmpty() to row.text("title") }
    }
    val songs =
        rows("SELECT * FROM ${n.song}") { row ->
            val id = row.text("id") ?: return@rows null
            // RiMusic and Kreate store -1 as a dislike marker; only positive timestamps are likes.
            val likedAt = row.long(n.likedAt)?.takeIf { it > 0 }
            ForeignSong(
                id = id,
                title = row.text("title").orEmpty(),
                artists =
                    artists[id] ?: row
                        .text(n.artistsText)
                        ?.takeIf(String::isNotBlank)
                        ?.let { listOf(ForeignArtist(null, it)) }
                        .orEmpty(),
                albumId = albums[id]?.first,
                albumName = albums[id]?.second,
                durationSeconds = row.text(n.duration)?.let(::parseDurationText),
                thumbnailUrl = row.text(n.thumbnail),
                explicit = row.long(n.explicit) == 1L,
                liked = likedAt != null,
                likedAt = likedAt,
                totalPlayTimeMs = row.long(n.totalPlayTime) ?: 0,
                isLocal = row.long("is_local") == 1L,
            )
        }.filterNotNull()
    val members = mutableMapOf<Long, MutableList<String>>()
    rows(
        "SELECT ${n.playlistId} AS playlistId, ${n.songId} AS songId FROM ${n.songPlaylistMap} ORDER BY ${n.playlistId}, position",
    ) { row ->
        members.getOrPut(row.long("playlistId") ?: 0) { mutableListOf() } += row.text("songId").orEmpty()
    }
    val playlists =
        rows("SELECT * FROM ${n.playlist}") { row ->
            ForeignPlaylist(
                name = row.text("name").orEmpty(),
                browseId = row.text(n.browseId)?.removePrefix("VL")?.takeIf(String::isNotBlank),
                songIds = members[row.long("id")].orEmpty(),
                isEditable = (row.long("isEditable") ?: row.long("editable")) == 1L,
            )
        }
    val plays =
        if (n.event.lowercase() in tables) {
            rows(
                "SELECT ${n.songId} AS songId, ${n.eventTimestamp} AS ts, ${n.eventPlayTime} AS playTime FROM ${n.event} ORDER BY ts, id",
            ) {
                ForeignPlay(it.text("songId").orEmpty(), it.long("ts") ?: 0, it.long("playTime") ?: 0)
            }
        } else {
            emptyList()
        }
    return ForeignLibrary(
        family = family,
        songs = songs,
        playlists = playlists,
        plays = plays,
        artists = bookmarks(tables, n.artist, "name", n.thumbnail, n.bookmarkedAt),
        albums = bookmarks(tables, n.album, "title", n.thumbnail, n.bookmarkedAt),
    )
}

private fun SQLiteDatabase.readSimpMusic(tables: Map<String, Set<String>>): ForeignLibrary {
    val songs =
        rows("SELECT * FROM song") { row ->
            val id = row.text("videoId") ?: return@rows null
            val artistIds = jsonStrings(row.text("artistId"))
            val artistNames = jsonStrings(row.text("artistName"))
            val liked = row.long("liked") == 1L
            ForeignSong(
                id = id,
                title = row.text("title").orEmpty(),
                artists =
                    artistNames.mapIndexedNotNull { index, name ->
                        name?.takeIf(String::isNotBlank)?.let { ForeignArtist(artistIds.getOrNull(index)?.youtubeChannelId(), it) }
                    },
                albumId = row.text("albumId"),
                albumName = row.text("albumName"),
                durationSeconds = row.long("durationSeconds")?.toInt()?.takeIf { it > 0 },
                thumbnailUrl = row.text("thumbnails"),
                explicit = row.long("isExplicit") == 1L,
                liked = liked,
                likedAt = row.long("favoriteAt").takeIf { liked },
                // song.totalPlayTime is a play counter, not milliseconds; history comes from playback_event instead.
            )
        }.filterNotNull()
    val members = mutableMapOf<Long, MutableList<String>>()
    rows("SELECT playlistId, songId FROM pair_song_local_playlist ORDER BY playlistId, position, id") { row ->
        members.getOrPut(row.long("playlistId") ?: 0) { mutableListOf() } += row.text("songId").orEmpty()
    }
    val localPlaylists =
        rows("SELECT * FROM local_playlist") { row ->
            ForeignPlaylist(
                name = row.text("title").orEmpty(),
                browseId = row.text("youtubePlaylistId")?.removePrefix("VL")?.takeIf(String::isNotBlank),
                songIds = members[row.long("id")].orEmpty(),
                isEditable = true,
            )
        }
    val savedPlaylists =
        if ("playlist" in tables) {
            rows("SELECT id, title, tracks FROM playlist WHERE liked = 1") { row ->
                ForeignPlaylist(
                    name = row.text("title").orEmpty(),
                    browseId = row.text("id")?.removePrefix("VL"),
                    songIds = jsonStrings(row.text("tracks")).filterNotNull(),
                )
            }
        } else {
            emptyList()
        }
    val plays =
        if ("playback_event" in tables) {
            rows("SELECT videoId, timestamp, listenedSecond FROM playback_event ORDER BY timestamp, eventId") { row ->
                ForeignPlay(row.text("videoId").orEmpty(), row.long("timestamp") ?: 0, (row.long("listenedSecond") ?: 0) * 1000)
            }
        } else {
            emptyList()
        }
    val now = System.currentTimeMillis()
    val artists =
        if ("artist" in tables) {
            rows("SELECT * FROM artist WHERE followed = 1") { row ->
                ForeignBookmark(
                    row.text("channelId").orEmpty(),
                    row.text("name").orEmpty(),
                    row.text("thumbnails"),
                    row.long("followedAt") ?: now,
                )
            }
        } else {
            emptyList()
        }
    val albums =
        if ("album" in tables) {
            rows("SELECT * FROM album WHERE liked = 1") { row ->
                ForeignBookmark(
                    row.text("browseId").orEmpty(),
                    row.text("title").orEmpty(),
                    row.text("thumbnails"),
                    row.long("favoriteAt") ?: now,
                    row.text("year")?.toIntOrNull(),
                )
            }
        } else {
            emptyList()
        }
    return ForeignLibrary(ForeignLibraryFamily.SIMPMUSIC, songs, localPlaylists + savedPlaylists, plays, artists, albums)
}

private fun SQLiteDatabase.readPrism(): ForeignLibrary {
    val plays =
        rows("SELECT songId, timestamp, playedMs FROM play_events ORDER BY timestamp, id") { row ->
            ForeignPlay(row.text("songId").orEmpty(), row.long("timestamp") ?: 0, row.long("playedMs") ?: 0)
        }
    val playTime =
        plays.filter { it.playedAt > 0 }.groupBy(ForeignPlay::songId).mapValues { (_, events) ->
            events.sumOf { it.playTimeMs.coerceAtLeast(0) }
        }
    val songs =
        rows("SELECT * FROM songs") { row ->
            val id = row.text("id").orEmpty()
            val artists = row.text("artistsJson")?.let { runCatching { prismArtists(it) }.getOrNull() }
            ForeignSong(
                id = id,
                title = row.text("title").orEmpty(),
                artists =
                    artists ?: row
                        .text("artistName")
                        .orEmpty()
                        .takeUnless { it == "Unknown artist" && row.text("artistId") == null }
                        .orEmpty()
                        .split(", ")
                        .filter(String::isNotBlank)
                        .mapIndexed { index, name ->
                            ForeignArtist(row.text("artistId")?.youtubeChannelId().takeIf { index == 0 }, name)
                        },
                albumId = row.text("albumId"),
                albumName = row.text("albumTitle"),
                durationSeconds = row.long("durationSec")?.toInt()?.takeIf { it > 0 },
                thumbnailUrl = row.text("thumbnail"),
                liked = row.long("liked") == 1L,
                likedAt = row.long("likedAt")?.takeIf { it > 0 },
                totalPlayTimeMs = playTime[id] ?: 0,
                isLocal = id.startsWith("local:"),
                isVideo = row.long("isVideo") == 1L,
            )
        }
    return ForeignLibrary(ForeignLibraryFamily.PRISM, songs, emptyList(), plays, emptyList(), emptyList())
}

private fun prismArtists(json: String): List<ForeignArtist> =
    Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
        val artist = element as? JsonObject ?: return@mapNotNull null
        val name = (artist["name"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        ForeignArtist((artist["id"] as? JsonPrimitive)?.contentOrNull?.youtubeChannelId(), name)
    }

private fun SQLiteDatabase.bookmarks(
    tables: Map<String, Set<String>>,
    table: String,
    nameColumn: String,
    thumbnailColumn: String,
    bookmarkColumn: String,
): List<ForeignBookmark> {
    if (tables[table.lowercase()]?.contains(bookmarkColumn.lowercase()) != true) return emptyList()
    return rows("SELECT * FROM $table WHERE $bookmarkColumn IS NOT NULL") { row ->
        ForeignBookmark(
            id = row.text("id").orEmpty(),
            name = row.text(nameColumn).orEmpty(),
            thumbnailUrl = row.text(thumbnailColumn),
            bookmarkedAt = row.long(bookmarkColumn) ?: 0,
            year = row.text("year")?.toIntOrNull(),
        )
    }
}

// ---- Prism archive extras (port of v14 PrismBackupImport.kt) ----

private const val PRISM_ARCHIVE_DIRECTORY = ".prism"
private const val PRISM_LOCAL_LIBRARY = "files/library/prism.json"
private const val PRISM_REMOTE_LIBRARY = "files/offline/library.json"
private const val PRISM_ARTIST_PREFERENCES = "shared_prefs/artist_prefs.xml"
private val PRISM_PAGE = Regex("files/offline/pages/[A-Za-z0-9_-]+\\.json")

internal fun prismArchiveFile(
    directory: File,
    entry: String,
): File? =
    if (entry in setOf(PRISM_LOCAL_LIBRARY, PRISM_REMOTE_LIBRARY, PRISM_ARTIST_PREFERENCES) || PRISM_PAGE.matches(entry)) {
        directory.resolve(PRISM_ARCHIVE_DIRECTORY).resolve(entry)
    } else {
        null
    }

internal fun ForeignLibrary.withPrismArchive(directory: File): ForeignLibrary {
    if (family != ForeignLibraryFamily.PRISM) return this
    val now = System.currentTimeMillis()
    val songs = songs.associateBy(ForeignSong::id).toMutableMap()
    val playlists = playlists.toMutableList()
    val artists = artists.toMutableList()
    val albums = albums.toMutableList()

    fun file(entry: String) = checkNotNull(prismArchiveFile(directory, entry))

    fun read(entry: String) = file(entry).takeIf(File::isFile)?.readText()?.let { Json.parseToJsonElement(it).jsonObject }

    fun members(collection: JsonObject): List<String> =
        collection.objects("songs").map { item ->
            val song = item.prismSong()
            val stored = songs[song.id]
            songs[song.id] =
                if (stored == null) {
                    song
                } else {
                    stored.copy(
                        title = stored.title.takeUnless { it.isBlank() || it == stored.id } ?: song.title,
                        artists = stored.artists.ifEmpty { song.artists },
                        albumId = stored.albumId?.takeIf(String::isNotBlank) ?: song.albumId,
                        albumName = stored.albumName?.takeIf(String::isNotBlank) ?: song.albumName,
                        durationSeconds = stored.durationSeconds?.takeIf { it > 0 } ?: song.durationSeconds,
                        thumbnailUrl = stored.thumbnailUrl?.takeIf(String::isNotBlank) ?: song.thumbnailUrl,
                        explicit = stored.explicit || song.explicit,
                        isLocal = stored.isLocal || song.isLocal,
                        isLive = stored.isLive || song.isLive,
                        isVideo = stored.isVideo || song.isVideo,
                        isUploaded = stored.isUploaded || song.isUploaded,
                        isEpisode = stored.isEpisode || song.isEpisode,
                    )
                }
            song.id
        }

    read(PRISM_LOCAL_LIBRARY)?.let { library ->
        library.objects("lists").forEach { list ->
            playlists +=
                ForeignPlaylist(
                    name = list.getValue("title").jsonPrimitive.content,
                    browseId = null,
                    songIds = members(list),
                    isEditable = true,
                    description = list.text("description"),
                    createdAt = list.long("createdAt"),
                    updatedAt = list.long("updatedAt"),
                )
        }
        library.objects("albums").forEach { album ->
            albums += album.bookmark("title", album.long("savedAt") ?: now)
        }
    }
    read(PRISM_REMOTE_LIBRARY)?.let { library ->
        library.objects("playlists").filter { it.text("kind") == "playlist" && !it.boolean("isMix") }.forEach { item ->
            val id =
                item
                    .getValue("id")
                    .jsonPrimitive.content
                    .removePrefix("VL")
            if (id.isBlank() || id in setOf("LM", "SE")) return@forEach
            val pageName =
                item
                    .getValue("id")
                    .jsonPrimitive.content
                    .replace(Regex("[^A-Za-z0-9_-]"), "_")
            val page = read("files/offline/pages/$pageName.json")?.takeIf { !it.boolean("album") }
            playlists +=
                ForeignPlaylist(
                    name = item.getValue("title").jsonPrimitive.content,
                    browseId = id,
                    songIds = page?.let(::members).orEmpty(),
                    isEditable = page?.boolean("owned") == true,
                    description = page?.text("description"),
                    thumbnailUrl = item.text("thumbnail"),
                )
        }
        library.objects("artists").filter { it.text("kind") == "artist" }.forEach { artists += it.bookmark("title", now) }
        library.objects("albums").filter { it.text("kind") == "album" }.forEach { albums += it.bookmark("title", now) }
    }
    file(PRISM_ARTIST_PREFERENCES).takeIf(File::isFile)?.let { preferences ->
        val favorites = mutableListOf<ForeignBookmark>()
        val xml = preferences.readText()
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) throw UnsupportedForeignLibraryException()
        try {
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isXIncludeAware = false
                    isExpandEntityReferences = false
                }
            val builder =
                factory.newDocumentBuilder().apply {
                    setEntityResolver { _, _ -> throw UnsupportedForeignLibraryException() }
                }
            val document = builder.parse(InputSource(StringReader(xml)))
            val strings = document.getElementsByTagName("string")
            for (index in 0 until strings.length) {
                val element = strings.item(index) as? Element ?: continue
                if (element.getAttribute("name") != "favorites") continue
                Json.parseToJsonElement(element.textContent).jsonArray.forEach { value ->
                    val artist = value.jsonObject
                    favorites.add(0, artist.bookmark("name", artist.long("at")?.takeIf { it > 0 } ?: now))
                }
            }
        } catch (_: Exception) {
            favorites.clear()
        }
        artists.addAll(0, favorites)
    }
    return copy(songs = songs.values.toList(), playlists = playlists, artists = artists, albums = albums)
}

private fun JsonObject.prismSong(): ForeignSong {
    val id = getValue("id").jsonPrimitive.content
    val album = get("album") as? JsonObject
    return ForeignSong(
        id = id,
        title = getValue("title").jsonPrimitive.content,
        artists = get("artists")?.let { prismArtists(it.toString()) }.orEmpty(),
        albumId = album?.text("id"),
        albumName = album?.text("title"),
        durationSeconds = (get("durationSec") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
        thumbnailUrl = text("thumbnail"),
        explicit = boolean("explicit"),
        isLocal = id.startsWith("local:"),
        isLive = boolean("isLive"),
        isVideo = boolean("isVideo"),
        isUploaded = boolean("isUpload"),
        isEpisode = boolean("isEpisode"),
    )
}

private fun JsonObject.bookmark(
    name: String,
    timestamp: Long,
) = ForeignBookmark(getValue("id").jsonPrimitive.content, getValue(name).jsonPrimitive.content, text("thumbnail"), timestamp)

private fun JsonObject.objects(key: String): List<JsonObject> = get(key)?.jsonArray.orEmpty().map { it.jsonObject }

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true

// ---- Small helpers ----

private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

private fun isYouTubeVideoId(id: String): Boolean = YOUTUBE_VIDEO_ID.matches(id)

private fun parseDurationText(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size !in 1..3) return null
    return parts
        .fold(0) { total, part -> total * 60 + (part.toIntOrNull()?.takeIf { it >= 0 } ?: return null) }
        .takeIf { it > 0 }
}

private fun String.youtubeChannelId(): String? = takeIf { it.startsWith("UC") }

private fun jsonStrings(text: String?): List<String?> =
    text
        ?.let {
            runCatching {
                Json.parseToJsonElement(it).jsonArray.map { element ->
                    (element as? JsonPrimitive)?.contentOrNull
                }
            }.getOrNull()
        }.orEmpty()

/** Epoch-millis (UTC) to the LocalDateTime Room stores, matching Converters. */
private fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
