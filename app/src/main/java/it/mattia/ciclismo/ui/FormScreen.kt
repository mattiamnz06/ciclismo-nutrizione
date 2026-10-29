package it.mattia.ciclismo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.engine.Metrics
import it.mattia.ciclismo.engine.Nutrition
import it.mattia.ciclismo.engine.Planner
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun FormScreen(nav: NavState) {
    val d = Repo.data
    val p = d.profile
    val today = LocalDate.now()
    val f = Planner.form(d, today)
    val (ctl, atl) = Planner.formSeries(d, today, 42)

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Performance Management Chart", 12)
            Muted("42 giorni", 12)
        }
        if (d.rides.isEmpty()) {
            Text("Nessun dato: importa i giri da Strava o inseriscili a mano.", fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            LineChart(listOf(ctl, atl), listOf(MaterialTheme.colorScheme.primary, Carb), Modifier.padding(top = 8.dp), height = 140.dp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● CTL fitness", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text("● ATL fatica", color = Carb, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Stat("CTL", "%.0f".format(f.ctl), Modifier.weight(1f))
            Stat("ATL", "%.0f".format(f.atl), Modifier.weight(1f))
            Stat("TSB", "%.0f".format(f.tsb), Modifier.weight(1f))
        }
        if (d.rides.isNotEmpty() && f.tsb < -30) {
            Text("TSB sotto -30: rischio di affaticamento. Ti propongo un giorno di riposo.", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    val lean = p.weight * (1 - (p.bodyFat ?: 0.0) / 100)
    AppCard {
        Muted("Potenza e corpo", 12)
        Spacer(Modifier.height(8.dp))
        StatGrid(
            listOf(
                "FTP" to "${p.ftp} W",
                "W/kg" to fmt2(Metrics.wattsPerKg(p.ftp.toDouble(), p.weight)),
                "Peso" to "${fmt1(p.weight)} kg",
                "% grasso" to (p.bodyFat?.let { "${fmt1(it)}%" } ?: "—"),
                "Massa magra" to if (p.bodyFat != null) "${fmt1(lean)} kg" else "—",
                "Metabolismo" to "${Nutrition.bmr(p.body()).toInt()} kcal",
                "FC a riposo" to "${p.restHr} bpm",
                "Sonno" to if (d.sleepHours > 0) "${fmt1(d.sleepHours)} h" else "—",
            ),
        )
    }

    val suggested = Planner.suggestedFtp(d)
    if (suggested != null && suggested >= p.ftp + 3) {
        AppCard(accent = true) {
            Accent("PROPOSTA FTP")
            Text("Il tuo miglior sforzo di 20 minuti suggerisce un'FTP di $suggested W (95% del 20'), oggi hai ${p.ftp} W. Aggiornarla ricalcola IF, TSS e zone.", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Btn("Aggiorna FTP a $suggested W", Modifier.fillMaxWidth().padding(top = 10.dp)) { Repo.setProfile { it.copy(ftp = suggested) } }
        }
    }

    if (d.weights.size >= 2) {
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted("Storico peso", 12)
                Muted("${d.weights.size} misure", 12)
            }
            LineChart(listOf(d.weights.map { it.second }), listOf(Protein), Modifier.padding(top = 8.dp), height = 90.dp)
        }
    }

    val gw = p.goalWeight
    if (gw != null) {
        AppCard {
            Muted("Obiettivo: ${p.goal.label}", 12)
            val diff = p.weight - gw
            Text(
                "Peso obiettivo ${fmt1(gw)} kg · " + if (kotlin.math.abs(diff) < 0.3) "raggiunto" else "mancano ${fmt1(kotlin.math.abs(diff))} kg",
                fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
            )
            val gd = runCatching { LocalDate.parse(p.goalDate) }.getOrNull()
            if (gd != null && gd.isAfter(today)) {
                val weeks = ChronoUnit.DAYS.between(today, gd) / 7.0
                if (weeks > 0.5) Muted("Ritmo necessario: ${fmt2(kotlin.math.abs(diff) / weeks)} kg a settimana (consigliato sotto 0,7).", 12, Modifier.padding(top = 4.dp))
            }
        }
    }
    Btn("Aggiungi una pesata", primary = false) { nav.section = "corpo"; nav.settings = true }
    Spacer(Modifier.height(8.dp))
}
