package br.yoofree.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Modo de tema escolhido nas configuracoes. */
enum class ThemeMode(val id: String, val label: String) {
    System("system", "Sistema"),
    Light("light", "Claro"),
    Dark("dark", "Escuro");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

/** Paleta nomeada do app; a UI referencia pelos getters top-level abaixo. */
data class YooFreePalette(
    val bg: Color,
    val panel: Color,
    val panelHi: Color,
    val hairline: Color,
    val textHigh: Color,
    val textLow: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val bad: Color,
)

// Paleta escura: neutros frios com leve tinta violeta, puxada do icone do app.
private val DarkPalette = YooFreePalette(
    bg = Color(0xFF0B0E14),
    panel = Color(0xFF121823),
    panelHi = Color(0xFF1A2231),
    hairline = Color(0xFF232C3B),
    textHigh = Color(0xFFE9EEF6),
    textLow = Color(0xFF93A1B5),
    accent = Color(0xFF8B7CF6),
    onAccent = Color(0xFF0B0E14),
    good = Color(0xFF5BD6A2),
    bad = Color(0xFFF0847C),
)

// Paleta clara: mesmos neutros frios em versao dia, com violeta mais fundo
// pra manter contraste.
private val LightPalette = YooFreePalette(
    bg = Color(0xFFF4F5F9),
    panel = Color(0xFFFFFFFF),
    panelHi = Color(0xFFE9EBF2),
    hairline = Color(0xFFD8DCE6),
    textHigh = Color(0xFF1A2030),
    textLow = Color(0xFF5C6779),
    accent = Color(0xFF6C5CE7),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF1F9D6B),
    bad = Color(0xFFD64545),
)

private val LocalYooFreePalette = staticCompositionLocalOf { DarkPalette }

// Getters top-level: a UI toda continua usando `Bg`, `Accent`, etc. e segue o
// tema ativo sem tocar em cada chamada.
val Bg: Color @Composable get() = LocalYooFreePalette.current.bg
val Panel: Color @Composable get() = LocalYooFreePalette.current.panel
val PanelHi: Color @Composable get() = LocalYooFreePalette.current.panelHi
val Hairline: Color @Composable get() = LocalYooFreePalette.current.hairline
val TextHigh: Color @Composable get() = LocalYooFreePalette.current.textHigh
val TextLow: Color @Composable get() = LocalYooFreePalette.current.textLow
val Accent: Color @Composable get() = LocalYooFreePalette.current.accent
val OnAccent: Color @Composable get() = LocalYooFreePalette.current.onAccent
val Good: Color @Composable get() = LocalYooFreePalette.current.good
val Bad: Color @Composable get() = LocalYooFreePalette.current.bad

private val YooFreeDarkColors = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = DarkPalette.onAccent,
    primaryContainer = Color(0xFF2A2550),
    onPrimaryContainer = Color(0xFFD9D2FF),
    secondary = DarkPalette.textLow,
    onSecondary = DarkPalette.bg,
    background = DarkPalette.bg,
    onBackground = DarkPalette.textHigh,
    surface = DarkPalette.panel,
    onSurface = DarkPalette.textHigh,
    surfaceVariant = DarkPalette.panelHi,
    onSurfaceVariant = DarkPalette.textLow,
    outline = DarkPalette.hairline,
    outlineVariant = DarkPalette.hairline,
    error = DarkPalette.bad,
    onError = DarkPalette.bg,
)

private val YooFreeLightColors = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = LightPalette.onAccent,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF241A57),
    secondary = LightPalette.textLow,
    onSecondary = LightPalette.bg,
    background = LightPalette.bg,
    onBackground = LightPalette.textHigh,
    surface = LightPalette.panel,
    onSurface = LightPalette.textHigh,
    surfaceVariant = LightPalette.panelHi,
    onSurfaceVariant = LightPalette.textLow,
    outline = LightPalette.hairline,
    outlineVariant = LightPalette.hairline,
    error = LightPalette.bad,
    onError = LightPalette.bg,
)

private val YooFreeType = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp,
    ),
)

private val YooFreeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun YooFreeTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    MaterialTheme(
        colorScheme = if (dark) YooFreeDarkColors else YooFreeLightColors,
        typography = YooFreeType,
        shapes = YooFreeShapes,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalYooFreePalette provides palette,
                content = content,
            )
        },
    )
}
