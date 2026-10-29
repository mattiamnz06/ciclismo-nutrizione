package it.mattia.ciclismo.engine

/** Values per 100 g (or ml). micro = iron mg, calcium mg, magnesium mg, potassium mg, vitamin C mg. */
class Food(
    val id: String,
    val name: String,
    val kcal: Double,
    val c: Double,
    val p: Double,
    val f: Double,
    val piece: Int = 0,
    val liquid: Boolean = false,
    val micro: DoubleArray = DoubleArray(5),
)

object Foods {
    val microNames = listOf("Ferro", "Calcio", "Magnesio", "Potassio", "Vitamina C")
    val rda = doubleArrayOf(10.0, 1000.0, 375.0, 3400.0, 90.0)

    private fun m(vararg v: Double) = v

    val all: Map<String, Food> = listOf(
        Food("avena", "Fiocchi d'avena", 372.0, 60.0, 13.0, 7.0, micro = m(4.5, 54.0, 177.0, 429.0, 0.0)),
        Food("latte", "Latte parz. scremato", 46.0, 5.0, 3.3, 1.6, liquid = true, micro = m(0.0, 120.0, 11.0, 150.0, 1.0)),
        Food("banana", "Banana", 89.0, 23.0, 1.1, 0.3, piece = 120, micro = m(0.3, 5.0, 27.0, 358.0, 9.0)),
        Food("miele", "Miele", 304.0, 82.0, 0.3, 0.0, micro = m(0.4, 6.0, 2.0, 52.0, 0.5)),
        Food("yogurt", "Yogurt greco 0%", 59.0, 3.6, 10.0, 0.4, micro = m(0.1, 110.0, 11.0, 141.0, 0.0)),
        Food("riso", "Riso basmati (crudo)", 355.0, 78.0, 7.5, 0.9, micro = m(0.8, 10.0, 25.0, 115.0, 0.0)),
        Food("pasta", "Pasta (cruda)", 356.0, 72.0, 12.5, 1.5, micro = m(1.3, 22.0, 53.0, 223.0, 0.0)),
        Food("patate", "Patate", 77.0, 17.0, 2.0, 0.1, micro = m(0.8, 10.0, 23.0, 420.0, 20.0)),
        Food("pane", "Pane", 270.0, 54.0, 8.0, 1.5, micro = m(1.2, 20.0, 25.0, 115.0, 0.0)),
        Food("ceci", "Ceci cotti", 164.0, 27.0, 9.0, 2.6, micro = m(2.9, 49.0, 48.0, 291.0, 1.3)),
        Food("pollo", "Petto di pollo (crudo)", 110.0, 0.0, 23.0, 1.2, micro = m(0.4, 5.0, 28.0, 330.0, 0.0)),
        Food("tacchino", "Fesa di tacchino", 107.0, 0.0, 23.5, 1.5, micro = m(0.7, 10.0, 28.0, 340.0, 0.0)),
        Food("merluzzo", "Merluzzo", 82.0, 0.0, 18.0, 0.7, micro = m(0.4, 15.0, 25.0, 300.0, 1.0)),
        Food("salmone", "Salmone", 185.0, 0.0, 20.0, 12.0, micro = m(0.3, 12.0, 29.0, 363.0, 0.0)),
        Food("tonno", "Tonno al naturale", 110.0, 0.0, 25.0, 1.0, micro = m(1.0, 10.0, 30.0, 250.0, 0.0)),
        Food("uova", "Uova", 143.0, 0.7, 12.6, 9.5, piece = 60, micro = m(1.8, 56.0, 12.0, 138.0, 0.0)),
        Food("zucchine", "Zucchine", 17.0, 3.0, 1.2, 0.3, micro = m(0.4, 16.0, 18.0, 261.0, 17.0)),
        Food("verdure", "Verdure cotte miste", 30.0, 5.0, 2.0, 0.3, micro = m(0.8, 40.0, 20.0, 250.0, 20.0)),
        Food("spinaci", "Spinaci", 23.0, 3.6, 3.0, 0.4, micro = m(3.0, 100.0, 79.0, 466.0, 10.0)),
        Food("olio", "Olio extravergine", 899.0, 0.0, 0.0, 100.0, micro = m(0.4, 1.0, 0.0, 1.0, 0.0)),
        Food("mandorle", "Mandorle", 579.0, 22.0, 21.0, 50.0, micro = m(3.7, 264.0, 270.0, 733.0, 0.0)),
        Food("kiwi", "Kiwi", 61.0, 15.0, 1.1, 0.5, piece = 80, micro = m(0.3, 34.0, 17.0, 312.0, 93.0)),
        Food("mela", "Mela", 52.0, 14.0, 0.3, 0.2, piece = 150, micro = m(0.1, 6.0, 5.0, 107.0, 5.0)),
        Food("arancia", "Arancia", 47.0, 12.0, 0.9, 0.1, piece = 150, micro = m(0.1, 40.0, 10.0, 181.0, 53.0)),
    ).associateBy { it.id }

    fun get(id: String): Food = all.getValue(id)

    /** Short equivalent suggestions shown under "Alternative". */
    val alternatives = mapOf(
        "riso" to "pasta o patate (stessi carboidrati)",
        "pasta" to "riso o patate",
        "patate" to "riso o pasta",
        "avena" to "pane integrale con miele",
        "pane" to "fette biscottate o patate",
        "pollo" to "tacchino, tonno o merluzzo",
        "tacchino" to "pollo, tonno o 2 uova",
        "merluzzo" to "tonno o pollo",
        "salmone" to "tonno o uova (meno grassi: merluzzo)",
        "tonno" to "pollo o tacchino",
        "uova" to "tonno o yogurt greco con fiocchi di latte",
        "yogurt" to "kefir, fiocchi di latte o 2 uova",
        "banana" to "mela o 2 datteri",
        "ceci" to "lenticchie o fagioli",
    )

    val howTo = mapOf(
        "riso" to "riso bianco basmati, non integrale, cotto al dente",
        "pasta" to "pasta di semola, al dente, condita con olio a crudo",
        "patate" to "patate lesse o al forno senza pelle croccante",
        "avena" to "fiocchi in acqua calda o nel latte per 3 minuti",
        "pane" to "pane bianco o semi-integrale, meglio se del giorno",
        "pollo" to "alla piastra o al vapore, poco sale",
        "tacchino" to "alla piastra con limone",
        "merluzzo" to "al forno con limone e prezzemolo",
        "salmone" to "al forno o al vapore",
        "tonno" to "al naturale scolato",
        "uova" to "sode o in camicia, senza burro",
        "yogurt" to "freddo, con un cucchiaino di miele se serve",
        "ceci" to "lessati, ben scolati",
    )
}
