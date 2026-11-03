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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.channelpulse.tv.data.AppSettings
import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.data.PollStatus
import com.channelpulse.tv.ui.DashFocus
import com.channelpulse.tv.ui.Metrics
import com.channelpulse.tv.ui.Pulse
import com.channelpulse.tv.ui.components.Avatar
import com.channelpulse.tv.ui.components.BarChart
import com.channelpulse.tv.ui.components.DeltaText
import com.channelpulse.tv.ui.components.PillButton
import com.channelpulse.tv.ui.components.QrCode
import com.channelpulse.tv.ui.components.RollingNumber
import com.channelpulse.tv.ui.components.rememberNow
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    channels: List<ChannelUi>,
    status: PollStatus,
    settings: AppSettings,
    phoneUrl: String?,
    gridState: LazyGridState,
    focusTarget: DashFocus,
    onOpen: (String) -> Unit,
    onWall: () -> Unit,
    onSettings: () -> Unit,
) {
    val now = rememberNow()
    val firstFocus = remember { FocusRequester() }
    val ready = settings.apiKeys.isNotEmpty() && channels.isNotEmpty()
    // Card, header button or first card to focus; falls back if that channel was removed.
    val focusChannelId = (focusTarget as? DashFocus.Channel)?.id?.takeIf { id -> channels.any { it.id == id } }
        ?: channels.firstOrNull()?.id
    val focusHeader = !ready || focusTarget == DashFocus.Settings || focusTarget == DashFocus.Wall

    LaunchedEffect(ready) {
        delay(150)
        try { firstFocus.requestFocus() } catch (ignored: Exception) {}
    }

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 26.dp)) {
        Header(
            status = status,
            now = now,
            showWall = ready,
            onWall = onWall,
            onSettings = onSettings,
            wallFocus = if (ready && focusTarget == DashFocus.Wall) firstFocus else null,
            settingsFocus = if (!ready || focusTarget == DashFocus.Settings) firstFocus else null,
        )
        status.error?.let { ErrorBanner(it) }
        Spacer(Modifier.height(14.dp))

        when {
            settings.apiKeys.isEmpty() -> SetupPanel(
                title = "Connect the YouTube Data API",
                lines = listOf(
                    "1.  Scan the code with your phone (same Wi-Fi as this TV).",
                    "2.  Follow the steps on your phone to create a free API key and paste it.",
                    "3.  Add channels by @handle or link. Add as many as you like.",
                ),
                phoneUrl = phoneUrl,
                phoneSetupOn = settings.phoneSetup,
            )
            channels.isEmpty() -> SetupPanel(
                title = "Add your first channel",
                lines = listOf(
                    "Scan the code and type a @handle, a channel link or a UC… channel ID.",
                    "You can paste several at once, one per line.",
                    "Or open Settings to type one with the remote.",
                ),
                phoneUrl = phoneUrl,
                phoneSetupOn = settings.phoneSetup,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(channels, key = { it.id }) { c ->
                    ChannelCard(
                        channel = c,
                        now = now,
                        onClick = { onOpen(c.id) },
                        modifier = if (!focusHeader && c.id == focusChannelId) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
                item(key = "add") { AddCard(onSettings) }
            }
        }
    }
}

@Composable
private fun Header(
    status: PollStatus,
    now: Long,
    showWall: Boolean,
    onWall: () -> Unit,
    onSettings: () -> Unit,
    wallFocus: FocusRequester?,
    settingsFocus: FocusRequester?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(Pulse.accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text("Channel Pulse", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(18.dp))
        Text(statusLine(status, now), color = Pulse.dim, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text(Fmt.clock(now), color = Pulse.dim, fontSize = 18.sp)
        Spacer(Modifier.width(18.dp))
        if (showWall) {
            PillButton("Live wall", onWall, modifier = if (wallFocus != null) Modifier.focusRequester(wallFocus) else Modifier)
            Spacer(Modifier.width(10.dp))
        }
        PillButton(
            "Settings",
            onSettings,
            modifier = if (settingsFocus != null) Modifier.focusRequester(settingsFocus) else Modifier,
        )
    }
}

private fun statusLine(s: PollStatus, now: Long): String {
    if (s.lastSuccessAt == 0L) return if (s.polling) "Connecting…" else ""
    val keys = if (s.healthyKeys == 1) "1 key" else "${s.healthyKeys} keys"
    return "Updated ${Fmt.ago(s.lastSuccessAt, now)}  ·  every ${s.intervalSec}s  ·  $keys"
}

@Composable
fun ErrorBanner(text: String) {
    Text(
        text,
        color = Color(0xFFFFD7D7),
        fontSize = 14.sp,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(Color(0xFF3A1E22), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun ChannelCard(channel: ChannelUi, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val st = channel.stats
    val lastHour = Metrics.views(channel, now, Metrics.HOUR)
    val live = channel.liveVideo
    val viewers: Long? = live?.concurrentViewers
    Card(
        onClick = onClick,
        modifier = modifier.height(212.dp),
        colors = CardDefaults.colors(
            containerColor = Pulse.surface,
            contentColor = Pulse.text,
            focusedContainerColor = Pulse.surfaceHi,
            focusedContentColor = Pulse.text,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(channel.thumb, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(channel.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(channel.handle, color = Pulse.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (live != null) {
                    Box(Modifier.size(10.dp).background(Pulse.accent, CircleShape))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("SUBSCRIBERS", color = Pulse.dim, fontSize = 10.sp, letterSpacing = 1.sp)
            when {
                st == null -> Text("—", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                st.subscribersHidden -> Text("Hidden", fontSize = 26.sp, color = Pulse.dim)
                else -> RollingNumber(st.subscribers, fontSize = 32.sp, roundedSigFigs = 3)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (viewers != null) "WATCHING NOW" else "TOTAL VIEWS", color = Pulse.dim, fontSize = 10.sp, letterSpacing = 1.sp)
                    when {
                        viewers != null ->
                            RollingNumber(viewers, fontSize = 17.sp, color = Pulse.accent, smoothMs = 2500)
                        st != null -> RollingNumber(st.views, fontSize = 17.sp, smoothMs = 4000)
                        else -> Text("—", fontSize = 17.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LAST HOUR", color = Pulse.dim, fontSize = 10.sp, letterSpacing = 1.sp)
                    DeltaText(Metrics.label(lastHour, Metrics.HOUR), Metrics.positive(lastHour), fontSize = 14)
                }
            }
            Spacer(Modifier.height(10.dp))
            BarChart(Metrics.lastHourBars(channel, now), Modifier.fillMaxWidth().height(24.dp))
        }
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.height(212.dp),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Pulse.dim,
            focusedContainerColor = Pulse.surfaceHi,
            focusedContentColor = Pulse.text,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Pulse.surface.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", fontSize = 44.sp, fontWeight = FontWeight.Light)
                Text("Add channel", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SetupPanel(title: String, lines: List<String>, phoneUrl: String?, phoneSetupOn: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .background(Pulse.surface, RoundedCornerShape(20.dp))
            .padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (phoneUrl != null) {
            QrCode(phoneUrl, 210.dp)
            Spacer(Modifier.width(32.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            lines.forEach {
                Text(it, color = Pulse.dim, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (phoneUrl != null) {
                Text("Or open on any device on this network:", color = Pulse.faint, fontSize = 13.sp)
                Text(phoneUrl, color = Pulse.text, fontSize = 16.sp)
            } else if (!phoneSetupOn) {
                Text("Phone setup is off. Start it in Settings to use your phone.", color = Pulse.warn, fontSize = 14.sp)
            } else {
                Text("Connect this TV to Wi-Fi or Ethernet to use phone setup.", color = Pulse.warn, fontSize = 14.sp)
            }
        }
    }
}
