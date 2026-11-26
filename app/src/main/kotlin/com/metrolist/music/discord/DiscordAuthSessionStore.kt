package com.metrolist.music.discord

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
private data class PendingDiscordAuth(
    val session: DiscordAuthSession,
    val expiresAt: Long,
)

/**
 * Persists the pending Discord OAuth session (authorize URL + PKCE verifier + state)
 * so login survives the app process being killed while the user is in the browser.
 * Copied from upstream v14 (DiscordAuthSessionStore); the only adaptation is the
 * backing store: Meowsic keeps it in a private SharedPreferences file instead of
 * the shared Settings object. Same 120s expiry, same JSON encoding.
 */
class DiscordAuthSessionStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(session: DiscordAuthSession) {
        prefs.edit {
            putString(
                SESSION_KEY,
                Json.encodeToString(PendingDiscordAuth(session, System.currentTimeMillis() + 120_000L)),
            )
        }
        Timber.tag(TAG).d("pending auth session saved")
    }

    fun pending(): DiscordAuthSession? {
        val raw = prefs.getString(SESSION_KEY, null).takeIf { !it.isNullOrBlank() } ?: return null
        val pending = runCatching { Json.decodeFromString<PendingDiscordAuth>(raw) }.getOrNull()
        if (pending == null || System.currentTimeMillis() >= pending.expiresAt) {
            clear()
            return null
        }
        return pending.session
    }

    fun clear() {
        prefs.edit { remove(SESSION_KEY) }
    }

    companion object {
        private const val TAG = "DiscordSvc"
        private const val PREFS_NAME = "discord_pending_auth"
        private const val SESSION_KEY = "pending_auth_session"
    }
}
