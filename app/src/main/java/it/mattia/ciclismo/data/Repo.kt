package it.mattia.ciclismo.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.mattia.ciclismo.engine.Food
import it.mattia.ciclismo.engine.Foods
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
        Foods.setCustom(data.customFoods)
    }

    /** Loads the data when the process was started by a receiver instead of the activity. */
    fun ensure(ctx: Context) {
        if (!::file.isInitialized) init(ctx.applicationContext)
    }

    fun update(f: (AppData) -> AppData) {
        data = f(data)
        Foods.setCustom(data.customFoods)
        save()
    }

    fun replaceAll(d: AppData) {
        data = d
        Foods.setCustom(data.customFoods)
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

    /** Grams of one planned food in a meal; null goes back to the plan, 0 removes it. */
    fun setItemGrams(mealKey: String, foodId: String, g: Int?) = update { d ->
        val k = "$mealKey|$foodId"
        d.copy(itemGrams = if (g == null) d.itemGrams - k else d.itemGrams + (k to g.coerceAtLeast(0)))
    }

    fun addToMeal(mealKey: String, foodId: String, g: Int) = update { d ->
        d.copy(added = d.added + (mealKey to (d.added[mealKey].orEmpty() + (foodId to g))))
    }

    /** Changes the grams of the [index]-th added food; 0 removes it. */
    fun setAddedGrams(mealKey: String, index: Int, g: Int) = update { d ->
        val list = d.added[mealKey].orEmpty().toMutableList()
        if (index !in list.indices) return@update d
        if (g <= 0) list.removeAt(index) else list[index] = list[index].first to g
        d.copy(added = if (list.isEmpty()) d.added - mealKey else d.added + (mealKey to list))
    }

    /** Saves a food from a barcode or typed by hand, replacing one with the same id. */
    fun saveFood(f: Food) = update { d -> d.copy(customFoods = d.customFoods.filter { it.id != f.id } + f) }

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
        Foods.setCustom(emptyList())
        save()
    }
}
