package dev.scorpiontaj.encore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.Album
import dev.scorpiontaj.encore.library.Artist
import dev.scorpiontaj.encore.library.Playlist
import dev.scorpiontaj.encore.library.SONG_PREFIX
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.library.UNKNOWN
import dev.scorpiontaj.encore.playback.PlayerViewModel
import dev.scorpiontaj.encore.playback.RadioState
import dev.scorpiontaj.encore.radio.RADIO_PREFIX
import dev.scorpiontaj.encore.radio.Station
import java.text.DecimalFormat

/** The song actions every list offers: play from a row, queue it next, or append it. */
class SongActions(
    val play: (List<Song>, Int) -> Unit,
    val shuffle: (List<Song>) -> Unit,
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
) {
    constructor(vm: PlayerViewModel) : this(
        play = { songs, start -> vm.play(songs, start) },
        shuffle = { songs -> vm.play(songs, shuffle = true) },
        playNext = vm::playNext,
        addToQueue = vm::addToQueue,
    )
}

@Composable
fun SongsScreen(songs: List<Song>, current: String?, actions: SongActions) {
    if (songs.isEmpty()) return Message(stringResource(R.string.library_empty))
    LazyColumn(Modifier.fillMaxSize()) {
        playAllHeader(songs, actions)
        songItems(songs, current, actions)
    }
}

@Composable
fun AlbumsScreen(albums: List<Album>, onOpen: (Album) -> Unit) {
    if (albums.isEmpty()) return Message(stringResource(R.string.library_empty))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(album) }
                    .padding(4.dp),
            ) {
                Artwork(album.songs.first().uri, size = Dp.Unspecified)
                Text(
                    album.title.orUnknown(stringResource(R.string.unknown_album)),
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    album.artist.orUnknown(stringResource(R.string.unknown_artist)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ArtistsScreen(artists: List<Artist>, onOpen: (Artist) -> Unit) {
    if (artists.isEmpty()) return Message(stringResource(R.string.library_empty))
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            ListRow(
                title = artist.name.orUnknown(stringResource(R.string.unknown_artist)),
                subtitle = pluralStringResource(R.plurals.songs_count, artist.songs.size, artist.songs.size),
                leading = { Artwork(artist.songs.first().uri) },
                onClick = { onOpen(artist) },
            )
        }
    }
}

@Composable
fun PlaylistsScreen(playlists: List<Playlist>, onOpen: (Playlist) -> Unit) {
    if (playlists.isEmpty()) return Message(stringResource(R.string.playlists_empty))
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }) { playlist ->
            ListRow(
                title = playlist.name,
                subtitle = pluralStringResource(R.plurals.songs_count, playlist.songs.size, playlist.songs.size),
                leading = { Artwork(playlist.songs.firstOrNull()?.uri) },
                onClick = { onOpen(playlist) },
            )
        }
    }
}

/** An album, artist or playlist opened from its tab: a header, then its songs. */
@Composable
fun CollectionScreen(title: String, subtitle: String, songs: List<Song>, current: String?, actions: SongActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(songs.firstOrNull()?.uri, size = 112.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        pluralStringResource(R.plurals.songs_count, songs.size, songs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        playAllHeader(songs, actions)
        songItems(songs, current, actions)
    }
}

@Composable
fun RadioScreen(state: RadioState, current: String?, onPlay: (Station) -> Unit, onRetry: () -> Unit) {
    when (state) {
        RadioState.Idle, RadioState.Loading -> Loading(stringResource(R.string.radio_loading))
        RadioState.Failed -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.radio_error), textAlign = TextAlign.Center)
            Button(onClick = onRetry, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.radio_retry)) }
        }
        is RadioState.Loaded -> {
            if (state.stations.isEmpty()) return Message(stringResource(R.string.radio_empty))
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.stations, key = { it.uuid.ifEmpty { it.url } }) { station ->
                    ListRow(
                        title = station.name,
                        subtitle = listOfNotNull(
                            station.codec,
                            station.bitrate.takeIf { it > 0 }?.let { stringResource(R.string.quality_bitrate, it) },
                            station.tags.ifEmpty { null },
                        ).joinToString(" · "),
                        leading = {
                            Surface(Modifier.size(48.dp), RoundedCornerShape(8.dp), MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_radio), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        },
                        highlighted = current == "$RADIO_PREFIX${station.uuid}",
                        onClick = { onPlay(station) },
                    )
                }
            }
        }
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Loading(text: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(text, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A song's format line, such as "FLAC · 24-bit · 96 kHz" or "MP3 · 320 kbps". */
@Composable
fun qualityLine(song: Song): String {
    val detail = when {
        song.bitDepth != null && song.sampleRate != null ->
            stringResource(R.string.quality_hires, song.bitDepth, KHZ.format(song.sampleRate / 1000.0))
        song.bitrate != null -> stringResource(R.string.quality_bitrate, song.bitrate / 1000)
        else -> null
    }
    return listOfNotNull(song.format.ifEmpty { null }, detail).joinToString(" · ")
}

/** Shows MediaStore's "<unknown>" as the localized fallback. */
fun String.orUnknown(fallback: String): String = if (this == UNKNOWN || isBlank()) fallback else this

private fun LazyListScope.playAllHeader(songs: List<Song>, actions: SongActions) = item {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Button(onClick = { actions.play(songs, 0) }) {
            Icon(painterResource(R.drawable.ic_play), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_play_all))
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = { actions.shuffle(songs) }) {
            Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_shuffle_all))
        }
    }
}

private fun LazyListScope.songItems(songs: List<Song>, current: String?, actions: SongActions) =
    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
        var menu by remember { mutableStateOf(false) }
        ListRow(
            title = song.title,
            subtitle = listOf(
                song.artist.orUnknown(stringResource(R.string.unknown_artist)),
                qualityLine(song),
            ).filter { it.isNotEmpty() }.joinToString(" · "),
            leading = { Artwork(song.uri) },
            highlighted = current == "$SONG_PREFIX${song.id}",
            badge = if (song.lossless) stringResource(R.string.quality_lossless) else null,
            onClick = { actions.play(songs, index) },
            trailing = {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_play_next)) },
                            onClick = { menu = false; actions.playNext(song) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_queue)) },
                            onClick = { menu = false; actions.addToQueue(song) },
                        )
                    }
                }
            },
        )
    }

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    badge: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlighted) FontWeight.SemiBold else null,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Text(
                            badge,
                            Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

private val KHZ = DecimalFormat("0.#")
