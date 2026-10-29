package it.mattia.ciclismo.sync

import android.content.Context
import android.net.Uri
import android.util.Xml
import it.mattia.ciclismo.data.Ride
import it.mattia.ciclismo.data.RideFactory
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimal GPX reader: time, elevation, and power / heart rate extensions when present. */
object Gpx {
    private class Pt(var lat: Double, var lon: Double) {
        var t = 0L
        var ele = 0.0
        var power = -1
        var hr = -1
    }

    private fun dist(a: Pt, b: Pt): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val x = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(x), sqrt(1 - x))
    }

    fun parse(ctx: Context, uri: Uri, fileName: String): Result<Ride> = runCatching {
        val pts = ArrayList<Pt>()
        ctx.contentResolver.openInputStream(uri)!!.use { s ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(s, null)
            var cur: Pt? = null
            var tag = ""
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        val local = p.name.substringAfter(':')
                        if (local == "trkpt") {
                            cur = Pt(p.getAttributeValue(null, "lat").toDouble(), p.getAttributeValue(null, "lon").toDouble())
                        }
                        tag = local
                    }
                    XmlPullParser.TEXT -> {
                        val c = cur
                        val txt = p.text.trim()
                        if (c != null && txt.isNotEmpty()) {
                            when (tag) {
                                "ele" -> c.ele = txt.toDoubleOrNull() ?: 0.0
                                "time" -> c.t = runCatching { Instant.parse(txt).toEpochMilli() }.getOrDefault(0L)
                                "power", "watts" -> c.power = txt.toDoubleOrNull()?.toInt() ?: -1
                                "hr" -> c.hr = txt.toDoubleOrNull()?.toInt() ?: -1
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (p.name.substringAfter(':') == "trkpt") {
                            cur?.let { pts.add(it) }
                            cur = null
                        }
                        tag = ""
                    }
                }
                ev = p.next()
            }
        }
        require(pts.size > 10 && pts.first().t > 0) { "GPX senza punti o senza orari" }
        val t0 = pts.first().t
        var km = 0.0
        var gain = 0.0
        for (i in 1 until pts.size) {
            km += dist(pts[i - 1], pts[i])
            val de = pts[i].ele - pts[i - 1].ele
            if (de > 0.5) gain += de
        }
        val time = pts.map { ((it.t - t0) / 1000).toInt() }
        val dur = time.last()
        val watts = if (pts.any { it.power >= 0 }) RideFactory.resample(time, pts.map { it.power.coerceAtLeast(0) }) else emptyList()
        val hr = if (pts.any { it.hr > 0 }) RideFactory.resample(time, pts.map { it.hr.coerceAtLeast(0) }) else emptyList()
        RideFactory.fromStream(
            id = "gpx_${t0}", startMs = t0, durSec = dur, km = km / 1000.0, elev = gain.toInt(),
            source = "GPX", watts = watts, hr = hr,
        )
    }
}
