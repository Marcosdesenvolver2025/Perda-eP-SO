package com.musibox.app

import com.musibox.app.download.LinkUtils
import com.musibox.app.download.Platform
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
    fun reconhecePlataformas() {
        assertEquals(Platform.YOUTUBE, LinkUtils.platformOf("https://www.youtube.com/watch?v=1"))
        assertEquals(Platform.YOUTUBE, LinkUtils.platformOf("https://m.youtube.com/shorts/1"))
        assertEquals(Platform.YOUTUBE, LinkUtils.platformOf("https://youtu.be/1"))
        assertEquals(Platform.TIKTOK, LinkUtils.platformOf("https://vm.tiktok.com/x"))
        assertEquals(Platform.INSTAGRAM, LinkUtils.platformOf("https://www.instagram.com/reel/x/"))
        assertEquals(Platform.TWITTER, LinkUtils.platformOf("https://x.com/a/status/1"))
        assertEquals(Platform.OTHER, LinkUtils.platformOf("https://exemplo.com/musica.mp3"))
        assertEquals(Platform.TIKTOK, LinkUtils.platformOf("https://exemplo.com", "TikTok"))
    }

    @Test
    fun mediaPages() {
        assertTrue(LinkUtils.isMediaPage("https://m.youtube.com/watch?v=abc123"))
        assertTrue(LinkUtils.isMediaPage("https://music.youtube.com/watch?v=iXSbnL56SWs&si=x"))
        assertTrue(LinkUtils.isMediaPage("https://www.youtube.com/shorts/abc"))
        assertTrue(LinkUtils.isMediaPage("https://youtu.be/abc"))
        assertFalse(LinkUtils.isMediaPage("https://m.youtube.com/"))
        assertFalse(LinkUtils.isMediaPage("https://m.youtube.com/results?search_query=x"))
        assertTrue(LinkUtils.isMediaPage("https://www.tiktok.com/@user/video/123"))
        assertFalse(LinkUtils.isMediaPage("https://www.tiktok.com/foryou"))
        assertTrue(LinkUtils.isMediaPage("https://www.instagram.com/reel/Cxyz/"))
        assertFalse(LinkUtils.isMediaPage("https://www.instagram.com/"))
        assertTrue(LinkUtils.isMediaPage("https://x.com/user/status/123"))
        assertTrue(LinkUtils.isMediaPage("https://vimeo.com/123456"))
        assertFalse(LinkUtils.isMediaPage("https://vimeo.com/"))
        assertTrue(LinkUtils.isMediaPage("https://m.soundcloud.com/artist/track"))
        assertFalse(LinkUtils.isMediaPage("https://m.soundcloud.com/discover/x"))
        assertFalse(LinkUtils.isMediaPage(null))
    }

    @Test
    fun addresses() {
        assertTrue(LinkUtils.looksLikeAddress("youtube.com"))
        assertTrue(LinkUtils.looksLikeAddress("https://x.com/a"))
        assertFalse(LinkUtils.looksLikeAddress("musica do roberto carlos"))
        assertEquals("https://youtube.com", LinkUtils.normalizeAddress("youtube.com"))
    }
}
