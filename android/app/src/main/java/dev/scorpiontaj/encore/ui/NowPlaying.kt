package dev.scorpiontaj.encore.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.Library
import dev.scorpiontaj.encore.library.LyricsFiles
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.playback.LyricsState
import dev.scorpiontaj.encore.playback.PlayerState
import dev.scorpiontaj.encore.playback.PlayerViewModel
import dev.scorpiontaj.encore.playback.SleepState
import dev.scorpiontaj.encore.radio.Recorder
import dev.scorpiontaj.encore.radio.Recording
import dev.scorpiontaj.encore.radio.stationOf
import dev.scorpiontaj.encore.settings.Settings
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Buzzes for playback actions — play, skip, swipe dismiss — when the person has not turned
 * haptics off in Settings → Now playing.
 */
@Composable
fun hapticFeedback(): (HapticFeedbackType) -> Unit {
    val feedback = LocalHapticFeedback.current
    val settings by Settings.state.collectAsStateWithLifecycle()
    return { type -> if (settings.haptics) feedback.performHapticFeedback(type) }
}

/** The bar above the tabs while something is loaded: tap it to open [NowPlayingScreen]. */
@Composable
fun MiniPlayer(state: PlayerState, library: Library?, vm: PlayerViewModel, onOpen: () -> Unit) {
    val haptic = hapticFeedback()
    val position = rememberPosition(state, vm)
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(MINI_RADIUS),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .swipeUpToOpen { haptic(HapticFeedbackType.LongPress); onOpen() }
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val song = library?.song(state.mediaId)
                val artworkUri = state.metadata.artworkUri?.toString()
                when {
                    song == null && artworkUri != null -> StationArtwork(artworkUri, size = 48.dp)
                    else -> Artwork(song?.uri, size = 48.dp, bitmap = state.artwork)
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        title(state),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = MINI_MUTED),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.previous() }) {
                    Icon(icon(R.drawable.ic_skip_previous), stringResource(R.string.action_previous))
                }
                PlayPauseButton(state.playing, vm::togglePlay, filled = false)
                IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.next() }) {
                    Icon(icon(R.drawable.ic_skip_next), stringResource(R.string.action_next))
                }
            }
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (position.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colors.onPrimaryContainer,
                    trackColor = colors.onPrimaryContainer.copy(alpha = MINI_TRACK),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
    }
}

/** Full-screen player: cover, format, seek bar, transport, shuffle, repeat and the queue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    library: Library?,
    lyrics: LyricsState,
    sleep: SleepState,
    recording: Recording?,
    vm: PlayerViewModel,
    onAddToPlaylist: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onEditLyrics: (Song) -> Unit,
    onClose: () -> Unit,
) {
    val haptic = hapticFeedback()
    var songMenu by remember { mutableStateOf(false) }
    val station = remember(state.mediaId, state.queue, state.index) { stationOf(state.queue.getOrNull(state.index)) }
    var sleepMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val song = library?.song(state.mediaId)
    val position = rememberPosition(state, vm)
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(showLyrics, song) { if (showLyrics) vm.loadLyrics(song) }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val reduceMotion = settings.reduceMotion
    // Keeps the screen on while this screen shows, when asked for in Settings → Now playing.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The ambient background: the cover, blurred and dimmed behind everything, as on the desktop.
        val cover = state.artwork
        if (settings.ambient && cover != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Crossfade(targetState = cover, animationSpec = tween(600), label = "ambient") { art ->
                Image(
                    art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.45f),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().dragToDismiss { haptic(HapticFeedbackType.LongPress); onClose() }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(icon(R.drawable.ic_expand_more), stringResource(R.string.action_close))
                }
                // Up to six buttons share this row: on a narrow phone the title shrinks to stay on
                // one line, and where even its smallest size would not fit, the row shows none.
                val title = stringResource(R.string.now_playing)
                val titleStyle = MaterialTheme.typography.titleMedium
                val measurer = rememberTextMeasurer()
                BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val fits = measurer.measure(title, titleStyle.copy(fontSize = TITLE_MIN_SP.sp), maxLines = 1)
                        .size.width <= constraints.maxWidth
                    if (fits) {
                        Text(
                            title,
                            style = titleStyle,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = TITLE_MIN_SP.sp,
                                maxFontSize = titleStyle.fontSize,
                            ),
                        )
                    }
                }
                if (song != null) {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            icon(R.drawable.ic_lyrics),
                            stringResource(if (showLyrics) R.string.lyrics_hide else R.string.lyrics_show),
                            tint = toggleTint(showLyrics),
                        )
                    }
                }
                if (station != null && Recorder.canRecord(station)) {
                    val recordingThis = recording?.station?.uuid == station.uuid
                    IconButton(onClick = { if (recordingThis) vm.stopRecording() else vm.startRecording(station) }) {
                        Icon(
                            icon(if (recordingThis) R.drawable.ic_stop else R.drawable.ic_record),
                            stringResource(if (recordingThis) R.string.record_stop else R.string.record_start),
                            tint = if (recordingThis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { sleepMenu = true }) {
                        Icon(icon(R.drawable.ic_bedtime), stringResource(R.string.sleep_timer), tint = toggleTint(sleep != SleepState.Off))
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        SLEEP_MINUTES.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes)) },
                                onClick = { sleepMenu = false; vm.sleepAfter(minutes) },
                            )
                        }
                        if (state.durationMs > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sleep_end_of_song)) },
                                onClick = { sleepMenu = false; vm.sleepAtEndOfSong() },
                            )
                        }
                        if (sleep != SleepState.Off) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sleep_off)) },
                                onClick = { sleepMenu = false; vm.cancelSleep() },
                            )
                        }
                    }
                }
                if (song != null || station != null) {
                    Box {
                        IconButton(onClick = { songMenu = true }) {
                            Icon(icon(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = songMenu, onDismissRequest = { songMenu = false }) {
                            if (song != null) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(icon("list-plus"), null) },
                                    text = { Text(stringResource(R.string.playlist_add_to)) },
                                    onClick = { songMenu = false; onAddToPlaylist(song) },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(icon("mic-vocal"), null) },
                                    text = { Text(stringResource(R.string.lyrics_edit)) },
                                    onClick = { songMenu = false; onEditLyrics(song) },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(icon("pencil"), null) },
                                    text = { Text(stringResource(R.string.tags_edit)) },
                                    onClick = { songMenu = false; onEditTags(song) },
                                )
                            }
                            DropdownMenuItem(
                                leadingIcon = { Icon(icon("square-arrow-out-up-right"), null) },
                                text = { Text(stringResource(R.string.action_share)) },
                                onClick = {
                                    songMenu = false
                                    if (song != null) vm.shareSong(song) else station?.let(vm::shareStation)
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(icon(R.drawable.ic_queue_music), stringResource(R.string.action_queue))
                }
            }
            AnimatedContent(
                targetState = showLyrics && song != null,
                modifier = Modifier.weight(3f).fillMaxWidth(),
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(140))
                    }
                },
                label = "now-playing-body",
            ) { showingLyrics ->
                when (showingLyrics) {
                    true -> LyricsView(lyrics, position, settings.lyricsScale, Modifier.fillMaxSize()) { vm.seekTo(it) }
                    // The cover is the largest square the space allows: a phone whose controls need
                    // more height gets a smaller cover instead of one pushed up over the header.
                    false -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        val side = minOf(maxWidth, maxHeight, COVER_MAX)
                        Box(
                            Modifier
                                .size(side)
                                .dragToDismiss { haptic(HapticFeedbackType.LongPress); onClose() }
                                .swipeToTrack(
                                    onPrevious = { haptic(HapticFeedbackType.TextHandleMove); vm.previous() },
                                    onNext = { haptic(HapticFeedbackType.TextHandleMove); vm.next() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val artworkUri = state.metadata.artworkUri?.toString()
                            when {
                                song == null && artworkUri != null -> StationArtwork(artworkUri, size = minOf(side, STATION_MAX))
                                else -> Artwork(song?.uri, size = side, bitmap = state.artwork)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                title(state),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val quality = song?.let { qualityLine(it) }
            if (!quality.isNullOrEmpty()) {
                Surface(
                    Modifier.padding(top = 8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        if (song.lossless) "${stringResource(R.string.quality_lossless)} · $quality" else quality,
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (state.seekable && state.durationMs > 0) {
                val thumbSize by animateDpAsState(if (scrubbing) 26.dp else 20.dp, label = "seek-thumb")
                val thumbInteraction = remember { MutableInteractionSource() }
                Slider(
                    value = if (scrubbing) scrub else (position.toFloat() / state.durationMs).coerceIn(0f, 1f),
                    onValueChange = { scrubbing = true; scrub = it },
                    onValueChangeFinished = {
                        vm.seekTo((scrub * state.durationMs).toLong())
                        scrubbing = false
                    },
                    interactionSource = thumbInteraction,
                    thumb = { SliderDefaults.Thumb(interactionSource = thumbInteraction, thumbSize = DpSize(thumbSize, thumbSize)) },
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = if (scrubbing) (scrub * state.durationMs).toLong() else position
                    Text(duration(shown), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(duration(state.durationMs), style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Text(
                    stringResource(R.string.radio_live),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.toggleShuffle() }) {
                    Icon(
                        icon(R.drawable.ic_shuffle),
                        stringResource(if (state.shuffle) R.string.shuffle_on else R.string.shuffle_off),
                        tint = toggleTint(state.shuffle),
                    )
                }
                IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.previous() }, Modifier.size(56.dp)) {
                    Icon(icon(R.drawable.ic_skip_previous), stringResource(R.string.action_previous), Modifier.size(32.dp))
                }
                PlayPauseButton(state.playing, vm::togglePlay, filled = true)
                IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.next() }, Modifier.size(56.dp)) {
                    Icon(icon(R.drawable.ic_skip_next), stringResource(R.string.action_next), Modifier.size(32.dp))
                }
                IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.cycleRepeat() }) {
                    val (repeatIcon, label) = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one to R.string.repeat_one
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat to R.string.repeat_all
                        else -> R.drawable.ic_repeat to R.string.repeat_off
                    }
                    Icon(icon(repeatIcon), stringResource(label), tint = toggleTint(state.repeatMode != Player.REPEAT_MODE_OFF))
                }
            }
            StatusLine(sleep, recording)
            Spacer(Modifier.weight(1f))
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            Text(
                stringResource(R.string.queue_title),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.queue.isEmpty()) {
                Message(stringResource(R.string.queue_empty))
            } else {
                val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0))
                // Dragging a row after a long press moves it about: the rows are one height, so
                // the drag translates into whole-row moves, handed to the queue as they happen.
                val rowHeight = with(LocalDensity.current) { QUEUE_ROW.dp.toPx() }
                var dragFrom by remember { mutableIntStateOf(-1) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(state.queue, key = { index, item -> "$index:${item.mediaId}" }) { index, item ->
                        val here = remember { mutableIntStateOf(index) }
                        here.intValue = index
                        val dismiss = rememberSwipeToDismissBoxState()
                        SwipeToDismissBox(
                            state = dismiss,
                            enableDismissFromStartToEnd = false,
                            onDismiss = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    haptic(HapticFeedbackType.LongPress)
                                    vm.removeFromQueue(here.intValue)
                                }
                            },
                            modifier = Modifier
                                .zIndex(if (index == dragFrom) 1f else 0f)
                                .graphicsLayer { translationY = if (index == dragFrom) dragOffset else 0f }
                                .pointerInput(vm) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            if (state.queue.size > 1) {
                                                dragFrom = here.intValue
                                                dragOffset = 0f
                                                haptic(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (dragFrom < 0) return@detectDragGesturesAfterLongPress
                                            dragOffset += amount.y
                                            val to = (dragFrom + (dragOffset / rowHeight).toInt())
                                                .coerceIn(0, state.queue.size - 1)
                                            if (to != dragFrom) {
                                                vm.moveInQueue(dragFrom, to)
                                                dragOffset -= (to - dragFrom) * rowHeight
                                                dragFrom = to
                                                haptic(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        onDragEnd = {
                                            dragFrom = -1
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragFrom = -1
                                            dragOffset = 0f
                                        },
                                    )
                                },
                            backgroundContent = {
                                // Drawn only mid-swipe: the row above it is transparent.
                                if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    Box(
                                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            icon(R.drawable.ic_close),
                                            stringResource(R.string.action_remove),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        ) {
                            val itemSong = library?.song(item.mediaId)
                            ListRow(
                                title = item.mediaMetadata.title?.toString().orEmpty(),
                                subtitle = item.mediaMetadata.artist?.toString()
                                    ?: stringResource(if (itemSong == null) R.string.radio_station else R.string.unknown_artist),
                                leading = { Artwork(itemSong?.uri) },
                                highlighted = index == state.index,
                                onClick = { vm.skipTo(index) },
                                trailing = {
                                    IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); vm.removeFromQueue(index) }) {
                                        Icon(icon(R.drawable.ic_close), stringResource(R.string.action_remove))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What is running in the background: the sleep timer's countdown and a radio recording. */
@Composable
private fun StatusLine(sleep: SleepState, recording: Recording?) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sleep, recording) {
        while (sleep is SleepState.At || recording != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val lines = listOfNotNull(
        when (sleep) {
            is SleepState.At -> ((sleep.atMs - now) / 60_000 + 1).toInt().let { pluralStringResource(R.plurals.sleep_stops_in, it, it) }
            SleepState.EndOfSong -> stringResource(R.string.sleep_stops_after_song)
            SleepState.Off -> null
        },
        recording?.let { stringResource(R.string.record_running, duration(now - it.sinceMs)) },
    )
    lines.forEach {
        Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Lyrics for the playing song; synced lines follow the playback position and seek on tap. */
@Composable
private fun LyricsView(state: LyricsState, positionMs: Long, scale: Float, modifier: Modifier, onSeek: (Long) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (state) {
            LyricsState.Idle, LyricsState.Loading -> CircularProgressIndicator()
            LyricsState.Missing -> Text(stringResource(R.string.lyrics_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LyricsState.Found -> Column(Modifier.fillMaxSize()) {
                val lines = state.lyrics.lines
                val active = if (state.lyrics.synced) lines.indexOfLast { it.startMs <= positionMs } else -1
                val list = rememberLazyListState()
                LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(lines) { index, line ->
                        Text(
                            line.text,
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = line.startMs >= 0) { onSeek(line.startMs) }
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleLarge.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) },
                            color = when {
                                !state.lyrics.synced || index == active -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                        )
                    }
                }
                Text(
                    when (val source = state.lyrics.source) {
                        LyricsFiles.SOURCE -> stringResource(R.string.lyrics_from_file)
                        else -> stringResource(R.string.lyrics_from, source)
                    },
                    Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, filled: Boolean) {
    val haptic = hapticFeedback()
    val label = stringResource(if (playing) R.string.action_pause else R.string.action_play)
    when (filled) {
        true -> FilledIconButton(
            onClick = { haptic(HapticFeedbackType.TextHandleMove); onClick() },
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) { Crossfade(playing, label = "play") { now -> Icon(icon(if (now) R.drawable.ic_pause else R.drawable.ic_play), label, Modifier.size(36.dp)) } }
        false -> IconButton(onClick = { haptic(HapticFeedbackType.TextHandleMove); onClick() }) { Crossfade(playing, label = "play") { now -> Icon(icon(if (now) R.drawable.ic_pause else R.drawable.ic_play), label) } }
    }
}

/** Opens the full player when the mini player is dragged or flung up far enough. */
private fun Modifier.swipeUpToOpen(onOpen: () -> Unit): Modifier = composed {
    var travelled by remember { mutableFloatStateOf(0f) }
    pointerInput(onOpen) {
        detectVerticalDragGestures(
            onDragStart = { travelled = 0f },
            onVerticalDrag = { change, amount ->
                travelled += amount
                change.consume()
            },
            onDragEnd = {
                if (-travelled > OPEN_SWIPE.dp.toPx()) onOpen()
            },
        )
    }
}

/** Drags the content down with the finger and closes it once the drag goes far enough. */
private fun Modifier.dragToDismiss(onDismiss: () -> Unit): Modifier = composed {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    pointerInput(onDismiss) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, amount ->
                change.consume()
                scope.launch { offset.snapTo((offset.value + amount).coerceAtLeast(0f)) }
            },
            onDragEnd = {
                scope.launch {
                    if (offset.value > DISMISS_DRAG.dp.toPx()) {
                        onDismiss()
                    } else {
                        offset.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
                    }
                }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f) } },
        )
    }.graphicsLayer { translationY = offset.value }
}

/** Plays the previous or next song when the cover is swiped sideways. */
private fun Modifier.swipeToTrack(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    var travelled by remember { mutableFloatStateOf(0f) }
    pointerInput(onPrevious, onNext) {
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onHorizontalDrag = { change, amount ->
                travelled += amount
                change.consume()
            },
            onDragEnd = {
                when {
                    travelled > SWIPE_TRACK.dp.toPx() -> onPrevious()
                    travelled < -SWIPE_TRACK.dp.toPx() -> onNext()
                }
            },
        )
    }
}

@Composable
private fun toggleTint(on: Boolean): Color =
    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/**
 * The playback position, read on every frame while playing so the seek bar follows the audio
 * clock at the display's refresh rate, and polled slowly while paused for remote changes.
 */
@Composable
private fun rememberPosition(state: PlayerState, vm: PlayerViewModel): Long {
    var position by remember { mutableLongStateOf(vm.positionMs) }
    LaunchedEffect(state.mediaId, state.playing) {
        position = vm.positionMs
        while (state.playing) {
            withFrameNanos {}
            position = vm.positionMs
        }
        while (true) {
            delay(POSITION_POLL_MS)
            position = vm.positionMs
        }
    }
    return position
}

@Composable
private fun title(state: PlayerState): String =
    (state.metadata.title ?: state.metadata.station)?.toString() ?: stringResource(R.string.app_name)

@Composable
private fun subtitle(state: PlayerState): String {
    val metadata = state.metadata
    return metadata.artist?.toString()?.orUnknown(stringResource(R.string.unknown_artist))
        ?: stringResource(if (metadata.station != null) R.string.radio_station else R.string.unknown_artist)
}

private const val POSITION_POLL_MS = 500L

/** The height of a queue row, the unit whole-row drags move by, in dp. */
private const val QUEUE_ROW = 64

/** How far up the mini player travels before it opens, and down a screen before it closes, in dp. */
private const val OPEN_SWIPE = 56
private const val DISMISS_DRAG = 120

/** How far sideways the cover travels before the track changes, in dp. */
private const val SWIPE_TRACK = 80
private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)

/** The corner radius of the floating mini player. */
private val MINI_RADIUS = 18.dp

/** How strongly the mini player's subtitle and progress track show against the accent. */
private const val MINI_MUTED = 0.8f
private const val MINI_TRACK = 0.25f

/** The largest the Now Playing cover grows, on tablets and unfolded phones. */
private val COVER_MAX = 420.dp

/** The largest a station's logo is drawn, since logos are small images. */
private val STATION_MAX = 280.dp

/** The smallest the Now Playing title shrinks to before it is cut short. */
private const val TITLE_MIN_SP = 11
