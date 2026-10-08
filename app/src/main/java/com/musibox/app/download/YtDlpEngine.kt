package com.musibox.app.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Erro de download com mensagem em português. [detail] guarda a linha técnica do yt-dlp
 * (mostrada em "Detalhes" para diagnóstico). [platformChanged] indica que vale atualizar o motor.
 */
class DownloadError(
    message: String,
    val retryable: Boolean = false,
    val detail: String? = null,
    val platformChanged: Boolean = false,
) : IOException(message)

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
    private val updateMutex = Mutex()
    @Volatile private var ready = false
    @Volatile private var nightlyTried = false
    private val prefs = context.getSharedPreferences("ytdlp_engine", Context.MODE_PRIVATE)

    /** Cache do yt-dlp (guarda a solução dos desafios do YouTube; acelera os próximos links). */
    private val cacheDir: File get() = File(context.noBackupFilesDir, "ytdlp-cache").apply { mkdirs() }

    val isReady: Boolean get() = ready

    private val _stage = MutableStateFlow<String?>(null)
    /** Etapa atual para mostrar na tela ("Atualizando o motor…", "Lendo o link…"). */
    val stage: StateFlow<String?> = _stage.asStateFlow()

    private fun YoutubeDLRequest.common(): YoutubeDLRequest = apply {
        addOption("--cache-dir", cacheDir.absolutePath)
        addOption("--no-warnings")
        addOption("--socket-timeout", "30")
    }

    /**
     * Atualiza o yt-dlp quando a última verificação tem mais de [maxAgeMs]. As plataformas mudam
     * com frequência e uma versão antiga deixa de conseguir ler os vídeos.
     */
    suspend fun updateIfStale(maxAgeMs: Long = 12L * 60 * 60 * 1000) {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < maxAgeMs) return
        runCatching { updateNow(nightly = false) }
    }

    /** Força a atualização (usada quando um link falha por mudança na plataforma). */
    suspend fun updateNow(nightly: Boolean): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        updateMutex.withLock {
            _stage.value = "Atualizando o motor de download…"
            try {
                val channel = if (nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
                val status = withTimeoutOrNull(120_000) { YoutubeDL.getInstance().updateYoutubeDL(context, channel) }
                if (status != null) prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                status == YoutubeDL.UpdateStatus.DONE
            } catch (e: Exception) {
                // Sem acesso ao servidor de atualização: tenta de novo em 1 hora.
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis() - 11L * 60 * 60 * 1000).apply()
                false
            } finally {
                _stage.value = null
            }
        }
    }

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
        updateIfStale()
        try {
            fetchInfoOnce(url, mp3Kbps)
        } catch (e: DownloadError) {
            // A plataforma mudou: atualiza o motor (versão de desenvolvimento, mais recente) e tenta de novo.
            if (!e.platformChanged || nightlyTried) throw e
            nightlyTried = true
            updateNow(nightly = true)
            fetchInfoOnce(url, mp3Kbps)
        } finally {
            _stage.value = null
        }
    }

    private fun fetchInfoOnce(url: String, mp3Kbps: Int): MediaInfo {
        _stage.value = "Lendo o link…"
        val request = YoutubeDLRequest(url).common().apply {
            addOption("-J")
            addOption("--no-playlist")
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
        return try {
            FormatParser.parse(url, response.out, mp3Kbps)
        } catch (e: ParseException) {
            throw DownloadError(e.message ?: "Não foi possível ler este link.", detail = errorLine(response.err))
        }
    }

    suspend fun search(query: String, limit: Int = 20): List<SearchResult> = withContext(Dispatchers.IO) {
        ensureReady()
        val request = YoutubeDLRequest("ytsearch$limit:$query").common().apply {
            addOption("--flat-playlist")
            addOption("-J")
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
        updateIfStale()
        try {
            downloadOnce(processId, url, selector, convertTo, mergeMp4, isAudio, mp3Kbps, dir, onProgress)
        } catch (e: DownloadError) {
            if (!e.platformChanged || nightlyTried) throw e
            nightlyTried = true
            updateNow(nightly = true)
            downloadOnce(processId, url, selector, convertTo, mergeMp4, isAudio, mp3Kbps, dir, onProgress)
        }
    }

    private fun downloadOnce(
        processId: String,
        url: String,
        selector: String,
        convertTo: String?,
        mergeMp4: Boolean,
        isAudio: Boolean,
        mp3Kbps: Int,
        dir: File,
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ): File {
        val request = YoutubeDLRequest(url).common().apply {
            addOption("-o", File(dir, "media.%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--continue")
            addOption("-f", selector)
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
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
            if (convertTo == "mp3" && mp3.exists() && mp3.length() > 0 && "thumbnail" in msg) return mp3
            throw classify(e.message)
        }
        return findOutput(dir) ?: throw DownloadError("O arquivo baixado não foi encontrado.")
    }

    fun stop(processId: String): Boolean = runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    suspend fun update(): String = withContext(Dispatchers.IO) {
        ensureReady()
        val status = updateMutex.withLock {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
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

        private const val KEY_LAST_CHECK = "last_update_check"

        /** Última linha "ERROR:" do yt-dlp (ou a última linha com texto). */
        fun errorLine(raw: String?): String? {
            val lines = raw.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
            val line = lines.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim() ?: lines.lastOrNull()
            return line?.take(400)
        }

        fun classify(raw: String?): DownloadError {
            val t = raw.orEmpty().lowercase(Locale.ROOT)
            val detail = errorLine(raw)
            fun err(msg: String, retryable: Boolean = false, changed: Boolean = false) =
                DownloadError(msg, retryable, detail, changed)
            return when {
                "drm" in t -> err("Este conteúdo é protegido (DRM) e não pode ser baixado.")
                "unsupported url" in t -> err("Link não suportado. Abra o vídeo e copie o link dele.")
                "private video" in t || "video is private" in t -> err("Este vídeo é privado.")
                "not a bot" in t || "sign in to confirm" in t ->
                    err("O YouTube pediu verificação para esta conexão. Tente de novo em alguns minutos ou use outra rede (Wi-Fi/dados).", changed = true)
                "confirm your age" in t || ("age" in t && "restrict" in t) ->
                    err("Conteúdo com restrição de idade não pode ser baixado.")
                "login required" in t || "requires authentication" in t || "log in" in t || "cookies" in t ->
                    err("Este conteúdo exige login na plataforma e não pode ser baixado.")
                "not available in your country" in t || ("geo" in t && "restrict" in t) ->
                    err("Conteúdo indisponível no seu país.")
                "no space left" in t || "enospc" in t -> err("Espaço insuficiente no aparelho.")
                "unable to download webpage" in t || "timed out" in t || "name resolution" in t ||
                    "network is unreachable" in t || "failed to resolve" in t || "connection reset" in t ||
                    "connection refused" in t || "errno 7" in t || "errno 101" in t || "errno 110" in t ||
                    "http error 5" in t -> err("Falha na conexão. Verifique a internet e tente de novo.", retryable = true)
                "video unavailable" in t || "http error 404" in t || "this video has been removed" in t ->
                    err("Conteúdo não encontrado ou removido.")
                // Mudanças na plataforma: atualizar o motor costuma resolver.
                "requested format is not available" in t || "only images are available" in t ||
                    "challenge" in t || "nsig" in t || "signature" in t || "unable to extract" in t ||
                    "http error 403" in t || "javascript" in t || "player" in t || "precondition" in t ->
                    err("A plataforma mudou e o motor precisou ser atualizado. Toque em Tentar de novo.", changed = true)
                else -> err("Não foi possível baixar este arquivo.", changed = true)
            }
        }
    }
}
