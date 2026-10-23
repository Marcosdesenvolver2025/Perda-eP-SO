package com.musibox.app.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var autoPlayNext = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as MusiBoxApp).container

    private var stopOnTaskRemoved = false
    private var rememberPosition = true
    private var transitionToken = 0
    private var recordedToken = -1
    private var consecutiveErrors = 0
    @Volatile private var sleepFading = false
    @Volatile private var autoTransition = false
    private val currentKey = MutableStateFlow<String?>(null)
    @Volatile private var currentFav = false

    private fun notificationButtons(fav: Boolean): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder()
            .setDisplayName(if (fav) "Remover dos favoritos" else "Favoritar")
            .setIconResId(if (fav) R.drawable.ic_notif_heart_filled else R.drawable.ic_notif_heart)
            .setSessionCommand(CMD_FAVORITE)
            .build(),
        CommandButton.Builder()
            .setDisplayName("Fechar")
            .setIconResId(R.drawable.ic_notif_close)
            .setSessionCommand(CMD_CLOSE)
            .build(),
    )

    private fun toggleFavoriteFromNotification() {
        val item = player.currentMediaItem ?: return
        val np = item.toNowPlaying()
        val target = !currentFav
        container.appScope.launch {
            runCatching {
                container.library.setFavorite(np.songKey, np.looseKey, np.title, np.artist, np.album, np.durationMs, target)
            }
        }
    }

    /** "X" da notificação ou do mini player: para e limpa a fila (o player some até tocar de novo). */
    private fun closePlayback() {
        savePlaybackState(markForSync = true)
        player.stop()
        player.clearMediaItems()
    }

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

        // Equalizador / graves / som 3D (Pro) presos à sessão de áudio do player
        runCatching {
            val sid = (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).generateAudioSessionId()
            player.audioSessionId = sid
            AudioFx.attach(this, sid)
        }
        scope.launch {
            com.musibox.app.billing.PlayBilling.pro.collect {
                val billing = com.musibox.app.billing.PlayBilling
                AudioFx.allowed = billing.isProNow()
                AudioFx.plusAllowed = billing.hasPlan(com.musibox.app.billing.Plan.PLUS)
                // Velocidade é recurso dos planos: sem plano volta ao normal
                if (!billing.isProNow() && player.playbackParameters.speed != 1f) {
                    player.playbackParameters = androidx.media3.common.PlaybackParameters(1f)
                }
                MusicWidget.refresh(this@PlaybackService)
            }
        }
        // Transição suave entre músicas (Plus e Pro): abaixa no fim e sobe no começo da próxima
        scope.launch {
            var fadeInFrom = -1L
            var lastIndex = -1
            while (isActive) {
                delay(80)
                if (sleepFading) continue
                if (!AudioFx.smoothActive()) {
                    if (player.volume != 1f) player.volume = 1f
                    fadeInFrom = -1L
                    continue
                }
                if (player.currentMediaItemIndex != lastIndex) {
                    if (lastIndex != -1 && autoTransition) fadeInFrom = 0L
                    lastIndex = player.currentMediaItemIndex
                    autoTransition = false
                }
                if (!player.isPlaying) continue
                val dur = player.duration
                val pos = player.currentPosition
                var target = 1f
                if (dur != C.TIME_UNSET && dur > SMOOTH_MS * 3) {
                    val remaining = dur - pos
                    if (remaining < SMOOTH_MS) target = (remaining.toFloat() / SMOOTH_MS).coerceIn(0.04f, 1f)
                }
                if (fadeInFrom >= 0L) {
                    if (pos < SMOOTH_MS) target = minOf(target, (pos.toFloat() / SMOOTH_MS).coerceIn(0.04f, 1f)) else fadeInFrom = -1L
                }
                if (kotlin.math.abs(player.volume - target) > 0.005f) player.volume = target
            }
        }
        // Timer de sono (Pro)
        scope.launch {
            SleepTimer.state.collectLatest { t ->
                player.pauseAtEndOfMediaItems = t.endOfTrack || !autoPlayNext
                if (t.endsAt > 0L) {
                    val wait = t.endsAt - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                    fadeOutAndPause()
                    SleepTimer.cancel()
                }
            }
        }
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && SleepTimer.state.value.endOfTrack) {
                    SleepTimer.cancel()
                }
            }
        })

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

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.notif_channel_playback)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(provider)

        player.addListener(listener)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentKey.value = mediaItem?.mediaMetadata?.extras?.getString(Extras.SONG_KEY)
            }
        })

        // Botões próprios na notificação: coração (favoritar) e X (fechar o player).
        scope.launch {
            combine(container.library.favoriteKeys, currentKey) { favs, key -> key != null && key in favs }
                .distinctUntilChanged()
                .collect { fav ->
                    currentFav = fav
                    session?.setCustomLayout(notificationButtons(fav))
                }
        }

        scope.launch {
            container.settings.settings.collect { s ->
                autoPlayNext = s.autoPlayNext
                player.pauseAtEndOfMediaItems = !s.autoPlayNext || SleepTimer.state.value.endOfTrack
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

    /** Abaixa o volume aos poucos e pausa (timer de sono). */
    private suspend fun fadeOutAndPause() {
        sleepFading = true
        val start = player.volume
        for (i in 1..20) {
            player.volume = start * (1f - i / 20f)
            delay(400)
        }
        player.pause()
        player.volume = start
        sleepFading = false
    }

    override fun onDestroy() {
        AudioFx.release()
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
            updateWidget(newSong = false)
            if (isPlaying) {
                consecutiveErrors = 0
                recordIfNeeded()
            } else {
                savePlaybackState(markForSync = true)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) autoTransition = true
            updateWidget(newSong = true)
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

    /** Atualiza o widget da tela inicial (se a pessoa tiver um). */
    private fun updateWidget(newSong: Boolean) {
        val app = applicationContext
        if (!MusicWidget.hasWidgets(app)) return
        val np = player.currentMediaItem?.toNowPlaying()
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        container.appScope.launch(Dispatchers.IO) {
            runCatching { MusicWidget.update(app, np?.title, np?.displayArtist, playing, np?.uri, newSong) }
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
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(CMD_FAVORITE)
                .add(CMD_CLOSE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(notificationButtons(currentFav))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_FAVORITE.customAction -> toggleFavoriteFromNotification()
                CMD_CLOSE.customAction -> closePlayback()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

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

    companion object {
        /** Duração da transição suave. */
        private const val SMOOTH_MS = 3500L
        private val CMD_FAVORITE = SessionCommand("musibox.FAVORITE", Bundle.EMPTY)
        private val CMD_CLOSE = SessionCommand("musibox.CLOSE", Bundle.EMPTY)
    }
}
