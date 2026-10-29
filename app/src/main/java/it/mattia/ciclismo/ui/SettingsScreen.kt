package it.mattia.ciclismo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import it.mattia.ciclismo.data.AppData
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.data.Supplement
import it.mattia.ciclismo.engine.Goal
import it.mattia.ciclismo.engine.Sex
import it.mattia.ciclismo.sync.Health
import it.mattia.ciclismo.sync.Notify
import it.mattia.ciclismo.sync.Strava
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

private val SECTIONS = listOf(
    Triple("profilo", "Profilo", "Nome, età, sesso, altezza"),
    Triple("corpo", "Corpo", "Peso, % grasso, FC a riposo e massima"),
    Triple("potenza", "Potenza", "FTP, zone di potenza e cardio, peso bici"),
    Triple("obiettivo", "Obiettivo", "Performance, dimagrimento, mantenimento, massa"),
    Triple("alimentazione", "Alimentazione", "Intolleranze, cibi, pasti e orari"),
    Triple("integratori", "Integratori", "Gel, barrette, isotonici, caffeina"),
    Triple("sync", "Sincronizzazioni", "Strava, Health Connect (Garmin, Samsung Health)"),
    Triple("notifiche", "Notifiche", "Pasti, bere in sella, acqua, pesata, piano della sera"),
    Triple("aspetto", "Unità e aspetto", "Tema chiaro, scuro o automatico"),
    Triple("dati", "Dati e privacy", "Esporta CSV, backup, ripristino, cancella"),
)

fun sectionTitle(id: String?) = SECTIONS.firstOrNull { it.first == id }?.second ?: "Impostazioni"

@Composable
fun SettingsScreen(nav: NavState) {
    val sec = nav.section
    if (sec == null) {
        SECTIONS.forEach { (id, title, sub) ->
            AppCard(onClick = { nav.section = id }) {
                Strong(title, 15)
                Muted(sub, 12)
            }
        }
        Muted("Tutti i dati restano sul telefono, nessun server esterno.", 12, Modifier.padding(vertical = 8.dp))
    } else {
        SectionContent(sec)
    }
}

@Composable
fun SectionContent(id: String) {
    val p = Repo.data.profile
    AppCard {
        when (id) {
            "profilo" -> {
                Field("Nome", p.name) { v -> Repo.setProfile { it.copy(name = v) } }
                Muted("Sesso")
                Chips(listOf("Uomo", "Donna"), if (p.sex == Sex.M) 0 else 1) { i -> Repo.setProfile { it.copy(sex = if (i == 0) Sex.M else Sex.F) } }
                Field("Età", p.age.toString(), true) { v -> v.toIntOrNull()?.let { a -> if (a in 10..100) Repo.setProfile { it.copy(age = a) } } }
                Field("Altezza (cm)", p.height.toInt().toString(), true) { v -> parseD(v)?.let { h -> if (h in 120.0..230.0) Repo.setProfile { it.copy(height = h) } } }
            }
            "corpo" -> {
                Field("Peso (kg)", fmt1(p.weight), true) { v -> parseD(v)?.let { w -> if (w in 30.0..250.0) Repo.setWeight(LocalDate.now(), w) } }
                Field("% grasso (vuoto = non noto)", p.bodyFat?.let { fmt1(it) } ?: "", true) { v ->
                    val x = parseD(v)
                    if (v.isBlank()) Repo.setProfile { it.copy(bodyFat = null) }
                    else if (x != null && x in 3.0..60.0) Repo.setProfile { it.copy(bodyFat = x) }
                }
                Field("FC a riposo (bpm)", p.restHr.toString(), true) { v -> v.toIntOrNull()?.let { h -> if (h in 30..110) Repo.setProfile { it.copy(restHr = h) } } }
                Field("FC massima (bpm)", p.maxHr.toString(), true) { v -> v.toIntOrNull()?.let { h -> if (h in 120..230) Repo.setProfile { it.copy(maxHr = h) } } }
                val ws = Repo.data.weights.takeLast(5).reversed()
                if (ws.isNotEmpty()) {
                    Muted("Storico peso", 12, Modifier.padding(top = 8.dp))
                    ws.forEach { Text("${it.first}: ${fmt1(it.second)} kg", fontSize = 13.sp) }
                }
            }
            "potenza" -> {
                Field("FTP (W)", p.ftp.toString(), true) { v -> v.toIntOrNull()?.let { f -> if (f in 80..600) Repo.setProfile { it.copy(ftp = f) } } }
                Field("Peso della bici (kg)", fmt1(p.bikeKg), true) { v -> parseD(v)?.let { b -> if (b in 4.0..25.0) Repo.setProfile { it.copy(bikeKg = b) } } }
                Muted("Zone di potenza (Coggan)", 12, Modifier.padding(top = 8.dp))
                val f = p.ftp
                listOf(
                    "Z1 Recupero" to "< ${(f * 0.55).toInt()} W", "Z2 Fondo" to "${(f * 0.56).toInt()}-${(f * 0.75).toInt()} W",
                    "Z3 Tempo" to "${(f * 0.76).toInt()}-${(f * 0.90).toInt()} W", "Z4 Soglia" to "${(f * 0.91).toInt()}-${(f * 1.05).toInt()} W",
                    "Z5 VO2max" to "${(f * 1.06).toInt()}-${(f * 1.20).toInt()} W", "Z6 Anaerobica" to "> ${(f * 1.20).toInt()} W",
                ).forEach { (n, r) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(n, fontSize = 13.sp); Muted(r, 13) } }
                Muted("Zone cardio", 12, Modifier.padding(top = 8.dp))
                val m = p.maxHr
                listOf(50 to 60, 60 to 70, 70 to 80, 80 to 90, 90 to 100).forEachIndexed { i, (a, b) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Zona ${i + 1}", fontSize = 13.sp); Muted("${m * a / 100}-${m * b / 100} bpm", 13)
                    }
                }
            }
            "obiettivo" -> {
                val goals = Goal.entries
                Chips(goals.map { it.label }, goals.indexOf(p.goal)) { i -> Repo.setProfile { it.copy(goal = goals[i]) } }
                Field("Peso obiettivo (kg, vuoto = nessuno)", p.goalWeight?.let { fmt1(it) } ?: "", true) { v ->
                    if (v.isBlank()) Repo.setProfile { it.copy(goalWeight = null) } else parseD(v)?.let { w -> Repo.setProfile { it.copy(goalWeight = w) } }
                }
                Field("Data obiettivo (aaaa-mm-gg)", p.goalDate) { v -> Repo.setProfile { it.copy(goalDate = v) } }
            }
            "alimentazione" -> {
                Field("Intolleranze o allergie (separate da virgola)", p.allergies) { v -> Repo.setProfile { it.copy(allergies = v) } }
                Field("Cibi da escludere", p.excluded) { v -> Repo.setProfile { it.copy(excluded = v) } }
                Field("Cibi preferiti", p.favorites) { v -> Repo.setProfile { it.copy(favorites = v) } }
                Muted("Pasti al giorno")
                Chips(listOf("3", "4", "5", "6"), p.nMeals.coerceIn(3, 6) - 3) { i -> Repo.setProfile { it.copy(nMeals = i + 3, mealTimes = "") } }
                Field("Orari abituali (es. 07:30, 13:00, 16:30, 20:00)", p.mealTimes) { v -> Repo.setProfile { it.copy(mealTimes = v) } }
                Muted("Se gli orari non sono tanti quanti i pasti, uso quelli predefiniti.", 12)
            }
            "integratori" -> Supplements()
            "sync" -> SyncSection()
            "notifiche" -> NotifSection()
            "aspetto" -> {
                Muted("Tema")
                val modes = listOf("auto", "light", "dark")
                Chips(listOf("Automatico", "Chiaro", "Scuro"), modes.indexOf(p.theme).coerceAtLeast(0)) { i -> Repo.setProfile { it.copy(theme = modes[i]) } }
                Muted("Unità: kg, km, kcal, ml.", 12, Modifier.padding(top = 8.dp))
            }
            "dati" -> DataSection()
        }
    }
}

@Composable
private fun Supplements() {
    val p = Repo.data.profile
    var name by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var na by remember { mutableStateOf("") }
    SwitchRow("Caffeina", "Proposta 3 mg/kg prima di soglia e gare", p.caffeine) { v -> Repo.setProfile { it.copy(caffeine = v) } }
    p.supplements.forEachIndexed { i, s ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${s.name}: ${s.carbG} g carbo, ${s.sodiumMg} mg sodio", Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = { Repo.setProfile { it.copy(supplements = it.supplements.filterIndexed { j, _ -> j != i }) } }) { Text("Togli") }
        }
    }
    Muted("Aggiungi un prodotto (per unità)", 12, Modifier.padding(top = 8.dp))
    Field("Nome (es. gel, barretta, isotonico)", name) { name = it }
    Field("Carboidrati (g)", carb, true) { carb = it }
    Field("Sodio (mg)", na, true) { na = it }
    Btn("Aggiungi") {
        val c = parseD(carb)?.toInt()
        if (name.isNotBlank() && c != null) {
            Repo.setProfile { it.copy(supplements = it.supplements + Supplement(name.trim(), c, parseD(na)?.toInt() ?: 0)) }
            name = ""; carb = ""; na = ""
        }
    }
}

@Composable
private fun SyncSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = Repo.data
    val p = d.profile
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { Repo.syncMessage = Health.sync(ctx).fold({ it }, { e -> "Errore Health Connect: ${e.message}" }) }
    }

    Strong("Strava", 15)
    Muted("Crea la tua app su strava.com/settings/api e imposta «Authorization Callback Domain» = strava. Poi inserisci qui Client ID e Client Secret.", 12)
    Field("Client ID", p.stravaId, true) { v -> Repo.setProfile { it.copy(stravaId = v.trim()) } }
    Field("Client Secret", p.stravaSecret) { v -> Repo.setProfile { it.copy(stravaSecret = v.trim()) } }
    Spacer(Modifier.height(4.dp))
    if (d.stravaRefresh.isEmpty()) {
        Btn("Collega Strava") {
            if (p.stravaId.isNotBlank() && p.stravaSecret.isNotBlank()) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Strava.authUrl(p.stravaId))))
            else Repo.syncMessage = "Inserisci prima Client ID e Client Secret"
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Sincronizza ora", Modifier.weight(1f)) { syncStravaNow(scope) }
            Btn("Scollega", Modifier.weight(1f), primary = false) { Strava.disconnect() }
        }
    }
    Muted("Ultima sincronizzazione: ${d.lastSync.ifEmpty { "mai" }}", 12, Modifier.padding(top = 4.dp))
    Muted("Garmin: attiva in Garmin Connect l'invio automatico a Strava; l'app riceve watt, FC e cadenza da lì.", 12)

    Spacer(Modifier.height(16.dp))
    Strong("Health Connect", 15)
    if (!Health.available(ctx)) {
        Muted("Health Connect non è disponibile su questo telefono (installa o aggiorna l'app Health Connect).", 12)
    } else {
        Muted("Legge peso, % grasso, FC a riposo e sonno da Samsung Health e dalle bilance smart. Il valore inserito a mano vince solo finché non arriva una misura più nuova.", 12)
        Btn("Concedi i permessi e sincronizza", Modifier.fillMaxWidth().padding(top = 4.dp)) { hcLauncher.launch(Health.permissions) }
        Btn("Sincronizza ora", Modifier.fillMaxWidth().padding(top = 6.dp), primary = false) {
            scope.launch { Repo.syncMessage = Health.sync(ctx).fold({ it }, { e -> "Errore Health Connect: ${e.message}" }) }
        }
    }
    if (Repo.syncMessage.isNotEmpty()) Text(Repo.syncMessage, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotifSection() {
    val ctx = LocalContext.current
    val p = Repo.data.profile
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun change(f: (it.mattia.ciclismo.data.Profile) -> it.mattia.ciclismo.data.Profile) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Repo.setProfile(f)
        Notify.scheduleAll(ctx)
    }
    fun clock(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    fun shift(m: Int, by: Int) = Math.floorMod(m + by, 24 * 60)

    SwitchRow("Notifiche attive", "Interruttore generale per tutti i promemoria", p.notifOn) { v -> change { it.copy(notifOn = v) } }
    if (!p.notifOn) return
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    SwitchRow("Pasti", "All'ora di ogni pasto del piano di oggi", p.notifMeals) { v -> change { it.copy(notifMeals = v) } }
    if (p.notifMeals) {
        val kinds = p.notifMealKinds.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Notify.mealKinds.forEach { (k, label) ->
                FilterChip(selected = k.name in kinds, label = { Text(label) }, onClick = {
                    val next = if (k.name in kinds) kinds - k.name else kinds + k.name
                    change { it.copy(notifMealKinds = next.joinToString(",")) }
                })
            }
        }
        val leads = listOf(0, 5, 10, 15, 30, 45, 60)
        val li = leads.indexOf(p.notifMealLead).coerceAtLeast(0)
        Stepper("Anticipo", if (p.notifMealLead == 0) "all'ora" else "${p.notifMealLead} min prima",
            { change { it.copy(notifMealLead = leads[(li - 1).coerceAtLeast(0)]) } },
            { change { it.copy(notifMealLead = leads[(li + 1).coerceAtMost(leads.lastIndex)]) } })
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    SwitchRow("Bere in sella", "Partono da soli all'ora del giro pianificato", p.notifDrink) { v -> change { it.copy(notifDrink = v) } }
    if (p.notifDrink) Stepper("Ogni", "${p.notifDrinkEvery} min",
        { change { it.copy(notifDrinkEvery = (p.notifDrinkEvery - 5).coerceAtLeast(10)) } },
        { change { it.copy(notifDrinkEvery = (p.notifDrinkEvery + 5).coerceAtMost(45)) } })
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    SwitchRow("Acqua durante il giorno", "Un bicchiere a intervalli regolari", p.notifWater) { v -> change { it.copy(notifWater = v) } }
    if (p.notifWater) {
        Stepper("Ogni", "${p.notifWaterEvery} h",
            { change { it.copy(notifWaterEvery = (p.notifWaterEvery - 1).coerceAtLeast(1)) } },
            { change { it.copy(notifWaterEvery = (p.notifWaterEvery + 1).coerceAtMost(4)) } })
        Stepper("Dalle", clock(p.notifWaterFrom),
            { change { it.copy(notifWaterFrom = shift(p.notifWaterFrom, -30)) } },
            { change { it.copy(notifWaterFrom = shift(p.notifWaterFrom, 30).coerceAtMost(p.notifWaterTo)) } })
        Stepper("Alle", clock(p.notifWaterTo),
            { change { it.copy(notifWaterTo = shift(p.notifWaterTo, -30).coerceAtLeast(p.notifWaterFrom)) } },
            { change { it.copy(notifWaterTo = shift(p.notifWaterTo, 30)) } })
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    SwitchRow("Pesata del mattino", "Alle ${clock(p.notifWeighAt)}", p.notifWeigh) { v -> change { it.copy(notifWeigh = v) } }
    if (p.notifWeigh) Stepper("Orario", clock(p.notifWeighAt),
        { change { it.copy(notifWeighAt = shift(p.notifWeighAt, -15)) } },
        { change { it.copy(notifWeighAt = shift(p.notifWeighAt, 15)) } })
    SwitchRow("Piano della sera", "Alle ${clock(p.notifEveningAt)}, per il giorno dopo", p.notifEvening) { v -> change { it.copy(notifEvening = v) } }
    if (p.notifEvening) Stepper("Orario", clock(p.notifEveningAt),
        { change { it.copy(notifEveningAt = shift(p.notifEveningAt, -15)) } },
        { change { it.copy(notifEveningAt = shift(p.notifEveningAt, 15)) } })
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    val today = Notify.plannedToday(Repo.data, java.time.LocalDate.now())
        .filter { it.minuteOfDay * 60_000L > java.time.LocalTime.now().toSecondOfDay() * 1000L }
        .sortedBy { it.minuteOfDay }
    Muted(if (today.isEmpty()) "Nessun promemoria rimasto per oggi." else "Prossimi oggi: " +
        today.take(6).joinToString(" · ") { "${clock(it.minuteOfDay)} ${it.title.substringBefore(" alle")}" }, 12)
    Btn("Prova una notifica", Modifier.fillMaxWidth().padding(top = 6.dp), primary = false) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Notify.show(ctx, 9, "Prova", "Le notifiche funzionano.")
    }
    Muted("I promemoria vengono ricalcolati ogni notte e dopo un riavvio del telefono.", 12, Modifier.padding(top = 4.dp))
}

@Composable
private fun DataSection() {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }

    fun write(uri: Uri?, text: String) {
        if (uri == null) return
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        Repo.syncMessage = "File salvato"
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val d = Repo.data
        val sb = StringBuilder("data,partenza,durata_s,km,dislivello_m,watt_medi,np,if,tss,kj,fc_media,fonte\n")
        d.rides.forEach { r ->
            sb.append("${r.date},${"%.2f".format(r.startHour)},${r.durSec},${"%.1f".format(r.km)},${r.elev},${"%.0f".format(r.avgW)},")
                .append("${"%.0f".format(r.np)},${"%.2f".format(r.ifv(d.profile.ftp))},${"%.0f".format(r.tss(d.profile.ftp))},${"%.0f".format(r.kj)},${r.avgHr},${r.source}\n")
        }
        sb.append("\ndata,peso_kg\n")
        d.weights.forEach { sb.append("${it.first},${it.second}\n") }
        write(uri, sb.toString())
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        write(uri, Repo.data.toJson().toString())
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() }
                Repo.replaceAll(AppData.fromJson(JSONObject(text)))
                Repo.syncMessage = "Backup ripristinato"
            } catch (e: Exception) {
                Repo.syncMessage = "File non valido: ${e.message}"
            }
        }
    }
    Btn("Esporta giri e peso (CSV)") { csv.launch("ciclismo-export.csv") }
    Spacer(Modifier.height(6.dp))
    Btn("Salva un backup", primary = false) { backup.launch("ciclismo-backup.json") }
    Spacer(Modifier.height(6.dp))
    Btn("Ripristina da backup", primary = false) { restore.launch(arrayOf("application/json", "*/*")) }
    Spacer(Modifier.height(6.dp))
    Btn("Cancella tutti i dati", primary = false) { confirm = true }
    if (Repo.syncMessage.isNotEmpty()) Muted(Repo.syncMessage, 12, Modifier.padding(top = 8.dp))
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Cancellare tutto?") },
            text = { Text("Profilo, giri, diario e pesi saranno eliminati dal telefono. Fai prima un backup se ti serve.") },
            confirmButton = { TextButton(onClick = { Repo.clearAll(); confirm = false }) { Text("Cancella") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } },
        )
    }
}
