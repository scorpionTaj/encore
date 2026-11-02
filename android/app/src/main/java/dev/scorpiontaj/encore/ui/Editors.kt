package dev.scorpiontaj.encore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.LyricLine
import dev.scorpiontaj.encore.library.Playlist
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.library.Tags
import dev.scorpiontaj.encore.settings.ThemeFile
import java.util.Locale

/** Asks for a name, as when making or renaming a playlist. */
@Composable
fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.smart_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Picks the playlist to add a song to, or makes a new one with it. */
@Composable
fun AddToPlaylistDialog(playlists: List<Playlist>, onPick: (Playlist) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_add_to)) },
        text = {
            LazyColumn {
                item {
                    ListRow(
                        title = stringResource(R.string.playlist_new),
                        subtitle = "",
                        leading = { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(24.dp)) },
                        onClick = onNew,
                    )
                }
                items(playlists, key = { it.id }) { playlist ->
                    ListRow(
                        title = playlist.name,
                        subtitle = pluralStringResource(R.plurals.songs_count, playlist.songs.size, playlist.songs.size),
                        leading = { Icon(painterResource(R.drawable.ic_queue_music), null, Modifier.size(24.dp)) },
                        onClick = { onPick(playlist) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Edits a song's title, artist, album, album artist, year, genre and track number. */
@Composable
fun TagEditorDialog(song: Song, initial: Tags, onSave: (Tags) -> Unit, onDismiss: () -> Unit) {
    var tags by remember(song.id, initial) { mutableStateOf(initial) }
    val fields: List<Triple<Int, String, (String) -> Tags>> = listOf(
        Triple(R.string.details_title, tags.title) { v -> tags.copy(title = v) },
        Triple(R.string.details_artist, tags.artist) { v -> tags.copy(artist = v) },
        Triple(R.string.details_album, tags.album) { v -> tags.copy(album = v) },
        Triple(R.string.details_album_artist, tags.albumArtist) { v -> tags.copy(albumArtist = v) },
        Triple(R.string.details_year, tags.year) { v -> tags.copy(year = v.filter(Char::isDigit).take(4)) },
        Triple(R.string.details_genre, tags.genre) { v -> tags.copy(genre = v) },
        Triple(R.string.details_track, tags.track) { v -> tags.copy(track = v.filter(Char::isDigit).take(3)) },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_edit)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(fields, key = { it.first }) { (label, value, change) ->
                    val numeric = label == R.string.details_year || label == R.string.details_track
                    OutlinedTextField(
                        value = value,
                        onValueChange = { tags = change(it) },
                        label = { Text(stringResource(label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = tags.title.isNotBlank(), onClick = { onSave(tags) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The lyrics editor, as on the desktop: a row per line with a time chip that stamps the playback
 * position when tapped, the line's text, and a button to remove it.
 */
@Composable
fun LyricsEditor(
    song: Song,
    initial: List<LyricLine>,
    positionMs: () -> Long,
    onSave: (List<LyricLine>) -> Unit,
    onClose: () -> Unit,
) {
    val lines = remember(song.id) {
        mutableStateListOf<LyricLine>().apply { addAll(initial.ifEmpty { listOf(LyricLine(-1, "")) }) }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lyrics_edit), style = MaterialTheme.typography.titleMedium)
                    Text(song.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = { onSave(lines.filter { it.text.isNotBlank() || it.startMs >= 0 }) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
            Text(
                stringResource(R.string.lyrics_edit_hint),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                itemsIndexed(lines) { index, line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { lines[index] = line.copy(startMs = positionMs()) },
                            label = { Text(stamp(line.startMs)) },
                        )
                        OutlinedTextField(
                            value = line.text,
                            onValueChange = { lines[index] = line.copy(text = it) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        )
                        IconButton(onClick = { lines.removeAt(index) }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.lyrics_remove_line))
                        }
                    }
                }
                item {
                    TextButton(onClick = { lines.add(LyricLine(-1, "")) }) {
                        Icon(painterResource(R.drawable.ic_add), null)
                        Text(stringResource(R.string.lyrics_add_line), Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

/** A theme from a link, shown before it is installed. */
@Composable
fun ThemeOfferDialog(theme: ThemeFile, onInstall: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_install_title)) },
        text = {
            Column {
                Text(theme.name, style = MaterialTheme.typography.titleMedium)
                if (theme.author.isNotEmpty()) {
                    Text(stringResource(R.string.theme_by, theme.author), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ThemeSwatches(theme, Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.theme_install)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A lyric time as a chip label: mm:ss.xx, or dashes when the line has none yet. */
private fun stamp(ms: Long): String {
    if (ms < 0) return "--:--.--"
    val centis = ms / 10
    return String.format(Locale.ROOT, "%02d:%02d.%02d", centis / 6000, centis / 100 % 60, centis % 100)
}
