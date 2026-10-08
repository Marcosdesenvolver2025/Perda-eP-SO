package com.musibox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.musibox.app.core.findActivity
import com.musibox.app.data.prefs.ThemeMode

object MbPalette {
    val Blue = Color(0xFF1E8BFF)
    val BlueBright = Color(0xFF1EC8FF)
    val BlueDeep = Color(0xFF2F6BFF)
    val Purple = Color(0xFF7B4DFF)
    val PurpleBright = Color(0xFF9B6BFF)
    val Red = Color(0xFFFF2D3D)
    val Pink = Color(0xFFFF4D8D)
    val Green = Color(0xFF2ECC71)
    val Orange = Color(0xFFFFA43B)

    val Bg = Color(0xFF07090F)
    val Surface = Color(0xFF0E121B)
    val Card = Color(0xFF121722)
    val CardHigh = Color(0xFF181E2B)
    val Border = Color(0xFF222A3A)
    val TextSecondary = Color(0xFF9AA4B8)
}

@Immutable
data class MbExtraColors(
    val card: Color,
    val cardHigh: Color,
    val border: Color,
    val vault: Color,
    val vaultSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val accentGradient: Brush,
    val vaultGradient: Brush,
    val heroGradient: Brush,
    val isDark: Boolean,
)

private val DarkExtras = MbExtraColors(
    card = MbPalette.Card,
    cardHigh = MbPalette.CardHigh,
    border = MbPalette.Border,
    vault = MbPalette.PurpleBright,
    vaultSoft = Color(0xFF1E1638),
    success = MbPalette.Green,
    warning = MbPalette.Orange,
    danger = Color(0xFFFF5A5F),
    accentGradient = Brush.horizontalGradient(listOf(MbPalette.BlueBright, MbPalette.BlueDeep)),
    vaultGradient = Brush.linearGradient(listOf(MbPalette.Purple, Color(0xFF4F5BFF))),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0B1A3D), Color(0xFF0E3B8C), Color(0xFF1E6BFF))),
    isDark = true,
)

private val LightExtras = MbExtraColors(
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFEFF3FA),
    border = Color(0xFFDCE3EF),
    vault = MbPalette.Purple,
    vaultSoft = Color(0xFFEDE6FF),
    success = Color(0xFF1E9E55),
    warning = Color(0xFFE08A00),
    danger = Color(0xFFD93A3F),
    accentGradient = Brush.horizontalGradient(listOf(MbPalette.Blue, MbPalette.BlueDeep)),
    vaultGradient = Brush.linearGradient(listOf(MbPalette.Purple, Color(0xFF4F5BFF))),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0E3B8C), Color(0xFF1E6BFF), Color(0xFF3FA2FF))),
    isDark = false,
)

private val DarkScheme = darkColorScheme(
    primary = MbPalette.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D2A55),
    onPrimaryContainer = Color.White,
    secondary = MbPalette.PurpleBright,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF241A45),
    onSecondaryContainer = Color.White,
    tertiary = MbPalette.BlueBright,
    background = MbPalette.Bg,
    onBackground = Color.White,
    surface = MbPalette.Bg,
    onSurface = Color.White,
    surfaceVariant = MbPalette.CardHigh,
    onSurfaceVariant = MbPalette.TextSecondary,
    surfaceContainerLowest = MbPalette.Bg,
    surfaceContainerLow = MbPalette.Surface,
    surfaceContainer = MbPalette.Card,
    surfaceContainerHigh = MbPalette.CardHigh,
    surfaceContainerHighest = Color(0xFF1E2533),
    outline = MbPalette.Border,
    outlineVariant = Color(0xFF1A2130),
    error = Color(0xFFFF5A5F),
    onError = Color.White,
    inverseSurface = Color(0xFFE6EAF2),
    inverseOnSurface = Color(0xFF0E121B),
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1570E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF0A2A5A),
    secondary = MbPalette.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE6FF),
    onSecondaryContainer = Color(0xFF241A45),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF0E121B),
    surface = Color(0xFFF5F7FB),
    onSurface = Color(0xFF0E121B),
    surfaceVariant = Color(0xFFEFF3FA),
    onSurfaceVariant = Color(0xFF5A6578),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFF3FA),
    surfaceContainerHighest = Color(0xFFE5EAF3),
    outline = Color(0xFFDCE3EF),
    outlineVariant = Color(0xFFE8EDF5),
    error = Color(0xFFD93A3F),
)

private val base = Typography()

val MbTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall,
)

val MbShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalMbColors = staticCompositionLocalOf { DarkExtras }

object Mb {
    val colors: MbExtraColors
        @Composable get() = LocalMbColors.current
}

val CaptionStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)

@Composable
fun MusiBoxTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = themeMode == ThemeMode.DARK || systemDark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalMbColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = MbTypography,
            shapes = MbShapes,
            content = content,
        )
    }
}
