package com.channelpulse.tv.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.channelpulse.tv.AppContainer
import com.channelpulse.tv.Screensaver
import com.channelpulse.tv.data.AppSettings
import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.data.KeyState
import com.channelpulse.tv.data.PollStatus
import com.channelpulse.tv.data.PulseRepository
import com.channelpulse.tv.ui.Pulse
import com.channelpulse.tv.ui.Screen
import com.channelpulse.tv.ui.PillRow
import com.channelpulse.tv.ui.TwoUp
import com.channelpulse.tv.ui.components.Avatar
import com.channelpulse.tv.ui.components.PillButton
import com.channelpulse.tv.ui.components.QrCode
import com.channelpulse.tv.ui.components.TvTextField
import com.channelpulse.tv.ui.components.rememberNow
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
    // The phone section's button sits lower than the channel field, so plain ◀ search misses it; link them.
    val phoneButton = remember { FocusRequester() }
    val channelField = remember { FocusRequester() }
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
            .padding(start = Screen.sidePadding(), end = Screen.sidePadding(), top = 26.dp, bottom = 40.dp)
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        status.error?.let { ErrorBanner(it) }
        Spacer(Modifier.height(18.dp))

        TwoUp(
            spacing = 20.dp,
            // Phone setup
            first = { m -> Section("Set up from your phone", m) {
                val on = settings.phoneSetup
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (on && phoneUrl != null) {
                        QrCode(phoneUrl, 150.dp)
                        Spacer(Modifier.width(18.dp))
                    }
                    Column {
                        Text(
                            if (on) "Scan with a phone on the same Wi-Fi to add channels and paste API keys with a real keyboard."
                            else "Phone setup is off. Start it to add channels and API keys from your phone.",
                            color = Pulse.dim, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            !on -> Text("Nobody on the network can open the setup page while it's off.", color = Pulse.faint, fontSize = 13.sp)
                            phoneUrl != null -> {
                                Text(phoneUrl, fontSize = 14.sp)
                                Text("Turns itself off after 15 minutes without use.", color = Pulse.faint, fontSize = 12.sp)
                            }
                            else -> Text("Connect the TV to a network to use this.", fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // One button whose label flips, so focus stays on it after pressing.
                PillButton(
                    if (on) "Stop phone setup" else "Start phone setup",
                    { container.settings.setPhoneSetup(!on) },
                    modifier = Modifier
                        .focusRequester(phoneButton)
                        .focusProperties { right = channelField },
                    danger = on,
                )
            } },
            // Add channel with the remote
            second = { m -> Section("Add a channel", m) {
                Text("@handle, channel link or UC… ID", color = Pulse.dim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvTextField(
                        value = channelInput,
                        onValueChange = { channelInput = it },
                        placeholder = "@channelhandle",
                        onSubmit = { addChannel() },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(channelField)
                            .focusProperties { left = phoneButton },
                    )
                    Spacer(Modifier.width(10.dp))
                    // Initial focus goes here, not on the text field, so the keyboard doesn't pop up on open.
                    PillButton(if (adding) "…" else "Add", { addChannel() }, modifier = Modifier.focusRequester(focus), selected = true)
                }
                channelMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Pulse.dim, fontSize = 13.sp)
                }
            } },
        )

        Spacer(Modifier.height(20.dp))

        // Channels
        Section("Channels (${channels.size})") {
            if (channels.isEmpty()) Text("No channels yet.", color = Pulse.faint, fontSize = 14.sp)
            channels.forEachIndexed { i, c ->
                // Keyed by channel so focus follows the channel when the list is reordered.
                key(c.id) {
                    val info: @Composable (Modifier) -> Unit = { m ->
                        Row(m, verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c.thumb, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(c.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val subs = c.stats?.let { if (it.subscribersHidden) "subscribers hidden" else Fmt.compact(it.subscribers) + " subscribers" }.orEmpty()
                                Text("${c.handle}  $subs", color = Pulse.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    val buttons: @Composable () -> Unit = {
                        // Always shown (no-ops at the ends) so the focused button never vanishes after a move.
                        PillButton("Move up", { container.settings.moveChannel(c.id, -1) })
                        Spacer(Modifier.width(8.dp))
                        PillButton("Move down", { container.settings.moveChannel(c.id, 1) })
                        Spacer(Modifier.width(8.dp))
                        PillButton("Remove", { removing(i == 0) { repo.removeChannel(c.id) } }, danger = true)
                    }
                    if (Screen.compact()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            info(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Row { buttons() }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            info(Modifier.weight(1f))
                            buttons()
                        }
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
            PillRow {
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
            PillRow {
                options.forEach { (label, sec) ->
                    PillButton(label, { container.settings.setWallCycleSeconds(sec) }, selected = settings.wallCycleSeconds == sec)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("Screensaver") {
            val context = LocalContext.current
            var saverOn by remember { mutableStateOf(Screensaver.isEnabled(context)) }
            var saverMsg by remember { mutableStateOf<String?>(null) }
            Text("Show the live wall as the TV's screensaver when it goes idle.", color = Pulse.dim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            PillRow {
                PillButton("On", { Screensaver.setEnabled(context, true); saverOn = true }, selected = saverOn)
                PillButton("Off", { Screensaver.setEnabled(context, false); saverOn = false }, selected = !saverOn)
                PillButton("Choose in TV settings", {
                    saverMsg = if (Screensaver.openSystemSettings(context)) null
                    else "This TV has no screensaver picker. Look under Settings → Device Preferences → Screen saver."
                })
            }
            Spacer(Modifier.height(8.dp))
            // Re-read every few seconds so it's current after visiting the TV's own settings.
            val tick = rememberNow(3_000L)
            val selected = remember(tick, saverOn) { Screensaver.isSelected(context) }
            when {
                !saverOn -> Text("Off: the TV uses its own screensaver.", color = Pulse.faint, fontSize = 12.sp)
                selected -> Text("The TV uses the live wall as its screensaver.", color = Pulse.up, fontSize = 12.sp)
                Screensaver.canSelect(context) ->
                    Text("Press On to make the live wall the TV's screensaver.", color = Pulse.faint, fontSize = 12.sp)
                else -> {
                    Text(
                        "Pick \"Channel Pulse live wall\" in the TV's screensaver settings. Google TV hides other apps' " +
                            "screensavers there; to let this switch choose it instead, run once from a computer, then press On again:",
                        color = Pulse.faint, fontSize = 12.sp,
                    )
                    Text("adb connect <TV IP>:5555", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Text(Screensaver.GRANT_COMMAND, fontSize = 13.sp)
                }
            }
            saverMsg?.let { Text(it, color = Pulse.warn, fontSize = 12.sp) }
        }

        Spacer(Modifier.height(20.dp))

        Section("Screen care") {
            Text("Burn-in protection moves the dashboard and live wall a few pixels every minute.", color = Pulse.dim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            PillRow {
                PillButton("On", { container.settings.setPixelShift(true) }, selected = settings.pixelShift)
                PillButton("Off", { container.settings.setPixelShift(false) }, selected = !settings.pixelShift)
            }
            Spacer(Modifier.height(16.dp))
            Text("Dim the screen at night. Any button on the remote brightens it for 2 minutes.", color = Pulse.dim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val dimOptions = listOf("Off" to (-1 to 7), "10 PM – 7 AM" to (22 to 7), "11 PM – 7 AM" to (23 to 7), "12 AM – 6 AM" to (0 to 6))
            PillRow {
                dimOptions.forEach { (label, hours) ->
                    val (start, end) = hours
                    val on = settings.nightDimStart == start && (start < 0 || settings.nightDimEnd == end)
                    PillButton(label, { container.settings.setNightDim(start, end) }, selected = on)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("About the numbers") {
            listOf(
                "Total views, video views, likes, comments and live viewers are exact, straight from the YouTube Data API.",
                "Subscriber counts are exact below 1,000. Above that, YouTube rounds them down to 3 digits for every app; the faint digits show where.",
                "\"Last hour\" counts views on each channel's newest uploads. YouTube updates video views often but a channel's total views only now and then.",
                "The 60-minute and 48-hour charts are built from readings this TV records, so they fill in while the app is open.",
                "YouTube Studio's own real-time panel isn't available through any public API.",
            ).forEach {
                Text("•  $it", color = Pulse.dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/** When focus enters a section, the whole section scrolls into view, not just the focused button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Section(title: String, modifier: Modifier = Modifier.fillMaxWidth(), content: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .bringIntoViewRequester(requester)
            .onFocusChanged { if (it.hasFocus) scope.launch { requester.bringIntoView() } }
            .background(Pulse.surface, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(title.uppercase(), color = Pulse.dim, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
