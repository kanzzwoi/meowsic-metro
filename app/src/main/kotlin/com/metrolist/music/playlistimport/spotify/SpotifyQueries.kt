/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Query GraphQL Pathfinder Spotify + parser responsnya, port dari spotlib
 * v14 (shared/.../meow/nyx/spotlib/Pathfinder.kt). Hash operasi dan bentuk
 * respons dipertahankan persis seperti v14; pembacaan JSON dibuat aman
 * (nilai yang bukan primitif dibaca sebagai null, bukan melempar).
 */
internal object SpotifyQueries {
    const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
    const val LIBRARY_V3_HASH = "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455"
    const val PLAYLIST_CONTENTS_HASH = "e4b2953f160e58e38ac025d79b5a9b3aceee5c4c716598e9830bfceb69faff5f"
    const val LIBRARY_TRACKS_HASH = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
    const val PROFILE_ATTRIBUTES_HASH = "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0"

    val LIBRARY_FEATURES =
        listOf(
            "LIKED_SONGS",
            "YOUR_EPISODES_V2",
            "PRERELEASES",
            "PRERELEASES_V2",
            "CLIPS",
            "EVENTS",
        )
}

internal data class LibraryPage(
    val playlists: List<SpotifyPlaylistMeta> = emptyList(),
    val folders: List<Pair<String, String>> = emptyList(),
    val likedSongs: SpotifyPlaylistMeta? = null,
    val totalCount: Int = 0,
)

internal fun parseLibraryPage(
    page: JsonObject,
    folderPath: List<String>,
): LibraryPage {
    val library = page.obj("data")?.obj("me")?.obj("libraryV3") ?: return LibraryPage()
    val playlists = ArrayList<SpotifyPlaylistMeta>()
    val folders = ArrayList<Pair<String, String>>()
    var likedSongs: SpotifyPlaylistMeta? = null

    for (entry in library.array("items")) {
        val wrapper = (entry as? JsonObject)?.obj("item") ?: continue
        val data = wrapper.obj("data")
        val type = wrapper.string("__typename").orEmpty()
        val uri = data?.string("uri") ?: wrapper.string("_uri") ?: continue
        val name = data?.string("name") ?: uri
        when (type) {
            "LibraryFolderResponseWrapper" -> {
                folders.add(uri to name)
            }

            "PlaylistResponseWrapper" -> {
                val image =
                    data
                        ?.obj("images")
                        ?.objects("items")
                        ?.firstOrNull()
                        ?.objects("sources")
                        ?.firstOrNull()
                        ?.string("url")
                playlists.add(
                    SpotifyPlaylistMeta(
                        uri = uri,
                        name = name,
                        owner = data?.obj("ownerV2")?.obj("data")?.string("name"),
                        description = data?.string("description").orEmpty(),
                        image = image,
                        folderPath = folderPath,
                    ),
                )
            }

            "LibraryPseudoPlaylistResponseWrapper" -> {
                if (uri == "spotify:collection:tracks") {
                    likedSongs =
                        SpotifyPlaylistMeta(
                            uri = uri,
                            name = name,
                            folderPath = folderPath,
                            source = SpotifyPlaylistSource.LIKED_SONGS,
                            trackCount =
                                data?.int("count") ?: 0,
                        )
                }
            }
        }
    }
    return LibraryPage(
        playlists,
        folders,
        likedSongs,
        library.int("totalCount") ?: playlists.size,
    )
}

internal fun parseTrackItem(
    entry: JsonObject,
    position: Int,
): SpotifyTrackItem {
    val wrapper = entry.obj("itemV2") ?: entry.obj("track") ?: entry
    val node = wrapper.obj("data") ?: wrapper
    val albumNode = node.obj("albumOfTrack")
    val durationRaw = node["duration"]
    val durationMs =
        when (durationRaw) {
            is JsonObject -> durationRaw.long("totalMilliseconds")
            null -> node.obj("trackDuration")?.long("totalMilliseconds")
            else -> (durationRaw as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        }
    val playable =
        node.obj("playability")?.bool("playable")
            ?: node.bool("isPlayable")
    val uri = node.string("uri") ?: wrapper.string("_uri") ?: wrapper.string("uri").orEmpty()
    return SpotifyTrackItem(
        uri = uri,
        name = node.string("name").orEmpty(),
        type = node.string("__typename") ?: "Track",
        artists =
            node
                .obj("artists")
                ?.objects("items")
                ?.mapNotNull { it.obj("profile")?.string("name") }
                .orEmpty(),
        album = albumNode?.string("name"),
        albumUri = albumNode?.string("uri"),
        albumArtists =
            albumNode
                ?.obj("artists")
                ?.objects("items")
                ?.mapNotNull { it.obj("profile")?.string("name") }
                .orEmpty(),
        releaseDate = albumNode?.obj("date")?.string("isoString"),
        durationMs = durationMs,
        trackNumber = node.int("trackNumber"),
        discNumber = node.int("discNumber"),
        explicit = node.obj("contentRating")?.string("label") == "EXPLICIT",
        isLocal = uri.startsWith("spotify:local:"),
        isPlayable = playable,
        addedAt = entry.obj("addedAt")?.string("isoString"),
        addedBy = entry.obj("addedBy")?.obj("data")?.string("name") ?: entry.obj("addedBy")?.obj("data")?.string("username"),
        position = position,
    )
}

internal fun parseLikedSongsPage(page: JsonObject): Pair<List<SpotifyTrackItem>, Int> {
    val tracks =
        page
            .obj("data")
            ?.obj("me")
            ?.obj("library")
            ?.obj("tracks") ?: return emptyList<SpotifyTrackItem>() to 0
    val items =
        tracks.array("items").mapIndexedNotNull { index, item ->
            (item as? JsonObject)?.let { parseTrackItem(it, index + 1) }
        }
    return items to (tracks.int("totalCount") ?: items.size)
}

internal fun parsePlaylistContentsPage(page: JsonObject): Pair<List<SpotifyTrackItem>, Int> {
    val content = page.obj("data")?.obj("playlistV2")?.obj("content") ?: return emptyList<SpotifyTrackItem>() to 0
    val items =
        content.array("items").mapIndexedNotNull { index, item ->
            (item as? JsonObject)?.let { parseTrackItem(it, index + 1) }
        }
    return items to (content.int("totalCount") ?: items.size)
}

internal fun parseProfile(page: JsonObject): SpotifyAccountProfile {
    val profile = page.obj("data")?.obj("me")?.obj("profile") ?: return SpotifyAccountProfile()
    return SpotifyAccountProfile(
        name = profile.string("name"),
        username = profile.string("username"),
        uri = profile.string("uri"),
        avatar = profile.string("avatar"),
    )
}

private fun JsonObject?.string(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject?.int(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private fun JsonObject?.long(key: String): Long? = (this?.get(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject?.bool(key: String): Boolean? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

private fun JsonObject?.array(key: String): JsonArray = this?.get(key) as? JsonArray ?: JsonArray(emptyList())

private fun JsonObject?.objects(key: String): List<JsonObject> =
    (this?.get(key) as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
