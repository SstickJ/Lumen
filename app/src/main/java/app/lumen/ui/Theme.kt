package app.lumen.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.lumen.core.Settings
import app.lumen.core.ThemeMode
import app.lumen.core.parseHexColor
import java.util.Locale

@Immutable
data class LumenColors(
    val dark: Boolean,
    val bgTop: Color, val bgBottom: Color,
    val text: Color, val dim: Color,
    val accent: Color, val warn: Color, val over: Color,
    val glassTop: Color, val glassBottom: Color,
    val edgeHi: Color, val edgeLo: Color,
    val haptics: Boolean
)

val LocalLumen = staticCompositionLocalOf<LumenColors> { error("LumenTheme missing") }

fun lumenColors(s: Settings, systemDark: Boolean): LumenColors {
    val dark = when (s.themeMode) { ThemeMode.SYSTEM -> systemDark; ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val accent = Color(parseHexColor(s.accentHex) ?: 0xFF5B9BFFL)
    val k = s.glassStrength
    return if (dark) LumenColors(
        true, Color(0xFF0A111F), Color(0xFF111C33), Color(0xFFF2F5FA), Color(0xFF9AA6BA),
        accent, Color(0xFFFFC266), Color(0xFFFF6B6B),
        Color.White.copy(alpha = 0.07f + 0.13f * k), Color.White.copy(alpha = 0.03f + 0.07f * k),
        Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), s.haptics
    ) else LumenColors(
        false, Color(0xFFE4ECF9), Color(0xFFF6F8FC), Color(0xFF0F1626), Color(0xFF5C687D),
        accent, Color(0xFFD98A12), Color(0xFFE5484D),
        Color.White.copy(alpha = 0.50f + 0.30f * k), Color.White.copy(alpha = 0.28f + 0.22f * k),
        Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.05f), s.haptics
    )
}

@Composable
fun LumenTheme(s: Settings, content: @Composable () -> Unit) {
    val c = lumenColors(s, isSystemInDarkTheme())
    val scheme = if (c.dark)
        darkColorScheme(primary = c.accent, onPrimary = Color.Black, background = c.bgTop, surface = c.bgBottom,
            onSurface = c.text, onBackground = c.text, surfaceVariant = c.bgBottom, onSurfaceVariant = c.dim)
    else
        lightColorScheme(primary = c.accent, onPrimary = Color.White, background = c.bgTop, surface = c.bgBottom,
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
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .drawBehind {
                val c1 = Offset(size.width * 0.12f, size.height * 0.06f)
                val r1 = size.width * 0.95f
                drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = if (c.dark) 0.34f else 0.30f), Color.Transparent), center = c1, radius = r1), radius = r1, center = c1)
                val c2 = Offset(size.width * 0.98f, size.height * 0.60f)
                val r2 = size.width * 0.8f
                drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f), Color.Transparent), center = c2, radius = r2), radius = r2, center = c2)
            },
        content = content
    )
}

/** Frosted-glass surface: translucent gradient fill plus a light-catching edge. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), tint: Color? = null): Modifier {
    val c = LocalLumen.current
    val fill = if (tint != null) Brush.verticalGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.10f)))
    else Brush.verticalGradient(listOf(c.glassTop, c.glassBottom))
    val edge = Brush.linearGradient(listOf(c.edgeHi, c.edgeLo, c.edgeHi.copy(alpha = c.edgeHi.alpha * 0.5f)))
    return this.clip(shape).background(fill).border(BorderStroke(1.dp, edge), shape)
}

@Composable
fun T(
    text: String, size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Normal,
    color: Color = LocalLumen.current.text, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE
) = Text(text, modifier = modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier, padding: Dp = 16.dp, tint: Color? = null,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    val m = modifier.glass(tint = tint).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(m.padding(padding), content = content)
}

@Composable
fun GlassButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
    icon: ImageVector? = null, enabled: Boolean = true
) {
    val c = LocalLumen.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    val base = if (primary) modifier.clip(shape).background(Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.78f))))
    else modifier.glass(shape)
    Row(
        base.clickable(enabled = enabled) {
            if (c.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }.padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (!enabled) c.dim else if (primary) (if (c.dark) Color.Black else Color.White) else c.text
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        T(text, 15.sp, FontWeight.SemiBold, fg)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLumen.current
    val shape = RoundedCornerShape(50)
    val m = if (selected) modifier.clip(shape).background(c.accent.copy(alpha = 0.28f)).border(1.dp, c.accent.copy(alpha = 0.8f), shape)
    else modifier.glass(shape)
    Box(m.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        T(text, 13.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal, if (selected) c.text else c.dim)
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o -> Chip(o, i == selected, { onSelect(i) }) }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun IconCircle(icon: ImageVector, onClick: () -> Unit, size: Dp = 44.dp, tint: Color = LocalLumen.current.text, desc: String? = null) {
    Box(Modifier.size(size).glass(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
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

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(label, 15.sp)
            if (hint != null) T(hint, 12.sp, color = LocalLumen.current.dim)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
