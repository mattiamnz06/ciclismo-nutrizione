package it.mattia.libreria.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mattia.libreria.data.Book
import it.mattia.libreria.data.Images
import it.mattia.libreria.data.MAX_WORDS
import it.mattia.libreria.data.Repo
import it.mattia.libreria.data.Status
import it.mattia.libreria.data.Store
import it.mattia.libreria.data.countWords
import it.mattia.libreria.data.limitWords
import it.mattia.libreria.data.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
private fun SheetBody(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) { content() }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (trailing != null) Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun toast(ctx: android.content.Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

/* ---------------------------- Nuovo libro ---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(repo: Repo, onClose: (Status?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(Status.TBR) }
    var cover by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { Images.load(ctx, uri, 600) }
            if (bmp != null) cover = Images.coverFit(bmp) else toast(ctx, "Immagine non valida")
        }
    }
    val n = pages.toIntOrNull() ?: 0
    ModalBottomSheet(onDismissRequest = { onClose(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetBody {
            Text("Nuovo libro", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Label("Titolo")
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Es. Il nome della rosa") }, shape = RoundedCornerShape(14.dp))
            Label("Pagine totali")
            OutlinedTextField(
                pages, { v -> pages = v.filter { it.isDigit() }.take(4) }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Es. 560") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp),
            )
            Label("Copertina (facoltativa)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shown = Book(id = "new", title = title.ifBlank { "?" }, pages = 1)
                if (cover != null) {
                    Image(cover!!.asImageBitmap(), null, Modifier.width(84.dp).height(126.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                } else {
                    Cover(shown, Modifier.width(84.dp).height(126.dp), showPercent = false)
                }
                Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (cover != null) "Cambia foto" else "Scegli foto") }
                    if (cover != null) TextButton(onClick = { cover = null }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
                }
            }
            Label("Stato iniziale")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegButton("Da leggere", status == Status.TBR, { status = Status.TBR }, Modifier.weight(1f))
                SegButton("Inizio ora", status == Status.READING, { status = Status.READING }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    val id = UUID.randomUUID().toString()
                    val b = Book(
                        id = id, title = title.trim(), pages = n.coerceIn(1, 9999), status = status,
                        started = if (status == Status.READING) today() else null, upd = System.currentTimeMillis(),
                    )
                    repo.add(b)
                    cover?.let { repo.setCover(id, it) }
                    toast(ctx, "Libro aggiunto")
                    onClose(status)
                },
                enabled = title.isNotBlank() && n >= 1,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Aggiungi libro", fontWeight = FontWeight.Bold) }
        }
    }
}

/* ---------------------------- Dettaglio ---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(repo: Repo, id: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalExt.current
    val b = repo.find(id)
    if (b == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var pageText by remember(id) { mutableStateOf(b.page.toString()) }
    var sum by remember(id) { mutableStateOf(b.summary) }
    var armed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(b.page) { if (pageText.toIntOrNull() != b.page) pageText = b.page.toString() }
    LaunchedEffect(sum) {
        delay(400)
        val cur = repo.find(id)
        if (cur != null && cur.summary != sum) repo.update(cur.copy(summary = sum))
    }
    fun flushAndClose() {
        repo.find(id)?.let { if (it.summary != sum) repo.update(it.copy(summary = sum)) }
        onClose()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { Images.load(ctx, uri, 600) }
            if (bmp != null) withContext(Dispatchers.IO) { repo.setCover(id, bmp) } else toast(ctx, "Immagine non valida")
        }
    }
    ModalBottomSheet(onDismissRequest = ::flushAndClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(b.percent, 92.dp, 10.dp, MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text(b.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)
                    Text(
                        "${b.pages} pagine · " + if (b.status == Status.DONE) "completato" else "${b.pages - b.page} alla fine",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Label("Copertina")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(b, Modifier.width(84.dp).height(126.dp), showPercent = false)
                Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (b.cover != 0L) "Cambia foto" else "Scegli foto") }
                    if (b.cover != 0L) TextButton(onClick = { repo.setCover(id, null) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
                }
            }

            Label("Pagina attuale", trailing = "di ${b.pages}")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { repo.update(b.setPage(b.page - 1, today())) }, Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−", fontSize = 22.sp) }
                OutlinedTextField(
                    pageText,
                    { v ->
                        val t = v.filter { it.isDigit() }.take(4)
                        pageText = t
                        t.toIntOrNull()?.let { repo.update(b.setPage(it, today())) }
                    },
                    Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold),
                )
                OutlinedButton(onClick = { repo.update(b.setPage(b.page + 1, today())) }, Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+", fontSize = 22.sp) }
            }
            Slider(value = b.page.toFloat(), onValueChange = { repo.update(b.setPage(it.toInt(), today())) }, valueRange = 0f..b.pages.toFloat())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (step in listOf(10, 25, 50)) {
                    FilledTonalButton(onClick = { repo.update(b.setPage(b.page + step, today())) }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+$step") }
                }
            }

            Label("Stato")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegButton(Status.READING.label, b.status == Status.READING, { repo.update(b.setStatus(Status.READING, today())) }, Modifier.weight(1f))
                    SegButton(Status.TBR.label, b.status == Status.TBR, { repo.update(b.setStatus(Status.TBR, today())) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegButton(Status.PAUSED.label, b.status == Status.PAUSED, { repo.update(b.setStatus(Status.PAUSED, today())) }, Modifier.weight(1f))
                    SegButton(Status.DONE.label, b.status == Status.DONE, { repo.update(b.setStatus(Status.DONE, today())) }, Modifier.weight(1f))
                }
            }

            Label("Voto", trailing = if (b.rating > 0) "${b.rating}/10" else "non assegnato")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 1..10) {
                    val sel = r == b.rating
                    val below = r < b.rating
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) ext.amber else if (below) ext.amberSoft else MaterialTheme.colorScheme.background)
                            .clickable { repo.update(b.copy(rating = if (sel) 0 else r)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$r", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sel) MaterialTheme.colorScheme.surface else if (below) ext.amber else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val wc = countWords(sum)
            Label("Il mio riassunto", trailing = "$wc / $MAX_WORDS parole")
            OutlinedTextField(
                sum,
                { v ->
                    val lim = limitWords(v)
                    if (lim != v) toast(ctx, "Limite di $MAX_WORDS parole raggiunto")
                    sum = lim
                },
                Modifier.fillMaxWidth(), minLines = 6, shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Scrivi qui cosa ti è rimasto di questo libro (max $MAX_WORDS parole)") },
            )

            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { Text("Iniziato", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatDate(b.started), fontWeight = FontWeight.SemiBold) }
                Column { Text("Finito", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatDate(b.finished), fontWeight = FontWeight.SemiBold) }
            }

            Spacer(Modifier.height(22.dp))
            Button(onClick = ::flushAndClose, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Fatto", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    if (!armed) armed = true else { repo.remove(id); onClose() }
                },
                Modifier.fillMaxWidth().height(48.dp),
                colors = if (armed) ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (armed) "Tocca ancora per eliminare" else "Elimina libro", fontWeight = FontWeight.Bold) }
        }
    }
}

/* ---------------------------- Impostazioni ---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(repo: Repo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) }

    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { Images.load(ctx, uri, 1600) }
            if (bmp != null) withContext(Dispatchers.IO) { repo.setWall(bmp) } else toast(ctx, "Immagine non valida")
        }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray()) } }.isSuccess }
            toast(ctx, if (ok) "Backup salvato" else "Salvataggio non riuscito")
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportCsv().toByteArray()) } }.isSuccess }
            toast(ctx, if (ok) "Lista esportata" else "Salvataggio non riuscito")
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { repo.importJson(ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }) }.getOrNull() }
            toast(ctx, if (n != null) "Importati $n libri" else "File non valido")
        }
    }

    val wall = remember(repo.wallRev) {
        if (repo.wallRev != 0L) BitmapFactory.decodeFile(Store.wallFile(ctx).path)?.asImageBitmap() else null
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetBody {
            Text("Impostazioni", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

            Label("Aspetto")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegButton("Sistema", repo.themeMode == 0, { repo.setTheme(0) }, Modifier.weight(1f))
                SegButton("Chiaro", repo.themeMode == 1, { repo.setTheme(1) }, Modifier.weight(1f))
                SegButton("Scuro", repo.themeMode == 2, { repo.setTheme(2) }, Modifier.weight(1f))
            }

            Label("Sfondo")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(72.dp).height(108.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    if (wall != null) Image(wall, null, Modifier.width(72.dp).height(108.dp), contentScale = ContentScale.Crop)
                    else Text("Nessuno", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pickWall.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (wall != null) "Cambia immagine" else "Scegli immagine") }
                    if (wall != null) TextButton(onClick = { repo.setWall(null) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (wall != null) {
                Label("Velatura per leggere meglio", trailing = "${repo.wallDim}%")
                Slider(value = repo.wallDim.toFloat(), onValueChange = { repo.setWallDim(it.toInt()) }, valueRange = 0f..85f)
            }
            Text("La foto resta sul telefono. Con la velatura i testi restano leggibili anche su immagini chiare.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

            Label("Dati")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { exportJson.launch("libreria-backup.json") }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salva backup completo (JSON)") }
                FilledTonalButton(onClick = { importJson.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Ripristina da backup") }
                FilledTonalButton(onClick = { exportCsv.launch("libreria-lista.csv") }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Esporta lista (CSV)") }
                TextButton(
                    onClick = { if (!armed) armed = true else { repo.clear(); armed = false; toast(ctx, "Libreria svuotata") } },
                    Modifier.fillMaxWidth().height(48.dp),
                    colors = if (armed) ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(if (armed) "Tocca ancora per svuotare" else "Svuota libreria", fontWeight = FontWeight.Bold) }
            }

            Label("Info")
            Text("${repo.books.size} libri salvati sul telefono · versione 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onClose, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Fatto", fontWeight = FontWeight.Bold) }
        }
    }
}
