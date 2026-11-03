package com.channelpulse.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.channelpulse.tv.ui.Pulse
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

/** Current time, updated every [everyMs]. */
@Composable
fun rememberNow(everyMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(everyMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(everyMs)
        }
    }
    return now
}

@Composable
fun Avatar(url: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Pulse.surfaceHi)
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
fun Thumbnail(url: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Pulse.surfaceHi)) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun LiveBadge(viewers: Long?, modifier: Modifier = Modifier, big: Boolean = false) {
    Row(
        modifier
            .background(Pulse.accent, RoundedCornerShape(6.dp))
            .padding(horizontal = if (big) 12.dp else 7.dp, vertical = if (big) 5.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (big) 10.dp else 6.dp).background(Color.White, CircleShape))
        Spacer(Modifier.width(if (big) 8.dp else 5.dp))
        Text("LIVE", color = Color.White, fontSize = if (big) 18.sp else 11.sp, fontWeight = FontWeight.Bold)
        if (viewers != null) {
            Spacer(Modifier.width(if (big) 10.dp else 6.dp))
            RollingNumber(viewers, fontSize = if (big) 18.sp else 11.sp, color = Color.White, smoothMs = 2000)
            Text(" watching", color = Color.White, fontSize = if (big) 18.sp else 11.sp)
        }
    }
}

@Composable
fun StatTile(
    label: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .background(Pulse.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label.uppercase(), color = Pulse.dim, fontSize = 11.sp, letterSpacing = 1.sp)
        content()
        if (note != null) Text(note, color = Pulse.faint, fontSize = 11.sp, maxLines = 1)
    }
}

/** Text for a change value: green for gains, red for losses, faint when unknown. */
@Composable
fun DeltaText(text: String?, positive: Boolean?, fontSize: Int = 13) {
    val color = when (positive) {
        true -> Pulse.up
        false -> Pulse.down
        null -> Pulse.faint
    }
    Text(text ?: "Collecting…", color = color, fontSize = fontSize.sp, maxLines = 1)
}

@Composable
fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(content) { qrBitmap(content, 512) }
    Box(
        modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        if (bitmap != null) Image(bitmap = bitmap, contentDescription = "QR code", modifier = Modifier.size(size - 20.dp))
    }
}

private fun qrBitmap(content: String, px: Int): ImageBitmap? = try {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, px, px,
        mapOf(EncodeHintType.MARGIN to 0)
    )
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) pixels[row + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
} catch (e: Exception) {
    null
}

/** A pill button; [selected] draws it filled. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val base = when {
        selected -> Pulse.accent
        danger -> Color(0xFF3A1E22)
        else -> Pulse.surfaceHi
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = base,
            contentColor = if (selected) Color.White else Pulse.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, fontSize = 14.sp)
    }
}

/**
 * Text input for the TV remote. D-pad focus lands on the field without opening the keyboard;
 * pressing OK starts editing and opens it. Done, Back, or ▲ ▼ leave editing again.
 * The phone setup page is quicker for long text like API keys.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val outer = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun stopEditing(moveTo: FocusDirection? = null) {
        if (!editing) return
        editing = false
        keyboard?.hide()
        try { outer.requestFocus() } catch (ignored: Exception) {}
        if (moveTo != null) focusManager.moveFocus(moveTo)
    }

    LaunchedEffect(editing) {
        if (editing) {
            try { field.requestFocus() } catch (ignored: Exception) {}
            keyboard?.show()
        }
    }

    Box(
        modifier
            .focusRequester(outer)
            .onFocusChanged { focused = it.hasFocus }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!editing && ok && e.type == KeyEventType.KeyUp) {
                    editing = true
                    true
                } else {
                    !editing && ok
                }
            }
            .focusable()
            .background(Pulse.bg, RoundedCornerShape(10.dp))
            .border(2.dp, if (focused) Color.White else Pulse.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Pulse.text, fontSize = 16.sp),
            cursorBrush = SolidColor(Pulse.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                stopEditing()
                onSubmit()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(field)
                // Only reachable while editing, so moving the D-pad over it never opens the keyboard.
                .focusProperties { canFocus = editing }
                .onFocusChanged { if (!it.isFocused && editing) editing = false }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionUp -> { stopEditing(FocusDirection.Up); true }
                        Key.DirectionDown -> { stopEditing(FocusDirection.Down); true }
                        Key.Back, Key.Escape -> { stopEditing(); true }
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = Pulse.faint, fontSize = 16.sp, maxLines = 1)
                    inner()
                }
            },
        )
    }
}
