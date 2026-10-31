package dev.scorpiontaj.encore.radio

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import dev.scorpiontaj.encore.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A recording in progress: which station, and since when on the [SystemClock.elapsedRealtime] clock. */
data class Recording(val station: Station, val sinceMs: Long)

/**
 * Records a radio stream to Music/Encore Recordings, as the desktop app does. It opens its own
 * connection to the stream and copies the bytes as they come, so the file is the station's own
 * MP3, AAC or Ogg. HLS stations arrive in segments and are not recorded. Recording lives for the
 * app's process, independent of any screen.
 */
object Recorder {
    private const val FOLDER = "Music/Encore Recordings/"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<Recording?>(null)
    val state: StateFlow<Recording?> = _state.asStateFlow()

    fun canRecord(station: Station): Boolean = !station.hls

    fun start(context: Context, station: Station) {
        if (!canRecord(station)) return
        stop()
        val resolver = context.applicationContext.contentResolver
        val recording = Recording(station, SystemClock.elapsedRealtime())
        _state.value = recording
        job = scope.launch {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.ROOT).format(Date())
            val name = "${station.name.replace(Regex("[/\\\\:*?\"<>|]"), " ").trim()} $stamp.${extension(station.codec)}"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime(station.codec))
                put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
                put(MediaStore.Audio.Media.TITLE, "${station.name} $stamp")
                put(MediaStore.Audio.Media.ARTIST, station.name)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@launch
            val connection = URL(station.url).openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("User-Agent", "Encore-Android/${BuildConfig.VERSION_NAME}")
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                resolver.openOutputStream(uri)?.use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
            } catch (error: Exception) {
                Log.w("radio", "radio: recording stopped", error)
            } finally {
                connection.disconnect()
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                // A newer recording may have started meanwhile; leave its state alone.
                if (_state.value === recording) _state.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = null
    }

    private fun extension(codec: String) = when (codec) {
        "AAC", "AAC+" -> "aac"
        "OGG" -> "ogg"
        else -> "mp3"
    }

    private fun mime(codec: String) = when (codec) {
        "AAC", "AAC+" -> "audio/aac"
        "OGG" -> "audio/ogg"
        else -> "audio/mpeg"
    }
}
