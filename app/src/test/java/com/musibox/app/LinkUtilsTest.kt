package com.musibox.app

import com.musibox.app.download.LinkUtils
import com.musibox.app.download.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
