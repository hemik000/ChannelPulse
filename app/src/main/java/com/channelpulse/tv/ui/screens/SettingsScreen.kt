package com.channelpulse.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.channelpulse.tv.AppContainer
import com.channelpulse.tv.data.AppSettings
import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.data.KeyState
import com.channelpulse.tv.data.PollStatus
import com.channelpulse.tv.data.PulseRepository
import com.channelpulse.tv.ui.Pulse
import com.channelpulse.tv.ui.components.Avatar
import com.channelpulse.tv.ui.components.PillButton
import com.channelpulse.tv.ui.components.QrCode
import com.channelpulse.tv.ui.components.TvTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: AppSettings,
    channels: List<ChannelUi>,
    status: PollStatus,
    keyStates: Map<String, KeyState>,
    phoneUrl: String?,
) {
    val repo = container.repo
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Move focus off a row before it disappears, so the remote doesn't lose its place.
    fun removing(isFirst: Boolean, action: () -> Unit) {
        focusManager.moveFocus(if (isFirst) FocusDirection.Down else FocusDirection.Up)
        action()
    }

    var channelInput by remember { mutableStateOf("") }
    var channelMsg by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var keyMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(150)
        try { focus.requestFocus() } catch (ignored: Exception) {}
    }

    fun addChannel() {
        if (adding || channelInput.isBlank()) return
        adding = true
        channelMsg = "Looking up…"
        scope.launch {
            val result = repo.addChannel(channelInput)
            channelMsg = result.fold(
                onSuccess = { channelInput = ""; "Added ${it.title}." },
                onFailure = { it.message ?: "Couldn't add that channel." },
            )
            adding = false
        }
    }

    fun addKey() {
        val k = keyInput.trim()
        keyMsg = when {
            k.isEmpty() -> "Type or paste a key."
            repo.addApiKey(k) -> { keyInput = ""; repo.refreshNow(); "Key added." }
            else -> "That key is already saved."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 48.dp, end = 48.dp, top = 26.dp, bottom = 40.dp)
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        status.error?.let { ErrorBanner(it) }
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Phone setup
            Section("Set up from your phone", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (phoneUrl != null) {
                        QrCode(phoneUrl, 150.dp)
                        Spacer(Modifier.width(18.dp))
                    }
                    Column {
                        Text(
                            "Scan with a phone on the same Wi-Fi to add channels and paste API keys with a real keyboard.",
                            color = Pulse.dim, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(phoneUrl ?: "Connect the TV to a network to use this.", fontSize = 14.sp)
                    }
                }
            }

            // Add channel with the remote
            Section("Add a channel", Modifier.weight(1f)) {
                Text("@handle, channel link or UC… ID", color = Pulse.dim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvTextField(
                        value = channelInput,
                        onValueChange = { channelInput = it },
                        placeholder = "@channelhandle",
                        onSubmit = { addChannel() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    // Initial focus goes here, not on the text field, so the keyboard doesn't pop up on open.
                    PillButton(if (adding) "…" else "Add", { addChannel() }, modifier = Modifier.focusRequester(focus), selected = true)
                }
                channelMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Pulse.dim, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Channels
        Section("Channels (${channels.size})") {
            if (channels.isEmpty()) Text("No channels yet.", color = Pulse.faint, fontSize = 14.sp)
            channels.forEachIndexed { i, c ->
                // Keyed by channel so focus follows the channel when the list is reordered.
                key(c.id) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.thumb, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val subs = c.stats?.let { if (it.subscribersHidden) "subscribers hidden" else Fmt.compact(it.subscribers) + " subscribers" }.orEmpty()
                            Text("${c.handle}  $subs", color = Pulse.dim, fontSize = 12.sp, maxLines = 1)
                        }
                        // Always shown (no-ops at the ends) so the focused button never vanishes after a move.
                        PillButton("Move up", { container.settings.moveChannel(c.id, -1) })
                        Spacer(Modifier.width(8.dp))
                        PillButton("Move down", { container.settings.moveChannel(c.id, 1) })
                        Spacer(Modifier.width(8.dp))
                        PillButton("Remove", { removing(i == 0) { repo.removeChannel(c.id) } }, danger = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // API keys
        Section("YouTube API keys") {
            Text(
                "Every key from a separate Google Cloud project adds 10,000 units a day. More keys = faster refresh.",
                color = Pulse.dim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            settings.apiKeys.forEachIndexed { i, key ->
                val ks = keyStates[key] ?: KeyState()
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.maskKey(key), fontSize = 15.sp)
                        val (text, color) = when {
                            ks.invalidReason != null -> "Rejected: ${ks.invalidReason}" to Pulse.down
                            ks.exhausted -> "Out of quota until midnight Pacific" to Pulse.warn
                            else -> "≈ ${Fmt.full(ks.used.toLong())} of 10,000 units used today" to Pulse.dim
                        }
                        Text(text, color = color, fontSize = 12.sp, maxLines = 2)
                    }
                    PillButton("Remove", { removing(i == 0) { container.settings.removeApiKey(key) } }, danger = true)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = "AIza…  (pasting from the phone page is easier)",
                    onSubmit = { addKey() },
                    keyboardType = KeyboardType.Ascii,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PillButton("Add key", { addKey() }, selected = true)
            }
            keyMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Pulse.dim, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Refresh speed
        Section("Refresh speed") {
            val options = listOf("Auto" to 0, "10s" to 10, "15s" to 15, "30s" to 30, "1m" to 60, "2m" to 120, "5m" to 300)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (label, sec) ->
                    PillButton(label, { container.settings.setRefreshSeconds(sec) }, selected = settings.refreshSeconds == sec)
                }
            }
            Spacer(Modifier.height(10.dp))
            val plan: PulseRepository.Plan = repo.plan(settings)
            Text(
                "Every ${plan.intervalSec}s ≈ ${Fmt.full(plan.unitsPerDay.toLong())} units/day of " +
                    "${Fmt.full(plan.capacityPerDay.toLong())} available from ${plan.healthyKeys} working " +
                    (if (plan.healthyKeys == 1) "key." else "keys."),
                color = if (plan.unitsPerDay > plan.capacityPerDay) Pulse.warn else Pulse.dim,
                fontSize = 13.sp,
            )
            Text(
                "Auto picks the fastest rate that lasts the whole day. Refreshing pauses while the app is closed.",
                color = Pulse.faint, fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Live wall
        Section("Live wall: switch channel every") {
            val options = listOf("10s" to 10, "20s" to 20, "30s" to 30, "1m" to 60, "Never" to 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (label, sec) ->
                    PillButton(label, { container.settings.setWallCycleSeconds(sec) }, selected = settings.wallCycleSeconds == sec)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("About the numbers") {
            listOf(
                "Total views, video views, likes, comments and live viewers are exact, straight from the YouTube Data API.",
                "Subscriber counts are exact below 1,000. Above that, YouTube rounds them down to 3 digits for every app; the faint digits show where.",
                "The 60-minute and 48-hour charts are built from readings this TV records, so they fill in while the app is open.",
                "YouTube Studio's own real-time panel isn't available through any public API.",
            ).forEach {
                Text("•  $it", color = Pulse.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(Pulse.surface, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(title.uppercase(), color = Pulse.dim, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
