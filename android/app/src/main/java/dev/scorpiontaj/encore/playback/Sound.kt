package dev.scorpiontaj.encore.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.core.content.edit
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One equalizer band: its centre frequency and current gain, in millibels. */
data class Band(val centerHz: Int, val levelMb: Int)

/** The equalizer as the settings screen shows it. Empty bands mean the device offers no equalizer. */
data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<Band> = emptyList(),
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
    val presets: List<String> = emptyList(),
)

/**
 * The equalizer on the player's audio session. PlaybackService attaches it and the settings
 * screen drives it; both run in the app's one process, so they share this object. Levels are
 * saved and restored with the player.
 */
object Sound {
    private const val PREFS = "sound"
    private const val TAG = "playback"

    private var equalizer: Equalizer? = null
    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /** Attaches to [audioSessionId], restoring the saved levels. Called by PlaybackService. */
    fun attach(context: Context, audioSessionId: Int) {
        release()
        val eq = runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "playback: no equalizer on this device", it) }
            .getOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (band in 0 until eq.numberOfBands) {
            val saved = prefs.getInt("band$band", Int.MIN_VALUE)
            if (saved != Int.MIN_VALUE) eq.setBandLevel(band.toShort(), saved.toShort())
        }
        eq.enabled = prefs.getBoolean("enabled", false)
        equalizer = eq
        publish()
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        _state.value = EqualizerState()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val eq = equalizer ?: return
        eq.enabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("enabled", enabled) }
        publish()
    }

    fun setLevel(context: Context, band: Int, levelMb: Int) {
        val eq = equalizer ?: return
        eq.setBandLevel(band.toShort(), levelMb.toShort())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt("band$band", levelMb) }
        publish()
    }

    fun usePreset(context: Context, preset: Int) {
        val eq = equalizer ?: return
        eq.usePreset(preset.toShort())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            for (band in 0 until eq.numberOfBands) putInt("band$band", eq.getBandLevel(band.toShort()).toInt())
        }
        publish()
    }

    private fun publish() {
        val eq = equalizer ?: return
        val (min, max) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
        _state.value = EqualizerState(
            enabled = eq.enabled,
            bands = (0 until eq.numberOfBands).map { band ->
                Band(eq.getCenterFreq(band.toShort()) / 1000, eq.getBandLevel(band.toShort()).toInt())
            },
            minMb = min,
            maxMb = max,
            presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
        )
    }
}

/** Off, then all, then one, as the desktop repeat button cycles. */
fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
