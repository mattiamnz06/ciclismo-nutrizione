package it.mattia.ciclismo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.engine.Metrics
import it.mattia.ciclismo.engine.Planner
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(nav: NavState) {
    val d = Repo.data
    val today = LocalDate.now()
    val v = DayView(d, today)
    val form = Planner.form(d, today)
    val hasHistory = d.rides.isNotEmpty()

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Forma (modello PMC)", 12)
            if (hasHistory) {
                val (label, col) = when {
                    form.tsb > 5 -> "Fresco" to Color(0xFF6AB7FF)
                    form.tsb > -10 -> "Giornata media" to Color(0xFFE8D36A)
                    form.tsb > -30 -> "In costruzione" to Color(0xFFFF8A5C)
                    else -> "Affaticato" to Color(0xFFFF6B6B)
                }
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = col)
            }
        }
        if (hasHistory) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Fitness CTL", "%.0f".format(form.ctl), Modifier.weight(1f))
                Stat("Fatica ATL", "%.0f".format(form.atl), Modifier.weight(1f))
                Stat("Freschezza TSB", "%.0f".format(form.tsb), Modifier.weight(1f))
            }
            if (form.tsb < -30) Text("TSB sotto -30: affaticamento. Ti consiglio un giorno di riposo.", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Muted("Collega Strava o aggiungi un giro a mano in «Giri» per vedere la forma.", 12, Modifier.padding(top = 6.dp))
        }
    }

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Calorie · giornata ${v.info.type.label.lowercase()}", 13)
            Muted("piano ${v.plan.kcal} kcal", 13)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${v.eatenKcal}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Muted("  mangiate · ne restano ${(v.plan.kcal - v.eatenKcal).coerceAtLeast(0)}", 13, Modifier.padding(bottom = 6.dp))
        }
        BarLine(v.eatenKcal / v.plan.kcal.toFloat(), MaterialTheme.colorScheme.primary, 8.dp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Metabolismo × 1,4 = ${v.plan.baseKcal}")
            Muted("Giro +${v.info.kj.toInt()}")
        }
    }

    AppCard {
        listOf(
            Triple("Carboidrati", v.eatenC to v.plan.carbG, Carb),
            Triple("Proteine", v.eatenP to v.plan.proteinG, Protein),
            Triple("Grassi", v.eatenF to v.plan.fatG, Fat),
        ).forEach { (n, pair, col) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(n, fontSize = 13.sp)
                Muted("${pair.first} / ${pair.second} g", 13)
            }
            BarLine(pair.first / pair.second.coerceAtLeast(1).toFloat(), col, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
    }

    AppCard {
        val i = v.info
        if (i.hasRide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val h = i.startHour.toInt()
                Muted(if (i.done) "Giro di oggi (fatto)" else "Giro di oggi · %02d:%02d".format(h, ((i.startHour - h) * 60).toInt()), 12)
                Muted("TSS ${"%.0f".format(i.tss)}", 12)
            }
            Strong(i.title, 15, Modifier.padding(top = 4.dp))
            val fuel = Planner.fuelFor(i, d)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Stat("Durata", fmtDur(i.durMin * 60), Modifier.weight(1f))
                Stat("IF", fmt2(i.ifv), Modifier.weight(1f))
                Stat("Lavoro", "${i.kj.toInt()} kJ", Modifier.weight(1f))
                Stat("Carbo", "${fuel?.carbGPerH ?: 0} g/h", Modifier.weight(1f))
            }
        } else {
            Strong("Nessun giro oggi: ${v.info.type.label.lowercase()}", 15)
            Muted(typeNote(v.info.type), 12, Modifier.padding(top = 4.dp))
            Btn("Pianifica un giro", Modifier.fillMaxWidth().padding(top = 8.dp), primary = false) { nav.tab = 3 }
        }
    }

    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val next = v.meals.firstOrNull { v.status(it) == null && it.timeMin >= nowMin - 45 } ?: v.meals.firstOrNull { v.status(it) == null }
    if (next != null) {
        val s = v.shown(next)
        AppCard(accent = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Accent("PROSSIMO PASTO · ${next.name.uppercase()}")
                Accent(next.time)
            }
            Text(s.items.joinToString(" + ") { "${it.qty()} ${it.food.name.substringBefore(" (").lowercase()}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Muted("${s.kcal} kcal · carbo ${s.c} g · proteine ${s.p} g · grassi ${s.f} g", 12, Modifier.padding(top = 6.dp))
            Btn("Vedi grammi e perché", Modifier.fillMaxWidth().padding(top = 10.dp)) {
                nav.diaryDate = today; nav.openMeal = next.id; nav.tab = 4
            }
        }
    }

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Idratazione", fontSize = 13.sp)
            Muted("%.2f / %.2f L".format(v.water / 1000.0, v.waterGoal / 1000.0).replace('.', ','), 13)
        }
        BarLine(v.water / v.waterGoal.toFloat(), Protein, modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("+250 ml", Modifier.weight(1f), primary = false) { Repo.addWater(today, 250) }
            Btn("+500 ml", Modifier.weight(1f), primary = false) { Repo.addWater(today, 500) }
        }
    }

    if (d.sleepHours > 0) {
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sonno ultima notte", fontSize = 13.sp)
                Muted("%.1f h".format(d.sleepHours).replace('.', ','), 13)
            }
            if (d.sleepHours < 7) Muted("Meno di 7 ore: il recupero è più lento, privilegia proteine e liquidi.", 12, Modifier.padding(top = 4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
}
