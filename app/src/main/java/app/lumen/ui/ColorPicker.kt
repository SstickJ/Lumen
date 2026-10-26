package app.lumen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.core.parseHexColor

private fun hexOf(h: Float, s: Float, v: Float): String =
    String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

private fun hsvOf(hex: String): FloatArray {
    val argb = (parseHexColor(hex) ?: 0xFF5B9BFFL).toInt()
    return FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
}

/** Accent picker: preset swatches, hue / saturation / brightness bars, and a hex box that accepts typing. */
@Composable
fun AccentPicker(hex: String, onChange: (String) -> Unit) {
    val c = LocalLumen.current
    val initial = remember { hsvOf(hex) }
    var h by remember { mutableStateOf(initial[0]) }
    var s by remember { mutableStateOf(initial[1]) }
    var v by remember { mutableStateOf(initial[2]) }
    var text by remember { mutableStateOf(hex) }
    val current = hexOf(h, s, v)

    // Follow outside changes (swatches, import, reset) without fighting the user's own drags.
    LaunchedEffect(hex) {
        if (!hex.equals(current, ignoreCase = true)) {
            val n = hsvOf(hex); h = n[0]; s = n[1]; v = n[2]
        }
        if (parseHexColor(text) != parseHexColor(hex)) text = hex
    }

    val swatches = listOf("#5B9BFF", "#35D0BA", "#A78BFA", "#FF8FA3", "#7BD88F", "#FFB547", "#1F2937")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            swatches.forEach { sw ->
                val col = Color(parseHexColor(sw)!!)
                val selected = sw.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(32.dp).bounceClick(scale = 0.88f) { onChange(sw); text = sw }
                        .clip(CircleShape).background(col)
                ) {
                    if (selected) Box(Modifier.align(Alignment.Center).size(10.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        GradientBar(h / 360f, (0..6).map { Color.hsv(it * 60f, 1f, 1f) }) { h = it * 360f; val x = hexOf(h, s, v); text = x; onChange(x) }
        GradientBar(s, listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v))) { s = it; val x = hexOf(h, s, v); text = x; onChange(x) }
        GradientBar(v, listOf(Color.Black, Color.hsv(h, s, 1f))) { v = it; val x = hexOf(h, s, v); text = x; onChange(x) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(parseHexColor(current)!!)))
            GlassField(
                text, { t ->
                    text = t
                    val norm = t.trim().let { if (it.startsWith("#")) it else "#$it" }
                    // Only apply once it is a complete, valid colour; partial typing is left alone.
                    if (norm.length == 7 && parseHexColor(norm) != null) onChange(norm.uppercase())
                },
                "Hex color", Modifier.weight(1f), placeholder = "#5B9BFF"
            )
        }
        T("Drag the bars or type a hex value like 5B9BFF.", 12.sp, FontWeight.Normal, c.dim)
    }
}

@Composable
private fun GradientBar(value: Float, colors: List<Color>, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val r = size.height / 2f
                    latest(((o.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val r = size.height / 2f
                    latest(((change.position.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f))
                }
            }
    ) {
        val r = size.height / 2f
        drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(r, r))
        val cx = r + value.coerceIn(0f, 1f) * (size.width - 2 * r)
        drawCircle(Color.White, r - 2.dp.toPx(), Offset(cx, r))
        drawCircle(Color.Black.copy(alpha = 0.25f), r - 2.dp.toPx(), Offset(cx, r), style = Stroke(1.dp.toPx()))
    }
}
