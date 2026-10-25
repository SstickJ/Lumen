package app.lumen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.AppViewModel
import app.lumen.core.*
import app.lumen.data.EntryWithIngredients
import app.lumen.data.ExerciseRow
import app.lumen.data.sum
import coil.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    vm: AppViewModel, onScan: () -> Unit, onExercise: () -> Unit, onManual: () -> Unit,
    onSettings: () -> Unit, onEntry: (Long) -> Unit
) {
    val c = LocalLumen.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (s.hcEnabled) vm.syncHealth(silent = true) }

    val isToday = date == vm.todayLogical()
    val eaten = entries.sumOf { it.sum().kcal }
    val burned = exercises.sumOf { it.kcal }
    val status = dayStatus(eaten, burned, s.targetKcal(), s.exerciseCreditPct, s.tolerancePct, LocalTime.now().hour, isToday)
    val statusColor = when (status.kind) {
        StatusKind.OVER -> c.over
        StatusKind.ALMOST, StatusKind.UNDER -> c.warn
        else -> c.accent
    }
    val protein = entries.sumOf { it.sum().protein }
    val carbs = entries.sumOf { it.sum().carbs }
    val fat = entries.sumOf { it.sum().fat }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { DateBar(date, isToday, { vm.shiftDay(-1) }, { vm.shiftDay(1) }, { vm.goToday() }, onSettings) }

            item {
                GlassCard(tint = statusColor, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(statusColor, 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            T(status.title, 16.sp, FontWeight.SemiBold)
                            T(status.detail, 13.sp, color = c.dim)
                        }
                    }
                }
            }

            item { ChartCard(series, s, onRange = { d -> vm.update { it.copy(graphDays = d) } }) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Eaten", eaten.roundToInt().toString(), "kcal", Modifier.weight(1f))
                    Stat("Burned", burned.roundToInt().toString(), "kcal", Modifier.weight(1f))
                    val left = status.remaining
                    Stat(if (left >= 0) "Left" else "Over", abs(left).roundToInt().toString(), "kcal", Modifier.weight(1f),
                        if (left < 0) c.over else null)
                }
            }

            if (s.showMacros && eaten > 0) item {
                GlassCard(padding = 12.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Macro("Protein", protein); Macro("Carbs", carbs); Macro("Fat", fat)
                    }
                }
            }

            item { T("Meals", 13.sp, FontWeight.SemiBold, c.dim, Modifier.padding(start = 4.dp, top = 6.dp)) }
            if (entries.isEmpty()) item {
                GlassCard { T("Nothing logged. Tap the camera to scan a meal or barcode.", 14.sp, color = c.dim) }
            }
            items(entries, key = { it.entry.id }) { e -> EntryRow(e) { onEntry(e.entry.id) } }

            if (exercises.isNotEmpty()) {
                item { T("Exercise", 13.sp, FontWeight.SemiBold, c.dim, Modifier.padding(start = 4.dp, top = 6.dp)) }
                items(exercises, key = { "x" + it.id }) { x -> ExerciseLine(x, onExercise) }
            }
        }

        BottomBar(Modifier.align(Alignment.BottomCenter), onExercise, onScan, onManual)
    }
}

@Composable
private fun DateBar(date: LocalDate, isToday: Boolean, prev: () -> Unit, next: () -> Unit, today: () -> Unit, settings: () -> Unit) {
    val c = LocalLumen.current
    val label = when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(Icons.Rounded.ChevronLeft, prev, 38.dp, desc = "Previous day")
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clickable(onClick = today)) { T(if (isToday) "Today" else label, 20.sp, FontWeight.SemiBold) }
        Spacer(Modifier.width(10.dp))
        IconCircle(Icons.Rounded.ChevronRight, next, 38.dp, desc = "Next day")
        Spacer(Modifier.weight(1f))
        IconCircle(Icons.Rounded.Settings, settings, 40.dp, desc = "Settings")
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier, valueColor: Color? = null) {
    val c = LocalLumen.current
    GlassCard(modifier, padding = 14.dp) {
        T(label, 12.sp, color = c.dim)
        Spacer(Modifier.height(2.dp))
        T(value, 24.sp, FontWeight.SemiBold, valueColor ?: c.text)
        T(unit, 11.sp, color = c.dim)
    }
}

@Composable
private fun Macro(label: String, grams: Double) {
    val c = LocalLumen.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T("${grams.roundToInt()} g", 16.sp, FontWeight.SemiBold)
        T(label, 11.sp, color = c.dim)
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun EntryRow(e: EntryWithIngredients, onClick: () -> Unit) {
    val c = LocalLumen.current
    val kcal = e.sum().kcal.roundToInt()
    val time = Instant.ofEpochMilli(e.entry.ts).atZone(ZoneId.systemDefault()).format(timeFmt)
    GlassCard(padding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val photo = e.entry.photoPath?.let { File(it) }?.takeIf { it.exists() }
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).glass(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(if (e.entry.source == "BARCODE") Icons.Rounded.QrCodeScanner else Icons.Rounded.Restaurant, null, tint = c.dim, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(e.entry.name, 15.sp, FontWeight.Medium, maxLines = 1)
                T("${e.entry.meal} · $time · ${e.ingredients.size} item(s)", 12.sp, color = c.dim, maxLines = 1)
            }
            T("$kcal", 18.sp, FontWeight.SemiBold)
            T(" kcal", 11.sp, color = c.dim)
        }
    }
}

@Composable
private fun ExerciseLine(x: ExerciseRow, onClick: () -> Unit) {
    val c = LocalLumen.current
    GlassCard(padding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = c.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(x.name, 15.sp, FontWeight.Medium, maxLines = 1)
                T("${x.minutes} min" + if (x.source == "HEALTH_CONNECT") " · Health Connect" else "", 12.sp, color = c.dim)
            }
            T("−${x.kcal.roundToInt()}", 18.sp, FontWeight.SemiBold)
            T(" kcal", 11.sp, color = c.dim)
        }
    }
}

@Composable
private fun BottomBar(modifier: Modifier, onExercise: () -> Unit, onScan: () -> Unit, onManual: () -> Unit) {
    val c = LocalLumen.current
    Row(
        modifier.navigationBarsPadding().padding(bottom = 16.dp)
            .glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        IconCircle(Icons.Rounded.FitnessCenter, onExercise, 46.dp, desc = "Add exercise")
        Box(
            Modifier.size(66.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.75f))))
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.CameraAlt, "Scan", tint = if (c.dark) Color.Black else Color.White, modifier = Modifier.size(30.dp)) }
        IconCircle(Icons.Rounded.Edit, onManual, 46.dp, desc = "Add manually")
    }
}

@Composable
private fun ChartCard(series: List<DayTotals>, s: Settings, onRange: (Int) -> Unit) {
    val c = LocalLumen.current
    val maintenance = s.maintenanceKcal()
    val target = s.targetKcal()
    val delta = s.goalDeltaKcal.toDouble()
    val bars = series.map { d ->
        val net = netKcal(d.eaten, d.exercise, maintenance)
        Triple(d.date, net, dayVerdict(d.eaten, d.exercise, maintenance, delta, target))
    }
    val n = bars.size
    var sel by remember(n) { mutableStateOf(n - 1) }
    val picked = bars.getOrNull(sel.coerceIn(0, max(n - 1, 0)))

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Intake − outtake", 14.sp, FontWeight.SemiBold)
                T(
                    if (picked != null && picked.third != DayVerdict.EMPTY)
                        "${picked.first.format(DateTimeFormatter.ofPattern("EEE d"))}: ${signed(picked.second)} kcal"
                    else "Tap a bar for details",
                    12.sp, color = c.dim
                )
            }
            listOf(7, 14, 30).forEach { d -> Chip("${d}d", s.graphDays == d, { onRange(d) }, Modifier.padding(start = 6.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        if (n > 0) {
            val maxAbs = max(bars.maxOf { abs(it.second) }, abs(delta)).coerceAtLeast(300.0) * 1.15
            Canvas(
                Modifier.fillMaxWidth().height(112.dp).pointerInput(n) {
                    detectTapGestures { o -> sel = ((o.x / size.width) * n).toInt().coerceIn(0, n - 1) }
                }
            ) {
                val mid = size.height / 2f
                val half = mid - 4.dp.toPx()
                val slot = size.width / n
                val bw = (slot * 0.52f).coerceAtMost(26.dp.toPx())
                drawLine(c.dim.copy(alpha = 0.35f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
                val gy = mid - (delta / maxAbs * half).toFloat()
                drawLine(c.accent.copy(alpha = 0.75f), Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                bars.forEachIndexed { i, b ->
                    val x = slot * i + (slot - bw) / 2f
                    if (b.third == DayVerdict.EMPTY) {
                        drawCircle(c.dim.copy(alpha = 0.5f), 2.dp.toPx(), Offset(x + bw / 2f, mid))
                    } else {
                        val h = (abs(b.second) / maxAbs * half).toFloat().coerceAtLeast(2.dp.toPx())
                        val col = when (b.third) { DayVerdict.OVER -> c.over; DayVerdict.UNDER -> c.warn; else -> c.accent }
                        val top = if (b.second >= 0) mid - h else mid
                        drawRoundRect(col.copy(alpha = if (i == sel) 1f else 0.7f), Offset(x, top), Size(bw, h), CornerRadius(bw / 2f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (n <= 14) bars.forEach { b ->
                    T(b.first.format(DateTimeFormatter.ofPattern("EEEEE")), 10.sp, color = c.dim, modifier = Modifier.weight(1f), maxLines = 1)
                } else {
                    T(bars.first().first.format(DateTimeFormatter.ofPattern("d MMM")), 10.sp, color = c.dim, modifier = Modifier.weight(1f))
                    T(bars.last().first.format(DateTimeFormatter.ofPattern("d MMM")), 10.sp, color = c.dim, modifier = Modifier.weight(1f))
                }
            }
            T("Bars: kcal eaten minus kcal burned. Dashed line: your goal.", 10.sp, color = c.dim, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun signed(v: Double): String {
    val r = v.roundToInt()
    return if (r > 0) "+$r" else if (r < 0) "−${-r}" else "0"
}
