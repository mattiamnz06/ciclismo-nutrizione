package it.mattia.libreria.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2EEE7),
    onPrimaryContainer = Color(0xFF0E6B5B),
    secondary = Color(0xFF3D6DB2),
    background = Color(0xFFEDF1F4),
    onBackground = Color(0xFF13202B),
    surface = Color.White,
    onSurface = Color(0xFF13202B),
    surfaceVariant = Color(0xFFE2E8EE),
    onSurfaceVariant = Color(0xFF5B6978),
    outline = Color(0xFFD3DBE3),
    outlineVariant = Color(0xFFD3DBE3),
    error = Color(0xFFC0392B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1B0),
    onPrimary = Color(0xFF07211B),
    primaryContainer = Color(0xFF123A32),
    onPrimaryContainer = Color(0xFF4FD1B0),
    secondary = Color(0xFF86AEEA),
    background = Color(0xFF0C1317),
    onBackground = Color(0xFFE8EEF2),
    surface = Color(0xFF151F26),
    onSurface = Color(0xFFE8EEF2),
    surfaceVariant = Color(0xFF1F2C35),
    onSurfaceVariant = Color(0xFF8C9DAA),
    outline = Color(0xFF26343E),
    outlineVariant = Color(0xFF26343E),
    error = Color(0xFFFF8E80),
)

class Ext(val amber: Color, val amberSoft: Color, val info: Color)

private val LightExt = Ext(Color(0xFFB1721A), Color(0xFFF4E6CC), Color(0xFF3D6DB2))
private val DarkExt = Ext(Color(0xFFE6B452), Color(0xFF3A2E14), Color(0xFF86AEEA))

val LocalExt = staticCompositionLocalOf { LightExt }

/** True quando è impostata un'immagine di sfondo: le schede diventano semitrasparenti. */
val LocalGlass = staticCompositionLocalOf { false }

@Composable
fun LibTheme(dark: Boolean, content: @Composable () -> Unit) {
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    CompositionLocalProvider(LocalExt provides (if (dark) DarkExt else LightExt)) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
