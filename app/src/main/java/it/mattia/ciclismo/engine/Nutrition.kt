package it.mattia.ciclismo.engine

enum class Goal(val label: String) {
    PERFORMANCE("Performance"), CUT("Dimagrimento"), MAINTAIN("Mantenimento"), MASS("Massa")
}

enum class Sex { M, F }

/** Day types from report section 7. Carb and protein ranges are g/kg. */
enum class DayType(val label: String, val carbPerKg: Double, val proteinPerKg: Double) {
    REST("Riposo", 3.5, 1.8),
    RECOVERY("Recupero", 4.5, 1.8),
    MEDIUM("Medio", 6.0, 1.8),
    HARD("Duro", 8.5, 1.8),
    LOAD("Carico carbo", 8.0, 1.5)
}

data class Body(
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val bodyFatPct: Double? = null,
)

data class DayPlan(
    val kcal: Int,
    val carbG: Int,
    val proteinG: Int,
    val fatG: Int,
    val bmr: Int,
    val baseKcal: Int,
)

/** Calories and macros (report sections 7 and 8). */
object Nutrition {

    /** Katch-McArdle when body fat is known, otherwise Mifflin-St Jeor. */
    fun bmr(b: Body): Double {
        val fat = b.bodyFatPct
        if (fat != null) {
            val lean = b.weightKg * (1 - fat / 100.0)
            return 370 + 21.6 * lean
        }
        val base = 10 * b.weightKg + 6.25 * b.heightCm - 5 * b.age
        return if (b.sex == Sex.M) base + 5 else base - 161
    }

    fun dayType(rideTss: Double?, rideHours: Double?): DayType = when {
        rideTss == null || rideHours == null || rideHours == 0.0 -> DayType.REST
        rideTss > 150 || rideHours > 3 -> DayType.HARD
        rideHours < 1 -> DayType.RECOVERY
        else -> DayType.MEDIUM
    }

    /** Day plan: BMR x 1.4 + ride kcal, goal adjustment, carbs and protein by g/kg, fat fills the rest. */
    fun dayPlan(b: Body, type: DayType, goal: Goal, rideKj: Double): DayPlan {
        val bmr = bmr(b)
        val base = bmr * 1.4
        var kcal = base + rideKj
        kcal += when (goal) {
            Goal.CUT -> if (type == DayType.HARD) 0.0 else -300.0
            Goal.MASS -> 300.0
            else -> 0.0
        }
        val carb = type.carbPerKg * b.weightKg
        val protein = type.proteinPerKg * b.weightKg
        val fat = ((kcal - carb * 4 - protein * 4) / 9).coerceAtLeast(b.weightKg * 0.8)
        return DayPlan(kcal.toInt(), carb.toInt(), protein.toInt(), fat.toInt(), bmr.toInt(), base.toInt())
    }

    data class RideFuel(val carbGPerH: Int, val fluidMlPerH: Int, val sodiumMgPerH: Int)

    /** In-ride targets by duration (report section 8), mid values; +10 g/h at threshold, +25% fluids above 25 C. */
    fun rideFuel(durationMin: Int, intense: Boolean = false, tempC: Double = 15.0): RideFuel {
        var carb: Int
        var fluid: Int
        var sodium: Int
        when {
            durationMin < 60 -> { carb = 0; fluid = 500; sodium = 150 }
            durationMin < 120 -> { carb = 45; fluid = 625; sodium = 450 }
            durationMin < 180 -> { carb = 60; fluid = 625; sodium = 550 }
            else -> { carb = 75; fluid = 650; sodium = 600 }
        }
        if (intense && durationMin >= 60) carb += 10
        if (tempC > 25) { fluid = (fluid * 1.25).toInt(); sodium = (sodium * 1.25).toInt() }
        return RideFuel(carb, fluid, sodium)
    }
}
