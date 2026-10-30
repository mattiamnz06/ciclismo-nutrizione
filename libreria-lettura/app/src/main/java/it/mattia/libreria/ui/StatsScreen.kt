package it.mattia.libreria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.libreria.data.Book
import it.mattia.libreria.data.Status
import java.time.LocalDate
import java.util.Locale

private val MESI = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")

@Composable
fun StatsScreen(books: List<Book>) {
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    val done = books.filter { it.status == Status.DONE && it.finished?.startsWith(year.toString()) == true }
    val months = IntArray(12)
    done.forEach { b -> b.finished?.substring(5, 7)?.toIntOrNull()?.let { m -> if (m in 1..12) months[m - 1]++ } }
    val pages = done.sumOf { it.pages }
    val rated = done.filter { it.rating > 0 }
    val avg = if (rated.isEmpty()) "—" else String.format(Locale.ITALIAN, "%.1f", rated.sumOf { it.rating }.toDouble() / rated.size)
    val max = maxOf(3, months.max())
    val curMonth = if (year == LocalDate.now().year) LocalDate.now().monthValue - 1 else -1

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Header("Statistiche", "I libri che hai finito, mese per mese", null)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { year-- }) { Text("‹", fontSize = 28.sp) }
                Text("$year", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = { year++ }) { Text("›", fontSize = 28.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("${done.size}", "libri letti", Modifier.weight(1f))
                Kpi(String.format(Locale.ITALIAN, "%,d", pages), "pagine", Modifier.weight(1f))
                Kpi(avg, "voto medio", Modifier.weight(1f))
            }
            Surface(shape = RoundedCornerShape(24.dp), color = glassColor(), shadowElevation = 2.dp) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
                    Text("Libri letti per mese", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Conta la data in cui hai finito il libro.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                        for (i in 0 until 12) {
                            val v = months[i]
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                if (v > 0) Text("$v", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                                Box(
                                    Modifier.width(18.dp).height(if (v > 0) 128.dp * (v / max.toFloat()) else 4.dp)
                                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                        .background(if (v > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in 0 until 12) {
                            Text(
                                MESI[i], modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp,
                                color = if (i == curMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (i == curMonth) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = glassColor(), shadowElevation = 2.dp) {
                Column(Modifier.padding(18.dp)) {
                    Text("Widget per la home", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Tieni premuto sulla schermata home, scegli Widget e poi \"Libro in lettura\". Mostra il libro che stai leggendo con la percentuale, dipinto a mano con i colori del tuo sfondo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = glassColor(), shadowElevation = 2.dp, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, maxLines = 1)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
