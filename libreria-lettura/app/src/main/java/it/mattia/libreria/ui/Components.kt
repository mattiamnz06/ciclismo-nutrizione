package it.mattia.libreria.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.libreria.data.Book
import it.mattia.libreria.data.Status
import it.mattia.libreria.data.Store

/** Copertina scelta dall'utente oppure una colorata con l'iniziale; con la percentuale sopra. */
@Composable
fun Cover(b: Book, modifier: Modifier = Modifier, showPercent: Boolean = true) {
    val ctx = LocalContext.current
    val bmp = remember(b.id, b.cover) {
        if (b.cover != 0L) BitmapFactory.decodeFile(Store.coverFile(ctx, b.id).path)?.asImageBitmap() else null
    }
    val hue = remember(b.title) { b.title.fold(0) { h, c -> (h * 31 + c.code) % 360 }.toFloat() }
    val initial = remember(b.title) { b.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?" }
    val shape = RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    val bg = Brush.linearGradient(listOf(Color.hsv(hue, .42f, .38f), Color.hsv((hue + 40f) % 360f, .48f, .24f)))
    Box(modifier.clip(shape).background(bg), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initial, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        }
        if (showPercent) {
            val p = b.percent
            Box(
                Modifier.align(Alignment.BottomCenter).padding(start = 5.dp, end = 5.dp, bottom = 9.dp).fillMaxWidth()
                    .clip(CircleShape).background(Color(0xB80A1014)).padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$p%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x47FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p / 100f).background(Color(0xFF5FE0BF)))
            }
        }
    }
}

/** Anello di avanzamento con la percentuale al centro. */
@Composable
fun Ring(percent: Int, size: Dp, stroke: Dp, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset((this.size.width - d) / 2f, (this.size.height - d) / 2f)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            drawArc(color, -90f, 360f * percent / 100f, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        Text("$percent%", fontWeight = FontWeight.ExtraBold, fontSize = (size.value * .26f).sp)
    }
}

@Composable
fun SegButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) cs.primaryContainer else cs.background)
            .border(1.5.dp, if (selected) cs.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) cs.primary else cs.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun StatusTag(s: Status, rating: Int) {
    val ext = LocalExt.current
    val cs = MaterialTheme.colorScheme
    val (bg, fg, text) = when {
        s == Status.DONE && rating > 0 -> Triple(ext.amberSoft, ext.amber, "★ $rating/10")
        s == Status.DONE -> Triple(ext.amberSoft, ext.amber, s.label)
        s == Status.READING -> Triple(cs.primaryContainer, cs.primary, s.label)
        s == Status.TBR -> Triple(ext.info.copy(alpha = .16f), ext.info, s.label)
        else -> Triple(ext.amberSoft, ext.amber, s.label)
    }
    Text(
        text, color = fg, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
