package com.channelpulse.tv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.data.PulseRepository
import com.channelpulse.tv.data.VideoSample
import com.channelpulse.tv.data.VideoStats
import com.channelpulse.tv.ui.Metrics
import com.channelpulse.tv.ui.Pulse
import com.channelpulse.tv.ui.Screen
import com.channelpulse.tv.ui.TwoUp
import com.channelpulse.tv.ui.components.Avatar
import com.channelpulse.tv.ui.components.BarChartCard
import com.channelpulse.tv.ui.components.DeltaText
import com.channelpulse.tv.ui.components.LiveBadge
import com.channelpulse.tv.ui.components.PillButton
import com.channelpulse.tv.ui.components.RollingNumber
import com.channelpulse.tv.ui.components.StatTile
import com.channelpulse.tv.ui.components.Thumbnail
import com.channelpulse.tv.ui.components.rememberNow
import kotlinx.coroutines.delay

@Composable
fun ChannelDetailScreen(
    channel: ChannelUi,
    videoHistory: (String) -> List<VideoSample>,
    onWall: () -> Unit,
    onCompare: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
) {
    val now = rememberNow()
    val st = channel.stats
    val focus = remember { FocusRequester() }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        try { focus.requestFocus() } catch (ignored: Exception) {}
    }
    LaunchedEffect(confirmRemove) {
        if (confirmRemove) {
            delay(4000)
            confirmRemove = false
        }
    }

    val lastHour = Metrics.views(channel, now, Metrics.HOUR)
    val lastDay = Metrics.views(channel, now, Metrics.DAY)
    val subsDay = Metrics.subs(channel, now, Metrics.DAY)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Screen.sidePadding(), end = Screen.sidePadding(), top = 26.dp, bottom = 32.dp)
    ) {
        // Header: buttons beside the title on a TV, below it on narrow screens.
        val title: @Composable (Modifier) -> Unit = { m ->
            Row(m, verticalAlignment = Alignment.CenterVertically) {
                Avatar(channel.thumb, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(channel.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        channel.liveVideo?.let {
                            Spacer(Modifier.width(12.dp))
                            LiveBadge(it.concurrentViewers)
                        }
                    }
                    val updated = st?.let { " · updated " + Fmt.ago(it.fetchedAt, now) }.orEmpty()
                    Text(channel.handle + updated, color = Pulse.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val buttons: @Composable () -> Unit = {
            PillButton("Live wall", onWall, modifier = Modifier.focusRequester(focus))
            Spacer(Modifier.width(10.dp))
            PillButton("Compare", onCompare)
            Spacer(Modifier.width(10.dp))
            PillButton("Refresh", onRefresh)
            Spacer(Modifier.width(10.dp))
            PillButton(
                if (confirmRemove) "Press again to remove" else "Remove",
                onClick = { if (confirmRemove) onRemove() else confirmRemove = true },
                danger = true,
            )
        }
        if (Screen.compact()) {
            title(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) { buttons() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                title(Modifier.weight(1f))
                buttons()
            }
        }

        Spacer(Modifier.height(20.dp))

        // Big numbers: one row on a TV, two by two on narrow screens.
        val tiles: List<Pair<Float, @Composable (Modifier) -> Unit>> = listOf(
            1.2f to { m ->
                StatTile(
                    "Subscribers",
                    m,
                    note = when {
                        st == null -> null
                        st.subscribersHidden -> "Hidden by the channel"
                        st.subscribers >= 1000 -> "YouTube rounds this to 3 digits"
                        else -> "Exact count"
                    },
                ) {
                    when {
                        st == null -> Text("—", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        st.subscribersHidden -> Text("Hidden", fontSize = 36.sp, color = Pulse.dim)
                        else -> RollingNumber(st.subscribers, fontSize = 44.sp, roundedSigFigs = 3)
                    }
                    DeltaText(Metrics.label(subsDay, Metrics.DAY)?.let { "$it last 24h" }, Metrics.positive(subsDay))
                }
            },
            1.2f to { m ->
                StatTile("Total views", m, note = "Exact count") {
                    if (st != null) RollingNumber(st.views, fontSize = 44.sp, smoothMs = 4000)
                    else Text("—", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    DeltaText(Metrics.label(lastHour, Metrics.HOUR)?.let { "$it last hour" }, Metrics.positive(lastHour))
                }
            },
            1f to { m ->
                StatTile("Last 24 hours", m, note = "Views recorded on this TV") {
                    val d = lastDay
                    if (d != null) RollingNumber(d.change, fontSize = 30.sp, prefix = if (d.change > 0) "+" else "", smoothMs = 2000)
                    else Text("Collecting…", fontSize = 18.sp, color = Pulse.faint, modifier = Modifier.padding(vertical = 8.dp))
                    val rate = lastHour?.let { if (it.spanMs > 0) it.change * Metrics.HOUR / it.spanMs else null }
                    Text(rate?.let { "≈ ${Fmt.full(it)} views/hour" } ?: " ", color = Pulse.dim, fontSize = 13.sp)
                }
            },
            0.8f to { m ->
                StatTile("Videos", m) {
                    if (st != null) RollingNumber(st.videoCount, fontSize = 30.sp) else Text("—", fontSize = 30.sp)
                }
            },
        )
        if (Screen.compact()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { (_, tile) -> tile(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                tiles.forEach { (weight, tile) -> tile(Modifier.weight(weight)) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Realtime-style charts
        TwoUp(
            spacing = 16.dp,
            first = { m ->
                BarChartCard(
                    title = "Upload views · last 60 minutes",
                    subtitle = "Newest ${PulseRepository.VIDEOS_PER_CHANNEL} uploads, per 2 minutes, recorded on this TV",
                    values = Metrics.uploadBars(channel, now),
                    modifier = m,
                )
            },
            second = { m ->
                BarChartCard(
                    title = "Views · last 48 hours",
                    subtitle = "Per hour, recorded while the app is open",
                    values = Metrics.last48hBars(channel, now),
                    modifier = m,
                )
            },
        )

        Spacer(Modifier.height(20.dp))
        Text("LATEST UPLOADS", color = Pulse.dim, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))

        if (channel.videos.isEmpty()) {
            Text(if (st == null) "Loading…" else "No public videos found.", color = Pulse.faint, fontSize = 15.sp)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                // Room for a focused card to grow without being clipped.
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
            ) {
                items(channel.videos, key = { it.id }) { v ->
                    VideoCard(v, videoHistory(v.id), now)
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoStats, history: List<VideoSample>, now: Long) {
    val context = LocalContext.current
    val recent = Metrics.video(history, now, Metrics.HOUR)
    Card(
        onClick = {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (ignored: Exception) {
            }
        },
        modifier = Modifier.width(250.dp),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(
            containerColor = Pulse.surface,
            contentColor = Pulse.text,
            focusedContainerColor = Pulse.surfaceHi,
            focusedContentColor = Pulse.text,
        ),
    ) {
        Box {
            Thumbnail(video.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            when {
                video.isLive -> LiveBadge(video.concurrentViewers, Modifier.padding(8.dp))
                video.isUpcoming -> Text(
                    "UPCOMING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(video.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(36.dp))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(video.views, fontSize = 20.sp, smoothMs = 3000)
                Text(" views", color = Pulse.dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            DeltaText(Metrics.label(recent, Metrics.HOUR)?.let { "$it last hour" }, Metrics.positive(recent), fontSize = 12)
            Text(
                "${Fmt.compact(video.likes)} likes · ${Fmt.compact(video.comments)} comments · ${Fmt.ago(video.publishedAt, now)}",
                color = Pulse.faint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
