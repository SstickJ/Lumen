package app.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.AppViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

fun fmtThousands(n: Long): String = NumberFormat.getIntegerInstance().format(n)

/** Day switcher. Tap the date to open a calendar and jump to any day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateNav(vm: AppViewModel) {
    val c = LocalLumen.current
    val date by vm.date.collectAsStateWithLifecycle()
    val today = vm.todayLogical()
    var open by remember { mutableStateOf(false) }
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(Icons.Rounded.ChevronLeft, { vm.shiftDay(-1) }, 38.dp, desc = "Previous day")
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.bounceClick(scale = 0.95f) { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            T(label, 20.sp, FontWeight.SemiBold)
            Icon(Icons.Rounded.ExpandMore, "Pick a date", tint = c.dim, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        IconCircle(Icons.Rounded.ChevronRight, { vm.shiftDay(1) }, 38.dp, desc = "Next day")
    }
    if (open) ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bgBottom
    ) {
        CalendarPicker(vm, date, today) { picked -> vm.setDate(picked); open = false }
    }
}

@Composable
private fun CalendarPicker(vm: AppViewModel, selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = LocalLumen.current
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val logged by remember(month) { vm.loggedDays(month) }.collectAsStateWithLifecycle(emptySet())
    val onAccent = c.onAccent
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1          // weeks start on Monday
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.ChevronLeft, { month = month.minusMonths(1) }, 38.dp, desc = "Previous month")
            T(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), 18.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            IconCircle(Icons.Rounded.ChevronRight, { month = month.plusMonths(1) }, 38.dp, desc = "Next month")
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                T(it, 12.sp, FontWeight.SemiBold, c.dim, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val idx = r * 7 + col
                    val dayNum = idx - lead + 1
                    if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(46.dp))
                    } else {
                        val d = month.atDay(dayNum)
                        val isSel = d == selected
                        val isToday = d == today
                        val shape = CircleShape
                        Box(
                            Modifier.weight(1f).height(46.dp).padding(2.dp)
                                .bounceClick(scale = 0.88f) { onPick(d) }
                                .then(if (isSel) Modifier.clip(shape).background(c.accent) else if (isToday) Modifier.border(1.dp, c.accent, shape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                T("$dayNum", 15.sp, if (isSel || isToday) FontWeight.SemiBold else FontWeight.Normal, if (isSel) onAccent else c.text)
                                if (d in logged) Dot(if (isSel) onAccent else c.accent, 4.dp)
                                else Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Today", { onPick(today) }, Modifier.weight(1f), primary = true, compact = true)
            GlassButton("Yesterday", { onPick(today.minusDays(1)) }, Modifier.weight(1f), compact = true)
        }
        T("A dot marks days with logged meals.", 11.sp, color = c.dim, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Compact steps summary used on Home. */
@Composable
fun StepsStrip(steps: Long, goal: Int, onClick: () -> Unit) {
    val c = LocalLumen.current
    val frac = (steps.toFloat() / goal).coerceIn(0f, 1f)
    Column(Modifier.bounceClick(scale = 0.98f, onClick = onClick).padding(vertical = 6.dp)) {
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

@Composable
fun HairDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalLumen.current.dim.copy(alpha = 0.14f)))
}
