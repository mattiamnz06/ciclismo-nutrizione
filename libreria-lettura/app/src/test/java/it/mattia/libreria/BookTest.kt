package it.mattia.libreria

import it.mattia.libreria.data.Book
import it.mattia.libreria.data.Status
import it.mattia.libreria.data.countWords
import it.mattia.libreria.data.limitWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookTest {
    private fun book(pages: Int = 200) = Book(id = "1", title = "Titolo", pages = pages)

    @Test fun percentRounds() {
        assertEquals(56, book(560).copy(page = 312).percent)
    }

    @Test fun lastPageMarksDone() {
        val r = book(100).setPage(100, "2026-10-30")
        assertEquals(Status.DONE, r.status)
        assertEquals("2026-10-30", r.finished)
        assertEquals(100, r.percent)
    }

    @Test fun firstPageStartsReading() {
        val r = book().setPage(5, "2026-10-30")
        assertEquals(Status.READING, r.status)
        assertEquals("2026-10-30", r.started)
    }

    @Test fun backingOffReopensBook() {
        val r = book(100).setPage(100, "d1").setPage(90, "d2")
        assertEquals(Status.READING, r.status)
        assertNull(r.finished)
    }

    @Test fun toBeReadResetsProgress() {
        val r = book().setPage(50, "d").setStatus(Status.TBR, "d")
        assertEquals(0, r.page)
        assertNull(r.started)
    }

    @Test fun summaryIsLimitedTo600Words() {
        val text = (1..700).joinToString(" ") { "p$it" }
        assertEquals(600, countWords(limitWords(text)))
        assertEquals("uno due", limitWords("uno due"))
    }
}
