package dev.scorpiontaj.encore.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import dev.scorpiontaj.encore.playback.UpdateState
import dev.scorpiontaj.encore.radio.Station
import dev.scorpiontaj.encore.settings.SettingsState
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
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
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

/** Updates, the equalizer and the app's version. */
@Composable
fun SettingsScreen(
    settings: SettingsState,
    onSettings: ((SettingsState) -> SettingsState) -> Unit,
    update: UpdateState,
    equalizer: EqualizerState,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onEqualizerEnabled: (Boolean) -> Unit,
    onBandLevel: (Int, Int) -> Unit,
    onPreset: (Int) -> Unit,
    onShowFolder: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle(stringResource(R.string.settings_playback)) }
        item {
            SwitchRow(stringResource(R.string.settings_normalize), stringResource(R.string.settings_normalize_body), settings.normalize) { on ->
                onSettings { it.copy(normalize = on) }
            }
        }
        item {
            SwitchRow(stringResource(R.string.settings_shuffle_by_album), stringResource(R.string.settings_shuffle_by_album_body), settings.shuffleByAlbum) { on ->
                onSettings { it.copy(shuffleByAlbum = on) }
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
        item { SectionTitle(stringResource(R.string.settings_hidden_folders)) }
        if (settings.hiddenFolders.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.settings_hidden_folders_empty),
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(settings.hiddenFolders.sortedWith(String.CASE_INSENSITIVE_ORDER), key = { "hidden:$it" }) { path ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(path, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2)
                TextButton(onClick = { onShowFolder(path) }) { Text(stringResource(R.string.folder_show)) }
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
        item { SectionTitle(stringResource(R.string.settings_appearance)) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { onSettings { it.copy(theme = mode) } },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.System -> R.string.theme_system
                                        ThemeMode.Light -> R.string.theme_light
                                        ThemeMode.Dark -> R.string.theme_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            item {
                SwitchRow(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_body), settings.dynamicColor) { on ->
                    onSettings { it.copy(dynamicColor = on) }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
        item { SectionTitle(stringResource(R.string.settings_updates)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
                Text(
                    when (update) {
                        UpdateState.Checking -> stringResource(R.string.update_checking)
                        UpdateState.Current -> stringResource(R.string.update_current)
                        UpdateState.Failed -> stringResource(R.string.update_failed)
                        UpdateState.Downloading -> stringResource(R.string.update_downloading)
                        is UpdateState.Offered -> stringResource(R.string.update_available, update.release.version)
                        UpdateState.Quiet -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp)) {
                    Button(onClick = onCheckUpdates, enabled = update != UpdateState.Checking && update != UpdateState.Downloading) {
                        Text(stringResource(R.string.update_check))
                    }
                    if (update is UpdateState.Offered) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onInstallUpdate) { Text(stringResource(R.string.update_install)) }
                    }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
        item { SectionTitle(stringResource(R.string.settings_equalizer)) }
        if (equalizer.bands.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.equalizer_unavailable),
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.equalizer_enabled), Modifier.weight(1f))
                    Switch(checked = equalizer.enabled, onCheckedChange = onEqualizerEnabled)
                }
            }
            if (equalizer.presets.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(equalizer.presets) { index, name ->
                            FilterChip(selected = false, onClick = { onPreset(index) }, label = { Text(name) }, enabled = equalizer.enabled)
                        }
                    }
                }
            }
            itemsIndexed(equalizer.bands) { index, band ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.equalizer_band, frequency(band.centerHz), band.levelMb / 100f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = band.levelMb.toFloat(),
                        onValueChange = { onBandLevel(index, it.toInt()) },
                        valueRange = equalizer.minMb.toFloat()..equalizer.maxMb.toFloat(),
                        enabled = equalizer.enabled,
                    )
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
        item { SectionTitle(stringResource(R.string.settings_about)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_about_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/scorpionTaj/encore".toUri()))
                }) { Text(stringResource(R.string.settings_source)) }
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

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
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

private fun frequency(hz: Int): String = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

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
                            Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                },
                onClick = { onFolder(path) },
                trailing = {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        androidx.compose.material3.IconButton(onClick = { menu = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
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

/** What was listened to most recently, newest first. */
@Composable
fun HistoryScreen(recent: List<TopSong>?, library: Library?, current: String?, onPlay: (String) -> Unit) {
    when {
        recent == null -> Loading(stringResource(R.string.library_loading))
        recent.isEmpty() -> Message(stringResource(R.string.history_empty))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(recent, key = { it.mediaId }) { entry ->
                val song = library?.song(entry.mediaId)
                ListRow(
                    title = entry.title,
                    subtitle = listOfNotNull(
                        entry.artist ?: stringResource(R.string.radio_station).takeIf { song == null },
                        pluralStringResource(R.plurals.stats_plays, entry.plays, entry.plays),
                    ).joinToString(" · "),
                    leading = { if (song != null) Artwork(song.uri) else StationArtwork(null) },
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
                        Icon(painterResource(R.drawable.ic_add), null)
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
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.smart_remove_rule))
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
