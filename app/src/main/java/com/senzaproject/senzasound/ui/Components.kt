package com.senzaproject.senzasound.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.senzaproject.senzasound.data.Eq
import com.senzaproject.senzasound.dsp.EqMath
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAU = 6.2831855f

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface1)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
    }
}

/** Slider 0..1 dengan judul dan nilai persen. */
@Composable
fun FxSlider(
    title: String,
    sub: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = TextDim)
            }
            Text("${(value * 100).roundToInt()}%", color = Accent, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Surface2,
            ),
        )
    }
}

/** Visual radial yang berdenyut mengikuti spektrum audio. */
@Composable
fun HeroVisualizer(bands: FloatArray, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hero")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.26f
        val n = max(bands.size, 1)
        val dim = if (active) 1f else 0.35f
        for (k in 0..2) {
            val rr = r * (1.05f + 0.3f * k) + r * 0.04f * sin(phase * TAU + k)
            drawCircle(
                color = Accent.copy(alpha = (0.32f - 0.09f * k) * dim),
                radius = rr,
                center = c,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(Accent2.copy(alpha = 0.55f * dim), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c,
        )
        for (i in 0 until n) {
            val ang = (i.toFloat() / n) * TAU - TAU / 4f
            val idle = 0.12f + 0.08f * sin(phase * TAU + i * 0.6f)
            val real = if (i < bands.size) bands[i] else 0f
            val v = if (active) max(real, idle) else idle * 0.6f
            val d0 = r * 1.0f
            val d1 = d0 + r * 0.14f + r * 0.7f * v
            drawLine(
                brush = Brush.linearGradient(listOf(Accent, Accent2)),
                start = Offset(c.x + cos(ang) * d0, c.y + sin(ang) * d0),
                end = Offset(c.x + cos(ang) * d1, c.y + sin(ang) * d1),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
                alpha = dim,
            )
        }
    }
}

/** Grafik respons frekuensi EQ. */
@Composable
fun EqCurve(gains: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val range = Eq.MAX_DB + 3f
        val midY = h / 2f
        for (db in intArrayOf(-12, -6, 0, 6, 12)) {
            val y = midY - db / range * midY
            drawLine(
                color = Color(if (db == 0) 0x44FFFFFF else 0x18FFFFFF),
                start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1.dp.toPx(),
            )
        }
        val pts = 96
        val line = Path()
        val fill = Path()
        fill.moveTo(0f, midY)
        for (i in 0 until pts) {
            val f = 20f * 1000f.pow(i / (pts - 1f))
            val db = EqMath.responseDb(gains, f).coerceIn(-range, range)
            val x = w * i / (pts - 1f)
            val y = midY - db / range * midY
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            fill.lineTo(x, y)
        }
        fill.lineTo(w, midY)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Accent.copy(alpha = 0.35f), Accent2.copy(alpha = 0.05f))))
        drawPath(line, AccentBrush, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Slider vertikal untuk gain band EQ (-12..+12 dB). Ketuk dua kali untuk reset ke 0. */
@Composable
fun VerticalGainSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    val cb by rememberUpdatedState(onChange)
    val pad = 16f

    fun fromY(y: Float): Float {
        val frac = ((y - pad) / (heightPx - 2 * pad)).coerceIn(0f, 1f)
        val raw = (0.5f - frac) * 2f * Eq.MAX_DB
        return (raw * 2f).roundToInt() / 2f
    }

    Canvas(
        modifier
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(enabled) {
                if (enabled) {
                    detectVerticalDragGestures(
                        onDragStart = { cb(fromY(it.y)) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            cb(fromY(change.position.y))
                        },
                    )
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(onDoubleTap = { cb(0f) }, onTap = { cb(fromY(it.y)) })
                }
            },
    ) {
        val cx = size.width / 2f
        val top = pad
        val bottom = size.height - pad
        val mid = (top + bottom) / 2f
        val ty = mid - value / (2f * Eq.MAX_DB) * (bottom - top)
        val trackW = 4.dp.toPx()
        drawLine(Surface2, Offset(cx, top), Offset(cx, bottom), strokeWidth = trackW, cap = StrokeCap.Round)
        drawLine(Color(0x55FFFFFF), Offset(cx - 9.dp.toPx(), mid), Offset(cx + 9.dp.toPx(), mid), strokeWidth = 1.dp.toPx())
        drawLine(
            if (enabled) Accent else Color(0xFF55607F),
            Offset(cx, mid), Offset(cx, ty), strokeWidth = trackW, cap = StrokeCap.Round,
        )
        drawCircle(if (enabled) Color.White else Color(0xFF8A93B0), 8.dp.toPx(), Offset(cx, ty))
        drawCircle(if (enabled) Accent else Color(0xFF55607F), 5.dp.toPx(), Offset(cx, ty))
    }
}

/** Batang spektrum. */
@Composable
fun SpectrumBars(bands: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = max(bands.size, 1)
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val v = if (i < bands.size) bands[i].coerceIn(0f, 1f) else 0f
            val bh = max(3.dp.toPx(), v * size.height)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Accent2, Accent), startY = size.height - bh, endY = size.height),
                topLeft = Offset(i * (bw + gap), size.height - bh),
                size = Size(bw, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2f, bw / 2f),
            )
        }
    }
}
