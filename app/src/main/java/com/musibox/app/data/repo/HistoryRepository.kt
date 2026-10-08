package com.musibox.app.data.repo

import androidx.room.withTransaction
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.db.PlayHistoryEntity
import com.musibox.app.data.db.PlayStatEntity
import com.musibox.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

data class PlayHistoryItem(val entry: PlayHistoryEntity, val song: com.musibox.app.data.db.Song?)

/** Histórico de reproduções e contagem de "mais tocadas". */
class HistoryRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val music: MusicRepository,
    private val requestSync: () -> Unit,
) {
    private val dao = db.historyDao()

    fun recentPlays(limit: Int = 500): Flow<List<PlayHistoryItem>> =
        combine(dao.observeRecent(limit), music.index) { list, idx ->
            list.map { PlayHistoryItem(it, idx.resolve(it.songKey, it.looseKey) ?: idx.byId(it.songId)) }
        }

    suspend fun recordPlay(
        songKey: String,
        looseKey: String,
        songId: Long,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ) {
        val s = settings.snapshot()
        val now = System.currentTimeMillis()
        db.withTransaction {
            val stat = dao.stat(songKey)
            dao.upsertStat(
                PlayStatEntity(
                    songKey = songKey,
                    looseKey = looseKey,
                    title = title,
                    artist = artist,
                    playCount = (stat?.playCount ?: 0) + 1,
                    lastPlayedAt = now,
                ),
            )
            if (s.historyEnabled) {
                dao.insert(
                    PlayHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        songKey = songKey,
                        looseKey = looseKey,
                        songId = songId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        playedAt = now,
                        deleted = false,
                        dirty = s.historySync,
                    ),
                )
            }
        }
        if (s.historyEnabled && s.historySync) requestSync()
    }

    /** Remove um item do histórico. Nunca apaga o arquivo de música. */
    suspend fun remove(id: String) {
        val s = settings.snapshot()
        if (s.historySync && s.ownerUid != null) {
            dao.softDelete(id)
            requestSync()
        } else {
            dao.hardDelete(id)
        }
    }

    /** Limpa o histórico de reproduções. Nunca apaga os arquivos de música. */
    suspend fun clearAll() {
        val s = settings.snapshot()
        if (s.historySync && s.ownerUid != null) {
            dao.softDeleteAll()
            requestSync()
        } else {
            dao.clear()
        }
    }
}
