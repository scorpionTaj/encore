package dev.scorpiontaj.encore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.Album
import dev.scorpiontaj.encore.library.Artist
import dev.scorpiontaj.encore.library.Library
import dev.scorpiontaj.encore.library.Playlist
import dev.scorpiontaj.encore.library.SONG_PREFIX
import dev.scorpiontaj.encore.library.SmartPlaylist
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.library.UNKNOWN
import dev.scorpiontaj.encore.playback.PlayerViewModel
import dev.scorpiontaj.encore.playback.RadioState
import dev.scorpiontaj.encore.radio.RadioBrowser
import dev.scorpiontaj.encore.radio.Station
import java.text.DecimalFormat
import java.util.Locale

/** The song actions every list offers: play from a row, queue it, or show its details. */
class SongActions(
    val play: (List<Song>, Int) -> Unit,
    val shuffle: (List<Song>) -> Unit,
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val details: (Song) -> Unit,
) {
    constructor(vm: PlayerViewModel, details: (Song) -> Unit) : this(
        play = { songs, start -> vm.play(songs, start) },
        shuffle = { songs -> vm.play(songs, shuffle = true) },
        playNext = vm::playNext,
        addToQueue = vm::addToQueue,
        details = details,
    )
}

/** The Songs tab's filters, as on the desktop songs list. */
private enum class SongFilter(val label: Int) {
    All(R.string.filter_all),
    Lossless(R.string.quality_lossless),
    NoAlbum(R.string.filter_no_album),
    NoArtist(R.string.filter_no_artist),
    NoCover(R.string.filter_no_cover),
    Duplicates(R.string.filter_duplicates),
}

private enum class SongSort(val label: Int) {
    Title(R.string.sort_title),
    Artist(R.string.sort_artist),
    Album(R.string.sort_album),
    Added(R.string.sort_added),
    Modified(R.string.sort_modified),
}

@Composable
fun SongsScreen(
    songs: List<Song>,
    duplicates: Set<Long>,
    missingCovers: Set<Long>?,
    current: String?,
    actions: SongActions,
    onFindMissingCovers: () -> Unit,
) {
    if (songs.isEmpty()) return Message(stringResource(R.string.library_empty))
    var filter by rememberSaveable { mutableStateOf(SongFilter.All) }
    LaunchedEffect(filter) { if (filter == SongFilter.NoCover) onFindMissingCovers() }
    var sort by rememberSaveable { mutableStateOf(SongSort.Title) }
    var sortMenu by remember { mutableStateOf(false) }
    val shown = remember(songs, duplicates, missingCovers, filter, sort) {
        songs.filter {
            when (filter) {
                SongFilter.All -> true
                SongFilter.Lossless -> it.lossless
                SongFilter.NoAlbum -> it.album == UNKNOWN || it.album.isBlank()
                SongFilter.NoArtist -> it.artist == UNKNOWN || it.artist.isBlank()
                SongFilter.NoCover -> missingCovers?.contains(it.id) == true
                SongFilter.Duplicates -> it.id in duplicates
            }
        }.let { list ->
            when (sort) {
                SongSort.Title -> list
                SongSort.Artist -> list.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.track }))
                SongSort.Album -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
                SongSort.Added -> list.sortedByDescending { it.dateAdded }
                SongSort.Modified -> list.sortedByDescending { it.dateModified }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.action_sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SongSort.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(entry.label),
                                        fontWeight = if (entry == sort) FontWeight.SemiBold else null,
                                    )
                                },
                                onClick = { sort = entry; sortMenu = false },
                            )
                        }
                    }
                }
                SongFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        }
        if (filter == SongFilter.NoCover && missingCovers == null) {
            item { Loading(stringResource(R.string.filter_checking_covers)) }
        } else if (shown.isEmpty()) {
            item { Message(stringResource(R.string.filter_empty)) }
        } else {
            playAllHeader(shown, actions)
            songItems(shown, current, actions)
        }
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
        items(albums, key = { it.id }) { album -> AlbumCard(album) { onOpen(album) } }
    }
}

@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
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

@Composable
fun ArtistsScreen(artists: List<Artist>, onOpen: (Artist) -> Unit) {
    if (artists.isEmpty()) return Message(stringResource(R.string.library_empty))
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist -> ArtistRow(artist) { onOpen(artist) } }
    }
}

@Composable
fun ArtistRow(artist: Artist, onOpen: () -> Unit) {
    ListRow(
        title = artist.name.orUnknown(stringResource(R.string.unknown_artist)),
        subtitle = pluralStringResource(R.plurals.songs_count, artist.songs.size, artist.songs.size),
        leading = { Artwork(artist.songs.first().uri) },
        onClick = onOpen,
    )
}

/** Smart playlists first, with a button to make one, then the playlists MediaStore knows. */
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    smart: List<SmartPlaylist>,
    library: Library,
    onOpen: (Playlist) -> Unit,
    onOpenSmart: (SmartPlaylist) -> Unit,
    onNewSmart: () -> Unit,
    onEditSmart: (SmartPlaylist) -> Unit,
    onDeleteSmart: (SmartPlaylist) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            FilledTonalButton(onClick = onNewSmart, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.smart_new))
            }
        }
        items(smart, key = { "smart:${it.id}" }) { playlist ->
            val songs = remember(playlist, library) { playlist.songs(library) }
            var menu by remember { mutableStateOf(false) }
            ListRow(
                title = playlist.name,
                subtitle = pluralStringResource(R.plurals.songs_count, songs.size, songs.size),
                leading = {
                    Surface(Modifier.size(48.dp), RoundedCornerShape(8.dp), MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_auto_awesome), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                onClick = { onOpenSmart(playlist) },
                trailing = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = { menu = false; onEditSmart(playlist) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menu = false; onDeleteSmart(playlist) },
                            )
                        }
                    }
                },
            )
        }
        if (playlists.isEmpty() && smart.isEmpty()) {
            item { Message(stringResource(R.string.playlists_empty)) }
        }
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
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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

/** The Radio tab: a country picker, then that country's stations with favourites first. */
@Composable
fun RadioScreen(
    state: RadioState,
    country: String,
    favourites: Set<String>,
    current: String?,
    nowPlaying: String?,
    onPlay: (Station) -> Unit,
    onRetry: () -> Unit,
    onCountry: (String) -> Unit,
    onFavourite: (Station) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        CountryPicker(country, onCountry)
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
                if (state.stations.isEmpty()) return@Column Message(stringResource(R.string.radio_empty))
                val stations = remember(state.stations, favourites) {
                    state.stations.sortedByDescending { it.uuid in favourites }
                }
                // A new favourite moves to the top; follow it there rather than keep the old anchor.
                val list = rememberLazyListState()
                var favouriteCount by remember { mutableIntStateOf(favourites.size) }
                LaunchedEffect(favourites.size) {
                    if (favourites.size > favouriteCount) list.animateScrollToItem(0)
                    favouriteCount = favourites.size
                }
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    items(stations, key = { it.uuid.ifEmpty { it.url } }) { station ->
                        StationRow(
                            station = station,
                            playing = current == station.mediaId,
                            nowPlaying = nowPlaying,
                            favourite = station.uuid in favourites,
                            onPlay = { onPlay(station) },
                            onFavourite = { onFavourite(station) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StationRow(
    station: Station,
    playing: Boolean,
    nowPlaying: String?,
    favourite: Boolean,
    onPlay: () -> Unit,
    onFavourite: () -> Unit,
) {
    val details = listOfNotNull(
        station.codec,
        station.bitrate.takeIf { it > 0 }?.let { stringResource(R.string.quality_bitrate, it) },
        station.tags.ifEmpty { null },
    ).joinToString(" · ")
    ListRow(
        title = station.name,
        // The song a station says is on replaces its details while it plays.
        subtitle = nowPlaying?.takeIf { playing } ?: details,
        leading = { StationArtwork(station.favicon) },
        highlighted = playing,
        onClick = onPlay,
        trailing = {
            IconButton(onClick = onFavourite) {
                Icon(
                    painterResource(if (favourite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                    stringResource(if (favourite) R.string.radio_unfavourite else R.string.radio_favourite),
                    tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun CountryPicker(country: String, onCountry: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        FilledTonalButton(onClick = { open = true }) {
            Text(stringResource(R.string.radio_country, countryName(country)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RadioBrowser.COUNTRIES.forEach { code ->
                DropdownMenuItem(
                    text = { Text(countryName(code), fontWeight = if (code == country) FontWeight.SemiBold else null) },
                    onClick = { open = false; onCountry(code) },
                )
            }
        }
    }
}

/** A song's tags and file facts, opened from its row menu. */
@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val unknown = stringResource(R.string.unknown_artist)
    val rows = listOfNotNull(
        R.string.details_title to song.title,
        R.string.details_artist to song.artist.orUnknown(unknown),
        R.string.details_album to song.album.orUnknown(stringResource(R.string.unknown_album)),
        R.string.details_album_artist to song.albumArtist.orUnknown(unknown),
        song.year?.let { R.string.details_year to it.toString() },
        song.genre?.let { R.string.details_genre to it },
        song.composer?.let { R.string.details_composer to it },
        (song.track % 1000).takeIf { it > 0 }?.let { R.string.details_track to it.toString() },
        R.string.details_length to duration(song.durationMs),
        R.string.details_format to qualityLine(song),
        song.dateAdded.takeIf { it > 0 }?.let { R.string.details_added to date(it) },
        song.dateModified.takeIf { it > 0 }?.let { R.string.details_modified to date(it) },
        R.string.details_file to song.path,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.action_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (label, value) ->
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
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

/** A country's name in the app's language, from its ISO code. */
fun countryName(code: String): String = Locale("", code).getDisplayCountry(Locale.getDefault()).ifEmpty { code }

/** A MediaStore timestamp, in seconds, as a medium date and short time in the app's language. */
fun date(seconds: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(seconds * 1000))

/** "3:07", or "1:02:45" past an hour. */
fun duration(ms: Long): String {
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun LazyListScope.playAllHeader(songs: List<Song>, actions: SongActions) = item {
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

fun LazyListScope.songItems(songs: List<Song>, current: String?, actions: SongActions) =
    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
        SongRow(song, current == "$SONG_PREFIX${song.id}", actions) { actions.play(songs, index) }
    }

@Composable
fun SongRow(song: Song, playing: Boolean, actions: SongActions, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListRow(
        title = song.title,
        subtitle = listOf(
            song.artist.orUnknown(stringResource(R.string.unknown_artist)),
            qualityLine(song),
        ).filter { it.isNotEmpty() }.joinToString(" · "),
        leading = { Artwork(song.uri) },
        highlighted = playing,
        badge = if (song.lossless) stringResource(R.string.quality_lossless) else null,
        onClick = onClick,
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_details)) },
                        onClick = { menu = false; actions.details(song) },
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
