package it.mattia.ciclismo

import it.mattia.ciclismo.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineTest {

    @Test fun npOfConstantPowerIsThatPower() {
        assertEquals(200.0, Metrics.np(List(600) { 200 }), 1e-6)
    }

    @Test fun npIsAboveAverageForVariableRide() {
        val w = List(600) { if ((it / 30) % 2 == 0) 100 else 300 }
        assertEquals(true, Metrics.np(w) > w.average())
    }

    @Test fun tssOfOneHourAtFtpIs100() {
        assertEquals(100.0, Metrics.tss(3600, Metrics.intensityFactor(260.0, 260.0)), 1e-9)
    }

    @Test fun kilojoules() {
        assertEquals(900.0, Metrics.kilojoules(200.0, 4500), 1e-9)
    }

    @Test fun bestEffort() {
        val w = List(10) { 100 } + List(5) { 300 } + List(10) { 100 }
        assertEquals(300.0, Metrics.bestEffort(w, 5)!!, 1e-9)
        assertNull(Metrics.bestEffort(w, 100))
    }

    @Test fun zones() {
        assertEquals(1, Metrics.zone(100.0, 260.0))
        assertEquals(4, Metrics.zone(260.0, 260.0))
        assertEquals(6, Metrics.zone(400.0, 260.0))
    }

    @Test fun formRisesWithLoad() {
        val f = Metrics.form(List(42) { 100.0 })
        assertEquals(true, f.atl > f.ctl)
    }

    @Test fun bmrMifflinMale() {
        val b = Body(Sex.M, 30, 180.0, 74.0)
        assertEquals(1720.0, Nutrition.bmr(b), 1.0)
    }

    @Test fun dayPlanMacros() {
        val b = Body(Sex.M, 30, 180.0, 74.0)
        val p = Nutrition.dayPlan(b, DayType.MEDIUM, Goal.PERFORMANCE, 900.0)
        assertEquals(444, p.carbG)
        assertEquals(133, p.proteinG)
    }

    @Test fun rideFuelHeat() {
        assertEquals(812,Nutrition.rideFuel(240, tempC = 30.0).fluidMlPerH)
    }
}
