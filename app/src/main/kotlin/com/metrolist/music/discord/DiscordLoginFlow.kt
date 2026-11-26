package com.metrolist.music.discord

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Android Discord login flow, copied from upstream v14 (DiscordLoginFlow).
 * The old flow waited in-memory for the redirect (DiscordOAuthActivity + a
 * 120s await); it broke whenever the app process died while the user was in
 * the browser, because the PKCE verifier only lived in memory. This flow
 * persists the session first (DiscordAuthSessionStore) and completes the
 * exchange from the stored session when the redirect comes back, even in a
 * fresh process.
 */
object DiscordLoginFlow {
    private const val TAG = "DiscordSvc"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var store: DiscordAuthSessionStore? = null

    fun init(context: Context) {
        if (store == null) {
            synchronized(this) {
                if (store == null) store = DiscordAuthSessionStore(context)
            }
        }
    }

    private fun sessions(context: Context? = null): DiscordAuthSessionStore {
        store?.let { return it }
        val ctx = context ?: throw IllegalStateException("DiscordLoginFlow.init() must be called first")
        init(ctx)
        return store!!
    }

    var exchanging by mutableStateOf(false)
        private set

    val isActive get() = store?.pending() != null || exchanging

    fun start(session: DiscordAuthSession, context: Context) {
        sessions(context).save(session)
    }

    fun clear() {
        store?.clear()
    }

    fun handleCallback(intent: Intent, context: Context): Boolean {
        val data = intent.data ?: return false
        if (data.scheme != "meowsic-auth" || data.host != "discord") return false
        val session = sessions(context).pending() ?: return true
        val redirect = session.redirectUri.toUri()
        if (data.scheme != redirect.scheme || data.authority != redirect.authority || data.path != redirect.path) return true
        if (data.getQueryParameter("state") != session.state) return true
        clear()
        if (data.getQueryParameter("error") != null) {
            DiscordRpcManager.setError("discord_error_denied")
            return true
        }
        val code = data.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            DiscordRpcManager.setError("discord_error_token_exchange_failed")
            return true
        }
        exchanging = true
        scope.launch {
            try {
                // OAuth can return to a new process while presence is still disabled.
                DiscordRpcManager.init(context)
                val result =
                    withContext(Dispatchers.IO) {
                        DiscordRpcManager.auth.exchangeCode(code, session.pkceVerifier, session.state, session.state)
                    }
                DiscordRpcManager.storeAndConnect(result.accessToken, result.refreshToken, result.expiresInSec)
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "DiscordLoginFlow: token exchange failed")
                DiscordRpcManager.setError("discord_error_token_exchange_failed")
            } finally {
                exchanging = false
            }
        }
        return true
    }
}
