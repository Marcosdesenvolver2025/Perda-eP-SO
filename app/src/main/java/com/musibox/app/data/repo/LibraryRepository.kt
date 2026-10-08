package com.musibox.app.data.repo

import androidx.room.withTransaction
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.db.FavoriteEntity
import com.musibox.app.data.db.PlayStatEntity
import com.musibox.app.data.db.PlaylistEntity
import com.musibox.app.data.db.PlaylistItemEntity
import com.musibox.app.data.db.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Referência a uma música salva (playlist/favorito/histórico) com o arquivo local, se existir. */
data class TrackRef(
    val songKey: String,
    val looseKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val song: Song?,
) {
    val available: Boolean get() = song != null
}

data class PlaylistSummary(val playlist: PlaylistEntity, val count: Int)

data class PlaylistTrack(val item: PlaylistItemEntity, val track: TrackRef)

data class MostPlayed(val stat: PlayStatEntity, val song: Song?)

/** Favoritos, playlists e estatísticas de reprodução. */
class LibraryRepository(
    private val db: AppDatabase,
    private val music: MusicRepository,
    private val requestSync: () -> Unit,
) {
    private val favDao = db.favoriteDao()
    private val plDao = db.playlistDao()
    private val histDao = db.historyDao()

    val favorites: Flow<List<FavoriteEntity>> = favDao.observe()

    val favoriteKeys: Flow<Set<String>> = favorites.map { list -> list.mapTo(HashSet()) { it.songKey } }

    val favoriteTracks: Flow<List<TrackRef>> = combine(favorites, music.index) { favs, idx ->
        favs.map { f ->
            TrackRef(f.songKey, f.looseKey, f.title, f.artist, f.album, f.durationMs, idx.resolve(f.songKey, f.looseKey))
        }
    }

    suspend fun isFavorite(songKey: String): Boolean = favDao.get(songKey)?.deleted == false

    suspend fun setFavorite(
        songKey: String,
        looseKey: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        favorite: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val existing = favDao.get(songKey)
        if (favorite) {
            favDao.upsert(
                FavoriteEntity(
                    songKey = songKey,
                    looseKey = looseKey,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    addedAt = if (existing != null && !existing.deleted) existing.addedAt else now,
                    updatedAt = now,
                    deleted = false,
                    dirty = true,
                ),
            )
        } else if (existing != null) {
            favDao.upsert(existing.copy(deleted = true, dirty = true, updatedAt = now))
        }
        requestSync()
    }

    suspend fun toggleFavorite(song: Song): Boolean {
        val fav = !isFavorite(song.contentKey)
        setFavorite(song.contentKey, song.looseKey, song.title, song.artist, song.album, song.durationMs, fav)
        return fav
    }

    // ---------- Playlists ----------

    val playlists: Flow<List<PlaylistSummary>> = combine(plDao.observe(), plDao.observeCounts()) { lists, counts ->
        val map = counts.associate { it.playlistId to it.count }
        lists.map { PlaylistSummary(it, map[it.id] ?: 0) }
    }

    fun playlist(id: String): Flow<PlaylistEntity?> = plDao.observeOne(id)

    fun playlistTracks(id: String): Flow<List<PlaylistTrack>> =
        combine(plDao.observeItems(id), music.index) { items, idx ->
            items.map { item ->
                PlaylistTrack(
                    item,
                    TrackRef(
                        item.songKey, item.looseKey, item.title, item.artist, item.album, item.durationMs,
                        idx.resolve(item.songKey, item.looseKey),
                    ),
                )
            }
        }

    suspend fun createPlaylist(name: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        plDao.upsert(PlaylistEntity(id, name.trim().ifBlank { "Nova playlist" }, now, now, deleted = false, dirty = true))
        requestSync()
        return id
    }

    suspend fun renamePlaylist(id: String, name: String) {
        val p = plDao.get(id) ?: return
        plDao.upsert(p.copy(name = name.trim().ifBlank { p.name }, updatedAt = System.currentTimeMillis(), dirty = true))
        requestSync()
    }

    suspend fun deletePlaylist(id: String) {
        val p = plDao.get(id) ?: return
        db.withTransaction {
            plDao.clearItems(id)
            plDao.upsert(p.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis()))
        }
        requestSync()
    }

    /** Adiciona músicas ao final da playlist (ignora as que já estão nela). */
    suspend fun addToPlaylist(playlistId: String, songs: List<Song>): Int {
        val p = plDao.get(playlistId) ?: return 0
        var added = 0
        db.withTransaction {
            val items = plDao.items(playlistId)
            val existingKeys = items.mapTo(HashSet()) { it.songKey }
            var position = (items.maxOfOrNull { it.position } ?: -1) + 1
            val now = System.currentTimeMillis()
            val newItems = songs.filter { existingKeys.add(it.contentKey) }.map { s ->
                PlaylistItemEntity(
                    id = UUID.randomUUID().toString(),
                    playlistId = playlistId,
                    songKey = s.contentKey,
                    looseKey = s.looseKey,
                    title = s.title,
                    artist = s.artist,
                    album = s.album,
                    durationMs = s.durationMs,
                    position = position++,
                    addedAt = now,
                )
            }
            added = newItems.size
            if (newItems.isNotEmpty()) {
                plDao.insertItems(newItems)
                plDao.upsert(p.copy(updatedAt = now, dirty = true))
            }
        }
        if (added > 0) requestSync()
        return added
    }

    suspend fun removeFromPlaylist(playlistId: String, itemId: String) {
        val p = plDao.get(playlistId) ?: return
        db.withTransaction {
            plDao.deleteItem(itemId)
            renumber(playlistId)
            plDao.upsert(p.copy(updatedAt = System.currentTimeMillis(), dirty = true))
        }
        requestSync()
    }

    suspend fun moveInPlaylist(playlistId: String, from: Int, to: Int) {
        val p = plDao.get(playlistId) ?: return
        db.withTransaction {
            val items = plDao.items(playlistId).toMutableList()
            if (from !in items.indices || to !in items.indices) return@withTransaction
            val item = items.removeAt(from)
            items.add(to, item)
            plDao.clearItems(playlistId)
            plDao.insertItems(items.mapIndexed { i, it -> it.copy(position = i) })
            plDao.upsert(p.copy(updatedAt = System.currentTimeMillis(), dirty = true))
        }
        requestSync()
    }

    private suspend fun renumber(playlistId: String) {
        val items = plDao.items(playlistId)
        plDao.insertItems(items.mapIndexed { i, it -> it.copy(position = i) })
    }

    // ---------- Mais tocadas ----------

    fun mostPlayed(limit: Int = 100): Flow<List<MostPlayed>> =
        combine(histDao.observeTopStats(limit), music.index) { stats, idx ->
            stats.map { MostPlayed(it, idx.resolve(it.songKey, it.looseKey)) }
        }
}
