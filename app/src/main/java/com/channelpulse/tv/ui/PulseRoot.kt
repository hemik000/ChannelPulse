package com.channelpulse.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.channelpulse.tv.ui.components.ScreenCare
import com.channelpulse.tv.AppContainer
import com.channelpulse.tv.ui.screens.ChannelDetailScreen
import com.channelpulse.tv.ui.screens.ComparePickScreen
import com.channelpulse.tv.ui.screens.CompareScreen
import com.channelpulse.tv.ui.screens.DashboardScreen
import com.channelpulse.tv.ui.screens.LiveWallScreen
import com.channelpulse.tv.ui.screens.SettingsScreen

sealed interface Route {
    data object Dashboard : Route
    data class Detail(val channelId: String) : Route
    data class Wall(val channelId: String?) : Route
    data object Settings : Route
    data class ComparePick(val firstId: String) : Route
    data class Compare(val aId: String, val bId: String) : Route
}

/** What gets focus when the dashboard is shown again. */
sealed interface DashFocus {
    data object First : DashFocus
    data object Wall : DashFocus
    data object Settings : DashFocus
    data class Channel(val id: String) : DashFocus
}

@Composable
fun PulseRoot(container: AppContainer) {
    val repo = container.repo
    val channels by repo.channels.collectAsState()
    val status by repo.status.collectAsState()
    val settings by container.settings.state.collectAsState()
    val keyStates by container.keyPool.state.collectAsState()
    val phoneUrl by container.server.url.collectAsState()

    val stack = remember { mutableStateListOf<Route>(Route.Dashboard) }
    // Kept here so the dashboard returns to the same card and scroll position after Back.
    val dashGrid = rememberLazyGridState()
    var dashFocus by remember { mutableStateOf<DashFocus>(DashFocus.First) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val replaceTop: (Route) -> Unit = { stack[stack.lastIndex] = it }
    BackHandler(enabled = stack.size > 1) { back() }
    // Any remote key press lifts night dimming for a while.
    var lastInputAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val route = stack.last()

    ScreenCare(
        // Only the always-on screens drift; menus stay put.
        shift = settings.pixelShift && (route == Route.Dashboard || route is Route.Wall),
        dimStart = settings.nightDimStart,
        dimEnd = settings.nightDimEnd,
        lastInputAt = lastInputAt,
        modifier = Modifier
            .background(Pulse.bg)
            .onPreviewKeyEvent { lastInputAt = System.currentTimeMillis(); false },
    ) {
        Box(Modifier.fillMaxSize().background(Pulse.bg)) {
            when (route) {
                Route.Dashboard -> DashboardScreen(
                    channels = channels,
                    status = status,
                    settings = settings,
                    phoneUrl = phoneUrl,
                    gridState = dashGrid,
                    focusTarget = dashFocus,
                    onOpen = { dashFocus = DashFocus.Channel(it); stack.add(Route.Detail(it)) },
                    onWall = { dashFocus = DashFocus.Wall; stack.add(Route.Wall(null)) },
                    onSettings = { dashFocus = DashFocus.Settings; stack.add(Route.Settings) },
                )

                is Route.Detail -> {
                    val channel = channels.firstOrNull { it.id == route.channelId }
                    if (channel == null) {
                        LaunchedEffect(route) { back() }
                    } else {
                        ChannelDetailScreen(
                            channel = channel,
                            videoHistory = repo::videoHistory,
                            onWall = { stack.add(Route.Wall(channel.id)) },
                            onCompare = { stack.add(Route.ComparePick(channel.id)) },
                            onRefresh = { repo.refreshNow() },
                            onRemove = {
                                repo.removeChannel(channel.id)
                                back()
                            },
                        )
                    }
                }

                is Route.Wall -> LiveWallScreen(
                    channels = channels,
                    startId = route.channelId,
                    cycleSeconds = settings.wallCycleSeconds,
                    status = status,
                )

                is Route.ComparePick -> {
                    val first = channels.firstOrNull { it.id == route.firstId }
                    if (first == null) {
                        LaunchedEffect(route) { back() }
                    } else {
                        ComparePickScreen(
                            first = first,
                            others = channels.filter { it.id != first.id },
                            onPick = { replaceTop(Route.Compare(first.id, it)) },
                        )
                    }
                }

                is Route.Compare -> {
                    val a = channels.firstOrNull { it.id == route.aId }
                    val b = channels.firstOrNull { it.id == route.bId }
                    if (a == null || b == null) {
                        LaunchedEffect(route) { back() }
                    } else {
                        CompareScreen(
                            a = a,
                            b = b,
                            onChange = { replaceTop(Route.ComparePick(a.id)) },
                            onSwap = { replaceTop(Route.Compare(b.id, a.id)) },
                        )
                    }
                }

                Route.Settings -> SettingsScreen(
                    container = container,
                    settings = settings,
                    channels = channels,
                    status = status,
                    keyStates = keyStates,
                    phoneUrl = phoneUrl,
                )
            }
        }
    }
}
