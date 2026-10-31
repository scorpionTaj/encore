package dev.scorpiontaj.encore.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app picks light or dark. */
enum class ThemeMode { System, Light, Dark }

/** The person's choices from the Settings page. */
data class SettingsState(
    val theme: ThemeMode = ThemeMode.System,
    /** Colours from the wallpaper, on Android 12 and newer. */
    val dynamicColor: Boolean = false,
    /** Plays every song at a similar level using its ReplayGain tags. */
    val normalize: Boolean = true,
    /** Shuffle keeps each album's songs together, in order, and shuffles the albums. */
    val shuffleByAlbum: Boolean = false,
    /** Folders, such as "Music/Voice notes/", whose songs, and their subfolders' songs, stay out of the library. */
    val hiddenFolders: Set<String> = emptySet(),
)

/**
 * The app's settings, shared by the screens and PlaybackService. Both run in the app's one
 * process, so they read the same object. Call [init] before reading [state].
 */
object Settings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = SettingsState(
            theme = runCatching { ThemeMode.valueOf(prefs.getString(THEME, null)!!) }.getOrDefault(ThemeMode.System),
            dynamicColor = prefs.getBoolean(DYNAMIC_COLOR, false),
            normalize = prefs.getBoolean(NORMALIZE, true),
            shuffleByAlbum = prefs.getBoolean(SHUFFLE_BY_ALBUM, false),
            hiddenFolders = prefs.getStringSet(HIDDEN_FOLDERS, emptySet()).orEmpty().toSet(),
        )
    }

    fun update(change: (SettingsState) -> SettingsState) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit {
            putString(THEME, next.theme.name)
            putBoolean(DYNAMIC_COLOR, next.dynamicColor)
            putBoolean(NORMALIZE, next.normalize)
            putBoolean(SHUFFLE_BY_ALBUM, next.shuffleByAlbum)
            putStringSet(HIDDEN_FOLDERS, next.hiddenFolders)
        }
    }

    private const val THEME = "theme"
    private const val DYNAMIC_COLOR = "dynamic_color"
    private const val NORMALIZE = "normalize"
    private const val SHUFFLE_BY_ALBUM = "shuffle_by_album"
    private const val HIDDEN_FOLDERS = "hidden_folders"
}
