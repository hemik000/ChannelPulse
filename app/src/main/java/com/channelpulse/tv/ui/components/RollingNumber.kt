package com.channelpulse.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.channelpulse.tv.data.Fmt
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * A number whose digits roll like an odometer when it changes.
 *
 * @param smoothMs when > 0, counts through the values between the old and new reading over this
 *   many milliseconds (use for exact counts like views). Leave 0 for rounded counts.
 * @param roundedSigFigs when set and the value is ≥ 1,000, digits after this many significant
 *   figures are drawn faint, because YouTube rounds them.
 */
@Composable
fun RollingNumber(
    value: Long,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Bold,
    smoothMs: Int = 0,
    roundedSigFigs: Int? = null,
    prefix: String = "",
) {
    var shown by remember { mutableLongStateOf(value) }
    var rising by remember { mutableStateOf(true) }

    LaunchedEffect(value) {
        val start = shown
        val diff = value - start
        if (diff == 0L) return@LaunchedEffect
        rising = diff > 0
        if (smoothMs <= 0) {
            shown = value
            return@LaunchedEffect
        }
        val steps = min(abs(diff), (smoothMs / 60).toLong().coerceAtLeast(1L))
        val stepDelay = (smoothMs / steps).coerceAtLeast(16L)
        for (i in 1..steps) {
            shown = start + diff * i / steps
            delay(stepDelay)
        }
        shown = value
    }

    val text = prefix + Fmt.full(shown)
    val style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, fontFeatureSettings = "tnum")
    val faint = (if (color == Color.Unspecified) Color.White else color).copy(alpha = 0.32f)
    val digitsTotal = text.count { it.isDigit() }
    val dimFrom = if (roundedSigFigs != null && shown >= 1000) roundedSigFigs else Int.MAX_VALUE

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        var digitIndex = 0
        text.forEachIndexed { index, ch ->
            val fromEnd = text.length - index
            val dim = ch.isDigit() && digitIndex >= dimFrom && digitIndex < digitsTotal
            if (ch.isDigit()) digitIndex++
            val c = if (dim) faint else color
            key(fromEnd) {
                if (ch.isDigit()) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            val dir = if (rising) 1 else -1
                            (slideInVertically(tween(260)) { h -> h * dir } + fadeIn(tween(260))) togetherWith
                                (slideOutVertically(tween(260)) { h -> -h * dir } + fadeOut(tween(200))) using
                                SizeTransform(clip = true)
                        },
                        label = "digit",
                    ) { d ->
                        Text(text = d.toString(), style = style, color = c)
                    }
                } else {
                    Text(text = ch.toString(), style = style, color = c)
                }
            }
        }
    }
}

/**
 * [RollingNumber] that shrinks below [maxFontSize] when the number wouldn't fit the available width,
 * so long counts like 64,38,28,652 are never cut off.
 */
@Composable
fun FitRollingNumber(
    value: Long,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Bold,
    smoothMs: Int = 0,
    roundedSigFigs: Int? = null,
    prefix: String = "",
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        val chars = (prefix + Fmt.full(value)).length.coerceAtLeast(1)
        // Tabular bold digits are about 0.6 em wide.
        val fit = (maxWidth.value / (chars * 0.6f * fontScale)).sp
        RollingNumber(
            value = value,
            fontSize = if (fit < maxFontSize) fit else maxFontSize,
            color = color,
            fontWeight = fontWeight,
            smoothMs = smoothMs,
            roundedSigFigs = roundedSigFigs,
            prefix = prefix,
        )
    }
}
