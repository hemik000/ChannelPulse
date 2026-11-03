package com.channelpulse.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen-size helpers. TVs report about 960×540 dp whatever their resolution; phones, tablets
 * and TVs with unusual density settings get layouts that stack or scale instead of overflowing.
 */
object Screen {
    /** Below this width, side-by-side sections stack. */
    private const val COMPACT_DP = 720

    @Composable
    fun widthDp(): Int = LocalConfiguration.current.screenWidthDp

    @Composable
    fun compact(): Boolean = widthDp() < COMPACT_DP

    /** Left/right margin: TV overscan-safe on big screens, tighter on small ones. */
    @Composable
    fun sidePadding(): Dp = if (compact()) 20.dp else 48.dp

    /** Text scale for the giant live-wall numbers, relative to a 960 dp-wide TV. */
    @Composable
    fun wallScale(): Float = (widthDp() / 960f).coerceIn(0.5f, 1.5f)
}

/** Two blocks side by side with equal width, or stacked when the screen is narrow. */
@Composable
fun TwoUp(
    spacing: Dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    if (Screen.compact()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

/** A row of pill buttons that wraps onto more lines when it doesn't fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
