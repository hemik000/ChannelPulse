package com.channelpulse.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.ui.Pulse

/**
 * Bar chart of gains per bucket, like the realtime cards in YouTube Studio.
 * Values of -1 mean "no data recorded yet" and are drawn as faint stubs.
 */
@Composable
fun BarChartCard(
    title: String,
    subtitle: String,
    values: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = Pulse.accent,
) {
    val total = values.filter { it > 0 }.sum()
    val hasData = values.any { it >= 0 }
    Column(
        modifier
            .background(Pulse.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), color = Pulse.dim, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(subtitle, color = Pulse.faint, fontSize = 11.sp)
            }
            Text(
                if (hasData) Fmt.full(total) else "Collecting…",
                color = if (hasData) Pulse.text else Pulse.faint,
                fontSize = if (hasData) 22.sp else 14.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        BarChart(values, Modifier.fillMaxWidth().height(70.dp), color)
    }
}

@Composable
fun BarChart(values: List<Long>, modifier: Modifier = Modifier, color: Color = Pulse.accent) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val slot = size.width / values.size
        val barW = (slot * 0.68f).coerceAtLeast(1f)
        val radius = CornerRadius(barW / 3f, barW / 3f)
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2f
            if (v < 0) {
                drawRoundRect(Pulse.line, Offset(x, size.height - 3f), Size(barW, 3f), radius)
            } else {
                val h = (size.height * (v.toFloat() / max)).coerceAtLeast(3f)
                val c = if (i == values.lastIndex) color else color.copy(alpha = 0.55f)
                drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), radius)
            }
        }
    }
}

/** Small line chart for a series of totals. */
@Composable
fun Sparkline(points: List<Long>, modifier: Modifier = Modifier, color: Color = Pulse.accent) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.minOrNull() ?: 0L
        val max = points.maxOrNull() ?: 0L
        val range = (max - min).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (points.size - 1)
        var prev: Offset? = null
        points.forEachIndexed { i, v ->
            val p = Offset(i * stepX, size.height - size.height * ((v - min) / range))
            prev?.let { drawLine(color, it, p, strokeWidth = 3f) }
            prev = p
        }
    }
}
