/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.constants.SlimNavBarHeight
import com.metrolist.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
    glass: Boolean = false,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    // Nav bar memakai NavigationBar bawaan Material3 (bukan komponen kustom),
    // sesuai panduan Android: ukuran, padding, dan indikator aktif mengikuti
    // standar platform. Efek kaca hanya diterapkan pada containerColor dan
    // pembungkus pil, sehingga tata letak tidak melenceng.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        GlassNavBarContainer(glass = glass, pureBlack = pureBlack, slimNav = slimNav) {
            NavigationBar(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth().height(if (slimNav) SlimNavBarHeight else NavigationBarHeight),
            ) {
                navigationItems.forEach { screen ->
                    val isSelected = remember(currentRoute, screen.route) {
                        isRouteSelected(currentRoute, screen.route, navigationItems)
                    }
                    val currentIsSelected by rememberUpdatedState(isSelected)
                    val iconRes = remember(isSelected, screen) {
                        if (isSelected) screen.iconIdActive else screen.iconIdInactive
                    }

                    val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                    val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
                    val interactionSource = remember { MutableInteractionSource() }

                    if (isSearchItem || isHomeHoldItem) {
                        LaunchedEffect(interactionSource) {
                            var isLongClick = false
                            interactionSource.interactions.collectLatest { interaction ->
                                when (interaction) {
                                    is PressInteraction.Press -> {
                                        isLongClick = false
                                        delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                        isLongClick = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                                    }
                                    is PressInteraction.Release -> {
                                        if (!isLongClick) onItemClick(screen, currentIsSelected)
                                    }
                                    is PressInteraction.Cancel -> isLongClick = false
                                }
                            }
                        }
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (!isSearchItem && !isHomeHoldItem) {
                                onItemClick(screen, currentIsSelected)
                            }
                        },
                        interactionSource = interactionSource,
                        // Indikator aktif mengikuti peran warna tema (R2.4),
                        // diberi transparansi agar tetap terlihat di atas kaca.
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                unselectedIconColor = contentColor,
                                unselectedTextColor = contentColor,
                            ),
                        icon = {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = stringResource(screen.titleId),
                            )
                        },
                        label =
                            if (!slimNav) {
                                {
                                    Text(
                                        text = stringResource(screen.titleId),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }
}

/**
 * Wadah nav bar bergaya kaca (liquid glass).
 *
 * Saat [glass] false, tampil seperti sebelumnya: persegi penuh dengan warna
 * permukaan solid, sehingga perilaku lama tetap utuh.
 *
 * Saat [glass] true, kontainer menjadi pil yang mengapung dengan:
 * - latar semi-transparan (frosted tipis, bukan blur konten asli),
 * - border tipis terang di tepi atas sebagai pantulan cahaya,
 * - bayangan lembut agar terlepas dari latar.
 *
 * Sengaja TIDAK memakai blur per-frame agar tidak membebani GPU; kesan kaca
 * dibangun dari transparansi + border + bayangan.
 */
@Composable
private fun GlassNavBarContainer(
    glass: Boolean,
    pureBlack: Boolean,
    slimNav: Boolean,
    content: @Composable () -> Unit,
) {
    if (!glass) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(if (slimNav) SlimNavBarHeight else NavigationBarHeight)) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) { content() }
            }
        }
        return
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassTint =
        if (isDark) {
            Color.Black.copy(alpha = 0.28f)
        } else {
            Color.White.copy(alpha = 0.42f)
        }
    val rimColor =
        if (isDark) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.85f)
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                // Ruang induk = bottomInset + navPadding. Pil harus LEBIH PENDEK
                // dari itu agar terlihat mengapung. Padding inilah yang
                // menciptakan jarak, dan karena Box memakai fillMaxHeight
                // (tinggi sudah pasti), padding tidak menambah tinggi total.
                .padding(horizontal = 40.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(percent = 50), clip = false)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(glassTint)
                    .border(
                        width = 1.dp,
                        color = rimColor,
                        shape = RoundedCornerShape(percent = 50),
                    ),
        ) {
            content()
        }
    }
}
