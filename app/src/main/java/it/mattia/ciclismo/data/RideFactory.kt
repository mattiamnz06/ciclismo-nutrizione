package it.mattia.ciclismo.data

import it.mattia.ciclismo.engine.Metrics
import kotlin.math.roundToInt

/** Builds rides from raw streams or from manual input. */
object RideFactory {

    /** Fills a 1 Hz series from (time, value) samples, holding the last value across gaps. */
    fun resample(time: List<Int>, values: List<Int>): List<Int> {
        if (time.isEmpty() || values.isEmpty()) return emptyList()
        val n = minOf(time.size, values.size)
        val total = time[n - 1] + 1
        val out = IntArray(total)
        var idx = 0
        var last = values[0]
        for (s in 0 until total) {
            while (idx < n && time[idx] <= s) { last = values[idx]; idx++ }
            out[s] = last
        }
        return out.toList()
    }

    private fun chartOf(watts: List<Int>): Pair<List<Int>, Int> {
        if (watts.isEmpty()) return emptyList<Int>() to 1
        val step = maxOf(1, watts.size / 170)
        val pts = watts.chunked(step).map { it.average().roundToInt() }
        return pts to step
    }

    private fun bestsOf(watts: List<Int>): List<Int> =
        listOf(5, 60, 300, 1200).map { Metrics.bestEffort(watts, it)?.roundToInt() ?: 0 }

    /** Pw:FC drift: change of efficiency (avg watts / avg HR) between the first and second half. */
    private fun driftOf(watts: List<Int>, hr: List<Int>): Double? {
        val n = minOf(watts.size, hr.size)
        if (n < 1200) return null
        val h = n / 2
        val e1 = watts.subList(0, h).average() / hr.subList(0, h).average().coerceAtLeast(1.0)
        val e2 = watts.subList(h, n).average() / hr.subList(h, n).average().coerceAtLeast(1.0)
        return if (e1 > 0) (e2 / e1 - 1.0) * 100.0 else null
    }

    fun fromStream(
        id: String, startMs: Long, durSec: Int, km: Double, elev: Int, source: String,
        watts: List<Int>, hr: List<Int>, avgHrIn: Int = 0, maxHrIn: Int = 0, cad: Int = 0,
        maxWIn: Int = 0, avgWIn: Double = 0.0, npIn: Double = 0.0,
    ): Ride {
        val hasW = watts.isNotEmpty()
        val avgW = if (hasW) watts.average() else avgWIn
        val np = if (hasW) Metrics.np(watts) else if (npIn > 0) npIn else Metrics.npFromAverage(avgW)
        val (chart, step) = chartOf(watts)
        return Ride(
            id = id, startMs = startMs, durSec = durSec, km = km, elev = elev,
            avgW = avgW, np = np, maxW = if (hasW) watts.max() else maxWIn,
            kj = Metrics.kilojoules(avgW, durSec),
            avgHr = if (hr.isNotEmpty()) hr.average().roundToInt() else avgHrIn,
            maxHr = if (hr.isNotEmpty()) hr.max() else maxHrIn,
            cad = cad, drift = if (hasW && hr.isNotEmpty()) driftOf(watts, hr) else null,
            source = source, chart = chart, chartStep = step, bests = if (hasW) bestsOf(watts) else emptyList(),
        )
    }

    fun manual(
        startMs: Long, durMin: Int, km: Double, elev: Int, avgW: Double, npIn: Double, avgHr: Int,
        carbIn: Int, fluidIn: Int, tempC: Double?, rpe: Int, notes: String,
    ): Ride {
        val sec = durMin * 60
        val np = if (npIn > 0) npIn else Metrics.npFromAverage(avgW)
        return Ride(
            id = "man_$startMs", startMs = startMs, durSec = sec, km = km, elev = elev, avgW = avgW,
            np = np, maxW = 0, kj = Metrics.kilojoules(avgW, sec), avgHr = avgHr, carbIn = carbIn,
            fluidIn = fluidIn, tempC = tempC, rpe = rpe, notes = notes, source = "Manuale",
        )
    }
}
