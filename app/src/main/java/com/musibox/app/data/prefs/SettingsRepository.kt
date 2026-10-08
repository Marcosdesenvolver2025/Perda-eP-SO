package com.musibox.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { DARK("Escuro"), SYSTEM("Seguir o sistema") }
enum class SaveDestination(val label: String) { GALLERY("Galeria do dispositivo"), VAULT("Cofre") }
enum class DownloadFolder(val label: String) {
    MEDIA("Música/MusiBox e Filmes/MusiBox"),
    DOWNLOADS("Download/MusiBox"),
}
enum class AudioFormat(val label: String) { MP3("MP3"), M4A("M4A (mais rápido)") }
enum class LibrarySort(val label: String) { TITLE("Título"), ARTIST("Artista"), RECENT("Adicionadas recentemente") }

enum class AutoLock(val millis: Long, val label: String) {
    IMMEDIATE(0, "Imediatamente"),
    S30(30_000, "Após 30 segundos"),
    M1(60_000, "Após 1 minuto"),
    M5(300_000, "Após 5 minutos"),
    ON_CLOSE(Long.MAX_VALUE, "Somente quando fechar o aplicativo"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoPlayNext: Boolean = true,
    val rememberPosition: Boolean = true,
    val skipSilence: Boolean = false,
    val pauseOnDisconnect: Boolean = true,
    val stopOnTaskRemoved: Boolean = false,
    val askDestination: Boolean = true,
    val defaultDestination: SaveDestination = SaveDestination.GALLERY,
    val downloadFolder: DownloadFolder = DownloadFolder.MEDIA,
    val wifiOnly: Boolean = false,
    val videoQuality: Int = 720,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val audioBitrate: Int = 192,
    val historyEnabled: Boolean = true,
    val historySync: Boolean = false,
    val librarySort: LibrarySort = LibrarySort.TITLE,
    val vaultAutoLock: AutoLock = AutoLock.IMMEDIATE,
    val vaultBiometric: Boolean = false,
    val vaultSecureScreen: Boolean = true,
    val vaultHideThumbnails: Boolean = false,
    val onboardingDone: Boolean = false,
    val ownerUid: String? = null,
    val restoreDeclined: Boolean = false,
    val settingsUpdatedAt: Long = 0,
    val settingsDirty: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastPullAt: Long = 0,
    val lastPlaybackDirty: Boolean = false,
    val queueIndex: Int = 0,
    val queuePositionMs: Long = 0,
    val ytdlpUpdatedAt: Long = 0,
)

object SettingKeys {
    val theme = stringPreferencesKey("theme")
    val autoPlayNext = booleanPreferencesKey("auto_play_next")
    val rememberPosition = booleanPreferencesKey("remember_position")
    val skipSilence = booleanPreferencesKey("skip_silence")
    val pauseOnDisconnect = booleanPreferencesKey("pause_on_disconnect")
    val stopOnTaskRemoved = booleanPreferencesKey("stop_on_task_removed")
    val askDestination = booleanPreferencesKey("ask_destination")
    val defaultDestination = stringPreferencesKey("default_destination")
    val downloadFolder = stringPreferencesKey("download_folder")
    val wifiOnly = booleanPreferencesKey("wifi_only")
    val videoQuality = intPreferencesKey("video_quality")
    val audioFormat = stringPreferencesKey("audio_format")
    val audioBitrate = intPreferencesKey("audio_bitrate")
    val historyEnabled = booleanPreferencesKey("history_enabled")
    val historySync = booleanPreferencesKey("history_sync")
    val librarySort = stringPreferencesKey("library_sort")
    val vaultAutoLock = stringPreferencesKey("vault_auto_lock")
    val vaultSecureScreen = booleanPreferencesKey("vault_secure_screen")
    val vaultHideThumbnails = booleanPreferencesKey("vault_hide_thumbnails")

    // Somente deste aparelho (não sincronizam)
    val vaultBiometric = booleanPreferencesKey("vault_biometric")
    val onboardingDone = booleanPreferencesKey("onboarding_done")
    val ownerUid = stringPreferencesKey("owner_uid")
    val restoreDeclined = booleanPreferencesKey("restore_declined")
    val settingsUpdatedAt = longPreferencesKey("settings_updated_at")
    val settingsDirty = booleanPreferencesKey("settings_dirty")
    val lastSyncAt = longPreferencesKey("last_sync_at")
    val lastPullAt = longPreferencesKey("last_pull_at")
    val lastPlaybackDirty = booleanPreferencesKey("last_playback_dirty")
    val queueIndex = intPreferencesKey("queue_index")
    val queuePositionMs = longPreferencesKey("queue_position_ms")
    val ytdlpUpdatedAt = longPreferencesKey("ytdlp_updated_at")

    /** Preferências que acompanham a Conta Google. */
    val synced: List<Preferences.Key<*>> = listOf(
        theme, autoPlayNext, rememberPosition, skipSilence, pauseOnDisconnect, stopOnTaskRemoved,
        askDestination, defaultDestination, downloadFolder, wifiOnly, videoQuality, audioFormat,
        audioBitrate, historyEnabled, historySync, librarySort, vaultAutoLock, vaultSecureScreen,
        vaultHideThumbnails,
    )
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    /** Chamado quando uma preferência sincronizável muda (agenda a sincronização). */
    @Volatile
    var onSyncedChange: (() -> Unit)? = null

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun snapshot(): AppSettings = settings.first()

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val k = SettingKeys
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[k.theme], d.themeMode),
            autoPlayNext = this[k.autoPlayNext] ?: d.autoPlayNext,
            rememberPosition = this[k.rememberPosition] ?: d.rememberPosition,
            skipSilence = this[k.skipSilence] ?: d.skipSilence,
            pauseOnDisconnect = this[k.pauseOnDisconnect] ?: d.pauseOnDisconnect,
            stopOnTaskRemoved = this[k.stopOnTaskRemoved] ?: d.stopOnTaskRemoved,
            askDestination = this[k.askDestination] ?: d.askDestination,
            defaultDestination = enumOr(this[k.defaultDestination], d.defaultDestination),
            downloadFolder = enumOr(this[k.downloadFolder], d.downloadFolder),
            wifiOnly = this[k.wifiOnly] ?: d.wifiOnly,
            videoQuality = this[k.videoQuality] ?: d.videoQuality,
            audioFormat = enumOr(this[k.audioFormat], d.audioFormat),
            audioBitrate = this[k.audioBitrate] ?: d.audioBitrate,
            historyEnabled = this[k.historyEnabled] ?: d.historyEnabled,
            historySync = this[k.historySync] ?: d.historySync,
            librarySort = enumOr(this[k.librarySort], d.librarySort),
            vaultAutoLock = enumOr(this[k.vaultAutoLock], d.vaultAutoLock),
            vaultBiometric = this[k.vaultBiometric] ?: d.vaultBiometric,
            vaultSecureScreen = this[k.vaultSecureScreen] ?: d.vaultSecureScreen,
            vaultHideThumbnails = this[k.vaultHideThumbnails] ?: d.vaultHideThumbnails,
            onboardingDone = this[k.onboardingDone] ?: d.onboardingDone,
            ownerUid = this[k.ownerUid]?.takeIf { it.isNotBlank() },
            restoreDeclined = this[k.restoreDeclined] ?: d.restoreDeclined,
            settingsUpdatedAt = this[k.settingsUpdatedAt] ?: d.settingsUpdatedAt,
            settingsDirty = this[k.settingsDirty] ?: d.settingsDirty,
            lastSyncAt = this[k.lastSyncAt] ?: d.lastSyncAt,
            lastPullAt = this[k.lastPullAt] ?: d.lastPullAt,
            lastPlaybackDirty = this[k.lastPlaybackDirty] ?: d.lastPlaybackDirty,
            queueIndex = this[k.queueIndex] ?: d.queueIndex,
            queuePositionMs = this[k.queuePositionMs] ?: d.queuePositionMs,
            ytdlpUpdatedAt = this[k.ytdlpUpdatedAt] ?: d.ytdlpUpdatedAt,
        )
    }

    /** Altera uma preferência que acompanha a conta e marca para sincronizar. */
    private suspend fun <T> setSynced(key: Preferences.Key<T>, value: T) {
        store.edit {
            it[key] = value
            it[SettingKeys.settingsDirty] = true
            it[SettingKeys.settingsUpdatedAt] = System.currentTimeMillis()
        }
        onSyncedChange?.invoke()
    }

    private suspend fun <T> setLocal(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    // Aparência / player
    suspend fun setTheme(mode: ThemeMode) = setSynced(SettingKeys.theme, mode.name)
    suspend fun setAutoPlayNext(v: Boolean) = setSynced(SettingKeys.autoPlayNext, v)
    suspend fun setRememberPosition(v: Boolean) = setSynced(SettingKeys.rememberPosition, v)
    suspend fun setSkipSilence(v: Boolean) = setSynced(SettingKeys.skipSilence, v)
    suspend fun setPauseOnDisconnect(v: Boolean) = setSynced(SettingKeys.pauseOnDisconnect, v)
    suspend fun setStopOnTaskRemoved(v: Boolean) = setSynced(SettingKeys.stopOnTaskRemoved, v)
    suspend fun setLibrarySort(v: LibrarySort) = setSynced(SettingKeys.librarySort, v.name)

    // Downloads
    suspend fun setAskDestination(v: Boolean) = setSynced(SettingKeys.askDestination, v)
    suspend fun setDefaultDestination(v: SaveDestination) = setSynced(SettingKeys.defaultDestination, v.name)
    suspend fun setDownloadFolder(v: DownloadFolder) = setSynced(SettingKeys.downloadFolder, v.name)
    suspend fun setWifiOnly(v: Boolean) = setSynced(SettingKeys.wifiOnly, v)
    suspend fun setVideoQuality(v: Int) = setSynced(SettingKeys.videoQuality, v)
    suspend fun setAudioFormat(v: AudioFormat) = setSynced(SettingKeys.audioFormat, v.name)
    suspend fun setAudioBitrate(v: Int) = setSynced(SettingKeys.audioBitrate, v)

    // Histórico
    suspend fun setHistoryEnabled(v: Boolean) = setSynced(SettingKeys.historyEnabled, v)
    suspend fun setHistorySync(v: Boolean) = setSynced(SettingKeys.historySync, v)

    // Cofre
    suspend fun setVaultAutoLock(v: AutoLock) = setSynced(SettingKeys.vaultAutoLock, v.name)
    suspend fun setVaultSecureScreen(v: Boolean) = setSynced(SettingKeys.vaultSecureScreen, v)
    suspend fun setVaultHideThumbnails(v: Boolean) = setSynced(SettingKeys.vaultHideThumbnails, v)
    suspend fun setVaultBiometric(v: Boolean) = setLocal(SettingKeys.vaultBiometric, v)

    // Estado local
    suspend fun setOnboardingDone(v: Boolean) = setLocal(SettingKeys.onboardingDone, v)
    suspend fun setYtdlpUpdatedAt(v: Long) = setLocal(SettingKeys.ytdlpUpdatedAt, v)

    suspend fun setOwner(uid: String?, restoreDeclined: Boolean) {
        store.edit {
            if (uid == null) it.remove(SettingKeys.ownerUid) else it[SettingKeys.ownerUid] = uid
            it[SettingKeys.restoreDeclined] = restoreDeclined
        }
    }

    suspend fun setRestoreDeclined(v: Boolean) = setLocal(SettingKeys.restoreDeclined, v)

    suspend fun setSyncTimes(lastSyncAt: Long?, lastPullAt: Long?) {
        store.edit {
            if (lastSyncAt != null) it[SettingKeys.lastSyncAt] = lastSyncAt
            if (lastPullAt != null) it[SettingKeys.lastPullAt] = lastPullAt
        }
    }

    suspend fun markSettingsClean(updatedAt: Long) {
        store.edit {
            if ((it[SettingKeys.settingsUpdatedAt] ?: 0L) == updatedAt) it[SettingKeys.settingsDirty] = false
        }
    }

    suspend fun markSettingsDirty() {
        store.edit {
            it[SettingKeys.settingsDirty] = true
            if ((it[SettingKeys.settingsUpdatedAt] ?: 0L) == 0L) {
                it[SettingKeys.settingsUpdatedAt] = System.currentTimeMillis()
            }
        }
    }

    suspend fun savePlaybackState(index: Int, positionMs: Long, markForSync: Boolean) {
        store.edit {
            it[SettingKeys.queueIndex] = index
            it[SettingKeys.queuePositionMs] = positionMs
            if (markForSync) it[SettingKeys.lastPlaybackDirty] = true
        }
    }

    suspend fun setLastPlaybackDirty(v: Boolean) = setLocal(SettingKeys.lastPlaybackDirty, v)

    /** Mapa com as preferências sincronizáveis, para enviar à nuvem. */
    suspend fun syncedValues(): Map<String, Any> {
        val prefs = store.data.first()
        val out = HashMap<String, Any>()
        for (key in SettingKeys.synced) {
            val v = prefs[key] ?: continue
            out[key.name] = v
        }
        return out
    }

    /** Aplica preferências vindas da nuvem sem marcar como pendentes. */
    suspend fun applyRemote(values: Map<String, Any?>, updatedAt: Long) {
        store.edit { prefs ->
            for (key in SettingKeys.synced) {
                val v = values[key.name] ?: continue
                @Suppress("UNCHECKED_CAST")
                when (key.name) {
                    SettingKeys.videoQuality.name, SettingKeys.audioBitrate.name ->
                        (v as? Number)?.let { prefs[key as Preferences.Key<Int>] = it.toInt() }
                    else -> when (v) {
                        is Boolean -> prefs[key as Preferences.Key<Boolean>] = v
                        is String -> prefs[key as Preferences.Key<String>] = v
                        else -> Unit
                    }
                }
            }
            prefs[SettingKeys.settingsUpdatedAt] = updatedAt
            prefs[SettingKeys.settingsDirty] = false
        }
    }
}
