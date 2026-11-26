package com.metrolist.music.discord

import android.util.Base64
import com.metrolist.music.BuildConfig
import com.metrolist.music.discord.DiscordDefaults.DISCORD_OAUTH_AUTHORIZE
import com.metrolist.music.discord.DiscordDefaults.DISCORD_OAUTH_TOKEN
import com.metrolist.music.discord.DiscordDefaults.DISCORD_REDIRECT_URI
import com.metrolist.music.discord.DiscordDefaults.DISCORD_SCOPES
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class DiscordAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val scope: String,
)

data class PkcePair(
    val verifier: String,
    val challenge: String,
)

@Serializable
data class DiscordAuthSession(
    val authorizeUrl: String,
    val redirectUri: String,
    val pkceVerifier: String,
    val state: String,
)

sealed class DiscordAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NetworkFailure(
        cause: Throwable,
    ) : DiscordAuthException("Network failure: ${cause.message}", cause)

    class InvalidGrant(
        message: String = "Invalid or expired grant",
    ) : DiscordAuthException(message)

    class StateMismatch : DiscordAuthException("OAuth state mismatch")
}

class DiscordAuth(
    private val httpClient: HttpClient = defaultClient(),
) {
    private val redirectUri: String = DISCORD_REDIRECT_URI

    fun createSession(): DiscordAuthSession {
        val pkce = generatePkcePair()
        val state = generateState()

        val authorizeUrl =
            URLBuilder(DISCORD_OAUTH_AUTHORIZE)
                .apply {
                    parameters.append("client_id", BuildConfig.DISCORD_APP_ID.toString())
                    parameters.append("response_type", "code")
                    parameters.append("redirect_uri", redirectUri)
                    parameters.append("scope", DISCORD_SCOPES)
                    parameters.append("state", state)
                    parameters.append("code_challenge_method", "S256")
                    parameters.append("code_challenge", pkce.challenge)
                }.buildString()

        Timber.tag(TAG).i("createSession: url length=%d, redirectUri=%s", authorizeUrl.length, redirectUri)

        return DiscordAuthSession(
            authorizeUrl = authorizeUrl,
            redirectUri = redirectUri,
            pkceVerifier = pkce.verifier,
            state = state,
        )
    }

    suspend fun exchangeCode(
        code: String,
        verifier: String,
        state: String,
        expectedState: String,
    ): DiscordAuthResult {
        if (state != expectedState) {
            Timber.tag(TAG).w("exchangeCode: state mismatch")
            throw DiscordAuthException.StateMismatch()
        }

        return performTokenExchange(
            grantType = "authorization_code",
            extraParams =
                mapOf(
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "code_verifier" to verifier,
                ),
        )
    }

    suspend fun refresh(refreshToken: String): DiscordAuthResult =
        performTokenExchange(
            grantType = "refresh_token",
            extraParams = mapOf("refresh_token" to refreshToken),
        )

    private suspend fun performTokenExchange(
        grantType: String,
        extraParams: Map<String, String>,
    ): DiscordAuthResult {
        try {
            val response: HttpResponse =
                httpClient.submitForm(
                    url = DISCORD_OAUTH_TOKEN,
                    formParameters =
                        parameters {
                            append("client_id", BuildConfig.DISCORD_APP_ID.toString())
                            append("grant_type", grantType)
                            extraParams.forEach { (name, value) -> append(name, value) }
                        },
                )

            val body = response.bodyAsText()
            if (response.status.value in 200..299) {
                val json = Json.parseToJsonElement(body).jsonObject
                return DiscordAuthResult(
                    accessToken = json["access_token"]?.jsonPrimitive?.content ?: "",
                    refreshToken = json["refresh_token"]?.jsonPrimitive?.content ?: "",
                    expiresInSec = json["expires_in"]?.jsonPrimitive?.long ?: 0L,
                    scope = json["scope"]?.jsonPrimitive?.content ?: DISCORD_SCOPES,
                )
            }

            val errorCode =
                runCatching {
                    Json
                        .parseToJsonElement(body)
                        .jsonObject["error"]
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull()
            if (response.status == HttpStatusCode.BadRequest && errorCode == "invalid_grant") {
                Timber.tag(TAG).w("token exchange: invalid_grant on %s", grantType)
                throw DiscordAuthException.InvalidGrant()
            }
            throw DiscordAuthException.NetworkFailure(Exception("HTTP ${response.status.value}: $body"))
        } catch (e: DiscordAuthException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw DiscordAuthException.NetworkFailure(e)
        }
    }

    companion object {
        private const val TAG = "DiscordSvc"

        private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 15_000L
            }
            expectSuccess = false
        }

        fun generatePkcePair(): PkcePair {
            val bytes = ByteArray(64)
            SecureRandom().nextBytes(bytes)
            val verifier = Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            val challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.UTF_8)),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            return PkcePair(verifier = verifier, challenge = challenge)
        }

        private fun generateState(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
        }
    }
}
