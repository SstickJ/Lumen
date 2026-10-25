package app.lumen.net

import android.util.Base64
import app.lumen.core.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Blocking network calls. Call from Dispatchers.IO. */
class Net {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    private val ua = "Lumen/1.0 (Android; personal calorie tracker)"

    fun analyze(jpeg: ByteArray, s: Settings): AiMeal {
        val spec = buildAiRequest(s, Base64.encodeToString(jpeg, Base64.NO_WRAP))
        val rb = Request.Builder().url(spec.url)
            .post(spec.body.toRequestBody("application/json".toMediaType()))
        spec.headers.forEach { (k, v) -> rb.header(k, v) }
        val client = http.newBuilder()
            .callTimeout(s.aiTimeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(s.aiTimeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
        val text = client.newCall(rb.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val detail = try { extractAiText(s.aiProvider, body); "" } catch (e: AiParseException) { e.message.orEmpty() }
                throw AiParseException("HTTP ${r.code}" + if (detail.isNotEmpty()) ": $detail" else "")
            }
            body
        }
        return parseAiMeal(extractAiText(s.aiProvider, text))
    }

    /** Barcode lookup: Open Food Facts first, USDA branded foods as a fallback. */
    fun barcode(raw: String, s: Settings): FoodHit? {
        val code = normalizeBarcode(raw) ?: return null
        parseOffProduct(get(offProductUrl(s.offBaseUrl, code)), code)?.let { return it }
        if (s.usdaApiKey.isNotBlank()) {
            val hits = runCatching { parseUsdaSearch(get(usdaSearchUrl(code, s.usdaApiKey))) }.getOrDefault(emptyList())
            hits.firstOrNull { it.barcode?.trimStart('0') == code.trimStart('0') }?.let { return it }
        }
        return null
    }

    fun search(query: String, s: Settings): List<FoodHit> = when (s.foodProvider) {
        FoodProvider.OPEN_FOOD_FACTS -> parseOffSearch(get(offSearchUrl(s.offBaseUrl, query)))
        FoodProvider.USDA -> parseUsdaSearch(get(usdaSearchUrl(query, s.usdaApiKey)))
    }

    fun fetchText(url: String): String = get(url)

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        return http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            // Open Food Facts answers 404 + JSON for unknown barcodes; let the parser handle that.
            if (!r.isSuccessful && r.code != 404) throw IOException("HTTP ${r.code} from ${r.request.url.host}")
            body
        }
    }
}
