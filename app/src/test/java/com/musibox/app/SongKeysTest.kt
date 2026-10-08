package com.musibox.app

import com.musibox.app.core.Fmt
import com.musibox.app.core.SongKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SongKeysTest {
    @Test
    fun mesmaMusicaEmAparelhosDiferentesTemMesmaChave() {
        val a = SongKeys.contentKey("Eu Gosto Assim", "Gusttavo Lima", 185_400)
        val b = SongKeys.contentKey("eu gosto  assim", "GUSTTAVO LIMA", 185_900)
        assertEquals(a, b)
        assertEquals(SongKeys.looseKey("Coração", "Artista"), SongKeys.looseKey("Coracao", "artista"))
        assertNotEquals(a, SongKeys.contentKey("Outra", "Gusttavo Lima", 185_400))
    }

    @Test
    fun formataTempoETamanho() {
        assertEquals("03:05", Fmt.duration(185_000))
        assertEquals("1:01:01", Fmt.duration(3_661_000))
        assertEquals("4,2 MB", Fmt.size(4_404_019))
        assertEquals("0 B", Fmt.size(0))
    }
}
