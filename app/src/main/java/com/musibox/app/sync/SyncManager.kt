package com.musibox.app.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.musibox.app.MusiBoxApp
import com.musibox.app.core.ConnectivityObserver
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.db.FavoriteEntity
import com.musibox.app.data.db.PlayHistoryEntity
import com.musibox.app.data.db.PlaylistEntity
import com.musibox.app.data.db.PlaylistItemEntity
import com.musibox.app.data.db.QueueItemEntity
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class SyncStatus(val label: String) {
    SIGNED_OUT("Sem conta conectada"),
    NEEDS_SETUP("Escolha como sincronizar"),
    SYNCED("Sincronizado"),
    SYNCING("Sincronizando..."),
    OFFLINE("Sem conexão"),
    PENDING("Sincronização pendente"),
    ERROR("Erro ao sincronizar"),
}

data class SyncUiState(
    val status: SyncStatus = SyncStatus.SIGNED_OUT,
    val lastSyncAt: Long = 0,
    val error: String? = null,
    val pending: Int = 0,
)

/** O que fazer quando o usuário entra com a conta. */
sealed interface SignInResolution {
    /** Há dados na nuvem e o aparelho está vazio: "Encontramos dados sincronizados da sua conta." */
    data class OfferRestore(val uid: String) : SignInResolution

    /** Há dados nos dois lados: combinar, usar os deste aparelho ou restaurar os da nuvem. */
    data class Conflict(val uid: String) : SignInResolution

    /** Os dados deste aparelho pertencem a outra conta; nunca misturar. */
    data class OtherAccount(val uid: String) : SignInResolution

    /** Não foi possível verificar a nuvem (sem internet). */
    data class CheckFailed(val uid: String, val message: String) : SignInResolution
}

enum class RestoreMode { CLOUD, MERGE, LOCAL, LATER }

/**
 * Sincronização "offline primeiro": tudo é salvo no banco local imediatamente e enviado ao
 * Firestore (users/{uid}/...) quando houver internet. Cada conta só acessa os próprios dados.
 */
class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val auth: AuthManager,
    private val connectivity: ConnectivityObserver,
    scope: CoroutineScope,
) {
    private val fs: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val favDao = db.favoriteDao()
    private val plDao = db.playlistDao()
    private val histDao = db.historyDao()

    private val running = MutableStateFlow(false)
    private val lastError = MutableStateFlow<String?>(null)

    private val _resolution = MutableStateFlow<SignInResolution?>(null)
    val resolution: StateFlow<SignInResolution?> = _resolution.asStateFlow()

    private val pendingCount: Flow<Int> = combine(
        favDao.observeDirtyCount(),
        plDao.observeDirtyCount(),
        histDao.observeDirtyCount(),
        settings.settings,
    ) { f, p, h, s ->
        f + p + (if (s.historySync) h else 0) + (if (s.settingsDirty) 1 else 0) + (if (s.lastPlaybackDirty) 1 else 0)
    }

    private val base = combine(auth.user, settings.settings, connectivity.online) { u, s, online -> Triple(u, s, online) }
    private val work = combine(running, lastError, pendingCount) { r, e, p -> Triple(r, e, p) }

    val state: StateFlow<SyncUiState> = combine(base, work) { (user, s, online), (isRunning, error, pending) ->
        val status = when {
            user == null -> SyncStatus.SIGNED_OUT
            s.ownerUid != user.uid -> SyncStatus.NEEDS_SETUP
            isRunning -> SyncStatus.SYNCING
            !online -> SyncStatus.OFFLINE
            error != null -> SyncStatus.ERROR
            pending > 0 -> SyncStatus.PENDING
            else -> SyncStatus.SYNCED
        }
        SyncUiState(status, s.lastSyncAt, error, pending)
    }.stateIn(scope, SharingStarted.Eagerly, SyncUiState())

    private fun userRoot(uid: String): DocumentReference = fs.collection("users").document(uid)

    // ------------------------------------------------------------------
    // Agendamento

    fun requestSync(delaySeconds: Long = 5) {
        if (auth.currentUser == null) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    // ------------------------------------------------------------------
    // Login / conflitos

    /** Decide o que fazer depois do login. Pode abrir um diálogo de escolha. */
    suspend fun onSignedIn(user: AccountUser) {
        val s = settings.snapshot()
        when {
            s.ownerUid == user.uid -> {
                _resolution.value = null
                requestSync(0)
            }
            s.ownerUid != null && localHasData() -> _resolution.value = SignInResolution.OtherAccount(user.uid)
            else -> {
                val cloud = try {
                    cloudHasData(user.uid)
                } catch (e: Exception) {
                    _resolution.value = SignInResolution.CheckFailed(user.uid, friendly(e))
                    return
                }
                val local = localHasData()
                when {
                    !cloud -> {
                        // Primeira vez com esta conta: envia o que existe no aparelho.
                        settings.setOwner(user.uid, restoreDeclined = false)
                        markAllLocalDirty()
                        _resolution.value = null
                        requestSync(0)
                    }
                    !local -> _resolution.value = SignInResolution.OfferRestore(user.uid)
                    else -> _resolution.value = SignInResolution.Conflict(user.uid)
                }
            }
        }
    }

    /** Verifica de novo ao abrir o app (ex.: login feito sem internet). */
    suspend fun checkPendingSetup() {
        val user = auth.currentUser ?: return
        val s = settings.snapshot()
        if (s.ownerUid != user.uid && _resolution.value == null) onSignedIn(user)
        else if (s.ownerUid == user.uid) requestSync(2)
    }

    fun dismissResolution() {
        _resolution.value = null
    }

    suspend fun resolve(mode: RestoreMode) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        _resolution.value = null
        when (mode) {
            RestoreMode.CLOUD -> {
                wipeLocalSyncedData()
                settings.setOwner(uid, restoreDeclined = false)
                restoreAllFromCloud(uid)
            }
            RestoreMode.MERGE -> {
                settings.setOwner(uid, restoreDeclined = false)
                mergeFromCloud(uid)
                markAllLocalDirty()
                performSync()
            }
            RestoreMode.LOCAL -> {
                settings.setOwner(uid, restoreDeclined = false)
                tombstoneRemoteNotInLocal(uid)
                markAllLocalDirty()
                performSync()
            }
            RestoreMode.LATER -> {
                settings.setOwner(uid, restoreDeclined = true)
                requestSync(0)
            }
        }
    }

    /** "Substituir": apaga playlists/favoritos/histórico locais de outra conta antes de entrar. */
    suspend fun replaceOtherAccountData() {
        val user = auth.currentUser ?: return
        wipeLocalSyncedData()
        settings.setOwner(null, restoreDeclined = false)
        _resolution.value = null
        onSignedIn(user)
    }

    /** Restauração manual (Configurações > Conta e sincronização > Restaurar dados). */
    suspend fun restoreNow(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Entre com sua Conta Google.")
        settings.setOwner(user.uid, restoreDeclined = false)
        mergeFromCloud(user.uid)
        settings.setSyncTimes(System.currentTimeMillis(), System.currentTimeMillis() - SKEW_MS)
        requestSync(0)
    }

    suspend fun signOut(keepLocalData: Boolean) {
        auth.signOut()
        if (!keepLocalData) {
            wipeLocalSyncedData()
            settings.setOwner(null, restoreDeclined = false)
        }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        _resolution.value = null
        lastError.value = null
    }

    /** Exclui tudo o que existe na nuvem para esta conta (os arquivos do aparelho não são tocados). */
    suspend fun deleteCloudData(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Você não está conectado.")
        val root = userRoot(user.uid)
        for (name in COLLECTIONS) {
            val docs = root.collection(name).get(Source.SERVER).await().documents
            docs.chunked(400).forEach { chunk ->
                val batch = fs.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        root.delete().await()
        // Depois de excluir, a conta é desconectada para não reenviar os dados.
        auth.signOut()
        settings.setOwner(null, restoreDeclined = false)
        settings.setSyncTimes(0, 0)
        markAllLocalDirty()
    }

    // ------------------------------------------------------------------
    // Sincronização

    /** Envia pendências e recebe alterações. Retorna true se não sobrou nada pendente. */
    suspend fun performSync(): Boolean {
        val user = auth.currentUser ?: return true
        val s = settings.snapshot()
        if (s.ownerUid != user.uid) return true
        if (!connectivity.isOnlineNow()) return false
        running.value = true
        return try {
            val pullStart = System.currentTimeMillis()
            push(user, s)
            if (!s.restoreDeclined) pull(user.uid, s.lastPullAt)
            settings.setSyncTimes(
                lastSyncAt = System.currentTimeMillis(),
                lastPullAt = if (!s.restoreDeclined) pullStart - SKEW_MS else null,
            )
            lastError.value = null
            !hasPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError.value = friendly(e)
            false
        } finally {
            running.value = false
        }
    }

    private suspend fun hasPending(): Boolean {
        val s = settings.snapshot()
        return favDao.dirty().isNotEmpty() || plDao.dirty().isNotEmpty() ||
            (s.historySync && histDao.dirty().isNotEmpty()) || s.settingsDirty || s.lastPlaybackDirty
    }

    private suspend fun push(user: AccountUser, s: AppSettings) {
        val root = userRoot(user.uid)
        val ops = ArrayList<(WriteBatch) -> Unit>()

        val favs = favDao.dirty()
        favs.forEach { f -> ops += { b -> b.set(root.collection("favorites").document(f.songKey), favoriteToMap(f)) } }

        val playlists = plDao.dirty()
        val items = playlists.associate { it.id to plDao.items(it.id) }
        playlists.forEach { p ->
            ops += { b -> b.set(root.collection("playlists").document(p.id), playlistToMap(p, items[p.id].orEmpty())) }
        }

        val history = if (s.historySync) histDao.dirty() else emptyList()
        history.forEach { h -> ops += { b -> b.set(root.collection("history").document(h.id), historyToMap(h)) } }

        if (s.settingsDirty) {
            val values = settings.syncedValues()
            ops += { b ->
                b.set(root.collection("settings").document("app"), mapOf("values" to values, "updatedAt" to s.settingsUpdatedAt))
            }
        }

        val lastPlayback = if (s.lastPlaybackDirty) lastPlaybackMap(s) else null
        if (lastPlayback != null) ops += { b -> b.set(root.collection("appData").document("lastPlayback"), lastPlayback) }

        ops += { b ->
            b.set(
                root,
                mapOf(
                    "displayName" to user.name,
                    "email" to user.email,
                    "photoUrl" to user.photoUrl,
                    "lastSyncAt" to System.currentTimeMillis(),
                    "app" to "MusiBox",
                ),
                SetOptions.merge(),
            )
        }

        ops.chunked(400).forEach { chunk ->
            val batch = fs.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }

        favs.forEach { favDao.markClean(it.songKey, it.updatedAt) }
        favDao.purgeDeleted()
        playlists.forEach { plDao.markClean(it.id, it.updatedAt) }
        plDao.purgeDeleted()
        history.forEach { histDao.markClean(it.id, it.deleted) }
        histDao.purgeDeleted()
        if (s.settingsDirty) settings.markSettingsClean(s.settingsUpdatedAt)
        if (s.lastPlaybackDirty) settings.setLastPlaybackDirty(false)
    }

    private suspend fun fetch(col: CollectionReference, since: Long): List<DocumentSnapshot> {
        val query: Query = if (since > 0) col.whereGreaterThan("updatedAt", since) else col
        return query.get(Source.SERVER).await().documents
    }

    private suspend fun pull(uid: String, since: Long) {
        val root = userRoot(uid)
        val favs = fetch(root.collection("favorites"), since).mapNotNull { favoriteFromDoc(it) }
        val playlists = fetch(root.collection("playlists"), since).mapNotNull { playlistFromDoc(it) }
        val historySync = settings.snapshot().historySync
        val history = if (historySync) fetch(root.collection("history"), since).mapNotNull { historyFromDoc(it) } else emptyList()

        db.withTransaction {
            for (r in favs) {
                val l = favDao.get(r.songKey)
                if (!SyncMerge.remoteWins(l?.updatedAt, r.updatedAt)) continue
                if (r.deleted) favDao.hardDelete(r.songKey) else favDao.upsert(r.copy(dirty = false))
            }
            for ((p, its) in playlists) {
                val l = plDao.get(p.id)
                if (!SyncMerge.remoteWins(l?.updatedAt, p.updatedAt)) continue
                plDao.clearItems(p.id)
                if (p.deleted) {
                    plDao.hardDelete(p.id)
                } else {
                    plDao.upsert(p.copy(dirty = false))
                    plDao.insertItems(its)
                }
            }
            for (h in history) {
                val l = histDao.get(h.id)
                if (h.deleted) {
                    if (l != null) histDao.hardDelete(h.id)
                } else if (l == null) {
                    histDao.insert(h.copy(dirty = false))
                }
            }
        }

        val local = settings.snapshot()
        val sdoc = root.collection("settings").document("app").get(Source.SERVER).await()
        if (sdoc.exists()) {
            val updatedAt = sdoc.getLong("updatedAt") ?: 0L
            @Suppress("UNCHECKED_CAST")
            val values = sdoc.get("values") as? Map<String, Any?>
            if (values != null && updatedAt > local.settingsUpdatedAt && !local.settingsDirty) {
                settings.applyRemote(values, updatedAt)
            }
        }
    }

    private suspend fun restoreAllFromCloud(uid: String) {
        val root = userRoot(uid)
        val favs = fetch(root.collection("favorites"), 0).mapNotNull { favoriteFromDoc(it) }.filterNot { it.deleted }
        val playlists = fetch(root.collection("playlists"), 0).mapNotNull { playlistFromDoc(it) }.filterNot { it.first.deleted }
        val history = fetch(root.collection("history"), 0).mapNotNull { historyFromDoc(it) }.filterNot { it.deleted }
        db.withTransaction {
            favDao.upsertAll(favs.map { it.copy(dirty = false) })
            for ((p, its) in playlists) {
                plDao.upsert(p.copy(dirty = false))
                plDao.insertItems(its)
            }
            if (history.isNotEmpty()) histDao.upsertAll(history.map { it.copy(dirty = false) })
        }
        val sdoc = root.collection("settings").document("app").get(Source.SERVER).await()
        if (sdoc.exists()) {
            @Suppress("UNCHECKED_CAST")
            val values = sdoc.get("values") as? Map<String, Any?>
            if (values != null) settings.applyRemote(values, sdoc.getLong("updatedAt") ?: System.currentTimeMillis())
        }
        restoreLastPlayback(uid)
        settings.setSyncTimes(System.currentTimeMillis(), System.currentTimeMillis() - SKEW_MS)
    }

    private suspend fun mergeFromCloud(uid: String) {
        val root = userRoot(uid)
        val remoteFavs = fetch(root.collection("favorites"), 0).mapNotNull { favoriteFromDoc(it) }
        val remotePlaylists = fetch(root.collection("playlists"), 0).mapNotNull { playlistFromDoc(it) }
        val remoteHistory = if (settings.snapshot().historySync) {
            fetch(root.collection("history"), 0).mapNotNull { historyFromDoc(it) }
        } else emptyList()
        val retiredRemoteIds = ArrayList<String>()

        db.withTransaction {
            val localFavs = favDao.all()
            val mergedFavs = SyncMerge.unionFavorites(localFavs, remoteFavs)
            favDao.upsertAll(mergedFavs)

            val locals = plDao.all()
            for ((rp, rItems) in remotePlaylists) {
                if (rp.deleted) continue
                val match = SyncMerge.matchPlaylist(rp, locals)
                if (match == null) {
                    plDao.upsert(rp.copy(dirty = true))
                    plDao.insertItems(rItems)
                } else {
                    val merged = SyncMerge.unionItems(match.id, plDao.items(match.id), rItems)
                    plDao.clearItems(match.id)
                    plDao.insertItems(merged.map { if (it.playlistId != match.id) it.copy(playlistId = match.id) else it })
                    plDao.upsert(match.copy(deleted = false, dirty = true, updatedAt = System.currentTimeMillis()))
                    if (match.id != rp.id) retiredRemoteIds += rp.id
                }
            }

            val newHistory = SyncMerge.unionHistory(histDao.all(), remoteHistory)
            if (newHistory.isNotEmpty()) histDao.upsertAll(newHistory.map { it.copy(dirty = false) })
        }

        // Playlists com o mesmo nome foram combinadas: a cópia antiga da nuvem é retirada.
        if (retiredRemoteIds.isNotEmpty()) {
            val now = System.currentTimeMillis()
            retiredRemoteIds.chunked(400).forEach { chunk ->
                val batch = fs.batch()
                chunk.forEach { id ->
                    batch.set(root.collection("playlists").document(id), mapOf("deleted" to true, "updatedAt" to now, "items" to emptyList<Any>()), SetOptions.merge())
                }
                batch.commit().await()
            }
        }
    }

    /** "Usar dados deste aparelho": o que não existe localmente é marcado como removido na nuvem. */
    private suspend fun tombstoneRemoteNotInLocal(uid: String) {
        val root = userRoot(uid)
        val localFavKeys = favDao.all().filterNot { it.deleted }.mapTo(HashSet()) { it.songKey }
        val localPlIds = plDao.all().filterNot { it.deleted }.mapTo(HashSet()) { it.id }
        val now = System.currentTimeMillis()
        val ops = ArrayList<(WriteBatch) -> Unit>()
        root.collection("favorites").get(Source.SERVER).await().documents
            .filter { it.id !in localFavKeys }
            .forEach { d -> ops += { b -> b.set(d.reference, mapOf("deleted" to true, "updatedAt" to now), SetOptions.merge()) } }
        root.collection("playlists").get(Source.SERVER).await().documents
            .filter { it.id !in localPlIds }
            .forEach { d ->
                ops += { b -> b.set(d.reference, mapOf("deleted" to true, "updatedAt" to now, "items" to emptyList<Any>()), SetOptions.merge()) }
            }
        ops.chunked(400).forEach { chunk ->
            val batch = fs.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }

    private suspend fun restoreLastPlayback(uid: String) {
        val doc = runCatching { userRoot(uid).collection("appData").document("lastPlayback").get(Source.SERVER).await() }
            .getOrNull() ?: return
        if (!doc.exists()) return
        val songKey = doc.getString("songKey") ?: return
        val looseKey = doc.getString("looseKey") ?: ""
        val song = db.songDao().getAll().let { all ->
            all.firstOrNull { it.contentKey == songKey } ?: all.firstOrNull { it.looseKey == looseKey }
        } ?: return // arquivo não existe neste aparelho: não finge que foi recuperado
        db.withTransaction {
            db.queueDao().clear()
            db.queueDao().insertAll(
                listOf(
                    QueueItemEntity(
                        0, song.id, song.uri, song.title, song.artist, song.album, song.albumId, song.durationMs,
                        song.contentKey, song.looseKey,
                    ),
                ),
            )
        }
        settings.savePlaybackState(0, doc.getLong("positionMs") ?: 0L, markForSync = false)
    }

    // ------------------------------------------------------------------

    private suspend fun cloudHasData(uid: String): Boolean {
        val root = userRoot(uid)
        for (name in listOf("playlists", "favorites", "settings", "history")) {
            val snap = root.collection(name).limit(1).get(Source.SERVER).await()
            if (!snap.isEmpty) return true
        }
        return false
    }

    suspend fun localHasData(): Boolean =
        favDao.allActive().isNotEmpty() || plDao.allActive().isNotEmpty() || histDao.countActive() > 0

    private suspend fun markAllLocalDirty() {
        favDao.markAllDirty()
        plDao.markAllDirty()
        if (settings.snapshot().historySync) histDao.markAllDirty()
        settings.markSettingsDirty()
        settings.setLastPlaybackDirty(true)
    }

    /** Chamado ao ativar "Sincronizar histórico". */
    suspend fun onHistorySyncEnabled() {
        histDao.markAllDirty()
        requestSync(0)
    }

    private suspend fun wipeLocalSyncedData() {
        db.withTransaction {
            favDao.clear()
            plDao.clearAllItems()
            plDao.clear()
            histDao.clear()
        }
    }

    private suspend fun lastPlaybackMap(s: AppSettings): Map<String, Any?>? {
        val item = db.queueDao().at(s.queueIndex) ?: return null
        return mapOf(
            "songKey" to item.songKey,
            "looseKey" to item.looseKey,
            "title" to item.title,
            "artist" to item.artist,
            "album" to item.album,
            "durationMs" to item.durationMs,
            "positionMs" to s.queuePositionMs,
            "updatedAt" to System.currentTimeMillis(),
        )
    }

    private fun friendly(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Acesso negado pelo servidor. Verifique as regras do Firestore."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> "Sem conexão com o servidor."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND ->
            "Banco de dados não encontrado. Crie o Firestore no console do Firebase."
        else -> e.message ?: "Erro ao sincronizar."
    }

    // ------------------------------------------------------------------
    // Conversões

    private fun favoriteToMap(f: FavoriteEntity) = mapOf(
        "songKey" to f.songKey,
        "looseKey" to f.looseKey,
        "title" to f.title,
        "artist" to f.artist,
        "album" to f.album,
        "durationMs" to f.durationMs,
        "addedAt" to f.addedAt,
        "updatedAt" to f.updatedAt,
        "deleted" to f.deleted,
    )

    private fun favoriteFromDoc(d: DocumentSnapshot): FavoriteEntity? {
        val updatedAt = d.getLong("updatedAt") ?: return null
        return FavoriteEntity(
            songKey = d.getString("songKey") ?: d.id,
            looseKey = d.getString("looseKey") ?: "",
            title = d.getString("title") ?: "",
            artist = d.getString("artist") ?: "",
            album = d.getString("album") ?: "",
            durationMs = d.getLong("durationMs") ?: 0,
            addedAt = d.getLong("addedAt") ?: updatedAt,
            updatedAt = updatedAt,
            deleted = d.getBoolean("deleted") ?: false,
            dirty = false,
        )
    }

    private fun playlistToMap(p: PlaylistEntity, items: List<PlaylistItemEntity>) = mapOf(
        "name" to p.name,
        "createdAt" to p.createdAt,
        "updatedAt" to p.updatedAt,
        "deleted" to p.deleted,
        "items" to items.sortedBy { it.position }.map { i ->
            mapOf(
                "id" to i.id,
                "songKey" to i.songKey,
                "looseKey" to i.looseKey,
                "title" to i.title,
                "artist" to i.artist,
                "album" to i.album,
                "durationMs" to i.durationMs,
                "position" to i.position,
                "addedAt" to i.addedAt,
            )
        },
    )

    private fun playlistFromDoc(d: DocumentSnapshot): Pair<PlaylistEntity, List<PlaylistItemEntity>>? {
        val updatedAt = d.getLong("updatedAt") ?: return null
        val p = PlaylistEntity(
            id = d.id,
            name = d.getString("name") ?: "Playlist",
            createdAt = d.getLong("createdAt") ?: updatedAt,
            updatedAt = updatedAt,
            deleted = d.getBoolean("deleted") ?: false,
            dirty = false,
        )
        @Suppress("UNCHECKED_CAST")
        val raw = d.get("items") as? List<Map<String, Any?>> ?: emptyList()
        val items = raw.mapIndexed { index, m ->
            PlaylistItemEntity(
                id = (m["id"] as? String) ?: UUID.randomUUID().toString(),
                playlistId = d.id,
                songKey = (m["songKey"] as? String) ?: "",
                looseKey = (m["looseKey"] as? String) ?: "",
                title = (m["title"] as? String) ?: "",
                artist = (m["artist"] as? String) ?: "",
                album = (m["album"] as? String) ?: "",
                durationMs = (m["durationMs"] as? Number)?.toLong() ?: 0L,
                position = (m["position"] as? Number)?.toInt() ?: index,
                addedAt = (m["addedAt"] as? Number)?.toLong() ?: updatedAt,
            )
        }.filter { it.songKey.isNotBlank() }
        return p to items
    }

    private fun historyToMap(h: PlayHistoryEntity) = mapOf(
        "songKey" to h.songKey,
        "looseKey" to h.looseKey,
        "title" to h.title,
        "artist" to h.artist,
        "album" to h.album,
        "durationMs" to h.durationMs,
        "playedAt" to h.playedAt,
        "deleted" to h.deleted,
        "updatedAt" to System.currentTimeMillis(),
    )

    private fun historyFromDoc(d: DocumentSnapshot): PlayHistoryEntity? {
        val playedAt = d.getLong("playedAt") ?: return null
        return PlayHistoryEntity(
            id = d.id,
            songKey = d.getString("songKey") ?: return null,
            looseKey = d.getString("looseKey") ?: "",
            songId = -1,
            title = d.getString("title") ?: "",
            artist = d.getString("artist") ?: "",
            album = d.getString("album") ?: "",
            durationMs = d.getLong("durationMs") ?: 0,
            playedAt = playedAt,
            deleted = d.getBoolean("deleted") ?: false,
            dirty = false,
        )
    }

    companion object {
        private const val WORK_NAME = "musibox_sync"
        private const val PERIODIC_NAME = "musibox_sync_periodic"
        private const val SKEW_MS = 5 * 60_000L
        private val COLLECTIONS = listOf("favorites", "playlists", "history", "settings", "appData")
    }
}

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MusiBoxApp).container
        val done = container.sync.performSync()
        return when {
            done -> Result.success()
            runAttemptCount < 10 -> Result.retry()
            else -> Result.success()
        }
    }
}
