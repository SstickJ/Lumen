package app.lumen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.AppViewModel
import app.lumen.core.*
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

private val meals = listOf("Breakfast", "Lunch", "Snack", "Dinner")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalLumen.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val d = draft
    if (d == null) { LaunchedEffect(Unit) { onBack() }; return }

    var showAdd by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler { vm.discardDraft(); onBack() }

    val totals = d.ingredients.totals()

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.ChevronLeft, { vm.discardDraft(); onBack() }, 42.dp, desc = "Back")
            Spacer(Modifier.width(12.dp))
            T(if (d.entryId == null) "Review meal" else "Edit meal", 20.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (d.entryId != null) IconCircle(Icons.Rounded.Delete, { confirmDelete = true }, 42.dp, tint = c.over, desc = "Delete")
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val photo = d.photoPath?.let { File(it) }?.takeIf { it.exists() }
            if (photo != null) AsyncImage(photo, null, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(26.dp)), contentScale = ContentScale.Crop)

            GlassCard {
                GlassField(d.name, { v -> vm.updateDraft { it.copy(name = v) } }, "Meal name")
                Spacer(Modifier.height(10.dp))
                ChipRow(meals, meals.indexOf(d.meal).coerceAtLeast(0)) { i -> vm.updateDraft { it.copy(meal = meals[i]) } }
            }

            GlassCard(tint = c.accent) {
                Row(verticalAlignment = Alignment.Bottom) {
                    T("${totals.kcal.roundToInt()}", 40.sp, FontWeight.SemiBold)
                    T(" kcal", 14.sp, color = c.dim, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    if (d.confidence != null) T("AI confidence ${(d.confidence * 100).roundToInt()}%", 12.sp, color = c.dim, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (s.showMacros) T("P ${totals.protein.roundToInt()} g · C ${totals.carbs.roundToInt()} g · F ${totals.fat.roundToInt()} g", 13.sp, color = c.dim)
                if (!d.notes.isNullOrBlank()) { Spacer(Modifier.height(6.dp)); T(d.notes, 12.sp, color = c.dim) }
            }

            T("Ingredients", 13.sp, FontWeight.SemiBold, c.dim, Modifier.padding(start = 4.dp))
            if (d.ingredients.isEmpty()) GlassCard { T("No ingredients yet. Add one below.", 14.sp, color = c.dim) }
            d.ingredients.forEachIndexed { i, ing ->
                GlassCard(padding = 12.dp, onClick = { editIndex = i }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(ing.name, 15.sp, FontWeight.Medium, maxLines = 2)
                            T("${fmtNum(ing.grams)} g · ${ing.kcal.roundToInt()} kcal", 12.sp, color = c.dim)
                        }
                        IconCircle(Icons.Rounded.Close, { vm.updateDraft { dr -> dr.copy(ingredients = dr.ingredients.filterIndexed { k, _ -> k != i }) } }, 34.dp, desc = "Remove")
                    }
                }
            }
            GlassButton("Add ingredient", { showAdd = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp)) {
            GlassButton("Save to log", { vm.saveDraft() }, Modifier.fillMaxWidth(), primary = true, enabled = d.ingredients.isNotEmpty())
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.bgBottom
        ) {
            AddIngredientSheet(vm, s) { ing -> vm.updateDraft { it.copy(ingredients = it.ingredients + ing) }; showAdd = false }
        }
    }

    editIndex?.let { idx ->
        d.ingredients.getOrNull(idx)?.let { ing ->
            IngredientDialog(ing, onDismiss = { editIndex = null }) { upd ->
                vm.updateDraft { dr -> dr.copy(ingredients = dr.ingredients.mapIndexed { k, old -> if (k == idx) upd else old }) }
                editIndex = null
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.bgBottom,
        title = { T("Delete this meal?", 18.sp, FontWeight.SemiBold) },
        text = { T("It will be removed from your log.", 14.sp, color = c.dim) },
        confirmButton = { TextButton({ confirmDelete = false; vm.deleteDraftEntry() }) { Text("Delete", color = c.over) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun IngredientDialog(ing: IngredientDraft, onDismiss: () -> Unit, onSave: (IngredientDraft) -> Unit) {
    val c = LocalLumen.current
    var name by remember { mutableStateOf(ing.name) }
    var grams by remember { mutableStateOf(ing.grams) }
    var kcal by remember { mutableStateOf(ing.kcalPer100) }
    var p by remember { mutableStateOf(ing.proteinPer100) }
    var cb by remember { mutableStateOf(ing.carbsPer100) }
    var f by remember { mutableStateOf(ing.fatPer100) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.bgBottom,
        title = { T("Edit ingredient", 18.sp, FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassField(name, { name = it }, "Name")
                NumField("Amount (g)", grams) { grams = it }
                T("Nutrition per 100 g", 12.sp, color = c.dim)
                NumField("Calories (kcal)", kcal) { kcal = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Protein", p, Modifier.weight(1f)) { p = it }
                    NumField("Carbs", cb, Modifier.weight(1f)) { cb = it }
                    NumField("Fat", f, Modifier.weight(1f)) { f = it }
                }
            }
        },
        confirmButton = {
            TextButton({
                onSave(ing.copy(name = name.ifBlank { ing.name }, grams = grams.coerceAtLeast(1.0), kcalPer100 = kcal.coerceAtLeast(0.0),
                    proteinPer100 = p.coerceAtLeast(0.0), carbsPer100 = cb.coerceAtLeast(0.0), fatPer100 = f.coerceAtLeast(0.0)))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddIngredientSheet(vm: AppViewModel, s: Settings, onAdd: (IngredientDraft) -> Unit) {
    val c = LocalLumen.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var customName by remember { mutableStateOf("") }
    var customGrams by remember { mutableStateOf(100.0) }
    var customKcal by remember { mutableStateOf(0.0) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        delay(450)
        loading = true
        try { results = vm.searchFoods(query.trim()); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Search failed" }
        finally { loading = false }
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        T("Add ingredient", 18.sp, FontWeight.SemiBold)
        T("Searching ${s.foodProvider.label}", 12.sp, color = c.dim)
        Spacer(Modifier.height(10.dp))
        GlassField(query, { query = it }, "Search food (e.g. olive oil, rice, whey)")
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = c.accent)
        error?.let { T(it, 12.sp, color = c.over, modifier = Modifier.padding(top = 6.dp)) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(top = 8.dp)) {
            items(results) { h ->
                Column(Modifier.fillMaxWidth().clickable {
                    onAdd(IngredientDraft.fromFood(h, (h.servingGrams ?: s.defaultServingGrams.toDouble()).coerceAtLeast(1.0)))
                }.padding(vertical = 10.dp)) {
                    T(h.name, 15.sp, FontWeight.Medium, maxLines = 2)
                    T(listOfNotNull(h.brand, "${h.kcalPer100.roundToInt()} kcal / 100 g").joinToString(" · "), 12.sp, color = c.dim)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        T("Or add your own", 13.sp, FontWeight.SemiBold, c.dim)
        Spacer(Modifier.height(6.dp))
        GlassField(customName, { customName = it }, "Name")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Amount (g)", customGrams, Modifier.weight(1f)) { customGrams = it }
            NumField("Total kcal", customKcal, Modifier.weight(1f)) { customKcal = it }
        }
        Spacer(Modifier.height(10.dp))
        GlassButton("Add custom", {
            if (customName.isNotBlank() && customGrams > 0) onAdd(IngredientDraft.fromTotals(customName.trim(), customGrams, customKcal, 0.0, 0.0, 0.0))
        }, Modifier.fillMaxWidth(), enabled = customName.isNotBlank() && customGrams > 0)
    }
}
