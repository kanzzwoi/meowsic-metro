/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.DefaultDialog
import timber.log.Timber

/**
 * Layar login akun keempat provider, adaptasi dari Metrolist v14
 * (AppleMusicLoginScreen / DeezerLoginScreen / TidalLoginScreen /
 * AmazonMusicLoginScreen): masing-masing hanya membungkus
 * ProviderLoginShell dengan URL + pengekstrak kredensial providernya.
 * Login terjadi di situs provider sendiri; yang ditangkap hanya token/
 * cookie sesi — bukan password.
 *
 * Judul dan teks error memakai String biasa (bukan resource) karena
 * tugas ini tidak boleh menambah string XML — daftarnya ada di laporan.
 */

@Composable
@Suppress("FunctionName")
fun AppleMusicLoginScreen(
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onSessionToken: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    ProviderLoginShell(
        title = stringResource(R.string.import_sign_in_apple_music),
        loginError = stringResource(R.string.import_sign_in_failed_apple_music),
        cookieUrl = "https://music.apple.com",
        loginUrl = "https://music.apple.com",
        logTag = "AppleMusicImport",
        credentialLogMessage = "Apple Music WebView session token",
        extractCredentials = { AppleMusicAccountManager.extractUserToken(it) },
        credentialKey = { it },
        isAuthenticating = isAuthenticating,
        authenticationError = authenticationError,
        onClearAuthenticationError = onClearAuthenticationError,
        onCredentials = onSessionToken,
        onNavigateBack = onNavigateBack,
        onWebViewError = { error, request ->
            Timber.tag("AppleMusicImport").w(
                "Apple Music WebView failed code=%d host=%s",
                error.errorCode,
                request.url.host,
            )
        },
        onWebViewDisposed = { Timber.tag("AppleMusicImport").d("Apple Music login WebView destroyed") },
    )
}

@Composable
@Suppress("FunctionName")
fun DeezerLoginScreen(
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onSessionToken: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    ProviderLoginShell(
        title = stringResource(R.string.import_sign_in_deezer),
        loginError = stringResource(R.string.import_sign_in_failed_deezer),
        cookieUrl = "https://www.deezer.com",
        loginUrl = "https://www.deezer.com/",
        logTag = "DeezerImport",
        credentialLogMessage = "Deezer WebView session token",
        extractCredentials = { DeezerAccountManager.extractArl(it) },
        credentialKey = { it },
        isAuthenticating = isAuthenticating,
        authenticationError = authenticationError,
        onClearAuthenticationError = onClearAuthenticationError,
        onCredentials = onSessionToken,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
@Suppress("FunctionName")
fun TidalLoginScreen(
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onSessionAuthenticated: (sessionId: String, userId: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    ProviderLoginShell(
        title = stringResource(R.string.import_sign_in_tidal),
        loginError = stringResource(R.string.import_sign_in_failed_tidal),
        cookieUrl = "https://listen.tidal.com",
        loginUrl = "https://listen.tidal.com/login",
        logTag = "TidalImport",
        credentialLogMessage = "Tidal WebView session credentials",
        extractCredentials = { TidalAccountManager.extractSessionCredentials(it) },
        credentialKey = { it.first },
        isAuthenticating = isAuthenticating,
        authenticationError = authenticationError,
        onClearAuthenticationError = onClearAuthenticationError,
        onCredentials = { (sessionId, userId) -> onSessionAuthenticated(sessionId, userId) },
        onNavigateBack = onNavigateBack,
    )
}

@Composable
@Suppress("FunctionName")
fun AmazonMusicLoginScreen(
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onSessionToken: (cookies: String, host: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var marketplace by remember { mutableStateOf<String?>(null) }
    val host = marketplace
    if (host == null) {
        AmazonMusicMarketplacePickerDialog(
            onSelect = { marketplace = it },
            onDismiss = onNavigateBack,
        )
        return
    }
    ProviderLoginShell(
        title = stringResource(R.string.import_sign_in_amazon),
        loginError = stringResource(R.string.import_sign_in_failed_amazon),
        cookieUrl = "https://$host",
        loginUrl = "https://$host",
        logTag = "AmazonMusicImport",
        credentialLogMessage = "Amazon Music WebView session cookie",
        extractCredentials = { AmazonMusicImportManager.extractSessionCookie(it) },
        credentialKey = { it },
        isAuthenticating = isAuthenticating,
        authenticationError = authenticationError,
        onClearAuthenticationError = onClearAuthenticationError,
        onCredentials = { onSessionToken(it, host) },
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Pilih marketplace Amazon Music dulu sebelum login, ala v14
 * (AmazonMusicMarketplacePicker).
 */
@Composable
private fun AmazonMusicMarketplacePickerDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.import_choose_marketplace)) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
        ) {
            items(AmazonMusicImportManager.marketplaceHosts.toList()) { host ->
                TextButton(
                    onClick = { onSelect(host) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(host) }
            }
        }
    }
}
