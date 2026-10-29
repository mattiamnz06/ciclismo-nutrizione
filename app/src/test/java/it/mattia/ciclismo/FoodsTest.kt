package it.mattia.ciclismo

import it.mattia.ciclismo.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodsTest {
    @Test fun tableHasEnoughFoodsWithAllColumns() {
        assertTrue(Foods.all.size >= 200)
        assertTrue(Foods.all.values.all { it.micro.size == Foods.RAW })
        val oats = Foods.get("avena")
        assertTrue(oats.kcal in 350.0..400.0 && oats.c in 55.0..70.0)
        assertTrue("glutine" in oats.allergens)
    }

    @Test fun percentsCoverElevenNutrients() {
        val raw = Foods.rawRda.copyOf()
        assertEquals(List(11) { 100 }, Foods.percents(raw))
    }

    @Test fun plannerOnlyUsesKnownFoods() {
        val body = Body(Sex.M, 30, 180.0, 74.0)
        for (type in DayType.values()) for (seed in 0 until 12) for (ride in listOf(false, true)) {
            val plan = Nutrition.dayPlan(body, type, Goal.PERFORMANCE, if (ride) 1500.0 else 0.0)
            val ctx = MealCtx(
                type, plan, 74.0, ride, 10 * 60, if (ride) 150 else 0, "Fondo", if (ride) 90 else 0,
                seed % 2 == 0, seed, 5, MealPlanner.defaultTimes(5), emptyList(), emptyList(),
            )
            MealPlanner.build(ctx).flatMap { it.items }.forEach { assertNotNull(it.food.id, Foods.find(it.food.id)) }
        }
    }

    @Test fun allergenExclusionUsesTags() {
        val ex = MealPlanner.expandExcluded("lattosio, glutine")
        assertTrue(MealPlanner.matches("yogurt", ex))
        assertTrue(MealPlanner.matches("pane", ex))
        assertTrue(!MealPlanner.matches("riso", ex))
    }

    @Test fun boosterPicksWeakestNutrientButNotSodiumOrVitaminD() {
        val micros = Foods.microNames.mapIndexed { i, n -> Triple(n, 0, if (i == 2) 40 else if (i == 7) 5 else 100) }
        val b = Planner.booster(micros, emptyList())
        assertEquals("Magnesio", b?.nutrient)
        assertNotNull(Foods.find(b!!.foodId))
    }

    @Test fun searchFindsByName() {
        assertTrue(Foods.search("parmig").any { it.id == "parmigiano" })
    }
}
