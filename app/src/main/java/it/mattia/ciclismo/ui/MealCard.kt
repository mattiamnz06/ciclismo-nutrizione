package it.mattia.ciclismo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Closed: time, name, one-line summary, kcal. Open: every food with grams and macros, totals,
 * why, how, what to drink and alternatives (report section 6).
 */
@Composable
fun MealCard(
    m: it.mattia.ciclismo.engine.Meal,
    expanded: Boolean,
    onToggle: () -> Unit,
    dayKcal: Int,
    weight: Double,
    status: String? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(accent = status == "si", onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.time, Modifier.width(48.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Strong(m.name, 14)
                Muted(m.summary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Strong("${m.kcal} kcal", 14)
                val tag = when (status) {
                    "si" -> "Mangiato"
                    "no" -> "Saltato"
                    else -> m.tag
                }
                if (tag.isNotEmpty()) Muted(tag, 11)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Muted("Alimento", 11, Modifier.weight(2.6f))
                listOf("Qtà", "kcal", "C", "P", "G").forEach { Muted(it, 11, Modifier.weight(1.1f).wrapAlign()) }
            }
            m.items.forEach { it ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(it.food.name.substringBefore(" (") , Modifier.weight(2.6f), fontSize = 12.sp)
                    listOf(it.qty(), "${it.kcal.toInt()}", "${it.c.toInt()}", "${it.p.toInt()}", "${it.f.toInt()}").forEach { v ->
                        Text(v, Modifier.weight(1.1f).wrapAlign(), fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Totale", Modifier.weight(3.7f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                listOf("${m.kcal}", "${m.c}", "${m.p}", "${m.f}").forEach { v ->
                    Text(v, Modifier.weight(1.1f).wrapAlign(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            val share = if (dayKcal > 0) m.kcal * 100 / dayKcal else 0
            Muted("$share% delle calorie del giorno · carbo ${fmt1(m.c / weight)} g/kg", 11, Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Accent("PERCHÉ")
            Text(m.why, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("Come: ${m.how}", fontSize = 12.sp)
            Text("Bevi: ${m.drink}", fontSize = 12.sp)
            Text("Alternative: ${m.alt}", fontSize = 12.sp)
            if (actions != null) {
                Spacer(Modifier.height(8.dp))
                actions()
            }
        }
    }
}

private fun Modifier.wrapAlign(): Modifier = this
