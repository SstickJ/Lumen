package app.lumen.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Digits only; 12-digit UPC-A is padded to EAN-13 (how Open Food Facts stores it). */
fun normalizeBarcode(raw: String): String? {
    val d = raw.filter { it.isDigit() }
    return when {
        d.length == 12 -> "0$d"
        d.length in 8..14 -> d
        else -> null
    }
}

private fun cleanLang(lang: String) = lang.filter { it.isLetter() }.take(5).lowercase()

private fun offFields(lang: String): String {
    val l = cleanLang(lang)
    val extra = if (l.isNotEmpty()) ",product_name_$l" else ""
    return "code,product_name,product_name_en$extra,generic_name,abbreviated_product_name,brands,nutriments,serving_quantity"
}

fun offProductUrl(base: String, barcode: String, lang: String = "") =
    "${base.trim().trimEnd('/')}/api/v2/product/${enc(barcode)}.json?fields=${offFields(lang)}"

fun offSearchUrl(base: String, query: String, lang: String = "") =
    "${base.trim().trimEnd('/')}/cgi/search.pl?search_terms=${enc(query)}&search_simple=1&action=process&json=1&page_size=25" +
        (if (cleanLang(lang).isNotEmpty()) "&lc=${cleanLang(lang)}" else "") + "&fields=${offFields(lang)}"

fun usdaSearchUrl(query: String, apiKey: String) =
    "https://api.nal.usda.gov/fdc/v1/foods/search?query=${enc(query)}&pageSize=25&dataType=Foundation,SR%20Legacy,Branded&api_key=${enc(apiKey.ifBlank { "DEMO_KEY" })}"

private fun JSONObject.num(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val v = optDouble(key, Double.NaN)
    return if (v.isNaN()) null else v
}

/** Many European products only carry a localized name (product_name_pt), so try the user's language and then anything. */
private fun offName(p: JSONObject, lang: String): String? {
    val l = cleanLang(lang)
    val keys = buildList {
        if (l.isNotEmpty()) add("product_name_$l")
        add("product_name"); add("product_name_en"); add("generic_name"); add("abbreviated_product_name")
    }
    keys.map { p.optString(it, "").trim() }.firstOrNull { it.isNotEmpty() }?.let { return it }
    val it = p.keys()
    while (it.hasNext()) {
        val k = it.next()
        if (k.startsWith("product_name")) p.optString(k, "").trim().let { v -> if (v.isNotEmpty()) return v }
    }
    return null
}

private fun offHit(p: JSONObject, fallbackCode: String?, lang: String): FoodHit {
    val nut = p.optJSONObject("nutriments")
    val kcal = nut?.num("energy-kcal_100g") ?: nut?.num("energy_100g")?.let { it / 4.184 }
    val brand = p.optString("brands", "").split(",").firstOrNull()?.trim()?.ifEmpty { null }
    val realName = offName(p, lang)
    val name = realName ?: brand ?: "Unnamed product"
    val serving = p.num("serving_quantity")?.takeIf { it > 0.0 }
    return FoodHit(
        name = name, brand = if (realName == null) null else brand,
        kcalPer100 = kcal ?: 0.0,
        proteinPer100 = nut?.num("proteins_100g") ?: 0.0,
        carbsPer100 = nut?.num("carbohydrates_100g") ?: 0.0,
        fatPer100 = nut?.num("fat_100g") ?: 0.0,
        servingGrams = serving,
        barcode = p.optString("code", "").ifEmpty { fallbackCode ?: "" }.ifEmpty { null },
        hasNutrition = kcal != null
    )
}

/** Returns null only when the barcode is unknown (status 0 / no product). */
fun parseOffProduct(json: String, barcode: String, lang: String = ""): FoodHit? {
    val o = try { JSONObject(json) } catch (e: Exception) { return null }
    if (o.optInt("status", 0) != 1) return null
    val p = o.optJSONObject("product") ?: return null
    return offHit(p, barcode, lang)
}

fun parseOffSearch(json: String, lang: String = ""): List<FoodHit> {
    val arr: JSONArray = try { JSONObject(json).optJSONArray("products") } catch (e: Exception) { null } ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { offHit(it, null, lang) } }.filter { it.hasNutrition && it.name != "Unnamed product" }
}

fun parseUsdaSearch(json: String): List<FoodHit> {
    val arr: JSONArray = try { JSONObject(json).optJSONArray("foods") } catch (e: Exception) { null } ?: return emptyList()
    val out = mutableListOf<FoodHit>()
    for (i in 0 until arr.length()) {
        val f = arr.optJSONObject(i) ?: continue
        val name = f.optString("description", "").trim()
        if (name.isEmpty()) continue
        val nuts = f.optJSONArray("foodNutrients") ?: continue
        val byId = HashMap<Int, Double>()
        for (k in 0 until nuts.length()) {
            val n = nuts.optJSONObject(k) ?: continue
            val id = n.optInt("nutrientId", -1)
            val v = n.optDouble("value", Double.NaN)
            if (id >= 0 && !v.isNaN()) byId[id] = v
        }
        val kcal = byId[1008] ?: byId[2047] ?: byId[2048] ?: continue
        val brand = f.optString("brandName", "").ifEmpty { f.optString("brandOwner", "") }.trim().ifEmpty { null }
        val serving = if (f.optString("servingSizeUnit", "").lowercase() == "g") f.num("servingSize") else null
        out += FoodHit(
            name = name.lowercase().replaceFirstChar { it.uppercase() }, brand = brand,
            kcalPer100 = kcal, proteinPer100 = byId[1003] ?: 0.0, carbsPer100 = byId[1005] ?: 0.0, fatPer100 = byId[1004] ?: 0.0,
            servingGrams = serving, barcode = f.optString("gtinUpc", "").ifEmpty { null }, hasNutrition = true
        )
    }
    return out
}
