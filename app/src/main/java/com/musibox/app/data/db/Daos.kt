package com.musibox.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Song>>

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun get(id: Long): Song?

    @Query("SELECT id FROM songs")
    suspend fun allIds(): List<Long>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(size), 0) FROM songs")
    suspend fun totalSize(): Long

    @Upsert
    suspend fun upsertAll(songs: List<Song>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun clear()
}

data class PlaylistCount(val playlistId: String, val count: Int)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeOne(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists")
    suspend fun all(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE deleted = 0")
    suspend fun allActive(): List<PlaylistEntity>

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    suspend fun items(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT playlistId, COUNT(*) AS count FROM playlist_items GROUP BY playlistId")
    fun observeCounts(): Flow<List<PlaylistCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearItems(playlistId: String)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("SELECT * FROM playlists WHERE dirty = 1")
    suspend fun dirty(): List<PlaylistEntity>

    @Query("UPDATE playlists SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM playlist_items")
    suspend fun clearAllItems()

    @Query("UPDATE playlists SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT COUNT(*) FROM playlists WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM playlists")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE deleted = 0 ORDER BY addedAt DESC")
    fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE songKey = :key")
    suspend fun get(key: String): FavoriteEntity?

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE deleted = 0")
    suspend fun allActive(): List<FavoriteEntity>

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Upsert
    suspend fun upsertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE songKey = :key")
    suspend fun hardDelete(key: String)

    @Query("SELECT * FROM favorites WHERE dirty = 1")
    suspend fun dirty(): List<FavoriteEntity>

    @Query("UPDATE favorites SET dirty = 0 WHERE songKey = :key AND updatedAt = :updatedAt")
    suspend fun markClean(key: String, updatedAt: Long)

    @Query("DELETE FROM favorites WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()

    @Query("UPDATE favorites SET dirty = 1")
    suspend fun markAllDirty()

    @Query("SELECT COUNT(*) FROM favorites WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM play_history WHERE deleted = 0 ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE id = :id")
    suspend fun get(id: String): PlayHistoryEntity?

    @Query("SELECT * FROM play_history")
    suspend fun all(): List<PlayHistoryEntity>

    @Query("SELECT COUNT(*) FROM play_history WHERE deleted = 0")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlayHistoryEntity)

    @Upsert
    suspend fun upsertAll(items: List<PlayHistoryEntity>)

    @Query("UPDATE play_history SET deleted = 1, dirty = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE play_history SET deleted = 1, dirty = 1 WHERE deleted = 0")
    suspend fun softDeleteAll()

    @Query("DELETE FROM play_history WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Query("SELECT * FROM play_history WHERE dirty = 1")
    suspend fun dirty(): List<PlayHistoryEntity>

    @Query("UPDATE play_history SET dirty = 0 WHERE id = :id AND deleted = :deleted")
    suspend fun markClean(id: String, deleted: Boolean)

    @Query("DELETE FROM play_history WHERE deleted = 1 AND dirty = 0")
    suspend fun purgeDeleted()

    @Query("UPDATE play_history SET dirty = 1")
    suspend fun markAllDirty()

    @Query("UPDATE play_history SET dirty = 0")
    suspend fun markAllClean()

    @Query("SELECT COUNT(*) FROM play_history WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT * FROM play_stats WHERE songKey = :key")
    suspend fun stat(key: String): PlayStatEntity?

    @Upsert
    suspend fun upsertStat(stat: PlayStatEntity)

    @Query("SELECT * FROM play_stats ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun observeTopStats(limit: Int): Flow<List<PlayStatEntity>>
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun get(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE position = :position")
    suspend fun at(position: Int): QueueItemEntity?

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, error: String?, now: Long)

    @Query(
        "UPDATE downloads SET progress = :progress, downloadedBytes = :downloaded, totalBytes = :total, " +
            "speedBps = :speed, etaSec = :eta, updatedAt = :now WHERE id = :id",
    )
    suspend fun setProgress(id: String, progress: Float, downloaded: Long, total: Long, speed: Long, eta: Long, now: Long)

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', progress = 1, outputUri = :uri, vaultItemId = :vaultId, " +
            "mimeType = :mime, totalBytes = :size, downloadedBytes = :size, speedBps = 0, etaSec = 0, " +
            "errorMessage = NULL, completedAt = :now, updatedAt = :now WHERE id = :id",
    )
    suspend fun complete(id: String, uri: String?, vaultId: String?, mime: String, size: Long, now: Long)

    @Query("UPDATE downloads SET destination = :destination, outputUri = :uri, vaultItemId = :vaultId, updatedAt = :now WHERE id = :id")
    suspend fun setLocation(id: String, destination: String, uri: String?, vaultId: String?, now: Long)

    @Query(
        "UPDATE downloads SET status = 'QUEUED', progress = 0, downloadedBytes = 0, speedBps = 0, etaSec = 0, " +
            "errorMessage = NULL, updatedAt = :now WHERE id = :id",
    )
    suspend fun resetForRetry(id: String, now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','RUNNING','PAUSED')")
    suspend fun unfinished(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'RUNNING'")
    suspend fun running(): List<DownloadEntity>

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun completedSize(): Long
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun get(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items")
    suspend fun all(): List<VaultItemEntity>

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE vault_items SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE vault_items SET folder = :folder WHERE id IN (:ids)")
    suspend fun move(ids: List<String>, folder: String)

    @Query("SELECT COALESCE(SUM(size), 0) FROM vault_items")
    suspend fun totalSize(): Long

    @Query("DELETE FROM vault_items")
    suspend fun clear()
}
