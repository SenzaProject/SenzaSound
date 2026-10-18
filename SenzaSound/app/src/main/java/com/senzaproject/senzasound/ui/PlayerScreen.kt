package com.senzaproject.senzasound.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.senzasound.dsp.ToneGenerator

private fun fmt(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayerScreen(
    vm: SoundViewModel,
    padding: PaddingValues,
    hasMicPermission: Boolean,
    requestMic: () -> Unit,
) {
    val ui by vm.player.ui.collectAsStateWithLifecycle()
    val bands by vm.analyzer.bands.collectAsStateWithLifecycle()
    val toneMode by vm.tone.active.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            vm.tone.stop()
            vm.player.setTracks(uris)
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            Text("Pemutar & Uji Suara", style = MaterialTheme.typography.headlineMedium)
            Text("Dengarkan efek langsung — pasti aktif di pemutar ini", style = MaterialTheme.typography.bodySmall, color = TextDim)
        }

        GlassCard {
            SpectrumBars(bands, Modifier.fillMaxWidth().height(90.dp))
            if (!hasMicPermission) {
                Spacer(Modifier.height(8.dp))
                Text("Analyzer spektrum membutuhkan izin audio (RECORD_AUDIO). Tidak ada rekaman yang dibuat.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                Button(onClick = requestMic) { Text("Izinkan analyzer") }
            }
        }

        GlassCard {
            val track = ui.tracks.getOrNull(ui.index)
            Text(track?.title ?: "Belum ada lagu", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (track == null) "Pilih file audio dari penyimpananmu" else "Trek ${ui.index + 1} dari ${ui.tracks.size}",
                style = MaterialTheme.typography.bodySmall, color = TextDim,
            )
            if (ui.error != null) Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            val dur = ui.durationMs.coerceAtLeast(1)
            Slider(
                value = ui.positionMs.coerceIn(0, dur).toFloat(),
                onValueChange = { vm.player.seekTo(it.toInt()) },
                valueRange = 0f..dur.toFloat(),
                enabled = track != null,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Surface2),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(ui.positionMs), style = MaterialTheme.typography.labelSmall, color = TextDim)
                Text(fmt(ui.durationMs), style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm.player::prev, enabled = track != null) { Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", Modifier.size(32.dp)) }
                FilledIconButton(onClick = vm.player::toggle, enabled = track != null, modifier = Modifier.size(64.dp)) {
                    Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/Jeda", Modifier.size(36.dp))
                }
                IconButton(onClick = vm.player::next, enabled = track != null) { Icon(Icons.Rounded.SkipNext, "Berikutnya", Modifier.size(32.dp)) }
            }
            Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, null)
                Text("  Pilih lagu")
            }
        }

        GlassCard {
            SectionTitle("Penguji suara", "Sinyal uji untuk mendengar perubahan equalizer")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToneGenerator.Mode.values().forEach { m ->
                    FilterChip(
                        selected = toneMode == m,
                        onClick = { vm.toggleTone(m, hasMicPermission) },
                        label = { Text(m.label) },
                        colors = chipColors(),
                    )
                }
            }
            Text(
                "Sweep menggerakkan nada dari bass ke treble sehingga puncak dan lembah kurva EQ terdengar jelas. " +
                    "Mulai dengan volume kecil.",
                style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
