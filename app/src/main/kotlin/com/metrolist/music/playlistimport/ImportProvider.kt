/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport

import com.metrolist.music.R

/** Penyedia impor playlist publik, ala Metrolist v14. Semua tanpa login. */
enum class ImportProvider(
    val route: String,
    val titleRes: Int,
    val hintRes: Int,
    val providerName: String,
) {
    SPOTIFY(
        "spotify",
        R.string.import_spotify,
        R.string.import_playlist_url_hint,
        "Spotify",
    ),
    APPLE_MUSIC(
        "apple_music",
        R.string.import_apple_music,
        R.string.import_playlist_url_hint_apple,
        "Apple Music",
    ),
    DEEZER(
        "deezer",
        R.string.import_deezer,
        R.string.import_playlist_url_hint_deezer,
        "Deezer",
    ),
    TIDAL(
        "tidal",
        R.string.import_tidal,
        R.string.import_playlist_url_hint_tidal,
        "Tidal",
    ),
    AMAZON(
        "amazon",
        R.string.import_amazon,
        R.string.import_playlist_url_hint_amazon,
        "Amazon Music",
    ),
    ;

    suspend fun load(url: String): Result<ImportedPlaylist> =
        when (this) {
            SPOTIFY -> SpotifyPlaylistImporter.loadPlaylist(url)
            APPLE_MUSIC -> AppleMusicPlaylistImporter.loadPlaylist(url)
            DEEZER -> DeezerPlaylistImporter.loadPlaylist(url)
            TIDAL -> TidalPlaylistImporter.loadPlaylist(url)
            AMAZON -> AmazonMusicPlaylistImporter.loadPlaylist(url)
        }

    companion object {
        fun fromRoute(route: String?): ImportProvider =
            values().firstOrNull { it.route == route } ?: SPOTIFY
    }
}
