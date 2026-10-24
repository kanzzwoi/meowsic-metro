/**
 * Meowsic (C) 2026 — AboutScreen kustom Kanzz
 */
package com.metrolist.music.ui.screens.settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain

@Composable
private fun KanzAvatar(
    sizeDp: Int,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    onClick: (() -> Unit)? = null
) {
    val fallback = painterResource(R.drawable.small_icon)
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.size(sizeDp.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
    ) {
        AsyncImage(
            model = R.drawable.kanz_avatar,
            contentDescription = "Kanzz",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = fallback,
            fallback = fallback,
            error = fallback,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
) {
    val uriHandler = LocalUriHandler.current
    val playerConnection = LocalPlayerConnection.current
    val snackbarHostState = remember { SnackbarHostState() }
    val windowInsets = LocalPlayerAwareWindowInsets.current

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top)))
            Spacer(Modifier.height(16.dp))

            // App Header — MEOWSIC
            ElevatedCard(shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Image(
                        painter = painterResource(R.drawable.small_icon),
                        contentDescription = stringResource(R.string.metrolist),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.SrcIn),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("MEOWSIC", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.5).sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
                                Text(BuildConfig.ARCHITECTURE.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            if (BuildConfig.DEBUG) {
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)) {
                                    Text("DEBUG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Kanzz hero
            ElevatedCard(shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        var clickCount by remember { mutableIntStateOf(0) }
                        KanzAvatar(
                            sizeDp = 110,
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            onClick = {
                                clickCount++
                                if (clickCount >= 3) {
                                    clickCount = 0
                                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = "Mh2JWGWvy_Y")))
                                }
                            }
                        )
                        Column(verticalArrangement = Arrangement.Center) {
                            Text("Kanzz", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, lineHeight = 38.sp, letterSpacing = (-0.5).sp)
                            Text("hamba Allah", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 3 socials: website / WA channel / Instagram @kanzgt
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { uriHandler.openUri("https://meowku.my.id") }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(painterResource(R.drawable.language), contentDescription = null)
                        }
                        FilledTonalButton(onClick = { uriHandler.openUri("https://whatsapp.com/channel/0029VamAXeQ2975Go0mfiL2y") }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(painterResource(R.drawable.whatsapp), contentDescription = null)
                        }
                        FilledTonalButton(onClick = { uriHandler.openUri("https://www.instagram.com/kanzgt") }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(painterResource(R.drawable.instagram), contentDescription = null)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "TERIMAKASIH SUDAH BERKUNJUNG",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.stands_with_palestine),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(48.dp))
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)))
    }
}
