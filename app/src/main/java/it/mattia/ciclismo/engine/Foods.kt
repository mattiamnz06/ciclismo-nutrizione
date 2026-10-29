package it.mattia.ciclismo.engine

/**
 * Values per 100 g (or ml). [micro] follows [Foods.rawNames]: iron, calcium, magnesium, potassium, sodium,
 * zinc (mg), vitamin C, D (µg), E (mg), B1, B2, B3, B6 (mg), folate, B12 (µg), omega 3 (g).
 */
class Food(
    val id: String,
    val name: String,
    val kcal: Double,
    val c: Double,
    val p: Double,
    val f: Double,
    val piece: Int = 0,
    val liquid: Boolean = false,
    val micro: DoubleArray = DoubleArray(Foods.RAW),
    val category: String = "Altro",
    val allergens: List<String> = emptyList(),
    val barcode: String = "",
)

object Foods {
    const val RAW = 16
    val rawNames = listOf(
        "Ferro", "Calcio", "Magnesio", "Potassio", "Sodio", "Zinco", "Vitamina C", "Vitamina D", "Vitamina E",
        "B1", "B2", "B3", "B6", "Folati", "B12", "Omega 3",
    )
    /** Daily reference intakes for the raw columns (sodium: adequate intake, not an upper limit). */
    val rawRda = doubleArrayOf(10.0, 1000.0, 375.0, 3400.0, 1500.0, 11.0, 90.0, 15.0, 15.0, 1.2, 1.3, 16.0, 1.3, 400.0, 2.4, 1.6)

    /** The 11 nutrients shown in the app; "Gruppo B" is the mean of B1, B2, B3, B6, folate and B12. */
    val microNames = listOf(
        "Ferro", "Calcio", "Magnesio", "Potassio", "Sodio", "Zinco", "Vitamina C", "Vitamina D", "Vitamina E", "Gruppo B", "Omega 3",
    )
    private const val B_FROM = 9
    private const val B_TO = 14

    /** Percent of the daily reference for each of the 11 shown nutrients, from raw amounts. */
    fun percents(raw: DoubleArray): List<Int> = microNames.indices.map { i ->
        when {
            i < B_FROM -> raw[i] / rawRda[i] * 100
            i == B_FROM -> (B_FROM..B_TO).map { (raw[it] / rawRda[it]).coerceAtMost(1.5) }.average() * 100
            else -> raw[15] / rawRda[15] * 100
        }.toInt()
    }

    val all: Map<String, Food> = FOOD_TABLE.lineSequence().filter { it.isNotBlank() }.map { line ->
        val c = line.split(';')
        val n = c.drop(6).map { it.toDouble() }
        Food(
            id = c[0], name = c[1], category = c[2], piece = c[3].toInt(), liquid = c[4] == "1",
            allergens = c[5].split(' ').filter { it.isNotEmpty() },
            kcal = n[0], c = n[1], p = n[2], f = n[3], micro = n.drop(4).toDoubleArray(),
        )
    }.associateBy { it.id }

    /** Foods added by the user (barcode or by hand), looked up after the built-in table. */
    @Volatile
    private var custom: Map<String, Food> = emptyMap()

    fun setCustom(list: List<Food>) {
        custom = list.associateBy { it.id }
    }

    fun find(id: String): Food? = all[id] ?: custom[id]

    fun get(id: String): Food = find(id) ?: Food(id, id, 0.0, 0.0, 0.0, 0.0)

    fun byBarcode(code: String): Food? = custom.values.firstOrNull { it.barcode == code }

    val categories: List<String> get() = (all.values + custom.values).map { it.category }.distinct()

    /** Case-insensitive search on names and categories; custom foods first. */
    fun search(q: String, limit: Int = 40): List<Food> {
        val t = q.trim().lowercase()
        val pool = custom.values + all.values
        if (t.isEmpty()) return pool.take(limit)
        return pool.filter { it.name.lowercase().contains(t) || it.category.lowercase().contains(t) }
            .sortedBy { if (it.name.lowercase().startsWith(t)) 0 else 1 }
            .take(limit)
    }

    /** Short equivalent suggestions shown under "Alternative". */
    val alternatives = mapOf(
        "riso" to "pasta o patate (stessi carboidrati)",
        "pasta" to "riso o patate",
        "patate" to "riso o pasta",
        "couscous" to "riso, quinoa o pasta",
        "quinoa" to "cous cous o riso",
        "farro" to "orzo o riso integrale",
        "gnocchi" to "patate o pasta",
        "patate_dolci" to "patate o riso",
        "avena" to "pane integrale con miele",
        "corn_flakes" to "fiocchi d'avena o riso soffiato",
        "pane" to "fette biscottate o patate",
        "fette_biscottate" to "pane o gallette di riso",
        "gallette_riso" to "pane o crackers",
        "pollo" to "tacchino, tonno o merluzzo",
        "tacchino" to "pollo, tonno o 2 uova",
        "manzo" to "pollo, tacchino o bresaola",
        "merluzzo" to "tonno, nasello o pollo",
        "orata" to "branzino, merluzzo o trota",
        "trota" to "salmone o orata",
        "sgombro" to "salmone o sardine",
        "salmone" to "tonno o uova (meno grassi: merluzzo)",
        "tonno" to "pollo o tacchino",
        "uova" to "tonno o yogurt greco con fiocchi di latte",
        "yogurt" to "kefir, fiocchi di latte o 2 uova",
        "skyr" to "yogurt greco o fiocchi di latte",
        "ricotta" to "fiocchi di latte o yogurt greco",
        "tofu" to "tempeh, uova o legumi",
        "banana" to "mela o 2 datteri",
        "ceci" to "lenticchie o fagioli",
        "lenticchie" to "ceci o fagioli",
        "fagioli_cannellini" to "ceci o lenticchie",
    )

    val howTo = mapOf(
        "riso" to "riso bianco basmati, non integrale, cotto al dente",
        "pasta" to "pasta di semola, al dente, condita con olio a crudo",
        "patate" to "patate lesse o al forno senza pelle croccante",
        "couscous" to "cous cous in acqua bollente salata per 5 minuti, poi sgranato",
        "quinoa" to "quinoa sciacquata e cotta 15 minuti",
        "farro" to "farro perlato lessato per 25 minuti",
        "gnocchi" to "gnocchi lessati finché salgono a galla",
        "patate_dolci" to "patate dolci al forno a 200 °C per 40 minuti",
        "avena" to "fiocchi in acqua calda o nel latte per 3 minuti",
        "corn_flakes" to "con il latte freddo",
        "pane" to "pane bianco o semi-integrale, meglio se del giorno",
        "fette_biscottate" to "con un velo di miele o marmellata",
        "pollo" to "alla piastra o al vapore, poco sale",
        "tacchino" to "alla piastra con limone",
        "manzo" to "alla piastra, cottura media",
        "merluzzo" to "al forno con limone e prezzemolo",
        "orata" to "al forno con pomodorini",
        "trota" to "al cartoccio con erbe",
        "sgombro" to "alla griglia o al forno",
        "salmone" to "al forno o al vapore",
        "tonno" to "al naturale scolato",
        "uova" to "sode o in camicia, senza burro",
        "yogurt" to "freddo, con un cucchiaino di miele se serve",
        "skyr" to "freddo, anche con frutta",
        "ricotta" to "fresca, anche con miele",
        "tofu" to "a cubetti saltato in padella con salsa di soia",
        "ceci" to "lessati, ben scolati",
        "lenticchie" to "lessate 20 minuti, anche in zuppa",
    )
}
