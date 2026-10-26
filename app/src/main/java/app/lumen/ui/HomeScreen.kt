package app.lumen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d")
private val letterFmt = DateTimeFormatter.ofPattern("EEEEE")

@Composable
fun HomeScreen(vm: AppViewModel, onActivity: () -> Unit, onManual: () -> Unit, onEntry: (Long) -> Unit) {
    val c = LocalLumen.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.autoSync() }
    LaunchedEffect(date, s.graphDays) { vm.refreshSteps() }

    val isToday = date == vm.todayLogical()
    val totals = remember(entries) { entries.map { it.sum() } }
    val eaten = totals.sumOf { it.kcal }
    val stepsToday = steps[date]
    val stepKcal = if (s.stepsCredit && stepsToday != null) stepsKcal(stepsToday, s.weightKg) else 0.0
    val burned = exercises.sumOf { it.kcal } + stepKcal
    val status = dayStatus(eaten, burned, s.targetKcal(), s.exerciseCreditPct, s.tolerancePct, LocalTime.now().hour, isToday)
    val statusColor = when (status.kind) {
        StatusKind.OVER -> c.over
        StatusKind.ALMOST, StatusKind.UNDER -> c.warn
        else -> c.accent
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { DateNav(date, isToday, { vm.shiftDay(-1) }, { vm.shiftDay(1) }, { vm.goToday() }) }

        // Status: plain text, no box.
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(statusColor, 9.dp)
                    Spacer(Modifier.width(8.dp))
                    T(status.title, 13.sp, FontWeight.SemiBold, statusColor)
                }
                Spacer(Modifier.height(6.dp))
                T(status.detail, 28.sp, FontWeight.SemiBold)
            }
        }

        item { TrendChart(series, s) { next -> vm.update { it.copy(graphDays = next) } } }

        item {
            Row(Modifier.fillMaxWidth()) {
                Metric("eaten", eaten.roundToInt().toString(), null, Modifier.weight(1f))
                Metric("burned", burned.roundToInt().toString(), null, Modifier.weight(1f))
                val left = status.remaining
                Metric(if (left >= 0) "left" else "over", abs(left).roundToInt().toString(), if (left < 0) c.over else null, Modifier.weight(1f))
            }
        }

        if (stepsToday != null) item { StepsStrip(stepsToday, s.stepGoal, onActivity) }

        if (s.showMacros && eaten > 0) item {
            T(
                "Protein ${totals.sumOf { it.protein }.roundToInt()} g   ·   Carbs ${totals.sumOf { it.carbs }.roundToInt()} g   ·   Fat ${totals.sumOf { it.fat }.roundToInt()} g",
                12.sp, color = c.dim
            )
        }

        item {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("Meals", 13.sp, FontWeight.SemiBold, c.dim, Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onManual).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(16.dp))
                        T(" Add manually", 12.sp, FontWeight.SemiBold, c.accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    T("Nothing logged yet. Tap the camera to scan a meal or barcode.", 14.sp, color = c.dim)
                } else {
                    GlassCard(padding = 4.dp) {
                        entries.forEachIndexed { i, e ->
                            if (i > 0) HairDivider()
                            EntryRow(e) { onEntry(e.entry.id) }
                        }
                    }
                }
            }
        }

        if (exercises.isNotEmpty()) item {
            Column {
                T("Exercise", 13.sp, FontWeight.SemiBold, c.dim)
                Spacer(Modifier.height(10.dp))
                GlassCard(padding = 4.dp) {
                    exercises.forEachIndexed { i, x ->
                        if (i > 0) HairDivider()
                        ExerciseLine(x, onActivity)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color?, modifier: Modifier) {
    val c = LocalLumen.current
    Column(modifier) {
        T(value, 26.sp, FontWeight.SemiBold, color ?: c.text)
        T(label, 12.sp, color = c.dim)
    }
}

@Composable
private fun EntryRow(e: EntryWithIngredients, onClick: () -> Unit) {
    val c = LocalLumen.current
    val kcal = remember(e) { e.sum().kcal.roundToInt() }
    val time = remember(e.entry.ts) { Instant.ofEpochMilli(e.entry.ts).atZone(ZoneId.systemDefault()).format(timeFmt) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val photo = remember(e.entry.photoPath) { e.entry.photoPath?.let { File(it) }?.takeIf { it.exists() } }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.dim.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (photo != null) AsyncImage(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(if (e.entry.source == "BARCODE") Icons.Rounded.QrCodeScanner else Icons.Rounded.Restaurant, null, tint = c.dim, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(e.entry.name, 15.sp, FontWeight.Medium, maxLines = 1)
            T("${e.entry.meal} · $time", 12.sp, color = c.dim, maxLines = 1)
        }
        T("$kcal", 17.sp, FontWeight.SemiBold)
        T(" kcal", 11.sp, color = c.dim)
    }
}

@Composable
private fun ExerciseLine(x: ExerciseRow, onClick: () -> Unit) {
    val c = LocalLumen.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.FitnessCenter, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(x.name, 15.sp, FontWeight.Medium, maxLines = 1)
            T("${x.minutes} min", 12.sp, color = c.dim)
        }
        T("−${x.kcal.roundToInt()}", 17.sp, FontWeight.SemiBold)
        T(" kcal", 11.sp, color = c.dim)
    }
}

/** Minimal trend: one thin bar per day (kcal eaten minus kcal burned), coloured by how the day went. Tap the title to change the range. */
@Composable
private fun TrendChart(series: List<DayTotals>, s: Settings, onRange: (Int) -> Unit) {
    val c = LocalLumen.current
    val bars = remember(series, s) {
        val maintenance = s.maintenanceKcal()
        val target = s.targetKcal()
        val delta = s.goalDeltaKcal.toDouble()
        series.map { d -> Triple(d.date, netKcal(d.eaten, d.exercise, maintenance), dayVerdict(d.eaten, d.exercise, maintenance, delta, target)) }
    }
    val n = bars.size
    var sel by remember(n) { mutableStateOf(max(n - 1, 0)) }
    if (n == 0) return
    val picked = bars.getOrNull(sel)

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(50)).clickable { onRange(when (s.graphDays) { 7 -> 14; 14 -> 30; else -> 7 }) }.padding(vertical = 4.dp)) {
                T("Last ${s.graphDays} days", 12.sp, FontWeight.SemiBold, c.dim)
            }
            Spacer(Modifier.weight(1f))
            if (picked != null && picked.third != DayVerdict.EMPTY) T("${picked.first.format(dayFmt)}   ${signed(picked.second)} kcal", 12.sp, color = c.dim)
        }
        Spacer(Modifier.height(8.dp))
        val maxAbs = max(bars.maxOf { abs(it.second) }, 300.0) * 1.1
        Canvas(
            Modifier.fillMaxWidth().height(80.dp).pointerInput(n) {
                detectTapGestures { o -> sel = ((o.x / size.width) * n).toInt().coerceIn(0, n - 1) }
            }
        ) {
            val mid = size.height / 2f
            val half = mid - 2.dp.toPx()
            val slot = size.width / n
            val bw = (slot * 0.42f).coerceAtMost(12.dp.toPx())
            drawLine(c.dim.copy(alpha = 0.16f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            bars.forEachIndexed { i, b ->
                val x = slot * i + (slot - bw) / 2f
                if (b.third == DayVerdict.EMPTY) {
                    drawCircle(c.dim.copy(alpha = 0.35f), 1.5.dp.toPx(), Offset(x + bw / 2f, mid))
                } else {
                    val h = (abs(b.second) / maxAbs * half).toFloat().coerceAtLeast(3.dp.toPx())
                    val col = when (b.third) { DayVerdict.OVER -> c.over; DayVerdict.UNDER -> c.warn; else -> c.accent }
                    drawRoundRect(
                        col.copy(alpha = if (i == sel) 1f else 0.5f),
                        Offset(x, if (b.second >= 0) mid - h else mid), Size(bw, h), CornerRadius(bw / 2f)
                    )
                }
            }
        }
        if (n <= 7) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            bars.forEach { b -> T(b.first.format(letterFmt), 10.sp, color = c.dim, modifier = Modifier.weight(1f), maxLines = 1) }
        }
    }
}

private fun signed(v: Double): String {
    val r = v.roundToInt()
    return if (r > 0) "+$r" else if (r < 0) "−${-r}" else "0"
}
