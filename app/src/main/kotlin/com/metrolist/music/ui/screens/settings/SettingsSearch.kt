/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Settings search, adapted from Metrolist's shared SettingsSearch (KMP).
 * Metrolist's index is its settings registry (every page + item with its
 * real title, description, group and choices); the same index is built here
 * from Meowsic's real settings items (see SettingsSearchIndex.kt) — the
 * item text IS the index, and tapping a result opens the page and scrolls
 * to + highlights the item (Metrolist's highlightedItemId), falling back
 * to the item's group when the item is hidden by a condition. The field,
 * result rows, yellow match highlight and empty state follow Metrolist.
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import kotlin.math.roundToInt

/**
 * One searchable settings destination.
 *
 * This mirrors Metrolist's SettingsItemRegistration / SettingsSearchResult:
 * an item result carries the real item (its title + description + group +
 * choices are the search text, exactly like Metrolist's
 * settingsSearchResults), a page result has [itemId] null.
 * [dialog] names a dialog on the target screen that must be opened on entry
 * (items that live inside a dialog, e.g. the word-synced toggle inside
 * Content's "Provider selection" dialog).
 * [pageTitle] is shown as a third line on item results so the user sees
 * which screen the result opens.
 */
data class SettingsSearchEntry(
    val title: String,
    val subtitle: String?,
    val keywords: List<String> = emptyList(),
    val group: String? = null,
    val choices: List<String> = emptyList(),
    val icon: Painter,
    val onClick: () -> Unit,
    val itemId: String? = null,
    val dialog: String? = null,
    val pageTitle: String? = null,
)

/**
 * Case-insensitive contains match on title, description, group title,
 * choice labels and page search terms — the same rule as Metrolist's
 * settingsSearchResults. The real item text IS the index; nothing here is
 * a hand-written keyword except page-level search terms.
 */
fun searchSettingsEntries(
    entries: List<SettingsSearchEntry>,
    query: String,
): List<SettingsSearchEntry> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return emptyList()
    return entries.filter { entry ->
        entry.title.lowercase().contains(normalized) ||
            entry.subtitle.orEmpty().lowercase().contains(normalized) ||
            entry.group.orEmpty().lowercase().contains(normalized) ||
            entry.choices.any { it.lowercase().contains(normalized) } ||
            entry.keywords.any { it.lowercase().contains(normalized) }
    }
}

/**
 * Item positions for scroll-to-search-result, Metrolist's highlightedItemId
 * adapted to Meowsic's Column + verticalScroll screens (which have no
 * LazyList item indices). An anchor's content offset is its top edge in the
 * window minus the scrolling column's top edge plus the current scroll
 * value, so the recorded value is valid whatever the scroll position was
 * when it was measured.
 */
class SettingsItemOffsets {
    private val offsets = mutableMapOf<String, Int>()

    fun record(
        anchorId: String,
        topInWindow: Float,
        columnTopInWindow: Float,
        scrollValue: Int,
    ) {
        offsets[anchorId] =
            (topInWindow - columnTopInWindow + scrollValue).roundToInt().coerceAtLeast(0)
    }

    fun offset(anchorId: String): Int? = offsets[anchorId]
}

/**
 * After a screen opens with a search target, scroll to the item and leave
 * it highlighted. Falls back to the item's group when the item itself is
 * hidden by a condition (e.g. crossfade duration while crossfade is off) —
 * the group's first row is the toggle that reveals it. No-op when neither
 * anchor was recorded, and no scroll at all when the target lives in a
 * dialog ([openDialog] != null): the dialog opens instead, and scrolling
 * the column behind it would move the wrong content.
 */
@Composable
fun SettingsHighlightEffect(
    scrollState: ScrollState,
    highlightItemId: String?,
    highlightGroupId: String?,
    itemOffsets: SettingsItemOffsets,
    openDialog: String? = null,
) {
    LaunchedEffect(highlightItemId) {
        if (openDialog != null) return@LaunchedEffect
        val id = highlightItemId ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val target = itemOffsets.offset(id)
            ?: highlightGroupId?.let { itemOffsets.offset(it) }
        target?.let { scrollState.animateScrollTo(it) }
    }
}

/** Yellow highlight on the matched substring, verbatim Metrolist. */
fun highlightSearchMatches(
    text: String,
    query: String,
): AnnotatedString {
    val match = query.trim()
    if (match.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var currentIndex = 0
        while (currentIndex < text.length) {
            val matchIndex = text.indexOf(match, startIndex = currentIndex, ignoreCase = true)
            if (matchIndex < 0) {
                append(text.substring(currentIndex))
                break
            }
            append(text.substring(currentIndex, matchIndex))
            withStyle(
                SpanStyle(
                    color = Color(0xFF211A00),
                    background = Color(0xFFFFD54F),
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                append(text.substring(matchIndex, matchIndex + match.length))
            }
            currentIndex = matchIndex + match.length
        }
    }
}

/** Connected card shape for search rows: 20dp outer corners, 5dp inner, Metrolist. */
private fun searchResultShape(first: Boolean, last: Boolean): RoundedCornerShape =
    when {
        first && last -> RoundedCornerShape(20.dp)
        first -> RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 5.dp,
            bottomEnd = 5.dp,
        )

        last -> RoundedCornerShape(
            topStart = 5.dp,
            topEnd = 5.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp,
        )

        else -> RoundedCornerShape(5.dp)
    }

@Composable
fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSearchResultRow(
    entry: SettingsSearchEntry,
    query: String,
    first: Boolean,
    last: Boolean,
) {
    Surface(
        onClick = entry.onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = searchResultShape(first, last),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    highlightSearchMatches(entry.title, query),
                    style = MaterialTheme.typography.bodyLarge,
                )
                entry.subtitle?.let {
                    Text(
                        highlightSearchMatches(it, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Item results name the screen they open, like Metrolist.
                if (entry.itemId != null && entry.pageTitle != null &&
                    entry.pageTitle != entry.subtitle
                ) {
                    Text(
                        highlightSearchMatches(entry.pageTitle, query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingsSearchEmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.search),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.settings_search_no_results),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.settings_search_no_results_desc, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
