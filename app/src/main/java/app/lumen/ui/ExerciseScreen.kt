package app.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.AppViewModel
import app.lumen.core.EXERCISE_METS
import app.lumen.core.estimateBurn
import app.lumen.health.HcState
import app.lumen.health.HealthSync
import kotlin.math.roundToInt

@Composable
fun ExerciseScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalLumen.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val list by vm.exercises.collectAsStateWithLifecycle()
    val hc by vm.hc.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(30.0) }
    var kcal by remember { mutableStateOf(0.0) }
    var met by remember { mutableStateOf(5.0) }
    var fieldKey by remember { mutableStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.refreshHc()
        if (granted.containsAll(HealthSync.permissions)) { vm.update { it.copy(hcEnabled = true) }; vm.syncHealth() }
    }
    LaunchedEffect(Unit) { vm.refreshHc() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.ChevronLeft, onBack, 42.dp, desc = "Back")
            Spacer(Modifier.width(12.dp))
            T("Exercise", 20.sp, FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard {
                T("Mi Fitness via Health Connect", 14.sp, FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                T(
                    when (hc) {
                        HcState.READY -> "Connected. Workouts from the last ${s.hcLookbackDays} days are imported."
                        HcState.NEEDS_PERMISSION -> "Allow access to workouts and active calories."
                        HcState.NEEDS_INSTALL -> "Install or update Health Connect from Google Play first."
                        HcState.UNAVAILABLE -> "Health Connect isn't available on this device."
                    }, 12.sp, color = c.dim
                )
                T("In Mi Fitness, turn on Health Connect sync so workouts reach it.", 12.sp, color = c.dim, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(10.dp))
                when (hc) {
                    HcState.READY -> GlassButton("Sync now", { vm.syncHealth() }, icon = Icons.Rounded.Sync)
                    HcState.NEEDS_PERMISSION -> GlassButton("Grant access", { permLauncher.launch(HealthSync.permissions) }, primary = true)
                    else -> {}
                }
            }

            GlassCard {
                T("Add workout", 14.sp, FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXERCISE_METS.forEach { (n, m) ->
                        Chip(n, name == n, { name = n; met = m; fieldKey++ })
                    }
                }
                Spacer(Modifier.height(8.dp))
                key(fieldKey) { GlassField(name, { name = it }, "Workout name") }
                Spacer(Modifier.height(8.dp))
                key(fieldKey) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Minutes", minutes, Modifier.weight(1f), integer = true) { minutes = it }
                        NumField("Calories burnt", kcal, Modifier.weight(1f)) { kcal = it }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Estimate", { kcal = estimateBurn(met, s.weightKg, minutes.toInt()); fieldKey++ }, Modifier.weight(1f))
                    GlassButton("Add", {
                        vm.addExercise(name, minutes.toInt().coerceAtLeast(1), kcal.coerceAtLeast(0.0)); name = ""; kcal = 0.0; fieldKey++
                    }, Modifier.weight(1f), primary = true, enabled = kcal > 0)
                }
                T("Estimate uses MET × your weight × time. Your watch's number is usually better.", 11.sp, color = c.dim, modifier = Modifier.padding(top = 6.dp))
            }

            T("Today's workouts", 13.sp, FontWeight.SemiBold, c.dim, Modifier.padding(start = 4.dp))
            if (list.isEmpty()) GlassCard { T("No workouts on this day.", 14.sp, color = c.dim) }
            list.forEach { x ->
                GlassCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
