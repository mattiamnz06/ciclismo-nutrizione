package it.mattia.ciclismo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.engine.DayType
import it.mattia.ciclismo.engine.Item
import it.mattia.ciclismo.engine.Meal
import java.time.LocalDate
import kotlin.math.roundToInt

private val DAYS = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
private val MONTHS = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")

fun dayShort(d: LocalDate) = DAYS[d.dayOfWeek.value - 1]
fun dateIt(d: LocalDate) = "${dayShort(d)} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
fun fmtDur(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "$h h ${"%02d".format(m)}" else "$m min"
}
fun fmt1(v: Double) = "%.1f".format(v).replace('.', ',')
fun fmt2(v: Double) = "%.2f".format(v).replace('.', ',')
fun parseD(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun Meal.scaled(s: Double): Meal =
    if (s == 1.0) this else Meal(id, timeMin, name, kind, items.map { Item(it.food, (it.grams * s).roundToInt()) }, why, how, drink, alt, tag)

fun typeColor(t: DayType): Color = when (t) {
    DayType.REST -> Color(0xFF8793A0)
    DayType.RECOVERY -> Color(0xFF4FB3A9)
    DayType.MEDIUM -> Color(0xFF6AB7FF)
    DayType.HARD -> Color(0xFFFF8A5C)
    DayType.LOAD -> Color(0xFFB38CFF)
}

fun typeNote(t: DayType) = when (t) {
    DayType.REST -> "Calorie più basse, proteine alte, magnesio e ferro (mandorle, spinaci, legumi), almeno 2,5 L di liquidi."
    DayType.RECOVERY -> "Più liquidi, frutta e verdura ricche di antiossidanti."
    DayType.MEDIUM -> "Pasto pre-giro con 1 g/kg di carboidrati, recupero entro 60 minuti."
    DayType.HARD -> "60-90 g/h in sella, recupero subito, il giorno dopo recupero attivo."
    DayType.LOAD -> "Poche fibre, niente cibi nuovi."
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = if (accent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val m = modifier.fillMaxWidth().padding(vertical = 5.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(14.dp), content = content)
        }
    } else {
        Card(modifier = m, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
fun Muted(text: String, size: Int = 12, modifier: Modifier = Modifier) =
    Text(text, fontSize = size.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
fun Strong(text: String, size: Int = 15, modifier: Modifier = Modifier, color: Color = Color.Unspecified) =
    Text(text, fontSize = size.sp, fontWeight = FontWeight.SemiBold, modifier = modifier, color = color)

@Composable
fun Accent(text: String) =
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Muted(label, 11)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = Condensed, color = valueColor)
    }
}

@Composable
fun StatGrid(items: List<Pair<String, String>>, cols: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { Stat(it.first, it.second, Modifier.weight(1f)) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun BarLine(frac: Float, color: Color, height: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(height)
            .then(Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(Color.Gray.copy(alpha = 0.25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            val w = size.width * frac.coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(color, size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        }
    }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier.fillMaxWidth(), primary: Boolean = true, onClick: () -> Unit) {
    if (primary) Button(onClick, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text(text) }
    else FilledTonalButton(onClick, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text(text) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o -> FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(o) }) }
    }
}

@Composable
fun Stepper(label: String, value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalIconButton(onClick = onDec, modifier = Modifier.size(44.dp)) { Text("−", fontSize = 20.sp) }
        Text(value, Modifier.widthIn(min = 76.dp), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = onInc, modifier = Modifier.size(44.dp)) { Text("+", fontSize = 20.sp) }
    }
}

/** Text field that keeps its own text and reports every change. */
@Composable
fun Field(label: String, value: String, numeric: Boolean = false, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var t by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = t, onValueChange = { t = it; onChange(it) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
fun SwitchRow(title: String, sub: String = "", checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub.isNotEmpty()) Muted(sub)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Simple multi-series line chart; [hline] draws a dashed horizontal reference. */
@Composable
fun LineChart(series: List<List<Double>>, colors: List<Color>, modifier: Modifier = Modifier, hline: Double? = null, height: Dp = 110.dp) {
    val ref = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        val all = series.flatten() + listOfNotNull(hline)
        if (all.isEmpty()) return@Canvas
        val lo = minOf(0.0, all.min())
        val hi = (all.max() * 1.08).coerceAtLeast(lo + 1)
        fun y(v: Double) = (size.height - ((v - lo) / (hi - lo) * size.height)).toFloat()
        if (hline != null) {
            drawLine(ref, Offset(0f, y(hline)), Offset(size.width, y(hline)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
        }
        series.forEachIndexed { si, s ->
            if (s.size < 2) return@forEachIndexed
            val path = Path()
            s.forEachIndexed { i, v ->
                val x = size.width * i / (s.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, colors[si % colors.size], style = Stroke(width = 4f))
        }
    }
}
