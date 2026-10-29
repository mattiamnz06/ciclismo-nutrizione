package it.mattia.ciclismo.sync

import it.mattia.ciclismo.engine.Food
import it.mattia.ciclismo.engine.Foods
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Product lookup by barcode on Open Food Facts (values per 100 g or ml). */
object OpenFoodFacts {
    private const val FIELDS = "product_name,product_name_it,brands,quantity,allergens_tags,nutriments"

    // OFF nutrient key -> (raw micro column, factor from grams to the column unit).
    private val MICRO = listOf(
        "iron" to 1e3, "calcium" to 1e3, "magnesium" to 1e3, "potassium" to 1e3, "sodium" to 1e3, "zinc" to 1e3,
        "vitamin-c" to 1e3, "vitamin-d" to 1e6, "vitamin-e" to 1e3, "vitamin-b1" to 1e3, "vitamin-b2" to 1e3,
        "vitamin-pp" to 1e3, "vitamin-b6" to 1e3, "vitamin-b9" to 1e6, "vitamin-b12" to 1e6, "omega-3-fat" to 1.0,
    )

    private val ALLERGENS = mapOf(
        "en:milk" to "lattosio", "en:gluten" to "glutine", "en:fish" to "pesce", "en:crustaceans" to "crostacei",
        "en:molluscs" to "pesce", "en:nuts" to "fruttasecca", "en:peanuts" to "fruttasecca", "en:eggs" to "uova",
        "en:soybeans" to "soia",
    )

    /** Fails when the product is unknown or the network is down; the exception message says which. */
    suspend fun lookup(code: String): Result<Food> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS").openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "CiclismoNutrizione/1.0 (app personale Android)")
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
            val o = JSONObject(body)
            val p = o.optJSONObject("product")
            if (o.optInt("status", 0) != 1 || p == null) throw NoSuchElementException("Prodotto non trovato su Open Food Facts")
            parse(code, p)
        }
    }

    fun parse(code: String, p: JSONObject): Food {
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        fun v(k: String) = n.optDouble("${k}_100g", Double.NaN).takeIf { !it.isNaN() }
        val kcal = v("energy-kcal") ?: v("energy")?.let { it / 4.184 } ?: 0.0
        val name = p.optString("product_name_it").ifBlank { p.optString("product_name") }.ifBlank { "Prodotto $code" }
        val brand = p.optString("brands").substringBefore(',').trim()
        val tags = p.optJSONArray("allergens_tags")
        val allergens = if (tags == null) emptyList() else (0 until tags.length()).mapNotNull { ALLERGENS[tags.getString(it)] }.distinct()
        return Food(
            id = "off_$code",
            name = if (brand.isNotEmpty() && !name.contains(brand, ignoreCase = true)) "$name ($brand)" else name,
            kcal = kcal, c = v("carbohydrates") ?: 0.0, p = v("proteins") ?: 0.0, f = v("fat") ?: 0.0,
            liquid = p.optString("quantity").lowercase().contains("ml") || p.optString("quantity").lowercase().endsWith(" l"),
            micro = DoubleArray(Foods.RAW) { i -> (v(MICRO[i].first) ?: 0.0) * MICRO[i].second },
            category = "I miei alimenti", allergens = allergens, barcode = code,
        )
    }
}
