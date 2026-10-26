package app.lumen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import app.lumen.core.*
import app.lumen.data.*
import app.lumen.health.HcState
import app.lumen.health.HealthSync
import app.lumen.net.Net
import app.lumen.util.Images
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface UiEvent {
    data object OpenReview : UiEvent
    data object GoHome : UiEvent
    data class Message(val text: String) : UiEvent
}

/** A meal being created or edited. Saved to the database only when the user confirms. */
data class Draft(
    val entryId: Long? = null,
    val name: String = "",
    val meal: String = "Lunch",
    val ts: Long = System.currentTimeMillis(),
    val ingredients: List<IngredientDraft> = emptyList(),
    val source: String = "MANUAL",
    val photoPath: String? = null,
    val barcode: String? = null,
    val confidence: Double? = null,
    val notes: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val db = Room.databaseBuilder(ctx, LumenDb::class.java, "lumen.db").build()
    private val dao = db.dao()
    private val store = SettingsStore(ctx)
    private val net = Net()

    val settings: StateFlow<Settings> = store.flow

    // ---------- day selection ----------
    private val _date = MutableStateFlow(dayOf(System.currentTimeMillis(), zone, store.flow.value.dayStartHour))
    val date: StateFlow<LocalDate> = _date
    fun shiftDay(n: Long) { _date.value = _date.value.plusDays(n) }
    fun goToday() { _date.value = todayLogical() }
    fun todayLogical(): LocalDate = dayOf(System.currentTimeMillis(), zone, settings.value.dayStartHour)

    private val startHour = settings.map { it.dayStartHour }.distinctUntilChanged()

    /** Steps per day from Health Connect (empty until connected). */
    private val _steps = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
    val steps: StateFlow<Map<LocalDate, Long>> = _steps

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<EntryWithIngredients>> =
        combine(_date, startHour) { d, h -> dayRangeMs(d, zone, h) }
            .flatMapLatest { (s, e) -> dao.entries(s, e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val exercises: StateFlow<List<ExerciseRow>> =
        combine(_date, startHour) { d, h -> dayRangeMs(d, zone, h) }
            .flatMapLatest { (s, e) -> dao.exercises(s, e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Eaten / burned per day for the chart, ending on the selected day. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val series: StateFlow<List<DayTotals>> =
        combine(_date, settings.map { it.graphDays to it.dayStartHour }.distinctUntilChanged()) { d, g -> Triple(d, g.first, g.second) }
            .flatMapLatest { (d, n, h) ->
                val first = d.minusDays((n - 1).toLong())
                val from = dayRangeMs(first, zone, h).first
                val to = dayRangeMs(d, zone, h).second
                combine(
                    dao.entries(from, to), dao.exercises(from, to), _steps,
                    settings.map { it.stepsCredit to it.weightKg }.distinctUntilChanged()
                ) { es, xs, st, credit ->
                    val eaten = es.groupBy { dayOf(it.entry.ts, zone, h) }.mapValues { (_, l) -> l.sumOf { e -> e.sum().kcal } }
                    val burned = xs.groupBy { dayOf(it.ts, zone, h) }.mapValues { (_, l) -> l.sumOf { x -> x.kcal } }
                    (0 until n).map { i ->
                        val day = first.plusDays(i.toLong())
                        val stepBurn = if (credit.first) stepsKcal(st[day] ?: 0L, credit.second) else 0.0
                        DayTotals(day, eaten[day] ?: 0.0, (burned[day] ?: 0.0) + stepBurn)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------- UI state ----------
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy
    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private val _hc = MutableStateFlow(HcState.UNAVAILABLE)
    val hc: StateFlow<HcState> = _hc

    private fun msg(t: String) { _events.trySend(UiEvent.Message(t)) }
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { msg(e.message ?: e.javaClass.simpleName) }
            finally { _busy.value = null }
        }
    }

    init {
        viewModelScope.launch {
            runCatching {
                _hc.value = HealthSync.state(ctx)
                if (settings.value.hcEnabled && _hc.value == HcState.READY) HealthSync.sync(ctx, settings.value, dao)
            }
            refreshSteps()
        }
    }

    // ---------- settings ----------
    fun update(transform: (Settings) -> Settings) = store.update(transform)
    fun resetSettings() = store.update { Settings() }
    fun exportJson(): String = settings.value.toJson(includeSecrets = false).toString(2)

    fun importJson(text: String): Boolean = try {
        val j = JSONObject(text.trim())
        store.update { Settings.fromJson(j, it) }
        msg("Settings imported"); true
    } catch (e: Exception) { msg("That isn't valid settings JSON"); false }

    fun importUrl(url: String) = launchSafe {
        _busy.value = "Fetching config…"
        val text = io { net.fetchText(url.trim()) }
        _busy.value = null
        importJson(text)
    }

    fun testAi() = launchSafe {
        _busy.value = "Testing AI connection…"
        val jpg = io { Images.blankJpeg() }
        val r = io { runCatching { net.analyze(jpg, settings.value) } }
        _busy.value = null
        msg(r.fold(
            { "AI connection works ✓" },
            { e ->
                if (e is AiParseException && !(e.message ?: "").startsWith("HTTP")) "Reached the model, but its reply wasn't usable: ${e.message}"
                else "AI test failed: ${e.message}"
            }
        ))
    }

    // ---------- logging flow ----------
    private fun newTs(): Long {
        val now = System.currentTimeMillis()
        val s = settings.value
        return if (_date.value == dayOf(now, zone, s.dayStartHour)) now
        else dayRangeMs(_date.value, zone, s.dayStartHour).first + 12 * 3600_000L
    }

    private fun mealAt(ts: Long) = mealForHour(Instant.ofEpochMilli(ts).atZone(zone).hour)

    private suspend fun finishDraft(d: Draft) {
        if (!settings.value.confirmBeforeSaving) {
            save(d)
            msg("Logged ${d.ingredients.totals().kcal.toInt()} kcal")
            _events.send(UiEvent.GoHome)
        } else {
            _draft.value = d
            _events.send(UiEvent.OpenReview)
        }
    }

    fun analyzePhoto(uri: Uri) = launchSafe {
        val s = settings.value
        val needsKey = s.aiApiKey.isBlank() && (s.aiProvider != AiProvider.OPENAI || s.effectiveBase.contains("api.openai.com"))
        if (needsKey) { msg("Add your AI API key in Settings first."); return@launchSafe }
        _busy.value = "Analyzing your meal…"
        val bytes = io { Images.prepare(ctx, uri, s.aiMaxImagePx, s.aiJpegQuality) }
        val photo = io { Images.savePhoto(ctx, bytes) }
        val meal = io { net.analyze(bytes, s) }
        _busy.value = null
        if (!meal.isFood) { msg("No food detected in that photo."); return@launchSafe }
        val ts = newTs()
        finishDraft(Draft(ts = ts, name = meal.dish, meal = mealAt(ts), ingredients = meal.ingredients,
            source = "AI", photoPath = photo, confidence = meal.confidence, notes = meal.notes))
    }

    private var lastCode = ""
    private var lastCodeAt = 0L

    fun lookupBarcode(raw: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (_busy.value != null || (!force && raw == lastCode && now - lastCodeAt < 6000)) return
        lastCode = raw; lastCodeAt = now
        launchSafe {
            val code = normalizeBarcode(raw)
            if (code == null) { msg("That doesn't look like a product barcode (8 to 14 digits)."); return@launchSafe }
            _busy.value = "Looking up barcode…"
            val s = settings.value
            // 1) products you already logged or corrected, 2) Open Food Facts, 3) USDA branded foods
            val hit = dao.lastIngredientByBarcode(code)?.toFoodHit() ?: io { net.barcode(code, s) }
            _busy.value = null
            val ts = newTs()
            if (hit == null) {
                msg("Barcode $code wasn't found online. Enter it once and it will be remembered.")
                _draft.value = Draft(
                    ts = ts, name = "Unknown product", meal = mealAt(ts), source = "BARCODE", barcode = code,
                    ingredients = listOf(IngredientDraft("Unknown product", s.defaultServingGrams.toDouble(), 0.0, 0.0, 0.0, 0.0, code)),
                    notes = "Barcode $code wasn't found. Tap the ingredient to enter its name and calories; the app will remember it next time."
                )
                _events.send(UiEvent.OpenReview)
                return@launchSafe
            }
            val grams = (hit.servingGrams ?: s.defaultServingGrams.toDouble()).coerceAtLeast(1.0)
            val d = Draft(ts = ts, name = listOfNotNull(hit.brand, hit.name).distinct().joinToString(" · "), meal = mealAt(ts),
                ingredients = listOf(IngredientDraft.fromFood(hit, grams)), source = "BARCODE", barcode = hit.barcode ?: code,
                notes = if (hit.hasNutrition) null else "No calorie data for this product online. Tap the ingredient to enter it (0 is right for plain water).")
            if (s.barcodeAutoLog && hit.hasNutrition) {
                save(d); msg("Logged ${d.name}"); _events.send(UiEvent.GoHome)
            } else {
                _draft.value = d; _events.send(UiEvent.OpenReview)
            }
        }
    }

    fun startManual() {
        val ts = newTs()
        _draft.value = Draft(ts = ts, meal = mealAt(ts), source = "MANUAL")
        _events.trySend(UiEvent.OpenReview)
    }

    fun openEntry(id: Long) = launchSafe {
        val e = dao.entry(id) ?: return@launchSafe
        _draft.value = Draft(
            entryId = e.entry.id, name = e.entry.name, meal = e.entry.meal, ts = e.entry.ts,
            ingredients = e.ingredients.map { it.toDraft() }, source = e.entry.source, photoPath = e.entry.photoPath,
            barcode = e.entry.barcode, confidence = e.entry.confidence, notes = e.entry.notes
        )
        _events.send(UiEvent.OpenReview)
    }

    fun updateDraft(f: (Draft) -> Draft) { _draft.value = _draft.value?.let(f) }
    fun discardDraft() { _draft.value = null }

    private suspend fun save(d: Draft) {
        db.withTransaction {
            val row = EntryRow(
                id = d.entryId ?: 0, ts = d.ts, name = d.name.ifBlank { d.ingredients.firstOrNull()?.name ?: "Meal" },
                meal = d.meal, source = d.source, photoPath = d.photoPath, barcode = d.barcode,
                confidence = d.confidence, notes = d.notes
            )
            val id = if (d.entryId == null) dao.insertEntry(row) else { dao.updateEntry(row); dao.clearIngredients(d.entryId); d.entryId }
            dao.insertIngredients(d.ingredients.map { it.toRow(id) })
        }
    }

    fun saveDraft() = launchSafe {
        val d = _draft.value ?: return@launchSafe
        if (d.ingredients.isEmpty()) { msg("Add at least one ingredient."); return@launchSafe }
        save(d)
        _draft.value = null
        _events.send(UiEvent.GoHome)
    }

    fun deleteDraftEntry() = launchSafe {
        val d = _draft.value ?: return@launchSafe
        d.entryId?.let { dao.deleteEntry(it) }
        d.photoPath?.let { runCatching { File(it).delete() } }
        _draft.value = null
        _events.send(UiEvent.GoHome)
    }

    suspend fun searchFoods(query: String): List<FoodHit> = io { net.search(query, settings.value) }

    // ---------- exercise ----------
    fun addExercise(name: String, minutes: Int, kcal: Double) = launchSafe {
        dao.insertExercise(ExerciseRow(ts = newTs(), name = name.ifBlank { "Workout" }, minutes = minutes, kcal = kcal, source = "MANUAL"))
    }

    fun deleteExercise(id: Long) = launchSafe { dao.deleteExercise(id) }

    fun refreshHc() = launchSafe { _hc.value = HealthSync.state(ctx) }

    /** Reload steps for the graph range ending on the selected day. Silent: steps are optional. */
    fun refreshSteps() {
        viewModelScope.launch {
            runCatching {
                val state = HealthSync.state(ctx)
                _hc.value = state
                if (state != HcState.READY) { _steps.value = emptyMap(); return@runCatching }
                val s = settings.value
                val last = _date.value
                val first = last.minusDays((maxOf(s.graphDays, 7) - 1).toLong())
                _steps.value = HealthSync.stepsByDay(ctx, first, last, s.dayStartHour)
            }
        }
    }

    fun syncHealth(silent: Boolean = false) = launchSafe {
        _hc.value = HealthSync.state(ctx)
        if (_hc.value != HcState.READY) { if (!silent) msg("Health Connect isn't ready. Grant access first."); return@launchSafe }
        val r = HealthSync.sync(ctx, settings.value, dao)
        refreshSteps()
        if (!silent) msg(
            if (r.sessions == 0) "No workouts found in the last ${settings.value.hcLookbackDays} days"
            else "Synced ${r.sessions} workout(s)\n" + r.lines.take(4).joinToString("\n")
        )
    }
}
