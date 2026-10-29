package dev.scorpiontaj.encore.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import dev.scorpiontaj.encore.library.SONG_PREFIX
import dev.scorpiontaj.encore.library.Summary
import dev.scorpiontaj.encore.playback.EqualizerState
import dev.scorpiontaj.encore.playback.RadioState
import dev.scorpiontaj.encore.playback.UpdateState
import dev.scorpiontaj.encore.radio.Station

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
    update: UpdateState,
    equalizer: EqualizerState,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onEqualizerEnabled: (Boolean) -> Unit,
    onBandLevel: (Int, Int) -> Unit,
    onPreset: (Int) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
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
