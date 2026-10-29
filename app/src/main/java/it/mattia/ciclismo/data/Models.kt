package it.mattia.ciclismo.data

import it.mattia.ciclismo.engine.Body
import it.mattia.ciclismo.engine.Food
import it.mattia.ciclismo.engine.Foods
import it.mattia.ciclismo.engine.Goal
import it.mattia.ciclismo.engine.Metrics
import it.mattia.ciclismo.engine.Sex
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Supplement(val name: String, val carbG: Int, val sodiumMg: Int)

data class Profile(
    val name: String = "",
    val sex: Sex = Sex.M,
    val age: Int = 30,
    val height: Double = 180.0,
    val weight: Double = 74.0,
    val bodyFat: Double? = null,
    val restHr: Int = 55,
    val maxHr: Int = 188,
    val ftp: Int = 260,
    val bikeKg: Double = 9.0,
    val goal: Goal = Goal.PERFORMANCE,
    val goalWeight: Double? = null,
    val goalDate: String = "",
    val allergies: String = "",
    val excluded: String = "",
    val favorites: String = "",
    val nMeals: Int = 4,
    val mealTimes: String = "",
    val supplements: List<Supplement> = emptyList(),
    val caffeine: Boolean = true,
    val theme: String = "auto",
    val onboarded: Boolean = false,
    val stravaId: String = "",
    val stravaSecret: String = "",
    val notifMeals: Boolean = false,
    val notifDrink: Boolean = false,
    val notifWeigh: Boolean = false,
    val notifEvening: Boolean = false,
) {
    fun body() = Body(sex, age, height, weight, bodyFat)
}

data class RideType(val id: String, val label: String, val ifv: Double)

object RideTypes {
    val all = listOf(
        RideType("recupero", "Recupero", 0.55),
        RideType("fondo", "Fondo", 0.68),
        RideType("sweetspot", "Sweet spot", 0.88),
        RideType("soglia", "Soglia", 0.95),
        RideType("vo2", "Ripetute VO2", 0.92),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[1]
}

data class PlannedRide(val type: String, val durMin: Int, val startHour: Int)

data class Ride(
    val id: String,
    val startMs: Long,
    val durSec: Int,
    val km: Double,
    val elev: Int,
    val avgW: Double,
    val np: Double,
    val maxW: Int,
    val kj: Double,
    val avgHr: Int = 0,
    val maxHr: Int = 0,
    val cad: Int = 0,
    val drift: Double? = null,
    val carbIn: Int = 0,
    val fluidIn: Int = 0,
    val tempC: Double? = null,
    val rpe: Int = 0,
    val notes: String = "",
    val source: String = "Manuale",
    val chart: List<Int> = emptyList(),
    val chartStep: Int = 1,
    val bests: List<Int> = emptyList(),
) {
    val date: LocalDate get() = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val startHour: Double
        get() {
            val t = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
            return t.hour + t.minute / 60.0
        }
    val vi: Double get() = Metrics.variabilityIndex(np, avgW)
    fun ifv(ftp: Int): Double = Metrics.intensityFactor(np, ftp.toDouble())
    fun tss(ftp: Int): Double = Metrics.tss(durSec, ifv(ftp))
}

data class AppData(
    val profile: Profile = Profile(),
    val rides: List<Ride> = emptyList(),
    val eaten: Map<String, String> = emptyMap(),
    val scale: Map<String, Double> = emptyMap(),
    val water: Map<String, Int> = emptyMap(),
    val weights: List<Pair<String, Double>> = emptyList(),
    val planned: Map<String, PlannedRide> = emptyMap(),
    val lastSync: String = "",
    val sleepHours: Double = 0.0,
    val stravaAccess: String = "",
    val stravaRefresh: String = "",
    val stravaExpires: Long = 0,
    /** Foods from barcodes or typed by hand. */
    val customFoods: List<Food> = emptyList(),
    /** "date|meal|food" -> grams eaten instead of the planned amount (0 = removed). */
    val itemGrams: Map<String, Int> = emptyMap(),
    /** "date|meal" -> foods added to that meal, as (food id, grams). */
    val added: Map<String, List<Pair<String, Int>>> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("profile", profileJson(profile))
        put("rides", JSONArray(rides.map { rideJson(it) }))
        put("eaten", JSONObject(eaten as Map<*, *>))
        put("scale", JSONObject(scale as Map<*, *>))
        put("water", JSONObject(water as Map<*, *>))
        put("weights", JSONArray(weights.map { JSONArray().put(it.first).put(it.second) }))
        put("planned", JSONObject().also { o ->
            planned.forEach { (k, v) ->
                o.put(k, JSONObject().put("type", v.type).put("durMin", v.durMin).put("startHour", v.startHour))
            }
        })
        put("lastSync", lastSync)
        put("sleepHours", sleepHours)
        put("stravaAccess", stravaAccess)
        put("stravaRefresh", stravaRefresh)
        put("stravaExpires", stravaExpires)
        put("customFoods", JSONArray(customFoods.map { foodJson(it) }))
        put("itemGrams", JSONObject(itemGrams as Map<*, *>))
        put("added", JSONObject().also { o ->
            added.forEach { (k, v) -> o.put(k, JSONArray(v.map { JSONArray().put(it.first).put(it.second) })) }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): AppData {
            fun <T> JSONObject.mapOf(key: String, conv: (JSONObject, String) -> T): Map<String, T> {
                val m = optJSONObject(key) ?: return emptyMap()
                return m.keys().asSequence().associateWith { conv(m, it) }
            }
            val rides = o.optJSONArray("rides")
            val weights = o.optJSONArray("weights")
            return AppData(
                profile = profileFrom(o.optJSONObject("profile") ?: JSONObject()),
                rides = if (rides == null) emptyList() else (0 until rides.length()).map { rideFrom(rides.getJSONObject(it)) },
                eaten = o.mapOf("eaten") { j, k -> j.getString(k) },
                scale = o.mapOf("scale") { j, k -> j.getDouble(k) },
                water = o.mapOf("water") { j, k -> j.getInt(k) },
                weights = if (weights == null) emptyList() else (0 until weights.length()).map {
                    val a = weights.getJSONArray(it); a.getString(0) to a.getDouble(1)
                },
                planned = o.mapOf("planned") { j, k ->
                    val p = j.getJSONObject(k); PlannedRide(p.getString("type"), p.getInt("durMin"), p.getInt("startHour"))
                },
                lastSync = o.optString("lastSync", ""),
                sleepHours = o.optDouble("sleepHours", 0.0),
                stravaAccess = o.optString("stravaAccess", ""),
                stravaRefresh = o.optString("stravaRefresh", ""),
                stravaExpires = o.optLong("stravaExpires", 0),
                customFoods = o.optJSONArray("customFoods")?.let { a -> (0 until a.length()).map { foodFrom(a.getJSONObject(it)) } } ?: emptyList(),
                itemGrams = o.mapOf("itemGrams") { j, k -> j.getInt(k) },
                added = o.mapOf("added") { j, k ->
                    val a = j.getJSONArray(k); (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
                },
            )
        }

        private fun profileJson(p: Profile) = JSONObject().apply {
            put("name", p.name); put("sex", p.sex.name); put("age", p.age); put("height", p.height)
            put("weight", p.weight); put("bodyFat", p.bodyFat ?: JSONObject.NULL); put("restHr", p.restHr)
            put("maxHr", p.maxHr); put("ftp", p.ftp); put("bikeKg", p.bikeKg); put("goal", p.goal.name)
            put("goalWeight", p.goalWeight ?: JSONObject.NULL); put("goalDate", p.goalDate)
            put("allergies", p.allergies); put("excluded", p.excluded); put("favorites", p.favorites)
            put("nMeals", p.nMeals); put("mealTimes", p.mealTimes); put("caffeine", p.caffeine)
            put("theme", p.theme); put("onboarded", p.onboarded)
            put("stravaId", p.stravaId); put("stravaSecret", p.stravaSecret)
            put("notifMeals", p.notifMeals); put("notifDrink", p.notifDrink)
            put("notifWeigh", p.notifWeigh); put("notifEvening", p.notifEvening)
            put("supplements", JSONArray(p.supplements.map {
                JSONObject().put("name", it.name).put("carbG", it.carbG).put("sodiumMg", it.sodiumMg)
            }))
        }

        private fun profileFrom(o: JSONObject): Profile {
            val d = Profile()
            val sup = o.optJSONArray("supplements")
            return Profile(
                name = o.optString("name", d.name),
                sex = if (o.optString("sex", "M") == "F") Sex.F else Sex.M,
                age = o.optInt("age", d.age), height = o.optDouble("height", d.height),
                weight = o.optDouble("weight", d.weight),
                bodyFat = if (o.isNull("bodyFat")) null else o.optDouble("bodyFat"),
                restHr = o.optInt("restHr", d.restHr), maxHr = o.optInt("maxHr", d.maxHr),
                ftp = o.optInt("ftp", d.ftp), bikeKg = o.optDouble("bikeKg", d.bikeKg),
                goal = runCatching { Goal.valueOf(o.optString("goal", "PERFORMANCE")) }.getOrDefault(Goal.PERFORMANCE),
                goalWeight = if (o.isNull("goalWeight")) null else o.optDouble("goalWeight"),
                goalDate = o.optString("goalDate", ""),
                allergies = o.optString("allergies", ""), excluded = o.optString("excluded", ""),
                favorites = o.optString("favorites", ""),
                nMeals = o.optInt("nMeals", d.nMeals), mealTimes = o.optString("mealTimes", ""),
                supplements = if (sup == null) emptyList() else (0 until sup.length()).map {
                    val s = sup.getJSONObject(it); Supplement(s.getString("name"), s.getInt("carbG"), s.getInt("sodiumMg"))
                },
                caffeine = o.optBoolean("caffeine", true), theme = o.optString("theme", "auto"),
                onboarded = o.optBoolean("onboarded", false),
                stravaId = o.optString("stravaId", ""), stravaSecret = o.optString("stravaSecret", ""),
                notifMeals = o.optBoolean("notifMeals", false), notifDrink = o.optBoolean("notifDrink", false),
                notifWeigh = o.optBoolean("notifWeigh", false), notifEvening = o.optBoolean("notifEvening", false),
            )
        }

        private fun foodJson(f: Food) = JSONObject().apply {
            put("id", f.id); put("name", f.name); put("kcal", f.kcal); put("c", f.c); put("p", f.p); put("f", f.f)
            put("piece", f.piece); put("liquid", f.liquid); put("category", f.category); put("barcode", f.barcode)
            put("allergens", JSONArray(f.allergens)); put("micro", JSONArray(f.micro.toList()))
        }

        private fun foodFrom(o: JSONObject): Food {
            val m = o.optJSONArray("micro")
            val al = o.optJSONArray("allergens")
            return Food(
                id = o.getString("id"), name = o.getString("name"), kcal = o.optDouble("kcal", 0.0),
                c = o.optDouble("c", 0.0), p = o.optDouble("p", 0.0), f = o.optDouble("f", 0.0),
                piece = o.optInt("piece", 0), liquid = o.optBoolean("liquid", false),
                micro = DoubleArray(Foods.RAW) { i -> m?.optDouble(i, 0.0) ?: 0.0 },
                category = o.optString("category", "I miei alimenti"), barcode = o.optString("barcode", ""),
                allergens = if (al == null) emptyList() else (0 until al.length()).map { al.getString(it) },
            )
        }

        private fun ints(a: List<Int>) = JSONArray(a)

        private fun rideJson(r: Ride) = JSONObject().apply {
            put("id", r.id); put("startMs", r.startMs); put("durSec", r.durSec); put("km", r.km)
            put("elev", r.elev); put("avgW", r.avgW); put("np", r.np); put("maxW", r.maxW); put("kj", r.kj)
            put("avgHr", r.avgHr); put("maxHr", r.maxHr); put("cad", r.cad)
            put("drift", r.drift ?: JSONObject.NULL); put("carbIn", r.carbIn); put("fluidIn", r.fluidIn)
            put("tempC", r.tempC ?: JSONObject.NULL); put("rpe", r.rpe); put("notes", r.notes)
            put("source", r.source); put("chart", ints(r.chart)); put("chartStep", r.chartStep)
            put("bests", ints(r.bests))
        }

        private fun intList(a: JSONArray?): List<Int> = if (a == null) emptyList() else (0 until a.length()).map { a.getInt(it) }

        private fun rideFrom(o: JSONObject) = Ride(
            id = o.getString("id"), startMs = o.getLong("startMs"), durSec = o.getInt("durSec"),
            km = o.optDouble("km", 0.0), elev = o.optInt("elev", 0), avgW = o.optDouble("avgW", 0.0),
            np = o.optDouble("np", 0.0), maxW = o.optInt("maxW", 0), kj = o.optDouble("kj", 0.0),
            avgHr = o.optInt("avgHr", 0), maxHr = o.optInt("maxHr", 0), cad = o.optInt("cad", 0),
            drift = if (o.isNull("drift")) null else o.optDouble("drift"),
            carbIn = o.optInt("carbIn", 0), fluidIn = o.optInt("fluidIn", 0),
            tempC = if (o.isNull("tempC")) null else o.optDouble("tempC"),
            rpe = o.optInt("rpe", 0), notes = o.optString("notes", ""), source = o.optString("source", "Manuale"),
            chart = intList(o.optJSONArray("chart")), chartStep = o.optInt("chartStep", 1),
            bests = intList(o.optJSONArray("bests")),
        )
    }
}
