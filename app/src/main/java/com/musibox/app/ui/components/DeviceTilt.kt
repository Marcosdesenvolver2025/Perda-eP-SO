package com.musibox.app.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Inclinação do aparelho (paralaxe 3D). Lido só na fase de desenho: não recompõe a tela.

@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(lifecycle) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = 0f
        var fy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                val y = (e.values[1] / 9.81f - 0.65f).coerceIn(-1f, 1f)
                fx += (x - fx) * 0.12f
                fy += (y - fy) * 0.12f
                val cur = tilt.value
                if (abs(fx - cur.x) > 0.008f || abs(fy - cur.y) > 0.008f) tilt.value = Offset(fx, fy)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                sm?.unregisterListener(listener)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sm?.unregisterListener(listener)
        }
    }
    return tilt
}

