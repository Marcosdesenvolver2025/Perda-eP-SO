package com.musibox.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.BuildConfig
import com.musibox.app.core.Fmt
import com.musibox.app.core.Permissions
import com.musibox.app.data.prefs.AppSettings
import com.musibox.app.data.prefs.AudioFormat
import com.musibox.app.data.prefs.DownloadFolder
import com.musibox.app.data.prefs.SaveDestination
import com.musibox.app.data.prefs.ThemeMode
import com.musibox.app.sync.SyncStatus
import com.musibox.app.ui.components.ChoiceDialog
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.SettingDivider
import com.musibox.app.ui.components.SettingItem
import com.musibox.app.ui.components.SettingSwitch
import com.musibox.app.ui.components.SettingsSection
import com.musibox.app.ui.components.rememberAudioPermission
import com.musibox.app.ui.components.rememberPermissionState
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import com.musibox.app.ui.theme.Mb
import kotlinx.coroutines.launch

private enum class SettingDialog { THEME, DESTINATION, FOLDER, VIDEO_QUALITY, AUDIO_FORMAT, BITRATE }

private val videoQualities = listOf(360, 480, 720, 1080, 1440, 2160)
private val bitrates = listOf(128, 192, 256, 320)

@Composable
fun SettingsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val user by container.auth.user.collectAsStateWithLifecycle()
    val sync by container.sync.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var dialog by remember { mutableStateOf<SettingDialog?>(null) }
    val audioPermission = rememberAudioPermission(onGranted = { container.music.launchRescan() })
    val notifPermission = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
        { Permissions.hasNotifications(context) },
    )

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Configurações", onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SettingsSection("Conta e sincronização") {
                    SettingItem(
                        Icons.Rounded.AccountCircle,
                        user?.name ?: user?.email ?: "Entrar com Google",
                        if (user == null) "Sincronize playlists, favoritos e configurações" else "${sync.status.label}${if (sync.lastSyncAt > 0) " • ${Fmt.dateTime(sync.lastSyncAt)}" else ""}",
                        onClick = { nav.navigate(Routes.ACCOUNT) },
                        tint = if (sync.status == SyncStatus.ERROR) Mb.colors.danger else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                SettingsSection("Aparência") {
                    SettingItem(Icons.Rounded.Palette, "Papéis de parede e temas", "Fundo do app e do navegador", onClick = { nav.navigate(Routes.WALLPAPERS) })
                    SettingDivider()
                    SettingItem(Icons.Rounded.DarkMode, "Modo", settings.themeMode.label, onClick = { dialog = SettingDialog.THEME })
                }
            }
            item {
                SettingsSection("Player") {
                    SettingSwitch(Icons.Rounded.PlayCircle, "Reprodução automática", "Continua para a próxima música da fila",
                        settings.autoPlayNext, { v -> scope.launch { container.settings.setAutoPlayNext(v) } })
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.Restore, "Lembrar posição", "Retoma a última música, a fila e a posição",
                        settings.rememberPosition, { v -> scope.launch { container.settings.setRememberPosition(v) } })
                    SettingDivider()
                    SettingSwitch(Icons.AutoMirrored.Rounded.VolumeOff, "Pular silêncio", "Remove trechos silenciosos durante a reprodução",
                        settings.skipSilence, { v -> scope.launch { container.settings.setSkipSilence(v) } })
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.Headset, "Pausar ao desconectar fones", "Evita tocar alto pelo alto-falante",
                        settings.pauseOnDisconnect, { v -> scope.launch { container.settings.setPauseOnDisconnect(v) } })
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.CloseFullscreen, "Parar ao fechar o app", "Para a música quando o app é removido dos recentes",
                        settings.stopOnTaskRemoved, { v -> scope.launch { container.settings.setStopOnTaskRemoved(v) } })
                    SettingDivider()
                    SettingItem(Icons.Rounded.GraphicEq, "Qualidade de áudio",
                        "Os arquivos do aparelho tocam na qualidade original.")
                }
            }
            item {
                SettingsSection("Histórico") {
                    SettingSwitch(Icons.Rounded.History, "Salvar histórico de reprodução", null,
                        settings.historyEnabled, { v -> scope.launch { container.settings.setHistoryEnabled(v) } })
                    SettingDivider()
                    SettingSwitch(Icons.Rounded.Sync, "Sincronizar histórico com a conta", "Leva o histórico para outros aparelhos",
                        settings.historySync, { v ->
                            scope.launch {
                                container.settings.setHistorySync(v)
                                if (v) container.sync.onHistorySyncEnabled()
                            }
                        }, enabled = settings.historyEnabled)
                }
            }
            item {
                SettingsSection("Cofre") {
                    SettingItem(Icons.Rounded.Lock, "Configurações do cofre", "Alterar PIN, biometria, bloqueio automático e privacidade",
                        onClick = { nav.navigate(Routes.VAULT_SETTINGS) }, tint = Mb.colors.vault)
                    SettingDivider()
                    SettingItem(Icons.Rounded.Folder, "Pasta ao tirar do cofre", settings.downloadFolder.label, onClick = { dialog = SettingDialog.FOLDER })
                }
            }
            item {
                SettingsSection("Armazenamento") {
                    SettingItem(Icons.Rounded.Storage, "Espaço usado e cache", "Músicas, vídeos, imagens, cofre e cache",
                        onClick = { nav.navigate(Routes.STORAGE) })
                }
            }
            item {
                SettingsSection("Permissões") {
                    SettingItem(
                        Icons.Rounded.MusicNote,
                        "Músicas e áudio",
                        if (audioPermission.granted) "Permitido" else "Negado — a biblioteca de músicas fica indisponível",
                        onClick = { if (audioPermission.permanentlyDenied || audioPermission.granted) audioPermission.openSettings() else audioPermission.request() },
                    )
                    SettingDivider()
                    SettingItem(
                        Icons.Rounded.Notifications,
                        "Notificações",
                        if (notifPermission.granted) "Permitido" else "Negado — os controles da música não aparecem na barra",
                        onClick = { if (notifPermission.granted || notifPermission.permanentlyDenied) notifPermission.openSettings() else notifPermission.request() },
                    )
                }
            }
            item {
                SettingsSection("Sobre") {
                    SettingItem(Icons.Rounded.Info, "MusiBox ${BuildConfig.VERSION_NAME}", "Player de músicas, navegador e cofre privado.")
                }
            }
        }
    }

    when (dialog) {
        SettingDialog.THEME -> ChoiceDialog("Tema", ThemeMode.entries, settings.themeMode, { it.label }, { v ->
            dialog = null; scope.launch { container.settings.setTheme(v) }
        }, { dialog = null })
        SettingDialog.DESTINATION -> ChoiceDialog("Destino padrão", SaveDestination.entries, settings.defaultDestination, { it.label }, { v ->
            dialog = null; scope.launch { container.settings.setDefaultDestination(v) }
        }, { dialog = null }, description = "Usado quando \"Sempre perguntar\" está desligado (e já vem marcado ao baixar).")
        SettingDialog.FOLDER -> ChoiceDialog("Pasta padrão", DownloadFolder.entries, settings.downloadFolder, { it.label }, { v ->
            dialog = null; scope.launch { container.settings.setDownloadFolder(v) }
        }, { dialog = null }, description = "Onde ficam os arquivos que você tira do cofre.")
        SettingDialog.VIDEO_QUALITY -> ChoiceDialog("Qualidade de vídeo", videoQualities, settings.videoQuality, { "${it}p" }, { v ->
            dialog = null; scope.launch { container.settings.setVideoQuality(v) }
        }, { dialog = null })
        SettingDialog.AUDIO_FORMAT -> ChoiceDialog("Formato de áudio", AudioFormat.entries, settings.audioFormat, { it.label }, { v ->
            dialog = null; scope.launch { container.settings.setAudioFormat(v) }
        }, { dialog = null })
        SettingDialog.BITRATE -> ChoiceDialog("Qualidade do MP3", bitrates, settings.audioBitrate, { "$it kbps" }, { v ->
            dialog = null; scope.launch { container.settings.setAudioBitrate(v) }
        }, { dialog = null })
        null -> Unit
    }
}
