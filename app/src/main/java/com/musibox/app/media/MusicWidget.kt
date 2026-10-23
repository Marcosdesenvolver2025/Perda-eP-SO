package com.musibox.app.media

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musibox.app.MainActivity
import com.musibox.app.R
import com.musibox.app.billing.Plan
import com.musibox.app.billing.PlayBilling
import com.musibox.app.themes.AppTheme
import java.io.File

/**
 * Widget da tela inicial (Plus e Pro): capa, música, cantor e botões anterior / tocar / próxima.
 * Sem Plus ou Pro, mostra o convite para os planos.
 */
class MusicWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids, last(context), loadArt(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV, ACTION_PLAY, ACTION_NEXT -> {
                if (!PlayBilling.hasPlan(Plan.PLUS)) {
                    open(context, pro = true)
                    return
                }
                val pending = goAsync()
                runCommand(context.applicationContext, intent.action!!) { pending.finish() }
            }
            else -> super.onReceive(context, intent)
        }
    }

    /** Liga ao serviço de música (MediaController) e executa o botão. */
    private fun runCommand(context: Context, action: String, done: () -> Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                done()
                return@addListener
            }
            fun act() {
                when (action) {
                    ACTION_PREV -> c.seekToPrevious()
                    ACTION_NEXT -> c.seekToNext()
                    ACTION_PLAY -> if (c.isPlaying) c.pause() else {
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
                        c.play()
                    }
                }
                android.os.Handler(context.mainLooper).postDelayed({
                    MediaController.releaseFuture(future)
                    done()
                }, 1500)
            }
            if (c.mediaItemCount > 0) act() else {
                // O serviço acabou de abrir: espera a fila salva voltar
                var tries = 0
                val h = android.os.Handler(context.mainLooper)
                val r = object : Runnable {
                    override fun run() {
                        tries++
                        if (c.mediaItemCount > 0 || tries > 15) act() else h.postDelayed(this, 120)
                    }
                }
                h.postDelayed(r, 120)
            }
        }, executor)
    }

    data class Info(val title: String, val artist: String, val playing: Boolean, val hasSong: Boolean)

    companion object {
        const val ACTION_PREV = "com.musibox.widget.PREV"
        const val ACTION_PLAY = "com.musibox.widget.PLAY"
        const val ACTION_NEXT = "com.musibox.widget.NEXT"
        private const val PREFS = "music_widget"

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MusicWidget::class.java))

        fun hasWidgets(context: Context): Boolean = runCatching { ids(context).isNotEmpty() }.getOrDefault(false)

        /** Chamado pelo serviço de música a cada troca de música ou de play/pause. */
        fun update(context: Context, title: String?, artist: String?, playing: Boolean, uri: Uri?, newSong: Boolean) {
            if (!hasWidgets(context)) return
            val info = Info(title ?: "MusiBox", artist ?: "Toque para abrir", playing, title != null)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", info.title).putString("artist", info.artist)
                .putBoolean("playing", playing).putBoolean("has", info.hasSong).apply()
            if (newSong) {
                val art = uri?.let { cover(context, it) }
                saveArt(context, art)
            }
            render(context, AppWidgetManager.getInstance(context), ids(context), info, loadArt(context))
        }

        /** Redesenha com o último estado (ex.: mudou o plano ou o tema). */
        fun refresh(context: Context) {
            if (!hasWidgets(context)) return
            render(context, AppWidgetManager.getInstance(context), ids(context), last(context), loadArt(context))
        }

        private fun last(context: Context): Info {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Info(
                p.getString("title", "MusiBox") ?: "MusiBox",
                p.getString("artist", "Toque para abrir") ?: "",
                p.getBoolean("playing", false),
                p.getBoolean("has", false),
            )
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, info: Info, art: Bitmap?) {
            if (ids.isEmpty()) return
            val unlocked = PlayBilling.hasPlan(Plan.PLUS)
            val accent = AppTheme.accent.let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
            val v = RemoteViews(context.packageName, R.layout.widget_player)
            if (!unlocked) {
                v.setTextViewText(R.id.widget_title, "Widget do Plus e Pro")
                v.setTextViewText(R.id.widget_artist, "Toque para ver os planos")
                v.setViewVisibility(R.id.widget_controls, View.GONE)
                v.setImageViewBitmap(R.id.widget_art, placeholder(accent))
                v.setOnClickPendingIntent(R.id.widget_root, openIntent(context, pro = true))
            } else {
                v.setTextViewText(R.id.widget_title, info.title)
                v.setTextViewText(R.id.widget_artist, info.artist)
                v.setViewVisibility(R.id.widget_controls, View.VISIBLE)
                v.setImageViewBitmap(R.id.widget_art, art?.let { rounded(it) } ?: placeholder(accent))
                v.setImageViewBitmap(R.id.widget_play, playButton(accent, info.playing))
                v.setOnClickPendingIntent(R.id.widget_root, openIntent(context, pro = false))
                v.setOnClickPendingIntent(R.id.widget_prev, action(context, ACTION_PREV, 1))
                v.setOnClickPendingIntent(R.id.widget_play, action(context, ACTION_PLAY, 2))
                v.setOnClickPendingIntent(R.id.widget_next, action(context, ACTION_NEXT, 3))
            }
            runCatching { manager.updateAppWidget(ids, v) }
        }

        private fun action(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, MusicWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun openIntent(context: Context, pro: Boolean): PendingIntent {
            val i = Intent(context, MainActivity::class.java)
                .putExtra(if (pro) MainActivity.EXTRA_OPEN_PRO else MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, if (pro) 11 else 10, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun open(context: Context, pro: Boolean) {
            runCatching { openIntent(context, pro).send() }
        }

        private fun artFile(context: Context) = File(context.cacheDir, "widget_art.png")

        private fun saveArt(context: Context, bmp: Bitmap?) {
            val f = artFile(context)
            if (bmp == null) {
                f.delete()
                return
            }
            runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }

        private fun loadArt(context: Context): Bitmap? = artFile(context).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

        private fun cover(context: Context, uri: Uri): Bitmap? {
            runCatching { return context.contentResolver.loadThumbnail(uri, Size(200, 200), null) }
            val r = MediaMetadataRetriever()
            return try {
                r.setDataSource(context, uri)
                val bytes = r.embeddedPicture ?: return null
                val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)?.let { Bitmap.createScaledBitmap(it, 200, 200, true) }
            } catch (e: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }

        private fun rounded(src: Bitmap): Bitmap {
            val size = 200
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 28f, 28f, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val s = Bitmap.createScaledBitmap(src, size, size, true)
            c.drawBitmap(s, 0f, 0f, p)
            return out
        }

        private fun placeholder(accent: Int): Bitmap {
            val size = 200
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = 0xFF2A2A2A.toInt()
            c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 28f, 28f, p)
            // nota musical
            p.color = accent
            c.drawCircle(78f, 136f, 26f, p)
            c.drawRect(96f, 52f, 108f, 136f, p)
            val flag = Path().apply { moveTo(104f, 52f); lineTo(150f, 66f); lineTo(150f, 88f); lineTo(104f, 76f); close() }
            c.drawPath(flag, p)
            return out
        }

        private fun playButton(accent: Int, playing: Boolean): Bitmap {
            val size = 144
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = accent
            c.drawCircle(size / 2f, size / 2f, size / 2f, p)
            val lum = (0.299 * android.graphics.Color.red(accent) + 0.587 * android.graphics.Color.green(accent) + 0.114 * android.graphics.Color.blue(accent)) / 255
            p.color = if (lum > 0.55) 0xFF0A0A0A.toInt() else 0xFFFFFFFF.toInt()
            if (playing) {
                c.drawRoundRect(RectF(50f, 44f, 64f, 100f), 4f, 4f, p)
                c.drawRoundRect(RectF(80f, 44f, 94f, 100f), 4f, 4f, p)
            } else {
                val tri = Path().apply { moveTo(56f, 42f); lineTo(104f, 72f); lineTo(56f, 102f); close() }
                c.drawPath(tri, p)
            }
            return out
        }
    }
}
