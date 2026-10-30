package app.lumen.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.lumen.core.Settings
import app.lumen.core.ThemeMode
import app.lumen.core.accentNeedsDarkText
import app.lumen.core.isNeutralColor
import app.lumen.core.parseHexColor
import app.lumen.core.readableAccent
import java.util.Locale
import kotlin.math.roundToInt

@Immutable
data class LumenColors(
    val dark: Boolean,
    val bgTop: Color, val bgBottom: Color,
    val text: Color, val dim: Color,
    val accent: Color, val warn: Color, val over: Color,
    val glassTop: Color, val glassBottom: Color,
    val edgeHi: Color, val edgeLo: Color,
    val haptics: Boolean,
    val onAccent: Color,   // text / icons drawn on top of the accent colour
    val glow: Color        // soft background glow (a calm blue-grey when the accent is white or grey)
)

val LocalLumen = staticCompositionLocalOf<LumenColors> { error("LumenTheme missing") }

/** The app's content, recorded once, so floating glass (tab bar, dialogs) can show a blurred copy of what is behind it. */
val LocalBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

fun lumenColors(s: Settings, systemDark: Boolean): LumenColors {
    val dark = when (s.themeMode) { ThemeMode.SYSTEM -> systemDark; ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val argb = readableAccent(parseHexColor(s.accentHex) ?: 0xFFFFFFFFL, dark)
    val accent = Color(argb)
    val onAccent = if (accentNeedsDarkText(argb)) Color(0xFF0B0F19) else Color.White
    val glow = if (isNeutralColor(argb)) Color(0xFF7C93C9) else accent
    val k = s.glassStrength
    return if (dark) LumenColors(
        true, Color(0xFF0A111F), Color(0xFF111C33), Color(0xFFF2F5FA), Color(0xFF9AA6BA),
        accent, Color(0xFFFFC266), Color(0xFFFF6B6B),
        Color.White.copy(alpha = 0.07f + 0.13f * k), Color.White.copy(alpha = 0.03f + 0.07f * k),
        Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), s.haptics, onAccent, glow
    ) else LumenColors(
        false, Color(0xFFE4ECF9), Color(0xFFF6F8FC), Color(0xFF0F1626), Color(0xFF5C687D),
        accent, Color(0xFFD98A12), Color(0xFFE5484D),
        Color.White.copy(alpha = 0.50f + 0.30f * k), Color.White.copy(alpha = 0.28f + 0.22f * k),
        Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.05f), s.haptics, onAccent, glow
    )
}

@Composable
fun LumenTheme(s: Settings, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val c = remember(s, sysDark) { lumenColors(s, sysDark) }
    val scheme = if (c.dark)
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bgTop, surface = c.bgBottom,
            onSurface = c.text, onBackground = c.text, surfaceVariant = c.bgBottom, onSurfaceVariant = c.dim)
    else
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bgTop, surface = c.bgBottom,
            onSurface = c.text, onBackground = c.text, surfaceVariant = c.bgBottom, onSurfaceVariant = c.dim)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !c.dark
            ctl.isAppearanceLightNavigationBars = !c.dark
        }
    }
    CompositionLocalProvider(LocalLumen provides c) { MaterialTheme(colorScheme = scheme, content = content) }
}

@Composable
fun LumenBackground(content: @Composable BoxScope.() -> Unit) {
    val c = LocalLumen.current
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val sky = Brush.verticalGradient(listOf(c.bgTop, c.bgBottom))
            val c1 = Offset(size.width * 0.12f, size.height * 0.06f)
            val r1 = size.width * 0.95f
            val g1 = Brush.radialGradient(listOf(c.glow.copy(alpha = if (c.dark) 0.30f else 0.26f), Color.Transparent), center = c1, radius = r1)
            val c2 = Offset(size.width * 0.98f, size.height * 0.60f)
            val r2 = size.width * 0.8f
            val g2 = Brush.radialGradient(listOf(c.glow.copy(alpha = 0.16f), Color.Transparent), center = c2, radius = r2)
            onDrawBehind {
                drawRect(sky)
                drawCircle(g1, radius = r1, center = c1)
                drawCircle(g2, radius = r2, center = c2)
            }
        },
        content = content
    )
}

/**
 * Frosted-glass surface. With [blur] = true (for glass that floats over other content) the content behind is
 * blurred, so text on top stays readable. Needs Android 12+; older versions get a more opaque fill instead.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), tint: Color? = null, blur: Boolean = false): Modifier {
    val c = LocalLumen.current
    val backdrop = LocalBackdrop.current
    val extra = if (blur) 0.10f else 0f
    val fill = remember(c.glassTop, c.glassBottom, tint, extra) {
        if (tint != null) Brush.verticalGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.10f)))
        else Brush.verticalGradient(listOf(
            c.glassTop.copy(alpha = (c.glassTop.alpha + extra).coerceAtMost(1f)),
            c.glassBottom.copy(alpha = (c.glassBottom.alpha + extra).coerceAtMost(1f))
        ))
    }
    val edge = remember(c.edgeHi, c.edgeLo) {
        Brush.linearGradient(listOf(c.edgeHi, c.edgeLo, c.edgeHi.copy(alpha = c.edgeHi.alpha * 0.5f)))
    }
    if (!blur || backdrop == null || Build.VERSION.SDK_INT < 31) {
        return this.background(fill, shape).border(BorderStroke(1.dp, edge), shape)
    }
    val layer = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { 22.dp.toPx() }
    var pos by remember { mutableStateOf(Offset.Zero) }
    SideEffect { layer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp) }
    return this
        .onGloballyPositioned { pos = it.positionInWindow() }
        .clip(shape)
        .drawBehind {
            // Copy the part of the recorded app content that sits under this element, blur it, draw it.
            val sz = IntSize(size.width.roundToInt(), size.height.roundToInt())
            layer.record(this, layoutDirection, sz) { translate(-pos.x, -pos.y) { drawLayer(backdrop) } }
            drawLayer(layer)
        }
        .background(fill, shape)
        .border(BorderStroke(1.dp, edge), shape)
}

/**
 * iOS / HyperOS-style press: the element springs down slightly while pressed and bounces back, no ripple box.
 * Put it BEFORE background/clip/glass in the chain so the whole element scales.
 */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, scale: Float = 0.94f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f), label = "press"
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = if (pressed && enabled) 0.92f else 1f }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun T(
    text: String, size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Normal,
    color: Color = LocalLumen.current.text, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE
) = Text(
    text, modifier = modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines,
    lineHeight = (size.value * 1.24f).sp
)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier, padding: Dp = 16.dp, tint: Color? = null,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    val base = if (onClick != null) modifier.bounceClick(scale = 0.98f, onClick = onClick) else modifier
    Column(base.glass(tint = tint).padding(padding), content = content)
}

@Composable
fun GlassButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
    icon: ImageVector? = null, enabled: Boolean = true, compact: Boolean = false
) {
    val c = LocalLumen.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    val clickable = modifier.bounceClick(enabled = enabled) {
        if (c.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
    val styled = if (primary) clickable.clip(shape).background(Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.78f))))
    else clickable.glass(shape)
    Row(
        styled.padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 9.dp else 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (!enabled) c.dim else if (primary) c.onAccent else c.text
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(if (compact) 16.dp else 18.dp)); Spacer(Modifier.width(if (compact) 6.dp else 8.dp)) }
        T(text, if (compact) 13.sp else 15.sp, FontWeight.SemiBold, fg)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLumen.current
    val shape = RoundedCornerShape(50)
    val press = modifier.bounceClick(scale = 0.92f, onClick = onClick)
    val styled = if (selected) press.clip(shape).background(c.accent.copy(alpha = 0.28f)).border(1.dp, c.accent.copy(alpha = 0.8f), shape)
    else press.glass(shape)
    Box(styled.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        T(text, 13.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal, if (selected) c.text else c.dim)
    }
}

/** Single-line chip row that scrolls sideways instead of overflowing. */
@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o -> Chip(o, i == selected, { onSelect(i) }) }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun IconCircle(icon: ImageVector, onClick: () -> Unit, size: Dp = 44.dp, tint: Color = LocalLumen.current.text, desc: String? = null) {
    Box(Modifier.size(size).bounceClick(scale = 0.9f, onClick = onClick).glass(CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun GlassField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, minLines: Int = 1,
    password: Boolean = false, placeholder: String? = null
) {
    val c = LocalLumen.current
    val ph: (@Composable () -> Unit)? =
        if (placeholder != null) ({ Text(placeholder, color = c.dim.copy(alpha = 0.7f)) }) else null
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = ph,
        singleLine = singleLine, minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.edgeHi.copy(alpha = if (c.dark) 0.18f else 0.6f),
            focusedLabelColor = c.accent, unfocusedLabelColor = c.dim,
            focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.accent,
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
        )
    )
}

fun fmtNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

/** Numeric field with its own text state, so half-typed numbers aren't rewritten under the cursor. */
@Composable
fun NumField(label: String, value: Double, modifier: Modifier = Modifier, integer: Boolean = false, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(fmtNum(value)) }
    GlassField(
        text, { t -> text = t; t.replace(',', '.').toDoubleOrNull()?.let(onValue) }, label, modifier,
        keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal
    )
}

/** iOS-style toggle with a springy thumb. */
@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLumen.current
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.6f, stiffness = 520f), label = "thumb")
    val track by animateColorAsState(if (checked) c.accent else c.dim.copy(alpha = 0.35f), tween(180), label = "track")
    val thumb by animateColorAsState(if (checked) c.onAccent else Color.White, tween(180), label = "thumb")
    Box(
        Modifier.size(width = 50.dp, height = 30.dp).bounceClick(scale = 0.94f) { onChange(!checked) }
            .clip(RoundedCornerShape(50)).background(track)
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(26.dp).clip(CircleShape).background(thumb))
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            T(label, 15.sp)
            if (hint != null) T(hint, 12.sp, color = LocalLumen.current.dim)
        }
        GlassSwitch(checked, onChange)
    }
}

@Composable
fun LumenSlider(value: Float, onChange: (Float) -> Unit, steps: Int = 0) {
    val c = LocalLumen.current
    Slider(
        value, onChange, valueRange = 0f..1f, steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.dim.copy(alpha = 0.25f),
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
        )
    )
}

/** Dialog action with the same springy press as every other button (replaces Material's TextButton). */
@Composable
fun DialogButton(text: String, onClick: () -> Unit, color: Color = LocalLumen.current.accent, enabled: Boolean = true) {
    Box(Modifier.bounceClick(enabled = enabled, scale = 0.92f, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        T(text, 15.sp, FontWeight.SemiBold, if (enabled) color else LocalLumen.current.dim)
    }
}
