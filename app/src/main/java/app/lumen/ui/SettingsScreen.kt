package app.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.AppViewModel
import app.lumen.core.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalLumen.current
    T(title, 13.sp, FontWeight.SemiBold, c.dim, Modifier.padding(start = 4.dp, top = 8.dp))
    GlassCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
private fun Hint(text: String) = T(text, 12.sp, color = LocalLumen.current.dim)

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalLumen.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    BackHandler(onBack = onBack)
    var showKey by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var temp by remember { mutableStateOf(s.aiTemperature?.let { fmtNum(it) } ?: "") }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.ChevronLeft, onBack, 42.dp, desc = "Back")
            Spacer(Modifier.width(12.dp))
            T("Settings", 20.sp, FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          key(refresh) {
            Section("AI MEAL RECOGNITION") {
                val providers = AiProvider.values().toList()
                ChipRow(providers.map { when (it) { AiProvider.GEMINI -> "Gemini"; AiProvider.OPENAI -> "OpenAI-compat"; AiProvider.ANTHROPIC -> "Anthropic" } },
                    providers.indexOf(s.aiProvider)) { i -> vm.update { it.copy(aiProvider = providers[i]) } }
                GlassField(s.aiApiKey, { v -> vm.update { it.copy(aiApiKey = v) } }, "API key", password = !showKey)
                SwitchRow("Show key", showKey, { showKey = it })
                GlassField(s.aiBaseUrl, { v -> vm.update { it.copy(aiBaseUrl = v) } }, "Base URL", placeholder = s.aiProvider.defaultBase)
                GlassField(s.aiModel, { v -> vm.update { it.copy(aiModel = v) } }, "Model", placeholder = s.aiProvider.defaultModel)
                Hint("Blank fields use the provider default. For a local server (Ollama, LM Studio) pick OpenAI-compatible and enter e.g. http://192.168.1.10:11434/v1 with a vision model.")
                GlassField(temp, { v -> temp = v; vm.update { it.copy(aiTemperature = v.replace(',', '.').toDoubleOrNull()) } },
                    "Temperature (blank = provider default)", keyboardType = KeyboardType.Decimal)
                SwitchRow("Request JSON mode", s.aiJsonMode, { v -> vm.update { it.copy(aiJsonMode = v) } }, "Turn off if your provider rejects response_format.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Max image px", s.aiMaxImagePx.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(aiMaxImagePx = v.toInt()) } }
                    NumField("JPEG quality", s.aiJpegQuality.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(aiJpegQuality = v.toInt()) } }
                    NumField("Timeout s", s.aiTimeoutSec.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(aiTimeoutSec = v.toInt()) } }
                }
                GlassField(s.aiPrompt, { v -> vm.update { it.copy(aiPrompt = v) } }, "System prompt", singleLine = false, minLines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Reset prompt", { vm.update { it.copy(aiPrompt = DEFAULT_AI_PROMPT) } }, Modifier.weight(1f))
                    GlassButton("Test AI", { vm.testAi() }, Modifier.weight(1f), primary = true)
                }
            }

            Section("FOOD DATABASE") {
                val fp = FoodProvider.values().toList()
                ChipRow(fp.map { if (it == FoodProvider.USDA) "USDA" else "Open Food Facts" }, fp.indexOf(s.foodProvider)) { i -> vm.update { it.copy(foodProvider = fp[i]) } }
                GlassField(s.offBaseUrl, { v -> vm.update { it.copy(offBaseUrl = v) } }, "Open Food Facts URL")
                GlassField(s.usdaApiKey, { v -> vm.update { it.copy(usdaApiKey = v) } }, "USDA API key (DEMO_KEY works, rate limited)")
                Hint("Barcodes always use Open Food Facts, with USDA branded foods as fallback. The provider above is used when searching ingredients. Try pt.openfoodfacts.org for Portuguese products.")
            }

            Section("GOAL & ENERGY") {
                SwitchRow("Calculate my target automatically", s.autoGoal, { v -> vm.update { it.copy(autoGoal = v) } }, "Mifflin-St Jeor formula")
                if (s.autoGoal) {
                    ChipRow(listOf("Male", "Female"), s.sex.ordinal) { i -> vm.update { it.copy(sex = Sex.values()[i]) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Age", s.age.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(age = v.toInt()) } }
                        NumField("Height cm", s.heightCm, Modifier.weight(1f)) { v -> vm.update { it.copy(heightCm = v) } }
                        NumField("Weight kg", s.weightKg, Modifier.weight(1f)) { v -> vm.update { it.copy(weightKg = v) } }
                    }
                    val acts = listOf("Sedentary" to 1.2, "Light" to 1.375, "Moderate" to 1.55, "Active" to 1.725)
                    T("Daily activity (excluding logged workouts)", 12.sp, color = c.dim)
                    ChipRow(acts.map { it.first }, acts.indexOfFirst { abs(it.second - s.activityFactor) < 0.001 }) { i -> vm.update { it.copy(activityFactor = acts[i].second) } }
                } else {
                    NumField("Daily calorie target", s.manualGoalKcal.toDouble(), integer = true) { v -> vm.update { it.copy(manualGoalKcal = v.toInt()) } }
                }
                T("Goal", 12.sp, color = c.dim)
                val goals = listOf("Lose" to -500, "Maintain" to 0, "Gain" to 300)
                ChipRow(goals.map { it.first }, goals.indexOfFirst { it.second == s.goalDeltaKcal }) { i -> vm.update { it.copy(goalDeltaKcal = goals[i].second) }; refresh++ }
                NumField("Daily surplus (+) / deficit (−) kcal", s.goalDeltaKcal.toDouble(), integer = true) { v -> vm.update { it.copy(goalDeltaKcal = v.toInt()) } }
                Hint("Target: ${s.targetKcal().roundToInt()} kcal/day · maintenance ${s.maintenanceKcal().roundToInt()} kcal")
                s.targetWarning()?.let { T(it, 12.sp, color = c.over) }
                T("Workout calories added to your budget: ${s.exerciseCreditPct}%", 13.sp)
                Slider(s.exerciseCreditPct / 100f, { v -> vm.update { it.copy(exerciseCreditPct = (v * 10).roundToInt() * 10) } }, valueRange = 0f..1f, steps = 9)
                T("\"On target\" tolerance: ±${s.tolerancePct}%", 13.sp)
                Slider(s.tolerancePct / 20f, { v -> vm.update { it.copy(tolerancePct = (v * 20).roundToInt()) } }, valueRange = 0f..1f, steps = 19)
                NumField("Day starts at hour (0-12)", s.dayStartHour.toDouble(), integer = true) { v -> vm.update { it.copy(dayStartHour = v.toInt()) } }
                Hint("Set to 4 to count late-night snacks toward the previous day.")
            }

            Section("LOGGING") {
                SwitchRow("Review before saving", s.confirmBeforeSaving, { v -> vm.update { it.copy(confirmBeforeSaving = v) } }, "Off = AI results are logged immediately.")
                SwitchRow("Auto-log scanned barcodes", s.barcodeAutoLog, { v -> vm.update { it.copy(barcodeAutoLog = v) } }, "Logs one serving without asking.")
                NumField("Default serving (g)", s.defaultServingGrams.toDouble(), integer = true) { v -> vm.update { it.copy(defaultServingGrams = v.toInt()) } }
                SwitchRow("Show protein / carbs / fat", s.showMacros, { v -> vm.update { it.copy(showMacros = v) } })
                SwitchRow("Haptic feedback", s.haptics, { v -> vm.update { it.copy(haptics = v) } })
            }

            Section("HEALTH CONNECT (MI FITNESS)") {
                SwitchRow("Auto-import workouts", s.hcEnabled, { v -> vm.update { it.copy(hcEnabled = v) }; if (v) vm.syncHealth() }, "Grant access from the Exercise screen first.")
                NumField("Look back (days)", s.hcLookbackDays.toDouble(), integer = true) { v -> vm.update { it.copy(hcLookbackDays = v.toInt()) } }
            }

            Section("APPEARANCE") {
                ChipRow(listOf("System", "Light", "Dark"), s.themeMode.ordinal) { i -> vm.update { it.copy(themeMode = ThemeMode.values()[i]) } }
                val swatches = listOf("#5B9BFF", "#35D0BA", "#A78BFA", "#FF8FA3", "#7BD88F")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    swatches.forEach { hex ->
                        val col = Color(parseHexColor(hex)!!)
                        Box(Modifier.size(34.dp).clip(CircleShape).background(col)
                            .border(if (s.accentHex.equals(hex, true)) 2.dp else 0.dp, c.text, CircleShape)
                            .clickable { vm.update { it.copy(accentHex = hex) } })
                    }
                }
                GlassField(s.accentHex, { v -> vm.update { it.copy(accentHex = v) } }, "Accent color (hex)")
                T("Glass strength", 13.sp)
                Slider(s.glassStrength, { v -> vm.update { it.copy(glassStrength = v) } })
            }

            Section("CONFIG IMPORT / EXPORT") {
                Hint("Export copies every setting except API keys. Import merges JSON over your current settings, so partial files work.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Copy settings", { clipboard.setText(AnnotatedString(vm.exportJson())) }, Modifier.weight(1f))
                    GlassButton("Paste JSON", { showImport = true }, Modifier.weight(1f))
                }
                GlassField(s.configUrl, { v -> vm.update { it.copy(configUrl = v) } }, "Remote config URL (optional)", keyboardType = KeyboardType.Uri)
                GlassButton("Fetch & apply", { vm.importUrl(s.configUrl) }, Modifier.fillMaxWidth(), enabled = s.configUrl.isNotBlank())
                Hint("Only use a URL you control: a config can change the AI endpoint, which receives your photos and key.")
                GlassButton("Reset all settings", { vm.resetSettings(); temp = ""; refresh++ }, Modifier.fillMaxWidth())
            }
          }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showImport) AlertDialog(
        onDismissRequest = { showImport = false }, containerColor = c.bgBottom,
        title = { T("Import settings", 18.sp, FontWeight.SemiBold) },
        text = { GlassField(importText, { importText = it }, "Settings JSON", singleLine = false, minLines = 6) },
        confirmButton = { TextButton({ if (vm.importJson(importText)) { showImport = false; importText = ""; refresh++ } }) { Text("Import") } },
        dismissButton = { TextButton({ showImport = false }) { Text("Cancel") } }
    )
}
