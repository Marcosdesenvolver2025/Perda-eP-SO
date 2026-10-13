package com.musibox.app.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musibox.app.MusiBoxApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Botões "Pausar" e "Cancelar" da notificação de download. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val container = (context.applicationContext as MusiBoxApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> container.downloads.pause(id)
                    ACTION_CANCEL -> container.downloads.cancel(id)
                }
            } finally {
                DownloadNotifications.cancel(context, id)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.musibox.app.download.PAUSE"
        const val ACTION_CANCEL = "com.musibox.app.download.CANCEL"
        const val EXTRA_ID = "download_id"
    }
}
