package app.lumen.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.lumen.core.EnergyRec
import app.lumen.core.Settings
import app.lumen.core.estimateBurn
import app.lumen.core.pickBurn
import app.lumen.data.ExerciseRow
import app.lumen.data.LumenDao
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

enum class HcState { UNAVAILABLE, NEEDS_INSTALL, NEEDS_PERMISSION, READY }

/** Imports workouts (e.g. from Mi Fitness) through Android Health Connect. */
object HealthSync {
    /** Needed for the connection to count as ready. Total calories are optional (a fallback for some watch apps). */
    val required: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )
    val permissions: Set<String> = required + HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

    suspend fun state(ctx: Context): HcState = when (HealthConnectClient.getSdkStatus(ctx)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
            if (granted.containsAll(required)) HcState.READY else HcState.NEEDS_PERMISSION
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.NEEDS_INSTALL
        else -> HcState.UNAVAILABLE
    }

    data class SyncReport(val sessions: Int, val lines: List<String>)

    /** Re-syncing is idempotent (keyed by session id), so a corrected calorie value replaces an old one. */
    suspend fun sync(ctx: Context, s: Settings, dao: LumenDao): SyncReport {
        val client = HealthConnectClient.getOrCreate(ctx)
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(s.hcLookbackDays.toLong()))
        val sessions = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))
        ).records
        val lines = mutableListOf<String>()
        for (sess in sessions) {
            val sessEnd = if (sess.endTime.isAfter(sess.startTime)) sess.endTime else sess.startTime.plusSeconds(60)
            val minutes = Duration.between(sess.startTime, sessEnd).toMinutes().toInt().coerceAtLeast(1)
            val window = TimeRangeFilter.between(sess.startTime, sessEnd)
            val origin = sess.metadata.dataOrigin.packageName

            // Mi Fitness may write calories as Active or as Total records, possibly with a different time span.
            val active = client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, window)).records
                .map { it.metadata.dataOrigin.packageName to EnergyRec(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories) }
            val total = runCatching {
                client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, window)).records
                    .map { it.metadata.dataOrigin.packageName to EnergyRec(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories) }
            }.getOrDefault(emptyList())
            fun fromOrigin(l: List<Pair<String, EnergyRec>>) = l.filter { it.first == origin }.ifEmpty { l }.map { it.second }

            val pick = pickBurn(
                sess.startTime.toEpochMilli(), sessEnd.toEpochMilli(), fromOrigin(active), fromOrigin(total),
                estimateBurn(5.0, s.weightKg, minutes)
            )
            val name = sess.title?.takeIf { it.isNotBlank() } ?: typeName(sess.exerciseType)
            dao.insertExercise(
                ExerciseRow(
                    ts = sess.startTime.toEpochMilli(), name = name, minutes = minutes, kcal = pick.kcal,
                    source = "HEALTH_CONNECT", externalId = "hc:" + sess.metadata.id
                )
            )
            lines += "$name, $minutes min: ${pick.kcal.roundToInt()} kcal (${pick.source})"
        }
        return SyncReport(sessions.size, lines)
    }

    /** Steps per logical day (Health Connect de-duplicates overlapping sources). Days are keyed like the rest of the app. */
    suspend fun stepsByDay(ctx: Context, first: LocalDate, last: LocalDate, dayStartHour: Int): Map<LocalDate, Long> {
        val client = HealthConnectClient.getOrCreate(ctx)
        val start = first.atStartOfDay().plusHours(dayStartHour.toLong())
        val end = last.plusDays(1).atStartOfDay().plusHours(dayStartHour.toLong())
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end), Period.ofDays(1))
        )
        return groups.associate { g -> g.startTime.minusHours(dayStartHour.toLong()).toLocalDate() to (g.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
    }

    private fun typeName(t: Int): String = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        else -> "Workout"
    }
}
