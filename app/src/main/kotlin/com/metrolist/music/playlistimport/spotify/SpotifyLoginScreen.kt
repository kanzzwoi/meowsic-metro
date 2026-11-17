/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.spotify

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.metrolist.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Layar login Spotify lewat WebView, port dari v14
 * (android/.../spotifyimport/SpotifyLoginScreen.kt).
 *
 * Cara kerja: WebView membuka halaman login Spotify (accounts.spotify.com).
 * Pengguna mengetik password di halaman Spotify sendiri — Meowsic TIDAK
 * pernah membaca atau menyimpan password. Begitu login berhasil, cookie
 * sesi `sp_dc` muncul di CookieManager; cookie itu diambil dan diteruskan
 * ke [onSessionCookie], lalu dihapus dari WebView (tidak ditinggal di sana).
 * Sesi selanjutnya disimpan sebagai JSON di DataStore (lihat SpotifyAccount).
 *
 * Dipakai sebagai layar penuh lewat route navigasi (mis. "spotify_login"),
 * bukan dialog — v14 membungkusnya dalam ProviderLoginWindow, Meowsic
 * memakai route biasa.
 *
 * @param isAuthenticating true selagi pemanggil menukar cookie jadi sesi
 *  (SpotifyImportManager.authenticate).
 * @param authenticationError pesan galat dari penukaran sesi, atau null.
 * @param onClearAuthenticationError dipanggil saat pengguna menekan Retry.
 * @param onSessionCookie dipanggil sekali dengan nilai sp_dc saat login berhasil.
 * @param onNavigateBack dipanggil saat pengguna menekan kembali / tombol back.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginScreen(
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onSessionCookie: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BlockScreenshots()
    val currentOnSessionCookie by rememberUpdatedState(onSessionCookie)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var submittedCookie by remember { mutableStateOf<String?>(null) }

    fun submitCookieIfAvailable() {
        val cookie = spotifySessionCookie(CookieManager.getInstance().getCookie(SPOTIFY_OPEN_URL)) ?: return
        if (cookie == submittedCookie) return
        submittedCookie = cookie
        // Jangan pernah log nilai cookie.
        Timber.tag(TAG).i("Spotify WebView session cookie detected")
        currentOnSessionCookie(cookie)
        clearSpotifySessionCookie()
    }

    // Polling cookie: cookie bisa muncul tanpa onPageFinished (mis. setelah
    // redirect login), jadi dicek berkala selagi layar ini hidup.
    LaunchedEffect(webView, authenticationError) {
        if (webView == null || authenticationError != null) return@LaunchedEffect
        while (true) {
            delay(500)
            submitCookieIfAvailable()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
            Timber.tag(TAG).d("Spotify login WebView destroyed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.spotify_sign_in)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webViewContext ->
                    WebView(webViewContext).apply webView@ {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        webView = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(this@webView, true)
                        }
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    isPageLoading = true
                                    pageError = null
                                    url?.toUri()?.let { Timber.tag(TAG).d("Spotify WebView loading host=%s path=%s", it.host, it.path) }
                                }

                                override fun onPageFinished(
                                    view: WebView,
                                    url: String?,
                                ) {
                                    isPageLoading = false
                                    url?.toUri()?.let { Timber.tag(TAG).d("Spotify WebView loaded host=%s path=%s", it.host, it.path) }
                                    submitCookieIfAvailable()
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (!request.isForMainFrame) return
                                    Timber.tag(TAG).w(
                                        "Spotify WebView failed code=%d host=%s",
                                        error.errorCode,
                                        request.url.host,
                                    )
                                    isPageLoading = false
                                    scope.launch { pageError = context.getString(R.string.spotify_login_page_failed, error.description) }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    if (!request.isForMainFrame ||
                                        isTrustedLoginUrl(request.url.toString())
                                    ) {
                                        return false
                                    }
                                    openExternalLoginUrl(view.context, request.url.toString())
                                    return true
                                }
                            }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            allowFileAccess = false
                            allowContentAccess = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = WebSettings.getDefaultUserAgent(webViewContext)
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl(SPOTIFY_LOGIN_URL)
                    }
                },
            )

            if ((isPageLoading || isAuthenticating) && pageError == null && authenticationError == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            (authenticationError ?: pageError)?.let { error ->
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = {
                                submittedCookie = null
                                pageError = null
                                onClearAuthenticationError()
                                webView?.reload()
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                        if (authenticationError != null) {
                            Button(
                                onClick = {
                                    submittedCookie = null
                                    pageError = null
                                    onClearAuthenticationError()
                                    clearSpotifySessionCookie()
                                    webView?.loadUrl(SPOTIFY_LOGIN_URL)
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.spotify_sign_in_again))
                            }
                        }
                    }
                }
            }
        }
    }

    BackHandler {
        if (webView?.canGoBack() == true && authenticationError == null && pageError == null) {
            webView?.goBack()
        } else {
            onNavigateBack()
        }
    }
}

/**
 * Hanya URL login Spotify yang boleh dibuka di dalam WebView
 * (accounts.spotify.com + host *.spotify.com). URL lain (mis. login sosial
 * Google/Facebook) dibuka di browser luar.
 */
private fun isTrustedLoginUrl(url: String): Boolean {
    val host = runCatching { url.toUri().host }.getOrNull().orEmpty()
    return host == "accounts.spotify.com" || host.endsWith(".spotify.com")
}

private fun openExternalLoginUrl(
    context: android.content.Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.onFailure {
        Timber.tag(TAG).w(it, "Failed to open external login url")
    }
}

/** Hapus cookie sp_dc dari WebView setelah ditangkap (jangan ditinggal). */
private fun clearSpotifySessionCookie() {
    CookieManager.getInstance().apply {
        setCookie(SPOTIFY_OPEN_URL, "sp_dc=; Max-Age=0; Path=/; Domain=.spotify.com; Secure")
        flush()
    }
    Timber.tag(TAG).i("Cleared stale Spotify WebView session cookie")
}

/** Blokir screenshot/rekaman layar selagi layar login tampil. */
@Composable
private fun BlockScreenshots() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private const val TAG = "SpotifyImport"
private const val SPOTIFY_OPEN_URL = "https://open.spotify.com/"
private const val SPOTIFY_LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
