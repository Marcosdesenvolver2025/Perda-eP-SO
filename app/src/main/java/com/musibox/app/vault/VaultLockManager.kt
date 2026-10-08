package com.musibox.app.vault

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.musibox.app.data.prefs.AutoLock
import com.musibox.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controla quando o cofre está aberto e aplica o bloqueio automático:
 * imediatamente, após 30 s / 1 min / 5 min fora do cofre, ou somente ao fechar o app.
 */
class VaultLockManager(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val onLocked: () -> Unit,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile private var autoLock: AutoLock = AutoLock.IMMEDIATE
    private var leftVaultAt: Long? = null
    private var backgroundAt: Long? = null
    private var visibleScreens = 0
    private var expectingExternal = false

    fun start() {
        scope.launch { settings.settings.collect { autoLock = it.vaultAutoLock } }
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = onAppBackground()
                override fun onStart(owner: LifecycleOwner) = onAppForeground()
            })
        }
    }

    fun unlock() {
        leftVaultAt = null
        backgroundAt = null
        _unlocked.value = true
    }

    fun lock() {
        if (_unlocked.value) {
            _unlocked.value = false
            onLocked()
        }
    }

    /** Antes de abrir o seletor de arquivos/compartilhar, para não bloquear no meio da ação. */
    fun expectExternalActivity() {
        expectingExternal = true
    }

    fun onVaultScreenVisible() {
        visibleScreens++
        val left = leftVaultAt
        if (left != null && System.currentTimeMillis() - left >= autoLock.millis) lock()
        leftVaultAt = null
    }

    fun onVaultScreenHidden() {
        visibleScreens = (visibleScreens - 1).coerceAtLeast(0)
        if (visibleScreens == 0 && _unlocked.value) {
            // Ao sair das telas do cofre dentro do app.
            scope.launch(Dispatchers.Main) {
                // Pequena espera para trocas entre telas do próprio cofre não contarem como saída.
                kotlinx.coroutines.delay(400)
                if (visibleScreens == 0) {
                    if (autoLock == AutoLock.IMMEDIATE) lock()
                    else if (autoLock != AutoLock.ON_CLOSE) leftVaultAt = System.currentTimeMillis()
                }
            }
        }
    }

    private fun onAppBackground() {
        if (expectingExternal) return
        when (autoLock) {
            AutoLock.IMMEDIATE -> lock()
            AutoLock.ON_CLOSE -> Unit
            else -> backgroundAt = System.currentTimeMillis()
        }
    }

    private fun onAppForeground() {
        if (expectingExternal) {
            expectingExternal = false
            return
        }
        val bg = backgroundAt
        backgroundAt = null
        if (bg != null && autoLock != AutoLock.ON_CLOSE && System.currentTimeMillis() - bg >= autoLock.millis) lock()
    }

    /** Chamado quando a Activity principal é finalizada (app fechado). */
    fun onAppClosed() {
        lock()
    }

    suspend fun awaitMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
}
