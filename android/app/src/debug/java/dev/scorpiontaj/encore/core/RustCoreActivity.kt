package dev.scorpiontaj.encore.core

import android.app.Activity
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.scorpiontaj.encore.R

/**
 * A developer screen for the Rust-core spike: plays the first song on the device through
 * [RustCore], bypassing Media3, and says what happened. Open it with
 * `adb shell am start -n dev.scorpiontaj.encore/.core.RustCoreActivity`.
 */
class RustCoreActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = STATUS_SP }
        val play = Button(this).apply {
            setText(R.string.rust_core_play)
            setOnClickListener { play() }
        }
        val stop = Button(this).apply {
            setText(R.string.rust_core_stop)
            setOnClickListener {
                if (RustCore.available) RustCore.stop()
                status.setText(R.string.rust_core_stopped)
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val padding = (PADDING_DP * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                addView(status)
                addView(play)
                addView(stop)
            },
        )
        status.setText(if (RustCore.available) R.string.rust_core_ready else R.string.rust_core_missing)
        if (RustCore.available) RustCore.init(applicationContext)
    }

    override fun onDestroy() {
        if (RustCore.available) RustCore.stop()
        super.onDestroy()
    }

    private fun play() {
        if (!RustCore.available) return
        val path = firstSong() ?: return status.setText(R.string.rust_core_no_song)
        val error = RustCore.play(path)
        status.text = error?.let { getString(R.string.rust_core_failed, it) } ?: getString(R.string.rust_core_playing, path)
    }

    /** The path of the first song MediaStore lists, which the engine opens as a file. */
    @Suppress("DEPRECATION") // DATA is the only column that names the file itself.
    private fun firstSong(): String? = contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Media.DATA),
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        MediaStore.Audio.Media.TITLE,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private companion object {
        const val STATUS_SP = 16f
        const val PADDING_DP = 24
    }
}
