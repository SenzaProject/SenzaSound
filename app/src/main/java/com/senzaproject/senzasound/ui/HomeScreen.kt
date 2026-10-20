package com.senzaproject.senzasound.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.senzaproject.senzasound.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.senzasound.data.Endpoint
import com.senzaproject.senzasound.data.Ieq
import com.senzaproject.senzasound.data.Profile

private fun profileIcon(p: Profile): ImageVector = when (p) {
    Profile.MOVIE -> Icons.Rounded.Movie
    Profile.MUSIC -> Icons.Rounded.MusicNote
    Profile.GAME -> Icons.Rounded.SportsEsports
    Profile.VOICE -> Icons.Rounded.Mic
    Profile.CUSTOM -> Icons.Rounded.Tune
}

private fun endpointIcon(e: Endpoint): ImageVector = when (e) {
    Endpoint.SPEAKER -> Icons.Rounded.Speaker
    Endpoint.HEADPHONE -> Icons.Rounded.Headphones
    Endpoint.BLUETOOTH -> Icons.Rounded.Bluetooth
}

@Composable
fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Accent.copy(alpha = 0.2f),
    selectedLabelColor = Accent,
    selectedLeadingIconColor = Accent,
)

@Composable
fun HomeScreen(vm: SoundViewModel, padding: PaddingValues) {
    val app by vm.state.collectAsStateWithLifecycle()
    val bands by vm.analyzer.bands.collectAsStateWithLifecycle()
    val endpoint by vm.endpoint.collectAsStateWithLifecycle()
    val s = app.current

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo_senza),
                contentDescription = "Logo Senza Sound",
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Senza Sound", style = MaterialTheme.typography.headlineMedium)
                Text("Audio spasial & equalizer profesional", style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
            Switch(
                checked = app.enabled,
                onCheckedChange = vm::setEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Bg),
            )
        }

        GlassCard {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                HeroVisualizer(bands, app.enabled, Modifier.fillMaxWidth().height(230.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (app.enabled) "Aktif · ${app.profile.label}" else "Dimatikan",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (app.enabled) Accent else TextDim,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(endpoint.label) },
                    leadingIcon = { Icon(endpointIcon(endpoint), null, tint = Accent) },
                )
            }
        }

        Column {
            SectionTitle("Profil suara", "Tiap profil menyimpan pengaturannya sendiri")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profile.values().forEach { p ->
                    FilterChip(
                        selected = app.profile == p,
                        onClick = { vm.setProfile(p) },
                        label = { Text(p.label) },
                        leadingIcon = { Icon(profileIcon(p), null) },
                        colors = chipColors(),
                    )
                }
            }
        }

        GlassCard {
            SectionTitle("Suara spasial", "Virtualizer 3D untuk ${endpoint.label.lowercase()}")
            FxSlider("Surround virtualizer", "Lebarkan panggung suara", s.spatial, vm::setSpatial, enabled = app.enabled)
            FxSlider("Kejernihan vokal", "Perjelas suara vokal & percakapan", s.dialog, vm::setDialog, enabled = app.enabled)
            FxSlider("Bass punch", "Tambah bobot & hentakan nada rendah", s.bass, vm::setBass, enabled = app.enabled)
        }

        GlassCard {
            SectionTitle("Intelligent EQ", "Kurva target otomatis yang dicampur ke equalizer")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ieq.values().forEach { i ->
                    FilterChip(
                        selected = s.ieq == i,
                        onClick = { vm.setIeq(i) },
                        label = { Text(i.label) },
                        colors = chipColors(),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            FxSlider("Intensitas", "Seberapa kuat kurva diterapkan", s.ieqAmount, vm::setIeqAmount, enabled = app.enabled && s.ieq != Ieq.OFF)
        }

        GlassCard {
            SectionTitle("Volume", "Kendali dinamika & perlindungan telinga")
            FxSlider("Volume leveler", "Samakan volume antar lagu/adegan", s.leveler, vm::setLeveler, enabled = app.enabled)
            FxSlider("Loudness boost", "Naikkan kenyaringan (hingga +8 dB)", s.loudness, vm::setLoudness, enabled = app.enabled)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Volume aman", style = MaterialTheme.typography.titleSmall)
                    Text("Batasi puncak suara agar telinga terlindungi", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
                Switch(
                    checked = s.safeVolume,
                    onCheckedChange = vm::setSafeVolume,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Bg),
                )
            }
        }

        OutlinedButton(onClick = vm::resetProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Reset profil ${app.profile.label} ke bawaan")
        }
        Spacer(Modifier.height(16.dp))
    }
}
