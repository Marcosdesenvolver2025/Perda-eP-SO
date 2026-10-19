package com.musibox.app

import android.app.Application
import coil.Coil
import com.musibox.app.core.ConnectivityObserver
import com.musibox.app.data.db.AppDatabase
import com.musibox.app.data.prefs.SettingsRepository
import com.musibox.app.data.repo.HistoryRepository
import com.musibox.app.data.repo.LibraryRepository
import com.musibox.app.data.repo.MusicRepository
import com.musibox.app.media.PlayerController
import com.musibox.app.storage.StorageRepository
import com.musibox.app.sync.AuthManager
import com.musibox.app.sync.SyncManager
import com.musibox.app.vault.PinManager
import com.musibox.app.vault.VaultBackup
import com.musibox.app.vault.VaultCrypto
import com.musibox.app.vault.VaultLockManager
import com.musibox.app.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Injeção de dependências manual: um único lugar cria e liga todos os componentes. */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase = AppDatabase.build(app)
    val settings = SettingsRepository(app)
    val connectivity = ConnectivityObserver(app)
    val auth = AuthManager(app)
    val sync = SyncManager(app, db, settings, auth, connectivity, appScope)

    val music = MusicRepository(app, db, appScope)
    val library = LibraryRepository(db, music) { sync.requestSync() }
    val history = HistoryRepository(db, settings, music) { sync.requestSync() }

    val crypto = VaultCrypto(app)
    val vault = VaultRepository(app, db, crypto)
    val pin = PinManager(app)
    val vaultLock = VaultLockManager(settings, appScope) {
        vault.clearTemp()
        runCatching { Coil.imageLoader(app).memoryCache?.clear() }
    }
    val vaultBackup = VaultBackup(app, vault)

    val player = PlayerController(app)
    val storage = StorageRepository(app, db, vault)

    fun start() {
        settings.onSyncedChange = { sync.requestSync() }
        vaultLock.start()
        vault.clearTemp()
        // Cancela tarefas do antigo baixador (versões anteriores do app)
        runCatching {
            val wm = androidx.work.WorkManager.getInstance(app)
            wm.cancelAllWorkByTag("musibox_download")
            wm.cancelUniqueWork("ytdlp_update")
            app.getSystemService(android.app.NotificationManager::class.java)?.apply {
                deleteNotificationChannel("downloads_progress")
                deleteNotificationChannel("downloads_done")
            }
        }
        appScope.launch {
            if (music.hasPermission()) {
                music.startObserving()
                music.rescan()
            }
        }
        appScope.launch {
            if (auth.isConfigured) {
                sync.schedulePeriodic()
                runCatching { sync.checkPendingSetup() }
            }
        }
    }
}
