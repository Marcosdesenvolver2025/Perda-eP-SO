package com.musibox.app

import com.musibox.app.browser.LinkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkUtilsTest {
    @Test
    fun extraiLinkDeTextoCompartilhado() {
        assertEquals(
            "https://youtu.be/abc123",
            LinkUtils.extractUrl("Olha esse vídeo! https://youtu.be/abc123 muito bom"),
        )
        assertEquals(
            "https://vm.tiktok.com/ZMabc/",
            LinkUtils.extractUrl("https://vm.tiktok.com/ZMabc/."),
        )
        assertNull(LinkUtils.extractUrl("sem link aqui"))
        assertNull(LinkUtils.extractUrl(null))
    }

    @Test
    fun hosts() {
        assertEquals("youtube.com", LinkUtils.hostOf("https://m.youtube.com/watch?v=1"))
        assertEquals("shopee.com.br", LinkUtils.hostOf("https://www.shopee.com.br/"))
    }

    @Test
    fun addresses() {
        assertTrue(LinkUtils.looksLikeAddress("youtube.com"))
        assertTrue(LinkUtils.looksLikeAddress("https://x.com/a"))
        assertFalse(LinkUtils.looksLikeAddress("musica do roberto carlos"))
        assertEquals("https://youtube.com", LinkUtils.normalizeAddress("youtube.com"))
    }
}
