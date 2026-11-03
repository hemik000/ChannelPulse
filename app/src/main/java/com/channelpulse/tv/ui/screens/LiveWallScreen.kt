package com.channelpulse.tv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.data.PollStatus
import com.channelpulse.tv.ui.Metrics
import com.channelpulse.tv.ui.Pulse
import com.channelpulse.tv.ui.Screen
import com.channelpulse.tv.ui.components.Avatar
import com.channelpulse.tv.ui.components.BarChart
import com.channelpulse.tv.ui.components.DeltaText
import com.channelpulse.tv.ui.components.LiveBadge
import com.channelpulse.tv.ui.components.RollingNumber
import com.channelpulse.tv.ui.components.rememberNow
import kotlinx.coroutines.delay

/**
 * Full-screen counter for an always-on TV. Cycles through all channels;
 * ◀ ▶ switches channel, OK pauses or resumes cycling, Back returns.
 */
@Composable
fun LiveWallScreen(
    channels: List<ChannelUi>,
    startId: String?,
    cycleSeconds: Int,
    status: PollStatus,
) {
    val now = rememberNow()
    val focus = remember { FocusRequester() }
    var index by remember { mutableIntStateOf(channels.indexOfFirst { it.id == startId }.coerceAtLeast(0)) }
    var paused by remember { mutableStateOf(startId != null) }
    val count = channels.size

    LaunchedEffect(Unit) {
        delay(100)
        try { focus.requestFocus() } catch (ignored: Exception) {}
    }
    LaunchedEffect(index, paused, cycleSeconds, count) {
        if (!paused && cycleSeconds > 0 && count > 1) {
            delay(cycleSeconds * 1000L)
            index = (index + 1) % count
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Pulse.bg)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || count == 0) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { index = (index + 1) % count; true }
                    Key.DirectionLeft -> { index = (index - 1 + count) % count; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { paused = !paused; true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable()
    ) {
        if (count == 0) {
            Text("No channels yet", fontSize = 28.sp, color = Pulse.dim, modifier = Modifier.align(Alignment.Center))
        } else {
            val channel = channels[index.coerceIn(0, count - 1)]

            // Clock and status, top right
            Row(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                val ok = status.error == null && status.lastSuccessAt > 0
                Box(Modifier.size(8.dp).background(if (ok) Pulse.up else Pulse.warn, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status.lastSuccessAt > 0) "Updated ${Fmt.ago(status.lastSuccessAt, now)}" else "Connecting…",
                    color = Pulse.faint,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(18.dp))
                Text(Fmt.clock(now), color = Pulse.dim, fontSize = 24.sp)
            }

            AnimatedContent(
                targetState = channel.id,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                label = "wall",
                modifier = Modifier.align(Alignment.Center),
            ) { id ->
                val c = channels.firstOrNull { it.id == id } ?: channel
                WallChannel(c, now)
            }

            // Channel dots and hints, bottom
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (count > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        channels.forEachIndexed { i, _ ->
                            Box(
                                Modifier
                                    .size(if (i == index) 10.dp else 7.dp)
                                    .background(if (i == index) Pulse.text else Pulse.faint, CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val hint = when {
                    count <= 1 -> "Back to exit"
                    paused || cycleSeconds == 0 -> "◀ ▶ switch channel  ·  OK resume cycling  ·  Back to exit"
                    else -> "◀ ▶ switch channel  ·  OK pause  ·  Back to exit"
                }
                Text(hint, color = Pulse.faint, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WallChannel(c: ChannelUi, now: Long) {
    // Sized for a 960 dp TV and scaled to the actual width, so big numbers never overflow.
    val k = Screen.wallScale()
    val st = c.stats
    val lastHour = Metrics.uploadViews(c, now, Metrics.HOUR)
    val live = c.liveVideo
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Screen.sidePadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.thumb, 64.dp * k)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(c.title, fontSize = (34 * k).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.handle, fontSize = (16 * k).sp, color = Pulse.dim)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            st == null -> Text("—", fontSize = (108 * k).sp, fontWeight = FontWeight.Black)
            st.subscribersHidden -> Text("Hidden", fontSize = (80 * k).sp, color = Pulse.dim)
            else -> RollingNumber(st.subscribers, fontSize = (112 * k).sp, fontWeight = FontWeight.Black, roundedSigFigs = 3)
        }
        Text(
            if (st != null && st.subscribers >= 1000 && !st.subscribersHidden) "SUBSCRIBERS  ·  rounded by YouTube" else "SUBSCRIBERS",
            color = Pulse.dim,
            fontSize = (15 * k).sp,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (st != null) RollingNumber(st.views, fontSize = (44 * k).sp, smoothMs = 5000) else Text("—", fontSize = (44 * k).sp)
                Text("TOTAL VIEWS", color = Pulse.dim, fontSize = (12 * k).sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.width(56.dp * k))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val label = Metrics.label(lastHour, Metrics.HOUR)
                if (lastHour != null) {
                    RollingNumber(lastHour.change, fontSize = (44 * k).sp, color = Pulse.up, prefix = if (lastHour.change > 0) "+" else "", smoothMs = 3000)
                    Text(if (label != null && label.contains(" in ")) "UPLOAD VIEWS · LAST ${(lastHour.spanMs / 60_000L).coerceAtLeast(1)} MIN" else "UPLOAD VIEWS · LAST HOUR", color = Pulse.dim, fontSize = (12 * k).sp, letterSpacing = 2.sp)
                } else {
                    DeltaText(null, null, fontSize = (22 * k).toInt())
                    Text("UPLOAD VIEWS · LAST HOUR", color = Pulse.dim, fontSize = (12 * k).sp, letterSpacing = 2.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (live != null) {
            LiveBadge(live.concurrentViewers, big = true)
            Text(live.title, color = Pulse.dim, fontSize = (15 * k).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        } else {
            BarChart(Metrics.uploadBars(c, now), Modifier.fillMaxWidth(0.6f).widthIn(max = 520.dp).height(38.dp * k))
            Text("UPLOAD VIEWS PER 2 MIN · LAST HOUR", color = Pulse.faint, fontSize = (10 * k).sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
