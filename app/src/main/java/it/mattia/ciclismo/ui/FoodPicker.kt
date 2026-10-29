package it.mattia.ciclismo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.engine.Food
import it.mattia.ciclismo.engine.Foods
import it.mattia.ciclismo.sync.OpenFoodFacts
import kotlinx.coroutines.launch

/** Search the food table, scan a barcode or type a new food, then choose the grams to add. */
@Composable
fun FoodPicker(title: String, onAdd: (Food, Int) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Food?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun lookup(c: String) {
        val clean = c.filter { it.isDigit() }
        if (clean.length < 8) { message = "Il codice deve avere almeno 8 cifre."; return }
        Foods.byBarcode(clean)?.let { chosen = it; message = ""; return }
        message = "Cerco $clean su Open Food Facts…"
        scope.launch {
            OpenFoodFacts.lookup(clean)
                .onSuccess { Repo.saveFood(it); chosen = it; message = "" }
                .onFailure { message = (it.message ?: "Errore di rete") + ". Puoi inserirlo a mano con «A mano»." }
        }
    }

    if (scanning) BarcodeScanner(onResult = { scanning = false; code = it; lookup(it) }, onClose = { scanning = false })

    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                Strong(title, 17)
                val sel = chosen
                when {
                    sel != null -> GramsStep(sel, onBack = { chosen = null }, onAdd = { g -> onAdd(sel, g) })
                    creating -> NewFoodForm(code, onCancel = { creating = false }) { f -> Repo.saveFood(f); creating = false; chosen = f }
                    else -> {
                        Field("Cerca alimento", query) { query = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Codice a barre", Modifier.weight(1f), primary = false) { message = ""; scanning = true }
                            Btn("A mano", Modifier.weight(1f), primary = false) { creating = true }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Field("Codice a mano", code, numeric = true, modifier = Modifier.weight(1f)) { code = it }
                            Spacer(Modifier.width(8.dp))
                            Btn("Cerca", Modifier.width(90.dp), primary = false) { lookup(code) }
                        }
                        if (message.isNotEmpty()) Muted(message, 12, Modifier.padding(vertical = 4.dp))
                        Spacer(Modifier.height(6.dp))
                        Foods.search(query, 40).forEach { f ->
                            Column(Modifier.fillMaxWidth().clickable { chosen = f }.padding(vertical = 8.dp)) {
                                Text(f.name)
                                Muted("${f.category} · ${f.kcal.toInt()} kcal · C ${f.c.toInt()} P ${f.p.toInt()} G ${f.f.toInt()} per 100 ${if (f.liquid) "ml" else "g"}", 11)
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Btn("Chiudi", primary = false, onClick = onClose)
            }
        }
    }
}

@Composable
private fun GramsStep(f: Food, onBack: () -> Unit, onAdd: (Int) -> Unit) {
    var grams by remember(f.id) { mutableIntStateOf(if (f.piece > 0) f.piece else 100) }
    val unit = if (f.liquid) "ml" else "g"
    Spacer(Modifier.height(8.dp))
    Text(f.name)
    Muted("Per 100 $unit: ${f.kcal.toInt()} kcal · C ${fmt1(f.c)} · P ${fmt1(f.p)} · G ${fmt1(f.f)}", 12)
    Stepper("Quantità", "$grams $unit", { grams = (grams - step(grams)).coerceAtLeast(5) }, { grams += step(grams) })
    Muted("Totale: ${(f.kcal * grams / 100).toInt()} kcal · C ${(f.c * grams / 100).toInt()} · P ${(f.p * grams / 100).toInt()} · G ${(f.f * grams / 100).toInt()}", 12)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("Indietro", Modifier.weight(1f), primary = false, onClick = onBack)
        Btn("Aggiungi", Modifier.weight(1f)) { onAdd(grams) }
    }
}

/** Grams step used by the steppers: finer for small portions. */
fun step(g: Int) = if (g < 50) 5 else 10

@Composable
private fun NewFoodForm(code: String, onCancel: () -> Unit, onSave: (Food) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Muted("Valori per 100 g, dall'etichetta", 12, Modifier.padding(top = 6.dp))
    Field("Nome", name) { name = it }
    Field("kcal", kcal, numeric = true) { kcal = it }
    Field("Carboidrati g", c, numeric = true) { c = it }
    Field("Proteine g", p, numeric = true) { p = it }
    Field("Grassi g", fat, numeric = true) { fat = it }
    if (err.isNotEmpty()) Muted(err, 12)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("Annulla", Modifier.weight(1f), primary = false, onClick = onCancel)
        Btn("Salva", Modifier.weight(1f)) {
            val k = parseD(kcal); val cc = parseD(c) ?: 0.0; val pp = parseD(p) ?: 0.0; val ff = parseD(fat) ?: 0.0
            if (name.isBlank() || k == null) { err = "Servono almeno nome e kcal."; return@Btn }
            val clean = code.filter { it.isDigit() }
            onSave(
                Food(
                    id = if (clean.length >= 8) "off_$clean" else "my_${System.currentTimeMillis()}",
                    name = name.trim(), kcal = k, c = cc, p = pp, f = ff, category = "I miei alimenti", barcode = clean,
                ),
            )
        }
    }
}
