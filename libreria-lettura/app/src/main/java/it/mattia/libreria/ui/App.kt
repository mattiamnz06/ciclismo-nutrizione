package it.mattia.libreria.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import it.mattia.libreria.data.Repo
import it.mattia.libreria.data.Status
import it.mattia.libreria.data.Store

private sealed interface Sheet {
    data object None : Sheet
    data object Add : Sheet
    data object Settings : Sheet
    data class Detail(val id: String) : Sheet
}

@Composable
fun AppRoot(repo: Repo) {
    val dark = when (repo.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    LibTheme(dark) {
        val ctx = LocalContext.current
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var filterIdx by rememberSaveable { mutableIntStateOf(0) }
        var sheet by remember { mutableStateOf<Sheet>(Sheet.None) }
        val filters = listOf(Status.READING, Status.TBR, Status.DONE, Status.PAUSED)
        val wall = remember(repo.wallRev) {
            if (repo.wallRev != 0L) BitmapFactory.decodeFile(Store.wallFile(ctx).path)?.asImageBitmap() else null
        }
        val bg = MaterialTheme.colorScheme.background

        CompositionLocalProvider(LocalGlass provides (wall != null)) {
            Box(Modifier.fillMaxSize().background(bg)) {
                if (wall != null) {
                    Image(wall, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(bg.copy(alpha = repo.wallDim / 100f)))
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (wall != null) .72f else .92f)) {
                            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(BookIcon, null) }, label = { Text("Libreria") })
                            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(BarsIcon, null) }, label = { Text("Statistiche") })
                        }
                    },
                    floatingActionButton = {
                        if (tab == 0) {
                            ExtendedFloatingActionButton(
                                onClick = { sheet = Sheet.Add },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                icon = { Icon(Icons.Default.Add, null) },
                                text = { Text("Nuovo libro") },
                            )
                        }
                    },
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        if (tab == 0) {
                            LibraryScreen(
                                books = repo.books, filter = filters[filterIdx],
                                onFilter = { filterIdx = filters.indexOf(it) },
                                onOpen = { sheet = Sheet.Detail(it) },
                                onSettings = { sheet = Sheet.Settings },
                            )
                        } else {
                            StatsScreen(repo.books)
                        }
                    }
                }
            }
        }

        when (val s = sheet) {
            Sheet.None -> {}
            Sheet.Add -> AddSheet(repo) { added ->
                if (added != null) filterIdx = filters.indexOf(added)
                sheet = Sheet.None
            }
            Sheet.Settings -> SettingsSheet(repo) { sheet = Sheet.None }
            is Sheet.Detail -> DetailSheet(repo, s.id) { sheet = Sheet.None }
        }
    }
}
