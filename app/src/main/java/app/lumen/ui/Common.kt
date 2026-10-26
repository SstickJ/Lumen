package app.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun fmtThousands(n: Long): String = NumberFormat.getIntegerInstance().format(n)

@Composable
fun DateNav(date: LocalDate, isToday: Boolean, prev: () -> Unit, next: () -> Unit, today: () -> Unit) {
    val label = when (date) {
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(Icons.Rounded.ChevronLeft, prev, 38.dp, desc = "Previous day")
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clickable(onClick = today)) { T(if (isToday) "Today" else label, 20.sp, FontWeight.SemiBold) }
        Spacer(Modifier.width(10.dp))
        IconCircle(Icons.Rounded.ChevronRight, next, 38.dp, desc = "Next day")
    }
}

/** Compact steps summary used on Home. */
@Composable
fun StepsStrip(steps: Long, goal: Int, onClick: () -> Unit) {
    val c = LocalLumen.current
    val frac = (steps.toFloat() / goal).coerceIn(0f, 1f)
    GlassCard(padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DirectionsWalk, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            T(fmtThousands(steps), 20.sp, FontWeight.SemiBold)
            T(" steps", 12.sp, color = c.dim, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            T("Goal ${fmtThousands(goal.toLong())}", 12.sp, color = c.dim)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(c.dim.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.accent))
        }
    }
}
