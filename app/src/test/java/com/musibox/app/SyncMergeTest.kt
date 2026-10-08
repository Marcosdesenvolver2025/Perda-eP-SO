package com.musibox.app

import com.musibox.app.data.db.FavoriteEntity
import com.musibox.app.data.db.PlaylistEntity
import com.musibox.app.data.db.PlaylistItemEntity
import com.musibox.app.sync.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun fav(key: String, deleted: Boolean = false, t: Long = 1) =
        FavoriteEntity(key, key, key, "a", "b", 1, t, t, deleted, false)

    private fun item(id: String, key: String, pos: Int, pl: String = "p") =
        PlaylistItemEntity(id, pl, key, key, key, "a", "b", 1, pos, 0)

    @Test
    fun ultimaAlteracaoVence() {
        assertTrue(SyncMerge.remoteWins(null, 5))
        assertTrue(SyncMerge.remoteWins(4, 5))
        assertFalse(SyncMerge.remoteWins(5, 5))
        assertFalse(SyncMerge.remoteWins(6, 5))
    }

    @Test
    fun combinarFavoritosNaoPerdeNada() {
        val merged = SyncMerge.unionFavorites(listOf(fav("a"), fav("b", deleted = true)), listOf(fav("a"), fav("c"), fav("d", deleted = true)))
        assertEquals(setOf("a", "c"), merged.map { it.songKey }.toSet())
    }

    @Test
    fun combinarPlaylistMantemOrdemESemDuplicar() {
        val local = listOf(item("1", "x", 0), item("2", "y", 1))
        val remote = listOf(item("9", "y", 0, "r"), item("8", "z", 1, "r"))
        val merged = SyncMerge.unionItems("p", local, remote)
        assertEquals(listOf("x", "y", "z"), merged.map { it.songKey })
        assertEquals(listOf(0, 1, 2), merged.map { it.position })
        assertTrue(merged.all { it.playlistId == "p" })
    }

    @Test
    fun encontraPlaylistPorNome() {
        val locals = listOf(PlaylistEntity("l1", "Treino", 0, 0))
        val match = SyncMerge.matchPlaylist(PlaylistEntity("r1", " treino ", 0, 0), locals)
        assertEquals("l1", match?.id)
    }
}
