/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.level.LEVEL_TIERS
import com.metrolist.music.level.LevelTier
import com.metrolist.music.level.expIntoLevel
import com.metrolist.music.level.levelForTotalExp
import com.metrolist.music.level.tierForLevel

/**
 * Banner level di Library (16:9, selebar layar): badge tier, level, nama tier,
 * dan progress EXP ke level berikutnya. Diketuk untuk melihat semua tier.
 */
@Composable
fun LevelBanner(
    totalExp: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val level = levelForTotalExp(totalExp)
    val tier = tierForLevel(level)
    val intoLevel = expIntoLevel(totalExp)
    val expToNext = 100L - intoLevel

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = 1.dp,
                    color = tier.accent.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(24.dp),
                )
                .clickable(onClick = onClick)
                .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Watermark: badge tier diperbesar dan dibuat samar di kanan.
        Image(
            painter = painterResource(tier.badgeRes),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(220.dp),
            alpha = 0.14f,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Badge tier + glow lingkaran warna tier.
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(112.dp)
                        .background(tier.accent.copy(alpha = 0.16f), CircleShape),
            ) {
                Image(
                    painter = painterResource(tier.badgeRes),
                    contentDescription = tier.name,
                    modifier = Modifier.size(104.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.level_banner_level, level),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tier.accent,
                )
                Text(
                    text = tier.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { intoLevel / 100f },
                    color = tier.accent,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text =
                        stringResource(
                            R.string.level_banner_exp_to_next,
                            expToNext,
                            level + 1,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.level_banner_total_exp, totalExp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Daftar semua tier: yang sudah kebuka penuh, yang belum agak redup. */
@Composable
fun LevelTiersDialog(
    totalExp: Long,
    onDismiss: () -> Unit,
) {
    val currentLevel = levelForTotalExp(totalExp)
    val currentTier = tierForLevel(currentLevel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.level_tiers_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = LEVEL_TIERS,
                    key = { it.name },
                ) { tier ->
                    LevelTierRow(
                        tier = tier,
                        isCurrent = tier == currentTier,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun LevelTierRow(
    tier: LevelTier,
    isCurrent: Boolean,
) {
    val range =
        if (tier.maxLevel == null) {
            stringResource(R.string.level_tier_range_open, tier.minLevel)
        } else {
            stringResource(R.string.level_tier_range, tier.minLevel, tier.maxLevel)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isCurrent) tier.accent.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                )
                .border(
                    width = if (isCurrent) 1.dp else 0.dp,
                    color = if (isCurrent) tier.accent else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
    ) {
        Image(
            painter = painterResource(tier.badgeRes),
            contentDescription = tier.name,
            modifier = Modifier.size(56.dp),
            alpha = if (isCurrent) 1f else 0.6f,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tier.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color =
                    if (isCurrent) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = range,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.level_tier_current),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tier.accent,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tier.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
