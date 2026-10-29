package it.mattia.ciclismo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.ciclismo.data.Repo
import it.mattia.ciclismo.sync.Notify
import it.mattia.ciclismo.sync.Strava
import java.time.LocalDate

private val TABS = listOf("Oggi", "Giri", "Settimana", "Piano", "Diario", "Forma")
private val TAB_ICONS = listOf(
    Icons.Filled.Home, Icons.Filled.LocationOn, Icons.Filled.DateRange, Icons.Filled.Edit, Icons.Filled.List, Icons.Filled.Favorite,
)

class NavState {
    var tab by mutableIntStateOf(0)
    var settings by mutableStateOf(false)
    var section by mutableStateOf<String?>(null)
    var diaryDate by mutableStateOf(LocalDate.now())
    var openMeal by mutableStateOf<String?>(null)
    var rideId by mutableStateOf<String?>(null)
    var manual by mutableStateOf(false)
    var planDate by mutableStateOf<LocalDate?>(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiclismoApp() {
    val nav = remember { NavState() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = Repo.data.profile

    LaunchedEffect(Unit) {
        Notify.scheduleAll(ctx)
        if (Repo.data.stravaRefresh.isNotEmpty()) {
            Strava.sync().onSuccess { n -> if (n > 0) Repo.syncMessage = "Importati $n nuovi giri da Strava" }
        }
    }
    val code = Repo.stravaCode
    LaunchedEffect(code) {
        if (code != null) {
            Repo.stravaCode = null
            Repo.syncMessage = "Collegamento a Strava…"
            Strava.exchange(code).fold(
                { syncStravaNow(scope) },
                { Repo.syncMessage = "Collegamento non riuscito: ${it.message}" },
            )
            nav.settings = true
            nav.section = "sync"
        }
    }

    BackHandler(enabled = nav.settings || nav.rideId != null || nav.manual) {
        when {
            nav.settings && nav.section != null -> nav.section = null
            nav.settings -> nav.settings = false
            nav.manual -> nav.manual = false
            else -> nav.rideId = null
        }
    }

    if (!p.onboarded) {
        Onboarding()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (nav.settings) sectionTitle(nav.section) else TABS[nav.tab],
                        fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFamily = Condensed,
                    )
                },
                actions = {
                    TextButton(onClick = {
                        if (nav.settings && nav.section != null) nav.section = null
                        else { nav.settings = !nav.settings; nav.section = null }
                    }) {
                        if (!nav.settings) Icon(Icons.Filled.Settings, contentDescription = null, Modifier.padding(end = 6.dp).size(20.dp))
                        Text(if (nav.settings) (if (nav.section != null) "Indietro" else "Chiudi") else "Impostazioni")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = !nav.settings && nav.tab == i,
                        onClick = { nav.tab = i; nav.settings = false; nav.section = null },
                        icon = { Icon(TAB_ICONS[i], contentDescription = null) },
                        label = { Text(t, fontSize = 10.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            when {
                nav.settings -> SettingsScreen(nav)
                nav.tab == 0 -> TodayScreen(nav)
                nav.tab == 1 -> RidesScreen(nav)
                nav.tab == 2 -> WeekScreen(nav)
                nav.tab == 3 -> PlanScreen(nav)
                nav.tab == 4 -> DiaryScreen(nav)
                else -> FormScreen(nav)
            }
        }
    }
}

@Composable
private fun Onboarding() {
    var step by remember { mutableIntStateOf(0) }
    val titles = listOf("Profilo", "Corpo", "Potenza")
    val ids = listOf("profilo", "corpo", "potenza")
    Scaffold { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Benvenuto", fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed)
            Muted("Tre passi per calcolare calorie e macro sui tuoi dati. Potrai cambiare tutto in Impostazioni.", 13)
            Spacer(Modifier.height(12.dp))
            Strong("${step + 1} di 3 · ${titles[step]}", 16)
            SectionContent(ids[step])
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) Btn("Indietro", Modifier.weight(1f), primary = false) { step-- }
                Btn(if (step == 2) "Inizia" else "Avanti", Modifier.weight(1f)) {
                    if (step == 2) Repo.setProfile { it.copy(onboarded = true) } else step++
                }
            }
            TextButton(onClick = { Repo.setProfile { it.copy(onboarded = true) } }) { Text("Salta per ora") }
        }
    }
}
