package com.musibox.app

import com.musibox.app.download.FormatParser
import com.musibox.app.download.OptionKind
import com.musibox.app.download.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatParserTest {
    private val youtubeJson = """
        {
          "id": "abc", "title": "Minha Música", "duration": 200.5, "uploader": "Canal",
          "extractor_key": "Youtube", "webpage_url": "https://www.youtube.com/watch?v=abc",
          "thumbnail": "https://i.ytimg.com/vi/abc/hq.jpg",
          "formats": [
            {"format_id": "sb0", "ext": "mhtml", "protocol": "mhtml", "vcodec": "none", "acodec": "none"},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129.5, "filesize": 3200000},
            {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus", "abr": 140.1, "filesize": 3300000},
            {"format_id": "18", "ext": "mp4", "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "height": 360, "tbr": 500, "filesize": 9000000},
            {"format_id": "136", "ext": "mp4", "vcodec": "avc1.4d401f", "acodec": "none", "height": 720, "tbr": 1500, "filesize": 20000000},
            {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none", "height": 1080, "tbr": 3000, "filesize_approx": 40000000},
            {"format_id": "248", "ext": "webm", "vcodec": "vp9", "acodec": "none", "height": 1080, "tbr": 2800, "filesize": null}
          ]
        }
    """.trimIndent()

    @Test
    fun montaOpcoesDeAudioEVideo() {
        val info = FormatParser.parse("https://youtu.be/abc", youtubeJson, 192)
        assertEquals("Minha Música", info.title)
        assertEquals(200L, info.durationSec)
        assertEquals(Platform.YOUTUBE, info.platform)
        val audio = info.options.filter { it.kind == OptionKind.AUDIO }
        assertEquals(2, audio.size)
        assertEquals("m4a", audio.first().ext)
        assertEquals(3200000L, audio.first().estimatedBytes)
        val mp3 = audio.first { it.convertTo == "mp3" }
        assertEquals(200L * 192 * 1000 / 8, mp3.estimatedBytes)
        val heights = info.options.filter { it.kind == OptionKind.VIDEO }.map { it.height }
        assertEquals(listOf(360, 720, 1080), heights)
        val hd = info.options.first { it.height == 720 }
        assertEquals(20000000L + 3200000L, hd.estimatedBytes)
        assertTrue(hd.mergeMp4)
        assertTrue(info.moreOptions.none { it.id == "fmt_sb0" })
    }

    @Test
    fun escolheOpcaoPadrao() {
        val info = FormatParser.parse("https://youtu.be/abc", youtubeJson)
        assertEquals(720, FormatParser.defaultOption(info, false, 720, true)!!.height)
        assertEquals("mp3", FormatParser.defaultOption(info, true, 720, true)!!.convertTo)
        assertEquals(360, FormatParser.defaultOption(info, false, 480, true)!!.height)
    }

    @Test
    fun linkDiretoSemFormatos() {
        val json = """{"title": "arquivo", "ext": "mp3", "filesize": 1000}"""
        val info = FormatParser.parse("https://exemplo.com/a.mp3", json)
        assertEquals(1, info.options.size)
        assertEquals(OptionKind.AUDIO, info.options.first().kind)
        assertEquals(1000L, info.options.first().estimatedBytes)
    }

    @Test
    fun playlistUsaPrimeiroItem() {
        val json = """{"_type": "playlist", "entries": [{"title": "Primeiro", "ext": "mp4"}]}"""
        val info = FormatParser.parse("https://exemplo.com/p", json)
        assertNotNull(info)
        assertEquals("Primeiro", info.title)
    }

    @Test
    fun postSoComImagemViraOpcaoFoto() {
        val json = """
            {"id": "p1", "title": "Pin", "extractor_key": "Pinterest", "webpage_url": "https://www.pinterest.com/pin/1/",
             "formats": [
               {"format_id": "small", "ext": "jpg", "url": "https://i.pinimg.com/236x/a.jpg", "width": 236, "height": 300},
               {"format_id": "orig", "ext": "jpg", "url": "https://i.pinimg.com/originals/a.jpg", "width": 1000, "height": 1300}
             ]}
        """.trimIndent()
        val info = FormatParser.parse("https://pin.it/x", json)
        assertEquals(1, info.options.size)
        val photo = info.options.first()
        assertEquals(OptionKind.PHOTO, photo.kind)
        assertEquals("direct:https://i.pinimg.com/originals/a.jpg", photo.selector)
        assertEquals(photo, FormatParser.defaultOption(info, preferAudio = true, videoQuality = 720, mp3 = true))
    }
}
