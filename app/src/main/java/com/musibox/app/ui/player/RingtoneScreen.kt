package com.musibox.app.ui.player

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.musibox.app.billing.Plan
import com.musibox.app.billing.PlayBilling
import com.musibox.app.core.Fmt
import com.musibox.app.media.RingtoneMaker
import com.musibox.app.ui.components.GradientButton
import com.musibox.app.ui.components.LocalAppContainer
import com.musibox.app.ui.components.MbTopBar
import com.musibox.app.ui.components.Neo
import com.musibox.app.ui.components.rememberSnack
import com.musibox.app.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Criar toque de celular com um trecho da música que está tocando (Pro). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneScreen(nav: NavController) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val state by container.player.state.collectAsStateWithLifecycle()
    val pro by PlayBilling.pro.collectAsStateWithLifecycle()
    val snack = rememberSnack()
    val scope = rememberCoroutineScope()
    val song = state.current
    val duration = state.durationMs.takeIf { it > 0 } ?: song?.durationMs ?: 0L
    var range by remember(song?.songKey) {
        val start = (duration * 0.3f).toLong().coerceAtLeast(0)
        mutableStateOf(start.toFloat()..minOf(duration, start + 30_000).toFloat())
    }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<RingtoneMaker.Result?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    val mp = remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        mp.value?.runCatching { stop(); release() }
        mp.value = null
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    Column(Modifier.fillMaxSize()) {
        MbTopBar("Criar toque", onBack = { nav.popBackStack() }, subtitle = "Trecho da música vira toque do celular")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (pro.plan != Plan.PRO) {
                Spacer(Modifier.height(30.dp))
                Icon(Icons.Rounded.WorkspacePremium, null, tint = Neo.Green, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Criar toque de celular é um recurso do Pro.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                GradientButton("Ver o Pro", onClick = { nav.navigate(Routes.PRO) }, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            if (song == null || song.uri == null || duration <= 0) {
                Spacer(Modifier.height(30.dp))
                Text(
                    "Toque uma música primeiro e depois volte aqui para escolher o trecho.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Neo.Card)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Neo.Green.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MusicNote, null, tint = Neo.Green) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.displayArtist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Escolha o trecho (até 40 segundos)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            RangeSlider(
                value = range,
                onValueChange = { r ->
                    var a = r.start
                    var b = r.endInclusive
                    if (b - a > RingtoneMaker.MAX_MS) {
                        if (a != range.start) b = a + RingtoneMaker.MAX_MS else a = b - RingtoneMaker.MAX_MS
                    }
                    if (b - a < 3_000f) b = minOf(duration.toFloat(), a + 3_000f)
                    range = a..b
                    saved = null
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Início ${Fmt.duration(range.start.toLong())}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${((range.endInclusive - range.start) / 1000).toInt()} s", color = Neo.Green, fontWeight = FontWeight.Bold)
                Text("Fim ${Fmt.duration(range.endInclusive.toLong())}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = {
                    if (previewJob != null) {
                        stopPreview()
                    } else {
                        container.player.pause()
                        runCatching {
                            val p = MediaPlayer()
                            p.setDataSource(context, song.uri)
                            p.prepare()
                            p.seekTo(range.start.toInt())
                            p.start()
                            mp.value = p
                            previewJob = scope.launch {
                                delay((range.endInclusive - range.start).toLong())
                                stopPreview()
                            }
                        }.onFailure { snack("Não foi possível tocar o trecho.") }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(if (previewJob != null) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (previewJob != null) "Parar" else "Ouvir o trecho")
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                "Salvar como toque",
                onClick = {
                    stopPreview()
                    saving = true
                    scope.launch {
                        runCatching {
                            RingtoneMaker.create(context, song.uri, song.title, range.start.toLong(), range.endInclusive.toLong())
                        }.onSuccess {
                            saved = it
                            snack("Toque salvo na pasta Toques/MusiBox.")
                        }.onFailure {
                            snack(if (it is RingtoneMaker.Unsupported) it.message ?: "Formato não suportado." else "Não foi possível criar o toque.")
                        }
                        saving = false
                    }
                },
                icon = Icons.Rounded.NotificationsActive,
                loading = saving,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            saved?.let { r ->
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (RingtoneMaker.canSetDefault(context)) {
                            snack(if (RingtoneMaker.setAsDefault(context, r.uri)) "Pronto! Este é o novo toque do celular." else "Não foi possível trocar o toque.")
                        } else {
                            snack("Autorize o MusiBox a mudar o toque e toque aqui de novo.")
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Definir como toque do celular") }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "O toque fica em Toques/MusiBox e aparece nas configurações de som do Android (toques, notificações e alarmes). " +
                    "Funciona com músicas MP3, M4A e WAV.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}
