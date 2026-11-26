/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.widget.Toast
import timber.log.Timber
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsTranslationHelper
import com.metrolist.music.ui.screens.settings.LyricsPosition
import com.metrolist.music.utils.ComposeToImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LyricsTranslationHeader(
    status: LyricsTranslationHelper.TranslationStatus,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = status !is LyricsTranslationHelper.TranslationStatus.Idle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        when (status) {
            is LyricsTranslationHelper.TranslationStatus.Translating -> {
                TranslationCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.ai_translating_lyrics),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            is LyricsTranslationHelper.TranslationStatus.Error -> {
                TranslationCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            is LyricsTranslationHelper.TranslationStatus.Success -> {
                TranslationCard(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_lyrics_translated),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun TranslationCard(
    containerColor: Color,
    contentColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
internal fun LyricsActionOverlay(
    isAutoScrollEnabled: Boolean,
    isSynced: Boolean,
    isSelectionModeActive: Boolean,
    anySelected: Boolean,
    onSyncClick: () -> Unit,
    onCancelSelection: () -> Unit,
    onShareSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = !isAutoScrollEnabled && isSynced && !isSelectionModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            FilledTonalButton(onClick = onSyncClick) {
                Icon(painterResource(R.drawable.sync), stringResource(R.string.auto_scroll), Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auto_scroll))
            }
        }
        
        AnimatedVisibility(
            visible = isSelectionModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onCancelSelection) {
                    Icon(painterResource(R.drawable.close), stringResource(R.string.cancel), Modifier.size(20.dp))
                }
                FilledTonalButton(
                    onClick = onShareSelection,
                    enabled = anySelected
                ) {
                    Icon(painterResource(R.drawable.share), stringResource(R.string.share_selected), Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsShareDialog(
    txt: String,
    title: String,
    arts: String,
    songId: String,
    onDismiss: () -> Unit,
    onShareAsImage: () -> Unit
) {
    val context = LocalContext.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp).fillMaxWidth(0.85f)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.share_lyrics), fontWeight = FontWeight.Normal, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "\"$txt\"\n\n$title - $arts\nhttps://music.youtube.com/watch?v=$songId")
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_lyrics)))
                        onDismiss()
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.share), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.share_as_text), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onShareAsImage()
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.share), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.share_as_image), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onDismiss() }.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsColorPickerDialog(
    txt: String,
    title: String,
    arts: String,
    thumbnailUrl: String?,
    lyricsTextPosition: LyricsPosition,
    onDismiss: () -> Unit,
    onShare: (backgroundColor: Color, textColor: Color, secondaryTextColor: Color, style: LyricsBackgroundStyle, aspectRatio: Float, lyricsSizeScale: Float, lyricsAlignment: Layout.Alignment) -> Unit,
    onDownload: (backgroundColor: Color, textColor: Color, secondaryTextColor: Color, style: LyricsBackgroundStyle, aspectRatio: Float, lyricsSizeScale: Float, lyricsAlignment: Layout.Alignment) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    val pal = remember { mutableStateListOf<Color>() }
    var bgStyle by remember { mutableStateOf(LyricsBackgroundStyle.BLUR) }
    // Rasio kartu: 1:1 (default) atau 4:3 landscape (lebih lebar, 2880x2160).
    var cardAspectRatio by remember { mutableStateOf(1f) }
    // Skala ukuran teks lirik: 1f = normal. User bisa mengecilkan supaya
    // baris panjang muat 1 baris, atau membesarkan.
    var lyricsSizeScale by remember { mutableStateOf(1f) }
    // Perataan lirik di kartu, awalnya ngikutin pengaturan posisi lirik
    // di pemutar; user bisa ubah sendiri di dialog ini.
    var lyricsAlignment by remember {
        mutableStateOf(
            when (lyricsTextPosition) {
                LyricsPosition.LEFT -> Layout.Alignment.ALIGN_NORMAL
                LyricsPosition.CENTER -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_OPPOSITE
            },
        )
    }
    var previewBackgroundColor by remember { mutableStateOf(Color(0xFF242424)) }
    var previewTextColor by remember { mutableStateOf(Color.White) }
    var previewSecondaryTextColor by remember { mutableStateOf(Color.White.copy(alpha = 0.7f)) }
    
    // Preview WYSIWYG: dirender pakai fungsi export yang sama
    // (ComposeToImage.createLyricsImage), cuma ukurannya kecil (720px) supaya
    // cepat. Bitmap lama dipertahankan selama render ulang supaya tidak
    // berkedip tiap ganti warna/gaya.
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(previewBackgroundColor, previewTextColor, previewSecondaryTextColor, bgStyle, cardAspectRatio, lyricsSizeScale, lyricsAlignment, txt, title, arts, thumbnailUrl) {
        try {
            val bmp = ComposeToImage.createLyricsImage(
                context, thumbnailUrl, title, arts, txt,
                (configuration.screenWidthDp * density.density).toInt(),
                (configuration.screenHeightDp * density.density).toInt(),
                previewBackgroundColor.toArgb(), bgStyle,
                previewTextColor.toArgb(), previewSecondaryTextColor.toArgb(),
                lyricsAlignment,
                outputHeight = 720,
                aspectRatio = cardAspectRatio,
                lyricsSizeScale = lyricsSizeScale,
            )
            previewBitmap = bmp.asImageBitmap()
        } catch (e: Exception) {
            Timber.e(e, "Failed to render lyrics preview")
        }
    }
    
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    val res = ImageLoader(context).execute(ImageRequest.Builder(context).data(thumbnailUrl).allowHardware(false).build())
                    val bmp = res.image?.toBitmap()
                    if (bmp != null) {
                        val swatches = Palette.from(bmp).generate().swatches.sortedByDescending { it.population }
                        pal.clear()
                        pal.addAll(swatches.map { Color(it.rgb) }.filter { 
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(it.toArgb(), hsv)
                            hsv[1] > 0.2f
                        }.take(5))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to extract palette colors")
                }
            }
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                Text(stringResource(R.string.customize_colors), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))

                Box(Modifier.fillMaxWidth().aspectRatio(cardAspectRatio).padding(8.dp).clip(RoundedCornerShape(12.dp))) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(previewBackgroundColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(stringResource(R.string.player_background_style), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    LyricsBackgroundStyle.entries.forEachIndexed { index, style ->
                        val label = when (style) {
                            LyricsBackgroundStyle.SOLID -> stringResource(R.string.player_background_solid)
                            LyricsBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                            else -> stringResource(R.string.gradient)
                        }
                        SegmentedButton(
                            selected = bgStyle == style,
                            onClick = { bgStyle = style },
                            shape = SegmentedButtonDefaults.itemShape(index, LyricsBackgroundStyle.entries.size),
                            label = { Text(label) },
                        )
                    }
                }

                Text(stringResource(R.string.lyrics_card_size), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    listOf(1f to "1:1", 4f / 3f to "4:3").forEach { (ratio, label) ->
                        FilterChip(selected = cardAspectRatio == ratio, onClick = { cardAspectRatio = ratio }, label = { Text(label) })
                    }
                }

                Text(stringResource(R.string.lyrics_card_text_size) + " ${(lyricsSizeScale * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = lyricsSizeScale,
                    onValueChange = { lyricsSizeScale = it },
                    valueRange = 0.7f..1.3f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )

                Text(stringResource(R.string.lyrics_text_align), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    listOf(
                        Layout.Alignment.ALIGN_NORMAL to stringResource(R.string.lyrics_align_left),
                        Layout.Alignment.ALIGN_CENTER to stringResource(R.string.lyrics_align_center),
                        Layout.Alignment.ALIGN_OPPOSITE to stringResource(R.string.lyrics_align_right),
                    ).forEach { (align, label) ->
                        FilterChip(selected = lyricsAlignment == align, onClick = { lyricsAlignment = align }, label = { Text(label) })
                    }
                }

                Spacer(Modifier.height(18.dp))
                
                Text(stringResource(R.string.background_color), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    (pal + listOf(Color(0xFF242424), Color(0xFF121212), Color.White, Color.Black, Color(0xFFF5F5F5))).distinct().take(8).forEach { color ->
                        Box(Modifier.size(32.dp).background(color, RoundedCornerShape(8.dp)).clickable { previewBackgroundColor = color }.border(2.dp, if (previewBackgroundColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)))
                    }
                }
                
                Text(stringResource(R.string.text_color), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    (pal + listOf(Color.White, Color.Black, Color(0xFF1DB954))).distinct().take(8).forEach { color ->
                        Box(Modifier.size(32.dp).background(color, RoundedCornerShape(8.dp)).clickable { previewTextColor = color }.border(2.dp, if (previewTextColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)))
                    }
                }
                
                Text(stringResource(R.string.secondary_text_color), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    (pal.map { it.copy(alpha = 0.7f) } + listOf(Color.White.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.7f), Color(0xFF1DB954))).distinct().take(8).forEach { color ->
                        Box(Modifier.size(32.dp).background(color, RoundedCornerShape(8.dp)).clickable { previewSecondaryTextColor = color }.border(2.dp, if (previewSecondaryTextColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)))
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(onClick = {
                    onShare(previewBackgroundColor, previewTextColor, previewSecondaryTextColor, bgStyle, cardAspectRatio, lyricsSizeScale, lyricsAlignment)
                }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share))
                }

                // Simpan kartu lirik ke galeri (tanpa membuka dialog bagikan).
                OutlinedButton(
                    onClick = {
                        onDownload(previewBackgroundColor, previewTextColor, previewSecondaryTextColor, bgStyle, cardAspectRatio, lyricsSizeScale, lyricsAlignment)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.download))
                }
            }
        }
    }
}

// Helper for coroutine scope
typealias CoroutineScope = kotlinx.coroutines.CoroutineScope
