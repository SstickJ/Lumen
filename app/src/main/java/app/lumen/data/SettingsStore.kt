package app.lumen.data

import android.content.Context
import app.lumen.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** All settings live in one JSON string, so they apply instantly and can be imported/exported. */
class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Settings> = _flow

    private fun load(): Settings =
        prefs.getString("json", null)
            ?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: Settings()

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_flow.value).sanitized()
        prefs.edit().putString("json", next.toJson().toString()).apply()
        _flow.value = next
    }
}
