package com.channelpulse.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** How long a remote key press keeps the screen bright during night dimming. */
const val UNDIM_MS = 2L * 60_000L

/**
 * Protects TVs left on all day: [shift] moves the content around a small circle (one step a minute),
 * and between [dimStart] and [dimEnd] o'clock the screen is darkened unless a key was pressed recently.
 */
@Composable
fun ScreenCare(
    shift: Boolean,
    dimStart: Int,
    dimEnd: Int,
    lastInputAt: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val now = rememberNow(15_000L)
    val step = ((now / 60_000L) % 12).toInt()
    val angle = step * (2 * Math.PI / 12)
    val dx by animateFloatAsState(if (shift) (cos(angle) * 6).toFloat() else 0f, tween(3000), label = "shiftX")
    val dy by animateFloatAsState(if (shift) (sin(angle) * 6).toFloat() else 0f, tween(3000), label = "shiftY")
    val dim = isNight(now, dimStart, dimEnd) && now - lastInputAt > UNDIM_MS
    val shade by animateFloatAsState(if (dim) 0.65f else 0f, tween(1500), label = "dim")

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().offset(dx.dp, dy.dp)) { content() }
        if (shade > 0f) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = shade }.background(Color.Black))
        }
    }
}

fun isNight(now: Long, start: Int, end: Int): Boolean {
    if (start < 0 || start == end) return false
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return if (start < end) hour in start until end else hour >= start || hour < end
}
