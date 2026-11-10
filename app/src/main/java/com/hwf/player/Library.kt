package com.hwf.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val mime: String?,
)

/** All music Android has indexed on the player (internal memory and SD card). */
fun loadLibrary(context: Context): List<Track> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.MIME_TYPE,
    )
    val tracks = mutableListOf<Track>()
    context.contentResolver.query(
        collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            tracks += Track(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                title = c.getString(titleCol) ?: "Без названия",
                artist = c.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Неизвестный исполнитель",
                album = c.getString(albumCol) ?: "",
                albumId = c.getLong(albumIdCol),
                durationMs = c.getLong(durCol),
                mime = c.getString(mimeCol),
            )
        }
    }
    return tracks
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMimeType(mime)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setExtras(Bundle().apply { putString("mime", mime) })
            .setArtworkUri(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId))
            .build()
    )
    .build()

/** Short codec label for the quality line, e.g. "FLAC". */
fun codecLabel(mime: String?): String = when (mime?.lowercase()) {
    "audio/flac", "audio/x-flac" -> "FLAC"
    "audio/mpeg", "audio/mp3" -> "MP3"
    "audio/mp4", "audio/aac", "audio/x-m4a", "audio/mp4a-latm" -> "AAC"
    "audio/x-wav", "audio/wav" -> "WAV"
    "audio/ogg", "audio/vorbis" -> "OGG"
    "audio/opus" -> "OPUS"
    "audio/x-ape", "audio/ape" -> "APE"
    "audio/x-dsf", "audio/dsf", "audio/x-dff" -> "DSD"
    null -> ""
    else -> mime.substringAfter('/').uppercase()
}

private val ARTIST_SEPARATORS = Regex("""\s*(?:/|,|;|&|\bfeat\.?\b|\bft\.?\b|\bx\b)\s*""", RegexOption.IGNORE_CASE)

/** "Mayot/Doomee, YUNGWAY feat. OG Buda" -> [Mayot, Doomee, YUNGWAY, OG Buda] */
fun splitArtists(s: String): List<String> =
    s.split(ARTIST_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
