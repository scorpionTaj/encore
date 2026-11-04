package dev.scorpiontaj.encore.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
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
    /** The bass boost and surround strengths, 0 to 1000, when the device offers the effects. */
    val bassStrength: Int = 0,
    val surroundStrength: Int = 0,
    val bassAvailable: Boolean = false,
    val surroundAvailable: Boolean = false,
)

/**
 * The equalizer, bass boost and surround effects on the player's audio session.
 * PlaybackService attaches them and the settings screen drives them; both run in the app's one
 * process, so they share this object. Levels are saved and restored with the player.
 */
object Sound {
    private const val PREFS = "sound"
    private const val TAG = "playback"
    private const val STRENGTH_MAX = 1000

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var surround: Virtualizer? = null
    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /** Attaches to [audioSessionId], restoring the saved levels. Called by PlaybackService. */
    fun attach(context: Context, audioSessionId: Int) {
        release()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "playback: no equalizer on this device", it) }
            .getOrNull()
            ?.let { eq ->
                for (band in 0 until eq.numberOfBands) {
                    val saved = prefs.getInt("band$band", Int.MIN_VALUE)
                    if (saved != Int.MIN_VALUE) eq.setBandLevel(band.toShort(), saved.toShort())
                }
                eq.enabled = prefs.getBoolean("enabled", false)
                equalizer = eq
            }
        bass = effect(context) { BassBoost(0, audioSessionId) }?.also {
            it.setStrength(prefs.getInt("bass", 0).coerceIn(0, STRENGTH_MAX).toShort())
        }
        surround = effect(context) { Virtualizer(0, audioSessionId) }?.also {
            it.setStrength(prefs.getInt("surround", 0).coerceIn(0, STRENGTH_MAX).toShort())
        }
        publish()
    }

    /** Builds an optional audio effect, or logs why the device does not have it. */
    private inline fun <reified T : android.media.audiofx.AudioEffect> effect(context: Context, build: () -> T): T? =
        runCatching { build() }
            .onFailure { Log.w(TAG, "playback: no ${T::class.java.simpleName} on this device", it) }
            .getOrNull()
            ?.also { effect -> effect.enabled = false }

    fun release() {
        equalizer?.release()
        equalizer = null
        bass?.release()
        bass = null
        surround?.release()
        surround = null
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

    /** Applies [levels] to the bands in order, as a preset the person saved would. */
    fun setLevels(context: Context, levels: List<Int>) {
        val eq = equalizer ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            for (band in 0 until eq.numberOfBands) {
                levels.getOrNull(band)?.let { level ->
                    eq.setBandLevel(band.toShort(), level.toShort())
                    putInt("band$band", level)
                }
            }
        }
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

    /** Sets the bass boost strength, 0 to 1000; zero turns the effect off. */
    fun setBass(context: Context, strength: Int) {
        val effect = bass ?: return
        val value = strength.coerceIn(0, STRENGTH_MAX)
        effect.setStrength(value.toShort())
        effect.enabled = value > 0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt("bass", value) }
        publish()
    }

    /** Sets the surround strength, 0 to 1000; zero turns the effect off. */
    fun setSurround(context: Context, strength: Int) {
        val effect = surround ?: return
        val value = strength.coerceIn(0, STRENGTH_MAX)
        effect.setStrength(value.toShort())
        effect.enabled = value > 0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt("surround", value) }
        publish()
    }

    private fun publish() {
        val eq = equalizer
        val (min, max) = eq?.bandLevelRange?.let { it[0].toInt() to it[1].toInt() } ?: (-1500 to 1500)
        _state.value = EqualizerState(
            enabled = eq?.enabled ?: false,
            bands = eq?.let { source ->
                (0 until source.numberOfBands).map { band ->
                    Band(source.getCenterFreq(band.toShort()) / 1000, source.getBandLevel(band.toShort()).toInt())
                }
            }.orEmpty(),
            minMb = min,
            maxMb = max,
            presets = eq?.let { source -> (0 until source.numberOfPresets).map { source.getPresetName(it.toShort()) } }.orEmpty(),
            bassStrength = bass?.roundedStrength?.toInt() ?: 0,
            surroundStrength = surround?.roundedStrength?.toInt() ?: 0,
            bassAvailable = bass != null,
            surroundAvailable = surround != null,
        )
    }
}

/** Off, then all, then one, as the desktop repeat button cycles. */
fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
