package it.mattia.ciclismo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.engine.DayType
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekScreen(nav: NavState) {
    val d = Repo.data
    val today = LocalDate.now()
    var offset by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(today) }
    var open by remember { mutableStateOf(setOf<String>()) }
    val start = today.with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
    val days = (0..6).map { DayView(d, start.plusDays(it.toLong())) }
    val sel = days.firstOrNull { it.date == selected } ?: days[0]

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("←", Modifier.width(64.dp), primary = false) { offset--; selected = start.minusWeeks(1) }
        Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            Strong("${dateIt(start)} - ${dateIt(start.plusDays(6))}", 14)
        }
        Btn("→", Modifier.width(64.dp), primary = false) { offset++; selected = start.plusWeeks(1) }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { v ->
            val isSel = v.date == sel.date
            val col = typeColor(v.info.type)
            Surface(
                onClick = { selected = v.date },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else col.copy(alpha = 0.5f)),
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Muted(dayShort(v.date), 11)
                    Text("${v.date.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.height(34.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val h = (v.info.tss / 200.0 * 34).coerceIn(if (v.info.hasRide) 4.0 else 2.0, 34.0)
                        Box(Modifier.width(14.dp).height(h.dp).background0(col))
                    }
                    Muted(if (v.info.hasRide) "%.0f".format(v.info.tss) else "—", 10)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DayType.entries.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background0(typeColor(t)))
                Muted("  " + t.label, 11)
            }
        }
    }

    AppCard {
        Row(Modifier.fillMaxWidth()) {
            Stat("TSS settimana", "%.0f".format(days.sumOf { it.info.tss }), Modifier.weight(1f))
            Stat("Ore in sella", "%.1f".format(days.sumOf { it.info.hours }).replace('.', ','), Modifier.weight(1f))
            Stat("Media kcal/giorno", "${days.sumOf { it.plan.kcal } / 7}", Modifier.weight(1f))
        }
    }

    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Strong(dateIt(sel.date), 15)
                val st = when {
                    sel.info.done -> "Giro fatto"
                    sel.info.hasRide -> "Giro pianificato"
                    else -> "Nessun giro"
                }
                Muted(st, 12)
            }
            Text(sel.info.type.label, color = typeColor(sel.info.type), fontWeight = FontWeight.SemiBold)
        }
        if (sel.info.hasRide) {
            Spacer(Modifier.height(8.dp))
            Strong(sel.info.title, 14)
            Muted("TSS ${"%.0f".format(sel.info.tss)} · ${sel.info.kj.toInt()} kJ", 12)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Calorie", "${sel.plan.kcal}", Modifier.weight(1f))
            Stat("Carbo", "${sel.plan.carbG} g", Modifier.weight(1f), Carb)
            Stat("Proteine", "${sel.plan.proteinG} g", Modifier.weight(1f), Protein)
            Stat("Grassi", "${sel.plan.fatG} g", Modifier.weight(1f), Fat)
        }
        val w = d.profile.weight
        Muted("Carbo ${fmt1(sel.plan.carbG / w)} g/kg · proteine ${fmt1(sel.plan.proteinG / w)} g/kg · peso ${fmt1(w)} kg", 12, Modifier.padding(top = 6.dp))
    }
    AppCard(accent = true) {
        Accent("NOTA DEL GIORNO")
        Text(typeNote(sel.info.type), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        if (sel.info.type == DayType.RECOVERY && !sel.info.hasRide) Muted("Ieri il carico era alto (TSS sopra 150): oggi recupero attivo o riposo.", 12, Modifier.padding(top = 4.dp))
    }
    if (sel.date.isBefore(today)) {
        val eaten = sel.eatenKcal
        Muted(
            if (sel.meals.any { sel.status(it) != null }) "Registrati $eaten kcal su ${sel.plannedKcal} previste" else "Per questo giorno non hai registrato i pasti",
            12, Modifier.padding(vertical = 4.dp),
        )
    } else {
        Muted("Tocca un pasto per grammi, perché e alternative", 12, Modifier.padding(vertical = 4.dp))
    }
    sel.meals.forEach { m ->
        val k = sel.key(m)
        MealCard(sel.shown(m), k in open, { open = if (k in open) open - k else open + k }, sel.plan.kcal, d.profile.weight, sel.status(m))
    }
    Btn("Pianifica un giro per questo giorno", primary = false) { nav.planDate = sel.date; nav.tab = 3 }
    Spacer(Modifier.height(8.dp))
}

private fun Modifier.background0(c: Color): Modifier =
    this.then(Modifier.background(c, RoundedCornerShape(3.dp)))
