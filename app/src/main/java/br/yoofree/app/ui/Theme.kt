package br.yoofree.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta: neutros frios com leve tinta violeta, puxada do icone do app.
val Bg = Color(0xFF0B0E14)
val Panel = Color(0xFF121823)
val PanelHi = Color(0xFF1A2231)
val Hairline = Color(0xFF232C3B)
val TextHigh = Color(0xFFE9EEF6)
val TextLow = Color(0xFF93A1B5)
val Accent = Color(0xFF8B7CF6)
val OnAccent = Color(0xFF0B0E14)
val Good = Color(0xFF5BD6A2)
val Bad = Color(0xFFF0847C)

private val YooFreeColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Color(0xFF2A2550),
    onPrimaryContainer = Color(0xFFD9D2FF),
    secondary = TextLow,
    onSecondary = Bg,
    background = Bg,
    onBackground = TextHigh,
    surface = Panel,
    onSurface = TextHigh,
    surfaceVariant = PanelHi,
    onSurfaceVariant = TextLow,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Bad,
    onError = Bg,
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
fun YooFreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YooFreeColors,
        typography = YooFreeType,
        shapes = YooFreeShapes,
        content = content,
    )
}
