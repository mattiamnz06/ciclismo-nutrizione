package it.mattia.ciclismo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.mattia.ciclismo.R
import it.mattia.ciclismo.data.Repo

/** Barlow Condensed (OFL) for titles and big numbers, as in the prototype. */
val Condensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

val Lime = Color(0xFFC6F432)
val Graphite = Color(0xFF111418)
val Muted = Color(0xFF9AA4B2)
val Carb = Color(0xFFFFB04A)
val Protein = Color(0xFF6AB7FF)
val Fat = Color(0xFFE8D36A)

private val Dark = darkColorScheme(
    primary = Lime, onPrimary = Graphite,
    background = Graphite, onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF1B2026), onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF2A323B), onSurfaceVariant = Muted,
    secondaryContainer = Color(0xFF2A323B), onSecondaryContainer = Color(0xFFF2F4F7),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4F6B00), onPrimary = Color.White,
    background = Color(0xFFF6F7F9), onBackground = Graphite,
    surface = Color.White, onSurface = Graphite,
    surfaceVariant = Color(0xFFE3E7EC), onSurfaceVariant = Color(0xFF52606D),
)

@Composable
fun CiclismoTheme(content: @Composable () -> Unit) {
    val dark = when (Repo.data.profile.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
