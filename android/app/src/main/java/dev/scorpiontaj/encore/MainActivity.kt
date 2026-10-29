package dev.scorpiontaj.encore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scorpiontaj.encore.library.Album
import dev.scorpiontaj.encore.library.Artist
import dev.scorpiontaj.encore.library.Playlist
import dev.scorpiontaj.encore.playback.PlayerViewModel
import dev.scorpiontaj.encore.ui.AlbumsScreen
import dev.scorpiontaj.encore.ui.ArtistsScreen
import dev.scorpiontaj.encore.ui.CollectionScreen
import dev.scorpiontaj.encore.ui.EncoreTheme
import dev.scorpiontaj.encore.ui.Loading
import dev.scorpiontaj.encore.ui.MiniPlayer
import dev.scorpiontaj.encore.ui.NowPlayingScreen
import dev.scorpiontaj.encore.ui.PlaylistsScreen
import dev.scorpiontaj.encore.ui.RadioScreen
import dev.scorpiontaj.encore.ui.SongActions
import dev.scorpiontaj.encore.ui.SongsScreen
import dev.scorpiontaj.encore.ui.orUnknown

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EncoreTheme { EncoreApp(vm) } }
    }
}

/** The bottom-bar tabs, in order. */
private enum class Tab(val label: Int, val icon: Int) {
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
}

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncoreApp(vm: PlayerViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val library by vm.library.collectAsStateWithLifecycle()
    val radio by vm.radio.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    val actions = remember(vm) { SongActions(vm) }

    LaunchedEffect(granted) { if (granted && vm.library.value == null) vm.loadLibrary() }
    LaunchedEffect(tab) { if (Tab.entries[tab] == Tab.Radio) vm.loadRadio() }
    BackHandler(enabled = detail != null && !nowPlaying) { detail = null }
    BackHandler(enabled = nowPlaying) { nowPlaying = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(detail?.let { stringResource(Tab.entries[tab].label) } ?: stringResource(R.string.app_name)) },
                    navigationIcon = {
                        if (detail != null) {
                            IconButton(onClick = { detail = null }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    if (player.hasMedia) MiniPlayer(player, library, vm) { nowPlaying = true }
                    NavigationBar {
                        Tab.entries.forEachIndexed { index, entry ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index; detail = null },
                                icon = { Icon(painterResource(entry.icon), null) },
                                label = { Text(stringResource(entry.label), maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val current = player.mediaId
                val lib = library
                val open = detail
                when {
                    Tab.entries[tab] == Tab.Radio -> RadioScreen(radio, current, vm::play, vm::retryRadio)
                    !granted -> PermissionPrompt { request.launch(AUDIO_PERMISSION) }
                    lib == null -> Loading(stringResource(R.string.library_loading))
                    open != null -> DetailScreen(open, current, actions)
                    else -> when (Tab.entries[tab]) {
                        Tab.Songs -> SongsScreen(lib.songs, current, actions)
                        Tab.Albums -> AlbumsScreen(lib.albums) { detail = Detail.OfAlbum(it) }
                        Tab.Artists -> ArtistsScreen(lib.artists) { detail = Detail.OfArtist(it) }
                        Tab.Playlists -> PlaylistsScreen(lib.playlists) { detail = Detail.OfPlaylist(it) }
                        Tab.Radio -> Unit
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = nowPlaying && player.hasMedia,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(player, library, vm) { nowPlaying = false }
        }
    }
}

@Composable
private fun DetailScreen(detail: Detail, current: String?, actions: SongActions) {
    val unknownArtist = stringResource(R.string.unknown_artist)
    when (detail) {
        is Detail.OfAlbum -> CollectionScreen(
            detail.album.title.orUnknown(stringResource(R.string.unknown_album)),
            detail.album.artist.orUnknown(unknownArtist),
            detail.album.songs,
            current,
            actions,
        )
        is Detail.OfArtist -> CollectionScreen(
            detail.artist.name.orUnknown(unknownArtist),
            "",
            detail.artist.songs,
            current,
            actions,
        )
        is Detail.OfPlaylist -> CollectionScreen(detail.playlist.name, "", detail.playlist.songs, current, actions)
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
