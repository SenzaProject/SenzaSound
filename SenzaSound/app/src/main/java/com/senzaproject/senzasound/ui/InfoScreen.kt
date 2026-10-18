package com.senzaproject.senzasound.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
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
            StatusRow("EQ 10 band + kompresor (DynamicsProcessing)", st.dynamics)
            StatusRow("EQ cadangan (Equalizer bawaan)", st.legacyEq)
            StatusRow("Bass enhancer", st.bass)
            StatusRow("Surround virtualizer", st.virtualizer)
            StatusRow("Loudness boost", st.loudness)
            Text("Output saat ini: ${endpoint.label}", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(top = 6.dp))
            st.errors.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
        }

        GlassCard {
            SectionTitle("Agar berlaku di aplikasi lain")
            Text(
                "1. Hidupkan Senza Sound di Beranda dan izinkan notifikasi (menjaga mesin tetap hidup).\n" +
                    "2. Di pemutar musik, buka Pengaturan → Equalizer/Efek audio, lalu pilih Senza Sound jika ada pilihan.\n" +
                    "3. Matikan penghemat baterai untuk Senza Sound di pengaturan sistem.\n" +
                    "4. Pemutar yang mengumumkan sesi audio otomatis terpasang. Pada beberapa ROM, efek global (sesi 0) " +
                    "diblokir; gunakan tab Pemutar untuk memastikan efek bekerja.\n" +
                    "5. Aplikasi DRM/streaming tertentu (mis. Netflix) dapat melewati efek pihak ketiga.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
        }

        GlassCard {
            SectionTitle("Tentang")
            Text("Senza Sound", style = MaterialTheme.typography.titleMedium)
            Text("Versi 1.0.0", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            Text("Developer: SenzaProject", style = MaterialTheme.typography.bodyMedium, color = TextDim)
            Spacer(Modifier.height(8.dp))
            Text(
                "Senza Sound memakai mesin audio Android sendiri (android.media.audiofx) dan bukan produk Dolby, " +
                    "serta tidak mengandung kode atau merek Dolby.",
                style = MaterialTheme.typography.bodySmall, color = TextDim,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
