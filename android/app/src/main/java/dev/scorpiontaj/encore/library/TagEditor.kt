package dev.scorpiontaj.encore.library

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.reference.ID3V2Version
import java.io.File
import kotlin.coroutines.resume

/** The tags the editor changes, as text; a blank value removes the tag. */
data class Tags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val year: String,
    val genre: String,
    val track: String,
) {
    companion object {
        fun of(song: Song) = Tags(
            title = song.title,
            artist = song.artist.takeUnless { it == UNKNOWN }.orEmpty(),
            album = song.album.takeUnless { it == UNKNOWN }.orEmpty(),
            albumArtist = song.albumArtist.takeUnless { it == UNKNOWN }.orEmpty(),
            year = song.year?.toString().orEmpty(),
            genre = song.genre.orEmpty(),
            track = (song.track % 1000).takeIf { it > 0 }?.toString().orEmpty(),
        )
    }
}

/** What saving tags came to: done, or Android wants the person to allow changing the file first. */
sealed interface TagWrite {
    data object Saved : TagWrite
    data class NeedsPermission(val request: IntentSender) : TagWrite
}

/**
 * Writes tags into a song's file. The tags are edited with jaudiotagger on a copy in the cache,
 * which is then written back over the original through MediaStore, and the file is rescanned so
 * the library shows the new values.
 */
object TagEditor {
    /** The tags in the file itself, which can hold more than MediaStore read, such as an ID3v2.4 year. */
    suspend fun read(context: Context, song: Song): Tags = withContext(Dispatchers.IO) {
        val fallback = Tags.of(song)
        val copy = File(context.cacheDir, "tag-read.${extension(song)}")
        try {
            context.contentResolver.openInputStream(song.uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                ?: return@withContext fallback
            val tag = AudioFileIO.read(copy).tag ?: return@withContext fallback
            fun field(key: FieldKey, otherwise: String) = runCatching { tag.getFirst(key) }.getOrNull()?.takeIf { it.isNotBlank() } ?: otherwise
            Tags(
                title = field(FieldKey.TITLE, fallback.title),
                artist = field(FieldKey.ARTIST, fallback.artist),
                album = field(FieldKey.ALBUM, fallback.album),
                albumArtist = field(FieldKey.ALBUM_ARTIST, fallback.albumArtist),
                year = field(FieldKey.YEAR, fallback.year).take(4),
                genre = field(FieldKey.GENRE, fallback.genre),
                track = field(FieldKey.TRACK, fallback.track).substringBefore('/'),
            )
        } catch (error: Exception) {
            fallback
        } finally {
            copy.delete()
        }
    }

    suspend fun write(context: Context, song: Song, tags: Tags): TagWrite = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // Opening for append writes nothing, but fails when Encore may not change the file.
        try {
            resolver.openOutputStream(song.uri, "wa")?.close()
        } catch (denied: SecurityException) {
            return@withContext TagWrite.NeedsPermission(permission(context, song, denied) ?: throw denied)
        }
        val copy = File(context.cacheDir, "tag-edit.${extension(song)}")
        try {
            resolver.openInputStream(song.uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                ?: error("tags: cannot read ${song.path}")
            // ID3v2.3 for MP3: Android's scanner reads the year from v2.3's TYER, not v2.4's TDRC.
            TagOptionSingleton.getInstance().iD3V2Version = ID3V2Version.ID3_V23
            val file = AudioFileIO.read(copy)
            (file as? MP3File)?.let { mp3 -> if (mp3.hasID3v2Tag() && mp3.iD3v2Tag !is ID3v23Tag) mp3.iD3v2Tag = ID3v23Tag(mp3.iD3v2TagAsv24) }
            val tag = file.tagOrCreateAndSetDefault
            listOf(
                FieldKey.TITLE to tags.title,
                FieldKey.ARTIST to tags.artist,
                FieldKey.ALBUM to tags.album,
                FieldKey.ALBUM_ARTIST to tags.albumArtist,
                FieldKey.YEAR to tags.year,
                FieldKey.GENRE to tags.genre,
                FieldKey.TRACK to tags.track,
            ).forEach { (key, value) ->
                if (value.isBlank()) tag.deleteField(key) else tag.setField(key, value.trim())
            }
            file.commit()
            resolver.openOutputStream(song.uri, "wt")?.use { out -> copy.inputStream().use { it.copyTo(out) } }
                ?: error("tags: cannot write ${song.path}")
        } finally {
            copy.delete()
        }
        rescan(context, song)
        TagWrite.Saved
    }

    private fun extension(song: Song) = song.path.substringAfterLast('.', "").ifEmpty { "mp3" }.lowercase()

    /** The system prompt that lets Encore change a file another app put on the device. */
    private fun permission(context: Context, song: Song, denied: SecurityException): IntentSender? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri)).intentSender
        denied is RecoverableSecurityException -> denied.userAction.actionIntent.intentSender
        else -> null
    }

    /** Asks MediaStore to read the file's tags again, and waits until it has. */
    private suspend fun rescan(context: Context, song: Song) {
        @Suppress("DEPRECATION")
        val path = context.contentResolver.query(song.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return
        suspendCancellableCoroutine { done ->
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> done.resume(Unit) }
        }
    }
}
