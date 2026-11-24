package com.musibox.app.media

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EditCategory(val label: String) {
    SPEED("Velocidade e tom"),
    SPACE("Espaço e ambiente"),
    RETRO("Textura e retrô"),
    CUTS("Cortes e graves"),
}

/**
 * Music Edits (planos Go, Plus e Pro): efeitos aplicados na hora, enquanto a música toca.
 * [speed] e [pitch] mudam a velocidade e o tom; o resto do som é feito no [EditProcessor].
 */
enum class MusicEdit(
    val id: String,
    val label: String,
    val desc: String,
    val emoji: String,
    val category: EditCategory?,
    val speed: Float = 1f,
    val pitch: Float = 1f,
) {
    NONE("none", "Normal", "Música original, sem efeito", "🎵", null),

    SLOWED_REVERB("slowed_reverb", "Slowed + Reverb", "Mais lenta, grave e com eco", "🌙", EditCategory.SPEED, 0.85f, 0.85f),
    SUPER_SLOWED("super_slowed", "Super Slowed", "Bem lenta e pesada", "🐢", EditCategory.SPEED, 0.74f, 0.74f),
    ULTRA_SLOWED("ultra_slowed", "Ultra Slowed", "Extremamente lenta e pesada", "🧊", EditCategory.SPEED, 0.62f, 0.62f),
    SPED_UP("sped_up", "Sped Up", "Mais rápida e aguda", "⚡", EditCategory.SPEED, 1.2f, 1.2f),
    NIGHTCORE("nightcore", "Nightcore", "Muito acelerada e aguda", "🌸", EditCategory.SPEED, 1.32f, 1.32f),
    DAYCORE("daycore", "Daycore", "O contrário do Nightcore: lenta e grave", "🌫️", EditCategory.SPEED, 0.86f, 0.8f),

    EIGHT_D("8d", "8D Audio", "O som gira ao redor da cabeça (use fones)", "🎧", EditCategory.SPACE),
    UNDERWATER("underwater", "Debaixo d'água", "Som abafado, como se estivesse debaixo d'água", "🌊", EditCategory.SPACE),
    OTHER_ROOM("other_room", "Outro cômodo", "A música tocando em outro cômodo", "🚪", EditCategory.SPACE),
    BATHROOM("bathroom", "Banheiro da festa", "A festa rolando do lado de fora", "🚻", EditCategory.SPACE),
    SPATIAL("spatial", "3D / Spatial", "Som mais amplo e profundo, sem girar", "🌐", EditCategory.SPACE),

    LOFI("lofi", "Lo-Fi", "Som suave com chiado de vinil", "📻", EditCategory.RETRO),
    BITCRUSHED("bitcrushed", "Bitcrushed", "Som de videogame antigo", "👾", EditCategory.RETRO),
    VHS("vhs", "VHS / Fita", "Fita cassete velha e gasta", "📼", EditCategory.RETRO),

    CHOPPED("chopped", "Chopped and Screwed", "Lenta, com cortes e repetições", "✂️", EditCategory.CUTS, 0.78f, 0.78f),
    BASS("bass", "Bass Boosted", "Graves no máximo", "🔊", EditCategory.CUTS),
    KARAOKE("karaoke", "Karaokê (sem voz)", "Tira a voz principal. Fica melhor em algumas músicas", "🎤", EditCategory.CUTS),
    REVERSED("reversed", "Reversed", "Música de trás para frente", "⏪", EditCategory.CUTS),
    ;

    companion object {
        fun fromId(id: String?): MusicEdit = entries.firstOrNull { it.id == id } ?: NONE
    }
}

object MusicEdits {
    /** Efeito da música que está tocando agora (o que a pessoa escolheu). */
    private val _current = MutableStateFlow(MusicEdit.NONE)
    val current: StateFlow<MusicEdit> = _current.asStateFlow()

    /** Efeito que o som está usando de verdade (vira Normal se a pessoa não tiver plano). */
    @Volatile
    var active: MusicEdit = MusicEdit.NONE
        internal set

    /** Pedidos da tela para trocar o efeito da música atual (o serviço de reprodução aplica). */
    internal val requests = MutableSharedFlow<MusicEdit>(extraBufferCapacity = 4)

    fun select(edit: MusicEdit) {
        requests.tryEmit(edit)
    }

    internal fun setCurrent(edit: MusicEdit, allowed: Boolean) {
        _current.value = edit
        active = if (allowed) edit else MusicEdit.NONE
    }
}
