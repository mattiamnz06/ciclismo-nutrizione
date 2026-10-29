package it.mattia.ciclismo.engine

import kotlin.math.abs
import kotlin.math.roundToInt

enum class Kind { BREAKFAST, SNACK, LUNCH, DINNER, PRE, POST, SUPPER }

class Item(val food: Food, val grams: Int) {
    val kcal: Double get() = food.kcal * grams / 100
    val c: Double get() = food.c * grams / 100
    val p: Double get() = food.p * grams / 100
    val f: Double get() = food.f * grams / 100

    fun qty(): String {
        val unit = if (food.liquid) "ml" else "g"
        if (food.piece > 0) {
            val n = grams.toDouble() / food.piece
            if (abs(n - n.roundToInt()) < 0.15 && n.roundToInt() >= 1) return "${n.roundToInt()} ($grams g)"
        }
        return "$grams $unit"
    }
}

class Meal(
    val id: String,
    val timeMin: Int,
    val name: String,
    val kind: Kind,
    val items: List<Item>,
    val why: String,
    val how: String,
    val drink: String,
    val alt: String,
    val tag: String,
) {
    val time: String get() = "%02d:%02d".format(timeMin / 60, timeMin % 60)
    val kcal: Int get() = items.sumOf { it.kcal }.roundToInt()
    val c: Int get() = items.sumOf { it.c }.roundToInt()
    val p: Int get() = items.sumOf { it.p }.roundToInt()
    val f: Int get() = items.sumOf { it.f }.roundToInt()
    val summary: String get() = items.take(4).joinToString(", ") { it.food.name.substringBefore(" (").lowercase() }

    fun micro(i: Int, scale: Double = 1.0): Double = items.sumOf { it.food.micro[i] * it.grams / 100 } * scale
}

class MealCtx(
    val type: DayType,
    val plan: DayPlan,
    val weight: Double,
    val hasRide: Boolean,
    val startMin: Int,
    val durMin: Int,
    val rideLabel: String,
    val inRideCarbG: Int,
    val snackTomorrow: Boolean,
    val seed: Int,
    val nMeals: Int,
    val times: List<Int>,
    val excluded: List<String>,
    val favorites: List<String>,
)

/** Chooses foods and grams per meal so that the meal hits its carb, protein and fat targets. */
object MealPlanner {

    fun defaultTimes(n: Int): List<Int> = when {
        n <= 3 -> listOf(450, 780, 1200)
        n == 4 -> listOf(450, 780, 990, 1200)
        n == 5 -> listOf(450, 630, 780, 990, 1200)
        else -> listOf(450, 630, 780, 990, 1200, 1290)
    }

    fun parseTimes(s: String, n: Int): List<Int> {
        val parsed = s.split(',', ';', ' ').mapNotNull {
            val p = it.trim().split(':')
            if (p.size == 2) {
                val h = p[0].toIntOrNull()
                val m = p[1].toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) h * 60 + m else null
            } else null
        }.sorted()
        return if (parsed.size == n) parsed else defaultTimes(n)
    }

    /** Expands intolerances and exclusions into food-name fragments. */
    fun expandExcluded(raw: String): List<String> {
        val out = mutableListOf<String>()
        raw.lowercase().split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
            out += t
            when {
                t.contains("lattosio") || t.contains("latticin") -> out += listOf("latte", "yogurt")
                t.contains("glutine") || t.contains("celiac") -> out += listOf("pasta", "pane", "avena")
                t.contains("pesce") -> out += listOf("salmone", "tonno", "merluzzo")
                t.contains("frutta secca") || t.contains("noci") -> out += "mandorle"
            }
        }
        return out
    }

    private class Slot(val role: Char, val ids: List<String>, val fixed: Int = 0)

    private fun fix(id: String, g: Int) = Slot('F', listOf(id), g)

    private fun matches(id: String, terms: List<String>): Boolean {
        val n = Foods.get(id).name.lowercase()
        return terms.any { it.isNotBlank() && (n.contains(it) || id.contains(it)) }
    }

    private fun pick(ids: List<String>, seed: Int, c: MealCtx): String {
        val ok = ids.filter { !matches(it, c.excluded) }
        val pool = if (ok.isEmpty()) ids else ok
        val fav = pool.firstOrNull { matches(it, c.favorites) }
        return fav ?: pool[Math.floorMod(seed, pool.size)]
    }

    private fun template(kind: Kind, c: MealCtx, seed: Int): List<Slot> {
        val rest = c.type == DayType.REST
        val load = c.type == DayType.LOAD
        return when (kind) {
            Kind.BREAKFAST -> if (seed % 2 == 0)
                listOf(fix("latte", 150), fix("banana", 120), Slot('C', listOf("avena")), Slot('P', listOf("yogurt")), Slot('L', listOf("mandorle")))
            else
                listOf(fix("arancia", 150), Slot('C', listOf("pane")), Slot('P', listOf("uova", "yogurt")), fix("miele", 15))
            Kind.SNACK -> if (seed % 2 == 0)
                listOf(fix("mela", 150), Slot('C', listOf("pane")), Slot('P', listOf("yogurt")), Slot('L', listOf("mandorle")))
            else
                listOf(fix("miele", 10), Slot('C', listOf("banana")), Slot('P', listOf("yogurt")), Slot('L', listOf("mandorle")))
            Kind.LUNCH -> {
                val s = mutableListOf(fix(if (load) "zucchine" else "verdure", if (load) 100 else 200))
                if (rest) s += fix("spinaci", 100)
                s += Slot('C', listOf("riso", "pasta", "patate"))
                s += Slot('P', listOf("pollo", "tacchino", "tonno", "merluzzo"))
                s += Slot('L', listOf("olio"))
                s += Slot('D', if (rest) listOf("ceci") else listOf("pane"))
                s += fix("kiwi", 80)
                s
            }
            Kind.DINNER -> {
                val s = mutableListOf(fix(if (load) "zucchine" else "verdure", if (load) 100 else 250))
                s += Slot('C', listOf("pasta", "patate", "riso"))
                s += Slot('P', listOf("salmone", "merluzzo", "uova", "pollo", "tacchino"))
                s += Slot('L', listOf("olio"))
                s += Slot('D', if (rest) listOf("ceci") else listOf("pane"))
                s += fix("arancia", 150)
                s
            }
            Kind.PRE -> listOf(fix("banana", 120), Slot('C', listOf("pane")), fix("miele", 15), Slot('P', listOf("yogurt")))
            Kind.POST -> listOf(fix("latte", 200), fix("banana", 120), Slot('C', listOf("pane")), fix("miele", 20), Slot('P', listOf("yogurt")))
            Kind.SUPPER -> listOf(Slot('C', listOf("pane")), fix("miele", 20), fix("banana", 120), Slot('P', listOf("yogurt")))
        }
    }

    private class Composed(val items: List<Item>, val carbId: String?, val protId: String?)

    private fun roundG(g: Double, id: String): Int {
        val step = if (id == "olio" || id == "miele") 1 else if (g >= 40) 5 else 1
        return ((g / step).roundToInt() * step)
    }

    private fun compose(kind: Kind, c: MealCtx, seed: Int, tc: Double, tp: Double, tf: Double): Composed {
        val slots = template(kind, c, seed)
        var rc = tc
        var rp = tp
        var rf = tf
        val grams = DoubleArray(slots.size)
        val foods = slots.mapIndexed { i, s -> if (s.role == 'F') s.ids[0] else pick(s.ids, seed + i, c) }
        for ((i, s) in slots.withIndex()) {
            if (s.role != 'F') continue
            if (matches(foods[i], c.excluded)) continue
            val fd = Foods.get(foods[i])
            grams[i] = s.fixed.toDouble()
            rc -= fd.c * s.fixed / 100; rp -= fd.p * s.fixed / 100; rf -= fd.f * s.fixed / 100
        }
        rc = rc.coerceAtLeast(0.0)
        val hasD = slots.any { it.role == 'D' }
        val mainShare = if (hasD) 0.75 else 1.0
        var carbId: String? = null
        var protId: String? = null
        for ((i, s) in slots.withIndex()) {
            val fd = Foods.get(foods[i])
            when (s.role) {
                'C', 'D' -> if (fd.c > 0) {
                    val share = if (s.role == 'C') mainShare else 1.0 - mainShare
                    grams[i] = rc * share / fd.c * 100
                    rp -= fd.p * grams[i] / 100; rf -= fd.f * grams[i] / 100
                    if (s.role == 'C') carbId = fd.id
                }
            }
        }
        for ((i, s) in slots.withIndex()) {
            if (s.role != 'P') continue
            val fd = Foods.get(foods[i])
            grams[i] = (rp.coerceAtLeast(0.0) / fd.p * 100).coerceAtMost(350.0)
            rf -= fd.f * grams[i] / 100
            protId = fd.id
        }
        for ((i, s) in slots.withIndex()) {
            if (s.role != 'L') continue
            val fd = Foods.get(foods[i])
            grams[i] = (rf.coerceAtLeast(0.0) / fd.f * 100).coerceAtMost(30.0)
        }
        val items = slots.indices.mapNotNull { i ->
            val g = roundG(grams[i], foods[i])
            if (g < 3) null else Item(Foods.get(foods[i]), g)
        }
        return Composed(items, carbId, protId)
    }

    private fun clock(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    private fun why(kind: Kind, c: MealCtx, timeMin: Int): String {
        val rideTxt = "${c.rideLabel} alle ${clock(c.startMin)}"
        return when (kind) {
            Kind.BREAKFAST -> "Carboidrati complessi e proteine per partire con il glicogeno pieno e sostenere la mattina" +
                (if (c.hasRide) " ($rideTxt)." else ".")
            Kind.SNACK -> "Tiene stabile la glicemia tra i pasti senza appesantire" + (if (c.hasRide) " in vista del giro ($rideTxt)." else ".")
            Kind.LUNCH -> when {
                c.hasRide && c.startMin > timeMin ->
                    "Carboidrati facili da digerire %.0f ore prima del giro (%s) per arrivare con il glicogeno pieno; proteine per il recupero di ieri; pochi grassi e fibre per non appesantire."
                        .format((c.startMin - timeMin) / 60.0, rideTxt)
                c.type == DayType.REST -> "Giorno di riposo: proteine alte e legumi e spinaci per ferro e magnesio, calorie contenute."
                c.type == DayType.LOAD -> "Carico di carboidrati: poche fibre e niente cibi nuovi per arrivare all'evento con scorte piene."
                else -> "Pasto bilanciato per ricostruire le scorte dopo il giro e sostenere il pomeriggio."
            }
            Kind.DINNER -> when (c.type) {
                DayType.HARD, DayType.MEDIUM -> "Ricostruisce glicogeno e muscoli dopo l'allenamento; le proteine servono al recupero notturno."
                DayType.LOAD -> "Carico di carboidrati: porzioni abbondanti di cibi semplici, poche fibre."
                DayType.REST -> "Proteine e verdure ricche di magnesio per il recupero; carboidrati moderati perché oggi non ti alleni."
                DayType.RECOVERY -> "Recupero attivo: più liquidi, frutta e verdura ricche di antiossidanti, carboidrati medi."
            }
            Kind.PRE -> "Carboidrati veloci circa 90 minuti prima del giro ($rideTxt): riempiono il glicogeno senza pesare. Pochi grassi e fibre."
            Kind.POST -> "Entro 30-60 minuti dalla fine del giro: 1,0-1,2 g/kg di carboidrati e 0,3 g/kg di proteine per recuperare subito."
            Kind.SUPPER -> "Spuntino di carboidrati la sera prima di un giro duro: parti domani con le scorte piene."
        }
    }

    private fun drink(kind: Kind, c: MealCtx): String = when (kind) {
        Kind.PRE -> "300-500 ml di acqua nell'ora prima, con un pizzico di sale se fa caldo."
        Kind.POST -> "500-700 ml di acqua o isotonico, più il 150% del peso perso nel giro."
        else -> "500 ml di acqua."
    }

    private fun name(kind: Kind, timeMin: Int): String = when (kind) {
        Kind.BREAKFAST -> "Colazione"
        Kind.SNACK -> if (timeMin < 720) "Spuntino mattina" else if (timeMin < 1080) "Spuntino pomeriggio" else "Spuntino serale"
        Kind.LUNCH -> "Pranzo"
        Kind.DINNER -> "Cena"
        Kind.PRE -> "Pre-giro"
        Kind.POST -> "Recupero"
        Kind.SUPPER -> "Spuntino per domani"
    }

    private class Target(val kind: Kind, val id: String, val timeMin: Int, var c: Double, var p: Double, var f: Double)

    fun build(c: MealCtx): List<Meal> {
        val w = c.weight
        val ride = c.hasRide && c.durMin >= 45
        val endMin = c.startMin + c.durMin
        val extras = mutableListOf<Target>()
        if (ride) {
            val long = c.durMin >= 120
            extras += Target(Kind.PRE, "pre", (c.startMin - 90).coerceAtLeast(0), w * (if (long) 1.0 else 0.6), w * 0.1, 4.0)
            extras += Target(Kind.POST, "post", (endMin + 30).coerceAtMost(1380), w * (if (long) 1.1 else 0.8), w * 0.3, 5.0)
        }
        if (c.snackTomorrow) extras += Target(Kind.SUPPER, "sera", 1290, w * 0.8, w * 0.1, 3.0)

        val times = c.times.filter { t -> !(ride && t > c.startMin - 120 && t < endMin + 45) }
        val bases = times.map { t ->
            val k = when {
                t < 600 -> Kind.BREAKFAST
                t < 720 -> Kind.SNACK
                t < 900 -> Kind.LUNCH
                t < 1080 -> Kind.SNACK
                t < 1290 -> Kind.DINNER
                else -> Kind.SNACK
            }
            Target(k, "m$t", t, 0.0, 0.0, 0.0)
        }

        val remC = (c.plan.carbG - c.inRideCarbG - extras.sumOf { it.c }).coerceAtLeast(0.0)
        val remP = (c.plan.proteinG - extras.sumOf { it.p }).coerceAtLeast(0.0)
        val remF = (c.plan.fatG - extras.sumOf { it.f }).coerceAtLeast(0.0)
        fun weightOf(k: Kind) = when (k) {
            Kind.BREAKFAST -> 0.25; Kind.SNACK -> 0.10; Kind.LUNCH -> 0.35; else -> 0.30
        }
        val total = bases.sumOf { weightOf(it.kind) }.coerceAtLeast(0.01)
        bases.forEach { b ->
            val s = weightOf(b.kind) / total
            b.c = remC * s; b.p = remP * s; b.f = remF * s
        }

        val all = (bases + extras).sortedBy { it.timeMin }
        return all.mapIndexed { i, t ->
            val comp = compose(t.kind, c, c.seed + i, t.c, t.p, t.f)
            val altParts = listOfNotNull(comp.carbId, comp.protId).mapNotNull { Foods.alternatives[it] }
            var alt = altParts.joinToString("; ").ifEmpty { "Scegli equivalenti con gli stessi macro." }
            if (t.kind == Kind.POST && c.type == DayType.HARD) alt += "; dopo un giro duro puoi concederti una porzione di pizza (circa 250 g)."
            val how = listOfNotNull(comp.carbId, comp.protId).mapNotNull { Foods.howTo[it] }.joinToString("; ").ifEmpty { "Preparazione semplice, poco sale." }
            Meal(
                id = t.id, timeMin = t.timeMin, name = name(t.kind, t.timeMin), kind = t.kind, items = comp.items,
                why = why(t.kind, c, t.timeMin), how = how.replaceFirstChar { it.uppercase() }, drink = drink(t.kind, c), alt = alt,
                tag = when (t.kind) {
                    Kind.PRE -> "Pre-giro"; Kind.POST -> "Recupero"; Kind.SUPPER -> "Per domani"
                    else -> if (c.type == DayType.LOAD) "Carico" else ""
                },
            )
        }
    }
}
