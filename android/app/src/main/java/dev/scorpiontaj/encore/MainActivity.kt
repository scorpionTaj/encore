package dev.scorpiontaj.encore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import dev.scorpiontaj.encore.ui.SettingsHome
import dev.scorpiontaj.encore.ui.SettingsModel
import dev.scorpiontaj.encore.ui.SettingsPage
import dev.scorpiontaj.encore.ui.SettingsPageScreen
import android.app.Activity
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import dev.scorpiontaj.encore.library.LyricLine
import dev.scorpiontaj.encore.library.Tags
import dev.scorpiontaj.encore.playback.LyricsState
import dev.scorpiontaj.encore.ui.AddToPlaylistDialog
import dev.scorpiontaj.encore.ui.LyricsEditor
import dev.scorpiontaj.encore.ui.NameDialog
import dev.scorpiontaj.encore.ui.TagEditorDialog
import dev.scorpiontaj.encore.ui.ThemeOfferDialog
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scorpiontaj.encore.library.Album
import dev.scorpiontaj.encore.library.Artist
import dev.scorpiontaj.encore.library.Library
import dev.scorpiontaj.encore.library.Playlist
import dev.scorpiontaj.encore.library.SmartPlaylist
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.playback.PlayerViewModel
import dev.scorpiontaj.encore.playback.RadioState
import dev.scorpiontaj.encore.playback.UpdateState
import dev.scorpiontaj.encore.ui.AlbumsScreen
import dev.scorpiontaj.encore.ui.ArtistsScreen
import dev.scorpiontaj.encore.ui.CollectionScreen
import dev.scorpiontaj.encore.ui.EncoreTheme
import dev.scorpiontaj.encore.ui.hapticFeedback
import dev.scorpiontaj.encore.ui.icon
import dev.scorpiontaj.encore.ui.isDark
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.scorpiontaj.encore.ui.FoldersScreen
import dev.scorpiontaj.encore.ui.GenresScreen
import dev.scorpiontaj.encore.ui.HomeScreen
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import dev.scorpiontaj.encore.ui.HistoryScreen
import dev.scorpiontaj.encore.ui.Loading
import dev.scorpiontaj.encore.ui.MiniPlayer
import dev.scorpiontaj.encore.ui.NowPlayingScreen
import dev.scorpiontaj.encore.ui.PlaylistsScreen
import dev.scorpiontaj.encore.ui.RadioScreen
import dev.scorpiontaj.encore.ui.SearchScreen
import dev.scorpiontaj.encore.ui.SmartPlaylistDialog
import dev.scorpiontaj.encore.ui.SongDetailsDialog
import dev.scorpiontaj.encore.ui.StatsScreen
import dev.scorpiontaj.encore.ui.UpdateCard
import dev.scorpiontaj.encore.ui.UpdateProgressCard
import dev.scorpiontaj.encore.ui.SongActions
import dev.scorpiontaj.encore.ui.SongsScreen
import dev.scorpiontaj.encore.ui.orUnknown

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
        enableEdgeToEdge()
        openFrom(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val themes by vm.themes.collectAsStateWithLifecycle()
            val custom = themes.firstOrNull { it.id == settings.customTheme }
            // Status and navigation bar icons follow the app's theme, not only the system's.
            val dark = isDark(settings, custom)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            EncoreTheme(settings, custom) { EncoreApp(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /**
     * Asks for the screen's fastest mode at the current resolution, since some devices keep apps
     * at 60 Hz unless the app itself asks. Compose then animates at whatever the display runs.
     */
    private fun requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.modeId == current.modeId) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    /** Plays an audio file another app asked Encore to open, or offers a theme from a theme link. */
    private fun openFrom(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        // encore://theme/… and sonora://theme/… links offer a theme; anything else is a file to play.
        if (uri.scheme == "encore" || uri.scheme == "sonora") vm.offerTheme(uri) else vm.playUri(uri)
        setIntent(Intent(this, MainActivity::class.java))
    }
}

/** The bottom-bar tabs, in order. */
private enum class Tab(val label: Int, val icon: Int) {
    Home(R.string.page_home, R.drawable.ic_home),
    Songs(R.string.tab_songs, R.drawable.ic_music_note),
    Albums(R.string.tab_albums, R.drawable.ic_album),
    Artists(R.string.tab_artists, R.drawable.ic_person),
    Playlists(R.string.tab_playlists, R.drawable.ic_queue_music),
    Radio(R.string.tab_radio, R.drawable.ic_radio),
}

/** A collection opened from a tab, shown over it until Back. */
private sealed interface Detail {
    data class OfAlbum(val album: Album) : Detail
    data class OfArtist(val artist: Artist) : Detail
    data class OfPlaylist(val playlist: Playlist) : Detail
    data class OfSmart(val playlist: SmartPlaylist) : Detail
}

/**
 * Keeps an open collection across rotation by the keys that resolve it again — an album or
 * playlist id, an artist or smart-playlist name — since the objects themselves are too big for
 * the saved state. [DetailScreen] re-reads the real songs from the library.
 */
private val DetailSaver = listSaver<Detail?, String>(
    save = { detail ->
        when (detail) {
            is Detail.OfAlbum -> listOf("album", detail.album.id.toString(), detail.album.title)
            is Detail.OfArtist -> listOf("artist", detail.artist.name)
            is Detail.OfPlaylist -> listOf("playlist", detail.playlist.id.toString(), detail.playlist.name)
            is Detail.OfSmart -> listOf("smart", detail.playlist.id, detail.playlist.name)
            null -> emptyList()
        }
    },
    restore = { parts ->
        when (parts.getOrNull(0)) {
            "album" -> Detail.OfAlbum(Album(parts[1].toLongOrNull() ?: -1, parts[2], "", emptyList()))
            "artist" -> Detail.OfArtist(Artist(parts[1], emptyList()))
            "playlist" -> Detail.OfPlaylist(Playlist(parts[1].toLongOrNull() ?: -1, parts[2], emptyList()))
            "smart" -> Detail.OfSmart(SmartPlaylist(parts[1], parts[2], emptyList(), true))
            else -> null
        }
    },
)

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/** A page opened from the top bar, shown over the tabs until Back. */
private enum class Page(val title: Int, val icon: Int) {
    Search(R.string.action_search, R.drawable.ic_search),
    Stats(R.string.page_stats, R.drawable.ic_bar_chart),
    History(R.string.page_history, R.drawable.ic_history),
    Genres(R.string.page_genres, R.drawable.ic_audio_lines),
    Folders(R.string.page_folders, R.drawable.ic_folder),
    Settings(R.string.page_settings, R.drawable.ic_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncoreApp(vm: PlayerViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val library by vm.library.collectAsStateWithLifecycle()
    val radio by vm.radio.collectAsStateWithLifecycle()
    val country by vm.country.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val equalizer by vm.equalizer.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val smart by vm.smartPlaylists.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val resume by vm.resume.collectAsStateWithLifecycle()
    val homeStats by vm.homeStats.collectAsStateWithLifecycle()
    val homeSongs by vm.homeSongs.collectAsStateWithLifecycle()
    val missingCovers by vm.missingCovers.collectAsStateWithLifecycle()
    var folder by rememberSaveable { mutableStateOf("") }
    /** The genre whose songs the Genres page shows, or the list of all genres. */
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SmartPlaylist?>(null) }
    var creating by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val reduceMotion = settings.reduceMotion
    val haptic = hapticFeedback()
    LaunchedEffect(settingsPage) { if (settingsPage == SettingsPage.Storage) vm.refreshCacheSize() }
    val pickLyricsFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let(vm::grantLyricsFolder)
    }
    /** A song waiting for Android's delete confirmation, held by the model so rotation keeps it. */
    val confirmDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val song = vm.pendingDelete ?: return@rememberLauncherForActivityResult
        vm.pendingDelete = null
        if (result.resultCode == Activity.RESULT_OK) vm.songDeleted(song)
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var detail by rememberSaveable(stateSaver = DetailSaver) { mutableStateOf<Detail?>(null) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<Song?>(null) }
    val themes by vm.themes.collectAsStateWithLifecycle()
    val offeredTheme by vm.offeredTheme.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    /** The song a playlist is being picked for, the playlist being renamed, and whether a new one is being named. */
    var addingToPlaylist by remember { mutableStateOf<Song?>(null) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var naming by remember { mutableStateOf(false) }
    var editingTags by remember { mutableStateOf<Song?>(null) }
    var editingLyrics by remember { mutableStateOf<Song?>(null) }
    /** Tags and lyrics waiting on a system prompt, saved again once the person allows it. */
    val allowWrite = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (song, tags) = vm.pendingTags ?: return@rememberLauncherForActivityResult
        vm.pendingTags = null
        if (result.resultCode == Activity.RESULT_OK) scope.launch {
            // The provider may ask again; the effect below relaunches when it does.
            vm.saveTags(song, tags)?.let { request -> vm.pendingWriteRequest = request }
        }
    }
    LaunchedEffect(vm.pendingWriteRequest) {
        val request = vm.pendingWriteRequest ?: return@LaunchedEffect
        vm.pendingWriteRequest = null
        allowWrite.launch(IntentSenderRequest.Builder(request).build())
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val (song, lines) = vm.pendingLyrics ?: return@rememberLauncherForActivityResult
        vm.pendingLyrics = null
        if (tree == null) return@rememberLauncherForActivityResult
        vm.grantLyricsFolder(tree)
        scope.launch {
            if (vm.saveLyrics(song, lines)) editingLyrics = null else vm.showNotice(R.string.notice_lyrics_folder)
        }
    }
    val actions = remember(vm) {
        SongActions(
            play = { songs, start -> vm.play(songs, start) },
            shuffle = { songs -> vm.play(songs, shuffle = true) },
            playNext = vm::playNext,
            addToQueue = vm::addToQueue,
            details = { details = it },
            addToPlaylist = { addingToPlaylist = it },
            editTags = { editingTags = it },
            share = vm::shareSong,
            openAlbum = { song -> vm.library.value?.albums?.firstOrNull { it.id == song.albumId }?.let { nowPlaying = false; detail = Detail.OfAlbum(it) } },
            openArtist = { song -> vm.library.value?.artists?.firstOrNull { it.name == song.artist }?.let { nowPlaying = false; detail = Detail.OfArtist(it) } },
            delete = { song ->
                scope.launch {
                    when (val request = vm.deleteSong(song)) {
                        null -> vm.songDeleted(song)
                        else -> {
                            vm.pendingDelete = song
                            confirmDelete.launch(IntentSenderRequest.Builder(request).build())
                        }
                    }
                }
            },
        )
    }
    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        // Clearing first would restart this effect and cancel the snackbar, so clear once it is done.
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(
            shown.count?.let { resources.getQuantityString(shown.message, it, *shown.args.toTypedArray()) }
                ?: resources.getString(shown.message, *shown.args.toTypedArray()),
        )
        vm.clearNotice()
    }
    // A radio stream's current song, which the service puts in the artist field.
    val streamSong = player.metadata.artist?.toString()?.takeIf { player.metadata.station != null }

    LaunchedEffect(granted) { if (granted && vm.library.value == null) vm.loadLibrary() }
    LaunchedEffect(tab, page) { if (Tab.entries[tab] == Tab.Radio || page == Page.Search) vm.loadRadio() }
    LaunchedEffect(tab) { if (Tab.entries[tab] == Tab.Home) vm.loadHome() }
    LaunchedEffect(page) {
        if (page == Page.Stats) vm.loadStats()
        if (page == Page.History) vm.loadRecent()
    }
    BackHandler(enabled = detail != null && !nowPlaying) { detail = null }
    BackHandler(enabled = page == Page.Settings && settingsPage != null && !nowPlaying) { settingsPage = null }
    BackHandler(enabled = page == Page.Folders && folder.isNotEmpty() && detail == null && !nowPlaying) {
        folder = folder.removeSuffix("/").substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    }
    BackHandler(enabled = page == Page.Genres && genre != null && detail == null && !nowPlaying) {
        genre = null
    }
    BackHandler(
        enabled = page != null && detail == null && !nowPlaying &&
            !(page == Page.Folders && folder.isNotEmpty()) && !(page == Page.Settings && settingsPage != null) &&
            !(page == Page.Genres && genre != null),
    ) {
        page = null
    }
    BackHandler(enabled = nowPlaying) { nowPlaying = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        val open = page
                        val section = settingsPage
                        Text(
                            when {
                                open == Page.Settings && section != null -> stringResource(section.title)
                                open != null -> stringResource(open.title)
                                else -> stringResource(Tab.entries[tab].label)
                            },
                            // A tab's own page carries its name large, as the page's heading.
                            style = if (page == null && detail == null) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        if (detail != null || page != null) {
                            IconButton(onClick = {
                                when {
                                    detail != null -> detail = null
                                    page == Page.Settings && settingsPage != null -> settingsPage = null
                                    page == Page.Genres && genre != null -> genre = null
                                    else -> page = null
                                }
                            }) {
                                Icon(icon(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = {
                        if (page == null && detail == null) {
                            IconButton(onClick = { page = Page.Search }) {
                                Icon(icon(R.drawable.ic_search), stringResource(R.string.action_search))
                            }
                            Box {
                                IconButton(onClick = { overflow = true }) {
                                    Icon(icon(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                                }
                                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                    Page.entries.filter { it != Page.Search }.forEach { entry ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(entry.title)) },
                                            leadingIcon = { Icon(icon(entry.icon), null) },
                                            onClick = {
                                                overflow = false
                                                if (entry == Page.Folders) folder = ""
                                                page = entry
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = player.hasMedia,
                        enter = if (reduceMotion) EnterTransition.None else expandVertically() + fadeIn(),
                        exit = if (reduceMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
                    ) { MiniPlayer(player, library, vm) { nowPlaying = true } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        val colors = MaterialTheme.colorScheme
                        Tab.entries.forEachIndexed { index, entry ->
                            NavigationBarItem(
                                selected = tab == index && page == null,
                                onClick = { haptic(HapticFeedbackType.TextHandleMove); tab = index; detail = null; page = null },
                                icon = { Icon(icon(entry.icon), null) },
                                label = { Text(stringResource(entry.label), maxLines = 1) },
                                // The chosen tab shows in the accent colour alone, without the pill behind it.
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primary,
                                    selectedTextColor = colors.primary,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                (update as? UpdateState.Offered)?.takeIf { page == null }?.let {
                    UpdateCard(it, onInstall = vm::installUpdate, onDismiss = vm::dismissUpdate)
                }
                when {
                    page == null && update is UpdateState.Downloading -> UpdateProgressCard(update, onCancel = vm::cancelUpdateDownload)
                    page == null && update is UpdateState.Installing -> UpdateProgressCard(update, onCancel = null)
                    else -> {}
                }
                val current = player.mediaId
                val lib = library
                val open = detail
                val openArtist = { name: String ->
                    lib?.artists?.firstOrNull { it.name == name }?.let { detail = Detail.OfArtist(it) }
                }
                AnimatedContent(
                    targetState = Screen(tab, page, open, settingsPage),
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        // Drilling in slides from the right, coming back from the left, and
                        // switching tabs only fades.
                        when {
                            reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                            targetState.depth > initialState.depth ->
                                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 }) togetherWith fadeOut(tween(140))
                            targetState.depth < initialState.depth ->
                                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 10 }) togetherWith fadeOut(tween(140))
                            else -> fadeIn(tween(220)) togetherWith fadeOut(tween(140))
                        }
                    },
                    contentKey = { it },
                    label = "screen",
                ) { screen ->
                    // Each screen draws from its own target, so the outgoing one keeps its content while it fades.
                    val tab = screen.tab
                    val page = screen.page
                    val open = screen.detail
                    val shownSettings = screen.settingsPage
                    Box(Modifier.fillMaxSize()) {
                    when {
                        open != null -> DetailScreen(open, lib, smart, current, actions, vm::removeFromPlaylist)
                        page == Page.Search -> SearchScreen(
                            library = lib,
                            radio = radio,
                            favourites = favourites,
                            current = current,
                            nowPlaying = streamSong,
                            actions = actions,
                            onAlbum = { detail = Detail.OfAlbum(it) },
                            onArtist = { detail = Detail.OfArtist(it) },
                            onStation = vm::play,
                            onFavourite = vm::toggleFavourite,
                        )
                        page == Page.Stats -> StatsScreen(
                            period = period,
                            summary = summary,
                            library = lib,
                            onPeriod = vm::loadStats,
                            onSong = { id -> lib?.song(id)?.let { vm.play(listOf(it)) } },
                            onArtist = { openArtist(it) },
                        )
                        page == Page.History -> HistoryScreen(recent, lib, current) { id ->
                            lib?.song(id)?.let { vm.play(listOf(it)) }
                                ?: (radio as? RadioState.Loaded)?.stations?.firstOrNull { it.mediaId == id }?.let(vm::play)
                        }
                        page == Page.Genres -> when (lib) {
                            null -> Loading(stringResource(R.string.library_loading))
                            else -> when (val open = genre) {
                                null -> GenresScreen(lib) { genre = it }
                                else -> CollectionScreen(
                                    open,
                                    "",
                                    lib.songs.filter { it.genre == open }
                                        .sortedWith(compareBy({ it.album.lowercase() }, { it.track })),
                                    current,
                                    actions,
                                )
                            }
                        }
                        page == Page.Folders -> when (lib) {
                            null -> Loading(stringResource(R.string.library_loading))
                            else -> FoldersScreen(lib, folder, current, actions, onFolder = { folder = it }, onHide = vm::hideFolder)
                        }
                        page == Page.Settings -> when (shownSettings) {
                            null -> SettingsHome { settingsPage = it }
                            else -> SettingsPageScreen(
                                shownSettings,
                                SettingsModel(
                                    settings = settings,
                                    update = update,
                                    equalizer = equalizer,
                                    themes = themes,
                                    cacheBytes = cacheBytes,
                                    onSettings = vm::updateSettings,
                                    onCheckUpdates = { vm.checkForUpdates(asked = true) },
                                    onInstallUpdate = vm::installUpdate,
                                    onCancelUpdate = vm::cancelUpdateDownload,
                                    onEqualizerEnabled = vm::setEqualizerEnabled,
                                    onBandLevel = vm::setBandLevel,
                                    onPreset = vm::useEqualizerPreset,
                                    onBass = vm::setBassStrength,
                                    onSurround = vm::setSurroundStrength,
                                    onSavePreset = vm::saveEqualizerPreset,
                                    onDeletePreset = vm::deleteEqualizerPreset,
                                    onUsePreset = vm::useEqualizerPreset,
                                    onShowFolder = vm::showFolder,
                                    onTheme = vm::useTheme,
                                    onDeleteTheme = vm::deleteTheme,
                                    onPickLyricsFolder = { pickLyricsFolder.launch(musicFolder()) },
                                    onForgetLyricsFolder = vm::forgetLyricsFolder,
                                    onRescan = vm::rescanLibrary,
                                    onClearCache = vm::clearCache,
                                    onClearHistory = vm::clearHistory,
                                    onLanguage = vm::setLanguage,
                                    language = vm.language,
                                ),
                            )
                        }
                        Tab.entries[tab] == Tab.Radio -> RadioScreen(
                            state = radio,
                            country = country,
                            favourites = favourites,
                            current = current,
                            nowPlaying = streamSong,
                            onPlay = vm::play,
                            onRetry = vm::retryRadio,
                            onCountry = vm::pickCountry,
                            onFavourite = vm::toggleFavourite,
                        )
                        !granted -> PermissionPrompt { request.launch(AUDIO_PERMISSION) }
                        lib == null -> Loading(stringResource(R.string.library_loading))
                        else -> when (Tab.entries[tab]) {
                            Tab.Home -> HomeScreen(
                                resume = resume,
                                stats = homeStats,
                                played = homeSongs,
                                library = lib,
                                current = current,
                                actions = actions,
                                onLoad = vm::loadHome,
                            )
                            Tab.Songs -> SongsScreen(
                                lib.songs, lib.duplicates, missingCovers, current, actions, vm::findMissingCovers,
                                sortName = settings.songSort,
                                filterName = settings.songFilter,
                                onSortFilter = { sort, filter -> vm.updateSettings { it.copy(songSort = sort, songFilter = filter) } },
                            )
                            Tab.Albums -> AlbumsScreen(lib.albums) { detail = Detail.OfAlbum(it) }
                            Tab.Artists -> ArtistsScreen(lib.artists) { detail = Detail.OfArtist(it) }
                            Tab.Playlists -> PlaylistsScreen(
                                playlists = lib.playlists,
                                smart = smart,
                                library = lib,
                                onOpen = { detail = Detail.OfPlaylist(it) },
                                onOpenSmart = { detail = Detail.OfSmart(it) },
                                onNewSmart = { creating = true },
                                onEditSmart = { editing = it },
                                onDeleteSmart = vm::deleteSmartPlaylist,
                                onNew = { naming = true },
                                onRename = { renaming = it },
                                onDelete = vm::deletePlaylist,
                            )
                            Tab.Radio -> Unit
                        }
                    }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = nowPlaying && player.hasMedia,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(
                state = player,
                library = library,
                lyrics = lyrics,
                sleep = sleep,
                recording = recording,
                vm = vm,
                onAddToPlaylist = { addingToPlaylist = it },
                onEditTags = { editingTags = it },
                onEditLyrics = { editingLyrics = it },
            ) { nowPlaying = false }
        }

        editingLyrics?.let { song ->
            // Reading may still be under way; the editor fills in as the lines arrive.
            LaunchedEffect(song) { vm.loadLyrics(song) }
            val shown = (lyrics as? LyricsState.Found)?.takeIf { it.mediaId == song.toMediaItem().mediaId }?.lyrics?.lines.orEmpty()
            LyricsEditor(
                song = song,
                initial = shown,
                positionMs = { if (player.mediaId == song.toMediaItem().mediaId) vm.positionMs else -1 },
                onSave = { lines ->
                    scope.launch {
                        when (vm.saveLyrics(song, lines)) {
                            true -> editingLyrics = null
                            // No folder holds the song yet: ask for one, starting at Music.
                            false -> {
                                vm.pendingLyrics = song to lines
                                vm.showNotice(R.string.notice_lyrics_pick_folder)
                                pickFolder.launch(musicFolder())
                            }
                        }
                    }
                },
                onClose = { editingLyrics = null },
            )
        }

        details?.let { SongDetailsDialog(it) { details = null } }
        addingToPlaylist?.let { song ->
            AddToPlaylistDialog(
                playlists = library?.playlists.orEmpty(),
                onPick = { playlist ->
                    vm.addToPlaylist(playlist, listOf(song))
                    addingToPlaylist = null
                },
                onNew = { naming = true },
                onDismiss = { addingToPlaylist = null },
            )
        }
        if (naming) {
            NameDialog(
                title = stringResource(R.string.playlist_new),
                initial = "",
                onConfirm = { name ->
                    vm.createPlaylist(name, listOfNotNull(addingToPlaylist))
                    naming = false
                    addingToPlaylist = null
                },
                onDismiss = { naming = false },
            )
        }
        renaming?.let { playlist ->
            NameDialog(
                title = stringResource(R.string.action_rename),
                initial = playlist.name,
                onConfirm = { name ->
                    vm.renamePlaylist(playlist, name)
                    renaming = null
                },
                onDismiss = { renaming = null },
            )
        }
        editingTags?.let { song ->
            val tags by produceState<Tags?>(null, song) { value = vm.readTags(song) }
            val loaded = tags ?: return@let
            TagEditorDialog(
                song = song,
                initial = loaded,
                onSave = { tags ->
                    editingTags = null
                    scope.launch {
                        vm.saveTags(song, tags)?.let { request ->
                            vm.pendingTags = song to tags
                            vm.pendingWriteRequest = request
                        }
                    }
                },
                onDismiss = { editingTags = null },
            )
        }
        offeredTheme?.let { theme -> ThemeOfferDialog(theme, onInstall = vm::installOfferedTheme, onDismiss = vm::dismissTheme) }
        if (creating || editing != null) {
            SmartPlaylistDialog(
                initial = editing,
                onSave = { playlist ->
                    vm.saveSmartPlaylist(playlist)
                    // keep an open smart playlist showing its new rules
                    (detail as? Detail.OfSmart)?.takeIf { it.playlist.id == playlist.id }?.let { detail = Detail.OfSmart(playlist) }
                    creating = false
                    editing = null
                },
                onDismiss = { creating = false; editing = null },
            )
        }
    }
}

@Composable
private fun DetailScreen(
    detail: Detail,
    library: Library?,
    smart: List<SmartPlaylist>,
    current: String?,
    actions: SongActions,
    onRemoveFromPlaylist: (Playlist, Song) -> Unit,
) {
    val unknownArtist = stringResource(R.string.unknown_artist)
    when (detail) {
        is Detail.OfAlbum -> {
            // A rotation restores only the album's id, so read the songs back from the library.
            val album = library?.albums?.firstOrNull { it.id == detail.album.id } ?: detail.album
            CollectionScreen(
                album.title.orUnknown(stringResource(R.string.unknown_album)),
                album.artist.orUnknown(unknownArtist),
                album.songs,
                current,
                actions,
            )
        }
        is Detail.OfArtist -> {
            val artist = library?.artists?.firstOrNull { it.name == detail.artist.name } ?: detail.artist
            CollectionScreen(artist.name.orUnknown(unknownArtist), "", artist.songs, current, actions)
        }
        is Detail.OfPlaylist -> {
            // Read the playlist again from the library, so songs added or removed show at once. Removing
            // a song makes the playlist again under a new id, so fall back to its name.
            val playlist = library?.playlists?.let { all ->
                all.firstOrNull { it.id == detail.playlist.id } ?: all.firstOrNull { it.name == detail.playlist.name }
            } ?: detail.playlist
            CollectionScreen(playlist.name, "", playlist.songs, current, actions) { onRemoveFromPlaylist(playlist, it) }
        }
        is Detail.OfSmart -> {
            // A restored snapshot carries no rules; the live one does.
            val playlist = smart.firstOrNull { it.id == detail.playlist.id } ?: detail.playlist
            val songs = remember(playlist, library) { library?.let(playlist::songs).orEmpty() }
            CollectionScreen(playlist.name, stringResource(R.string.smart_label), songs, current, actions)
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.permission_body),
            Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onGrant, Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.permission_grant)) }
    }
}

/** The storage provider's Music folder, where the folder picker for lyrics starts. */
private fun musicFolder(): Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music")

/** What the main area shows; a change between two of these animates. */
private data class Screen(val tab: Int, val page: Page?, val detail: Detail?, val settingsPage: SettingsPage?) {
    /** How far down the drill-down stack the screen sits; deeper screens slide in from the right. */
    val depth: Int
        get() = when {
            settingsPage != null -> 3
            detail != null -> 2
            page != null -> 1
            else -> 0
        }
}
