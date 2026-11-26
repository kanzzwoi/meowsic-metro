package com.metrolist.music.discord

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens a Discord URL, copied from upstream v14 (DiscordUrlOpener.android).
 * For the OAuth authorize URL it first tries the Discord app itself via the
 * discord:// deep link, then falls back to Custom Tabs, then a plain browser.
 */
fun Context.openDiscordUrl(url: String) {
    if (url.startsWith("https://discord.com/oauth2/authorize")) {
        val actionUri = url.replace("https://discord.com", "discord://action").toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, actionUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    } else {
        val discordUri = buildDiscordSchemeUri(url)
        if (discordUri != null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, discordUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    val customTabsPackage = CustomTabsClient.getPackageName(this, null)
    if (customTabsPackage != null) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(this, url.toUri())
        return
    }

    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun buildDiscordSchemeUri(url: String): Uri? =
    when {
        url.startsWith("https://discord.com/") -> url.replace("https://", "discord://").toUri()
        url.startsWith("https://discord.gg/") -> url.replace("https://", "discord://").toUri()
        url.startsWith("https://discord.gift/") -> url.replace("https://", "discord://").toUri()
        url.startsWith("https://discord.new/") -> url.replace("https://", "discord://").toUri()
        else -> null
    }
