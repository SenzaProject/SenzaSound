package com.senzaproject.senzasound.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.senzasound.data.Eq
import com.senzaproject.senzasound.dsp.EqMath
import kotlin.math.roundToInt

@Composable
fun EqScreen(vm: SoundViewModel, padding: PaddingValues) {
    val app by vm.state.collectAsStateWithLifecycle()
    val s = app.current
    var showSave by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val endpoint by vm.endpoint.collectAsStateWithLifecycle()
    val effective = EqMath.effectiveGains(s, endpoint)

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            Text("Equalizer", style = MaterialTheme.typography.headlineMedium)
            Text(
                "32 band · profil ${app.profile.label} · preset: ${s.eqPreset}",
                style = MaterialTheme.typography.bodySmall, color = TextDim,
            )
        }

        GlassCard {
            SectionTitle("Respons frekuensi", "Termasuk Intelligent EQ, dialog, dan bass")
            EqCurve(effective, Modifier.fillMaxWidth().height(140.dp))
        }

        GlassCard {
            SectionTitle("Bass & vokal", "Atur cepat tenaga bass dan kejernihan suara")
            FxSlider("Bass punch", "Tenaga & hentakan nada rendah", s.bass, vm::setBass, enabled = app.enabled)
            FxSlider("Kejernihan vokal", "Perjelas suara penyanyi & percakapan", s.dialog, vm::setDialog, enabled = app.enabled)
        }

        GlassCard {
            SectionTitle("32 band", "Geser kiri-kanan · seret untuk atur · ketuk dua kali untuk reset ke 0 dB")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                for (i in 0 until Eq.BANDS) {
                    val v = if (i < s.bands.size) s.bands[i] else 0f
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(46.dp)) {
                        Text(
                            (if (v > 0) "+" else "") + (if (v == v.roundToInt().toFloat()) "${v.roundToInt()}" else "%.1f".format(v)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent, textAlign = TextAlign.Center,
                        )
                        VerticalGainSlider(
                            value = v,
                            onChange = { vm.setBand(i, it) },
                            modifier = Modifier.fillMaxWidth().height(210.dp),
                            enabled = app.enabled,
                        )
                        Text(Eq.LABELS[i], style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                }
            }
        }

        Column {
            SectionTitle("Preset", "Pilih kurva siap pakai atau simpan milikmu")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Eq.PRESETS.keys.forEach { name ->
                    FilterChip(
                        selected = s.eqPreset == name,
                        onClick = { vm.applyPreset(name) },
                        label = { Text(name) },
                        colors = chipColors(),
                    )
                }
            }
            if (app.userPresets.isNotEmpty()) {
                Text("Preset saya", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.userPresets.keys.forEach { name ->
                        FilterChip(
                            selected = s.eqPreset == name,
                            onClick = { vm.applyPreset(name) },
                            label = { Text(name) },
                            colors = chipColors(),
                        )
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.userPresets.keys.forEach { name ->
                        TextButton(onClick = { vm.deletePreset(name) }) { Text("Hapus \"$name\"") }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = vm::resetEq, modifier = Modifier.weight(1f)) { Text("Reset") }
            OutlinedButton(onClick = { newName = ""; showSave = true }, modifier = Modifier.weight(1f)) { Text("Simpan preset") }
        }
        Column(Modifier.height(16.dp)) {}
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Simpan preset") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Nama preset") })
            },
            confirmButton = {
                TextButton(onClick = { vm.savePreset(newName); showSave = false }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Batal") } },
        )
    }
}
