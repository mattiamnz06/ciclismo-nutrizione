package it.mattia.ciclismo.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.data.Ride
import it.mattia.ciclismo.data.RideFactory
import it.mattia.ciclismo.engine.Metrics
import it.mattia.ciclismo.engine.Nutrition
import it.mattia.ciclismo.sync.Gpx
import it.mattia.ciclismo.sync.Strava
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun syncStravaNow(scope: CoroutineScope) {
    scope.launch {
        Repo.syncMessage = "Sincronizzazione Strava in corso…"
        Repo.syncMessage = Strava.sync().fold({ "Importati $it nuovi giri da Strava" }, { "Errore Strava: ${it.message}" })
    }
}

@Composable
fun RidesScreen(nav: NavState) {
    val d = Repo.data
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ride = d.rides.firstOrNull { it.id == nav.rideId }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else "file.gpx"
            } ?: "file.gpx"
            Gpx.parse(ctx, uri, name).fold(
                { Repo.addRide(it); Repo.syncMessage = "Importato il giro da $name" },
                { Repo.syncMessage = "Importazione GPX non riuscita: ${it.message}" },
            )
        }
    }

    when {
        nav.manual -> ManualRide(nav)
        ride != null -> RideDetail(ride, nav)
        else -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Strava", Modifier.weight(1f), primary = false) {
                    if (d.stravaRefresh.isEmpty()) { Repo.syncMessage = "Collega prima Strava in Impostazioni › Sincronizzazioni"; } else syncStravaNow(scope)
                }
                Btn("GPX", Modifier.weight(1f), primary = false) { picker.launch(arrayOf("*/*")) }
                Btn("+ A mano", Modifier.weight(1f)) { nav.manual = true }
            }
            val msg = Repo.syncMessage
            Muted(if (msg.isNotEmpty()) msg else "Strava: ultima sincronizzazione ${d.lastSync.ifEmpty { "mai" }}", 12, Modifier.padding(vertical = 8.dp))
            if (d.rides.isEmpty()) {
                AppCard { Text("Nessun giro. Collega Strava (Garmin Connect lo alimenta), importa un GPX o inseriscilo a mano.", fontSize = 14.sp) }
            }
            d.rides.forEach { r ->
                AppCard(onClick = { nav.rideId = r.id }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Muted(dateIt(r.date) + " · %02d:%02d".format(r.startHour.toInt(), ((r.startHour % 1) * 60).toInt()), 12)
                        Muted(r.source, 12)
                    }
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        Text("%.0f km".format(r.km), fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFamily = Condensed)
                        Muted("   ${fmtDur(r.durSec)} · +${r.elev} m", 13, Modifier.padding(bottom = 4.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Stat("Watt medi", "%.0f".format(r.avgW), Modifier.weight(1f))
                        Stat("NP", "%.0f".format(r.np), Modifier.weight(1f))
                        Stat("IF", fmt2(r.ifv(d.profile.ftp)), Modifier.weight(1f))
                        Stat("TSS", "%.0f".format(r.tss(d.profile.ftp)), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RideDetail(r: Ride, nav: NavState) {
    val d = Repo.data
    val ftp = d.profile.ftp
    var confirm by remember { mutableStateOf(false) }

    TextButton(onClick = { nav.rideId = null }) { Text("← Torna all'elenco") }
    AppCard {
        Strong("${dateIt(r.date)} · ${fmtDur(r.durSec)} · %.0f km".format(r.km), 15)
        Muted(r.source, 12)
    }
    if (r.chart.size > 1) {
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted("Potenza nel tempo (W)", 12)
                Muted("tratteggio = FTP $ftp W", 12)
            }
            LineChart(listOf(r.chart.map { it.toDouble() }), listOf(MaterialTheme.colorScheme.primary), Modifier.padding(top = 6.dp), hline = ftp.toDouble())
        }
    }
    AppCard {
        Muted("Potenza e carico", 12)
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "Watt medi" to "%.0f W".format(r.avgW), "NP" to "%.0f W".format(r.np),
                "Max" to if (r.maxW > 0) "${r.maxW} W" else "—",
                "IF" to fmt2(r.ifv(ftp)), "VI" to fmt2(r.vi), "TSS" to "%.0f".format(r.tss(ftp)),
                "Lavoro" to "${r.kj.toInt()} kJ", "W/kg medi" to fmt2(r.avgW / d.profile.weight),
                "Dislivello" to "${r.elev} m",
            ),
        )
    }
    val ifv = r.ifv(ftp)
    val kcal = r.kj.toInt()
    val carbBurn = (kcal * (0.3 + 0.6 * ((ifv - 0.5) / 0.6).coerceIn(0.0, 1.0)) / 4).toInt()
    AppCard {
        Muted("Cuore, pedalata ed energia", 12)
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "FC media" to if (r.avgHr > 0) "${r.avgHr} bpm" else "—",
                "FC max" to if (r.maxHr > 0) "${r.maxHr} bpm" else "—",
                "Cadenza" to if (r.cad > 0) "${r.cad} rpm" else "—",
                "EF" to if (r.avgHr > 0) fmt2(r.np / r.avgHr) else "—",
                "Deriva Pw:FC" to (r.drift?.let { "%.1f%%".format(it).replace('.', ',') } ?: "—"),
                "Calorie" to "$kcal kcal",
                "Carbo bruciati" to "~$carbBurn g",
            ),
        )
    }
    if (r.bests.any { it > 0 }) {
        AppCard {
            val est = Metrics.estimatedFtp((r.bests.getOrNull(3) ?: 0).toDouble()).toInt()
            Muted("Migliori sforzi" + if (est > 0) " · FTP stimata $est W" else "", 12)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("5 s", "1 min", "5 min", "20 min").forEachIndexed { i, l ->
                    Stat(l, r.bests.getOrNull(i)?.takeIf { it > 0 }?.let { "$it W" } ?: "—", Modifier.weight(1f))
                }
            }
        }
    }
    if (r.chart.isNotEmpty()) {
        val secs = DoubleArray(6)
        r.chart.forEach { w -> secs[Metrics.zone(w.toDouble(), ftp.toDouble()) - 1] += r.chartStep.toDouble() }
        val total = secs.sum().coerceAtLeast(1.0)
        val names = listOf("Z1 Recupero", "Z2 Fondo", "Z3 Tempo", "Z4 Soglia", "Z5 VO2max", "Z6 Anaerobica")
        AppCard {
            Muted("Tempo nelle zone di potenza", 12)
            names.forEachIndexed { i, n ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.width(104.dp), fontSize = 12.sp)
                    BarLine((secs[i] / total).toFloat(), MaterialTheme.colorScheme.primary, 8.dp, Modifier.weight(1f))
                    Muted(fmtDur(secs[i].toInt()), 12, Modifier.width(66.dp))
                }
            }
        }
    }
    AppCard(accent = true) {
        Accent("NUTRIZIONE DEL GIRO")
        val fuel = Nutrition.rideFuel((r.durSec / 60), ifv >= 0.9, r.tempC ?: 15.0)
        val h = r.durSec / 3600.0
        val goalCarb = (fuel.carbGPerH * h).toInt()
        Text(
            "Consigliati per questo giro: $goalCarb g di carboidrati, ${(fuel.fluidMlPerH * h).toInt()} ml e ${(fuel.sodiumMgPerH * h).toInt()} mg di sodio." +
                if (r.carbIn > 0 || r.fluidIn > 0) " Hai assunto ${r.carbIn} g e ${r.fluidIn} ml." else " Non hai registrato cosa hai assunto.",
            fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
        )
        Muted(
            "Recupero entro 30-60 minuti: ${(d.profile.weight * 1.1).toInt()} g di carboidrati e ${(d.profile.weight * 0.3).toInt()} g di proteine.",
            12, Modifier.padding(top = 6.dp),
        )
        if (r.tempC != null || r.rpe > 0) Muted("Temperatura ${r.tempC?.toInt() ?: "—"} °C · sforzo percepito ${if (r.rpe > 0) "${r.rpe}/10" else "—"}", 12)
        if (r.notes.isNotEmpty()) Muted("Note: ${r.notes}", 12)
    }
    Btn("Elimina giro", primary = false) { confirm = true }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Eliminare il giro?") }, text = { Text("L'azione non si può annullare.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.update { it.copy(rides = it.rides.filter { x -> x.id != r.id }) }
                    nav.rideId = null; confirm = false
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } },
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ManualRide(nav: NavState) {
    val d = Repo.data
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var time by remember { mutableStateOf("08:00") }
    var min by remember { mutableStateOf("75") }
    var km by remember { mutableStateOf("35") }
    var elev by remember { mutableStateOf("300") }
    var ap by remember { mutableStateOf("180") }
    var np by remember { mutableStateOf("") }
    var hr by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fluid by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var rpe by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    TextButton(onClick = { nav.manual = false }) { Text("← Annulla") }
    AppCard {
        Strong("Giro a mano", 15)
        Field("Data (aaaa-mm-gg)", date) { date = it }
        Field("Ora di partenza (hh:mm)", time) { time = it }
        Field("Durata (minuti)", min, true) { min = it }
        Field("Distanza (km)", km, true) { km = it }
        Field("Dislivello (m)", elev, true) { elev = it }
        Field("Watt medi", ap, true) { ap = it }
        Field("NP (vuoto = watt medi × 1,05)", np, true) { np = it }
        Field("FC media (facoltativa)", hr, true) { hr = it }
        Field("Carboidrati assunti (g)", carb, true) { carb = it }
        Field("Liquidi bevuti (ml)", fluid, true) { fluid = it }
        Field("Temperatura (°C)", temp, true) { temp = it }
        Field("Sforzo percepito RPE 1-10", rpe, true) { rpe = it }
        Field("Note", notes) { notes = it }
    }
    val dur = parseD(min) ?: 0.0
    val avg = parseD(ap) ?: 0.0
    val npv = parseD(np) ?: Metrics.npFromAverage(avg)
    val ifv = Metrics.intensityFactor(npv, d.profile.ftp.toDouble())
    AppCard {
        Muted("Calcolato dall'app (FTP ${d.profile.ftp} W)", 12)
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "NP" to "%.0f W".format(npv), "IF" to fmt2(ifv),
                "TSS" to "%.0f".format(Metrics.tss((dur * 60).toInt(), ifv)),
                "Lavoro" to "%.0f kJ".format(Metrics.kilojoules(avg, (dur * 60).toInt())),
                "Calorie" to "%.0f kcal".format(Metrics.kilojoules(avg, (dur * 60).toInt())),
                "VI" to fmt2(Metrics.variabilityIndex(npv, avg)),
            ),
        )
    }
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    Btn("Salva giro") {
        try {
            val day = LocalDate.parse(date.trim())
            val t = LocalTime.parse(time.trim())
            require(dur > 0 && avg > 0) { "Servono durata e watt medi" }
            val ms = day.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Repo.addRide(
                RideFactory.manual(
                    ms, dur.toInt(), parseD(km) ?: 0.0, (parseD(elev) ?: 0.0).toInt(), avg, parseD(np) ?: 0.0,
                    (parseD(hr) ?: 0.0).toInt(), (parseD(carb) ?: 0.0).toInt(), (parseD(fluid) ?: 0.0).toInt(),
                    parseD(temp), (parseD(rpe) ?: 0.0).toInt(), notes,
                ),
            )
            nav.manual = false
        } catch (e: Exception) {
            error = "Controlla i dati: ${e.message}"
        }
    }
    Spacer(Modifier.height(8.dp))
}
