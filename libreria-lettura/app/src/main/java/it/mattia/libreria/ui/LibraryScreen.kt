package it.mattia.libreria.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.libreria.data.Book
import it.mattia.libreria.data.Status
import java.time.LocalDate

fun formatDate(d: String?): String {
    if (d.isNullOrBlank()) return "—"
    val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    return try {
        val x = LocalDate.parse(d)
        "${x.dayOfMonth} ${months[x.monthValue - 1]} ${x.year}"
    } catch (e: Exception) {
        d
    }
}

@Composable
fun glassColor() = MaterialTheme.colorScheme.surface.let { if (LocalGlass.current) it.copy(alpha = .72f) else it }

@Composable
fun Header(title: String, subtitle: String, onSettings: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, lineHeight = 36.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (onSettings != null) {
            Surface(shape = CircleShape, color = glassColor(), shadowElevation = 2.dp, modifier = Modifier.size(42.dp)) {
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") }
            }
        }
    }
}

@Composable
fun LibraryScreen(books: List<Book>, filter: Status, onFilter: (Status) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val year = LocalDate.now().year
    val reading = books.count { it.status == Status.READING }
    val doneYear = books.count { it.status == Status.DONE && it.finished?.startsWith(year.toString()) == true }
    val sub = "$reading in lettura · $doneYear " + (if (doneYear == 1) "libro letto nel $year" else "libri letti nel $year")

    val order = listOf(Status.READING, Status.TBR, Status.DONE, Status.PAUSED)
    val items = books.filter { it.status == filter }.let { l ->
        if (filter == Status.DONE) l.sortedByDescending { it.finished ?: "" } else l.sortedByDescending { it.upd }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Libreria", sub, onSettings)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(order) { s ->
                val n = books.count { it.status == s }
                FilterChip(selected = filter == s, onClick = { onFilter(s) }, label = { Text("${s.label}  $n") })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            val (t, d) = when (filter) {
                Status.READING -> "Nessuna lettura in corso" to "Scegli un libro dalla lista \"Da leggere\" o aggiungine uno nuovo."
                Status.TBR -> "La lista è vuota" to "Aggiungi i libri che vuoi leggere prossimamente."
                Status.DONE -> "Ancora nessun libro finito" to "Quando arrivi all'ultima pagina, il libro passa qui."
                Status.PAUSED -> "Nessun libro in pausa" to "Qui trovi i libri che hai interrotto."
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(d, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { b -> BookCard(b) { onOpen(b.id) } }
            }
        }
    }
}

@Composable
fun BookCard(b: Book, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = glassColor(), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(b, Modifier.width(64.dp).height(96.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(b.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp, maxLines = 2)
                val sub = when (b.status) {
                    Status.DONE -> "${b.pages} pagine · finito il ${formatDate(b.finished)}"
                    Status.TBR -> "${b.pages} pagine"
                    else -> "${b.page} di ${b.pages} pagine"
                }
                Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                Box(Modifier.padding(top = 8.dp)) { StatusTag(b.status, b.rating) }
            }
        }
    }
}
