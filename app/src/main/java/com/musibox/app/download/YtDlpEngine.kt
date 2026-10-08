package com.musibox.app.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

class DownloadError(message: String, val retryable: Boolean = false) : IOException(message)

data class SearchResult(
    val url: String,
    val title: String,
    val channel: String?,
    val durationSec: Long,
    val thumbnail: String?,
)

/**
 * Motor de download baseado no yt-dlp (com ffmpeg para converter para MP3 e juntar áudio+vídeo).
 * Não quebra DRM: conteúdo protegido é recusado com uma mensagem clara.
 */
class YtDlpEngine(private val context: Context) {
    private val initMutex = Mutex()
    @Volatile private var ready = false

    val isReady: Boolean get() = ready

    suspend fun ensureReady() {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                    ready = true
                } catch (e: Exception) {
                    throw DownloadError("Não foi possível iniciar o motor de download: ${e.message ?: "erro desconhecido"}")
                }
            }
        }
    }

    suspend fun fetchInfo(url: String, mp3Kbps: Int): MediaInfo = withContext(Dispatchers.IO) {
        ensureReady()
        val request = YoutubeDLRequest(url).apply {
            addOption("-J")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
        }
        val response = try {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = "info_${System.nanoTime()}",
                redirectErrorStream = false,
                callback = null,
            )
        } catch (e: YoutubeDL.CanceledException) {
            throw DownloadError("Operação cancelada.")
        } catch (e: Exception) {
            throw classify(e.message)
        }
        try {
            FormatParser.parse(url, response.out, mp3Kbps)
        } catch (e: ParseException) {
            throw DownloadError(e.message ?: "Não foi possível ler este link.")
        }
    }

    suspend fun search(query: String, limit: Int = 20): List<SearchResult> = withContext(Dispatchers.IO) {
        ensureReady()
        val request = YoutubeDLRequest("ytsearch$limit:$query").apply {
            addOption("--flat-playlist")
            addOption("-J")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
        }
        val response = try {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = "search_${System.nanoTime()}",
                redirectErrorStream = false,
                callback = null,
            )
        } catch (e: Exception) {
            throw classify(e.message)
        }
        val root = runCatching { JSONObject(response.out) }.getOrNull() ?: return@withContext emptyList()
        val entries = root.optJSONArray("entries") ?: return@withContext emptyList()
        val out = ArrayList<SearchResult>()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val id = e.optString("id")
            val url = e.optString("url").ifBlank { if (id.isNotBlank()) "https://www.youtube.com/watch?v=$id" else "" }
            if (url.isBlank()) continue
            val thumbs = e.optJSONArray("thumbnails")
            var thumb: String? = null
            if (thumbs != null && thumbs.length() > 0) thumb = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
            if (thumb.isNullOrBlank() && id.isNotBlank()) thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            out += SearchResult(
                url = url,
                title = e.optString("title").ifBlank { "Sem título" },
                channel = e.optString("channel").ifBlank { e.optString("uploader") }.ifBlank { null },
                durationSec = e.optDouble("duration", 0.0).let { if (it.isNaN()) 0L else it.toLong() },
                thumbnail = thumb,
            )
        }
        out
    }

    /**
     * Baixa para [dir] e devolve o arquivo final. Bloqueia até terminar; pode ser
     * interrompido por [stop]. Partes já baixadas são reaproveitadas ao continuar.
     */
    suspend fun download(
        processId: String,
        url: String,
        selector: String,
        convertTo: String?,
        mergeMp4: Boolean,
        isAudio: Boolean,
        mp3Kbps: Int,
        dir: File,
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        ensureReady()
        dir.mkdirs()
        val request = YoutubeDLRequest(url).apply {
            addOption("-o", File(dir, "media.%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--no-warnings")
            addOption("--continue")
            addOption("-f", selector)
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--socket-timeout", "20")
            addOption("-N", "4")
            if (mergeMp4) addOption("--merge-output-format", "mp4")
            if (convertTo != null) {
                addOption("-x")
                addOption("--audio-format", convertTo)
                if (convertTo == "mp3") addOption("--audio-quality", "${mp3Kbps}K")
            }
            if (isAudio) {
                addOption("--embed-metadata")
                if (convertTo == "mp3") {
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")
                }
            }
        }
        try {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = processId,
                redirectErrorStream = false,
                callback = { progress, eta, line -> onProgress(progress, eta, line) },
            )
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            // Se apenas a capa não pôde ser embutida, o MP3 está pronto e pode ser usado.
            val msg = e.message.orEmpty().lowercase(Locale.ROOT)
            val mp3 = File(dir, "media.mp3")
            if (convertTo == "mp3" && mp3.exists() && mp3.length() > 0 && "thumbnail" in msg) return@withContext mp3
            throw classify(e.message)
        }
        findOutput(dir) ?: throw DownloadError("O arquivo baixado não foi encontrado.")
    }

    fun stop(processId: String): Boolean = runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    suspend fun update(): String = withContext(Dispatchers.IO) {
        ensureReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> "Motor de download atualizado."
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "O motor de download já está atualizado."
            else -> "Verificação concluída."
        }
    }

    fun version(): String? = runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull()

    companion object {
        private val ignoredExt = setOf("part", "ytdl", "temp", "tmp", "jpg", "jpeg", "webp", "png", "json", "vtt", "srt")

        fun findOutput(dir: File): File? =
            dir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && it.extension.lowercase(Locale.ROOT) !in ignoredExt && !it.name.contains(".part") }
                ?.maxByOrNull { it.length() }

        fun classify(raw: String?): DownloadError {
            val t = raw.orEmpty().lowercase(Locale.ROOT)
            return when {
                "drm" in t -> DownloadError("Este conteúdo é protegido (DRM) e não pode ser baixado.")
                "unsupported url" in t -> DownloadError("Link não suportado.")
                "private video" in t || "video is private" in t -> DownloadError("Este vídeo é privado.")
                "not a bot" in t || "sign in to confirm" in t ->
                    DownloadError("A plataforma pediu verificação para este acesso. Tente mais tarde ou atualize o motor em Configurações.")
                "login required" in t || "requires authentication" in t || "log in" in t || "cookies" in t ->
                    DownloadError("Este conteúdo exige login na plataforma e não pode ser baixado.")
                "confirm your age" in t || ("age" in t && "restrict" in t) ->
                    DownloadError("Conteúdo com restrição de idade não pode ser baixado.")
                "not available in your country" in t || ("geo" in t && "restrict" in t) ->
                    DownloadError("Conteúdo indisponível no seu país.")
                "no space left" in t || "enospc" in t -> DownloadError("Espaço insuficiente no aparelho.")
                "requested format is not available" in t -> DownloadError("Formato não suportado para este conteúdo.")
                "unable to download webpage" in t || "timed out" in t || "name resolution" in t ||
                    "network is unreachable" in t || "failed to resolve" in t || "connection" in t ||
                    "errno 7" in t || "errno 101" in t || "errno 110" in t || "http error 5" in t ->
                    DownloadError("Sem conexão com a internet.", retryable = true)
                "video unavailable" in t || "http error 404" in t || "not found" in t ->
                    DownloadError("Conteúdo não encontrado ou indisponível.")
                else -> DownloadError("Não foi possível baixar este arquivo.")
            }
        }
    }
}
