package com.musibox.app.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.QueueItemEntity
import com.musibox.app.data.db.Song
import com.musibox.app.data.repo.ALBUM_ART_BASE
import android.content.ContentUris

/** Música atual / item da fila, como a interface enxerga. */
data class NowPlaying(
    val mediaId: String,
    val songId: Long,
    val uri: Uri?,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val songKey: String,
    val looseKey: String,
) {
    val displayArtist: String get() = SongKeys.displayArtist(artist)
}

internal object Extras {
    const val SONG_KEY = "songKey"
    const val LOOSE_KEY = "looseKey"
    const val ALBUM_ID = "albumId"
    const val DURATION = "durationMs"
    const val URI = "uri"
    const val RAW_ARTIST = "rawArtist"
    const val RAW_ALBUM = "rawAlbum"
}

fun buildMediaItem(
    songId: Long,
    uri: String,
    title: String,
    artist: String,
    album: String,
    albumId: Long,
    durationMs: Long,
    songKey: String,
    looseKey: String,
): MediaItem {
    val parsed = Uri.parse(uri)
    val extras = Bundle().apply {
        putString(Extras.SONG_KEY, songKey)
        putString(Extras.LOOSE_KEY, looseKey)
        putLong(Extras.ALBUM_ID, albumId)
        putLong(Extras.DURATION, durationMs)
        putString(Extras.URI, uri)
        putString(Extras.RAW_ARTIST, artist)
        putString(Extras.RAW_ALBUM, album)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(SongKeys.displayArtist(artist))
        .setAlbumTitle(SongKeys.displayAlbum(album))
        .setArtworkUri(ContentUris.withAppendedId(ALBUM_ART_BASE, albumId))
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(songId.toString())
        .setUri(parsed)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(parsed).build())
        .setMediaMetadata(metadata)
        .build()
}

fun Song.toMediaItem(): MediaItem =
    buildMediaItem(id, uri, title, artist, album, albumId, durationMs, contentKey, looseKey)

fun QueueItemEntity.toMediaItem(): MediaItem =
    buildMediaItem(songId, uri, title, artist, album, albumId, durationMs, songKey, looseKey)

/** Restaura o endereço do arquivo quando o item chega à sessão sem ele. */
fun MediaItem.withPlayableUri(): MediaItem {
    if (localConfiguration != null) return this
    val uri = requestMetadata.mediaUri
        ?: mediaMetadata.extras?.getString(Extras.URI)?.let(Uri::parse)
        ?: return this
    return buildUpon().setUri(uri).build()
}

fun MediaItem.toNowPlaying(): NowPlaying {
    val extras = mediaMetadata.extras
    val uri = localConfiguration?.uri
        ?: requestMetadata.mediaUri
        ?: extras?.getString(Extras.URI)?.let(Uri::parse)
    val title = mediaMetadata.title?.toString() ?: ""
    val rawArtist = extras?.getString(Extras.RAW_ARTIST) ?: mediaMetadata.artist?.toString() ?: ""
    val rawAlbum = extras?.getString(Extras.RAW_ALBUM) ?: mediaMetadata.albumTitle?.toString() ?: ""
    val duration = extras?.getLong(Extras.DURATION) ?: 0L
    return NowPlaying(
        mediaId = mediaId,
        songId = mediaId.toLongOrNull() ?: -1L,
        uri = uri,
        title = title,
        artist = rawArtist,
        album = rawAlbum,
        albumId = extras?.getLong(Extras.ALBUM_ID) ?: 0L,
        durationMs = duration,
        songKey = extras?.getString(Extras.SONG_KEY) ?: SongKeys.contentKey(title, rawArtist, duration),
        looseKey = extras?.getString(Extras.LOOSE_KEY) ?: SongKeys.looseKey(title, rawArtist),
    )
}

fun NowPlaying.toQueueEntity(position: Int): QueueItemEntity = QueueItemEntity(
    position = position,
    songId = songId,
    uri = uri?.toString() ?: "",
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    songKey = songKey,
    looseKey = looseKey,
)
