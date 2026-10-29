package it.mattia.ciclismo.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import it.mattia.ciclismo.data.Repo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Weight, body fat, resting HR and sleep from Health Connect (Samsung Health, smart scales). */
object Health {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun available(ctx: Context): Boolean =
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(ctx: Context): Set<String> =
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()

    suspend fun sync(ctx: Context): Result<String> = runCatching {
        val client = HealthConnectClient.getOrCreate(ctx)
        val ok = client.permissionController.getGrantedPermissions()
        val end = Instant.now()
        val filter = TimeRangeFilter.between(end.minus(90, ChronoUnit.DAYS), end)
        val parts = ArrayList<String>()
        val zone = ZoneId.systemDefault()

        if (HealthPermission.getReadPermission(WeightRecord::class) in ok) {
            val w = client.readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records
            if (w.isNotEmpty()) {
                val byDay = w.groupBy { it.time.atZone(zone).toLocalDate() }
                    .mapValues { e -> e.value.maxByOrNull { it.time }!!.weight.inKilograms }
                Repo.update { d ->
                    val merged = (d.weights.filter { it.first !in byDay.keys.map { k -> k.toString() } } +
                        byDay.map { it.key.toString() to it.value }).sortedBy { it.first }
                    val latest = merged.last().second
                    d.copy(weights = merged, profile = d.profile.copy(weight = latest))
                }
                parts += "peso"
            }
        }
        if (HealthPermission.getReadPermission(BodyFatRecord::class) in ok) {
            val r = client.readRecords(ReadRecordsRequest(BodyFatRecord::class, filter)).records.maxByOrNull { it.time }
            if (r != null) {
                Repo.setProfile { it.copy(bodyFat = r.percentage.value) }
                parts += "% grasso"
            }
        }
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in ok) {
            val r = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, filter)).records.maxByOrNull { it.time }
            if (r != null) {
                Repo.setProfile { it.copy(restHr = r.beatsPerMinute.toInt()) }
                parts += "FC a riposo"
            }
        }
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in ok) {
            val r = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records.maxByOrNull { it.endTime }
            if (r != null) {
                val h = Duration.between(r.startTime, r.endTime).toMinutes() / 60.0
                Repo.update { it.copy(sleepHours = h) }
                parts += "sonno"
            }
        }
        if (parts.isEmpty()) "Nessun dato nuovo in Health Connect" else "Aggiornati: " + parts.joinToString(", ")
    }
}
