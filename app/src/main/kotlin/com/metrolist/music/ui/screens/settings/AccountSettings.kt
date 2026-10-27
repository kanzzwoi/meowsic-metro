/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import timber.log.Timber
import com.metrolist.music.utils.reportException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.UseLoginForBrowse
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.InfoLabel
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import com.metrolist.music.viewmodels.HomeViewModel

/**
 * Kartu aksi besar: ikon di atas, label di bawah. Dipakai untuk baris atas
 * (Login / Masuk dengan token) dan baris bawah (Pengaturan / Join saluran).
 */
@Composable
private fun ProfileActionTile(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Baris toggle berbentuk pill: ikon, label, lalu switch di kanan. */
@Composable
private fun ProfileToggleRow(
    icon: Painter,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Switch(
                enabled = enabled,
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    Icon(
                        painter = painterResource(
                            id = if (checked) R.drawable.check else R.drawable.close,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")
    val (authUser, onAuthUserChange) = rememberPreference(InnerTubeAuthUserKey, "0")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Logout confirmation dialog
        if (showLogoutDialog) {
            DefaultDialog(
                onDismiss = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_dialog_title)) },
                content = {
                    Text(
                        text = stringResource(R.string.logout_dialog_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                },
                buttons = {
                    TextButton(
                        onClick = {
                            Timber.d("[LOGOUT_CLEAR] User chose to clear data")
                            scope.launch {
                                try {
                                    Timber.d("[LOGOUT_CLEAR] Starting clear and logout process")
                                    // Forget account first (stops all sync), then clear data.
                                    // This prevents background syncs from re-adding songs.
                                    accountSettingsViewModel.logoutAndClearLibraryData(context)
                                    Timber.d("[LOGOUT_CLEAR] Library data cleared and account forgotten")
                                } catch (e: Exception) {
                                    Timber.e(e, "[LOGOUT_CLEAR] Error clearing library data, proceeding with logout")
                                    reportException(e)
                                }
                                onInnerTubeCookieChange("")
                                Timber.d("[LOGOUT_CLEAR] Logout complete")
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.logout_clear))
                    }
                    TextButton(
                        onClick = {
                            Timber.d("[LOGOUT_KEEP] User chose to keep data")
                            scope.launch {
                                Timber.d("[LOGOUT_KEEP] Starting logout process (keeping data)")
                                accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                                Timber.d("[LOGOUT_KEEP] Logout complete")
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.logout_keep))
                    }
                }
            )
        }

        if (showTokenEditor) {
            val text = """
                ***INNERTUBE COOKIE*** =$innerTubeCookie
                ***VISITOR DATA*** =$visitorData
                ***DATASYNC ID*** =$dataSyncId
                ***AUTH USER*** =$authUser
                ***ACCOUNT NAME*** =$accountNamePref
                ***ACCOUNT EMAIL*** =$accountEmail
                ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
            """.trimIndent()

            TextFieldDialog(
                initialTextFieldValue = TextFieldValue(text),
                onDone = { data ->
                    var cookie = ""
                    var visitorDataValue = ""
                    var dataSyncIdValue = ""
                    var authUserValue = "0"
                    var accountNameValue = ""
                    var accountEmailValue = ""
                    var accountChannelHandleValue = ""

                    data.split("\n").forEach {
                        when {
                            it.startsWith("***INNERTUBE COOKIE*** =") -> cookie = it.substringAfter("=")
                            it.startsWith("***VISITOR DATA*** =") -> visitorDataValue = it.substringAfter("=")
                            it.startsWith("***DATASYNC ID*** =") -> dataSyncIdValue = it.substringAfter("=")
                            it.startsWith("***AUTH USER*** =") -> authUserValue = it.substringAfter("=")
                            it.startsWith("***ACCOUNT NAME*** =") -> accountNameValue = it.substringAfter("=")
                            it.startsWith("***ACCOUNT EMAIL*** =") -> accountEmailValue = it.substringAfter("=")
                            it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> accountChannelHandleValue = it.substringAfter("=")
                        }
                    }
                    // Write all credentials atomically to DataStore and wait for completion
                    // before restarting, preventing the race condition where the process
                    // would be killed before async DataStore coroutines finished writing.
                    accountSettingsViewModel.saveTokenAndRestart(
                        context = context,
                        cookie = cookie,
                        visitorData = visitorDataValue,
                        dataSyncId = dataSyncIdValue,
                        authUser = authUserValue,
                        accountName = accountNameValue,
                        accountEmail = accountEmailValue,
                        accountChannelHandle = accountChannelHandleValue,
                    )
                },
                onDismiss = { showTokenEditor = false },
                singleLine = false,
                maxLines = 20,
                isInputValid = { fullText ->
                    // Extract the cookie value from the formatted template line,
                    // then validate it separately — avoids the bug where parseCookieString
                    // received the entire multi-line template and failed to find "SAPISID"
                    // as a key because the "***INNERTUBE COOKIE*** =" prefix shadowed it.
                    val cookieLine = fullText.lines()
                        .find { it.startsWith("***INNERTUBE COOKIE*** =") }
                    val cookieValue = cookieLine?.substringAfter("***INNERTUBE COOKIE*** =")?.trim() ?: ""
                    cookieValue.isNotEmpty() && "SAPISID" in parseCookieString(cookieValue)
                },
                extraContent = {
                    Spacer(Modifier.height(8.dp))
                    InfoLabel(text = stringResource(R.string.token_adv_login_description))
                }
            )
        }

        // ---- baris atas: login / masuk dengan token ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoggedIn && accountImageUrl != null) {
                // Sudah login: tampilkan akun aktif sebagai tile pertama.
                ProfileActionTile(
                    icon = painterResource(R.drawable.ic_toolbar_account),
                    label = accountName.ifBlank { stringResource(R.string.account) },
                    onClick = {
                        onClose()
                        navController.navigate("account")
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                ProfileActionTile(
                    icon = painterResource(R.drawable.login),
                    label = stringResource(R.string.login),
                    onClick = {
                        onClose()
                        navController.navigate("login")
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            ProfileActionTile(
                icon = painterResource(R.drawable.token),
                label = stringResource(R.string.advanced_login),
                onClick = {
                    if (!isLoggedIn) showTokenEditor = true
                    else if (!showToken) showToken = true
                    else showTokenEditor = true
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (isLoggedIn) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileActionTile(
                    icon = painterResource(R.drawable.account),
                    label = stringResource(R.string.switch_youtube_channel),
                    onClick = {
                        onClose()
                        navController.navigate("switch_channel")
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- toggle pill ----
        ProfileToggleRow(
            icon = painterResource(R.drawable.cached),
            label = stringResource(R.string.more_content),
            checked = useLoginForBrowse,
            enabled = isLoggedIn,
            onCheckedChange = {
                YouTube.useLoginForBrowse = it
                onUseLoginForBrowseChange(it)
            },
        )

        Spacer(Modifier.height(10.dp))

        ProfileToggleRow(
            icon = painterResource(R.drawable.cached),
            label = stringResource(R.string.yt_sync),
            checked = ytmSync,
            enabled = isLoggedIn,
            onCheckedChange = onYtmSyncChange,
        )

        Spacer(Modifier.height(16.dp))

        // ---- baris bawah: pengaturan / join saluran ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileActionTile(
                icon = painterResource(R.drawable.settings),
                label = stringResource(R.string.settings),
                onClick = {
                    onClose()
                    navController.navigate("settings")
                },
                modifier = Modifier.weight(1f),
            )
            ProfileActionTile(
                icon = painterResource(R.drawable.whatsapp),
                label = stringResource(R.string.join_channel),
                onClick = {
                    onClose()
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://whatsapp.com/channel/0029VamAXeQ2975Go0mfiL2y"))
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // ---- banner update (kalau ada versi baru) ----
        if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
            val releaseInfo = Updater.getCachedLatestRelease()
            val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }
            if (downloadUrl != null) {
                Spacer(Modifier.height(12.dp))
                ProfileActionTile(
                    icon = painterResource(R.drawable.update),
                    label = stringResource(R.string.new_version_available),
                    onClick = { uriHandler.openUri(downloadUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- tombol logout (hanya kalau sudah login) ----
        if (isLoggedIn) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    Timber.d("[LOGOUT] User clicked logout button, showing dialog")
                    showLogoutDialog = true
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_logout))
            }
        }
    }
}
