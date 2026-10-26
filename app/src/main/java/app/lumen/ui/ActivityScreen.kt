package app.lumen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.AppViewModel
import app.lumen.core.EXERCISE_METS
import app.lumen.core.Settings
import app.lumen.core.estimateBurn
import app.lumen.core.stepsKcal
import app.lumen.health.HcState
import app.lumen.health.HealthSync
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(vm: AppViewModel) {
    val c = LocalLumen.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val list by vm.exercises.collectAsStateWithLifecycle()
    val hc by vm.hc.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(date) { vm.refreshHc(); vm.refreshSteps() }

    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.refreshHc()
        if (granted.containsAll(HealthSync.required)) { vm.update { it.copy(hcEnabled = true) }; vm.syncHealth() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            T("Activity", 26.sp, FontWeight.SemiBold)
            DateNav(vm)

            StepsCard(date, steps, s)

            // Only shown while Lumen has no access to Health Connect; once connected it disappears.
            if (hc == HcState.NEEDS_PERMISSION || hc == HcState.NEEDS_INSTALL || hc == HcState.UNAVAILABLE) {
                GlassCard(tint = c.warn, padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(c.warn, 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T("Connect Mi Fitness", 15.sp, FontWeight.SemiBold)
                            T(
                                when (hc) {
                                    HcState.NEEDS_PERMISSION -> "Allow Health Connect access to import steps and workouts."
                                    HcState.NEEDS_INSTALL -> "Install or update Health Connect from Google Play."
                                    else -> "Health Connect isn't available on this device."
                                }, 12.sp, color = c.dim
                            )
                        }
                    }
                    if (hc == HcState.NEEDS_PERMISSION) { Spacer(Modifier.height(12.dp)); GlassButton("Grant access", { permLauncher.launch(HealthSync.permissions) }, primary = true) }
                    T("In Mi Fitness, turn on Health Connect sync so data can reach it.", 11.sp, color = c.dim, modifier = Modifier.padding(top = 8.dp))
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Workouts", 13.sp, FontWeight.SemiBold, c.dim, Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
                GlassButton("Sync now", { vm.syncHealth() }, icon = Icons.Rounded.Sync, compact = true)
                GlassButton("Add workout", { showAdd = true }, icon = Icons.Rounded.Add, compact = true)
            }
            if (list.isEmpty()) T("No workouts on this day.", 14.sp, color = c.dim, modifier = Modifier.padding(start = 4.dp))
            else GlassCard(padding = 4.dp) {
                list.forEachIndexed { i, x ->
                    if (i > 0) HairDivider()
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(x.name, 15.sp, FontWeight.Medium)
                            T("${x.minutes} min" + if (x.source == "HEALTH_CONNECT") " · Health Connect" else "", 12.sp, color = c.dim)
                        }
                        T("${x.kcal.roundToInt()} kcal", 15.sp, FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        IconCircle(Icons.Rounded.Close, { vm.deleteExercise(x.id) }, 34.dp, desc = "Delete")
                    }
                }
            }
            Spacer(Modifier.height(130.dp))
        }
    }

    if (showAdd) ModalBottomSheet(
        onDismissRequest = { showAdd = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bgBottom
    ) { AddWorkoutSheet(vm, s) { showAdd = false } }
}

@Composable
private fun StepsCard(date: LocalDate, steps: Map<LocalDate, Long>, s: Settings) {
    val c = LocalLumen.current
    val today = steps[date]
    Column {
        T("Steps", 14.sp, FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (today == null) {
            T("No step data yet. Connect Health Connect below to see your Mi Fitness steps.", 13.sp, color = c.dim)
            return@Column
        }
        val frac = (today.toFloat() / s.stepGoal).coerceIn(0f, 1f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 10.dp.toPx()
                    val d = size.minDimension - sw
                    val tl = Offset(sw / 2f, sw / 2f)
                    drawArc(color = c.dim.copy(alpha = 0.18f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = tl, size = Size(d, d), style = Stroke(sw))
                    drawArc(color = c.accent, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                        topLeft = tl, size = Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(fmtThousands(today), 22.sp, FontWeight.SemiBold)
                    T("of ${fmtThousands(s.stepGoal.toLong())}", 11.sp, color = c.dim)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                T("${(frac * 100).roundToInt()}% of goal", 16.sp, FontWeight.SemiBold)
                T("≈ ${stepsKcal(today, s.weightKg).roundToInt()} kcal", 13.sp, color = c.dim)
                T(if (s.stepsCredit) "Added to your burned total" else "Not added to burned total", 11.sp, color = c.dim)
            }
        }
        Spacer(Modifier.height(14.dp))
        val days = (6 downTo 0).map { date.minusDays(it.toLong()) }
        val values = days.map { steps[it] ?: 0L }
        val maxV = max(values.maxOrNull() ?: 0L, s.stepGoal.toLong()).toFloat() * 1.1f
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val slot = size.width / 7f
            val bw = slot * 0.5f
            val gy = size.height - (s.stepGoal / maxV) * size.height
            drawLine(c.accent.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            values.forEachIndexed { i, v ->
                val h = (v / maxV * size.height).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color = if (v >= s.stepGoal) c.accent else c.accent.copy(alpha = 0.45f),
                    topLeft = Offset(slot * i + (slot - bw) / 2f, size.height - h), size = Size(bw, h), cornerRadius = CornerRadius(bw / 2f)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { d -> T(d.format(DateTimeFormatter.ofPattern("EEEEE")), 10.sp, color = c.dim, modifier = Modifier.weight(1f), maxLines = 1) }
        }
    }
}

@Composable
private fun AddWorkoutSheet(vm: AppViewModel, s: Settings, onDone: () -> Unit) {
    val c = LocalLumen.current
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(30.0) }
    var kcal by remember { mutableStateOf(0.0) }
    var met by remember { mutableStateOf(5.0) }
    var fieldKey by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        T("Add workout", 18.sp, FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EXERCISE_METS.forEach { (n, m) -> Chip(n, name == n, { name = n; met = m; fieldKey++ }) }
        }
        Spacer(Modifier.height(10.dp))
        key(fieldKey) { GlassField(name, { name = it }, "Workout name") }
        Spacer(Modifier.height(8.dp))
        key(fieldKey) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Minutes", minutes, Modifier.weight(1f), integer = true) { minutes = it }
                NumField("Calories burnt", kcal, Modifier.weight(1f)) { kcal = it }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Estimate", { kcal = estimateBurn(met, s.weightKg, minutes.toInt()); fieldKey++ }, Modifier.weight(1f))
            GlassButton("Add", {
                vm.addExercise(name, minutes.toInt().coerceAtLeast(1), kcal.coerceAtLeast(0.0)); onDone()
            }, Modifier.weight(1f), primary = true, enabled = kcal > 0)
        }
        T("Estimate uses MET × your weight × time. Your watch's number is usually better.", 11.sp, color = c.dim, modifier = Modifier.padding(top = 8.dp))
    }
}
