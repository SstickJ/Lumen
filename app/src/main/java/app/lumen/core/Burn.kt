package app.lumen.core

import kotlin.math.max
import kotlin.math.min

/** A calorie record from Health Connect (active or total), reduced to what we need. */
data class EnergyRec(val startMs: Long, val endMs: Long, val kcal: Double)
data class BurnPick(val kcal: Double, val source: String)

/** Share of [r] that falls inside the window, e.g. a day-long 100 kcal record gives a 1 h window ~4 kcal. */
fun overlapKcal(startMs: Long, endMs: Long, r: EnergyRec): Double {
    val dur = r.endMs - r.startMs
    if (dur <= 0) return if (r.startMs in startMs..endMs) r.kcal else 0.0
    val ov = min(endMs, r.endMs) - max(startMs, r.startMs)
    return if (ov <= 0) 0.0 else r.kcal * ov / dur
}

/** (records that sit inside the window, full value) to (all overlapping records, prorated by time). */
fun windowKcal(startMs: Long, endMs: Long, recs: List<EnergyRec>, toleranceMs: Long = 120_000): Pair<Double, Double> {
    val contained = recs.filter { it.startMs >= startMs - toleranceMs && it.endMs <= endMs + toleranceMs }.sumOf { it.kcal }
    val prorated = recs.sumOf { overlapKcal(startMs, endMs, it) }
    return contained to prorated
}

/**
 * Best calorie figure for one workout. Active calories are preferred; Total calories (which include resting
 * burn) are used if the watch app only writes those; a MET estimate is the last resort. Values under
 * [minKcal] count as "missing" so a tiny prorated slice never shows up as 0 kcal.
 */
fun pickBurn(
    startMs: Long, endMs: Long, active: List<EnergyRec>, total: List<EnergyRec>,
    estimate: Double, minKcal: Double = 1.0
): BurnPick {
    val (ac, ap) = windowKcal(startMs, endMs, active)
    val (tc, tp) = windowKcal(startMs, endMs, total)
    return when {
        ac >= minKcal -> BurnPick(ac, "active")
        ap >= minKcal -> BurnPick(ap, "active, prorated")
        tc >= minKcal -> BurnPick(tc, "total")
        tp >= minKcal -> BurnPick(tp, "total, prorated")
        else -> BurnPick(estimate, "estimate")
    }
}

/** ~0.04 kcal per step at 70 kg, scaled by body weight. */
fun stepsKcal(steps: Long, weightKg: Double): Double = steps * 0.04 * weightKg / 70.0
