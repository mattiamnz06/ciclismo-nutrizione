package it.mattia.ciclismo.sync

import android.net.Uri
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.data.Ride
import it.mattia.ciclismo.data.RideFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Strava via OAuth 2.0 with the user's own API keys (strava.com/settings/api). */
object Strava {
    const val REDIRECT = "ciclismo://strava"
    private const val API = "https://www.strava.com/api/v3"

    fun authUrl(clientId: String): String =
        "https://www.strava.com/oauth/authorize?client_id=${Uri.encode(clientId)}" +
            "&redirect_uri=${Uri.encode(REDIRECT)}&response_type=code&approval_prompt=auto" +
            "&scope=read,activity:read_all"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun http(method: String, url: String, body: String? = null, token: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 30000
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IOException("Strava HTTP $code: ${text.take(160)}")
        return text
    }

    private fun saveTokens(j: JSONObject) = Repo.update {
        it.copy(
            stravaAccess = j.getString("access_token"),
            stravaRefresh = j.getString("refresh_token"),
            stravaExpires = j.getLong("expires_at"),
        )
    }

    suspend fun exchange(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = Repo.data.profile
            val body = "client_id=${enc(p.stravaId)}&client_secret=${enc(p.stravaSecret)}" +
                "&code=${enc(code)}&grant_type=authorization_code"
            saveTokens(JSONObject(http("POST", "https://www.strava.com/oauth/token", body)))
        }
    }

    private fun accessToken(): String {
        val d = Repo.data
        if (d.stravaRefresh.isEmpty()) throw IOException("Strava non collegato")
        if (d.stravaExpires > System.currentTimeMillis() / 1000 + 60) return d.stravaAccess
        val p = d.profile
        val body = "client_id=${enc(p.stravaId)}&client_secret=${enc(p.stravaSecret)}" +
            "&grant_type=refresh_token&refresh_token=${enc(d.stravaRefresh)}"
        saveTokens(JSONObject(http("POST", "https://www.strava.com/oauth/token", body)))
        return Repo.data.stravaAccess
    }

    private fun ints(a: JSONArray?): List<Int> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optDouble(it, 0.0).toInt() }

    fun disconnect() = Repo.update { it.copy(stravaAccess = "", stravaRefresh = "", stravaExpires = 0) }

    /** Imports the last 120 days. Streams (second by second watts) are fetched for the newest 20 new rides. */
    suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val after = System.currentTimeMillis() / 1000 - 120L * 86400
            val list = JSONArray(http("GET", "$API/athlete/activities?after=$after&per_page=100", token = accessToken()))
            val known = Repo.data.rides.filter { it.chart.isNotEmpty() }.map { it.id }.toSet()
            val out = ArrayList<Ride>()
            var streams = 0
            for (i in 0 until list.length()) {
                val a = list.getJSONObject(i)
                val sport = a.optString("sport_type", a.optString("type", ""))
                if (!sport.contains("Ride")) continue
                val id = "strava_${a.getLong("id")}"
                if (id in known) continue
                val startMs = LocalDateTime.parse(a.getString("start_date_local").removeSuffix("Z"))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dur = a.optInt("moving_time", 0)
                val km = a.optDouble("distance", 0.0) / 1000.0
                val elev = a.optDouble("total_elevation_gain", 0.0).toInt()
                val hasPower = a.optDouble("average_watts", 0.0) > 0
                var watts = emptyList<Int>()
                var hr = emptyList<Int>()
                if (hasPower && streams < 20) {
                    try {
                        val s = JSONObject(
                            http("GET", "$API/activities/${a.getLong("id")}/streams?keys=time,watts,heartrate&key_by_type=true", token = accessToken()),
                        )
                        streams++
                        val time = ints(s.optJSONObject("time")?.optJSONArray("data"))
                        watts = RideFactory.resample(time, ints(s.optJSONObject("watts")?.optJSONArray("data")))
                        hr = RideFactory.resample(time, ints(s.optJSONObject("heartrate")?.optJSONArray("data")))
                    } catch (_: Exception) {
                    }
                }
                out += RideFactory.fromStream(
                    id = id, startMs = startMs, durSec = dur, km = km, elev = elev, source = "Strava",
                    watts = watts, hr = hr,
                    avgHrIn = a.optDouble("average_heartrate", 0.0).toInt(),
                    maxHrIn = a.optDouble("max_heartrate", 0.0).toInt(),
                    cad = a.optDouble("average_cadence", 0.0).toInt(),
                    maxWIn = a.optInt("max_watts", 0),
                    avgWIn = a.optDouble("average_watts", 0.0),
                    npIn = a.optDouble("weighted_average_watts", 0.0),
                )
            }
            Repo.addRides(out)
            val t = LocalTime.now()
            Repo.update { it.copy(lastSync = "%02d:%02d".format(t.hour, t.minute)) }
            out.size
        }
    }
}
