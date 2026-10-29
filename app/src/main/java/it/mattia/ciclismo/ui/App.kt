package it.mattia.ciclismo.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.engine.*

private val TABS = listOf("Oggi", "Giri", "Settimana", "Piano", "Diario", "Forma")

/** Profile kept in SharedPreferences for now; it moves to Room in the next stage. */
class ProfileStore(context: Context) {
    private val p = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    fun load() = Profile(
        sex = if (p.getString("sex", "M") == "F") Sex.F else Sex.M,
        age = p.getInt("age", 30), height = p.getFloat("height", 180f).toDouble(),
        weight = p.getFloat("weight", 74f).toDouble(), ftp = p.getInt("ftp", 260),
        goal = Goal.valueOf(p.getString("goal", Goal.PERFORMANCE.name)!!),
    )
    fun save(x: Profile) = p.edit()
        .putString("sex", x.sex.name).putInt("age", x.age).putFloat("height", x.height.toFloat())
        .putFloat("weight", x.weight.toFloat()).putInt("ftp", x.ftp).putString("goal", x.goal.name).apply()
}

data class Profile(val sex: Sex, val age: Int, val height: Double, val weight: Double, val ftp: Int, val goal: Goal) {
    fun body() = Body(sex, age, height, weight)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiclismoApp(context: Context) {
    val store = remember { ProfileStore(context) }
    var profile by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (settings) "Impostazioni" else TABS[tab], fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { settings = !settings }) { Text(if (settings) "Chiudi" else "Impostazioni") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = !settings && tab == i,
                        onClick = { tab = i; settings = false },
                        icon = { Text(t.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(t, fontSize = 10.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            when {
                settings -> SettingsScreen(profile) { profile = it; store.save(it) }
                tab == 0 -> TodayScreen(profile)
                else -> Placeholder(TABS[tab])
            }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    InfoCard("$name", "In arrivo nelle prossime versioni (vedi roadmap nel report).")
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodayScreen(p: Profile) {
    // Demo ride until Strava/manual rides land in the next stage: sweet spot, 1 h 15 at 200 W.
    val rideKj = Metrics.kilojoules(200.0, 4500)
    val rideNp = Metrics.npFromAverage(200.0)
    val rideIf = Metrics.intensityFactor(rideNp, p.ftp.toDouble())
    val rideTss = Metrics.tss(4500, rideIf)
    val type = Nutrition.dayType(rideTss, 1.25)
    val plan = Nutrition.dayPlan(p.body(), type, p.goal, rideKj)
    val fuel = Nutrition.rideFuel(75, intense = true)

    InfoCard(
        "Calorie del giorno · ${type.label}",
        "${plan.kcal} kcal\nMetabolismo ${plan.bmr} x 1,4 = ${plan.baseKcal} + giro ${rideKj.toInt()} kJ",
    )
    InfoCard(
        "Macro",
        "Carboidrati ${plan.carbG} g\nProteine ${plan.proteinG} g\nGrassi ${plan.fatG} g",
    )
    InfoCard(
        "Giro previsto (esempio)",
        "NP %.0f W · IF %.2f · TSS %.0f\nIn sella: %d g/h carbo, %d ml/h, %d mg/h sodio".format(
            rideNp, rideIf, rideTss, fuel.carbGPerH, fuel.fluidMlPerH, fuel.sodiumMgPerH,
        ),
    )
    InfoCard("W/kg", "FTP %d W = %.2f W/kg".format(p.ftp, Metrics.wattsPerKg(p.ftp.toDouble(), p.weight)))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(p: Profile, onChange: (Profile) -> Unit) {
    var age by remember { mutableStateOf(p.age.toString()) }
    var height by remember { mutableStateOf(p.height.toInt().toString()) }
    var weight by remember { mutableStateOf(p.weight.toString()) }
    var ftp by remember { mutableStateOf(p.ftp.toString()) }

    fun push(sex: Sex = p.sex, goal: Goal = p.goal) = onChange(
        Profile(
            sex, age.toIntOrNull() ?: p.age, height.toDoubleOrNull() ?: p.height,
            weight.replace(',', '.').toDoubleOrNull() ?: p.weight, ftp.toIntOrNull() ?: p.ftp, goal,
        ),
    )

    @Composable
    fun field(label: String, value: String, set: (String) -> Unit) = OutlinedTextField(
        value = value, onValueChange = { set(it); push() }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )

    Text("Profilo e corpo", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { s ->
            FilterChip(selected = p.sex == s, onClick = { push(sex = s) }, label = { Text(if (s == Sex.M) "Uomo" else "Donna") })
        }
    }
    field("Età", age) { age = it }
    field("Altezza (cm)", height) { height = it }
    field("Peso (kg)", weight) { weight = it }
    field("FTP (W)", ftp) { ftp = it }
    Text("Obiettivo", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Goal.entries.forEach { g ->
            FilterChip(selected = p.goal == g, onClick = { push(goal = g) }, label = { Text(g.label) })
        }
    }
    Spacer(Modifier.height(24.dp))
}
