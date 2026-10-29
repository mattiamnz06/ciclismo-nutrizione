package it.mattia.ciclismo.ui

import it.mattia.ciclismo.data.AppData
import it.mattia.ciclismo.engine.DayInfo
import it.mattia.ciclismo.engine.DayPlan
import it.mattia.ciclismo.engine.Foods
import it.mattia.ciclismo.engine.Item
import it.mattia.ciclismo.engine.Meal
import it.mattia.ciclismo.engine.MealPlanner
import it.mattia.ciclismo.engine.Planner
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the screens need to show one day: plan, meals, what was eaten and the automatic correction. */
class DayView(val d: AppData, val date: LocalDate) {
    val info: DayInfo = Planner.dayInfo(d, date)
    val plan: DayPlan = Planner.plan(d, info)
    val meals: List<Meal> = Planner.meals(d, info)

    private val eff = HashMap<String, Double>()
    private val extra = HashMap<String, Item>()
    var correctionText: String = ""
        private set

    init {
        // Positive: eaten less than planned so far; negative: eaten more. The next meal to eat absorbs half of it (max ±25%).
        var shortKcal = 0.0
        var note = ""
        for (m in meals) {
            val key = key(m)
            val user = d.scale[key] ?: 1.0
            val st = d.eaten[key]
            var s = user
            if (st == null && abs(shortKcal) >= 50) {
                val up = (shortKcal / m.kcal.coerceAtLeast(1) * 0.5).coerceIn(-0.25, 0.25)
                s = user * (1 + up)
                val pct = abs(up * 100).roundToInt()
                note = if (shortKcal > 0) "Hai saltato o ridotto circa ${shortKcal.roundToInt()} kcal: in «${m.name}» le quantità salgono del $pct%."
                else "Hai mangiato circa ${(-shortKcal).roundToInt()} kcal in più: in «${m.name}» le quantità scendono del $pct%."
                shortKcal = 0.0
            }
            eff[key] = s
            if (st == "no") shortKcal += m.kcal
            else if (st == "si") shortKcal += m.kcal - edited(m).kcal
        }
        // Micronutrient correction: the next meal still to eat gets a portion of a food rich in the weakest one.
        var microNote: String? = null
        val excluded = MealPlanner.expandExcluded(d.profile.excluded + "," + d.profile.allergies)
        val b = Planner.booster(micros, excluded)
        val next = meals.firstOrNull { status(it) == null }
        if (b != null && next != null) {
            val fd = Foods.get(b.foodId)
            extra[key(next)] = Item(fd, b.grams)
            microNote = "${b.nutrient} a fine giornata al ${b.percent}% del fabbisogno: in «${next.name}» aggiungo ${b.grams} g di ${fd.name.lowercase()}."
        }
        correctionText = listOfNotNull(
            note.ifEmpty { "Nessuna correzione: stai seguendo il piano." },
            microNote,
        ).joinToString("\n")
    }

    fun key(m: Meal) = "$date|${m.id}"
    fun status(m: Meal): String? = d.eaten[key(m)]
    fun userScale(m: Meal): Double = d.scale[key(m)] ?: 1.0
    fun scaleOf(m: Meal): Double = eff[key(m)] ?: 1.0
    /** Grams the user set for one planned food, or null when it follows the plan. */
    fun itemGrams(m: Meal, foodId: String): Int? = d.itemGrams["${key(m)}|$foodId"]

    fun added(m: Meal): List<Pair<String, Int>> = d.added[key(m)].orEmpty()

    /** Plan scaled by the correction, with the user's own grams and added foods. */
    private fun edited(m: Meal): Meal {
        val s = m.scaled(scaleOf(m))
        val planned = s.items.mapNotNull { it ->
            val g = itemGrams(m, it.food.id) ?: return@mapNotNull it
            if (g <= 0) null else Item(it.food, g)
        }
        return s.withItems(planned + added(m).map { Item(Foods.get(it.first), it.second) })
    }

    fun shown(m: Meal): Meal {
        val s = edited(m)
        val e = extra[key(m)] ?: return s
        return s.withItems(s.items + e)
    }

    /** True when the item at [index] of [shown] is the automatic micronutrient addition. */
    fun isBooster(m: Meal, index: Int): Boolean = extra[key(m)] != null && index == shown(m).items.lastIndex

    /** Planned foods of the meal, including removed ones, so they can be restored. */
    fun plannedItems(m: Meal): List<Item> = m.scaled(scaleOf(m)).items

    val eatenMeals: List<Meal> get() = meals.filter { status(it) == "si" }.map { shown(it) }
    val eatenKcal: Int get() = eatenMeals.sumOf { it.kcal }
    val eatenC: Int get() = eatenMeals.sumOf { it.c }
    val eatenP: Int get() = eatenMeals.sumOf { it.p }
    val eatenF: Int get() = eatenMeals.sumOf { it.f }
    val plannedKcal: Int get() = meals.sumOf { it.kcal }

    /** Eaten so far and full day, where skipped meals count as zero. */
    val micros: List<Triple<String, Int, Int>>
        get() = Planner.micros(meals.filter { status(it) != "no" }.map { shown(it) }, eatenMeals)

    val waterGoal: Int get() = Planner.waterGoal(d, info)
    val water: Int get() = d.water[date.toString()] ?: 0
}
