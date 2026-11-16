/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Kartu statistik ala Metrolist v14 (StatisticsScreen.kt +
 * ui/statistics/StatisticsCharts.kt + StatisticsChartData.kt +
 * StatisticsFormatter.jvm.kt), diport verbatim ke java.time:
 * kartu Ringkasan (SummaryGrid), heatmap Aktivitas, dan grafik
 * Kebiasaan mendengarkan (per jam). Animasi M3 Expressive diganti
 * tween biasa karena Meowsic memakai M3 standar.
 */

package com.metrolist.music.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Data + formatter (StatisticsChartData.kt + StatisticsFormatter.jvm.kt)
// ---------------------------------------------------------------------------

internal fun listeningCountTicks(maxCount: Int): List<Long> {
    val maximum = maxCount.coerceAtLeast(1).toLong()
    val roughStep = (maximum + 3) / 4
    var magnitude = 1L
    while (magnitude * 10 <= roughStep) magnitude *= 10
    val step = listOf(1L, 2L, 5L, 10L).first { it * magnitude >= roughStep } * magnitude
    val intervals = (maximum + step - 1) / step
    return (0..intervals).map { it * step }
}

internal fun listeningBarFraction(
    count: Int,
    ceiling: Long,
    minimumFraction: Float,
): Float = if (count <= 0) 0f else (count.toFloat() / ceiling.coerceAtLeast(1)).coerceIn(minimumFraction.coerceIn(0f, 1f), 1f)

internal fun listeningCountBuckets(maxCount: Int): List<IntRange> {
    val maximum = maxCount.coerceAtLeast(0)
    val bucketCount = maximum.coerceAtMost(4)
    return listOf(0..0) +
        (0 until bucketCount).map { index ->
            val start = (maximum.toLong() * index / bucketCount + 1).toInt()
            val end = (maximum.toLong() * (index + 1) / bucketCount).toInt()
            start..end
        }
}

internal data class StatisticsDay(
    val date: LocalDate,
    val count: Int,
)

internal fun listeningActivityWeeks(
    today: LocalDate,
    counts: Map<String, Int>,
    firstDate: LocalDate? = null,
    showFullRange: Boolean = false,
): List<List<StatisticsDay?>> {
    val start =
        if (showFullRange && firstDate != null) {
            firstDate.minusDays((firstDate.dayOfWeek.value - 1).toLong())
        } else {
            today.minusDays((today.dayOfWeek.value - 1 + 14 * 7).toLong())
        }
    val dayCount = ChronoUnit.DAYS.between(start, today).toInt() + 7 - (today.dayOfWeek.value - 1)
    return (0 until dayCount)
        .map { offset ->
            val date = start.plusDays(offset.toLong())
            if (date <= today &&
                (firstDate == null || date >= firstDate)
            ) {
                StatisticsDay(date, counts[date.toString()]?.coerceAtLeast(0) ?: 0)
            } else {
                null
            }
        }.chunked(7)
        .dropWhile { week -> week.all { it == null } }
}

/** Format angka/tanggal/jam mengikuti locale perangkat, ala v14. */
internal class StatisticsFormatter(
    languageTag: String? = null,
) {
    private val locale = languageTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()
    private val numbers = NumberFormat.getIntegerInstance(locale)
    private val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale))
    private val hours = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale))

    fun number(value: Long): String = numbers.format(value)

    fun date(value: LocalDate): String = dates.format(value)

    fun weekday(value: LocalDate): String = value.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    fun month(value: LocalDate): String = value.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)

    fun hour(value: Int): String = hours.format(LocalTime.of(value, 0))
}

// ---------------------------------------------------------------------------
// Kartu Ringkasan (StatisticsScreen.SummaryGrid)
// ---------------------------------------------------------------------------

@Composable
internal fun StatisticsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .semantics {
                        heading()
                    },
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
internal fun SummaryGrid(
    totalPlays: Int,
    totalTimeMs: Long,
    artistCount: Int,
    albumCount: Int,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(totalPlays, totalTimeMs, artistCount, albumCount) {
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        )
    }
    val progress = anim.value

    val animatedTracks = (totalPlays * progress).roundToInt()
    val animatedTimeMs = (totalTimeMs * progress).toLong()
    val animatedArtists = (artistCount * progress).roundToInt()
    val animatedAlbums = (albumCount * progress).roundToInt()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(stringResource(R.string.stats_share_plays), animatedTracks.toString(), Modifier.weight(1f))
            SummaryCard(
                stringResource(R.string.stats_share_time),
                if (hasMeasuredPlayTime(totalTimeMs)) {
                    statisticsDuration(animatedTimeMs)
                } else {
                    stringResource(R.string.statistics_no_data)
                },
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(stringResource(R.string.artists), animatedArtists.toString(), Modifier.weight(1f))
            SummaryCard(stringResource(R.string.albums), animatedAlbums.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * Statistik hanya tahu durasi putar kalau aplikasi ini yang mengukurnya.
 * Total nol berarti tidak ada yang terukur, bukan tidak ada yang didengarkan.
 */
internal fun hasMeasuredPlayTime(playTimeMs: Long): Boolean = playTimeMs > 0

@Composable
internal fun statisticsDuration(milliseconds: Long): String {
    val hours = milliseconds / 3_600_000
    val minutes = (milliseconds % 3_600_000) / 60_000
    return if (hours > 0) {
        stringResource(R.string.statistics_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.statistics_duration_minutes, minutes)
    }
}

// ---------------------------------------------------------------------------
// Heatmap Aktivitas + grafik Kebiasaan mendengarkan (StatisticsCharts.kt)
// ---------------------------------------------------------------------------

@Composable
internal fun ActivityHeatmap(
    dailyStats: Map<String, Int>,
    firstDate: LocalDate?,
    today: LocalDate,
) {
    val formatter = remember { StatisticsFormatter() }
    val weeks =
        remember(today, dailyStats, firstDate) {
            listeningActivityWeeks(today, dailyStats, firstDate, showFullRange = firstDate != null)
        }
    val days = remember(weeks) { weeks.flatten().filterNotNull() }
    val buckets = remember(days) { listeningCountBuckets(days.maxOf { it.count }) }
    var selectedDate by remember(today, dailyStats, firstDate) { mutableStateOf(today) }
    val selectedDay = days.first { it.date == selectedDate }
    val scroll = rememberScrollState()
    val labelHeight =
        with(LocalDensity.current) {
            MaterialTheme.typography.labelMedium.lineHeight
                .toDp()
        }
    val monthHeaderHeight = labelHeight + 8.dp
    val minimumCellSize = 28.dp.coerceAtLeast(labelHeight + 4.dp)
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekdayLabels = (0..6).map { formatter.weekday(monday.plusDays(it.toLong())) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val weekdayWidth =
        with(LocalDensity.current) {
            weekdayLabels.maxOf { textMeasurer.measure(it, labelStyle).size.width }.toDp()
        }
    val monthLabels =
        remember(weeks) {
            weeks.mapIndexedNotNull { index, week ->
                val day =
                    week.filterNotNull().firstOrNull { it.date.dayOfMonth == 1 }
                        ?: week.filterNotNull().firstOrNull()?.takeIf { index == 0 }
                day?.let { index to it.date }
            }
        }
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartDetail(formatter.date(selectedDate), selectedDay.count, formatter)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Round down once so individually rounded cells never overflow a fitted grid.
            val fittedCellSize = with(LocalDensity.current) { ((maxWidth - weekdayWidth - 8.dp).toPx() / weeks.size).toInt().toDp() }
            val cellSize = fittedCellSize.coerceIn(minimumCellSize, minimumCellSize.coerceAtLeast(32.dp))
            Row(
                Modifier.width((cellSize * weeks.size + weekdayWidth + 8.dp).coerceAtMost(maxWidth)).align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Spacer(Modifier.height(monthHeaderHeight))
                    weekdayLabels.forEach { label ->
                        Box(Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                    Row {
                        monthLabels.forEachIndexed { index, (weekIndex, date) ->
                            val span = (monthLabels.getOrNull(index + 1)?.first ?: weeks.size) - weekIndex
                            Box(
                                Modifier.width(cellSize * span).height(monthHeaderHeight).padding(start = 4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(formatter.month(date), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Row {
                        weeks.forEach { week ->
                            Column {
                                week.forEach { day ->
                                    if (day == null) {
                                        Spacer(Modifier.size(cellSize))
                                    } else {
                                        HeatmapCell(day, cellSize, day.date == selectedDate, buckets, formatter) { selectedDate = day.date }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ChartScrollIndicator(scroll)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.statistics_chart_less), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.statistics_chart_more), style = MaterialTheme.typography.labelMedium)
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                buckets.forEachIndexed { index, range ->
                    val label =
                        if (range.first == range.last) {
                            formatter.number(range.first.toLong())
                        } else {
                            stringResource(
                                R.string.statistics_chart_range,
                                formatter.number(range.first.toLong()),
                                formatter.number(range.last.toLong()),
                            )
                        }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(activityColor(index, buckets.lastIndex)))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: StatisticsDay,
    cellSize: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    buckets: List<IntRange>,
    formatter: StatisticsFormatter,
    onSelect: () -> Unit,
) {
    val label = remember(day.date, formatter) { formatter.date(day.date) }
    val description = chartDescription(label, day.count, formatter)
    val corner = animateDpAsState(cellSize * if (selected) 0.4f else 0.12f, tween(300))
    val inset = animateDpAsState(if (selected) 2.dp else 4.dp, tween(300))
    val bucket = buckets.indexOfFirst { day.count in it }
    val color = activityColor(bucket, buckets.lastIndex)
    val outline = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(cellSize).chartSelection(description, selected, onSelect)) {
        val padding = inset.value.toPx()
        val bounds = Size((size.width - padding * 2).coerceAtLeast(0f), (size.height - padding * 2).coerceAtLeast(0f))
        val radius = CornerRadius(corner.value.toPx())
        drawRoundRect(color, Offset(padding, padding), bounds, radius)
        if (selected) drawRoundRect(outline, Offset(padding, padding), bounds, radius, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun activityColor(
    bucket: Int,
    lastBucket: Int,
): Color =
    if (bucket == 0) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, bucket.toFloat() / lastBucket)
    }

@Composable
internal fun HourlyActivityChart(hourlyStats: Map<Int, Int>) {
    val formatter = remember { StatisticsFormatter() }
    val counts = remember(hourlyStats) { (0..23).map { hourlyStats[it]?.coerceAtLeast(0) ?: 0 } }
    val ticks = remember(counts) { listeningCountTicks(counts.max()) }
    var selectedHour by remember(hourlyStats) { mutableIntStateOf(counts.indices.maxBy { counts[it] }) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    val chartHeight = 144.dp
    val tickLabelHeight =
        with(LocalDensity.current) {
            MaterialTheme.typography.labelSmall.lineHeight
                .toDp()
        }
    val scroll = rememberScrollState()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartDetail(formatter.hour(selectedHour), counts[selectedHour], formatter)
        Text(
            stringResource(R.string.statistics_chart_listens),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Column(
                    Modifier.height(chartHeight + tickLabelHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    ticks.asReversed().forEach { tick ->
                        Text(
                            formatter.number(tick),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val plotWidth = maxWidth.coerceAtLeast(48.dp * 24)
                val slotWidthPx = with(LocalDensity.current) { (plotWidth / 24).toPx() }
                LaunchedEffect(hourlyStats, scroll.viewportSize, slotWidthPx) {
                    scroll.scrollTo((slotWidthPx * (selectedHour + 0.5f) - scroll.viewportSize / 2f).toInt().coerceAtLeast(0))
                }
                Column(Modifier.horizontalScroll(scroll).width(plotWidth)) {
                    Box(Modifier.fillMaxWidth().padding(top = tickLabelHeight / 2).height(chartHeight)) {
                        Canvas(Modifier.fillMaxSize()) {
                            ticks.forEach { tick ->
                                val y = size.height * (1f - tick.toFloat() / ticks.last())
                                drawLine(if (tick == 0L) axisColor else gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            }
                            val axisX = if (isRtl) size.width else 0f
                            drawLine(axisColor, Offset(axisX, 0f), Offset(axisX, size.height), 1.dp.toPx())
                        }
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                            counts.forEachIndexed { hour, count ->
                                HourlyBar(
                                    hour,
                                    count,
                                    hour == selectedHour,
                                    ticks.last(),
                                    chartHeight,
                                    formatter,
                                    Modifier.weight(1f).fillMaxHeight(),
                                ) { selectedHour = hour }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        (0..23).forEach { hour ->
                            Box(
                                Modifier.weight(1f).height(28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (hour == selectedHour) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 1.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            formatter.number(hour.toLong()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                } else if (hour % 3 == 0 || hour == 23) {
                                    Text(formatter.number(hour.toLong()), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        ChartScrollIndicator(scroll)
        Text(
            stringResource(R.string.statistics_chart_hour),
            Modifier.align(Alignment.End),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartDetail(
    label: String,
    count: Int,
    formatter: StatisticsFormatter,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatter.number(count.toLong()),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.statistics_chart_listens),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HourlyBar(
    hour: Int,
    count: Int,
    selected: Boolean,
    maximum: Long,
    chartHeight: androidx.compose.ui.unit.Dp,
    formatter: StatisticsFormatter,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val label = remember(hour, formatter) { formatter.hour(hour) }
    val description = chartDescription(label, count, formatter)
    val fraction =
        animateFloatAsState(listeningBarFraction(count, maximum, 4.dp / chartHeight), tween(400))
    val color =
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            tween(200),
        )
    val width = animateFloatAsState(if (selected) 0.7f else 0.55f, tween(300))
    val emptyColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Canvas(modifier.chartSelection(description, selected, onSelect)) {
        if (count > 0) {
            val barWidth = size.width * width.value.coerceIn(0f, 1f)
            val barHeight = size.height * fraction.value.coerceIn(0f, 1f)
            val x = (size.width - barWidth) / 2f
            val y = size.height - barHeight
            val radius = minOf(3.dp.toPx(), barHeight / 2f)
            drawRoundRect(color.value, Offset(x, y), Size(barWidth, barHeight), CornerRadius(radius))
            drawRect(color.value, Offset(x, y + barHeight / 2f), Size(barWidth, barHeight / 2f))
        } else {
            drawRoundRect(
                emptyColor,
                Offset((size.width - 10.dp.toPx()) / 2f, size.height - 2.dp.toPx()),
                Size(10.dp.toPx(), 2.dp.toPx()),
                CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun chartDescription(
    label: String,
    count: Int,
    formatter: StatisticsFormatter,
): String =
    stringResource(
        R.string.statistics_chart_detail,
        label,
        stringResource(R.string.statistics_chart_listens),
        formatter.number(count.toLong()),
    )

@Composable
private fun Modifier.chartSelection(
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            if (interaction is HoverInteraction.Enter || interaction is PressInteraction.Press ||
                interaction is FocusInteraction.Focus
            ) {
                select()
            }
        }
    }
    return clickable(source, indication = null, role = Role.Button, onClick = onSelect)
        .semantics {
            contentDescription = description
            selected = isSelected
        }
}

@Composable
private fun ChartScrollIndicator(scroll: ScrollState) {
    if (scroll.maxValue == 0) return
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumb = MaterialTheme.colorScheme.outline
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val thumbWidth = size.width * scroll.viewportSize / (scroll.viewportSize + scroll.maxValue).toFloat()
        val progress = scroll.value.toFloat() / scroll.maxValue
        val x = (size.width - thumbWidth) * if (isRtl) 1f - progress else progress
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(thumb, topLeft = Offset(x, 0f), size = Size(thumbWidth, size.height), cornerRadius = CornerRadius(size.height / 2))
    }
}
