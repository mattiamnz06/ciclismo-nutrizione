package it.mattia.ciclismo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import it.mattia.ciclismo.engine.Foods
import it.mattia.ciclismo.engine.Meal
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
    var edit by remember(date) { mutableStateOf(setOf<String>()) }
    var picker by remember { mutableStateOf<String?>(null) }

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
                "Quantità del pasto", "${(us * 100).toInt()}%",
                { Repo.setScale(key, ((us - 0.1).coerceAtLeast(0.5) * 10).toInt() / 10.0) },
                { Repo.setScale(key, ((us + 0.1).coerceAtMost(1.5) * 10 + 0.5).toInt() / 10.0) },
            )
            val editing = key in edit
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (editing) "Fine modifiche" else "Modifica alimenti", Modifier.weight(1f), primary = false) {
                    edit = if (editing) edit - key else edit + key
                }
                Btn("Aggiungi alimento", Modifier.weight(1f), primary = false) { picker = key }
            }
            if (editing) ItemEditor(v, m)
        }
    }
    val pk = picker
    if (pk != null) {
        FoodPicker("Aggiungi a «${v.meals.firstOrNull { v.key(it) == pk }?.name ?: "pasto"}»", onAdd = { f, g ->
            Repo.addToMeal(pk, f.id, g); picker = null
        }, onClose = { picker = null })
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

/** Grams of every food in the meal: planned foods can be changed or removed, added ones changed or deleted. */
@Composable
private fun ItemEditor(v: DayView, m: Meal) {
    val key = v.key(m)
    Spacer(Modifier.height(6.dp))
    v.plannedItems(m).forEach { it ->
        val g = v.itemGrams(m, it.food.id) ?: it.grams
        if (g > 0) ItemRow(it.food.name, g, it.food.liquid, changed = v.itemGrams(m, it.food.id) != null,
            onSet = { n -> Repo.setItemGrams(key, it.food.id, n) },
            onReset = { Repo.setItemGrams(key, it.food.id, null) })
    }
    v.added(m).forEachIndexed { i, (id, g) ->
        val fd = Foods.get(id)
        ItemRow("+ ${fd.name}", g, fd.liquid, changed = false, onSet = { n -> Repo.setAddedGrams(key, i, n) }, onReset = null)
    }
    val removed = v.plannedItems(m).filter { v.itemGrams(m, it.food.id) == 0 }
    if (removed.isNotEmpty()) {
        Muted("Tolti dal piano", 11, Modifier.padding(top = 6.dp))
        removed.forEach { it ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(it.food.name.substringBefore(" ("), Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = { Repo.setItemGrams(key, it.food.id, null) }) { Text("Rimetti") }
            }
        }
    }
}

@Composable
private fun ItemRow(name: String, g: Int, liquid: Boolean, changed: Boolean, onSet: (Int) -> Unit, onReset: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name.substringBefore(" ("), fontSize = 13.sp, maxLines = 1)
            if (changed && onReset != null) Text("modificato · ripristina", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onReset() })
        }
        FilledTonalIconButton(onClick = { onSet((g - step(g)).coerceAtLeast(5)) }, modifier = Modifier.size(36.dp)) { Text("−") }
        Text("$g ${if (liquid) "ml" else "g"}", Modifier.widthIn(min = 64.dp), fontSize = 13.sp, textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onSet(g + step(g)) }, modifier = Modifier.size(36.dp)) { Text("+") }
        TextButton(onClick = { onSet(0) }) { Text("Togli") }
    }
}
