package com.musibox.app.vault

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.musibox.app.R

/**
 * Ícone disfarçado (recurso do Pro): troca o ícone e o nome do MusiBox na tela inicial
 * por Calculadora, Notas, Relógio ou Clima. Ao tocar, o MusiBox abre normalmente.
 */
object IconDisguise {
    enum class Option(val alias: String, val label: String, val icon: Int) {
        DEFAULT("LauncherDefault", "MusiBox (padrão)", R.mipmap.ic_launcher),
        CALC("LauncherCalc", "Calculadora", R.mipmap.ic_disguise_calc),
        NOTES("LauncherNotes", "Notas", R.mipmap.ic_disguise_notes),
        CLOCK("LauncherClock", "Relógio", R.mipmap.ic_disguise_clock),
        WEATHER("LauncherWeather", "Clima", R.mipmap.ic_disguise_weather),
    }

    private fun component(context: Context, o: Option) = ComponentName(context.packageName, "com.musibox.app.${o.alias}")

    fun current(context: Context): Option {
        val pm = context.packageManager
        return Option.entries.firstOrNull { o ->
            when (pm.getComponentEnabledSetting(component(context, o))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> o == Option.DEFAULT
                else -> false
            }
        } ?: Option.DEFAULT
    }

    /** Liga o ícone escolhido e desliga os outros (o app não é fechado). */
    fun apply(context: Context, option: Option) {
        val pm = context.packageManager
        // Primeiro liga o novo (para nunca ficar sem ícone), depois desliga os outros
        pm.setComponentEnabledSetting(
            component(context, option),
            if (option == Option.DEFAULT) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        Option.entries.filter { it != option }.forEach { o ->
            pm.setComponentEnabledSetting(component(context, o), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    /** Sem Pro, volta ao ícone normal. */
    fun resetIfNeeded(context: Context) {
        if (current(context) != Option.DEFAULT) apply(context, Option.DEFAULT)
    }
}
