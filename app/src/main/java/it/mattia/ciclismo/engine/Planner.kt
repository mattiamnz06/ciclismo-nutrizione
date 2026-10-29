package it.mattia.ciclismo.engine

import it.mattia.ciclismo.data.AppData
import it.mattia.ciclismo.data.PlannedRide
import it.mattia.ciclismo.data.RideTypes
import java.time.LocalDate

data class DayInfo(
    val date: LocalDate,
    val hasRide: Boolean,
    val done: Boolean,
    val title: String,
    val tss: Double,
    val hours: Double,
    val kj: Double,
    val np: Double,
    val ifv: Double,
    val startHour: Double,
    val durMin: Int,
    val type: DayType,
    val snackTomorrow: Boolean,
    val daysToLoadEvent: Int,
)

/** Turns the stored data into day-by-day decisions (report sections 7 and 8). */
object Planner {

    private fun fmtDur(min: Int) = if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)}" else "$min min"

    private fun plannedTss(p: PlannedRide, ftp: Int): Double {
        val ifv = RideTypes.byId(p.type).ifv
        return p.durMin / 60.0 * ifv * ifv * 100
    }

    private fun rideTssOn(d: AppData, date: LocalDate): Double {
        val done = d.rides.filter { it.date == date }
        if (done.isNotEmpty()) return done.sumOf { it.tss(d.profile.ftp) }
        val p = d.planned[date.toString()] ?: return 0.0
        return plannedTss(p, d.profile.ftp)
    }

    fun dayInfo(d: AppData, date: LocalDate): DayInfo {
        val ftp = d.profile.ftp
        val done = d.rides.filter { it.date == date }.sortedBy { it.startMs }
        val pl = d.planned[date.toString()]
        var tss = 0.0
        var durSec = 0
        var kj = 0.0
        var np = 0.0
        var start = 8.0
        var title = "Riposo"
        var has = false
        var isDone = false
        if (done.isNotEmpty()) {
            has = true; isDone = true
            durSec = done.sumOf { it.durSec }
            tss = done.sumOf { it.tss(ftp) }
            kj = done.sumOf { it.kj }
            np = done.sumOf { it.np * it.durSec } / durSec.coerceAtLeast(1)
            start = done.first().startHour
            title = "%.0f km · %s".format(done.sumOf { it.km }, fmtDur(durSec / 60))
        } else if (pl != null) {
            val rt = RideTypes.byId(pl.type)
            has = true
            durSec = pl.durMin * 60
            np = rt.ifv * ftp
            tss = plannedTss(pl, ftp)
            kj = np / 1.03 * durSec / 1000.0
            start = pl.startHour.toDouble()
            title = "${rt.label} ${fmtDur(pl.durMin)}"
        }
        val hours = durSec / 3600.0
        val ifv = if (ftp > 0) np / ftp else 0.0
        val prev = rideTssOn(d, date.minusDays(1))
        var type = when {
            has -> Nutrition.dayType(tss, hours)
            prev > 150 -> DayType.RECOVERY
            else -> DayType.REST
        }
        var toEvent = 0
        if (type != DayType.HARD) {
            for (k in 1..3) {
                val p = d.planned[date.plusDays(k.toLong()).toString()]
                if (p != null && p.durMin >= 180) { toEvent = k; break }
            }
            if (toEvent > 0) type = DayType.LOAD
        }
        val tomorrow = d.planned[date.plusDays(1).toString()]
        val snack = tomorrow != null && (tomorrow.durMin > 180 || plannedTss(tomorrow, ftp) > 150)
        return DayInfo(date, has, isDone, title, tss, hours, kj, np, ifv, start, (durSec / 60), type, snack, toEvent)
    }

    fun fuelFor(info: DayInfo, d: AppData): Nutrition.RideFuel? {
        if (!info.hasRide) return null
        val intense = info.ifv >= 0.9
        return Nutrition.rideFuel(info.durMin, intense)
    }

    fun plan(d: AppData, info: DayInfo): DayPlan {
        val carb = if (info.type == DayType.LOAD) (if (info.daysToLoadEvent == 3) 6.5 else 9.0) else null
        return Nutrition.dayPlan(d.profile.body(), info.type, d.profile.goal, info.kj, carb)
    }

    fun meals(d: AppData, info: DayInfo): List<Meal> {
        val p = d.profile
        val fuel = fuelFor(info, d)
        val inRide = if (fuel != null) (fuel.carbGPerH * info.hours).toInt() else 0
        val n = p.nMeals.coerceIn(3, 6)
        val ctx = MealCtx(
            type = info.type, plan = plan(d, info), weight = p.weight, hasRide = info.hasRide,
            startMin = (info.startHour * 60).toInt(), durMin = info.durMin,
            rideLabel = info.title,
            inRideCarbG = inRide, snackTomorrow = info.snackTomorrow,
            seed = info.date.toEpochDay().toInt(), nMeals = n,
            times = MealPlanner.parseTimes(p.mealTimes, n),
            excluded = MealPlanner.expandExcluded(p.excluded + "," + p.allergies),
            favorites = p.favorites.lowercase().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() },
        )
        return MealPlanner.build(ctx)
    }

    /** Daily goal for drinking: 30 ml/kg plus the planned ride fluids. */
    fun waterGoal(d: AppData, info: DayInfo): Int {
        val fuel = fuelFor(info, d)
        val ride = if (fuel != null) (fuel.fluidMlPerH * info.hours).toInt() else 0
        return (d.profile.weight * 30).toInt() + ride
    }

    fun dailyTss(d: AppData): Map<LocalDate, Double> =
        d.rides.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.tss(d.profile.ftp) } }

    fun form(d: AppData, upTo: LocalDate): Metrics.Form {
        val m = dailyTss(d)
        if (m.isEmpty()) return Metrics.Form(0.0, 0.0, 0.0)
        val first = m.keys.min()
        if (first.isAfter(upTo)) return Metrics.Form(0.0, 0.0, 0.0)
        val series = ArrayList<Double>()
        var day = first
        while (!day.isAfter(upTo)) { series.add(m[day] ?: 0.0); day = day.plusDays(1) }
        return Metrics.form(series)
    }

    /** CTL and ATL for the last [days] days, for the chart. */
    fun formSeries(d: AppData, today: LocalDate, days: Int): Pair<List<Double>, List<Double>> {
        val ctl = ArrayList<Double>()
        val atl = ArrayList<Double>()
        for (i in days - 1 downTo 0) {
            val f = form(d, today.minusDays(i.toLong()))
            ctl.add(f.ctl); atl.add(f.atl)
        }
        return ctl to atl
    }

    /** Best 20 min power across all rides with a stream, and the FTP it suggests. */
    fun suggestedFtp(d: AppData): Int? {
        val best = d.rides.mapNotNull { it.bests.getOrNull(3)?.takeIf { v -> v > 0 } }.maxOrNull() ?: return null
        return Metrics.estimatedFtp(best.toDouble()).toInt()
    }

    private fun rawSum(meals: List<Meal>): DoubleArray =
        DoubleArray(Foods.RAW) { i -> meals.sumOf { it.micro(i) } }

    /** Percent of the reference intake for each shown nutrient: (name, eaten so far, full day). */
    fun micros(meals: List<Meal>, eaten: List<Meal>): List<Triple<String, Int, Int>> {
        val e = Foods.percents(rawSum(eaten))
        val f = Foods.percents(rawSum(meals))
        return Foods.microNames.mapIndexed { i, n -> Triple(n, e[i], f[i]) }
    }

    /** Small portions that cover a good part of one nutrient, in order of preference (index of [Foods.microNames]). */
    private val boosters = mapOf(
        0 to listOf("semi_zucca" to 25, "lenticchie" to 100, "spinaci" to 80),
        1 to listOf("grana_grattugiato" to 20, "yogurt_bianco" to 125, "mandorle" to 30),
        2 to listOf("mandorle" to 25, "semi_zucca" to 20, "anacardi" to 25),
        3 to listOf("banana" to 120, "kiwi" to 80, "albicocche_secche" to 30),
        4 to listOf("grana_grattugiato" to 15, "crackers" to 25, "olive" to 30),
        5 to listOf("semi_zucca" to 25, "anacardi" to 30, "ceci" to 100),
        6 to listOf("kiwi" to 80, "arancia" to 150, "fragole" to 150),
        7 to listOf("salmone_affumicato" to 50, "sgombro" to 80, "uova" to 60),
        8 to listOf("mandorle" to 20, "nocciole" to 20, "semi_girasole" to 15),
        9 to listOf("yogurt_bianco" to 125, "uova" to 60, "lenticchie" to 100),
        10 to listOf("noci" to 15, "semi_chia" to 10, "semi_lino" to 10),
    )

    class Booster(val nutrient: String, val percent: Int, val foodId: String, val grams: Int)

    /**
     * The nutrient furthest below 80% at the end of the day, with a portion of a food that contains it.
     * Sodium (the table is unsalted, cooking salt is not counted) and vitamin D (mostly from the sun) are only shown.
     */
    fun booster(micros: List<Triple<String, Int, Int>>, excluded: List<String>): Booster? {
        val i = micros.indices.filter { it != 4 && it != 7 && micros[it].third < 80 }.minByOrNull { micros[it].third } ?: return null
        val pick = boosters[i]?.firstOrNull { !MealPlanner.matches(it.first, excluded) } ?: return null
        return Booster(micros[i].first, micros[i].third, pick.first, pick.second)
    }
}
