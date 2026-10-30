package it.mattia.libreria.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.mattia.libreria.widget.BookWidget
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Lettura e scrittura su file, usata sia dall'app sia dal widget. */
object Store {
    fun file(ctx: Context) = File(ctx.filesDir, "books.json")
    fun wallFile(ctx: Context) = File(ctx.filesDir, "wall.jpg")
    fun coverFile(ctx: Context, id: String) = File(File(ctx.filesDir, "covers"), "$id.jpg")

    fun read(ctx: Context): List<Book> = try {
        val f = file(ctx)
        if (f.exists()) Book.listFromJson(JSONArray(f.readText())) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun write(ctx: Context, books: List<Book>) {
        val tmp = File(ctx.filesDir, "books.json.tmp")
        tmp.writeText(JSONArray(books.map { it.toJson() }).toString())
        tmp.renameTo(file(ctx))
    }
}

fun today(): String = LocalDate.now().toString()

class Repo(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("libreria", Context.MODE_PRIVATE)

    var books by mutableStateOf(Store.read(app))
        private set
    /** 0 = sistema, 1 = chiaro, 2 = scuro. */
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
        private set
    var wallDim by mutableIntStateOf(prefs.getInt("wallDim", 40))
        private set
    var wallRev by mutableLongStateOf(Store.wallFile(app).let { if (it.exists()) it.lastModified() else 0L })
        private set

    private fun commit(list: List<Book>) {
        books = list
        Store.write(app, list)
        BookWidget.refresh(app)
    }

    fun find(id: String): Book? = books.firstOrNull { it.id == id }
    fun add(b: Book) = commit(books + b)
    fun update(b: Book) = commit(books.map { if (it.id == b.id) b else it })

    fun remove(id: String) {
        Store.coverFile(app, id).delete()
        commit(books.filter { it.id != id })
    }

    fun clear() {
        books.forEach { Store.coverFile(app, it.id).delete() }
        commit(emptyList())
    }

    fun setCover(id: String, bmp: Bitmap?) {
        val b = find(id) ?: return
        if (bmp == null) {
            Store.coverFile(app, id).delete()
            update(b.copy(cover = 0L))
        } else {
            Images.saveJpeg(Images.coverFit(bmp), Store.coverFile(app, id), 85)
            update(b.copy(cover = System.currentTimeMillis()))
        }
    }

    fun setTheme(mode: Int) {
        themeMode = mode
        prefs.edit().putInt("theme", mode).apply()
    }

    fun setWallDim(v: Int) {
        wallDim = v
        prefs.edit().putInt("wallDim", v).apply()
    }

    fun setWall(bmp: Bitmap?) {
        val f = Store.wallFile(app)
        if (bmp == null) {
            f.delete()
            wallRev = 0L
        } else {
            Images.saveJpeg(Images.scaleMax(bmp, 1600), f, 85)
            wallRev = System.currentTimeMillis()
        }
    }

    /** Backup completo: libri, riassunti e copertine. */
    fun exportJson(): String {
        val arr = JSONArray()
        for (b in books) {
            val o = b.toJson()
            val cf = Store.coverFile(app, b.id)
            if (b.cover != 0L && cf.exists()) o.put("coverB64", Base64.encodeToString(cf.readBytes(), Base64.NO_WRAP))
            arr.put(o)
        }
        return JSONObject().put("app", "libreria-lettura").put("version", 1).put("books", arr).toString(1)
    }

    /** Sostituisce la libreria con il contenuto del backup. Restituisce i libri importati. */
    fun importJson(text: String): Int {
        val root = text.trim()
        val arr = if (root.startsWith("[")) JSONArray(root) else JSONObject(root).getJSONArray("books")
        val list = ArrayList<Book>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val b = Book.fromJson(o) ?: continue
            var cover = b.cover
            if (o.has("coverB64")) {
                val f = Store.coverFile(app, b.id)
                f.parentFile?.mkdirs()
                f.writeBytes(Base64.decode(o.getString("coverB64"), Base64.DEFAULT))
                if (cover == 0L) cover = System.currentTimeMillis()
            } else {
                cover = 0L
            }
            list.add(b.copy(cover = cover))
        }
        commit(list)
        return list.size
    }

    fun exportCsv(): String {
        fun q(v: Any?) = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""
        val sb = StringBuilder()
        sb.append("Titolo,Pagine,Pagina attuale,Percentuale,Stato,Voto,Inizio,Fine,Riassunto\n")
        for (b in books) {
            sb.append(listOf(b.title, b.pages, b.page, b.percent, b.status.label, if (b.rating > 0) b.rating else "", b.started, b.finished, b.summary).joinToString(",") { q(it) })
            sb.append('\n')
        }
        return sb.toString()
    }
}
