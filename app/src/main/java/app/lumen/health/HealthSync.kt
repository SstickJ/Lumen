package app.lumen.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.lumen.core.Settings
import app.lumen.core.estimateBurn
import app.lumen.data.ExerciseRow
import app.lumen.data.LumenDao
import java.time.Duration
import java.time.Instant

enum class HcState { UNAVAILABLE, NEEDS_INSTALL, NEEDS_PERMISSION, READY }

/** Imports workouts (e.g. from Mi Fitness) through Android Health Connect. */
object HealthSync {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )

    suspend fun state(ctx: Context): HcState = when (HealthConnectClient.getSdkStatus(ctx)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) HcState.READY else HcState.NEEDS_PERMISSION
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.NEEDS_INSTALL
        else -> HcState.UNAVAILABLE
    }

    /** Returns the number of workout sessions processed. Re-syncing is idempotent (keyed by session id). */
    suspend fun sync(ctx: Context, s: Settings, dao: LumenDao): Int {
        val client = HealthConnectClient.getOrCreate(ctx)
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(s.hcLookbackDays.toLong()))
        val sessions = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))
        ).records
        for (sess in sessions) {
            val minutes = Duration.between(sess.startTime, sess.endTime).toMinutes().toInt().coerceAtLeast(1)
            val active = client.aggregate(
                AggregateRequest(
                    setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    TimeRangeFilter.between(sess.startTime, sess.endTime)
                )
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            // No calorie data from the watch: fall back to a MET estimate so the workout still counts.
            val kcal = if (active > 0.0) active else estimateBurn(5.0, s.weightKg, minutes)
            dao.insertExercise(
                ExerciseRow(
                    ts = sess.startTime.toEpochMilli(),
                    name = sess.title?.takeIf { it.isNotBlank() } ?: typeName(sess.exerciseType),
                    minutes = minutes, kcal = kcal, source = "HEALTH_CONNECT",
                    externalId = "hc:" + sess.metadata.id
                )
            )
        }
        return sessions.size
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
