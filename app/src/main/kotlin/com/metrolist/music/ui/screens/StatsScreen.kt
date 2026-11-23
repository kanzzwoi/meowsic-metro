/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_ARTIST
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.StatPeriod
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ArtistListItem
import com.metrolist.music.ui.component.ChoiceChipsRow
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.TimeTransfer
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.isScrollingUp
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.viewmodels.StatsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val sArtists = viewModel.selectedArtists // SnapshotStateList<Artist>

// Helper actions:
    val toggleArtistSelection: (Artist) -> Unit = { artist ->
        if (sArtists.any { it.id == artist.id }) {
            sArtists.removeAll { it.id == artist.id }
        } else {
            sArtists.add(artist)
        }
    }

    val clearArtistSelection: () -> Unit = {
        sArtists.clear()
    }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver =
                listSaver<MutableList<Long>, Long>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val indexChips by viewModel.indexChips.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val mostPlayedSongsStats by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val mostPlayedArtists by viewModel.filteredArtists.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val allArtists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    // Total ringkasan ala v14 (tanpa filter pilihan artis): jumlah pemutaran
    // + durasi dari semua lagu periode ini, jumlah artis/album berbeda yang
    // diputar. Dipakai kartu Ringkasan.
    val allSongsStats by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val allAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val hourlyStats by viewModel.hourlyStats.collectAsStateWithLifecycle()
    val heatmapRange by viewModel.heatmapRange.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()
    val currentDate = LocalDateTime.now()
    val orderedMostPlayedSongs =
        remember(mostPlayedSongsStats, mostPlayedSongs) {
            val songsById = mostPlayedSongs.associateBy { it.song.id }
            mostPlayedSongsStats.mapNotNull { statsSong -> songsById[statsSong.id] }
        }
    val statsTotalPlays = allSongsStats.sumOf { it.songCountListened }
    val statsTotalTimeMs = allSongsStats.sumOf { it.timeListened ?: 0L }
    val statsArtistCount = allArtists.size
    val statsAlbumCount = allAlbums.size
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()

    // Ukuran ubin carousel ala v14: dihitung dari lebar layar (bukan 128dp
    // tetap), supaya ~3 ubin muat selebar layar HP. Tinggi judul/subjudul
    // dicadangkan seperti v14 supaya baris tidak lompat saat teks pendek.
    val statsTileSize = statisticsCarouselTileSize()
    val statsTitleHeight =
        with(LocalDensity.current) { MaterialTheme.typography.titleSmall.lineHeight.toDp() }
    val statsSubtitleHeight =
        with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }

    // Raport tahunan ala story (lihat RecapScreen): tombol Bagikan
    // membuka Raport Meowsic 2026, bukan lagi dialog bagikan statistik.
    var showRecap by remember { mutableStateOf(false) }

    var showTimeTransfer by rememberSaveable { mutableStateOf(false) }
    var prevOptionOrdinal by rememberSaveable { mutableStateOf<OptionStats?>(null) }
    var prevIndexChips by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(showTimeTransfer) {
        if (showTimeTransfer) {
            if (prevOptionOrdinal == null) prevOptionOrdinal = selectedOption
            if (prevIndexChips == null) prevIndexChips = indexChips
            viewModel.selectedOption.value = OptionStats.CONTINUOUS // "throughout time" in your VM
            viewModel.indexChips.value = StatPeriod.ALL.ordinal // optional: ensure it’s actually “now -> throughout time”
        }
    }

    if (showTimeTransfer) {
        TimeTransfer(
            onDismiss = {
                showTimeTransfer = false
                prevOptionOrdinal?.let { viewModel.selectedOption.value = it }
                prevIndexChips?.let { viewModel.indexChips.value = it }

                // Clear snapshots for the next open
                prevOptionOrdinal = null
                prevIndexChips = null
            },
        )
    }

    val weeklyDates =
        if (currentDate != null && firstEvent != null) {
            generateSequence(currentDate) { it.minusWeeks(1) }
                .takeWhile { it.isAfter(firstEvent?.event?.timestamp?.minusWeeks(1)) }
                .mapIndexed { index, date ->
                    val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")

                    val startDateFormatted = formatter.format(date)
                    val endDateFormatted = formatter.format(endDate)

                    val startMonth = date.month
                    val endMonth = endDate.month
                    val startYear = date.year
                    val endYear = endDate.year

                    val text =
                        when {
                            startYear != currentDate.year -> "$startDateFormatted, $startYear - $endDateFormatted, $endYear"
                            startMonth != endMonth -> "$startDateFormatted - $endDateFormatted"
                            else -> "${date.dayOfMonth} - $endDateFormatted"
                        }
                    Pair(index, text)
                }.toList()
        } else {
            emptyList()
        }

    val monthlyDates =
        if (currentDate != null && firstEvent != null) {
            generateSequence(
                currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1),
            ) { it.minusMonths(1) }
                .takeWhile {
                    it.isAfter(
                        firstEvent
                            ?.event
                            ?.timestamp
                            ?.withDayOfMonth(1),
                    )
                }.mapIndexed { index, date ->
                    val formatter = DateTimeFormatter.ofPattern("MMM")
                    val formattedDate = formatter.format(date)
                    val text =
                        if (date.year != currentDate.year) {
                            "$formattedDate ${date.year}"
                        } else {
                            formattedDate
                        }
                    Pair(index, text)
                }.toList()
        } else {
            emptyList()
        }

    val yearlyDates =
        if (currentDate != null && firstEvent != null) {
            generateSequence(
                currentDate
                    .plusYears(1)
                    .withDayOfYear(1)
                    .minusDays(1),
            ) { it.minusYears(1) }
                .takeWhile {
                    it.isAfter(
                        firstEvent
                            ?.event
                            ?.timestamp,
                    )
                }.mapIndexed { index, date ->
                    Pair(index, "${date.year}")
                }.toList()
        } else {
            emptyList()
        }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .asPaddingValues(),
            modifier =
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                ),
        ) {
            val filteredArtists =
                allArtists
                    .map { artistWrapper ->
                        Artist(
                            id = artistWrapper.artist.id,
                            name = artistWrapper.artist.name,
                        )
                    }.filter { artist ->
                        artist.name.contains(query.text, ignoreCase = true)
                    }

            item(key = "choice_chips") {
                ChoiceChipsRow(
                    chips =
                        when (selectedOption) {
                            OptionStats.WEEKS -> {
                                weeklyDates
                            }

                            OptionStats.MONTHS -> {
                                monthlyDates
                            }

                            OptionStats.YEARS -> {
                                yearlyDates
                            }

                            OptionStats.CONTINUOUS -> {
                                listOf(
                                    StatPeriod.WEEK_1.ordinal to
                                        pluralStringResource(
                                            R.plurals.n_week,
                                            1,
                                            1,
                                        ),
                                    StatPeriod.MONTH_1.ordinal to
                                        pluralStringResource(
                                            R.plurals.n_month,
                                            1,
                                            1,
                                        ),
                                    StatPeriod.MONTH_3.ordinal to
                                        pluralStringResource(
                                            R.plurals.n_month,
                                            3,
                                            3,
                                        ),
                                    StatPeriod.MONTH_6.ordinal to
                                        pluralStringResource(
                                            R.plurals.n_month,
                                            6,
                                            6,
                                        ),
                                    StatPeriod.YEAR_1.ordinal to
                                        pluralStringResource(
                                            R.plurals.n_year,
                                            1,
                                            1,
                                        ),
                                    StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
                                )
                            }
                        },
                    options =
                        listOf(
                            OptionStats.CONTINUOUS to stringResource(id = R.string.continuous),
                            OptionStats.WEEKS to stringResource(R.string.weeks),
                            OptionStats.MONTHS to stringResource(R.string.months),
                            OptionStats.YEARS to stringResource(R.string.years),
                        ),
                    selectedOption = selectedOption,
                    onSelectionChange = {
                        viewModel.selectedOption.value = it
                        viewModel.indexChips.value = 0
                    },
                    currentValue = indexChips,
                    onValueUpdate = { viewModel.indexChips.value = it },
                )
            }

            // Tombol Acak full-width ala v14 (di sana di bawah judul +
            // ringkasan, sebelum carousel). Acak pindah ke sini supaya
            // tombol melayang di bawah hanya Bagikan, seperti v14.
            if (!isSearching) {
                item(key = "shuffle_most_played") {
                    Column(
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.most_played_songs),
                                        items = orderedMostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled(),
                                    ),
                                )
                            },
                            enabled = orderedMostPlayedSongs.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.shuffle),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }

            if (!isSearching) {
                item(key = "mostPlayedSongs") {
                    StatisticsCarousel(
                        title = "${mostPlayedSongsStats.size} ${stringResource(id = R.string.songs)}",
                        onSeeAll = { navController.navigate("stats_detail/tracks") },
                        tileSize = statsTileSize,
                        titleHeight = statsTitleHeight,
                        subtitleHeight = statsSubtitleHeight,
                        modifier = Modifier.animateItem(),
                    ) {
                        itemsIndexed(
                            items = mostPlayedSongsStats,
                            key = { index, song -> "${song.id}_$index" },
                        ) { index, song ->
                            StatisticsCarouselItem(
                                thumbnailUrl = song.thumbnailUrl,
                                title = "${index + 1}. ${song.title}",
                                subtitle =
                                    joinByBullet(
                                        pluralStringResource(
                                            R.plurals.n_time,
                                            song.songCountListened,
                                            song.songCountListened,
                                        ),
                                        makeTimeString(song.timeListened),
                                    ),
                                size = statsTileSize,
                                titleHeight = statsTitleHeight,
                                subtitleHeight = statsSubtitleHeight,
                                shape = RoundedCornerShape(ThumbnailCornerRadius),
                                modifier =
                                    Modifier
                                        .combinedClickable(
                                            onClick = {
                                                if (song.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    val targetSong = mostPlayedSongs.find { it.id == song.id }
                                                    if (targetSong != null) {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                endpoint = WatchEndpoint(song.id),
                                                                preloadItem = targetSong.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                val targetSong = mostPlayedSongs.find { it.id == song.id }
                                                if (targetSong != null) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        SongMenu(
                                                            originalSong = targetSong,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                }
            }

            if (!isSearching) {
                item(key = "mostPlayedAlbums") {
                    if (mostPlayedAlbums.isNotEmpty()) {
                        StatisticsCarousel(
                            title = "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}",
                            onSeeAll = { navController.navigate("stats_detail/albums") },
                            tileSize = statsTileSize,
                            titleHeight = statsTitleHeight,
                            subtitleHeight = statsSubtitleHeight,
                            modifier = Modifier.animateItem(),
                        ) {
                            itemsIndexed(
                                items = mostPlayedAlbums,
                                key = { _, album -> album.id },
                            ) { index, album ->
                                StatisticsCarouselItem(
                                    thumbnailUrl = album.album.thumbnailUrl,
                                    title = "${index + 1}. ${album.album.title}",
                                    subtitle =
                                        joinByBullet(
                                            pluralStringResource(
                                                R.plurals.n_time,
                                                album.songCountListened ?: 0,
                                                album.songCountListened ?: 0,
                                            ),
                                            makeTimeString(album.timeListened),
                                        ),
                                    size = statsTileSize,
                                    titleHeight = statsTitleHeight,
                                    subtitleHeight = statsSubtitleHeight,
                                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("album/${album.id}")
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = album,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ).animateItem(),
                                )
                            }
                        }
                    } else {
                        StatisticsCarouselHeader(
                            title = "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}",
                            onSeeAll = { navController.navigate("stats_detail/albums") },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (!isSearching) {
                item(key = "mostPlayedArtists") {
                    StatisticsCarousel(
                        title = "${mostPlayedArtists.size} ${stringResource(id = R.string.artists)}",
                        onSeeAll = { navController.navigate("stats_detail/artists") },
                        tileSize = statsTileSize,
                        titleHeight = statsTitleHeight,
                        subtitleHeight = statsSubtitleHeight,
                        modifier = Modifier.animateItem(),
                    ) {
                        itemsIndexed(
                            items = mostPlayedArtists,
                            key = { _, artist -> artist.id },
                        ) { index, artist ->
                            StatisticsCarouselItem(
                                thumbnailUrl = artist.artist.thumbnailUrl,
                                title = "${index + 1}. ${artist.artist.name}",
                                subtitle =
                                    joinByBullet(
                                        pluralStringResource(
                                            R.plurals.n_time,
                                            artist.songCount,
                                            artist.songCount,
                                        ),
                                        makeTimeString(artist.timeListened?.toLong()),
                                    ),
                                size = statsTileSize,
                                titleHeight = statsTitleHeight,
                                subtitleHeight = statsSubtitleHeight,
                                shape = CircleShape,
                                modifier =
                                    Modifier
                                        .combinedClickable(
                                            onClick = {
                                                navController.navigate("artist/${artist.id}")
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    ArtistMenu(
                                                        originalArtist = artist,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                }
            }

            if (!isSearching) {
                // Tiga kartu ala v14, urutan sama: Ringkasan, Aktivitas
                // (heatmap harian), Kebiasaan mendengarkan (per jam).
                item(key = "statsOverview") {
                    StatisticsCard(stringResource(R.string.statistics_overview)) {
                        SummaryGrid(
                            totalPlays = statsTotalPlays,
                            totalTimeMs = statsTotalTimeMs,
                            artistCount = statsArtistCount,
                            albumCount = statsAlbumCount,
                        )
                    }
                }
                item(key = "statsActivity") {
                    StatisticsCard(stringResource(R.string.statistics_activity)) {
                        ActivityHeatmap(
                            dailyStats = dailyStats,
                            firstDate = heatmapRange.first,
                            today = heatmapRange.second,
                        )
                    }
                }
                item(key = "statsListeningHabits") {
                    StatisticsCard(stringResource(R.string.statistics_listening_habits)) {
                        HourlyActivityChart(hourlyStats = hourlyStats)
                    }
                }
            }


            if (isSearching) {
                items(
                    items =
                        allArtists.filter { artist ->
                            artist.artist.name.contains(query.text, ignoreCase = true)
                        },
                    key = { "stats_artist_${it.id}" },
                    contentType = { CONTENT_TYPE_ARTIST },
                ) { artist ->
                    val uiArtist = Artist(name = artist.artist.name, id = artist.id)
                    val isChecked = sArtists.any { it.id == uiArtist.id }
                    Row( // Use a row to arrange the checkbox and ArtistListItem horizontally
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    toggleArtistSelection(uiArtist)
                                }.padding(8.dp),
                    ) {
                        ArtistListItem(
                            artist = artist,
                            modifier = Modifier.weight(1f), // Allow ArtistListItem to take remaining space
                        )

                        Checkbox(
                            checked = sArtists.contains(Artist(name = artist.artist.name, id = artist.id)), // Get the current checked state
                            onCheckedChange = {
                                toggleArtistSelection(uiArtist)
                            },
                        )
                    }
                }
            }
            if (query.text.isNotEmpty() && filteredArtists.isEmpty()) {
                item(key = "no_result") {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found),
                    )
                }
            }

            // Ruang bawah 96dp ala v14 supaya tombol Bagikan yang melayang
            // tidak menutupi baris terakhir saat daftar di-scroll mentok.
            item(key = "bottom_spacing") {
                Spacer(Modifier.height(96.dp))
            }
        }
        // Hanya tombol Bagikan yang melayang, ala v14. Acak sudah pindah
        // ke tombol full-width di atas; dua tombol bertumpuk menutupi
        // konten seperti sebelumnya.
        if (mostPlayedSongsStats.isNotEmpty() && !isSearching) {
            AnimatedVisibility(
                visible = lazyListState.isScrollingUp(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showRecap = true },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.share)) },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    TopAppBar(
        title = {
            if (inSelectMode) {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            } else if (isSearching) {
                Row {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                    )

                    if (sArtists.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = clearArtistSelection) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "Clear Artists",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.stats))
            }
        },
        navigationIcon = {
            if (inSelectMode) {
                androidx.compose.material3.IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Select Button",
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = "Back Button",
                    )
                }
            }
        },
        actions = {
            if (inSelectMode) {
                Checkbox(
                    checked = true,
                    onCheckedChange = {
                    },
                )
                androidx.compose.material3.IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "More Button",
                    )
                }
            } else if (!isSearching) {
                androidx.compose.material3.IconButton(
                    onClick = { isSearching = true },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = "Search Button",
                    )
                }
                IconButton(
                    onClick = { showTimeTransfer = true },
                    onLongClick = { showTimeTransfer = true },
                ) {
                    Icon(
                        painterResource(R.drawable.sync),
                        contentDescription = "Time Transfer",
                    )
                }
            }
        },
    )

    if (showRecap) {
        RecapScreen(onDismiss = { showRecap = false })
    }
}

// Ukuran ubin carousel ala v14 (Dimensions.kt): dihitung dari lebar
// layar, bukan nilai tetap — target 160dp per ubin (130dp kalau ukuran
// grid SMALL), minimal 3 kolom (4 kalau SMALL), jarak antar ubin 6dp.
@Composable
private fun statisticsCarouselTileSize(): Dp {
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val available = screenWidth - 32.dp
    val target =
        160.dp * (if (gridItemSize == GridItemSize.BIG) 128f else 104f) / 128f
    val minColumns = if (gridItemSize == GridItemSize.BIG) 3 else 4
    val columns = (available / target).toInt().coerceAtLeast(minColumns)
    return (available - 6.dp * (columns - 1)) / columns
}

// Header carousel ala v14: judul headlineMedium warna primary + tombol
// panah berisi (lihat semua) ke layar detail.
@Composable
private fun StatisticsCarouselHeader(
    title: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSeeAll) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
            )
        }
    }
}

// Carousel ala v14: header + LazyRow dengan padding horizontal 16dp,
// jarak antar ubin 6dp, dan tinggi yang dicadangkan penuh supaya tidak
// melompat saat data berubah.
@Composable
private fun StatisticsCarousel(
    title: String,
    onSeeAll: () -> Unit,
    tileSize: Dp,
    titleHeight: Dp,
    subtitleHeight: Dp,
    modifier: Modifier = Modifier,
    items: LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatisticsCarouselHeader(title = title, onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(tileSize + 6.dp + titleHeight + subtitleHeight),
        ) {
            items()
        }
    }
}

// Ubin carousel ala v14: sampul saja (tanpa tombol putar di tengah),
// judul titleSmall + subjudul bodySmall, masing-masing 1 baris dengan
// ellipsis dan tinggi yang dicadangkan.
@Composable
private fun StatisticsCarouselItem(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    size: Dp,
    titleHeight: Dp,
    subtitleHeight: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(size)) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(size)
                    .clip(shape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(titleHeight),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(subtitleHeight),
        )
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
