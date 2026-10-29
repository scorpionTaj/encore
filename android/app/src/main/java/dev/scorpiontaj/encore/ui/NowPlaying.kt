package dev.scorpiontaj.encore.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.Library
import dev.scorpiontaj.encore.playback.LyricsState
import dev.scorpiontaj.encore.playback.PlayerState
import dev.scorpiontaj.encore.playback.PlayerViewModel
import kotlinx.coroutines.delay

/** The bar above the tabs while something is loaded: tap it to open [NowPlayingScreen]. */
@Composable
fun MiniPlayer(state: PlayerState, library: Library?, vm: PlayerViewModel, onOpen: () -> Unit) {
    val position = rememberPosition(state, vm)
    Surface(tonalElevation = 3.dp) {
        Column {
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (position.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val song = library?.song(state.mediaId)
                val artworkUri = state.metadata.artworkUri?.toString()
                when {
                    song == null && artworkUri != null -> StationArtwork(artworkUri, size = 44.dp)
                    else -> Artwork(song?.uri, size = 44.dp, bitmap = state.artwork)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title(state), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlayPauseButton(state.playing, vm::togglePlay, filled = false)
                IconButton(onClick = vm::next) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.action_next))
                }
            }
        }
    }
}

/** Full-screen player: cover, format, seek bar, transport, shuffle, repeat and the queue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(state: PlayerState, library: Library?, lyrics: LyricsState, vm: PlayerViewModel, onClose: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val song = library?.song(state.mediaId)
    val position = rememberPosition(state, vm)
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(showLyrics, song) { if (showLyrics) vm.loadLyrics(song) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.action_close))
                }
                Text(
                    stringResource(R.string.now_playing),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                if (song != null) {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            painterResource(R.drawable.ic_lyrics),
                            stringResource(if (showLyrics) R.string.lyrics_hide else R.string.lyrics_show),
                            tint = toggleTint(showLyrics),
                        )
                    }
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(painterResource(R.drawable.ic_queue_music), stringResource(R.string.action_queue))
                }
            }
            when (showLyrics && song != null) {
                true -> LyricsView(lyrics, position, Modifier.weight(3f).fillMaxWidth()) { vm.seekTo(it) }
                false -> {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.widthIn(max = 420.dp)) {
                        val artworkUri = state.metadata.artworkUri?.toString()
                        when {
                            song == null && artworkUri != null -> StationArtwork(artworkUri, size = 280.dp)
                            else -> Artwork(song?.uri, size = androidx.compose.ui.unit.Dp.Unspecified, bitmap = state.artwork)
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
                Slider(
                    value = if (scrubbing) scrub else (position.toFloat() / state.durationMs).coerceIn(0f, 1f),
                    onValueChange = { scrubbing = true; scrub = it },
                    onValueChangeFinished = {
                        vm.seekTo((scrub * state.durationMs).toLong())
                        scrubbing = false
                    },
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
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(
                        painterResource(R.drawable.ic_shuffle),
                        stringResource(if (state.shuffle) R.string.shuffle_on else R.string.shuffle_off),
                        tint = toggleTint(state.shuffle),
                    )
                }
                IconButton(onClick = vm::previous, Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.action_previous), Modifier.size(32.dp))
                }
                PlayPauseButton(state.playing, vm::togglePlay, filled = true)
                IconButton(onClick = vm::next, Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.action_next), Modifier.size(32.dp))
                }
                IconButton(onClick = vm::cycleRepeat) {
                    val (icon, label) = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one to R.string.repeat_one
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat to R.string.repeat_all
                        else -> R.drawable.ic_repeat to R.string.repeat_off
                    }
                    Icon(painterResource(icon), stringResource(label), tint = toggleTint(state.repeatMode != Player.REPEAT_MODE_OFF))
                }
            }
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
                LazyColumn(state = list) {
                    itemsIndexed(state.queue, key = { index, item -> "$index:${item.mediaId}" }) { index, item ->
                        val itemSong = library?.song(item.mediaId)
                        ListRow(
                            title = item.mediaMetadata.title?.toString().orEmpty(),
                            subtitle = item.mediaMetadata.artist?.toString()
                                ?: stringResource(if (itemSong == null) R.string.radio_station else R.string.unknown_artist),
                            leading = { Artwork(itemSong?.uri) },
                            highlighted = index == state.index,
                            onClick = { vm.skipTo(index) },
                            trailing = {
                                IconButton(onClick = { vm.removeFromQueue(index) }) {
                                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_remove))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Lyrics for the playing song; synced lines follow the playback position and seek on tap. */
@Composable
private fun LyricsView(state: LyricsState, positionMs: Long, modifier: Modifier, onSeek: (Long) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (state) {
            LyricsState.Idle, LyricsState.Loading -> CircularProgressIndicator()
            LyricsState.Missing -> Text(stringResource(R.string.lyrics_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LyricsState.Found -> {
                val lines = state.lyrics.lines
                val active = if (state.lyrics.synced) lines.indexOfLast { it.startMs <= positionMs } else -1
                val list = rememberLazyListState()
                LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(lines) { index, line ->
                        Text(
                            line.text,
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = line.startMs >= 0) { onSeek(line.startMs) }
                                .padding(vertical = 6.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = when {
                                !state.lyrics.synced || index == active -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, filled: Boolean) {
    val icon = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    val label = stringResource(if (playing) R.string.action_pause else R.string.action_play)
    when (filled) {
        true -> FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) { Icon(icon, label, Modifier.size(36.dp)) }
        false -> IconButton(onClick = onClick) { Icon(icon, label) }
    }
}

@Composable
private fun toggleTint(on: Boolean): Color =
    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/** The playback position, polled twice a second while playing. */
@Composable
private fun rememberPosition(state: PlayerState, vm: PlayerViewModel): Long {
    var position by remember { mutableLongStateOf(vm.positionMs) }
    LaunchedEffect(state.mediaId, state.playing) {
        position = vm.positionMs
        while (state.playing) {
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
