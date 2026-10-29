package it.mattia.ciclismo

import it.mattia.ciclismo.data.AppData
import it.mattia.ciclismo.data.PlannedRide
import it.mattia.ciclismo.data.Profile
import it.mattia.ciclismo.sync.Notify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotifyTest {
    private val day = LocalDate.of(2026, 6, 10)

    @Test fun masterSwitchOffMeansNothing() {
        val d = AppData(profile = Profile(notifOn = false, notifMeals = true, notifWeigh = true))
        assertTrue(Notify.plannedToday(d, day).isEmpty())
    }

    @Test fun onlySelectedMealKindsWithLead() {
        val d = AppData(profile = Profile(notifOn = true, notifMeals = true, notifMealKinds = "LUNCH", notifMealLead = 15))
        val list = Notify.plannedToday(d, day)
        assertEquals(1, list.size)
        assertTrue(list[0].title.startsWith("Pranzo"))
        assertEquals(780 - 15, list[0].minuteOfDay)
    }

    @Test fun rideDrinkRemindersFollowThePlannedRide() {
        val d = AppData(
            profile = Profile(notifOn = true, notifDrink = true, notifDrinkEvery = 20),
            planned = mapOf(day.toString() to PlannedRide("fondo", 120, 9)),
        )
        val list = Notify.plannedToday(d, day)
        assertEquals(6, list.size)
        assertEquals(9 * 60 + 20, list.minOf { it.minuteOfDay })
    }

    @Test fun waterEveryTwoHours() {
        val d = AppData(profile = Profile(notifOn = true, notifWater = true, notifWaterEvery = 2, notifWaterFrom = 540, notifWaterTo = 1260))
        assertEquals(listOf(540, 660, 780, 900, 1020, 1140, 1260), Notify.plannedToday(d, day).map { it.minuteOfDay })
    }
}
