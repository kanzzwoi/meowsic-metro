/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport.providers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.constants.DisableScreenshotKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Jendela login provider, adaptasi verbatim dari Metrolist v14
 * (ProviderLoginWindow + ProviderLoginShell). Pengguna login di situs
 * provider sendiri di dalam WebView; Meowsic TIDAK PERNAH membaca,
 * mencatat, atau menyimpan password — yang diambil hanya cookie/token
 * sesi dari CookieManager setelah login berhasil, persis seperti v14.
 *
 * Judul dan teks error memakai String biasa (bukan resource), karena
 * tugas ini tidak boleh menambah string XML — daftar string yang
 * dipakai ada di laporan, untuk dipindah ke metrolist_strings.xml.
 */

/** Dialog layar penuh untuk login provider (ala v14 ProviderLoginWindow). */
@Composable
fun ProviderLoginWindow(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * Poll cookie sampai kredensial muncul, ala v14
 * (shared PlaylistImportCredentials.awaitProviderCredentials).
 */
private suspend fun <T : Any> awaitProviderCredentials(extract: suspend () -> T?): T {
    while (true) {
        currentCoroutineContext().ensureActive()
        val credentials = extract()
        if (credentials != null) return credentials
        delay(1_000)
    }
}

private val loginScreenshotCount = AtomicInteger(0)

/**
 * Blokir screenshot selama jendela login tampil, ala v14
 * (BlockScreenshots Android). Bedanya dengan v14: saat jendela ditutup,
 * FLAG_SECURE hanya dihapus kalau pengaturan "nonaktifkan screenshot"
 * milik pengguna memang mati — supaya tidak mematikan perlindungan
 * screenshot yang sudah diaktifkan pengguna di MainActivity.
 */
@Composable
private fun BlockScreenshotsDuringLogin() {
    val context = LocalContext.current
    val disableScreenshots by remember(context) {
        context.dataStore.data.map { it[DisableScreenshotKey] ?: false }
    }.collectAsStateWithLifecycle(false)
    val currentDisableScreenshots by rememberUpdatedState(disableScreenshots)
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        if (window != null && loginScreenshotCount.incrementAndGet() == 1) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose {
            if (window != null && loginScreenshotCount.decrementAndGet() == 0 &&
                !currentDisableScreenshots
            ) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

/**
 * Shell login WebView generik, ala v14 ProviderLoginShell: membuka
 * [loginUrl] di WebView, memantau cookie [cookieUrl], dan memanggil
 * [onCredentials] begitu [extractCredentials] berhasil mengekstrak
 * kredensial sesi dari cookie. Navigasi di luar domain tepercaya
 * dibuka di browser luar (bukan di WebView).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
fun <Credentials> ProviderLoginShell(
    title: String,
    loginError: String,
    cookieUrl: String,
    loginUrl: String,
    logTag: String,
    credentialLogMessage: String,
    extractCredentials: (String) -> Credentials?,
    credentialKey: (Credentials) -> String,
    isAuthenticating: Boolean,
    authenticationError: String?,
    onClearAuthenticationError: () -> Unit,
    onCredentials: (Credentials) -> Unit,
    onNavigateBack: () -> Unit,
    onWebViewError: (WebResourceError, WebResourceRequest) -> Unit = { _, _ -> },
    onWebViewDisposed: () -> Unit = {},
) {
    BlockScreenshotsDuringLogin()
    val currentOnCredentials by rememberUpdatedState(onCredentials)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var submittedCredentialKey by remember { mutableStateOf<String?>(null) }

    fun submitCredentialsIfAvailable() {
        val cookieHeader = CookieManager.getInstance().getCookie(cookieUrl) ?: return
        val credentials = extractCredentials(cookieHeader) ?: return
        val key = credentialKey(credentials)
        if (key == submittedCredentialKey) return
        submittedCredentialKey = key
        // Hanya "terdeteksi", tanpa nilai kredensial — jangan pernah log token.
        Timber.tag(logTag).i("$credentialLogMessage detected")
        currentOnCredentials(credentials)
    }

    LaunchedEffect(webView, authenticationError) {
        if (webView == null || authenticationError != null) return@LaunchedEffect
        awaitProviderCredentials {
            extractCredentials(CookieManager.getInstance().getCookie(cookieUrl).orEmpty())
        }
        submitCredentialsIfAvailable()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
            onWebViewDisposed()
        }
    }

    ProviderLoginWindow(onDismissRequest = onNavigateBack) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webViewContext ->
                        WebView(webViewContext).apply webView@{
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
                                        submitCredentialsIfAvailable()
                                    }

                                    override fun onPageFinished(
                                        view: WebView,
                                        url: String?,
                                    ) {
                                        isPageLoading = false
                                        submitCredentialsIfAvailable()
                                    }

                                    override fun onLoadResource(
                                        view: WebView?,
                                        url: String?,
                                    ) {
                                        super.onLoadResource(view, url)
                                        submitCredentialsIfAvailable()
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        if (!request.isForMainFrame) return
                                        onWebViewError(error, request)
                                        isPageLoading = false
                                        pageError = loginError
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        if (!request.isForMainFrame ||
                                            isTrustedLoginUrl(request.url.toString(), loginUrl)
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
                            loadUrl(loginUrl)
                        }
                    },
                )
                if ((isPageLoading || isAuthenticating) && pageError == null &&
                    authenticationError == null
                ) {
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
                                    submittedCredentialKey = null
                                    pageError = null
                                    onClearAuthenticationError()
                                    webView?.reload()
                                },
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text("Retry")
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
}

/**
 * Hanya navigasi https di dalam domain provider (atau host SSO-nya)
 * yang boleh tetap di WebView — ala v14 (LoginWebViewNavigation).
 * Dipakai juga untuk marketplace Amazon (host music.amazon.*).
 */
fun isTrustedLoginUrl(
    value: String,
    loginUrl: String,
): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    if (uri.scheme != "https" || (uri.port != -1 && uri.port != 443) ||
        !uri.userInfo.isNullOrEmpty()
    ) {
        return false
    }
    val provider = runCatching { Uri.parse(loginUrl).host?.lowercase() }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    val providerRoots = listOf("youtube.com", "spotify.com", "deezer.com", "tidal.com", "apple.com")
    val root =
        providerRoots.firstOrNull { provider == it || provider.endsWith(".$it") }
            ?: AmazonMusicImportManager.marketplaceHosts
                .firstOrNull { it == provider }
                ?.removePrefix("music.")
            ?: return false
    if (host == root || host.endsWith(".$root")) return true
    return when (root) {
        "youtube.com" -> {
            host in setOf(
                "accounts.google.com",
                "myaccount.google.com",
                "consent.google.com",
                "www.google.com",
            )
        }
        "spotify.com", "deezer.com", "tidal.com" -> {
            host in setOf(
                "accounts.google.com",
                "appleid.apple.com",
                "account.apple.com",
                "www.facebook.com",
                "m.facebook.com",
            )
        }
        else -> false
    }
}

/** Buka URL login yang tidak tepercaya di browser luar (hanya https). */
fun openExternalLoginUrl(
    context: Context,
    url: String,
) {
    if (runCatching { Uri.parse(url).scheme }.getOrNull() != "https") return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
