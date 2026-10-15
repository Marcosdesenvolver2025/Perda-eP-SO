package com.musibox.app.download

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.ForegroundInfo
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musibox.app.MainActivity
import com.musibox.app.R
import com.musibox.app.core.Permissions
import java.util.concurrent.ConcurrentHashMap

/**
 * Notificações de download com a cara do MusiBox: cor azul do app, capa do vídeo/música,
 * progresso em tempo real, botões Pausar/Cancelar e "Tentar de novo" quando falha.
 * Downloads que vão para o cofre não mostram capa nem título na tela de bloqueio.
 */
object DownloadNotifications {
    private const val CHANNEL_PROGRESS = "downloads_progress"
    private const val CHANNEL_DONE = "downloads_done"
    private const val GROUP = "musibox_downloads"
    private const val BRAND = 0xFF1FDF67.toInt()

    private val thumbs = ConcurrentHashMap<String, Bitmap>()
    private val privateIds = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var logo: Bitmap? = null

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "MusiBox • Baixando", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progresso dos downloads em tempo real"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "MusiBox • Downloads concluídos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso quando um download termina ou falha"
            },
        )
    }

    /** Carrega a capa do download (uma vez) para mostrar na notificação. */
    suspend fun prepare(context: Context, downloadId: String, thumbnailUrl: String?, isPrivate: Boolean) {
        if (isPrivate) {
            privateIds += downloadId
            return
        }
        privateIds -= downloadId
        if (thumbnailUrl.isNullOrBlank() || thumbs.containsKey(downloadId)) return
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .allowHardware(false)
                .size(512)
                .build()
            val result = context.imageLoader.execute(request)
            val bmp = (result as? SuccessResult)?.drawable?.toBitmap()
            if (bmp != null) thumbs[downloadId] = bmp
        }
    }

    private fun appLogo(context: Context): Bitmap? =
        logo ?: runCatching {
            // Ícone do app (barra + play verde no fundo preto) como imagem da notificação
            val size = 192
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF181818.toInt() }
            c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.26f, size * 0.26f, paint)
            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.let { d ->
                val pad = -(size * 0.18f).toInt() // o desenho tem margem de ícone adaptável
                d.setBounds(pad, pad, size - pad, size - pad)
                d.draw(c)
            }
            out
        }.getOrNull()?.also { logo = it }

    /** Miniatura com cantos arredondados para o ícone grande. */
    private fun rounded(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF(0f, 0f, size.toFloat(), size.toFloat())
        c.drawRoundRect(r, size * 0.22f, size * 0.22f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (src.width - size) / 2f
        val top = (src.height - size) / 2f
        c.drawBitmap(src, -left, -top, paint)
        return out
    }

    private fun largeIcon(context: Context, downloadId: String): Bitmap? =
        thumbs[downloadId]?.let { rounded(it) } ?: appLogo(context)

    private fun openDownloadsIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun notificationId(downloadId: String) = 10_000 + (downloadId.hashCode() and 0x0FFFFFFF)

    private fun actionIntent(context: Context, action: String, downloadId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(DownloadActionReceiver.EXTRA_ID, downloadId)
        return PendingIntent.getBroadcast(
            context,
            (action + downloadId).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Versão que aparece na tela de bloqueio para downloads do cofre (sem título). */
    private fun publicVersion(context: Context, title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(BRAND)
            .build()

    /**
     * Notificação "Baixando…" com a porcentagem em tempo real, velocidade e tempo restante,
     * e os botões Pausar e Cancelar. Fica toda na cor do MusiBox.
     */
    fun progressInfo(context: Context, downloadId: String, title: String, percent: Int, text: String): ForegroundInfo {
        val p = percent.coerceIn(0, 100)
        val preparing = p <= 0 && (text.startsWith("Preparando") || text.isBlank())
        val finishing = text.startsWith("Finalizando") || text.startsWith("Salvando")
        val heading = when {
            finishing -> "Finalizando download…"
            preparing -> "Preparando download…"
            else -> "Baixando… $p%"
        }
        val isPrivate = downloadId in privateIds
        val n = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(largeIcon(context, downloadId))
            .setContentTitle(heading)
            .setContentText(title)
            .setSubText(if (preparing || finishing) "MusiBox" else text.substringAfter("% • ", "").ifBlank { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$text"))
            .setProgress(100, p, preparing || finishing)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setColor(BRAND)
            .setColorized(true)
            .setGroup(GROUP)
            .setContentIntent(openDownloadsIntent(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(if (isPrivate) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (isPrivate) setPublicVersion(publicVersion(context, heading, "MusiBox está salvando um arquivo no cofre")) }
            .addAction(R.drawable.ic_notif_pause, "Pausar", actionIntent(context, DownloadActionReceiver.ACTION_PAUSE, downloadId))
            .addAction(R.drawable.ic_notif_close, "Cancelar", actionIntent(context, DownloadActionReceiver.ACTION_CANCEL, downloadId))
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
        val isPrivate = downloadId in privateIds
        val thumb = thumbs.remove(downloadId)
        privateIds -= downloadId
        if (!Permissions.hasNotifications(context)) {
            cancel(context, downloadId)
            return
        }
        val heading = if (success) "Download concluído ✓" else "Falha no download"
        val builder = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(thumb?.let { rounded(it) } ?: appLogo(context))
            .setContentTitle(heading)
            .setContentText(if (success) "$title • $message" else "$title — $message")
            .setSubText(if (success) "Pronto para tocar" else "Toque para ver detalhes")
            .setColor(BRAND)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setContentIntent(openDownloadsIntent(context))
            .setVisibility(if (isPrivate) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
        if (isPrivate) builder.setPublicVersion(publicVersion(context, heading, "MusiBox"))
        if (success && thumb != null) {
            // Capa grande do vídeo/música quando a notificação é expandida
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(thumb)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText("$title • $message"),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$message"))
        }
        if (success) {
            builder.addAction(R.drawable.ic_notif_open, "Abrir", openDownloadsIntent(context))
        } else {
            builder.addAction(R.drawable.ic_notif_retry, "Tentar de novo", actionIntent(context, DownloadActionReceiver.ACTION_RETRY, downloadId))
        }
        runCatching {
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(notificationId(downloadId))
            nm.notify(notificationId(downloadId) + 1, builder.build())
        }
    }

    fun cancel(context: Context, downloadId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(downloadId)) }
    }

    fun cancelFinished(context: Context, downloadId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(downloadId) + 1) }
    }
}
