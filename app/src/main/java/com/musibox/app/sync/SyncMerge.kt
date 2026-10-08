package com.musibox.app.sync

import com.musibox.app.data.db.FavoriteEntity
import com.musibox.app.data.db.PlayHistoryEntity
import com.musibox.app.data.db.PlaylistEntity
import com.musibox.app.data.db.PlaylistItemEntity

/** Regras de combinação entre dados locais e da nuvem (sem perder registros). */
object SyncMerge {

    /** Last-writer-wins: o remoto só substitui o local se for mais novo. */
    fun remoteWins(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
        localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

    /** União de itens de playlist, mantendo a ordem local e acrescentando os que faltam. */
    fun unionItems(
        playlistId: String,
        local: List<PlaylistItemEntity>,
        remote: List<PlaylistItemEntity>,
    ): List<PlaylistItemEntity> {
        val keys = local.mapTo(HashSet()) { it.songKey }
        val merged = local.sortedBy { it.position }.toMutableList()
        for (r in remote.sortedBy { it.position }) {
            if (keys.add(r.songKey)) merged += r
        }
        return merged.mapIndexed { i, item -> item.copy(playlistId = playlistId, position = i) }
    }

    /** União de favoritos: mantém todos os ativos dos dois lados. */
    fun unionFavorites(local: List<FavoriteEntity>, remote: List<FavoriteEntity>): List<FavoriteEntity> {
        val byKey = LinkedHashMap<String, FavoriteEntity>()
        local.filterNot { it.deleted }.forEach { byKey[it.songKey] = it }
        remote.filterNot { it.deleted }.forEach { r ->
            val l = byKey[r.songKey]
            if (l == null) byKey[r.songKey] = r.copy(dirty = true)
        }
        return byKey.values.toList()
    }

    /** Playlist local correspondente: mesmo id ou mesmo nome (ignorando maiúsculas). */
    fun matchPlaylist(remote: PlaylistEntity, locals: List<PlaylistEntity>): PlaylistEntity? =
        locals.firstOrNull { it.id == remote.id }
            ?: locals.firstOrNull { !it.deleted && it.name.trim().equals(remote.name.trim(), ignoreCase = true) }

    fun unionHistory(local: List<PlayHistoryEntity>, remote: List<PlayHistoryEntity>): List<PlayHistoryEntity> {
        val ids = local.mapTo(HashSet()) { it.id }
        return remote.filter { !it.deleted && ids.add(it.id) }
    }
}
