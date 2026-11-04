package dev.scorpiontaj.encore.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.scorpiontaj.encore.BuildConfig
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.Album
import dev.scorpiontaj.encore.library.Artist
import dev.scorpiontaj.encore.library.Library
import dev.scorpiontaj.encore.library.Period
import dev.scorpiontaj.encore.library.Rule
import dev.scorpiontaj.encore.library.RuleField
import dev.scorpiontaj.encore.library.RuleOp
import dev.scorpiontaj.encore.library.SmartPlaylist
import dev.scorpiontaj.encore.library.TopSong
import dev.scorpiontaj.encore.library.SONG_PREFIX
import dev.scorpiontaj.encore.library.Summary
import dev.scorpiontaj.encore.playback.EqualizerState
import dev.scorpiontaj.encore.playback.RadioState
import android.text.format.Formatter
import dev.scorpiontaj.encore.playback.UpdateState
import dev.scorpiontaj.encore.radio.RADIO_PREFIX
import dev.scorpiontaj.encore.radio.Station
import dev.scorpiontaj.encore.settings.SettingsState
import dev.scorpiontaj.encore.settings.ThemeFile
import dev.scorpiontaj.encore.settings.ThemeMode

/** Search across songs, albums, artists and the loaded radio stations. */
@Composable
fun SearchScreen(
    library: Library?,
    radio: RadioState,
    favourites: Set<String>,
    current: String?,
    nowPlaying: String?,
    actions: SongActions,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onStation: (Station) -> Unit,
    onFavourite: (Station) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val needle = query.trim().lowercase()
    val songs = remember(library, needle) {
        if (needle.isEmpty()) emptyList() else library?.songs.orEmpty().filter {
            it.title.lowercase().contains(needle) || it.artist.lowercase().contains(needle) ||
                it.album.lowercase().contains(needle)
        }
    }
    val albums = remember(library, needle) {
        if (needle.isEmpty()) emptyList() else library?.albums.orEmpty().filter {
            it.title.lowercase().contains(needle) || it.artist.lowercase().contains(needle)
        }
    }
    val artists = remember(library, needle) {
        if (needle.isEmpty()) emptyList() else library?.artists.orEmpty().filter { it.name.lowercase().contains(needle) }
    }
    val stations = remember(radio, needle) {
        if (needle.isEmpty()) emptyList() else (radio as? RadioState.Loaded)?.stations.orEmpty().filter {
            it.name.lowercase().contains(needle) || it.tags.lowercase().contains(needle)
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(icon(R.drawable.ic_search), null) },
            singleLine = true,
        )
        when {
            needle.isEmpty() -> Message(stringResource(R.string.search_prompt))
            songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && stations.isEmpty() ->
                Message(stringResource(R.string.search_empty))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (songs.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.tab_songs)) }
                    itemsIndexed(songs.take(MAX_SONGS), key = { _, s -> "s${s.id}" }) { index, song ->
                        SongRow(song, current == "$SONG_PREFIX${song.id}", actions) { actions.play(songs, index) }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.tab_albums)) }
                    item {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(albums, key = { it.id }) { album ->
                                AlbumCard(album, Modifier.width(140.dp)) { onAlbum(album) }
                            }
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.tab_artists)) }
                    items(artists, key = { "a${it.name}" }) { artist -> ArtistRow(artist) { onArtist(artist) } }
                }
                if (stations.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.tab_radio)) }
                    items(stations, key = { "r${it.uuid}" }) { station ->
                        StationRow(
                            station = station,
                            playing = current == station.mediaId,
                            nowPlaying = nowPlaying,
                            favourite = station.uuid in favourites,
                            onPlay = { onStation(station) },
                            onFavourite = { onFavourite(station) },
                        )
                    }
                }
            }
        }
    }
}

/** How much was listened over a period, and the songs and artists played most. */
@Composable
fun StatsScreen(
    period: Period,
    summary: Summary?,
    library: Library?,
    onPeriod: (Period) -> Unit,
    onSong: (String) -> Unit,
    onArtist: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Period.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == period,
                        onClick = { onPeriod(entry) },
                        label = { Text(stringResource(periodLabel(entry))) },
                    )
                }
            }
        }
        if (summary == null) return@LazyColumn
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(pluralStringResource(R.plurals.stats_plays, summary.plays, summary.plays), Modifier.weight(1f))
                StatTile(listenedLabel(summary.listenedMs), Modifier.weight(1f))
            }
        }
        if (summary.songs.isEmpty()) {
            item { Message(stringResource(R.string.stats_empty)) }
            return@LazyColumn
        }
        item { SectionTitle(stringResource(R.string.stats_top_songs)) }
        itemsIndexed(summary.songs, key = { _, s -> "s${s.mediaId}" }) { index, top ->
            ListRow(
                title = "${index + 1}. ${top.title}",
                subtitle = listOfNotNull(
                    top.artist,
                    pluralStringResource(R.plurals.stats_plays, top.plays, top.plays),
                ).joinToString(" · "),
                leading = { Artwork(library?.song(top.mediaId)?.uri) },
                onClick = { onSong(top.mediaId) },
            )
        }
        if (summary.artists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.stats_top_artists)) }
            itemsIndexed(summary.artists, key = { _, a -> "a${a.name}" }) { index, top ->
                ListRow(
                    title = "${index + 1}. ${top.name}",
                    subtitle = pluralStringResource(R.plurals.stats_plays, top.plays, top.plays),
                    leading = { Artwork(library?.song(top.mediaId)?.uri) },
                    onClick = { onArtist(top.name) },
                )
            }
        }
    }
}

/** The card offering a newer release, shown above the tabs until dismissed. */
@Composable
fun UpdateCard(update: UpdateState.Offered, onInstall: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.update_available, update.release.version), style = MaterialTheme.typography.titleMedium)
            update.release.notes.take(MAX_NOTES).forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
                Button(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
            }
        }
    }
}

/**
 * The update coming down or being installed: a spinner, a bar that fills as bytes arrive and the
 * sizes behind it. When the server did not say how big it is, the bar paces itself instead.
 */
@Composable
fun UpdateProgressCard(state: UpdateState, onCancel: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(if (state is UpdateState.Installing) R.string.update_installing else R.string.update_downloading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val downloading = state as? UpdateState.Downloading
            if (downloading != null) {
                val context = LocalContext.current
                val fraction = downloading.total?.takeIf { it > 0 }
                    ?.let { total -> downloading.received.toFloat() / total }
                when (fraction) {
                    null -> LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    else -> LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
                downloading.total?.let { total ->
                    Text(
                        stringResource(
                            R.string.update_sizes,
                            Formatter.formatShortFileSize(context, downloading.received),
                            Formatter.formatShortFileSize(context, total),
                        ),
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onCancel != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StatTile(text: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun listenedLabel(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes >= 60) {
        pluralStringResource(R.plurals.stats_hours, minutes / 60, minutes / 60)
    } else {
        pluralStringResource(R.plurals.stats_minutes, minutes, minutes)
    }
}

private fun periodLabel(period: Period): Int = when (period) {
    Period.Week -> R.string.stats_week
    Period.Month -> R.string.stats_month
    Period.Year -> R.string.stats_year
    Period.All -> R.string.stats_all
}


private const val MAX_SONGS = 50
private const val MAX_NOTES = 5

/** The library by its folders on disk: subfolders first, then the songs in the open folder. */
@Composable
fun FoldersScreen(
    library: Library,
    folder: String,
    current: String?,
    actions: SongActions,
    onFolder: (String) -> Unit,
    onHide: (String) -> Unit,
) {
    val subfolders = remember(library, folder) {
        library.songs.map { it.folder }.filter { it.startsWith(folder) && it != folder }
            .map { folder + it.removePrefix(folder).substringBefore('/') + "/" }
            .groupingBy { it }.eachCount().toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val songs = remember(library, folder) { library.songs.filter { it.folder == folder } }
    if (subfolders.isEmpty() && songs.isEmpty()) return Message(stringResource(R.string.library_empty))
    LazyColumn(Modifier.fillMaxSize()) {
        if (folder.isNotEmpty()) {
            item {
                Text(
                    folder,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(subfolders.entries.toList(), key = { "f${it.key}" }) { (path, count) ->
            ListRow(
                title = path.removeSuffix("/").substringAfterLast('/'),
                subtitle = pluralStringResource(R.plurals.songs_count, count, count),
                leading = {
                    Surface(
                        Modifier.size(48.dp),
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                },
                onClick = { onFolder(path) },
                trailing = {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        androidx.compose.material3.IconButton(onClick = { menu = true }) {
                            Icon(icon(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.folder_hide)) },
                                onClick = { menu = false; onHide(path) },
                            )
                        }
                    }
                },
            )
        }
        if (songs.isNotEmpty()) {
            playAllHeader(songs, actions)
            songItems(songs, current, actions)
        }
    }
}

/** The genres the library carries, alphabetical, each opening the songs tagged with it. */
@Composable
fun GenresScreen(library: Library, onGenre: (String) -> Unit) {
    val genres = remember(library) {
        library.songs
            .filter { !it.genre.isNullOrBlank() }
            .groupBy { it.genre!! }
            .map { (genre, songs) ->
                genre to songs.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
            }
            .sortedBy { (genre, _) -> genre.lowercase() }
    }
    if (genres.isEmpty()) return Message(stringResource(R.string.genres_empty))
    LazyColumn(Modifier.fillMaxSize()) {
        items(genres, key = { it.first }) { (genre, songs) ->
            ListRow(
                title = genre,
                subtitle = pluralStringResource(R.plurals.songs_count, songs.size, songs.size),
                leading = {
                    Surface(
                        Modifier.size(48.dp),
                        RoundedCornerShape(8.dp),
                        MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon(R.drawable.ic_audio_lines), null, Modifier.size(24.dp))
                        }
                    }
                },
                onClick = { onGenre(genre) },
            )
        }
    }
}

/** What was listened to most recently, newest first. */
@Composable
fun HistoryScreen(recent: List<TopSong>?, library: Library?, current: String?, onPlay: (String) -> Unit) {
    when {
        recent == null -> Loading(stringResource(R.string.library_loading))
        recent.isEmpty() -> Message(stringResource(R.string.history_empty))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(recent, key = { it.mediaId }) { entry ->
                val song = library?.song(entry.mediaId)
                // Only a station id stands for the airwaves; a file opened from another app is
                // unknown, not a radio station.
                val station = entry.mediaId.startsWith(RADIO_PREFIX)
                ListRow(
                    title = entry.title,
                    subtitle = listOfNotNull(
                        entry.artist ?: stringResource(if (station) R.string.radio_station else R.string.unknown_artist)
                            .takeIf { song == null },
                        pluralStringResource(R.plurals.stats_plays, entry.plays, entry.plays),
                    ).joinToString(" · "),
                    leading = { if (song != null) Artwork(song.uri) else StationArtwork(if (station) null else "") },
                    highlighted = current == entry.mediaId,
                    onClick = { onPlay(entry.mediaId) },
                )
            }
        }
    }
}

/** Makes or edits a smart playlist: a name, whether every rule or any rule must match, and the rules. */
@Composable
fun SmartPlaylistDialog(initial: SmartPlaylist?, onSave: (SmartPlaylist) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var matchAll by remember { mutableStateOf(initial?.matchAll ?: true) }
    val rules = remember {
        androidx.compose.runtime.mutableStateListOf<Rule>().apply {
            addAll(initial?.rules ?: listOf(Rule(RuleField.Artist, RuleOp.Contains, "")))
        }
    }
    val valid = name.isNotBlank() && rules.isNotEmpty() && rules.all { it.value.isNotBlank() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.smart_new else R.string.smart_edit)) },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val trimmed = name.trim()
                    onSave(initial?.copy(name = trimmed, rules = rules.toList(), matchAll = matchAll) ?: SmartPlaylist.new(trimmed, rules.toList(), matchAll))
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.smart_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = matchAll, onClick = { matchAll = true }, label = { Text(stringResource(R.string.smart_match_all)) })
                        FilterChip(selected = !matchAll, onClick = { matchAll = false }, label = { Text(stringResource(R.string.smart_match_any)) })
                    }
                }
                itemsIndexed(rules) { index, rule -> RuleEditor(rule, onChange = { rules[index] = it }, onRemove = { rules.removeAt(index) }) }
                item {
                    TextButton(onClick = { rules.add(Rule(RuleField.Title, RuleOp.Contains, "")) }) {
                        Icon(icon(R.drawable.ic_add), null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.smart_add_rule))
                    }
                }
            }
        },
    )
}

@Composable
private fun RuleEditor(rule: Rule, onChange: (Rule) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Picker(stringResource(fieldLabel(rule.field)), RuleField.entries.map { it to stringResource(fieldLabel(it)) }) { field ->
                onChange(Rule(field, Rule.opsFor(field).first(), if (field == rule.field) rule.value else ""))
            }
            Picker(stringResource(opLabel(rule.op)), Rule.opsFor(rule.field).map { it to stringResource(opLabel(it)) }) { op ->
                onChange(rule.copy(op = op))
            }
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = onRemove) {
                Icon(icon(R.drawable.ic_close), stringResource(R.string.smart_remove_rule))
            }
        }
        OutlinedTextField(
            value = rule.value,
            onValueChange = { onChange(rule.copy(value = it)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    stringResource(
                        when (rule.field) {
                            RuleField.Length -> R.string.smart_hint_minutes
                            RuleField.Added -> R.string.smart_hint_days
                            else -> R.string.smart_hint_text
                        },
                    ),
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (rule.field == RuleField.Length || rule.field == RuleField.Added) {
                    androidx.compose.ui.text.input.KeyboardType.Number
                } else {
                    androidx.compose.ui.text.input.KeyboardType.Text
                },
            ),
        )
    }
}

/** A small button showing [label] that opens a menu of [options]. */
@Composable
private fun <T> Picker(label: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.OutlinedButton(onClick = { open = true }) { Text(label, maxLines = 1) }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

private fun fieldLabel(field: RuleField): Int = when (field) {
    RuleField.Title -> R.string.details_title
    RuleField.Artist -> R.string.details_artist
    RuleField.Album -> R.string.details_album
    RuleField.Genre -> R.string.details_genre
    RuleField.Format -> R.string.details_format
    RuleField.Length -> R.string.details_length
    RuleField.Added -> R.string.details_added
}

private fun opLabel(op: RuleOp): Int = when (op) {
    RuleOp.Contains -> R.string.smart_contains
    RuleOp.Is -> R.string.smart_is
    RuleOp.AtLeast -> R.string.smart_at_least
    RuleOp.AtMost -> R.string.smart_at_most
    RuleOp.WithinDays -> R.string.smart_within_days
}
