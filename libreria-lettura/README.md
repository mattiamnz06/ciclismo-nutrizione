# Libreria di lettura

App Android per segnare i libri letti. Versione 0.1.

- Per ogni libro: titolo, pagine totali, copertina facoltativa (dalla galleria o dalla fotocamera). La percentuale si calcola dalla pagina a cui sei arrivato.
- Stati: In lettura, Da leggere, Letto, In pausa. All'ultima pagina il libro passa a Letto con la data di fine.
- Voto da 1 a 10 e riassunto personale fino a 600 parole, visibile solo aprendo il libro.
- Statistiche: libri letti per mese e per anno, pagine, voto medio.
- Widget per la home: il libro in lettura con la percentuale, dipinto ad acquerello con i colori dello sfondo del telefono (Material You), verde petrolio dove non sono disponibili.
- Impostazioni: tema Sistema/Chiaro/Scuro, immagine di sfondo scelta da te con velatura regolabile, backup JSON completo (libri, riassunti, copertine), ripristino ed esportazione CSV.

I dati restano sul telefono (`books.json` in `filesDir`), niente account e niente rete.

## Build
Non serve nulla in locale: `.github/workflows/libreria-build.yml` esegue i test, compila l'APK di debug e lo pubblica nella release `libreria-lettura-latest`.
Per compilare a mano: `gradle assembleDebug` dentro questa cartella (Gradle 8.9, JDK 17, Android SDK 35).

## Stack
Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, Material 3, minSdk 29. Nessuna libreria esterna oltre ad AndroidX e Compose.
