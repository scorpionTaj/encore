package dev.scorpiontaj.encore.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app picks light or dark. */
enum class ThemeMode { System, Light, Dark }

/** The typeface the app draws text in: the desktop's Inter, or the phone's own. */
enum class Typeface { Inter, System }

/** The online lyrics sources, in the order the desktop asks them by default. The .lrc beside a song is always first. */
enum class LyricsSource(val label: String) { Lrclib("LRCLIB"), NetEase("NetEase"), Kugou("Kugou"), Musixmatch("Musixmatch") }

/** The person's choices from the Settings pages. */
data class SettingsState(
    // Appearance
    val theme: ThemeMode = ThemeMode.System,
    /** Colours from the wallpaper, on Android 12 and newer. */
    val dynamicColor: Boolean = false,
    /** The installed theme in use, by id, or null for Encore's own colours. */
    val customTheme: String? = null,
    /** The desktop icon pack drawn everywhere in the app, by folder name. */
    val iconPack: String = "lucide",
    val typeface: Typeface = Typeface.Inter,
    /** Turns off transitions and other movement, as the desktop's Motion setting does. */
    val reduceMotion: Boolean = false,
    // Now playing
    /** A blurred copy of the cover behind the now playing screen, like the desktop's ambient background. */
    val ambient: Boolean = true,
    /** Keeps the screen on while the now playing screen is open. */
    val keepScreenOn: Boolean = false,
    /** How large lyrics are drawn, as a multiple of the normal size. */
    val lyricsScale: Float = 1f,
    // Lyrics
    /** The online sources asked, in order. A source not listed is off. */
    val lyricsSources: List<LyricsSource> = LyricsSource.entries,
    /** The folder the person let Encore write lyrics into, as a document tree URI. */
    val lyricsTree: String? = null,
    // Playback
    /** Plays every song at a similar level using its ReplayGain tags. */
    val normalize: Boolean = true,
    /** Shuffle keeps each album's songs together, in order, and shuffles the albums. */
    val shuffleByAlbum: Boolean = false,
    /** Seconds each song fades out and the next fades in: 0 (off), 2, 4, 6 or 8. */
    val crossfadeSeconds: Int = 0,
    // Library
    /** Folders, such as "Music/Voice notes/", whose songs, and their subfolders' songs, stay out of the library. */
    val hiddenFolders: Set<String> = emptySet(),
    /** Reloads the library by itself when songs are added or removed. */
    val watchFolders: Boolean = true,
    /** The Songs tab's sort and filter, by name, kept between visits. */
    val songSort: String = "Title",
    val songFilter: String = "All",
    // General
    /** Looks for a newer release when the app starts. */
    val checkUpdates: Boolean = true,
)

/**
 * The app's settings, shared by the screens and PlaybackService. Both run in the app's one
 * process, so they read the same object. Call [init] before reading [state].
 */
object Settings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /** The crossfade lengths offered, in seconds, as on the desktop. */
    val CROSSFADES = listOf(0, 2, 4, 6, 8)

    /** The lyrics sizes offered, as multiples of the normal size. */
    val LYRICS_SCALES = listOf(0.8f, 1f, 1.25f, 1.5f)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaults = SettingsState()
        _state.value = SettingsState(
            theme = enumOr(prefs.getString(THEME, null), defaults.theme),
            dynamicColor = prefs.getBoolean(DYNAMIC_COLOR, defaults.dynamicColor),
            customTheme = prefs.getString(CUSTOM_THEME, null),
            iconPack = prefs.getString(ICON_PACK, null) ?: defaults.iconPack,
            typeface = enumOr(prefs.getString(TYPEFACE, null), defaults.typeface),
            reduceMotion = prefs.getBoolean(REDUCE_MOTION, defaults.reduceMotion),
            ambient = prefs.getBoolean(AMBIENT, defaults.ambient),
            keepScreenOn = prefs.getBoolean(KEEP_SCREEN_ON, defaults.keepScreenOn),
            lyricsScale = prefs.getFloat(LYRICS_SCALE, defaults.lyricsScale),
            lyricsSources = prefs.getString(LYRICS_SOURCES, null)
                ?.split(',')?.mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }
                ?: defaults.lyricsSources,
            lyricsTree = prefs.getString(LYRICS_TREE, null),
            normalize = prefs.getBoolean(NORMALIZE, defaults.normalize),
            shuffleByAlbum = prefs.getBoolean(SHUFFLE_BY_ALBUM, defaults.shuffleByAlbum),
            crossfadeSeconds = prefs.getInt(CROSSFADE, defaults.crossfadeSeconds),
            hiddenFolders = prefs.getStringSet(HIDDEN_FOLDERS, emptySet()).orEmpty().toSet(),
            watchFolders = prefs.getBoolean(WATCH_FOLDERS, defaults.watchFolders),
            songSort = prefs.getString(SONG_SORT, null) ?: defaults.songSort,
            songFilter = prefs.getString(SONG_FILTER, null) ?: defaults.songFilter,
            checkUpdates = prefs.getBoolean(CHECK_UPDATES, defaults.checkUpdates),
        )
    }

    fun update(change: (SettingsState) -> SettingsState) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit {
            putString(THEME, next.theme.name)
            putBoolean(DYNAMIC_COLOR, next.dynamicColor)
            putString(CUSTOM_THEME, next.customTheme)
            putString(ICON_PACK, next.iconPack)
            putString(TYPEFACE, next.typeface.name)
            putBoolean(REDUCE_MOTION, next.reduceMotion)
            putBoolean(AMBIENT, next.ambient)
            putBoolean(KEEP_SCREEN_ON, next.keepScreenOn)
            putFloat(LYRICS_SCALE, next.lyricsScale)
            putString(LYRICS_SOURCES, next.lyricsSources.joinToString(",") { it.name })
            putString(LYRICS_TREE, next.lyricsTree)
            putBoolean(NORMALIZE, next.normalize)
            putBoolean(SHUFFLE_BY_ALBUM, next.shuffleByAlbum)
            putInt(CROSSFADE, next.crossfadeSeconds)
            putStringSet(HIDDEN_FOLDERS, next.hiddenFolders)
            putBoolean(WATCH_FOLDERS, next.watchFolders)
            putString(SONG_SORT, next.songSort)
            putString(SONG_FILTER, next.songFilter)
            putBoolean(CHECK_UPDATES, next.checkUpdates)
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private const val THEME = "theme"
    private const val DYNAMIC_COLOR = "dynamic_color"
    private const val CUSTOM_THEME = "custom_theme"
    private const val ICON_PACK = "icon_pack"
    private const val TYPEFACE = "typeface"
    private const val REDUCE_MOTION = "reduce_motion"
    private const val AMBIENT = "ambient"
    private const val KEEP_SCREEN_ON = "keep_screen_on"
    private const val LYRICS_SCALE = "lyrics_scale"
    private const val LYRICS_SOURCES = "lyrics_sources"
    private const val LYRICS_TREE = "lyrics_tree"
    private const val NORMALIZE = "normalize"
    private const val SHUFFLE_BY_ALBUM = "shuffle_by_album"
    private const val CROSSFADE = "crossfade"
    private const val HIDDEN_FOLDERS = "hidden_folders"
    private const val WATCH_FOLDERS = "watch_folders"
    private const val SONG_SORT = "song_sort"
    private const val SONG_FILTER = "song_filter"
    private const val CHECK_UPDATES = "check_updates"
}
