package app.lumen.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

/** A collapsible group. Closed groups are not composed at all, which keeps this tab fast. */
@Composable
private fun Section(
    title: String, subtitle: String, icon: ImageVector,
    openMap: SnapshotStateMap<String, Boolean>, content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalLumen.current
    val open = openMap[title] == true
    GlassCard(Modifier.animateContentSize(), padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().bounceClick(scale = 0.985f) { openMap[title] = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(title, 16.sp, FontWeight.SemiBold)
                T(subtitle, 12.sp, color = c.dim, maxLines = 1)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.dim)
        }
        if (open) Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content
        )
    }
}

@Composable
private fun Sub(text: String) = T(text, 12.sp, FontWeight.SemiBold, LocalLumen.current.dim, Modifier.padding(top = 4.dp))

@Composable
private fun Hint(text: String) = T(text, 12.sp, color = LocalLumen.current.dim)

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalLumen.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.dim.copy(alpha = 0.08f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

private fun providerChip(p: AiProvider) = when (p) {
    AiProvider.GEMINI -> "Gemini"
    AiProvider.OPENAI -> "OpenAI-compat"
    AiProvider.ANTHROPIC -> "Anthropic"
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = LocalLumen.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showKey by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf(s.aiTemperature?.let { fmtNum(it) } ?: "") }
    var refresh by remember { mutableStateOf(0) }
    val openMap = remember { mutableStateMapOf<String, Boolean>() }
    val providers = AiProvider.values().toList()

    fun updFallback(i: Int, f: (AiEndpoint) -> AiEndpoint) =
        vm.update { st -> st.copy(aiFallbacks = st.aiFallbacks.mapIndexed { k, e -> if (k == i) f(e) else e }) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        T("Settings", 26.sp, FontWeight.SemiBold, modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 10.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            key(refresh) {

                // ---------------- AI ----------------
                Section(
                    "AI recognition",
                    s.aiLabel() + if (s.aiFallbacks.isNotEmpty()) "  +${s.aiFallbacks.size} fallback" else "",
                    Icons.Rounded.AutoAwesome, openMap
                ) {
                    Sub("Primary model")
                    ChipRow(providers.map { providerChip(it) }, providers.indexOf(s.aiProvider)) { i -> vm.update { it.copy(aiProvider = providers[i]) } }
                    GlassField(s.aiApiKey, { v -> vm.update { it.copy(aiApiKey = v) } }, "API key", password = !showKey)
                    GlassField(s.aiModel, { v -> vm.update { it.copy(aiModel = v) } }, "Model", placeholder = s.aiProvider.defaultModel)
                    SwitchRow("Show API keys", showKey, { showKey = it })

                    Sub("Fallback models")
                    Hint("If the primary model fails (rate limit, outage, bad reply), Lumen tries these in order.")
                    s.aiFallbacks.forEachIndexed { i, ep ->
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T("Fallback ${i + 1}", 13.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                IconCircle(Icons.Rounded.Close, {
                                    vm.update { st -> st.copy(aiFallbacks = st.aiFallbacks.filterIndexed { k, _ -> k != i }) }
                                }, 32.dp, desc = "Remove fallback")
                            }
                            ChipRow(providers.map { providerChip(it) }, providers.indexOf(ep.provider)) { k -> updFallback(i) { it.copy(provider = providers[k]) } }
                            GlassField(ep.model, { v -> updFallback(i) { it.copy(model = v) } }, "Model", placeholder = ep.provider.defaultModel)
                            GlassField(ep.apiKey, { v -> updFallback(i) { it.copy(apiKey = v) } }, "API key", password = !showKey)
                            GlassField(ep.baseUrl, { v -> updFallback(i) { it.copy(baseUrl = v) } }, "Base URL (optional)", placeholder = ep.provider.defaultBase)
                        }
                    }
                    GlassButton(
                        "Add fallback",
                        { vm.update { st -> st.copy(aiFallbacks = st.aiFallbacks + AiEndpoint(provider = AiProvider.values().first { it != st.aiProvider })) } },
                        icon = Icons.Rounded.Add, compact = true, enabled = s.aiFallbacks.size < 5
                    )

                    GlassButton("Test connection", { vm.testAi() }, Modifier.fillMaxWidth(), primary = true)

                    SwitchRow("Advanced options", openMap["ai_adv"] == true, { openMap["ai_adv"] = it })
                    if (openMap["ai_adv"] == true) {
                        GlassField(s.aiBaseUrl, { v -> vm.update { it.copy(aiBaseUrl = v) } }, "Base URL", placeholder = s.aiProvider.defaultBase)
                        Hint("For a local server (Ollama, LM Studio) pick OpenAI-compat and enter e.g. http://192.168.1.10:11434/v1 with a vision model.")
                        GlassField(
                            temp, { v -> temp = v; vm.update { it.copy(aiTemperature = v.replace(',', '.').toDoubleOrNull()) } },
                            "Temperature (blank = provider default)", keyboardType = KeyboardType.Decimal
                        )
                        SwitchRow("Request JSON mode", s.aiJsonMode, { v -> vm.update { it.copy(aiJsonMode = v) } }, "Turn off if your provider rejects response_format.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("Max image px", s.aiMaxImagePx.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(aiMaxImagePx = v.toInt()) } }
                            NumField("JPEG quality", s.aiJpegQuality.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(aiJpegQuality = v.toInt()) } }
                            NumField("Timeout s", s.aiTimeoutSec.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(aiTimeoutSec = v.toInt()) } }
                        }
                        GlassField(s.aiPrompt, { v -> vm.update { it.copy(aiPrompt = v) } }, "System prompt", singleLine = false, minLines = 4)
                        GlassButton("Reset prompt", { vm.update { it.copy(aiPrompt = DEFAULT_AI_PROMPT) } }, compact = true)
                    }
                }

                // ---------------- Food database ----------------
                Section("Food database", s.foodProvider.label, Icons.Rounded.Restaurant, openMap) {
                    val fp = FoodProvider.values().toList()
                    ChipRow(fp.map { if (it == FoodProvider.USDA) "USDA" else "Open Food Facts" }, fp.indexOf(s.foodProvider)) { i -> vm.update { it.copy(foodProvider = fp[i]) } }
                    Hint("Used when you search for ingredients. Barcodes always check your own saved products, then Open Food Facts, then USDA.")
                    GlassField(s.foodLanguage, { v -> vm.update { it.copy(foodLanguage = v) } }, "Product language (blank = device language)", placeholder = "pt")
                    GlassField(s.offBaseUrl, { v -> vm.update { it.copy(offBaseUrl = v) } }, "Open Food Facts URL")
                    GlassField(s.usdaApiKey, { v -> vm.update { it.copy(usdaApiKey = v) } }, "USDA API key (DEMO_KEY works, rate limited)")
                }

                // ---------------- Goals ----------------
                Section("Goals", "${s.targetKcal().roundToInt()} kcal per day", Icons.Rounded.Flag, openMap) {
                    SwitchRow("Calculate my target automatically", s.autoGoal, { v -> vm.update { it.copy(autoGoal = v) } }, "Uses the Mifflin-St Jeor formula")
                    if (s.autoGoal) {
                        ChipRow(listOf("Male", "Female"), s.sex.ordinal) { i -> vm.update { it.copy(sex = Sex.values()[i]) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("Age", s.age.toDouble(), Modifier.weight(1f), true) { v -> vm.update { it.copy(age = v.toInt()) } }
                            NumField("Height cm", s.heightCm, Modifier.weight(1f)) { v -> vm.update { it.copy(heightCm = v) } }
                            NumField("Weight kg", s.weightKg, Modifier.weight(1f)) { v -> vm.update { it.copy(weightKg = v) } }
                        }
                        Sub("Daily activity (without logged workouts)")
                        val acts = listOf("Sedentary" to 1.2, "Light" to 1.375, "Moderate" to 1.55, "Active" to 1.725)
                        ChipRow(acts.map { it.first }, acts.indexOfFirst { abs(it.second - s.activityFactor) < 0.001 }) { i -> vm.update { it.copy(activityFactor = acts[i].second) } }
                    } else {
                        NumField("Daily calorie target", s.manualGoalKcal.toDouble(), integer = true) { v -> vm.update { it.copy(manualGoalKcal = v.toInt()) } }
                    }
                    Sub("Goal")
                    val goals = listOf("Lose" to -500, "Maintain" to 0, "Gain" to 300)
                    ChipRow(goals.map { it.first }, goals.indexOfFirst { it.second == s.goalDeltaKcal }) { i -> vm.update { it.copy(goalDeltaKcal = goals[i].second) }; refresh++ }
                    NumField("Daily surplus (+) / deficit (−) in kcal", s.goalDeltaKcal.toDouble(), integer = true) { v -> vm.update { it.copy(goalDeltaKcal = v.toInt()) } }
                    Hint("Target ${s.targetKcal().roundToInt()} kcal per day · maintenance ${s.maintenanceKcal().roundToInt()} kcal")
                    s.targetWarning()?.let { T(it, 12.sp, color = c.over) }

                    Sub("Workout calories added to your budget: ${s.exerciseCreditPct}%")
                    LumenSlider(s.exerciseCreditPct / 100f, { v -> vm.update { it.copy(exerciseCreditPct = (v * 10).roundToInt() * 10) } }, steps = 9)
                    Sub("\"On target\" tolerance: ±${s.tolerancePct}%")
                    LumenSlider(s.tolerancePct / 20f, { v -> vm.update { it.copy(tolerancePct = (v * 20).roundToInt()) } }, steps = 19)
                    NumField("Day starts at hour (0-12)", s.dayStartHour.toDouble(), integer = true) { v -> vm.update { it.copy(dayStartHour = v.toInt()) } }
                    Hint("Set to 4 to count late-night snacks toward the previous day.")
                }

                // ---------------- Logging ----------------
                Section("Logging", if (s.confirmBeforeSaving) "Review before saving" else "Saves instantly", Icons.Rounded.Tune, openMap) {
                    SwitchRow("Review before saving", s.confirmBeforeSaving, { v -> vm.update { it.copy(confirmBeforeSaving = v) } }, "Off = AI results are logged immediately.")
                    SwitchRow("Auto-log scanned barcodes", s.barcodeAutoLog, { v -> vm.update { it.copy(barcodeAutoLog = v) } }, "Logs one serving without asking.")
                    SwitchRow("Show protein / carbs / fat", s.showMacros, { v -> vm.update { it.copy(showMacros = v) } })
                    SwitchRow("Haptic feedback", s.haptics, { v -> vm.update { it.copy(haptics = v) } })
                    NumField("Default serving (g)", s.defaultServingGrams.toDouble(), integer = true) { v -> vm.update { it.copy(defaultServingGrams = v.toInt()) } }
                }

                // ---------------- Health Connect ----------------
                Section(
                    "Health Connect", if (s.hcEnabled) "Auto-import on · goal ${fmtThousands(s.stepGoal.toLong())} steps" else "Auto-import off",
                    Icons.Rounded.Favorite, openMap
                ) {
                    SwitchRow("Auto-import workouts", s.hcEnabled, { v -> vm.update { it.copy(hcEnabled = v) }; if (v) vm.syncHealth() }, "Grant access first from the Activity tab.")
                    NumField("Look back (days)", s.hcLookbackDays.toDouble(), integer = true) { v -> vm.update { it.copy(hcLookbackDays = v.toInt()) } }
                    NumField("Daily step goal", s.stepGoal.toDouble(), integer = true) { v -> vm.update { it.copy(stepGoal = v.toInt()) } }
                    SwitchRow(
                        "Add step calories to burned", s.stepsCredit, { v -> vm.update { it.copy(stepsCredit = v) } },
                        "Off by default: your activity level and logged workouts already cover most walking, so this can double count."
                    )
                }

                // ---------------- Appearance ----------------
                Section("Appearance", "${s.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }} · ${s.accentHex}", Icons.Rounded.Palette, openMap) {
                    Sub("Theme")
                    val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
                    ChipRow(listOf("Light", "Dark", "System"), modes.indexOf(s.themeMode)) { i -> vm.update { it.copy(themeMode = modes[i]) } }
                    Sub("Accent color")
                    AccentPicker(s.accentHex) { hex -> vm.update { it.copy(accentHex = hex) } }
                    Sub("Glass strength")
                    LumenSlider(s.glassStrength, { v -> vm.update { it.copy(glassStrength = v) } })
                }

                // ---------------- Backup ----------------
                Section("Backup & sharing", "Export, import, reset", Icons.Rounded.Backup, openMap) {
                    Hint("Export copies every setting except API keys. Import merges JSON over your current settings, so partial files work.")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Copy settings", { clipboard.setText(AnnotatedString(vm.exportJson())) }, Modifier.weight(1f), compact = true)
                        GlassButton("Paste JSON", { showImport = true }, Modifier.weight(1f), compact = true)
                    }
                    GlassField(s.configUrl, { v -> vm.update { it.copy(configUrl = v) } }, "Remote config URL (optional)", keyboardType = KeyboardType.Uri)
                    GlassButton("Fetch & apply", { vm.importUrl(s.configUrl) }, Modifier.fillMaxWidth(), enabled = s.configUrl.isNotBlank(), compact = true)
                    Hint("Only use a URL you control: a config can change the AI endpoint, which receives your photos and key.")
                    GlassButton("Reset all settings", { vm.resetSettings(); temp = ""; refresh++ }, Modifier.fillMaxWidth(), compact = true)
                }
            }
            Spacer(Modifier.height(130.dp))
        }
    }

    if (showImport) AlertDialog(
        onDismissRequest = { showImport = false }, containerColor = c.bgBottom,
        title = { T("Import settings", 18.sp, FontWeight.SemiBold) },
        text = { GlassField(importText, { importText = it }, "Settings JSON", singleLine = false, minLines = 6) },
        confirmButton = { DialogButton("Import", { if (vm.importJson(importText)) { showImport = false; importText = ""; refresh++ } }) },
        dismissButton = { DialogButton("Cancel", { showImport = false }, color = c.dim) }
    )
}
