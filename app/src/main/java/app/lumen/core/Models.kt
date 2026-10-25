package app.lumen.core

import java.time.LocalDate

/** One ingredient / component of a logged food. Nutrition is stored per 100 g so grams can be edited freely. */
data class IngredientDraft(
    val name: String,
    val grams: Double,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val barcode: String? = null
) {
    val kcal: Double get() = kcalPer100 * grams / 100.0
    val protein: Double get() = proteinPer100 * grams / 100.0
    val carbs: Double get() = carbsPer100 * grams / 100.0
    val fat: Double get() = fatPer100 * grams / 100.0

    companion object {
        /** Build from totals for [grams] (what an AI model returns). */
        fun fromTotals(
            name: String, grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double,
            barcode: String? = null
        ): IngredientDraft {
            require(grams > 0.0) { "grams must be > 0" }
            val f = 100.0 / grams
            return IngredientDraft(name, grams, kcal * f, protein * f, carbs * f, fat * f, barcode)
        }

        fun fromFood(hit: FoodHit, grams: Double) = IngredientDraft(
            hit.name, grams, hit.kcalPer100, hit.proteinPer100, hit.carbsPer100, hit.fatPer100, hit.barcode
        )
    }
}

data class Totals(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

fun List<IngredientDraft>.totals() = Totals(sumOf { it.kcal }, sumOf { it.protein }, sumOf { it.carbs }, sumOf { it.fat })

/** A product / food returned by a food database (nutrition per 100 g). */
data class FoodHit(
    val name: String,
    val brand: String?,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val servingGrams: Double?,
    val barcode: String?,
    val hasNutrition: Boolean
)

data class AiMeal(
    val dish: String,
    val isFood: Boolean,
    val confidence: Double?,
    val notes: String?,
    val ingredients: List<IngredientDraft>
)

data class DayTotals(val date: LocalDate, val eaten: Double, val exercise: Double)

fun mealForHour(hour: Int): String = when (hour) {
    in 4..10 -> "Breakfast"
    in 11..14 -> "Lunch"
    in 15..17 -> "Snack"
    else -> "Dinner"
}

/** "#RRGGBB" or "#AARRGGBB" -> ARGB long, or null if invalid. */
fun parseHexColor(hex: String): Long? {
    val h = hex.trim().removePrefix("#")
    return try {
        when (h.length) {
            6 -> 0xFF000000L or java.lang.Long.parseLong(h, 16)
            8 -> java.lang.Long.parseLong(h, 16)
            else -> null
        }
    } catch (e: NumberFormatException) { null }
}
