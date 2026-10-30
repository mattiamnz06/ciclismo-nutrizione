package it.mattia.libreria.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import it.mattia.libreria.data.Book
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Disegna il widget come immagine: biscotto dai bordi ondulati, acquerello a macchie,
 * anello di lettura a pennellate. I colori vengono dal tema dinamico del telefono
 * (Material You) e, dove non c'è, dal verde petrolio.
 */
object WidgetArt {
    private class Pal(val c10: Int, val c100: Int, val c200: Int, val c300: Int, val c400: Int, val c600: Int, val c800: Int, val c900: Int)

    private fun pal(ctx: Context): Pal =
        if (Build.VERSION.SDK_INT >= 31) {
            fun c(id: Int) = ctx.getColor(id)
            Pal(
                c(android.R.color.system_accent1_10), c(android.R.color.system_accent1_100), c(android.R.color.system_accent1_200),
                c(android.R.color.system_accent1_300), c(android.R.color.system_accent1_400), c(android.R.color.system_accent1_600),
                c(android.R.color.system_accent1_800), c(android.R.color.system_accent1_900),
            )
        } else {
            fun p(s: String) = Color.parseColor(s)
            Pal(p("#F2F8F7"), p("#CFE5E2"), p("#A3CDC8"), p("#6FB0AA"), p("#3F918B"), p("#1F6F6B"), p("#0F4A47"), p("#0A3533"))
        }

    private fun cookie(cx: Float, cy: Float, r: Float, amp: Float, lobes: Int): Path {
        val path = Path()
        val n = 240
        for (i in 0..n) {
            val a = 2.0 * Math.PI * i / n
            val rr = r + amp * cos(lobes * a).toFloat()
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    fun render(ctx: Context, book: Book?, s: Int = 480): Bitmap {
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = pal(ctx)
        val rnd = Random((book?.id ?: "vuoto").hashCode().toLong())
        val sf = s.toFloat()
        val cx = sf / 2f
        val cy = sf / 2f
        val shape = cookie(cx, cy, sf * .465f, sf * .03f, 12)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = p.c10
        c.drawPath(shape, paint)

        // macchie d'acquerello dentro il biscotto
        c.save()
        c.clipPath(shape)
        val tones = intArrayOf(p.c200, p.c300, p.c100, p.c200)
        for (i in 0 until 9) {
            val x = rnd.nextFloat() * sf
            val y = rnd.nextFloat() * sf
            val r = sf * (.16f + rnd.nextFloat() * .2f)
            val col = tones[i % tones.size]
            paint.shader = RadialGradient(x, y, r, ColorUtils.setAlphaComponent(col, 120 + rnd.nextInt(60)), ColorUtils.setAlphaComponent(col, 0), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, paint)
        }
        paint.shader = null
        // bordo più scuro dove il pigmento si raccoglie
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sf * .03f
        paint.color = ColorUtils.setAlphaComponent(p.c300, 150)
        paint.maskFilter = BlurMaskFilter(sf * .012f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(shape, paint)
        paint.maskFilter = null
        // grana della carta
        paint.style = Paint.Style.FILL
        for (i in 0 until 3500) {
            paint.color = ColorUtils.setAlphaComponent(p.c800, 8 + rnd.nextInt(14))
            c.drawCircle(rnd.nextFloat() * sf, rnd.nextFloat() * sf, 0.8f + rnd.nextFloat() * 1.2f, paint)
        }
        c.restore()

        // contorno a mano, un po' doppio
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sf * .009f
        paint.color = ColorUtils.setAlphaComponent(p.c400, 190)
        c.drawPath(shape, paint)
        paint.strokeWidth = sf * .004f
        paint.color = ColorUtils.setAlphaComponent(p.c800, 110)
        c.save(); c.translate(sf * .004f, sf * .003f); c.drawPath(shape, paint); c.restore()

        // titolo
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = p.c900
        tp.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        tp.textSize = sf * .072f
        val title = book?.title ?: "Nessun libro in lettura"
        val w = (sf * .58f).toInt()
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, tp, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.translate(cx - w / 2f, sf * .15f)
        layout.draw(c)
        c.restore()

        // anello di lettura a pennellate
        val rcx = cx
        val rcy = sf * .6f
        val rr = sf * .165f
        val stroke = sf * .075f
        val pct = book?.percent ?: 0
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke
        paint.color = p.c100
        c.drawCircle(rcx, rcy, rr, paint)
        if (book != null && pct > 0) {
            val sweep = 360f * pct / 100f
            val passes = arrayOf(floatArrayOf(1.25f, 70f), floatArrayOf(1.0f, 150f), floatArrayOf(.72f, 235f))
            for (pass in passes) {
                paint.strokeWidth = stroke * pass[0]
                paint.color = ColorUtils.setAlphaComponent(p.c600, pass[1].toInt())
                val jx = (rnd.nextFloat() - .5f) * sf * .012f
                val jy = (rnd.nextFloat() - .5f) * sf * .012f
                c.drawArc(RectF(rcx - rr + jx, rcy - rr + jy, rcx + rr + jx, rcy + rr + jy), -90f, sweep, false, paint)
            }
            // fili di pennello più scuri
            paint.strokeWidth = sf * .004f
            for (i in 0 until 14) {
                paint.color = ColorUtils.setAlphaComponent(p.c800, 40 + rnd.nextInt(50))
                val off = (rnd.nextFloat() - .5f) * stroke * .8f
                c.drawArc(RectF(rcx - rr - off, rcy - rr - off, rcx + rr + off, rcy + rr + off), -90f + rnd.nextFloat() * 6f, sweep * (.6f + rnd.nextFloat() * .4f), false, paint)
            }
        }
        paint.strokeCap = Paint.Cap.BUTT

        // percentuale e pagine
        val pp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        pp.color = p.c800
        pp.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        pp.textSize = sf * .088f
        pp.textAlign = Paint.Align.CENTER
        val label = if (book != null) "$pct%" else "+"
        c.drawText(label, rcx, rcy + pp.textSize * .34f, pp)
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG)
        sub.color = p.c600
        sub.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        sub.textSize = sf * .045f
        sub.textAlign = Paint.Align.CENTER
        c.drawText(if (book != null) "${book.page} / ${book.pages} pagine" else "Aggiungi un libro", cx, sf * .875f, sub)
        return bmp
    }
}
