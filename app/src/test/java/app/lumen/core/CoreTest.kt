package app.lumen.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CoreTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    // ---------- energy ----------
    @Test fun bmrMaleAndFemale() {
        assertEquals(1780.0, bmr(Sex.MALE, 30, 180.0, 80.0), 0.001)
        assertEquals(1614.0, bmr(Sex.FEMALE, 30, 180.0, 80.0), 0.001)
    }

    @Test fun targetsAutoAndManual() {
        val auto = Settings(sex = Sex.MALE, age = 30, heightCm = 180.0, weightKg = 80.0, activityFactor = 1.2, goalDeltaKcal = -500)
        assertEquals(2136.0, auto.maintenanceKcal(), 0.001)
        assertEquals(1636.0, auto.targetKcal(), 0.001)
        val manual = Settings(autoGoal = false, manualGoalKcal = 2000, goalDeltaKcal = -300)
        assertEquals(2000.0, manual.targetKcal(), 0.001)
        assertEquals(2300.0, manual.maintenanceKcal(), 0.001)
        assertNull(auto.targetWarning())
        assertNotNull(Settings(autoGoal = false, manualGoalKcal = 900).targetWarning())
    }

    @Test fun logicalDayWithLateStart() {
        val ms = java.time.ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, lisbon).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 25), dayOf(ms, lisbon, 0))
        assertEquals(LocalDate.of(2026, 10, 24), dayOf(ms, lisbon, 4))
    }

    @Test fun dayRangeIsDstSafe() {
        // Europe/Lisbon leaves DST on 2026-10-25, that day has 25 hours.
        val (s, e) = dayRangeMs(LocalDate.of(2026, 10, 25), lisbon, 0)
        assertEquals(25L * 3600_000L, e - s)
        val (s2, e2) = dayRangeMs(LocalDate.of(2026, 10, 26), lisbon, 0)
        assertEquals(e, s2)
        assertEquals(24L * 3600_000L, e2 - s2)
    }

    // ---------- status ----------
    @Test fun statusKinds() {
        fun k(eaten: Double, ex: Double = 0.0, hour: Int = 14, today: Boolean = true) =
            dayStatus(eaten, ex, 2000.0, 100, 5, hour, today).kind
        assertEquals(StatusKind.EMPTY, k(0.0))
        assertEquals(StatusKind.ON_TRACK, k(900.0))
        assertEquals(StatusKind.ALMOST, k(1850.0))
        assertEquals(StatusKind.ON_TARGET, k(1950.0))
        assertEquals(StatusKind.ON_TARGET, k(2050.0))
        assertEquals(StatusKind.OVER, k(2300.0))
        assertEquals(StatusKind.UNDER, k(800.0, hour = 22))
        assertEquals(StatusKind.ON_TRACK, k(800.0, hour = 12))
        assertEquals(StatusKind.UNDER, k(800.0, hour = 12, today = false))
    }

    @Test fun exerciseExtendsBudget() {
        val s = dayStatus(2300.0, 400.0, 2000.0, 100, 5, 18)
        assertEquals(StatusKind.ON_TARGET, s.kind)
        assertEquals(100.0, s.remaining, 0.001)
        val half = dayStatus(2400.0, 400.0, 2000.0, 50, 5, 18)
        assertEquals(-200.0, half.remaining, 0.001)
        assertEquals(StatusKind.OVER, half.kind)
        assertEquals(StatusKind.ON_TARGET, dayStatus(2000.0, 0.0, 2000.0, 100, 5, 12).kind)
        assertTrue(dayStatus(1500.0, 400.0, 2000.0, 100, 5, 12).detail.contains("900"))
    }

    @Test fun verdicts() {
        // maintenance 2300, goal delta -300 -> target 2000
        assertEquals(DayVerdict.EMPTY, dayVerdict(0.0, 0.0, 2300.0, -300.0, 2000.0))
        assertEquals(DayVerdict.GOOD, dayVerdict(2000.0, 0.0, 2300.0, -300.0, 2000.0))
        assertEquals(DayVerdict.OVER, dayVerdict(2500.0, 0.0, 2300.0, -300.0, 2000.0))
        assertEquals(DayVerdict.UNDER, dayVerdict(1000.0, 0.0, 2300.0, -300.0, 2000.0))
        assertEquals(DayVerdict.GOOD, dayVerdict(2500.0, 500.0, 2300.0, -300.0, 2000.0))
        assertEquals(-300.0, netKcal(2000.0, 0.0, 2300.0), 0.001)
    }

    // ---------- ingredients ----------
    @Test fun ingredientScaling() {
        val i = IngredientDraft.fromTotals("Rice", 200.0, 260.0, 5.0, 56.0, 0.6)
        assertEquals(130.0, i.kcalPer100, 0.001)
        assertEquals(260.0, i.kcal, 0.001)
        val half = i.copy(grams = 100.0)
        assertEquals(130.0, half.kcal, 0.001)
        val t = listOf(i, half).totals()
        assertEquals(390.0, t.kcal, 0.001)
        assertEquals(7.5, t.protein, 0.001)
    }

    @Test fun mealsAndColors() {
        assertEquals("Breakfast", mealForHour(8)); assertEquals("Lunch", mealForHour(13))
        assertEquals("Snack", mealForHour(16)); assertEquals("Dinner", mealForHour(20)); assertEquals("Dinner", mealForHour(2))
        assertEquals(0xFF5B9BFFL, parseHexColor("#5B9BFF"))
        assertEquals(0x805B9BFFL, parseHexColor("805B9BFF"))
        assertNull(parseHexColor("blue")); assertNull(parseHexColor("#12"))
    }

    // ---------- AI parsing ----------
    private val goodReply = """
        ```json
        {"is_food": true, "dish": "Chicken rice bowl", "confidence": 0.82,
         "ingredients": [
           {"name": "Grilled chicken", "grams": 120, "kcal": 198, "protein_g": 37, "carbs_g": 0, "fat_g": 4},
           {"name": "White rice", "grams": 180, "kcal": 234, "protein_g": 4.5, "carbs_g": 51, "fat_g": 0.5},
           {"name": "bad", "grams": 0, "kcal": 10},
           {"name": "Olive oil", "grams": "10", "kcal": "88", "fat_g": 10}
         ], "notes": "Portion estimated from bowl size."}
        ```
    """.trimIndent()

    @Test fun parsesFencedMealJson() {
        val m = parseAiMeal(goodReply)
        assertEquals("Chicken rice bowl", m.dish)
        assertEquals(3, m.ingredients.size)
        assertEquals(0.82, m.confidence!!, 0.0001)
        assertEquals(198.0 + 234.0 + 88.0, m.ingredients.totals().kcal, 0.01)
        assertEquals("Portion estimated from bowl size.", m.notes)
    }

    @Test fun notFoodAndBrokenReplies() {
        val nf = parseAiMeal("""{"is_food": false, "dish": "", "ingredients": []}""")
        assertFalse(nf.isFood); assertTrue(nf.ingredients.isEmpty())
        assertThrows { parseAiMeal("sorry I cannot help") }
        assertThrows { parseAiMeal("""{"is_food": true, "ingredients": []}""") }
        assertThrows { parseAiMeal("{ not json }") }
    }

    private fun assertThrows(block: () -> Unit) {
        try { block() } catch (e: AiParseException) { return }
        fail("expected AiParseException")
    }

    @Test fun geminiRequest() {
        val s = Settings(aiProvider = AiProvider.GEMINI, aiApiKey = " KEY ", aiTemperature = 0.3)
        val r = buildAiRequest(s, "QUJD")
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", r.url)
        assertEquals("KEY", r.headers["x-goog-api-key"])
        val b = JSONObject(r.body)
        val part = b.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0)
        assertEquals("QUJD", part.getJSONObject("inline_data").getString("data"))
        assertEquals("application/json", b.getJSONObject("generationConfig").getString("responseMimeType"))
        assertEquals(0.3, b.getJSONObject("generationConfig").getDouble("temperature"), 0.0001)
        assertEquals(DEFAULT_AI_PROMPT, b.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test fun openAiRequestVariants() {
        val s = Settings(aiProvider = AiProvider.OPENAI, aiBaseUrl = "http://192.168.1.50:11434/v1/", aiModel = "llava", aiJsonMode = false)
        val r = buildAiRequest(s, "QUJD")
        assertEquals("http://192.168.1.50:11434/v1/chat/completions", r.url)
        assertTrue(r.headers.isEmpty())   // no key -> no auth header (Ollama / LM Studio)
        val b = JSONObject(r.body)
        assertFalse(b.has("response_format")); assertFalse(b.has("temperature"))
        assertEquals("llava", b.getString("model"))
        val img = b.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(1)
        assertTrue(img.getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
        val full = buildAiRequest(s.copy(aiBaseUrl = "https://x.test/v1/chat/completions", aiApiKey = "k", aiJsonMode = true), "QQ")
        assertEquals("https://x.test/v1/chat/completions", full.url)
        assertEquals("Bearer k", full.headers["Authorization"])
        assertEquals("json_object", JSONObject(full.body).getJSONObject("response_format").getString("type"))
    }

    @Test fun anthropicRequest() {
        val r = buildAiRequest(Settings(aiProvider = AiProvider.ANTHROPIC, aiApiKey = "sk"), "QUJD")
        assertEquals("https://api.anthropic.com/v1/messages", r.url)
        assertEquals("sk", r.headers["x-api-key"]); assertEquals("2023-06-01", r.headers["anthropic-version"])
        val b = JSONObject(r.body)
        assertEquals("claude-haiku-5-5", b.getString("model"))
        assertEquals("image", b.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("type"))
    }

    @Test fun responseTextExtraction() {
        val g = """{"candidates":[{"content":{"parts":[{"text":"{\"a\":"},{"text":"1}"}]}}]}"""
        assertEquals("{\"a\":1}", extractAiText(AiProvider.GEMINI, g))
        val o1 = """{"choices":[{"message":{"content":"hi"}}]}"""
        assertEquals("hi", extractAiText(AiProvider.OPENAI, o1))
        val o2 = """{"choices":[{"message":{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}}]}"""
        assertEquals("ab", extractAiText(AiProvider.OPENAI, o2))
        val a = """{"content":[{"type":"text","text":"yo"}]}"""
        assertEquals("yo", extractAiText(AiProvider.ANTHROPIC, a))
        try { extractAiText(AiProvider.GEMINI, """{"error":{"message":"API key not valid"}}"""); fail() }
        catch (e: AiParseException) { assertEquals("API key not valid", e.message) }
        try { extractAiText(AiProvider.GEMINI, """{"promptFeedback":{"blockReason":"SAFETY"}}"""); fail() }
        catch (e: AiParseException) { assertTrue(e.message!!.contains("SAFETY")) }
        try { extractAiText(AiProvider.OPENAI, "<html>502</html>"); fail() }
        catch (e: AiParseException) { assertTrue(e.message!!.contains("non-JSON")) }
    }

    // ---------- food APIs ----------
    @Test fun barcodeNormalisation() {
        assertEquals("5449000000996", normalizeBarcode("5449000000996"))
        assertEquals("0012345678905", normalizeBarcode("012345678905"))
        assertEquals("12345670", normalizeBarcode("1234-5670"))
        assertNull(normalizeBarcode("123")); assertNull(normalizeBarcode("abc"))
    }

    @Test fun offProduct() {
        val j = """{"status":1,"code":"5449000000996","product":{"code":"5449000000996","product_name":"Coca-Cola","brands":"Coca-Cola, Other",
            "serving_quantity":"330","nutriments":{"energy-kcal_100g":42,"proteins_100g":0,"carbohydrates_100g":10.6,"fat_100g":0}}}"""
        val h = parseOffProduct(j, "5449000000996")!!
        assertEquals("Coca-Cola", h.name); assertEquals("Coca-Cola", h.brand)
        assertEquals(42.0, h.kcalPer100, 0.001); assertEquals(330.0, h.servingGrams!!, 0.001)
        assertTrue(h.hasNutrition)
        // kJ-only fallback: 1000 kJ ~ 239 kcal
        val kj = """{"status":1,"product":{"product_name":"Bar","nutriments":{"energy_100g":1000}}}"""
        assertEquals(239.0, parseOffProduct(kj, "123")!!.kcalPer100, 0.5)
        // product without any energy data
        val none = parseOffProduct("""{"status":1,"product":{"product_name":"Mystery","nutriments":{}}}""", "999")!!
        assertFalse(none.hasNutrition); assertEquals("999", none.barcode)
        assertNull(parseOffProduct("""{"status":0,"status_verbose":"product not found"}""", "1"))
        assertNull(parseOffProduct("garbage", "1"))
    }

    @Test fun offSearchAndUsdaSearch() {
        val off = """{"products":[{"code":"1","product_name":"Oats","nutriments":{"energy-kcal_100g":370}},
            {"code":"2","product_name":"NoData","nutriments":{}},{"code":"3","nutriments":{"energy-kcal_100g":1}}]}"""
        val offHits = parseOffSearch(off)
        assertEquals(1, offHits.size); assertEquals("Oats", offHits[0].name)
        val usda = """{"foods":[{"description":"CHICKEN BREAST, ROASTED","foodNutrients":[
            {"nutrientId":1008,"value":165},{"nutrientId":1003,"value":31},{"nutrientId":1004,"value":3.6},{"nutrientId":1005,"value":0}]},
            {"description":"No energy","foodNutrients":[{"nutrientId":1003,"value":1}]},
            {"description":"Branded thing","brandName":"ACME","servingSize":40,"servingSizeUnit":"g","gtinUpc":"0123","foodNutrients":[{"nutrientId":2047,"value":400}]}]}"""
        val u = parseUsdaSearch(usda)
        assertEquals(2, u.size)
        assertEquals("Chicken breast, roasted", u[0].name); assertEquals(165.0, u[0].kcalPer100, 0.001); assertEquals(31.0, u[0].proteinPer100, 0.001)
        assertEquals("ACME", u[1].brand); assertEquals(40.0, u[1].servingGrams!!, 0.001)
        assertTrue(parseUsdaSearch("{}").isEmpty()); assertTrue(parseOffSearch("nope").isEmpty())
    }

    @Test fun urlBuilders() {
        assertEquals("https://world.openfoodfacts.org/api/v2/product/5449000000996.json?fields=code,product_name,product_name_en,generic_name,abbreviated_product_name,brands,nutriments,serving_quantity",
            offProductUrl("https://world.openfoodfacts.org/", "5449000000996"))
        assertTrue(offProductUrl("https://x", "1", "PT").contains("product_name_pt"))
        assertTrue(offSearchUrl("https://x", "agua", "pt").contains("&lc=pt"))
        assertTrue(offSearchUrl("https://pt.openfoodfacts.org", "arroz & feijão").contains("search_terms=arroz+%26+feij%C3%A3o"))
        assertTrue(usdaSearchUrl("egg", "").endsWith("api_key=DEMO_KEY"))
    }

    // ---------- settings ----------
    @Test fun settingsRoundTripAndSecrets() {
        val s = Settings(aiProvider = AiProvider.OPENAI, aiApiKey = "secret", aiTemperature = 0.5, themeMode = ThemeMode.DARK,
            goalDeltaKcal = -400, graphDays = 14, accentHex = "#FF6B6B", glassStrength = 0.8f, usdaApiKey = "u")
        val back = Settings.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s, back)
        val safe = s.toJson(includeSecrets = false)
        assertFalse(safe.has("aiApiKey")); assertFalse(safe.has("usdaApiKey"))
        // importing a secret-free export keeps the existing key
        assertEquals("secret", Settings.fromJson(safe, s).aiApiKey)
    }

    @Test fun settingsPartialImportAndSanitising() {
        val base = Settings(aiApiKey = "keep", age = 40)
        val m = Settings.fromJson(JSONObject("""{"aiModel":"gemini-3-flash","age":9999,"graphDays":5,"accentHex":"nope","aiProvider":"BOGUS","aiTemperature":null,"aiPrompt":"  "}"""),
            base.copy(aiTemperature = 0.9))
        assertEquals("gemini-3-flash", m.aiModel); assertEquals("keep", m.aiApiKey)
        assertEquals(120, m.age); assertEquals(7, m.graphDays)
        assertEquals("#FFFFFF", m.accentHex); assertEquals(AiProvider.GEMINI, m.aiProvider)
        assertNull(m.aiTemperature)
        assertEquals(DEFAULT_AI_PROMPT, m.aiPrompt)
        assertEquals("gemini-3-flash", m.effectiveModel); assertEquals("gemini-2.5-flash", Settings().effectiveModel)
    }

    @Test fun exerciseEstimate() {
        assertEquals(588.0, estimateBurn(9.8, 60.0, 60), 0.001)
        assertEquals(294.0, estimateBurn(9.8, 60.0, 30), 0.001)
        assertTrue(EXERCISE_METS.any { it.first == "Running" })
    }

    @Test fun portugueseOnlyProductNameIsFound() {
        val pt = """{"status":1,"product":{"code":"5601234567890","product_name_pt":"Água Mineral Natural","brands":"Luso","nutriments":{"energy-kcal_100g":0}}}"""
        val h = parseOffProduct(pt, "5601234567890", "pt")!!
        assertEquals("Água Mineral Natural", h.name); assertTrue(h.hasNutrition); assertEquals(0.0, h.kcalPer100, 0.0001)
        // language not requested: still found through any product_name_* key
        assertEquals("Água Mineral Natural", parseOffProduct(pt, "5601234567890")!!.name)
        // no name at all: falls back to the brand, then to a placeholder, but is never "not found"
        assertEquals("Luso", parseOffProduct("""{"status":1,"product":{"brands":"Luso","nutriments":{}}}""", "1")!!.name)
        val bare = parseOffProduct("""{"status":1,"product":{"nutriments":{}}}""", "123")!!
        assertEquals("Unnamed product", bare.name); assertFalse(bare.hasNutrition); assertEquals("123", bare.barcode)
    }

    @Test fun burnPrefersActiveThenTotalThenEstimate() {
        val s = 1_000_000L; val e = s + 10 * 60_000L          // 10 minute workout
        val exact = EnergyRec(s, e, 4.0)
        assertEquals(4.0, pickBurn(s, e, listOf(exact), emptyList(), 50.0).kcal, 0.001)
        assertEquals("active", pickBurn(s, e, listOf(exact), emptyList(), 50.0).source)
        // a day-long active record gives only a sliver for the workout -> ignored, total record used
        val dayLong = EnergyRec(s - 12 * 3600_000L, s + 12 * 3600_000L, 20.0)
        val tot = EnergyRec(s, e, 4.2)
        val p = pickBurn(s, e, listOf(dayLong), listOf(tot), 50.0)
        assertEquals(4.2, p.kcal, 0.001); assertEquals("total", p.source)
        // nothing usable -> estimate, never 0
        val none = pickBurn(s, e, listOf(dayLong), emptyList(), 50.0)
        assertEquals(50.0, none.kcal, 0.001); assertEquals("estimate", none.source)
        // prorated when it is the only data and large enough
        val big = EnergyRec(s - 50 * 60_000L, e, 70.0)          // 60 min record, 10 min inside
        assertEquals(11.666, pickBurn(s, e, listOf(big), emptyList(), 50.0).kcal, 0.01)
        assertEquals(0.0, overlapKcal(s, e, EnergyRec(e + 1, e + 100, 9.0)), 0.0)
        assertEquals(3.0, overlapKcal(s, e, EnergyRec(s + 5, s + 5, 3.0)), 0.0)
    }

    @Test fun foodLanguageSetting() {
        assertEquals("pt", Settings.fromJson(JSONObject("""{"foodLanguage":" PT-pt! "}""")).foodLanguage.take(2))
        assertEquals("", Settings().foodLanguage)
    }

    @Test fun stepsAndGoalSettings() {
        assertEquals(40.0, stepsKcal(1000, 70.0), 0.001)
        assertEquals(80.0, stepsKcal(1000, 140.0), 0.001)
        assertEquals(0.0, stepsKcal(0, 70.0), 0.0)
        val d = Settings()
        assertEquals(8000, d.stepGoal); assertFalse(d.stepsCredit)
        val back = Settings.fromJson(JSONObject(Settings(stepGoal = 12000, stepsCredit = true).toJson().toString()))
        assertEquals(12000, back.stepGoal); assertTrue(back.stepsCredit)
        assertEquals(50000, Settings.fromJson(JSONObject("""{"stepGoal":999999}""")).stepGoal)
    }

    @Test fun fallbackChainOrderAndCredentials() {
        val s = Settings(
            aiProvider = AiProvider.GEMINI, aiApiKey = "g", aiModel = "gem-x",
            aiFallbacks = listOf(
                AiEndpoint(AiProvider.OPENAI, "", "gpt-x", "o"),
                AiEndpoint(AiProvider.OPENAI, "http://192.168.1.5:11434/v1", "llava", ""),
                AiEndpoint(AiProvider.ANTHROPIC, "", "", "")
            )
        )
        val chain = s.aiChain()
        assertEquals(4, chain.size)
        assertEquals(listOf(AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.OPENAI, AiProvider.ANTHROPIC), chain.map { it.aiProvider })
        assertEquals("gem-x", chain[0].effectiveModel); assertEquals("gpt-x", chain[1].effectiveModel)
        assertEquals("claude-haiku-5-5", chain[3].effectiveModel)
        assertTrue(chain[1].aiHasCredentials())      // has a key
        assertTrue(chain[2].aiHasCredentials())      // local server, no key needed
        assertFalse(chain[3].aiHasCredentials())     // no key
        assertTrue(chain.all { it.aiFallbacks.isEmpty() })
        assertFalse(Settings().aiUsable())
        assertTrue(Settings(aiFallbacks = listOf(AiEndpoint(AiProvider.ANTHROPIC, "", "", "k"))).aiUsable())
        // each request builder targets its own endpoint
        assertTrue(buildAiRequest(chain[2], "QQ").url.startsWith("http://192.168.1.5:11434/v1/chat/completions"))
        assertEquals("k", buildAiRequest(Settings(aiProvider = AiProvider.ANTHROPIC, aiApiKey = "k"), "QQ").headers["x-api-key"])
    }

    @Test fun fallbacksRoundTripAndSecrets() {
        val s = Settings(aiFallbacks = listOf(AiEndpoint(AiProvider.OPENAI, "http://h/v1", "m", "secret1"), AiEndpoint(AiProvider.ANTHROPIC, "", "c", "secret2")))
        assertEquals(s, Settings.fromJson(JSONObject(s.toJson().toString())))
        val safe = s.toJson(includeSecrets = false)
        assertFalse(safe.toString().contains("secret1")); assertFalse(safe.toString().contains("secret2"))
        val merged = Settings.fromJson(safe, s)
        assertEquals("secret1", merged.aiFallbacks[0].apiKey); assertEquals("secret2", merged.aiFallbacks[1].apiKey)
        assertEquals("m", merged.aiFallbacks[0].model)
        // too many entries are trimmed
        val many = JSONObject("""{"aiFallbacks":[{},{},{},{},{},{},{}]}""")
        assertEquals(5, Settings.fromJson(many).aiFallbacks.size)
    }

    @Test fun defaultLookIsDarkWithWhiteAccentAndOldSettingsMigrate() {
        assertEquals(ThemeMode.DARK, Settings().themeMode)
        assertEquals("#FFFFFF", Settings().accentHex)
        val old = JSONObject("""{"v":1,"themeMode":"SYSTEM","aiModel":"keep-me"}""")
        val migrated = Settings.fromJson(Settings.migrate(old))
        assertEquals(ThemeMode.DARK, migrated.themeMode); assertEquals("keep-me", migrated.aiModel)
        // already-migrated settings keep the user's choice
        val cur = JSONObject("""{"v":2,"themeMode":"LIGHT"}""")
        assertEquals(ThemeMode.LIGHT, Settings.fromJson(Settings.migrate(cur)).themeMode)
        assertEquals(2, Settings().toJson().getInt("v"))
    }

    @Test fun accentContrastHelpers() {
        assertEquals(1.0, relativeLuminance(0xFFFFFFFFL), 0.001); assertEquals(0.0, relativeLuminance(0xFF000000L), 0.001)
        // white accent: fine on dark, swapped for a readable grey on light
        assertEquals(0xFFFFFFFFL, readableAccent(0xFFFFFFFFL, dark = true))
        assertEquals(0xFF3A4254L, readableAccent(0xFFFFFFFFL, dark = false))
        // near-black accent on a dark theme is lightened; a normal colour is untouched
        val lifted = readableAccent(0xFF1F2937L, dark = true)
        assertTrue(relativeLuminance(lifted) > relativeLuminance(0xFF1F2937L) + 0.1); assertEquals(0xFFL, (lifted shr 24) and 0xFFL)
        assertEquals(0xFF5B9BFFL, readableAccent(0xFF5B9BFFL, dark = true)); assertEquals(0xFF5B9BFFL, readableAccent(0xFF5B9BFFL, dark = false))
        // text on the accent: dark on white / light blue, white on dark colours (the white-toggle bug)
        assertTrue(accentNeedsDarkText(0xFFFFFFFFL)); assertTrue(accentNeedsDarkText(0xFF5B9BFFL)); assertFalse(accentNeedsDarkText(0xFF1F2937L))
        assertTrue(isNeutralColor(0xFFFFFFFFL)); assertTrue(isNeutralColor(0xFF808080L)); assertFalse(isNeutralColor(0xFF5B9BFFL))
        assertEquals(0xFF808080L, mixArgb(0xFF000000L, 0xFFFFFFFFL, 0.5))
    }
}
