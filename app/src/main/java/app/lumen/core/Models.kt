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

// ---- colour helpers (kept here so they can be unit tested) ----
fun relativeLuminance(argb: Long): Double {
    fun ch(shift: Int): Double {
        val v = ((argb shr shift) and 0xFF).toInt() / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
}

fun mixArgb(a: Long, b: Long, t: Double): Long {
    fun ch(shift: Int): Long {
        val x = (a shr shift) and 0xFF
        val y = (b shr shift) and 0xFF
        return Math.round(x + (y - x) * t).coerceIn(0L, 255L)
    }
    return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** An accent that is invisible on the chosen theme (white on light, near-black on dark) is nudged so it stays readable. */
fun readableAccent(argb: Long, dark: Boolean): Long {
    val l = relativeLuminance(argb)
    return when {
        dark && l < 0.06 -> mixArgb(argb, 0xFFFFFFFFL, 0.65)
        !dark && l > 0.80 -> 0xFF3A4254L
        else -> argb
    }
}

/** Text and icons drawn ON the accent colour: dark for light accents (white!), white for dark ones. */
fun accentNeedsDarkText(argb: Long): Boolean = relativeLuminance(argb) > 0.179

fun isNeutralColor(argb: Long): Boolean {
    val r = ((argb shr 16) and 0xFF).toInt(); val g = ((argb shr 8) and 0xFF).toInt(); val b = (argb and 0xFF).toInt()
    return maxOf(r, g, b) - minOf(r, g, b) < 24
}
