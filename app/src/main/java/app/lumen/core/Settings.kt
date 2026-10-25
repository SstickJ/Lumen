package app.lumen.core

import org.json.JSONObject

enum class AiProvider(val label: String, val defaultBase: String, val defaultModel: String) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-flash"),
    OPENAI("OpenAI-compatible", "https://api.openai.com/v1", "gpt-4o-mini"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com", "claude-haiku-5-5")
}

enum class FoodProvider(val label: String) { OPEN_FOOD_FACTS("Open Food Facts"), USDA("USDA FoodData Central") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Sex { MALE, FEMALE }

/** Everything configurable at runtime. Stored as one JSON blob, so import/export/remote-config are trivial. */
data class Settings(
    // AI vision
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val aiBaseUrl: String = "",
    val aiModel: String = "",
    val aiApiKey: String = "",
    val aiPrompt: String = DEFAULT_AI_PROMPT,
    val aiTemperature: Double? = null,
    val aiJsonMode: Boolean = true,
    val aiMaxImagePx: Int = 1024,
    val aiJpegQuality: Int = 85,
    val aiTimeoutSec: Int = 60,
    // Food database
    val foodProvider: FoodProvider = FoodProvider.OPEN_FOOD_FACTS,
    val offBaseUrl: String = "https://world.openfoodfacts.org",
    val usdaApiKey: String = "DEMO_KEY",
    // Goal / energy
    val autoGoal: Boolean = true,
    val manualGoalKcal: Int = 2000,
    val sex: Sex = Sex.MALE,
    val age: Int = 25,
    val heightCm: Double = 175.0,
    val weightKg: Double = 70.0,
    val activityFactor: Double = 1.2,
    val goalDeltaKcal: Int = 0,
    val exerciseCreditPct: Int = 100,
    val tolerancePct: Int = 5,
    val dayStartHour: Int = 0,
    val graphDays: Int = 7,
    // Behaviour
    val confirmBeforeSaving: Boolean = true,
    val barcodeAutoLog: Boolean = false,
    val defaultServingGrams: Int = 100,
    val showMacros: Boolean = true,
    val haptics: Boolean = true,
    // Health Connect (Mi Fitness)
    val hcEnabled: Boolean = false,
    val hcLookbackDays: Int = 7,
    // Look
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String = "#5B9BFF",
    val glassStrength: Float = 0.5f,
    // Remote config
    val configUrl: String = ""
) {
    val effectiveBase: String get() = aiBaseUrl.ifBlank { aiProvider.defaultBase }.trim().trimEnd('/')
    val effectiveModel: String get() = aiModel.ifBlank { aiProvider.defaultModel }.trim()

    fun sanitized(): Settings = copy(
        aiMaxImagePx = aiMaxImagePx.coerceIn(256, 4096),
        aiJpegQuality = aiJpegQuality.coerceIn(30, 100),
        aiTimeoutSec = aiTimeoutSec.coerceIn(10, 300),
        manualGoalKcal = manualGoalKcal.coerceIn(500, 10000),
        age = age.coerceIn(10, 120),
        heightCm = heightCm.coerceIn(80.0, 250.0),
        weightKg = weightKg.coerceIn(25.0, 400.0),
        activityFactor = activityFactor.coerceIn(1.0, 2.5),
        goalDeltaKcal = goalDeltaKcal.coerceIn(-1500, 1500),
        exerciseCreditPct = exerciseCreditPct.coerceIn(0, 100),
        tolerancePct = tolerancePct.coerceIn(0, 30),
        dayStartHour = dayStartHour.coerceIn(0, 12),
        graphDays = if (graphDays in listOf(7, 14, 30)) graphDays else 7,
        defaultServingGrams = defaultServingGrams.coerceIn(1, 2000),
        hcLookbackDays = hcLookbackDays.coerceIn(1, 60),
        glassStrength = glassStrength.coerceIn(0f, 1f),
        accentHex = if (parseHexColor(accentHex) != null) accentHex else "#5B9BFF"
    )

    fun toJson(includeSecrets: Boolean = true): JSONObject = JSONObject().apply {
        put("v", 1)
        put("aiProvider", aiProvider.name)
        put("aiBaseUrl", aiBaseUrl)
        put("aiModel", aiModel)
        if (includeSecrets) put("aiApiKey", aiApiKey)
        put("aiPrompt", aiPrompt)
        put("aiTemperature", aiTemperature ?: JSONObject.NULL)
        put("aiJsonMode", aiJsonMode)
        put("aiMaxImagePx", aiMaxImagePx)
        put("aiJpegQuality", aiJpegQuality)
        put("aiTimeoutSec", aiTimeoutSec)
        put("foodProvider", foodProvider.name)
        put("offBaseUrl", offBaseUrl)
        if (includeSecrets) put("usdaApiKey", usdaApiKey)
        put("autoGoal", autoGoal)
        put("manualGoalKcal", manualGoalKcal)
        put("sex", sex.name)
        put("age", age)
        put("heightCm", heightCm)
        put("weightKg", weightKg)
        put("activityFactor", activityFactor)
        put("goalDeltaKcal", goalDeltaKcal)
        put("exerciseCreditPct", exerciseCreditPct)
        put("tolerancePct", tolerancePct)
        put("dayStartHour", dayStartHour)
        put("graphDays", graphDays)
        put("confirmBeforeSaving", confirmBeforeSaving)
        put("barcodeAutoLog", barcodeAutoLog)
        put("defaultServingGrams", defaultServingGrams)
        put("showMacros", showMacros)
        put("haptics", haptics)
        put("hcEnabled", hcEnabled)
        put("hcLookbackDays", hcLookbackDays)
        put("themeMode", themeMode.name)
        put("accentHex", accentHex)
        put("glassStrength", glassStrength.toDouble())
        put("configUrl", configUrl)
    }

    companion object {
        /** Merge [j] over [base]: keys missing from [j] keep their current value (partial imports are fine). */
        fun fromJson(j: JSONObject, base: Settings = Settings()): Settings {
            fun s(k: String, d: String) = if (j.has(k) && !j.isNull(k)) j.optString(k, d) else d
            fun i(k: String, d: Int) = if (j.has(k) && !j.isNull(k)) j.optInt(k, d) else d
            fun dbl(k: String, d: Double) = if (j.has(k) && !j.isNull(k)) j.optDouble(k, d) else d
            fun b(k: String, d: Boolean) = if (j.has(k) && !j.isNull(k)) j.optBoolean(k, d) else d
            fun <E : Enum<E>> e(k: String, d: E, all: Array<E>): E {
                val name = if (j.has(k) && !j.isNull(k)) j.optString(k) else return d
                return all.firstOrNull { it.name == name } ?: d
            }
            return base.copy(
                aiProvider = e("aiProvider", base.aiProvider, AiProvider.values()),
                aiBaseUrl = s("aiBaseUrl", base.aiBaseUrl),
                aiModel = s("aiModel", base.aiModel),
                aiApiKey = s("aiApiKey", base.aiApiKey),
                aiPrompt = s("aiPrompt", base.aiPrompt).ifBlank { DEFAULT_AI_PROMPT },
                aiTemperature = if (j.has("aiTemperature")) (if (j.isNull("aiTemperature")) null else j.optDouble("aiTemperature")) else base.aiTemperature,
                aiJsonMode = b("aiJsonMode", base.aiJsonMode),
                aiMaxImagePx = i("aiMaxImagePx", base.aiMaxImagePx),
                aiJpegQuality = i("aiJpegQuality", base.aiJpegQuality),
                aiTimeoutSec = i("aiTimeoutSec", base.aiTimeoutSec),
                foodProvider = e("foodProvider", base.foodProvider, FoodProvider.values()),
                offBaseUrl = s("offBaseUrl", base.offBaseUrl),
                usdaApiKey = s("usdaApiKey", base.usdaApiKey),
                autoGoal = b("autoGoal", base.autoGoal),
                manualGoalKcal = i("manualGoalKcal", base.manualGoalKcal),
                sex = e("sex", base.sex, Sex.values()),
                age = i("age", base.age),
                heightCm = dbl("heightCm", base.heightCm),
                weightKg = dbl("weightKg", base.weightKg),
                activityFactor = dbl("activityFactor", base.activityFactor),
                goalDeltaKcal = i("goalDeltaKcal", base.goalDeltaKcal),
                exerciseCreditPct = i("exerciseCreditPct", base.exerciseCreditPct),
                tolerancePct = i("tolerancePct", base.tolerancePct),
                dayStartHour = i("dayStartHour", base.dayStartHour),
                graphDays = i("graphDays", base.graphDays),
                confirmBeforeSaving = b("confirmBeforeSaving", base.confirmBeforeSaving),
                barcodeAutoLog = b("barcodeAutoLog", base.barcodeAutoLog),
                defaultServingGrams = i("defaultServingGrams", base.defaultServingGrams),
                showMacros = b("showMacros", base.showMacros),
                haptics = b("haptics", base.haptics),
                hcEnabled = b("hcEnabled", base.hcEnabled),
                hcLookbackDays = i("hcLookbackDays", base.hcLookbackDays),
                themeMode = e("themeMode", base.themeMode, ThemeMode.values()),
                accentHex = s("accentHex", base.accentHex),
                glassStrength = dbl("glassStrength", base.glassStrength.toDouble()).toFloat(),
                configUrl = s("configUrl", base.configUrl)
            ).sanitized()
        }
    }
}
