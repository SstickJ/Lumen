package app.lumen.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mifflin-St Jeor resting energy expenditure (kcal/day). */
fun bmr(sex: Sex, age: Int, heightCm: Double, weightKg: Double): Double {
    val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
    return if (sex == Sex.MALE) base + 5.0 else base - 161.0
}

/** Estimated kcal/day to stay at the same weight (excluding logged workouts). */
fun Settings.maintenanceKcal(): Double =
    if (autoGoal) bmr(sex, age, heightCm, weightKg) * activityFactor else manualGoalKcal - goalDeltaKcal.toDouble()

/** Daily eating target. */
fun Settings.targetKcal(): Double =
    if (autoGoal) maintenanceKcal() + goalDeltaKcal else manualGoalKcal.toDouble()

fun Settings.targetWarning(): String? =
    if (targetKcal() < 1200.0) "This daily target is very low. Double-check your goal settings." else null

/** Logical "day" a timestamp belongs to; with dayStartHour = 4, 02:00 still counts as the previous day. */
fun dayOf(epochMs: Long, zone: ZoneId, dayStartHour: Int): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).minusHours(dayStartHour.toLong()).toLocalDate()

/** [start, end) in epoch millis for a logical day. DST-safe (a day can be 23 or 25 h). */
fun dayRangeMs(date: LocalDate, zone: ZoneId, dayStartHour: Int): Pair<Long, Long> {
    val s = date.atStartOfDay(zone).plusHours(dayStartHour.toLong()).toInstant().toEpochMilli()
    val e = date.plusDays(1).atStartOfDay(zone).plusHours(dayStartHour.toLong()).toInstant().toEpochMilli()
    return s to e
}

enum class StatusKind { EMPTY, ON_TRACK, ALMOST, ON_TARGET, OVER, UNDER }
data class Status(val kind: StatusKind, val title: String, val detail: String, val remaining: Double)

private fun n(d: Double) = d.roundToInt().toString()

/** The one-line "am I doing OK?" message shown at the top of Home. */
fun dayStatus(
    eaten: Double, exercise: Double, target: Double,
    creditPct: Int, tolerancePct: Int, hour: Int, isToday: Boolean = true
): Status {
    val allowance = target + exercise * creditPct / 100.0
    val remaining = allowance - eaten
    val tol = allowance * tolerancePct / 100.0
    return when {
        eaten <= 0.0 -> Status(StatusKind.EMPTY, "Nothing logged yet", "Your budget for the day is ${n(allowance)} kcal.", remaining)
        remaining < -tol -> Status(StatusKind.OVER, "Over budget", "${n(-remaining)} kcal above your target.", remaining)
        remaining <= tol -> Status(StatusKind.ON_TARGET, "Right on target", "You've hit your goal for the day.", remaining)
        remaining <= allowance * 0.10 -> Status(StatusKind.ALMOST, "Almost there", "${n(remaining)} kcal left.", remaining)
        isToday && hour >= 21 && remaining > allowance * 0.30 ->
            Status(StatusKind.UNDER, "Eating too little", "${n(remaining)} kcal below target, consider a snack.", remaining)
        !isToday && remaining > allowance * 0.30 ->
            Status(StatusKind.UNDER, "Under target", "${n(remaining)} kcal below target.", remaining)
        else -> Status(StatusKind.ON_TRACK, "On track", "${n(remaining)} kcal left to eat.", remaining)
    }
}

enum class DayVerdict { EMPTY, GOOD, OVER, UNDER }

/** Net energy of a day: eaten minus (maintenance + exercise). Negative = deficit. */
fun netKcal(eaten: Double, exercise: Double, maintenance: Double) = eaten - (maintenance + exercise)

fun dayVerdict(eaten: Double, exercise: Double, maintenance: Double, goalDelta: Double, target: Double): DayVerdict {
    if (eaten <= 0.0) return DayVerdict.EMPTY
    val net = netKcal(eaten, exercise, maintenance)
    val tol = abs(target) * 0.05
    return when {
        net > goalDelta + tol -> DayVerdict.OVER
        net < goalDelta - 0.30 * target -> DayVerdict.UNDER
        else -> DayVerdict.GOOD
    }
}

/** Rough workout burn: MET x body weight (kg) x hours. */
fun estimateBurn(met: Double, weightKg: Double, minutes: Int): Double = met * weightKg * minutes / 60.0

val EXERCISE_METS: List<Pair<String, Double>> = listOf(
    "Walking" to 3.5, "Running" to 9.8, "Cycling" to 7.5, "Gym" to 5.0,
    "Swimming" to 8.0, "HIIT" to 8.0, "Yoga" to 3.0, "Hiking" to 6.0
)
