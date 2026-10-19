package com.senzaproject.senzasound.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.senzaproject.senzasound.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(if (ok) "● Aktif" else "○ Tidak tersedia", color = if (ok) Accent else TextDim, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AboutPoint(title: String, desc: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextDim)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun InfoScreen(vm: SoundViewModel, padding: PaddingValues) {
    val st by vm.engine.collectAsStateWithLifecycle()
    val endpoint by vm.endpoint.collectAsStateWithLifecycle()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            Text("Diagnostik & Info", style = MaterialTheme.typography.headlineMedium)
            Text("Status mesin audio di perangkat ini", style = MaterialTheme.typography.bodySmall, color = TextDim)
        }

        GlassCard {
            SectionTitle("Mesin efek", "Sesi terpasang: ${if (st.sessions.isEmpty()) "belum ada" else st.sessions.joinToString()}")
            StatusRow("Efek native Senza (vendor)", st.native)
            StatusRow("Prosesor EQ 10 band & dinamika", st.dynamics)
            StatusRow("EQ cadangan", st.legacyEq)
            StatusRow("Penguat bass", st.bass)
            StatusRow("Virtualisasi spasial", st.virtualizer)
            StatusRow("Penguat kenyaringan", st.loudness)
            Text("Output saat ini: ${endpoint.label}", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(top = 6.dp))
            st.errors.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }

        GlassCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.logo_senza),
                    contentDescription = "Logo Senza Sound",
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Text("Senza Sound", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Audio spasial & equalizer profesional",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Tentang")
            Text(
                "Senza Sound adalah mesin audio buatan SenzaProject yang dirancang untuk membuat " +
                    "suara di perangkat Android terdengar lebih jernih, luas, dan bertenaga. " +
                    "Setiap komponen, mulai dari prosesor ekualisasi hingga kendali dinamika, " +
                    "dikembangkan sebagai satu kesatuan agar hasilnya konsisten di speaker, " +
                    "headphone, maupun Bluetooth.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
            Spacer(Modifier.height(14.dp))
            AboutPoint("Equalizer 10 band", "Kendali presisi 31 Hz hingga 16 kHz dengan profil Film, Musik, Game, dan Suara.")
            AboutPoint("Audio spasial", "Virtualisasi surround yang menyesuaikan diri ke headphone atau speaker.")
            AboutPoint("Kendali dinamika", "Volume leveler, loudness boost, dan limiter untuk suara stabil tanpa pecah.")
            AboutPoint("Perlindungan pendengaran", "Mode volume aman menjaga level keluaran tetap nyaman.")
            Spacer(Modifier.height(10.dp))
            AboutRow("Versi", "1.0.0")
            AboutRow("Pengembang", "SenzaProject")
            AboutRow("Platform", "Android 9 (API 28) ke atas")
            Spacer(Modifier.height(14.dp))
            Text(
                "\u00A9 2026 SenzaProject. Seluruh hak dilindungi.",
                style = MaterialTheme.typography.bodySmall, color = TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
