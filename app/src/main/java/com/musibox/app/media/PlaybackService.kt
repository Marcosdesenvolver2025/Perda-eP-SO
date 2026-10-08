package com.musibox.app.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.room.withTransaction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.musibox.app.MainActivity
import com.musibox.app.MusiBoxApp
import com.musibox.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço de reprodução em segundo plano (Media3 + MediaSession).
 * Gera a notificação de mídia e os controles da tela bloqueada automaticamente,
 * grava histórico e salva fila/posição para retomar depois.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as MusiBoxApp).container

    private var stopOnTaskRemoved = false
    private var rememberPosition = true
    private var transitionToken = 0
    private var recordedToken = -1
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val openApp = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, openApp, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()

        val provider = DefaultMediaNotificationProvider.Builder(this).build()
        provider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(provider)

        player.addListener(listener)

        scope.launch {
            container.settings.settings.collect { s ->
                player.pauseAtEndOfMediaItems = !s.autoPlayNext
                player.skipSilenceEnabled = s.skipSilence
                player.setHandleAudioBecomingNoisy(s.pauseOnDisconnect)
                stopOnTaskRemoved = s.stopOnTaskRemoved
                rememberPosition = s.rememberPosition
            }
        }
        scope.launch { restoreQueue() }
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) savePlaybackState(markForSync = false)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || stopOnTaskRemoved || !p.playWhenReady || p.mediaItemCount == 0 ||
            p.playbackState == Player.STATE_ENDED
        ) {
            savePlaybackState(markForSync = true)
            p?.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePlaybackState(markForSync = true)
        session?.run {
            player.removeListener(listener)
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
                recordIfNeeded()
            } else {
                savePlaybackState(markForSync = true)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            transitionToken++
            savePlaybackState(markForSync = true)
            if (player.isPlaying) recordIfNeeded()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            // Arquivo apagado, movido ou formato não suportado: pula para a próxima.
            consecutiveErrors++
            if (consecutiveErrors < 5 && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun recordIfNeeded() {
        if (recordedToken == transitionToken) return
        recordedToken = transitionToken
        val item = player.currentMediaItem ?: return
        val np = item.toNowPlaying()
        container.appScope.launch {
            runCatching {
                container.history.recordPlay(
                    np.songKey, np.looseKey, np.songId, np.title, np.artist, np.album, np.durationMs,
                )
            }
        }
    }

    private fun saveQueue() {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toNowPlaying().toQueueEntity(it) }
        val db = container.db
        container.appScope.launch {
            runCatching {
                db.withTransaction {
                    db.queueDao().clear()
                    items.chunked(500).forEach { db.queueDao().insertAll(it) }
                }
            }
        }
        savePlaybackState(markForSync = true)
    }

    private fun savePlaybackState(markForSync: Boolean) {
        if (!::player.isInitialized) return
        val index = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0)
        val hasItems = player.mediaItemCount > 0
        container.appScope.launch {
            runCatching {
                container.settings.savePlaybackState(index, position, markForSync && hasItems)
                if (markForSync && hasItems) container.sync.requestSync()
            }
        }
    }

    private suspend fun loadSavedQueue(): Triple<List<MediaItem>, Int, Long> {
        val s = container.settings.snapshot()
        val saved = container.db.queueDao().get()
        if (saved.isEmpty()) return Triple(emptyList(), 0, 0)
        val existing = container.db.songDao().allIds().toHashSet()
        val valid = saved.filter { it.songId in existing && it.uri.isNotBlank() }
        if (valid.isEmpty()) return Triple(emptyList(), 0, 0)
        val savedCurrent = saved.getOrNull(s.queueIndex)
        var index = valid.indexOfFirst { it.position == savedCurrent?.position }
        val position = if (index >= 0 && s.rememberPosition) s.queuePositionMs else 0L
        if (index < 0) index = 0
        return Triple(valid.map { it.toMediaItem() }, index, position)
    }

    private suspend fun restoreQueue() {
        if (player.mediaItemCount > 0) return
        val s = container.settings.snapshot()
        if (!s.rememberPosition) return
        val (items, index, position) = loadSavedQueue()
        if (items.isEmpty() || player.mediaItemCount > 0) return
        player.setMediaItems(items, index, position)
        player.playWhenReady = false
        player.prepare()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlayableUri() }.toMutableList())

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val (items, index, position) = loadSavedQueue()
                    if (items.isEmpty()) {
                        future.setException(IllegalStateException("Nenhuma música para retomar"))
                    } else {
                        future.set(MediaSession.MediaItemsWithStartPosition(items, index, position))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }
}
