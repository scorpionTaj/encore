package dev.scorpiontaj.encore.library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.MediaStore

/**
 * Makes and changes playlists through MediaStore. From Android 11 MediaStore keeps each playlist
 * as an .m3u file in Music/Playlists, so other players and the desktop app can read them too.
 * Only playlists Encore made can be changed; changing another app's throws. Blocking: call it off
 * the main thread, then reload the library.
 */
@Suppress("DEPRECATION")
object PlaylistEditor {
    private val PLAYLISTS = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI

    fun create(resolver: ContentResolver, name: String): Long? {
        val uri = resolver.insert(PLAYLISTS, ContentValues().apply { put(MediaStore.Audio.Playlists.NAME, name) })
        return uri?.let(ContentUris::parseId)
    }

    fun rename(resolver: ContentResolver, playlist: Long, name: String) {
        resolver.update(
            ContentUris.withAppendedId(PLAYLISTS, playlist),
            ContentValues().apply { put(MediaStore.Audio.Playlists.NAME, name) },
            null,
            null,
        )
    }

    fun delete(resolver: ContentResolver, playlist: Long) {
        resolver.delete(ContentUris.withAppendedId(PLAYLISTS, playlist), null, null)
    }

    /** Appends [songs] to the end of [playlist], skipping ones already in it. */
    fun add(resolver: ContentResolver, playlist: Long, songs: List<Song>, existing: List<Song>) {
        val members = members(playlist)
        val have = existing.mapTo(mutableSetOf()) { it.id }
        var order = existing.size
        val rows = songs.filter { have.add(it.id) }.map { song ->
            ContentValues().apply {
                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, song.id)
                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, ++order)
            }
        }
        if (rows.isNotEmpty()) resolver.bulkInsert(members, rows.toTypedArray())
    }

    /**
     * Takes [song] out of [playlist]. MediaStore lets apps add playlist members but not remove
     * them, so the playlist is made again under the same name with the other songs, in order.
     */
    fun remove(resolver: ContentResolver, playlist: Playlist, song: Song) {
        val kept = playlist.songs.filterNot { it.id == song.id }
        delete(resolver, playlist.id)
        val id = create(resolver, playlist.name) ?: error("playlists: cannot make ${playlist.name} again")
        if (kept.isNotEmpty()) add(resolver, id, kept, emptyList())
    }

    private fun members(playlist: Long) = MediaStore.Audio.Playlists.Members.getContentUri(MediaStore.VOLUME_EXTERNAL, playlist)
}
