package com.musibox.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.musibox.app.core.Permissions
import com.musibox.app.core.findActivity

/** Estado de uma permissão, atualizado ao voltar para o app. */
class PermissionState(
    val granted: Boolean,
    val permanentlyDenied: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun rememberPermissionState(
    permissions: Array<String>,
    isGranted: () -> Boolean,
    onGranted: () -> Unit = {},
): PermissionState {
    val context = LocalContext.current
    var granted by rememberSaveable { mutableStateOf(isGranted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = isGranted()
        if (now && !granted) onGranted()
        granted = now
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        asked = true
        val ok = result.values.any { it } || isGranted()
        granted = ok
        if (ok) {
            onGranted()
        } else {
            val activity = context.findActivity()
            denied = activity != null && permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        }
    }
    return PermissionState(
        granted = granted,
        permanentlyDenied = asked && denied,
        request = { launcher.launch(permissions) },
        openSettings = { Permissions.openAppSettings(context) },
    )
}

@Composable
fun rememberAudioPermission(onGranted: () -> Unit = {}): PermissionState {
    val context = LocalContext.current
    return rememberPermissionState(arrayOf(Permissions.audio), { Permissions.hasAudio(context) }, onGranted)
}
