package it.mattia.ciclismo

import it.mattia.ciclismo.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MealPlannerTest {
    private val body = Body(Sex.M, 30, 180.0, 74.0)

    private fun ctx(type: DayType, hasRide: Boolean, excluded: List<String> = emptyList()): MealCtx {
        val plan = Nutrition.dayPlan(body, type, Goal.PERFORMANCE, if (hasRide) 900.0 else 0.0)
        return MealCtx(
            type, plan, 74.0, hasRide, 17 * 60 + 30, if (hasRide) 75 else 0, "Sweet spot", if (hasRide) 56 else 0,
            false, 3, 4, MealPlanner.defaultTimes(4), excluded, emptyList(),
        )
    }

    @Test fun restDayMacrosAreClose() {
        val c = ctx(DayType.REST, false)
        val meals = MealPlanner.build(c)
        assertEquals(4, meals.size)
        assertTrue(abs(meals.sumOf { it.c } - c.plan.carbG) < c.plan.carbG * 0.15)
        assertTrue(abs(meals.sumOf { it.p } - c.plan.proteinG) < c.plan.proteinG * 0.25)
    }

    @Test fun rideDayAddsPreAndPostMeals() {
        val meals = MealPlanner.build(ctx(DayType.MEDIUM, true))
        assertTrue(meals.any { it.kind == Kind.PRE })
        assertTrue(meals.any { it.kind == Kind.POST })
        assertTrue(meals.all { it.items.isNotEmpty() })
    }

    @Test fun excludedFoodsNeverAppearWhenAlternativesExist() {
        val meals = MealPlanner.build(ctx(DayType.MEDIUM, false, listOf("riso", "pollo")))
        assertTrue(meals.none { m -> m.items.any { it.food.id == "riso" || it.food.id == "pollo" } })
    }

    @Test fun parseTimesFallsBackToDefault() {
        assertEquals(MealPlanner.defaultTimes(4), MealPlanner.parseTimes("07:30", 4))
        assertEquals(listOf(420, 780, 1200), MealPlanner.parseTimes("07:00, 13:00, 20:00", 3))
    }
}
