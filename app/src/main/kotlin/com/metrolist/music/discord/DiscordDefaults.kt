package com.metrolist.music.discord

object DiscordDefaults {
    const val YOUTUBE_WATCH_URL = "https://music.youtube.com/watch?v="

    const val BUTTON1_LABEL = "Listen on YouTube Music"
    const val BUTTON1_URL_TEMPLATE = "https://music.youtube.com/watch?v={song.id}"

    const val BUTTON2_LABEL = "Visit Meowsic"
    const val BUTTON2_URL = "https://github.com/kanzzwoi/meowsic-metro"

    const val STATE_TEMPLATE = "{artist.name}"
    const val DETAILS_TEMPLATE = "{song.name}"

    const val ACTIVITY_TYPE = "2"
    const val ACTIVITY_NAME = ""

    const val USER_STATUS = "online"
    const val STATUS_IDLE = "idle"
    const val STATUS_DND = "dnd"

    const val ACTIVITY_TYPE_LISTENING = "2"
    const val ACTIVITY_TYPE_PLAYING = "0"
    const val ACTIVITY_TYPE_WATCHING = "3"
    const val ACTIVITY_TYPE_COMPETING = "5"

    const val UNKNOWN_ARTIST = "Unknown Artist"
    const val UNKNOWN_ALBUM = "Unknown Album"

    const val DISCORD_OAUTH_AUTHORIZE = "https://discord.com/oauth2/authorize"
    const val DISCORD_OAUTH_TOKEN = "https://discord.com/api/v10/oauth2/token"

    // v14 redirect scheme (was meowsicdiscord://oauth2/callback in the old in-memory flow).
    // Must match the redirect registered in the Discord developer portal exactly.
    const val DISCORD_REDIRECT_URI = "meowsic-auth://discord/callback"

    const val DISCORD_SCOPES = "openid sdk.social_layer_presence"
}
