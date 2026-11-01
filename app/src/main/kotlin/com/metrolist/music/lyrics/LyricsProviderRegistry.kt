/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "Unison" to UnisonLyricsProvider,
        "BetterLyrics" to BetterLyricsProvider,
        "Paxsenix" to PaxsenixLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "KuGou" to KuGouLyricsProvider,
        "LyricsPlus" to LyricsPlusProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTube" to YouTubeLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun getProviderName(provider: LyricsProvider): String? =
        providerMap.entries.find { it.value == provider }?.key

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) {
            return getDefaultProviderOrder()
        }
        val saved = orderString.split(",").map { it.trim() }.filter { it in providerNames }
        // Unison selalu ikut di depan agar provider tanpa API key ini tidak
        // terlempar ke urutan akhir pada instalasi yang sudah menyimpan urutan.
        val front = if ("Unison" !in saved) listOf("Unison") else emptyList()
        return front + saved + getDefaultProviderOrder().filter { it !in saved && it != "Unison" }
    }

    fun serializeProviderOrder(providers: List<String>): String {
        return providers.filter { it in providerNames }.joinToString(",")
    }

    fun getDefaultProviderOrder(): List<String> = listOf(
        "Paxsenix",
        "BetterLyrics",
        "Unison",
        "LyricsPlus",
        "LrcLib",
        "KuGou",
        "YouTubeSubtitle",
        "YouTube",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> {
        val order = deserializeProviderOrder(orderString)
        return order.mapNotNull { getProviderByName(it) }
    }
}
