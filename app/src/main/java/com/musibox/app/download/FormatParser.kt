package com.musibox.app.download

import org.json.JSONArray
import org.json.JSONObject

enum class OptionKind { AUDIO, VIDEO }

/** Uma opção da lista "Baixar como". */
data class DownloadOption(
    val id: String,
    val label: String,
    val detail: String,
    val kind: OptionKind,
    val selector: String,
    val ext: String,
    val convertTo: String?,
    val mergeMp4: Boolean,
    val estimatedBytes: Long,
    val height: Int = 0,
)

data class MediaInfo(
    val url: String,
    val webpageUrl: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Long,
    val uploader: String?,
    val platform: Platform,
    val options: List<DownloadOption>,
    val moreOptions: List<DownloadOption>,
)

class ParseException(message: String) : Exception(message)

/** Lê o JSON do yt-dlp (-J) e monta as opções de áudio e vídeo. */
object FormatParser {

    private data class Fmt(
        val id: String,
        val ext: String,
        val vcodec: String,
        val acodec: String,
        val height: Int,
        val size: Long,
        val abr: Double,
        val tbr: Double,
        val note: String,
    ) {
        val hasVideo get() = vcodec != "none" && (vcodec.isNotEmpty() || height > 0)
        val hasAudio get() = acodec != "none" && (acodec.isNotEmpty() || !hasVideo)
        val audioOnly get() = hasAudio && !hasVideo
        val videoOnly get() = hasVideo && acodec == "none"
        val isAvc get() = vcodec.startsWith("avc")
    }

    fun parse(requestedUrl: String, json: String, mp3Kbps: Int = 192): MediaInfo {
        var root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ParseException("Resposta inválida do serviço.")
        }
        if (root.optString("_type") == "playlist") {
            val entries = root.optJSONArray("entries")
            val first = entries?.optJSONObject(0)
                ?: throw ParseException("Este link é uma playlist. Compartilhe um vídeo individual.")
            root = first
        }

        val title = root.optString("title").ifBlank { root.optString("id").ifBlank { "Mídia" } }
        val webpage = root.optString("webpage_url").ifBlank { requestedUrl }
        val duration = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0L else it.toLong() }
        val uploader = root.optString("uploader").ifBlank { root.optString("channel") }.ifBlank { null }
        val platform = LinkUtils.platformOf(webpage, root.optString("extractor_key"))
        val thumbnail = root.optString("thumbnail").ifBlank { null } ?: lastThumbnail(root.optJSONArray("thumbnails"))

        val formats = parseFormats(root.optJSONArray("formats"))
        val options = ArrayList<DownloadOption>()

        if (formats.isEmpty()) {
            // Link direto ou serviço com formato único.
            val ext = root.optString("ext").ifBlank { "mp4" }
            val audioExts = setOf("mp3", "m4a", "aac", "opus", "ogg", "wav", "flac")
            val isAudio = ext in audioExts
            val size = root.optLong("filesize", 0).takeIf { it > 0 } ?: root.optLong("filesize_approx", 0)
            options += DownloadOption(
                id = "original", label = "Original", detail = ext.uppercase(),
                kind = if (isAudio) OptionKind.AUDIO else OptionKind.VIDEO,
                selector = "b/best", ext = ext, convertTo = null, mergeMp4 = false, estimatedBytes = size,
            )
            if (!isAudio) {
                options += DownloadOption(
                    id = "mp3", label = "MP3 clássico", detail = "Somente áudio",
                    kind = OptionKind.AUDIO, selector = "ba/b", ext = "mp3", convertTo = "mp3", mergeMp4 = false,
                    estimatedBytes = estimateMp3(duration, mp3Kbps),
                )
            }
            return MediaInfo(requestedUrl, webpage, title, thumbnail, duration, uploader, platform, options, emptyList())
        }

        // ---------- Áudio ----------
        val audioOnly = formats.filter { it.audioOnly }
        val bestAudio = audioOnly.maxByOrNull { if (it.abr > 0) it.abr else it.tbr }
        val bestM4a = audioOnly.filter { it.ext == "m4a" }.maxByOrNull { if (it.abr > 0) it.abr else it.tbr }

        if (audioOnly.isNotEmpty()) {
            val fast = bestM4a ?: bestAudio!!
            options += DownloadOption(
                id = "audio_fast", label = "Rápido", detail = fast.ext.uppercase(),
                kind = OptionKind.AUDIO, selector = "ba[ext=m4a]/ba", ext = fast.ext, convertTo = null,
                mergeMp4 = false, estimatedBytes = fast.size.takeIf { it > 0 } ?: estimateBytes(duration, fast.abr),
            )
        } else {
            options += DownloadOption(
                id = "audio_fast", label = "Rápido", detail = "M4A",
                kind = OptionKind.AUDIO, selector = "ba/b", ext = "m4a", convertTo = "m4a",
                mergeMp4 = false, estimatedBytes = estimateBytes(duration, 128.0),
            )
        }
        options += DownloadOption(
            id = "audio_mp3", label = "MP3 clássico", detail = "MP3 ${mp3Kbps} kbps",
            kind = OptionKind.AUDIO, selector = "ba/b", ext = "mp3", convertTo = "mp3",
            mergeMp4 = false, estimatedBytes = estimateMp3(duration, mp3Kbps),
        )

        // ---------- Vídeo ----------
        val videoFormats = formats.filter { it.hasVideo && it.height > 0 }
        val heights = videoFormats.map { it.height }.filter { it >= 144 }.distinct().sortedDescending().take(6)
        val audioForMerge = bestM4a ?: bestAudio
        for (h in heights.sorted()) {
            val candidates = videoFormats.filter { it.height <= h }
            val chosen = candidates.filter { it.isAvc && it.height == h }.maxByOrNull { it.tbr }
                ?: candidates.filter { it.ext == "mp4" && it.height == h }.maxByOrNull { it.tbr }
                ?: candidates.filter { it.height == h }.maxByOrNull { it.tbr }
                ?: continue
            val needsAudio = chosen.videoOnly
            val size = chosen.size + if (needsAudio) (audioForMerge?.size ?: 0) else 0
            options += DownloadOption(
                id = "video_$h",
                label = videoLabel(h),
                detail = "MP4",
                kind = OptionKind.VIDEO,
                selector = "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h][ext=mp4]+ba[ext=m4a]/" +
                    "bv*[height<=$h]+ba/b[height<=$h]/b",
                ext = "mp4",
                convertTo = null,
                mergeMp4 = true,
                estimatedBytes = if (chosen.size > 0) size else 0,
                height = h,
            )
        }
        if (heights.isEmpty()) {
            val muxed = formats.filter { it.hasVideo || (!it.audioOnly && it.ext in setOf("mp4", "webm", "mov")) }
            if (muxed.isNotEmpty()) {
                val best = muxed.maxByOrNull { it.tbr }!!
                options += DownloadOption(
                    id = "video_best", label = "Melhor qualidade", detail = best.ext.uppercase(),
                    kind = OptionKind.VIDEO, selector = "b/bv*+ba", ext = "mp4", convertTo = null,
                    mergeMp4 = true, estimatedBytes = best.size,
                )
            }
        }

        // ---------- Mais formatos ----------
        val more = formats.filter { it.ext != "mhtml" }.map { f ->
            val res = when {
                f.hasVideo && f.height > 0 -> "${f.height}p"
                f.audioOnly && f.abr > 0 -> "${f.abr.toInt()} kbps"
                else -> f.note
            }
            val isVideo = f.hasVideo
            DownloadOption(
                id = "fmt_${f.id}",
                label = "${f.ext.uppercase()} • $res".trim().trimEnd('•').trim(),
                detail = listOfNotNull(
                    f.note.takeIf { it.isNotBlank() && it != res },
                    if (f.videoOnly) "vídeo + melhor áudio" else null,
                ).joinToString(" • "),
                kind = if (isVideo) OptionKind.VIDEO else OptionKind.AUDIO,
                selector = if (f.videoOnly) "${f.id}+ba/${f.id}" else f.id,
                ext = if (f.videoOnly) "mp4" else f.ext,
                convertTo = null,
                mergeMp4 = f.videoOnly,
                estimatedBytes = f.size,
                height = f.height,
            )
        }.sortedWith(compareBy<DownloadOption>({ it.kind }, { -it.height }, { -it.estimatedBytes }))

        return MediaInfo(requestedUrl, webpage, title, thumbnail, duration, uploader, platform, options, more)
    }

    private fun parseFormats(arr: JSONArray?): List<Fmt> {
        if (arr == null) return emptyList()
        val out = ArrayList<Fmt>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val protocol = o.optString("protocol")
            val ext = o.optString("ext")
            if (ext == "mhtml" || protocol == "mhtml") continue
            val id = o.optString("format_id")
            if (id.isBlank()) continue
            val size = o.optLong("filesize", 0).takeIf { it > 0 } ?: o.optLong("filesize_approx", 0)
            out += Fmt(
                id = id,
                ext = ext,
                vcodec = o.optString("vcodec").let { if (it == "null") "" else it },
                acodec = o.optString("acodec").let { if (it == "null") "" else it },
                height = o.optInt("height", 0),
                size = size,
                abr = o.optDouble("abr", 0.0).let { if (it.isNaN()) 0.0 else it },
                tbr = o.optDouble("tbr", 0.0).let { if (it.isNaN()) 0.0 else it },
                note = o.optString("format_note"),
            )
        }
        return out
    }

    private fun lastThumbnail(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        for (i in arr.length() - 1 downTo 0) {
            val url = arr.optJSONObject(i)?.optString("url")
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    fun videoLabel(h: Int): String = when {
        h <= 360 -> "Rápido (${h}p)"
        h <= 480 -> "Padrão (${h}p)"
        h <= 720 -> "Alta qualidade (${h}p)"
        h <= 1080 -> "Full HD (${h}p)"
        h <= 1440 -> "2K (${h}p)"
        else -> "4K (${h}p)"
    }

    fun estimateMp3(durationSec: Long, kbps: Int): Long = durationSec * kbps * 1000L / 8

    private fun estimateBytes(durationSec: Long, kbps: Double): Long =
        if (kbps <= 0) 0 else (durationSec * kbps * 1000 / 8).toLong()

    /** Escolhe a opção inicial conforme a preferência do usuário. */
    fun defaultOption(info: MediaInfo, preferAudio: Boolean, videoQuality: Int, mp3: Boolean): DownloadOption? {
        val audio = info.options.filter { it.kind == OptionKind.AUDIO }
        val video = info.options.filter { it.kind == OptionKind.VIDEO }
        if (preferAudio || video.isEmpty()) {
            return (if (mp3) audio.firstOrNull { it.convertTo == "mp3" } else audio.firstOrNull()) ?: audio.firstOrNull()
                ?: video.firstOrNull()
        }
        val ranked = video.filter { it.height in 1..videoQuality }.maxByOrNull { it.height }
        return ranked ?: video.minByOrNull { if (it.height == 0) Int.MAX_VALUE else it.height } ?: audio.firstOrNull()
    }
}
