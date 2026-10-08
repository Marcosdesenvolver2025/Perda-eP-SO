package com.musibox.app.download

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.musibox.app.MainActivity
import com.musibox.app.R
import com.musibox.app.core.Permissions

object DownloadNotifications {
    private const val CHANNEL_PROGRESS = "downloads_progress"
    private const val CHANNEL_DONE = "downloads_done"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Downloads em andamento", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progresso dos downloads"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Downloads concluídos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso quando um download termina ou falha"
            },
        )
    }

    private fun openDownloadsIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun notificationId(downloadId: String) = 10_000 + (downloadId.hashCode() and 0x0FFFFFFF)

    fun progressInfo(context: Context, downloadId: String, title: String, percent: Int, text: String): ForegroundInfo {
        val n = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openDownloadsIntent(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return ForegroundInfo(notificationId(downloadId), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    @SuppressLint("MissingPermission")
    fun updateProgress(context: Context, downloadId: String, title: String, percent: Int, text: String) {
        if (!Permissions.hasNotifications(context)) return
        val info = progressInfo(context, downloadId, title, percent, text)
        runCatching { NotificationManagerCompat.from(context).notify(info.notificationId, info.notification) }
    }

    @SuppressLint("MissingPermission")
    fun finished(context: Context, downloadId: String, title: String, success: Boolean, message: String) {
        if (!Permissions.hasNotifications(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(if (success) "Download concluído" else "Falha no download")
            .setContentText(if (success) title else "$title — $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (success) "$title\n$message" else "$title\n$message"))
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent(context))
            .build()
        runCatching {
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(notificationId(downloadId))
            nm.notify(notificationId(downloadId) + 1, n)
        }
    }

    fun cancel(context: Context, downloadId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(downloadId)) }
    }
}
