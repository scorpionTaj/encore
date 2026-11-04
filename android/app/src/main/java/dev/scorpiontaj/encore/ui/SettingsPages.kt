package dev.scorpiontaj.encore.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.scorpiontaj.encore.BuildConfig
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.playback.EqualizerState
import dev.scorpiontaj.encore.playback.UpdateState
import dev.scorpiontaj.encore.settings.LyricsSource
import dev.scorpiontaj.encore.settings.Settings
import dev.scorpiontaj.encore.settings.SettingsState
import dev.scorpiontaj.encore.settings.ThemeFile
import dev.scorpiontaj.encore.settings.ThemeMode
import dev.scorpiontaj.encore.settings.Typeface
import java.util.Locale

/** The Settings categories, as the desktop's settings pages, with their icon and summary. */
enum class SettingsPage(val title: Int, val summary: Int, val icon: String) {
    General(R.string.settings_page_general, R.string.settings_page_general_body, "settings"),
    Appearance(R.string.settings_appearance, R.string.settings_page_appearance_body, "palette"),
    NowPlaying(R.string.now_playing, R.string.settings_page_now_playing_body, "disc-3"),
    Lyrics(R.string.settings_page_lyrics, R.string.settings_page_lyrics_body, "mic-vocal"),
    Playback(R.string.settings_playback, R.string.settings_page_playback_body, "play"),
    Sound(R.string.settings_equalizer, R.string.settings_page_sound_body, "sliders-horizontal"),
    Library(R.string.settings_page_library, R.string.settings_page_library_body, "library-big"),
    Storage(R.string.settings_page_storage, R.string.settings_page_storage_body, "download"),
    About(R.string.settings_about, R.string.settings_page_about_body, "info"),
}

/** Everything the Settings pages read and the actions they take. */
class SettingsModel(
    val settings: SettingsState,
    val update: UpdateState,
    val equalizer: EqualizerState,
    val themes: List<ThemeFile>,
    val cacheBytes: Long,
    val onSettings: ((SettingsState) -> SettingsState) -> Unit,
    val onCheckUpdates: () -> Unit,
    val onInstallUpdate: () -> Unit,
    val onEqualizerEnabled: (Boolean) -> Unit,
    val onBandLevel: (Int, Int) -> Unit,
    val onPreset: (Int) -> Unit,
    val onBass: (Int) -> Unit,
    val onSurround: (Int) -> Unit,
    val onSavePreset: (String) -> Unit,
    val onDeletePreset: (String) -> Unit,
    val onUsePreset: (String) -> Unit,
    val onShowFolder: (String) -> Unit,
    val onTheme: (String?) -> Unit,
    val onDeleteTheme: (ThemeFile) -> Unit,
    val onPickLyricsFolder: () -> Unit,
    val onForgetLyricsFolder: () -> Unit,
    val onRescan: () -> Unit,
    val onClearCache: () -> Unit,
    val onClearHistory: () -> Unit,
    val onLanguage: (String?) -> Unit,
    val language: String?,
)

/** The list of Settings categories. */
@Composable
fun SettingsHome(onOpen: (SettingsPage) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(SettingsPage.entries) { page ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(Modifier.size(40.dp), RoundedCornerShape(12.dp), MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon(page.icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(stringResource(page.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(page.summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(icon("chevron-right"), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One Settings category's page. */
@Composable
fun SettingsPageScreen(page: SettingsPage, model: SettingsModel) {
    // Naming the equalizer's current levels, asked for from the Sound page.
    var namingPreset by remember { mutableStateOf(false) }
    if (namingPreset) {
        NameDialog(
            title = stringResource(R.string.settings_save_preset),
            initial = "",
            onConfirm = { name ->
                if (name.isNotBlank()) model.onSavePreset(name)
                namingPreset = false
            },
            onDismiss = { namingPreset = false },
        )
    }
    LazyColumn(Modifier.fillMaxSize().padding(bottom = 16.dp)) {
        when (page) {
            SettingsPage.General -> general(model)
            SettingsPage.Appearance -> appearance(model)
            SettingsPage.NowPlaying -> nowPlaying(model)
            SettingsPage.Lyrics -> lyrics(model)
            SettingsPage.Playback -> playback(model)
            SettingsPage.Sound -> sound(model) { namingPreset = true }
            SettingsPage.Library -> library(model)
            SettingsPage.Storage -> storage(model)
            SettingsPage.About -> about(model)
        }
    }
}

private fun LazyListScope.general(model: SettingsModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item { Group(R.string.settings_language, first = true) }
        val languages = listOf(null to R.string.settings_language_system, "en" to null, "fr" to null, "ar" to null)
        items(languages) { (tag, label) ->
            Choice(
                title = label?.let { stringResource(it) } ?: Locale.forLanguageTag(tag!!).let { it.getDisplayLanguage(it).replaceFirstChar(Char::uppercase) },
                selected = model.language == tag,
                onPick = { model.onLanguage(tag) },
            )
        }
    }
    item { Group(R.string.settings_updates) }
    item {
        SwitchRow(stringResource(R.string.settings_check_updates), stringResource(R.string.settings_check_updates_body), model.settings.checkUpdates) { on ->
            model.onSettings { it.copy(checkUpdates = on) }
        }
    }
    item { UpdateRow(model) }
}

private fun LazyListScope.appearance(model: SettingsModel) {
    val settings = model.settings
    item { Group(R.string.settings_page_theme_mode, first = true) }
    item {
        ChipRow {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.theme == mode,
                    onClick = { model.onSettings { it.copy(theme = mode) } },
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
    if (settings.customTheme == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        item {
            SwitchRow(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_body), settings.dynamicColor) { on ->
                model.onSettings { it.copy(dynamicColor = on) }
            }
        }
    }
    item { Group(R.string.settings_page_icons) }
    item {
        Text(
            stringResource(R.string.settings_page_icons_body),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(ICON_PACKS) { (pack, name) ->
        Choice(
            title = name,
            selected = settings.iconPack == pack,
            onPick = { model.onSettings { it.copy(iconPack = pack) } },
            trailing = {
                // A preview of the pack: the same icons drawn from it.
                CompositionLocalProvider(LocalIconPack provides pack) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("play", "shuffle", "heart", "radio", "settings").forEach { Icon(icon(it), null, Modifier.size(20.dp)) }
                    }
                }
            },
        )
    }
    item { Group(R.string.settings_page_font) }
    items(Typeface.entries) { typeface ->
        Choice(
            title = stringResource(if (typeface == Typeface.Inter) R.string.font_inter else R.string.font_system),
            selected = settings.typeface == typeface,
            onPick = { model.onSettings { it.copy(typeface = typeface) } },
        )
    }
    item { Group(R.string.settings_page_motion) }
    item {
        SwitchRow(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_body), settings.reduceMotion) { on ->
            model.onSettings { it.copy(reduceMotion = on) }
        }
    }
    item { Group(R.string.settings_themes) }
    item {
        Text(
            stringResource(R.string.settings_themes_body),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item { Choice(stringResource(R.string.theme_encore), settings.customTheme == null, { model.onTheme(null) }) }
    items(model.themes, key = { "theme:${it.id}" }) { theme ->
        Choice(
            title = theme.name,
            subtitle = theme.author,
            selected = settings.customTheme == theme.id,
            onPick = { model.onTheme(theme.id) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemeSwatches(theme)
                    if (!theme.bundled) {
                        IconButton(onClick = { model.onDeleteTheme(theme) }) {
                            Icon(icon("trash-2"), stringResource(R.string.theme_delete))
                        }
                    }
                }
            },
        )
    }
}

private fun LazyListScope.nowPlaying(model: SettingsModel) {
    val settings = model.settings
    item { Group(R.string.settings_group_display, first = true) }
    item {
        SwitchRow(stringResource(R.string.settings_ambient), stringResource(R.string.settings_ambient_body), settings.ambient) { on ->
            model.onSettings { it.copy(ambient = on) }
        }
    }
    item {
        SwitchRow(stringResource(R.string.settings_keep_screen_on), stringResource(R.string.settings_keep_screen_on_body), settings.keepScreenOn) { on ->
            model.onSettings { it.copy(keepScreenOn = on) }
        }
    }
    item {
        SwitchRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_body), settings.haptics) { on ->
            model.onSettings { it.copy(haptics = on) }
        }
    }
    item { Group(R.string.settings_lyrics_size) }
    item {
        ChipRow {
            Settings.LYRICS_SCALES.forEach { scale ->
                FilterChip(
                    selected = settings.lyricsScale == scale,
                    onClick = { model.onSettings { it.copy(lyricsScale = scale) } },
                    label = { Text("${(scale * 100).toInt()} %") },
                )
            }
        }
    }
}

private fun LazyListScope.lyrics(model: SettingsModel) {
    val settings = model.settings
    item { Group(R.string.settings_lyrics_sources, first = true) }
    item {
        Text(
            stringResource(R.string.settings_lyrics_sources_body),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // The enabled sources in their order, then the switched-off ones.
    val ordered = settings.lyricsSources + LyricsSource.entries.filterNot { it in settings.lyricsSources }
    itemsIndexed(ordered, key = { _, source -> source.name }) { index, source ->
        val enabled = source in settings.lyricsSources
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(source.label, Modifier.weight(1f))
            if (enabled) {
                IconButton(enabled = index > 0, onClick = {
                    model.onSettings { s -> s.copy(lyricsSources = s.lyricsSources.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                }) { Icon(icon("chevron-up"), stringResource(R.string.action_move_up)) }
                IconButton(enabled = index < settings.lyricsSources.lastIndex, onClick = {
                    model.onSettings { s -> s.copy(lyricsSources = s.lyricsSources.toMutableList().apply { add(index + 1, removeAt(index)) }) }
                }) { Icon(icon("chevron-down"), stringResource(R.string.action_move_down)) }
            }
            Switch(checked = enabled, onCheckedChange = { on ->
                model.onSettings { s -> s.copy(lyricsSources = if (on) s.lyricsSources + source else s.lyricsSources - source) }
            })
        }
    }
    item { Group(R.string.settings_lyrics_folder) }
    item {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                settings.lyricsTree?.let { stringResource(R.string.settings_lyrics_folder_set, folderName(it)) }
                    ?: stringResource(R.string.settings_lyrics_folder_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = model.onPickLyricsFolder) { Text(stringResource(R.string.settings_lyrics_folder_pick)) }
                if (settings.lyricsTree != null) {
                    TextButton(onClick = model.onForgetLyricsFolder) { Text(stringResource(R.string.settings_lyrics_folder_forget)) }
                }
            }
        }
    }
}

private fun LazyListScope.playback(model: SettingsModel) {
    val settings = model.settings
    item { Group(R.string.settings_crossfade, first = true) }
    item {
        Text(
            stringResource(R.string.settings_crossfade_body),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        ChipRow {
            Settings.CROSSFADES.forEach { seconds ->
                FilterChip(
                    selected = settings.crossfadeSeconds == seconds,
                    onClick = { model.onSettings { it.copy(crossfadeSeconds = seconds) } },
                    label = { Text(if (seconds == 0) stringResource(R.string.crossfade_off) else stringResource(R.string.crossfade_seconds, seconds)) },
                )
            }
        }
    }
    item { Group(R.string.settings_group_playing) }
    item {
        SwitchRow(stringResource(R.string.settings_normalize), stringResource(R.string.settings_normalize_body), settings.normalize) { on ->
            model.onSettings { it.copy(normalize = on) }
        }
    }
    item {
        SwitchRow(stringResource(R.string.settings_shuffle_by_album), stringResource(R.string.settings_shuffle_by_album_body), settings.shuffleByAlbum) { on ->
            model.onSettings { it.copy(shuffleByAlbum = on) }
        }
    }
    item { InfoRow(stringResource(R.string.settings_gapless), stringResource(R.string.settings_gapless_body)) }
}

private fun LazyListScope.sound(model: SettingsModel, onAskSavePreset: () -> Unit) {
    val equalizer = model.equalizer
    item { Group(R.string.settings_equalizer, first = true) }
    if (equalizer.bands.isEmpty()) {
        item { InfoRow(stringResource(R.string.settings_equalizer), stringResource(R.string.equalizer_unavailable)) }
    } else {
        item { SwitchRow(stringResource(R.string.equalizer_enabled), "", equalizer.enabled, model.onEqualizerEnabled) }
        if (equalizer.presets.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(equalizer.presets) { index, name ->
                        FilterChip(selected = false, onClick = { model.onPreset(index) }, label = { Text(name) }, enabled = equalizer.enabled)
                    }
                }
            }
        }
        itemsIndexed(equalizer.bands) { index, band ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                val hz = band.centerHz
                Text(
                    stringResource(R.string.equalizer_band, if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", band.levelMb / 100f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = band.levelMb.toFloat(),
                    onValueChange = { model.onBandLevel(index, it.toInt()) },
                    valueRange = equalizer.minMb.toFloat()..equalizer.maxMb.toFloat(),
                    enabled = equalizer.enabled,
                )
            }
        }
        // The device's own effects sit on the same audio session as the equalizer.
        if (equalizer.bassAvailable) {
            item { Group(R.string.settings_bass) }
            item { StrengthRow(stringResource(R.string.settings_bass), equalizer.bassStrength, model.onBass) }
        }
        if (equalizer.surroundAvailable) {
            item { Group(R.string.settings_surround) }
            item { StrengthRow(stringResource(R.string.settings_surround), equalizer.surroundStrength, model.onSurround) }
        }
    }
    // Presets the person saved from the levels above, as on the desktop's equalizer.
    if (model.settings.equalizerPresets.isNotEmpty() || equalizer.bands.isNotEmpty()) {
        item { Group(R.string.settings_own_presets) }
        model.settings.equalizerPresets.keys.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { name ->
            item("preset:$name") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { model.onUsePreset(name) }, enabled = equalizer.enabled) {
                        Icon(icon("play"), stringResource(R.string.preset_use))
                    }
                    IconButton(onClick = { model.onDeletePreset(name) }) {
                        Icon(icon("trash-2"), stringResource(R.string.preset_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (equalizer.bands.isNotEmpty()) {
            item { TextButton(onClick = onAskSavePreset, Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.settings_save_preset)) } }
        }
    }
}

/** A 0–100 % strength slider for an audio effect. */
@Composable
private fun StrengthRow(label: String, strength: Int, onStrength: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("$label · ${(strength / 10).coerceIn(0, 100)} %", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = strength.toFloat(),
            onValueChange = { onStrength(it.toInt()) },
            valueRange = 0f..1000f,
        )
    }
}

private fun LazyListScope.library(model: SettingsModel) {
    val settings = model.settings
    item { Group(R.string.settings_page_library, first = true) }
    item {
        SwitchRow(stringResource(R.string.settings_watch_folders), stringResource(R.string.settings_watch_folders_body), settings.watchFolders) { on ->
            model.onSettings { it.copy(watchFolders = on) }
        }
    }
    item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Button(onClick = model.onRescan) { Text(stringResource(R.string.settings_rescan)) }
        }
    }
    item { Group(R.string.settings_hidden_folders) }
    if (settings.hiddenFolders.isEmpty()) {
        item { InfoRow("", stringResource(R.string.settings_hidden_folders_empty)) }
    }
    items(settings.hiddenFolders.sortedWith(String.CASE_INSENSITIVE_ORDER), key = { "hidden:$it" }) { path ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon("folder"), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(path, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2)
            TextButton(onClick = { model.onShowFolder(path) }) { Text(stringResource(R.string.folder_show)) }
        }
    }
}

private fun LazyListScope.storage(model: SettingsModel) {
    item { Group(R.string.settings_page_storage, first = true) }
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_cache))
                Text(
                    stringResource(R.string.settings_cache_body, android.text.format.Formatter.formatShortFileSize(LocalContext.current, model.cacheBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = model.onClearCache) { Text(stringResource(R.string.settings_clear)) }
        }
    }
    item {
        var confirm by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_history))
                Text(
                    stringResource(R.string.settings_history_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.settings_clear)) }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.settings_history_confirm)) },
                text = { Text(stringResource(R.string.settings_history_confirm_body)) },
                confirmButton = { TextButton(onClick = { confirm = false; model.onClearHistory() }) { Text(stringResource(R.string.settings_clear)) } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

private fun LazyListScope.about(model: SettingsModel) {
    item {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)), color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon("encore"), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Text(stringResource(R.string.app_name), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item { Text(stringResource(R.string.settings_about_body), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    item { UpdateRow(model) }
    item { Group(R.string.settings_page_credits) }
    item { InfoRow("Tajeddine Bourhim", stringResource(R.string.settings_role_maintainer)) }
    item { InfoRow(stringResource(R.string.settings_license), stringResource(R.string.settings_license_body)) }
    item {
        val context = LocalContext.current
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/scorpionTaj/encore".toUri()))
            }) { Text(stringResource(R.string.settings_source)) }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/scorpionTaj/encore/issues".toUri()))
            }) { Text(stringResource(R.string.settings_report)) }
        }
    }
}

@Composable
private fun UpdateRow(model: SettingsModel) {
    val update = model.update
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            when (update) {
                UpdateState.Checking -> stringResource(R.string.update_checking)
                UpdateState.Current -> stringResource(R.string.update_current)
                UpdateState.Failed -> stringResource(R.string.update_failed)
                UpdateState.Downloading -> stringResource(R.string.update_downloading)
                is UpdateState.Offered -> stringResource(R.string.update_available, update.release.version)
                UpdateState.Quiet -> stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = model.onCheckUpdates, enabled = update != UpdateState.Checking && update != UpdateState.Downloading) {
                Text(stringResource(R.string.update_check))
            }
            if (update is UpdateState.Offered) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = model.onInstallUpdate) { Text(stringResource(R.string.update_install)) }
            }
        }
    }
}

/** A section heading; every section but a page's first is set off by a line above it. */
@Composable
private fun Group(title: Int, first: Boolean = false) {
    Column {
        if (!first) HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionTitle(stringResource(title))
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            if (body.isNotEmpty()) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title.isNotEmpty()) Text(title)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Choice(
    title: String,
    selected: Boolean,
    onPick: () -> Unit,
    subtitle: String = "",
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick).padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onPick)
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** The last part of a granted folder's path, such as "Music". */
private fun folderName(tree: String): String =
    android.net.Uri.decode(tree).substringAfterLast(':').ifEmpty { "/" }
