# Handoff — app ciclismo e nutrizione

Stato al 2026-10-29 (seconda sessione: alimenti, micronutrienti, diario, barcode, notifiche, interfaccia). Leggi questo file prima di ogni sessione di correzioni.

## Dove sta tutto
- Codice: `C:\Users\mattia\Claude\Progetti\ciclismo-nutrizione` · repo pubblico https://github.com/mattiamnz06/ciclismo-nutrizione (branch `main`).
- APK: https://github.com/mattiamnz06/ciclismo-nutrizione/releases/download/latest/ciclismo-nutrizione.apk (release `latest`, rigenerata a ogni push su `main`).
- Specifiche: `docs/report.txt` (testo del report "Report di progetto: app ciclismo e nutrizione", claude.ai/artifact/WNhq4MpZq4vCoEwoeQma6Z). Prototipo grafico: claude.ai/artifact/CxRtn4Royoh87WqsnxAogt.

## Come si compila e verifica
- **In locale non c'è Java né Android SDK**: si compila solo su GitHub Actions.
- `.github/workflows/build.yml` (su push): `gradle testDebugUnitTest` → `assembleDebug` → pubblica la release `latest`. Gradle 8.9 tramite setup-gradle, niente wrapper nel repo.
- `.github/workflows/smoke.yml` (manuale: `gh workflow run smoke.yml`): emulatore API 34, salta l'onboarding, apre le 6 schede, Carico carbo e Impostazioni, poi monkey per 400 eventi, e fallisce se nel logcat c'è `FATAL EXCEPTION`. Gli screenshot si scaricano con `gh run download <id> -n smoke -D <cartella>` (lo script è `.github/smoke.sh`).
- Ultima verifica: build e 25 test verdi; smoke test superato (ora copre anche notifiche, modifica del pasto, ricerca alimenti e le 6 schede in tema scuro). Screenshot controllati in chiaro e scuro. Non verificati su telefono reale: fotocamera e codice a barre, Open Food Facts, notifiche che suonano davvero, BOOT_COMPLETED.

## Stack e vincoli di versione
- Kotlin 2.0.21, AGP 8.7.3, compileSdk/targetSdk 35, minSdk 29, Compose BOM 2024.12.01, Material 3.
- Health Connect `connect-client:1.1.0-alpha07`: la 1.1.0 stabile richiede AGP 8.9.1+ e compileSdk 36. Per aggiornarla bisogna alzare insieme AGP, Gradle e compileSdk.
- Diario: AppData.itemGrams (grammi propri per alimento, 0 = tolto) e dded (alimenti aggiunti a un pasto); la correzione automatica va nei due sensi (±25% sul pasto dopo).
- Niente Room: i dati stanno in un solo JSON (`filesDir/data.json`), letto e scritto da `data/Repo.kt` come stato Compose. È una deviazione dal report (che prevede Room).
- APK firmato con la keystore di debug: installabile, ma non va su Play Store.

## Mappa del codice (`app/src/main/java/it/mattia/ciclismo/`)
- `engine/Metrics.kt`: NP, IF, VI, TSS, kJ, W/kg, migliori sforzi, FTP stimata, zone Coggan, PMC (CTL 42 g, ATL 7 g, TSB).
- `engine/Nutrition.kt`: BMR (Katch-McArdle se c'è la % di grasso, altrimenti Mifflin), tipi di giornata con g/kg, `dayPlan` (grassi limitati a 1,5 g/kg, l'eccesso va ai carboidrati fino a 12 g/kg), carburante in sella per durata.
- `engine/Foods.kt` + `engine/FoodTable.kt` (generato): **264 alimenti** USDA SR Legacy con nomi italiani, categoria, allergeni (lattosio, glutine, pesce, crostacei, fruttasecca, uova, soia) e 16 colonne di micro mostrate come **11 micronutrienti** (gruppo B = media di B1, B2, B3, B6, folati, B12). Più alimenti propri (`customFoods`), ricerca, `alternatives` e `howTo`. Rigenerare: `node tools/foods/build.mjs <cartella CSV USDA>` (spec in `tools/foods/spec.txt`, provenienza in `sources.txt`; il CSV è `FoodData_Central_sr_legacy_food_csv_2018-04.zip` di fdc.nal.usda.gov, non nel repo).
- `engine/Meals.kt`: `MealPlanner`. I template a slot (F fisso, C carbo, D carbo secondario 25%, P proteine, L grassi) calcolano i grammi per centrare i macro. Pasti base da `nMeals`/orari; con un giro aggiunge Pre-giro (−90 min) e Recupero (+30 min) e toglie i pasti base nella finestra del giro. Gestisce lo spuntino della sera prima di un giro duro, gli esclusi e le allergie (alias lattosio, glutine, pesce, frutta secca) e i preferiti.
- `engine/Planner.kt`: `dayInfo` (giro fatto oppure pianificato, tipo di giornata, recupero dopo TSS>150, carico carbo se nei 3 giorni dopo c'è un evento ≥180 min), piano del giorno, pasti, obiettivo acqua (30 ml/kg + liquidi del giro), forma, FTP suggerita, micronutrienti.
- `data/Models.kt` (Profile, Ride, PlannedRide, AppData con il JSON), `data/Repo.kt`, `data/RideFactory.kt` (ricampionamento a 1 Hz, grafico di ~170 punti, migliori sforzi, deriva Pw:FC, giro manuale).
- `sync/OpenFoodFacts.kt` (prodotto per codice a barre), `ui/BarcodeScanner.kt` (CameraX `LifecycleCameraController` + ML Kit), `ui/FoodPicker.kt` (ricerca, codice, alimento a mano).
- `sync/Strava.kt` (OAuth con redirect `ciclismo://strava`, ultimi 120 giorni, stream solo per i 20 giri nuovi più recenti per sync), `sync/Health.kt` (peso, grasso, FC a riposo, sonno), `sync/Gpx.kt`, `sync/Notify.kt` (AlarmManager: `plannedToday` calcola i promemoria del giorno dal piano, ricalcolo alle 00:05 e `BootReceiver` dopo il riavvio).
- `ui/`: `App.kt` (NavState, barra in basso con 6 schede, impostazioni dal pulsante in alto, onboarding in 3 passi), `DayView.kt` (piano del giorno, mangiato e correzione automatica), `MealCard.kt`, una schermata per file e `Common.kt` (componenti e grafico a linee su Canvas).

## Limiti noti e cose da fare
- **Alimenti**: fonte USDA invece di CREA (scelta dell'utente). Sodio dei cibi senza sale aggiunto; sodio e vitamina D si mostrano ma non attivano la correzione automatica. Barcode e Open Food Facts mai provati su un telefono reale.
- **Import file**: niente lettore FIT, c'è solo il GPX.
- **Garmin**: nessun import diretto da Health Connect (ExerciseSession); si passa da Garmin → Strava.
- **Unità**: solo metriche (va bene così per l'utente).
- **Notifiche**: allarmi non esatti (`setAndAllowWhileIdle`), possono ritardare di qualche minuto. Se cambiano gli orari dei pasti in Impostazioni → Pasti, si aggiornano alla prossima apertura dell'app o a mezzanotte.
- **Proposte di allenamento**: l'app non le fa. L'utente pianifica il giro (tipo, durata, ora) e l'app calcola alimentazione e carico. La domanda aperta del report non ha ancora una risposta.
- **Integrazioni non provate**: Strava e Health Connect non sono mai stati provati con dati reali (servono le chiavi dell'utente e il telefono Samsung).
- **Stima carboidrati bruciati**: formula euristica, `RideDetail` in `RidesScreen.kt`.
- **Domande aperte del report**: pasti al giorno di base (oggi 4) e bilancia smart; proposte di allenamento. L'utente ha chiesto di aspettare.
- **Rinviati dall'utente**: lettore FIT, import Garmin, prova di Strava e Health Connect. Play Store non interessa.

## Regole di lavoro
- Dopo ogni modifica: push, attendi `build.yml` (con `gh run watch`); per cambi di interfaccia lancia anche `smoke.yml` e guarda gli screenshot.
- Le commit richiedono la riga `Co-Authored-By` del modello in uso.
