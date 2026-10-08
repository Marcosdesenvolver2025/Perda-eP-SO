package com.musibox.app.data.repo

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.room.withTransaction
import com.musibox.app.core.Permissions
import com.musibox.app.core.SongKeys
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.db.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(val count: Int) : ScanState
    data object NoPermission : ScanState
    data class Error(val message: String) : ScanState
}

data class ArtistInfo(val name: String, val songCount: Int, val sample: Song)
data class AlbumInfo(val id: Long, val title: String, val artist: String, val songCount: Int, val sample: Song)
data class FolderInfo(val path: String, val name: String, val songCount: Int)

/** Índice para reencontrar músicas pelas chaves sincronizadas. */
class SongIndex(songs: List<Song>) {
    val all: List<Song> = songs
    private val byContent = HashMap<String, Song>(songs.size * 2)
    private val byLoose = HashMap<String, Song>(songs.size * 2)
    private val byId = HashMap<Long, Song>(songs.size * 2)

    init {
        for (s in songs) {
            byContent.putIfAbsent(s.contentKey, s)
            byLoose.putIfAbsent(s.looseKey, s)
            byId[s.id] = s
        }
    }

    fun resolve(contentKey: String, looseKey: String): Song? = byContent[contentKey] ?: byLoose[looseKey]
    fun byId(id: Long): Song? = byId[id]

    companion object {
        val EMPTY = SongIndex(emptyList())
    }
}

val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

fun Song.contentUri(): Uri = Uri.parse(uri)
fun Song.artworkUri(): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)
fun Song.displayArtist(): String = SongKeys.displayArtist(artist)
fun Song.displayAlbum(): String = SongKeys.displayAlbum(album)

@OptIn(FlowPreview::class)
class MusicRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val dao = db.songDao()
    private val scanMutex = Mutex()
    private val rescanRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var observerRegistered = false

    val songs: Flow<List<Song>> = dao.observeAll()

    val index: StateFlow<SongIndex> = songs
        .map { SongIndex(it) }
        .stateIn(scope, SharingStarted.Eagerly, SongIndex.EMPTY)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    init {
        rescanRequests.debounce(1500).onEach { rescan() }.launchIn(scope)
    }

    fun hasPermission(): Boolean = Permissions.hasAudio(context)

    fun requestRescan() {
        rescanRequests.tryEmit(Unit)
    }

    /** Observa mudanças no MediaStore (novos downloads, arquivos apagados). */
    fun startObserving() {
        if (observerRegistered || !hasPermission()) return
        observerRegistered = true
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    requestRescan()
                }
            },
        )
    }

    /** Lê o MediaStore e atualiza o índice local. Retorna o total de músicas. */
    suspend fun rescan(): Int = scanMutex.withLock {
        if (!hasPermission()) {
            _scanState.value = ScanState.NoPermission
            return@withLock 0
        }
        _scanState.value = ScanState.Scanning
        try {
            val found = withContext(Dispatchers.IO) { queryMediaStore() }
            db.withTransaction {
                val existing = dao.allIds().toHashSet()
                val foundIds = found.mapTo(HashSet()) { it.id }
                val removed = existing.filterNot { it in foundIds }
                removed.chunked(500).forEach { dao.deleteIds(it) }
                found.chunked(500).forEach { dao.upsertAll(it) }
            }
            startObserving()
            _scanState.value = ScanState.Done(found.size)
            found.size
        } catch (e: SecurityException) {
            _scanState.value = ScanState.NoPermission
            0
        } catch (e: Exception) {
            _scanState.value = ScanState.Error(e.message ?: "Erro ao procurar músicas.")
            0
        }
    }

    private fun queryMediaStore(): List<Song> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.VOLUME_NAME,
        )
        // Lê TODOS os áudios do aparelho (não só os marcados como "música" pelo Android),
        // menos toques, notificações e alarmes. Áudios de pastas como Download, Telegram,
        // SnapTube etc. também entram.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0"
        val result = ArrayList<Song>()
        val seen = HashSet<Long>()
        val cursor = runCatching { context.contentResolver.query(collection, projection, selection, null, null) }
            .getOrNull()
            ?: context.contentResolver.query(collection, projection, null, null, null)
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val volCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                if (!seen.add(id)) continue
                val duration = c.getLong(durCol)
                if (duration in 1..4_999) continue // ignora sons muito curtos (avisos)
                val displayName = c.getString(nameCol) ?: ""
                val relPathRaw = c.getString(pathCol) ?: ""
                if (isVoiceNote(relPathRaw)) continue
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.')
                val artist = c.getString(artistCol) ?: ""
                val album = c.getString(albumCol) ?: ""
                val relPath = relPathRaw.trimEnd('/')
                val volume = c.getString(volCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volume), id)
                result += Song(
                    id = id,
                    uri = uri.toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = c.getLong(albumIdCol),
                    durationMs = duration,
                    folderName = relPath.substringAfterLast('/').ifBlank { "Armazenamento interno" },
                    folderPath = relPath,
                    size = c.getLong(sizeCol),
                    mimeType = c.getString(mimeCol) ?: "audio/*",
                    dateAddedMs = c.getLong(dateCol) * 1000L,
                    contentKey = SongKeys.contentKey(title, artist, duration),
                    looseKey = SongKeys.looseKey(title, artist),
                )
            }
        }
        return result
    }

    /** Mensagens de voz de apps de conversa não são músicas. */
    private fun isVoiceNote(relPath: String): Boolean {
        val p = relPath.lowercase()
        return "voice notes" in p || "whatsapp voice" in p || "voice messages" in p || "/ptt" in p
    }

    fun artists(songs: List<Song>): List<ArtistInfo> =
        songs.groupBy { it.displayArtist() }
            .map { (name, list) -> ArtistInfo(name, list.size, list.first()) }
            .sortedBy { it.name.lowercase() }

    fun albums(songs: List<Song>): List<AlbumInfo> =
        songs.groupBy { it.albumId }
            .map { (id, list) ->
                val first = list.first()
                AlbumInfo(id, first.displayAlbum(), first.displayArtist(), list.size, first)
            }
            .sortedBy { it.title.lowercase() }

    fun folders(songs: List<Song>): List<FolderInfo> =
        songs.groupBy { it.folderPath }
            .map { (path, list) -> FolderInfo(path, list.first().folderName, list.size) }
            .sortedBy { it.name.lowercase() }

    fun launchRescan() {
        scope.launch { rescan() }
    }
}
