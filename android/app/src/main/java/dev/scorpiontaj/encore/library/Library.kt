package dev.scorpiontaj.encore.library

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** The value MediaStore stores when a tag is missing. */
const val UNKNOWN = "<unknown>"

/** Prefix of a library song's media id; the rest is its MediaStore id. */
const val SONG_PREFIX = "song:"

/** Extras key carrying a stream's MIME type across the controller-to-session boundary. */
const val EXTRA_MIME = "dev.scorpiontaj.encore.MIME"

/** One audio file as MediaStore indexed it, with what Android exposes about its format. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val durationMs: Long,
    val track: Int,
    val mime: String,
    /** Bits per second, from Android 11. */
    val bitrate: Int?,
    /** Hertz, where MediaStore has the column (see [hasAudioFormatColumns]). */
    val sampleRate: Int?,
    /** Where MediaStore has the column (see [hasAudioFormatColumns]). */
    val bitDepth: Int?,
    val year: Int?,
    /** From Android 11. */
    val genre: String?,
    val composer: String?,
    /** The folder under shared storage and the file name, such as "Music/Album/01 Song.flac". */
    val path: String,
    /** Seconds since the epoch. */
    val dateAdded: Long,
    /** When the file last changed, in seconds since the epoch. */
    val dateModified: Long,
) {
    /** The folder holding the file, such as "Music/Album/", or "" when MediaStore gives none. */
    val folder: String get() = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /** A short container or codec name, such as "FLAC" or "MP3". */
    val format: String
        get() = when (mime.lowercase()) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "M4A"
            "audio/aac", "audio/aacp", "audio/mp4a-latm" -> "AAC"
            "audio/ogg", "application/ogg", "audio/vorbis" -> "OGG"
            "audio/opus" -> "OPUS"
            "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "WAV"
            "audio/aiff", "audio/x-aiff" -> "AIFF"
            "audio/x-ape", "audio/ape" -> "APE"
            "audio/x-ms-wma" -> "WMA"
            else -> mime.substringAfter('/').uppercase()
        }

    /** True for formats that are always lossless. ALAC hides inside M4A and is not detected. */
    val lossless: Boolean get() = format in LOSSLESS

    /** The item handed to the player. The URI also rides in the request metadata, which survives IPC. */
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId("$SONG_PREFIX$id")
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist.takeUnless { it == UNKNOWN })
                .setAlbumTitle(album.takeUnless { it == UNKNOWN })
                .setDurationMs(durationMs)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    private companion object {
        val LOSSLESS = setOf("FLAC", "WAV", "AIFF", "APE")
    }
}

data class Album(val id: Long, val title: String, val artist: String, val songs: List<Song>)

data class Artist(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.distinctBy { it.albumId }.size
}

data class Playlist(val id: Long, val name: String, val songs: List<Song>)

/** Everything MediaStore knows about the device's music, grouped the way the tabs show it. */
data class Library(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
) {
    private val byId = songs.associateBy { it.id }

    /** Songs found in more than one file: the same title and artist, ignoring case. */
    val duplicates: Set<Long> by lazy {
        songs.groupBy { it.title.lowercase().trim() to it.artist.lowercase().trim() }
            .values.filter { it.size > 1 }.flatten().mapTo(mutableSetOf()) { it.id }
    }

    /** The song behind a player media id, or null for radio and unknown items. */
    fun song(mediaId: String?): Song? =
        mediaId?.removePrefix(SONG_PREFIX)?.takeIf { it != mediaId }?.toLongOrNull()?.let(byId::get)

    companion object {
        /**
         * Reads the library, leaving out songs under any of [hidden] folders. Blocking: call it off
         * the main thread, after audio permission is granted.
         */
        fun load(resolver: ContentResolver, hidden: Set<String> = emptySet()): Library {
            val songs = songs(resolver).filterNot { song -> hidden.any { song.folder.startsWith(it) } }
            val albums = songs.groupBy { it.albumId }.map { (id, tracks) ->
                val sorted = tracks.sortedBy { it.track }
                Album(id, sorted.first().album, sorted.first().albumArtist, sorted)
            }.sortedBy { it.title.lowercase() }
            val artists = songs.groupBy { it.artist }.map { (name, tracks) ->
                Artist(name, tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.track })))
            }.sortedBy { it.name.lowercase() }
            return Library(songs, albums, artists, playlists(resolver, songs.associateBy { it.id }))
        }

        private fun songs(resolver: ContentResolver): List<Song> {
            val columns = buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.ALBUM_ID)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.TRACK)
                add(MediaStore.Audio.Media.MIME_TYPE)
                add(MediaStore.Audio.Media.YEAR)
                add(MediaStore.Audio.Media.COMPOSER)
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.DISPLAY_NAME)
                add(MediaStore.Audio.Media.DATE_ADDED)
                add(MediaStore.Audio.Media.DATE_MODIFIED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.Audio.Media.BITRATE)
                    add(MediaStore.Audio.Media.GENRE)
                }
                if (hasAudioFormatColumns()) {
                    add(MediaStore.Audio.Media.SAMPLERATE)
                    add(MediaStore.Audio.Media.BITS_PER_SAMPLE)
                }
            }
            val query = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.IS_MUSIC} != 0")
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
            }
            val cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray(), query, null)
                ?: return emptyList()
            return cursor.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val artist = c.string(MediaStore.Audio.Media.ARTIST) ?: UNKNOWN
                        add(
                            Song(
                                id = c.long(MediaStore.Audio.Media._ID) ?: continue,
                                title = c.string(MediaStore.Audio.Media.TITLE) ?: UNKNOWN,
                                artist = artist,
                                album = c.string(MediaStore.Audio.Media.ALBUM) ?: UNKNOWN,
                                albumId = c.long(MediaStore.Audio.Media.ALBUM_ID) ?: 0,
                                albumArtist = c.string(MediaStore.Audio.Media.ALBUM_ARTIST) ?: artist,
                                durationMs = c.long(MediaStore.Audio.Media.DURATION) ?: 0,
                                track = c.long(MediaStore.Audio.Media.TRACK)?.toInt() ?: 0,
                                mime = c.string(MediaStore.Audio.Media.MIME_TYPE).orEmpty(),
                                bitrate = c.long(MediaStore.Audio.Media.BITRATE)?.toInt()?.takeIf { it > 0 },
                                sampleRate = c.long(MediaStore.Audio.Media.SAMPLERATE)?.toInt()?.takeIf { it > 0 },
                                bitDepth = c.long(MediaStore.Audio.Media.BITS_PER_SAMPLE)?.toInt()?.takeIf { it > 0 },
                                year = c.long(MediaStore.Audio.Media.YEAR)?.toInt()?.takeIf { it > 0 },
                                genre = c.string(MediaStore.Audio.Media.GENRE)?.takeIf { it.isNotBlank() },
                                composer = c.string(MediaStore.Audio.Media.COMPOSER)?.takeIf { it.isNotBlank() },
                                path = c.string(MediaStore.Audio.Media.RELATIVE_PATH).orEmpty() +
                                    c.string(MediaStore.Audio.Media.DISPLAY_NAME).orEmpty(),
                                dateAdded = c.long(MediaStore.Audio.Media.DATE_ADDED) ?: 0,
                                dateModified = c.long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0,
                            ),
                        )
                    }
                }
            }
        }

        /** MediaStore's playlists, deprecated since Android 12 but still indexed from .m3u files. */
        @Suppress("DEPRECATION")
        private fun playlists(resolver: ContentResolver, songs: Map<Long, Song>): List<Playlist> = runCatching {
            val cursor = resolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME),
                null,
                null,
                "${MediaStore.Audio.Playlists.NAME} COLLATE NOCASE ASC",
            ) ?: return emptyList()
            cursor.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val id = c.long(MediaStore.Audio.Playlists._ID) ?: continue
                        val name = c.string(MediaStore.Audio.Playlists.NAME) ?: continue
                        add(Playlist(id, name, members(resolver, id, songs)))
                    }
                }
            }
        }.getOrDefault(emptyList())

        @Suppress("DEPRECATION")
        private fun members(resolver: ContentResolver, playlist: Long, songs: Map<Long, Song>): List<Song> {
            val cursor = resolver.query(
                MediaStore.Audio.Playlists.Members.getContentUri(MediaStore.VOLUME_EXTERNAL, playlist),
                arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID),
                null,
                null,
                MediaStore.Audio.Playlists.Members.PLAY_ORDER,
            ) ?: return emptyList()
            return cursor.use { c ->
                buildList {
                    while (c.moveToNext()) c.long(MediaStore.Audio.Playlists.Members.AUDIO_ID)?.let(songs::get)?.let(::add)
                }
            }
        }
    }
}

/** Builds the player item for a radio stream or library song that came back from the controller. */
fun MediaItem.resolved(): MediaItem {
    val uri = requestMetadata.mediaUri ?: return this
    return buildUpon().setUri(uri).setMimeType(requestMetadata.extras?.getString(EXTRA_MIME)).build()
}

/** MediaStore gained sample rate and bit depth in Android 16, and in SDK extension 15 on Android 13 to 15. */
private fun hasAudioFormatColumns(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 15)

private fun Cursor.string(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

private fun Cursor.long(column: String): Long? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
