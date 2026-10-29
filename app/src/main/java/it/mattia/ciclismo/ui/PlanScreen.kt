package it.mattia.ciclismo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.data.PlannedRide
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.data.RideTypes
import it.mattia.ciclismo.sync.Notify
import java.time.LocalDate
import kotlin.math.ceil

private val DURS = listOf(60, 90, 120, 180, 240, 300)

@Composable
fun PlanScreen(nav: NavState) {
    val d = Repo.data
    val today = LocalDate.now()
    var carico by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("Prossima uscita", Modifier.weight(1f), primary = !carico) { carico = false }
        Btn("Carico carbo", Modifier.weight(1f), primary = carico) { carico = true }
    }
    Spacer(Modifier.height(8.dp))
    if (carico) LoadView(nav) else NextRide(nav, today)
}

@Composable
private fun NextRide(nav: NavState, today: LocalDate) {
    val d = Repo.data
    val ctx = LocalContext.current
    var tipo by remember { mutableIntStateOf(2) }
    var durIdx by remember { mutableIntStateOf(3) }
    var hour by remember { mutableIntStateOf(8) }
    val first = nav.planDate ?: today.plusDays(1)
    var date by remember { mutableStateOf(if (first.isBefore(today)) today else first) }
    LaunchedEffect(Unit) { nav.planDate = null }

    AppCard {
        Muted("Giorno")
        Chips(
            (0..6).map { dateIt(today.plusDays(it.toLong())) }, (date.toEpochDay() - today.toEpochDay()).toInt().coerceIn(0, 6),
        ) { date = today.plusDays(it.toLong()) }
        Spacer(Modifier.height(8.dp))
        Muted("Tipo di uscita")
        Chips(RideTypes.all.map { it.label }, tipo) { tipo = it }
        Spacer(Modifier.height(8.dp))
        Muted("Durata")
        Chips(DURS.map { if (it >= 60) "${it / 60} h${if (it % 60 > 0) " ${it % 60}" else ""}" else "$it min" }, durIdx) { durIdx = it }
        Stepper("Partenza", "%02d:00".format(hour), { hour = (hour - 1).coerceAtLeast(5) }, { hour = (hour + 1).coerceAtMost(21) })
    }

    val pr = PlannedRide(RideTypes.all[tipo].id, DURS[durIdx], hour)
    val sim = d.copy(planned = d.planned + (date.toString() to pr))
    val v = DayView(sim, date)
    val fuel = it.mattia.ciclismo.engine.Planner.fuelFor(v.info, sim)!!
    val h = v.info.hours

    AppCard {
        Muted("Carico previsto (partenza %02d:00, FTP ${d.profile.ftp} W)".format(hour))
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "Durata" to fmtDur(pr.durMin * 60), "Watt target" to "%.0f W".format(v.info.np),
                "IF" to fmt2(v.info.ifv), "TSS" to "%.0f".format(v.info.tss), "Lavoro" to "${v.info.kj.toInt()} kJ",
                "Giornata" to v.info.type.label,
            ),
        )
    }
    AppCard {
        Muted("In sella")
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "Carbo" to "${fuel.carbGPerH} g/h", "Liquidi" to "${fuel.fluidMlPerH} ml/h", "Sodio" to "${fuel.sodiumMgPerH} mg/h",
                "Carbo totali" to "${(fuel.carbGPerH * h).toInt()} g", "Liquidi totali" to "${(fuel.fluidMlPerH * h).toInt()} ml",
                "Sodio totale" to "${(fuel.sodiumMgPerH * h).toInt()} mg",
            ),
        )
        val sup = d.profile.supplements.firstOrNull { it.carbG > 0 }
        if (sup != null && fuel.carbGPerH > 0) {
            Text("Con ${sup.name}: circa ${ceil(fuel.carbGPerH * h / sup.carbG).toInt()} unità in totale, una ogni ${(60 * sup.carbG / fuel.carbGPerH.toDouble()).toInt()} minuti.", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (d.profile.caffeine && v.info.ifv >= 0.9) {
            Text("Caffeina: ${(d.profile.weight * 3).toInt()} mg (3 mg/kg) 45-60 minuti prima.", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Muted("Promemoria per bere ogni 20 minuti.", 12, Modifier.padding(top = 4.dp))
    }
    Strong("Timeline della giornata", 14, Modifier.padding(vertical = 6.dp))
    val rideStart = (v.info.startHour * 60).toInt()
    val events = v.meals.map { Triple(it.timeMin, it.name, "${it.kcal} kcal · ${it.summary}") } +
        listOf(
            Triple(rideStart, "Partenza", "${v.info.title}: ${fuel.carbGPerH} g/h di carbo, ${fuel.fluidMlPerH} ml/h"),
            Triple(rideStart + pr.durMin, "Arrivo", "Recupero entro 30-60 minuti"),
        )
    events.sortedBy { it.first }.forEach { (t, n, x) ->
        AppCard {
            Row(Modifier.fillMaxWidth()) {
                Text("%02d:%02d".format(t / 60, t % 60), Modifier.width(52.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Column {
                    Strong(n, 14)
                    Muted(x)
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Btn("Pianifica per ${dateIt(date)}") { Repo.plan(date, pr); Notify.stopDrinkReminders(ctx); Notify.scheduleAll(ctx) }
    if (d.planned.containsKey(date.toString())) {
        Spacer(Modifier.height(6.dp))
        Btn("Rimuovi la pianificazione", primary = false) { Repo.plan(date, null); Notify.scheduleAll(ctx) }
    }
    Spacer(Modifier.height(6.dp))
    Btn("Avvia promemoria per bere", primary = false) { Notify.startDrinkReminders(ctx, pr.durMin, fuel.fluidMlPerH, Repo.data.profile.notifDrinkEvery) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LoadView(nav: NavState) {
    val d = Repo.data
    val today = LocalDate.now()
    val eventDate = (0L..60L).map { today.plusDays(it) }.firstOrNull { (d.planned[it.toString()]?.durMin ?: 0) >= 180 }

    if (eventDate == null) {
        AppCard {
            Strong("Nessun evento lungo in programma", 15)
            Muted("Il carico di carboidrati serve per uscite o gare oltre le 3 ore: pianifica l'evento e l'app prepara i 3 giorni prima.", 13, Modifier.padding(top = 4.dp))
        }
        Btn("Pianifica un'uscita lunga sabato") {
            var sat = today
            while (sat.dayOfWeek.value != 6 || sat == today) sat = sat.plusDays(1)
            Repo.plan(sat, PlannedRide("fondo", 300, 8))
        }
        return
    }
    val ev = DayView(d, eventDate)
    val daysTo = (eventDate.toEpochDay() - today.toEpochDay()).toInt()
    AppCard {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Muted("Evento")
                Strong("${ev.info.title} · ${dateIt(eventDate)}", 15)
                Muted("TSS ${"%.0f".format(ev.info.tss)} · ${ev.info.kj.toInt()} kJ")
            }
            Column {
                Text("$daysTo", fontSize = 38.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed, color = MaterialTheme.colorScheme.primary)
                Muted("giorni", 11)
            }
        }
    }
    val w = d.profile.weight
    for (k in 3 downTo 1) {
        val day = eventDate.minusDays(k.toLong())
        val dv = DayView(d, day)
        val gkg = dv.plan.carbG / w
        AppCard(onClick = { nav.diaryDate = day; nav.tab = 4 }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Strong("Giorno -$k · ${dateIt(day)}", 13)
                Text("${dv.plan.carbG} g · ${fmt1(gkg)} g/kg", color = Carb, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            BarLine((gkg / 10).toFloat(), Carb, modifier = Modifier.padding(top = 8.dp))
            Muted("Poche fibre, niente cibi nuovi. ${dv.meals.size} pasti con grammi precisi.", 12, Modifier.padding(top = 8.dp))
        }
    }
    AppCard(onClick = { nav.diaryDate = eventDate; nav.tab = 4 }) {
        Strong("Giorno X · ${dateIt(eventDate)}", 13)
        Muted("Colazione con 2 g/kg di carboidrati (${(w * 2).toInt()} g), poi 60-90 g/h in sella.", 12, Modifier.padding(top = 4.dp))
    }
    Muted("Tocca un giorno per vedere i pasti con i grammi. Il peso può salire di 1-2 kg per l'acqua legata al glicogeno: è normale.", 12, Modifier.padding(vertical = 8.dp))
}
