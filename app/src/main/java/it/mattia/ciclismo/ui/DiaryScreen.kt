package it.mattia.ciclismo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.data.Repo

@Composable
fun DiaryScreen(nav: NavState) {
    val d = Repo.data
    val date = nav.diaryDate
    val v = DayView(d, date)
    var open by remember(date) { mutableStateOf(setOfNotNull(nav.openMeal?.let { "$date|$it" })) }
    LaunchedEffect(nav.openMeal) { nav.openMeal = null }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn("←", Modifier.width(64.dp), primary = false) { nav.diaryDate = date.minusDays(1) }
        Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) { Strong(dateIt(date), 15) }
        Btn("→", Modifier.width(64.dp), primary = false) { nav.diaryDate = date.plusDays(1) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Muted("Tocca un pasto per grammi, perché e alternative")
        Muted("${v.eatenMeals.size} di ${v.meals.size}")
    }
    v.meals.forEach { m ->
        val key = v.key(m)
        MealCard(
            m = v.shown(m), expanded = key in open,
            onToggle = { open = if (key in open) open - key else open + key },
            dayKcal = v.plan.kcal, weight = d.profile.weight, status = v.status(m),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Mangiato", Modifier.weight(1f), primary = v.status(m) != "si") {
                    Repo.setEaten(key, if (v.status(m) == "si") null else "si")
                }
                Btn("Saltato", Modifier.weight(1f), primary = false) {
                    Repo.setEaten(key, if (v.status(m) == "no") null else "no")
                }
            }
            Spacer(Modifier.height(6.dp))
            val us = v.userScale(m)
            Stepper(
                "Quantità", "${(us * 100).toInt()}%",
                { Repo.setScale(key, ((us - 0.1).coerceAtLeast(0.5) * 10).toInt() / 10.0) },
                { Repo.setScale(key, ((us + 0.1).coerceAtMost(1.5) * 10 + 0.5).toInt() / 10.0) },
            )
        }
    }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Micronutrienti di oggi", fontSize = 13.sp)
            Muted("mangiato · fine giornata", 12)
        }
        v.micros.forEach { (n, eaten, full) ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(n, fontSize = 12.sp)
                Muted("$eaten% · $full%", 12)
            }
            BarLine(eaten / 100f, androidx.compose.material3.MaterialTheme.colorScheme.primary, 5.dp, Modifier.padding(top = 4.dp))
        }
    }
    AppCard(accent = true) {
        Accent("CORREZIONE AUTOMATICA")
        Text(v.correctionText, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
    Spacer(Modifier.height(8.dp))
}
