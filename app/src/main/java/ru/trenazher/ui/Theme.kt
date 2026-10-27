package ru.trenazher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF0E9594)
val DeepTeal = Color(0xFF0B5D73)
val Amber = Color(0xFFFFB000)

/** Фирменный градиент: шапка, главная кнопка, иконка. */
val BrandGradient = Brush.linearGradient(listOf(Color(0xFF0EA5A4), DeepTeal))

/** Цвета ответов: отдельно от темы, чтобы «верно» и «неверно» читались одинаково везде. */
@Immutable
data class AnswerColors(
    val right: Color,
    val rightContainer: Color,
    val wrong: Color,
    val wrongContainer: Color,
)

private val LightAnswers = AnswerColors(Color(0xFF15803D), Color(0xFFDCF5E3), Color(0xFFC62E3A), Color(0xFFFCE3E5))
private val DarkAnswers = AnswerColors(Color(0xFF5BD68A), Color(0xFF16351F), Color(0xFFFF7A84), Color(0xFF3D1A1E))

val LocalAnswerColors = staticCompositionLocalOf { LightAnswers }

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F1EF),
    onPrimaryContainer = Color(0xFF00393A),
    secondary = Color(0xFF51606A),
    secondaryContainer = Color(0xFFE3ECEE),
    onSecondaryContainer = Color(0xFF162024),
    tertiary = Color(0xFFB87800),
    tertiaryContainer = Color(0xFFFFEFC7),
    onTertiaryContainer = Color(0xFF3B2600),
    background = Color(0xFFF4F7F7),
    onBackground = Color(0xFF111718),
    surface = Color(0xFFF4F7F7),
    onSurface = Color(0xFF111718),
    onSurfaceVariant = Color(0xFF56656A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEAF0F0),
    surfaceContainerHigh = Color(0xFFE2E9EA),
    surfaceContainerHighest = Color(0xFFDAE3E4),
    outline = Color(0xFF8A9A9E),
    outlineVariant = Color(0xFFD5DFE0),
    error = Color(0xFFC62E3A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5FD4CF),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF0E4F55),
    onPrimaryContainer = Color(0xFFC9F3F0),
    secondary = Color(0xFFB7C8CE),
    secondaryContainer = Color(0xFF253237),
    onSecondaryContainer = Color(0xFFD9E6EA),
    tertiary = Color(0xFFFFC85C),
    tertiaryContainer = Color(0xFF4A3300),
    onTertiaryContainer = Color(0xFFFFE6B0),
    background = Color(0xFF0C1113),
    onBackground = Color(0xFFE2EAEC),
    surface = Color(0xFF0C1113),
    onSurface = Color(0xFFE2EAEC),
    onSurfaceVariant = Color(0xFF9FB0B5),
    surfaceContainerLowest = Color(0xFF080C0D),
    surfaceContainerLow = Color(0xFF141B1E),
    surfaceContainer = Color(0xFF182124),
    surfaceContainerHigh = Color(0xFF1E282B),
    surfaceContainerHighest = Color(0xFF263134),
    outline = Color(0xFF6E7F84),
    outlineVariant = Color(0xFF2B373B),
    error = Color(0xFFFF7A84),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Типографика с множителем размера текста из настроек: вопросы должны читаться без напряжения. */
private fun typography(scale: Float): Typography {
    val t = Typography()
    fun s(v: Float) = (v * scale).sp
    return t.copy(
        displaySmall = t.displaySmall.copy(fontSize = s(34f), lineHeight = s(40f), fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontSize = s(28f), lineHeight = s(34f), fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontSize = s(23f), lineHeight = s(30f), fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontSize = s(21f), lineHeight = s(29f), fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = s(17f), lineHeight = s(24f), fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontSize = s(15f), lineHeight = s(21f), fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = s(17f), lineHeight = s(26f)),
        bodyMedium = t.bodyMedium.copy(fontSize = s(15f), lineHeight = s(22f)),
        bodySmall = t.bodySmall.copy(fontSize = s(13f), lineHeight = s(18f)),
        labelLarge = t.labelLarge.copy(fontSize = s(15f), lineHeight = s(20f), fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontSize = s(13f), lineHeight = s(18f), fontWeight = FontWeight.Medium),
    )
}

/** Тема из настроек: системная, светлая или тёмная. */
enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

@Composable
fun TrenazherTheme(mode: ThemeMode = ThemeMode.SYSTEM, textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = if (dark) Dark else Light
    androidx.compose.runtime.CompositionLocalProvider(LocalAnswerColors provides if (dark) DarkAnswers else LightAnswers) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = typography(textScale), content = content)
    }
}
