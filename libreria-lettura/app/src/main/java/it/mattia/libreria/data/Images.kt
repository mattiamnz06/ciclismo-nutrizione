package it.mattia.libreria.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object Images {
    /** Legge un'immagine scelta dall'utente, già ridotta e raddrizzata. */
    fun load(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return raw
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    /** Ritaglia al centro in formato 2:3 e porta a [w] x [h]. */
    fun coverFit(src: Bitmap, w: Int = 240, h: Int = 360): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val target = w.toFloat() / h
        val srcAspect = src.width.toFloat() / src.height
        val crop = if (srcAspect > target) {
            val cw = (src.height * target).toInt()
            Rect((src.width - cw) / 2, 0, (src.width + cw) / 2, src.height)
        } else {
            val ch = (src.width / target).toInt()
            Rect(0, (src.height - ch) / 2, src.width, (src.height + ch) / 2)
        }
        Canvas(out).drawBitmap(src, crop, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun scaleMax(src: Bitmap, maxSide: Int): Bitmap {
        val k = min(1f, maxSide.toFloat() / max(src.width, src.height))
        if (k >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
    }

    fun saveJpeg(b: Bitmap, f: File, quality: Int) {
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }
}
