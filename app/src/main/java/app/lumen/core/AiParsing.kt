package app.lumen.core

import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_AI_PROMPT = """You are a nutrition analyst. The user sends one photo of a meal or food item. Identify the dish and break it into its visible ingredients/components. Estimate each portion in grams (use plate size, cutlery, packaging and other objects for scale) and the calories and macros for that portion. Reply with ONLY a JSON object, no markdown, shaped exactly like: {"is_food": true, "dish": "short name", "confidence": 0.0, "ingredients": [{"name": "...", "grams": 0, "kcal": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0}], "notes": "optional short caveat"}. kcal, protein_g, carbs_g and fat_g are totals for the stated grams, not per 100 g. Include cooking oil, sauces and dressings when likely present. If the photo contains no food set is_food to false and ingredients to []. Be realistic and do not under-estimate."""

const val AI_USER_TEXT = "Analyze this meal."

class AiParseException(message: String) : Exception(message)

data class HttpSpec(val url: String, val headers: Map<String, String>, val body: String)

fun buildAiRequest(s: Settings, imageB64: String): HttpSpec {
    val base = s.effectiveBase
    val model = s.effectiveModel
    return when (s.aiProvider) {
        AiProvider.GEMINI -> {
            val gen = JSONObject()
            if (s.aiJsonMode) gen.put("responseMimeType", "application/json")
            s.aiTemperature?.let { gen.put("temperature", it) }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", s.aiPrompt))))
                .put("contents", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray()
                        .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageB64)))
                        .put(JSONObject().put("text", AI_USER_TEXT)))))
                .put("generationConfig", gen)
            val headers = mutableMapOf<String, String>()
            if (s.aiApiKey.isNotBlank()) headers["x-goog-api-key"] = s.aiApiKey.trim()
            HttpSpec("$base/v1beta/models/$model:generateContent", headers, body.toString())
        }
        AiProvider.OPENAI -> {
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val userContent = JSONArray()
                .put(JSONObject().put("type", "text").put("text", AI_USER_TEXT))
                .put(JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageB64")))
            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", s.aiPrompt))
                    .put(JSONObject().put("role", "user").put("content", userContent)))
            if (s.aiJsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
            s.aiTemperature?.let { body.put("temperature", it) }
            val headers = mutableMapOf<String, String>()
            if (s.aiApiKey.isNotBlank()) headers["Authorization"] = "Bearer ${s.aiApiKey.trim()}"
            HttpSpec(url, headers, body.toString())
        }
        AiProvider.ANTHROPIC -> {
            val url = if (base.endsWith("/messages")) base else "$base/v1/messages"
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 1500)
                .put("system", s.aiPrompt)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray()
                    .put(JSONObject().put("type", "image").put("source", JSONObject()
                        .put("type", "base64").put("media_type", "image/jpeg").put("data", imageB64)))
                    .put(JSONObject().put("type", "text").put("text", AI_USER_TEXT)))))
            s.aiTemperature?.let { body.put("temperature", it) }
            val headers = mutableMapOf("anthropic-version" to "2023-06-01")
            if (s.aiApiKey.isNotBlank()) headers["x-api-key"] = s.aiApiKey.trim()
            HttpSpec(url, headers, body.toString())
        }
    }
}

/** Pull the model's text out of a provider response. Throws [AiParseException] with the provider's error message. */
fun extractAiText(provider: AiProvider, responseBody: String): String {
    val o = try { JSONObject(responseBody) } catch (e: Exception) { throw AiParseException("Provider returned a non-JSON response") }
    o.optJSONObject("error")?.let { throw AiParseException(it.optString("message", "Provider error")) }
    return when (provider) {
        AiProvider.GEMINI -> {
            val cands = o.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val reason = o.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw AiParseException(if (reason.isNotEmpty()) "Request blocked by Gemini: $reason" else "Gemini returned no candidates")
            }
            val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
            sb.toString().ifBlank { throw AiParseException("Gemini returned an empty reply") }
        }
        AiProvider.OPENAI -> {
            val choices = o.optJSONArray("choices")
            if (choices == null || choices.length() == 0) throw AiParseException("Provider returned no choices")
            val content = choices.getJSONObject(0).optJSONObject("message")?.opt("content")
            val text = when (content) {
                is String -> content
                is JSONArray -> buildString {
                    for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text", "") ?: "")
                }
                else -> ""
            }
            text.ifBlank { throw AiParseException("Provider returned an empty reply") }
        }
        AiProvider.ANTHROPIC -> {
            val arr = o.optJSONArray("content") ?: throw AiParseException("Anthropic returned no content")
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                if (b.optString("type") == "text") sb.append(b.optString("text", ""))
            }
            sb.toString().ifBlank { throw AiParseException("Anthropic returned an empty reply") }
        }
    }
}

fun extractJsonObject(text: String): JSONObject {
    val s = text.trim()
    val start = s.indexOf('{')
    val end = s.lastIndexOf('}')
    if (start < 0 || end <= start) throw AiParseException("The model reply did not contain JSON")
    return try { JSONObject(s.substring(start, end + 1)) } catch (e: Exception) { throw AiParseException("The model reply was not valid JSON") }
}

fun parseAiMeal(text: String): AiMeal {
    val o = extractJsonObject(text)
    val isFood = o.optBoolean("is_food", true)
    val arr = o.optJSONArray("ingredients")
    val list = mutableListOf<IngredientDraft>()
    if (arr != null) for (i in 0 until arr.length()) {
        val ing = arr.optJSONObject(i) ?: continue
        val name = ing.optString("name", "").trim()
        val grams = ing.optDouble("grams", Double.NaN)
        val kcal = ing.optDouble("kcal", Double.NaN)
        if (name.isEmpty() || grams.isNaN() || kcal.isNaN() || grams <= 0.0 || kcal < 0.0) continue
        list += IngredientDraft.fromTotals(
            name, grams, kcal,
            ing.optDouble("protein_g", 0.0).let { if (it.isNaN()) 0.0 else it },
            ing.optDouble("carbs_g", 0.0).let { if (it.isNaN()) 0.0 else it },
            ing.optDouble("fat_g", 0.0).let { if (it.isNaN()) 0.0 else it }
        )
    }
    if (isFood && list.isEmpty()) throw AiParseException("The model did not return any usable ingredients")
    val conf = o.optDouble("confidence", Double.NaN).takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0)
    val dish = o.optString("dish", "").trim().ifEmpty { list.firstOrNull()?.name ?: "Meal" }
    val notes = o.optString("notes", "").trim().ifEmpty { null }
    return AiMeal(dish, isFood, conf, notes, if (isFood) list else emptyList())
}
