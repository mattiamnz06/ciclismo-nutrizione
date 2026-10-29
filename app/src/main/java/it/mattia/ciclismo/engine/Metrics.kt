package it.mattia.ciclismo.engine

import kotlin.math.pow

/** Power metrics (report section 9). All functions are pure. */
object Metrics {

    /** Normalized Power: 30 s rolling mean, 4th power, mean, 4th root. 1 Hz samples. */
    fun np(watts: List<Int>): Double {
        if (watts.size < 30) return if (watts.isEmpty()) 0.0 else watts.average()
        var sum = 0.0
        for (i in 0 until 30) sum += watts[i]
        var acc = 0.0
        var n = 0
        acc += (sum / 30).pow(4); n++
        for (i in 30 until watts.size) {
            sum += watts[i] - watts[i - 30]
            acc += (sum / 30).pow(4); n++
        }
        return (acc / n).pow(0.25)
    }

    /** NP fallback when no stream is available: avg watts x 1.05. */
    fun npFromAverage(avgWatts: Double): Double = avgWatts * 1.05

    fun intensityFactor(np: Double, ftp: Double): Double = if (ftp > 0) np / ftp else 0.0

    fun variabilityIndex(np: Double, avgWatts: Double): Double = if (avgWatts > 0) np / avgWatts else 0.0

    /** TSS = hours x IF^2 x 100. */
    fun tss(durationSec: Int, intensityFactor: Double): Double =
        durationSec / 3600.0 * intensityFactor * intensityFactor * 100.0

    /** Work in kJ = avg watts x seconds / 1000 (1 kJ is about 1 kcal). */
    fun kilojoules(avgWatts: Double, durationSec: Int): Double = avgWatts * durationSec / 1000.0

    fun wattsPerKg(ftp: Double, weightKg: Double): Double = if (weightKg > 0) ftp / weightKg else 0.0

    /** Best mean power over [windowSec] seconds, or null if the ride is shorter. */
    fun bestEffort(watts: List<Int>, windowSec: Int): Double? {
        if (watts.size < windowSec || windowSec <= 0) return null
        var sum = 0L
        for (i in 0 until windowSec) sum += watts[i]
        var best = sum
        for (i in windowSec until watts.size) {
            sum += watts[i] - watts[i - windowSec]
            if (sum > best) best = sum
        }
        return best.toDouble() / windowSec
    }

    /** Suggested new FTP: 95% of the best 20 min. */
    fun estimatedFtp(best20MinWatts: Double): Double = best20MinWatts * 0.95

    /** Coggan zone (1-6, 7 never used here) for a power value; zones from report section 9. */
    fun zone(watts: Double, ftp: Double): Int {
        val p = watts / ftp
        return when {
            p < 0.55 -> 1
            p < 0.76 -> 2
            p < 0.91 -> 3
            p < 1.06 -> 4
            p < 1.21 -> 5
            else -> 6
        }
    }

    data class Form(val ctl: Double, val atl: Double, val tsb: Double)

    /**
     * Performance Management Chart. [dailyTss] is ordered oldest to newest.
     * CTL: exponential mean over 42 days, ATL over 7 days; TSB = yesterday's CTL - ATL.
     */
    fun form(dailyTss: List<Double>, ctl0: Double = 0.0, atl0: Double = 0.0): Form {
        var ctl = ctl0
        var atl = atl0
        var tsb = ctl0 - atl0
        for (t in dailyTss) {
            tsb = ctl - atl
            ctl += (t - ctl) / 42.0
            atl += (t - atl) / 7.0
        }
        return Form(ctl, atl, tsb)
    }
}
