package it.mattia.libreria.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

const val MAX_WORDS = 600

enum class Status(val key: String, val label: String) {
    READING("reading", "In lettura"),
    TBR("tbr", "Da leggere"),
    DONE("done", "Letto"),
    PAUSED("paused", "In pausa");

    companion object {
        fun of(key: String?): Status = entries.firstOrNull { it.key == key } ?: TBR
    }
}

data class Book(
    val id: String,
    val title: String,
    val pages: Int,
    val page: Int = 0,
    val status: Status = Status.TBR,
    val rating: Int = 0,
    val summary: String = "",
    val started: String? = null,
    val finished: String? = null,
    /** Momento in cui è stata scelta la copertina, 0 se non c'è. */
    val cover: Long = 0L,
    val upd: Long = 0L,
) {
    val percent: Int
        get() = when {
            status == Status.DONE -> 100
            pages <= 0 -> 0
            else -> (page * 100f / pages).roundToInt().coerceIn(0, 100)
        }

    /** Aggiorna la pagina e di conseguenza stato e date. */
    fun setPage(p: Int, today: String): Book {
        val np = p.coerceIn(0, pages)
        var b = copy(page = np, upd = System.currentTimeMillis())
        if (np >= pages) {
            b = b.copy(status = Status.DONE, finished = finished ?: today, started = started ?: today)
        } else {
            if (b.status == Status.DONE) b = b.copy(status = Status.READING, finished = null)
            if (np > 0 && b.status == Status.TBR) b = b.copy(status = Status.READING, started = started ?: today)
        }
        return b
    }

    fun setStatus(s: Status, today: String): Book {
        val now = System.currentTimeMillis()
        return when (s) {
            Status.DONE -> copy(status = s, page = pages, finished = finished ?: today, started = started ?: today, upd = now)
            Status.READING -> copy(status = s, finished = null, started = started ?: today, upd = now)
            Status.TBR -> copy(status = s, finished = null, started = null, page = 0, upd = now)
            Status.PAUSED -> copy(status = s, finished = null, upd = now)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("pages", pages)
        put("page", page)
        put("status", status.key)
        put("rating", rating)
        put("summary", summary)
        put("started", started ?: JSONObject.NULL)
        put("finished", finished ?: JSONObject.NULL)
        put("cover", cover)
        put("upd", upd)
    }

    companion object {
        fun fromJson(o: JSONObject): Book? {
            val title = o.optString("title", "")
            val pages = o.optInt("pages", 0)
            if (title.isBlank() || pages <= 0) return null
            return Book(
                id = o.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() },
                title = title,
                pages = pages,
                page = o.optInt("page", 0).coerceIn(0, pages),
                status = Status.of(o.optString("status", "tbr")),
                rating = o.optInt("rating", 0).coerceIn(0, 10),
                summary = o.optString("summary", ""),
                started = if (o.isNull("started")) null else o.optString("started"),
                finished = if (o.isNull("finished")) null else o.optString("finished"),
                cover = o.optLong("cover", 0L),
                upd = o.optLong("upd", 0L),
            )
        }

        fun listFromJson(a: JSONArray): List<Book> =
            (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
    }
}

fun countWords(s: String): Int = Regex("\\S+").findAll(s).count()

/** Taglia il testo alla parola numero [max]. */
fun limitWords(s: String, max: Int = MAX_WORDS): String {
    val ms = Regex("\\S+").findAll(s).toList()
    return if (ms.size <= max) s else s.substring(0, ms[max - 1].range.last + 1)
}
