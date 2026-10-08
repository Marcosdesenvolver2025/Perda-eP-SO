package com.musibox.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Música encontrada no aparelho (índice do MediaStore). */
@Entity(tableName = "songs", indices = [Index("contentKey"), Index("looseKey")])
data class Song(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folderName: String,
    val folderPath: String,
    val size: Long,
    val mimeType: String,
    val dateAddedMs: Long,
    val contentKey: String,
    val looseKey: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songKey: String,
    val looseKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "playlist_items",
    indices = [Index("playlistId")],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistItemEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val songKey: String,
    val looseKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "play_history", indices = [Index("playedAt"), Index("songKey")])
data class PlayHistoryEntity(
    @PrimaryKey val id: String,
    val songKey: String,
    val looseKey: String,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val playedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val songKey: String,
    val looseKey: String,
    val title: String,
    val artist: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)

/** Fila de reprodução salva para retomar de onde parou. */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val songId: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val songKey: String,
    val looseKey: String,
)

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"
}

object DownloadKind {
    const val DOWNLOAD = "DOWNLOAD"
    const val IMPORT = "IMPORT"
}

@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val sourceUrl: String,
    val platform: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val formatSelector: String,
    val formatLabel: String,
    val isAudio: Boolean,
    val convertTo: String?,
    val mergeMp4: Boolean,
    val targetExt: String,
    val fileName: String,
    val destination: String,
    val status: String,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val etaSec: Long,
    val errorMessage: String?,
    val outputUri: String?,
    val vaultItemId: String?,
    val mimeType: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

object VaultCategory {
    const val PHOTO = "PHOTO"
    const val VIDEO = "VIDEO"
    const val AUDIO = "AUDIO"
    const val FILE = "FILE"

    fun fromMime(mime: String?): String = when {
        mime == null -> FILE
        mime.startsWith("image/") -> PHOTO
        mime.startsWith("video/") -> VIDEO
        mime.startsWith("audio/") -> AUDIO
        else -> FILE
    }
}

@Entity(tableName = "vault_items", indices = [Index("category")])
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mimeType: String,
    val category: String,
    val size: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val folder: String,
    val hasThumb: Boolean,
    val createdAt: Long,
    val importedFrom: String?,
)
