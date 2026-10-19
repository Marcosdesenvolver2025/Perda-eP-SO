package com.musibox.app.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Timer de sono (recurso do Pró): a música para sozinha no horário escolhido
 * ou no fim da música atual. Quem pausa de fato é o serviço de música.
 */
object SleepTimer {
    data class State(val endsAt: Long = 0L, val endOfTrack: Boolean = false) {
        val active: Boolean get() = endsAt > 0L || endOfTrack
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(minutes: Int) {
        _state.value = State(endsAt = System.currentTimeMillis() + minutes * 60_000L)
    }

    fun endOfTrack() {
        _state.value = State(endOfTrack = true)
    }

    fun cancel() {
        _state.value = State()
    }

    /** "Para em 23 min" / "Para no fim da música" */
    fun label(s: State = _state.value): String = when {
        s.endOfTrack -> "Para no fim da música"
        s.endsAt > 0 -> {
            val left = ((s.endsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
            "Para em $left min"
        }
        else -> "Timer de sono"
    }
}
