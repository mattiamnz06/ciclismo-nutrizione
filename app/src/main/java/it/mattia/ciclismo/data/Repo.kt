package it.mattia.ciclismo.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Single source of truth: one JSON file in app storage, exposed as Compose state. */
object Repo {
    var data by mutableStateOf(AppData())
        private set

    /** Authorization code received from the Strava redirect, consumed by the UI. */
    var stravaCode by mutableStateOf<String?>(null)

    var syncMessage by mutableStateOf("")

    private lateinit var file: File

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "data.json")
        data = try {
            AppData.fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            AppData()
        }
    }

    fun update(f: (AppData) -> AppData) {
        data = f(data)
        save()
    }

    fun replaceAll(d: AppData) {
        data = d
        save()
    }

    private fun save() {
        try {
            file.writeText(data.toJson().toString())
        } catch (_: Exception) {
        }
    }

    fun setProfile(f: (Profile) -> Profile) = update { it.copy(profile = f(it.profile)) }

    fun addRide(r: Ride) = update { d ->
        d.copy(rides = (d.rides.filter { it.id != r.id } + r).sortedByDescending { it.startMs })
    }

    fun addRides(list: List<Ride>) = update { d ->
        val ids = list.map { it.id }.toSet()
        d.copy(rides = (d.rides.filter { it.id !in ids } + list).sortedByDescending { it.startMs })
    }

    fun addWater(date: LocalDate, ml: Int) = update { d ->
        val k = date.toString()
        d.copy(water = d.water + (k to ((d.water[k] ?: 0) + ml)))
    }

    fun setEaten(key: String, v: String?) = update { d ->
        d.copy(eaten = if (v == null) d.eaten - key else d.eaten + (key to v))
    }

    fun setScale(key: String, v: Double) = update { d -> d.copy(scale = d.scale + (key to v)) }

    fun plan(date: LocalDate, p: PlannedRide?) = update { d ->
        d.copy(planned = if (p == null) d.planned - date.toString() else d.planned + (date.toString() to p))
    }

    /** Weight from the scale or Health Connect: also updates the profile so everything is recomputed. */
    fun setWeight(date: LocalDate, kg: Double) = update { d ->
        val k = date.toString()
        d.copy(
            weights = (d.weights.filter { it.first != k } + (k to kg)).sortedBy { it.first },
            profile = d.profile.copy(weight = kg),
        )
    }

    fun clearAll() {
        data = AppData()
        save()
    }
}
