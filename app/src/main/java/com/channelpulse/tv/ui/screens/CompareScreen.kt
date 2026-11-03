package com.channelpulse.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Delta
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.ui.Metrics
import com.channelpulse.tv.ui.Pulse
import com.channelpulse.tv.ui.Screen
import com.channelpulse.tv.ui.components.Avatar
import com.channelpulse.tv.ui.components.FitRollingNumber
import com.channelpulse.tv.ui.components.PillButton
import com.channelpulse.tv.ui.components.rememberNow
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Lets you pick the second channel for a head-to-head comparison. */
@Composable
fun ComparePickScreen(first: ChannelUi, others: List<ChannelUi>, onPick: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(others.isNotEmpty()) {
        delay(150)
        try { focus.requestFocus() } catch (ignored: Exception) {}
    }
    Column(Modifier.fillMaxSize().padding(start = Screen.sidePadding(), end = Screen.sidePadding(), top = 26.dp)) {
        Text("Compare ${first.title} with…", fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        if (others.isEmpty()) {
            Text("Add another channel to compare it with ${first.title}.", color = Pulse.dim, fontSize = 17.sp)
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(others, key = { it.id }) { c ->
                Card(
                    onClick = { onPick(c.id) },
                    modifier = if (c.id == others.first().id) Modifier.focusRequester(focus) else Modifier,
                    scale = CardDefaults.scale(focusedScale = 1.04f),
                    colors = CardDefaults.colors(
                        containerColor = Pulse.surface,
                        contentColor = Pulse.text,
                        focusedContainerColor = Pulse.surfaceHi,
                        focusedContentColor = Pulse.text,
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.thumb, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val subs = c.stats?.let { if (it.subscribersHidden) "subscribers hidden" else Fmt.compact(it.subscribers) + " subscribers" }.orEmpty()
                            Text(subs, color = Pulse.dim, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Two channels side by side. Only measured numbers: the current gap and how it moved over the
 * readings this TV recorded. No projections.
 */
@Composable
fun CompareScreen(a: ChannelUi, b: ChannelUi, onChange: () -> Unit, onSwap: () -> Unit) {
    val now = rememberNow()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try { focus.requestFocus() } catch (ignored: Exception) {}
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Screen.sidePadding(), end = Screen.sidePadding(), top = 20.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Head to head", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PillButton("Pick another channel", onChange, modifier = Modifier.focusRequester(focus))
            Spacer(Modifier.width(10.dp))
            PillButton("Swap sides", onSwap)
        }
        Spacer(Modifier.height(12.dp))

        // Names
        Row(verticalAlignment = Alignment.CenterVertically) {
            Side(a, Modifier.weight(1f), alignEnd = true)
            Text("VS", color = Pulse.accent, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 28.dp))
            Side(b, Modifier.weight(1f), alignEnd = false)
        }
        Spacer(Modifier.height(12.dp))
        GapBanner(a, b, now)
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(Pulse.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            val sa = a.stats
            val sb = b.stats
            MetricRow(
                "Subscribers",
                sa?.subscribers?.takeIf { !sa.subscribersHidden },
                sb?.subscribers?.takeIf { !sb.subscribersHidden },
                rounded = true,
            )
            MetricRow("Total views", sa?.views, sb?.views)
            ChangeRow("Upload views", Metrics.HOUR, Metrics.uploadViews(a, now, Metrics.HOUR), Metrics.uploadViews(b, now, Metrics.HOUR))
            ChangeRow("Views", Metrics.DAY, Metrics.views(a, now, Metrics.DAY), Metrics.views(b, now, Metrics.DAY))
            ChangeRow("Subscribers", Metrics.DAY, Metrics.subs(a, now, Metrics.DAY), Metrics.subs(b, now, Metrics.DAY))
            MetricRow("Videos", sa?.videoCount, sb?.videoCount, last = true)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Green marks the higher number. Changes come from readings this TV recorded; subscriber counts above 1,000 are rounded by YouTube.",
            color = Pulse.faint, fontSize = 12.sp,
        )
    }
}

@Composable
private fun Side(c: ChannelUi, modifier: Modifier, alignEnd: Boolean) {
    Row(modifier, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        if (!alignEnd) {
            Avatar(c.thumb, 48.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(c.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.handle, color = Pulse.dim, fontSize = 14.sp, maxLines = 1)
        }
        if (alignEnd) {
            Spacer(Modifier.width(14.dp))
            Avatar(c.thumb, 48.dp)
        }
    }
}

/** "X leads by N subscribers", and how the gap moved over the recorded readings. */
@Composable
private fun GapBanner(a: ChannelUi, b: ChannelUi, now: Long) {
    val sa = a.stats
    val sb = b.stats
    val text: String
    var detail: String? = null
    if (sa == null || sb == null) {
        text = "Loading…"
    } else if (sa.subscribersHidden || sb.subscribersHidden) {
        text = "Subscriber gap unavailable: one channel hides its count."
    } else if (sa.subscribers == sb.subscribers) {
        text = "Level on subscribers"
    } else {
        val leader = if (sa.subscribers > sb.subscribers) a else b
        text = "${leader.title} leads by ${Fmt.full(abs(sa.subscribers - sb.subscribers))} subscribers"
        val da: Delta? = Metrics.subs(a, now, Metrics.DAY)
        val db: Delta? = Metrics.subs(b, now, Metrics.DAY)
        if (da != null && db != null) {
            // Positive = the leader pulled further ahead over the recorded span.
            val widened = if (leader === a) da.change - db.change else db.change - da.change
            val span = minOf(da.spanMs, db.spanMs)
            val period = if (span >= Metrics.DAY * 0.9) "the last 24h" else "the last ${Fmt.duration(span)}"
            detail = when {
                widened > 0 -> "Gap widened by ${Fmt.full(widened)} in $period"
                widened < 0 -> "Gap narrowed by ${Fmt.full(-widened)} in $period"
                else -> "Gap unchanged in $period"
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pulse.surfaceHi, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(detail ?: "Gap change appears once this TV has recorded a few readings.", color = Pulse.dim, fontSize = 13.sp)
    }
}

/** A change over a window, labelled with the time actually recorded when that's less than the window. */
@Composable
private fun ChangeRow(name: String, windowMs: Long, a: Delta?, b: Delta?) {
    val spans = listOfNotNull(a?.spanMs, b?.spanMs)
    val window = if (windowMs == Metrics.HOUR) "last hour" else "last 24h"
    val period = spans.minOrNull()?.takeIf { it < windowMs * 0.9 }?.let { "last ${Fmt.duration(it)}" } ?: window
    MetricRow("$name · $period", a?.change, b?.change, signed = true)
}

@Composable
private fun MetricRow(
    label: String,
    a: Long?,
    b: Long?,
    rounded: Boolean = false,
    signed: Boolean = false,
    last: Boolean = false,
) {
    val aWins = a != null && b != null && a > b
    val bWins = a != null && b != null && b > a
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Value(a, aWins, rounded, signed)
        }
        Text(
            label.uppercase(),
            color = Pulse.dim, fontSize = 11.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center,
            modifier = Modifier.width(if (Screen.compact()) 110.dp else 220.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Value(b, bWins, rounded, signed)
        }
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Pulse.line))
}

@Composable
private fun Value(v: Long?, wins: Boolean, rounded: Boolean, signed: Boolean) {
    val color: Color = if (wins) Pulse.up else Pulse.text
    when {
        v == null -> Text("—", color = Pulse.faint, fontSize = 24.sp)
        rounded -> FitRollingNumber(v, maxFontSize = 28.sp, color = color, roundedSigFigs = 3)
        else -> FitRollingNumber(v, maxFontSize = 24.sp, color = color, prefix = if (signed && v > 0) "+" else "", smoothMs = 2000)
    }
}
