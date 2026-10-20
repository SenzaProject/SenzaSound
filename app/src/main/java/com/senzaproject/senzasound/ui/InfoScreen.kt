package com.senzaproject.senzasound.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.senzasound.R

@Composable
private fun StatusRow(label: String, state: String, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(state, color = if (active) Accent else TextDim, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun InfoScreen(vm: SoundViewModel, padding: PaddingValues) {
    val st by vm.engine.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val endpoint by vm.endpoint.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
    } catch (t: Throwable) {
        "1.0"
    }
    val appSessions = st.sessions.count { it != 0 }

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
            SectionTitle("Status mesin", "Output saat ini: ${endpoint.label}")
            StatusRow("Efek global (semua suara)", if (st.globalAttached) "● Terpasang" else "○ Tidak terpasang", st.globalAttached)
            StatusRow(
                "Pemutar terhubung",
                if (appSessions > 0) "● $appSessions sesi" else "○ Belum ada",
                appSessions > 0,
            )
            st.errors.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(top = 2.dp))
            }
        }

        GlassCard {
            SectionTitle(
                "Kemampuan perangkat",
                if (caps.probed) "Hasil uji otomatis saat aplikasi dibuka" else "Sedang menguji…",
            )
            StatusRow("Prosesor EQ 32 band & dinamika", if (caps.dynamics) "● Tersedia" else "○ Tidak tersedia", caps.dynamics)
            StatusRow("Penguat bass", if (caps.bass) "● Tersedia" else "○ Tidak tersedia", caps.bass)
            StatusRow("Virtualisasi spasial", if (caps.virtualizer) "● Tersedia" else "○ Tidak tersedia", caps.virtualizer)
            StatusRow("Penguat kenyaringan", if (caps.loudness) "● Tersedia" else "○ Tidak tersedia", caps.loudness)
            StatusRow("EQ cadangan", if (caps.legacyEq) "● Siaga" else "○ Tidak tersedia", caps.legacyEq)
            StatusRow("Efek native (vendor)", if (caps.native) "● Aktif" else "○ Tidak dipasang", caps.native)
            Text(
                "EQ cadangan hanya dipakai bila prosesor utama tidak tersedia. " +
                    "Efek native bersifat opsional dan aktif jika modul vendor Senza Sound terpasang.",
                style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(top = 8.dp),
            )
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo_senza),
                    contentDescription = "Logo Senza Sound",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Senza Sound", style = MaterialTheme.typography.titleLarge)
                    Text("Versi $version", style = MaterialTheme.typography.bodyMedium, color = TextDim)
                    Text("Developer: SenzaProject", style = MaterialTheme.typography.bodyMedium, color = TextDim)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Senza Sound menghadirkan pengalaman audio yang jernih, luas, dan bertenaga. " +
                    "Equalizer 32 band, profil suara adaptif, penguat dialog, virtualisasi spasial, " +
                    "kendali dinamika, dan perlindungan pendengaran bekerja bersama dalam satu mesin " +
                    "audio yang dirancang oleh SenzaProject.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
