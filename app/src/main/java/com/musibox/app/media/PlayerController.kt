package com.musibox.app.media

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musibox.app.data.db.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val connected: Boolean = false,
    val current: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
    val queue: List<NowPlaying> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/** Ponte entre a interface e o serviço de reprodução (MediaController). */
class PlayerController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED)
            refresh(rebuildQueue = queueChanged)
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                refresh(rebuildQueue = true)
            } catch (e: Exception) {
                future = null
            }
        }, mainExecutor)
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let { action(it); return }
        connect()
        val f = future ?: return
        f.addListener({ controller?.let(action) }, mainExecutor)
    }

    private fun refresh(rebuildQueue: Boolean) {
        val c = controller ?: return
        val queue = if (rebuildQueue || _state.value.queue.size != c.mediaItemCount) {
            (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toNowPlaying() }
        } else {
            _state.value.queue
        }
        val current = c.currentMediaItem?.toNowPlaying()
        val duration = c.duration.takeIf { it > 0 } ?: current?.durationMs ?: 0L
        _state.value = PlayerState(
            connected = true,
            current = current,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            repeatMode = c.repeatMode,
            shuffle = c.shuffleModeEnabled,
            speed = c.playbackParameters.speed,
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem() || c.currentPosition > 3000,
        )
        if (c.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val c = controller ?: break
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0),
                    durationMs = c.duration.takeIf { it > 0 } ?: _state.value.durationMs,
                )
                delay(500)
            }
        }
    }

    // ---------------- Comandos ----------------

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle && startIndex == 0) items.indices.random() else startIndex.coerceIn(items.indices)
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playSong(song: Song, context: List<Song> = listOf(song)) {
        val index = context.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playSongs(if (context.isEmpty()) listOf(song) else context, index)
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }

    fun pause() = withController { it.pause() }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() = withController { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }

    fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }

    fun playAt(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0)
            c.prepare()
            c.play()
        }
    }

    fun removeAt(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun move(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun playNext(songs: List<Song>) = withController { c ->
        val items: List<MediaItem> = songs.map { it.toMediaItem() }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
            c.play()
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, items)
        }
    }

    fun addToQueue(songs: List<Song>) = withController { c ->
        val items: List<MediaItem> = songs.map { it.toMediaItem() }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
        } else {
            c.addMediaItems(items)
        }
    }

    fun clearQueue() = withController { c ->
        c.stop()
        c.clearMediaItems()
    }
}
