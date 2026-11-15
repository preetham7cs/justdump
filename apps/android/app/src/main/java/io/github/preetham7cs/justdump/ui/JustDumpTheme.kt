package io.github.preetham7cs.justdump.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

private val LightInk = lightColorScheme(
    primary = Color(0xFF8C3D18),
    onPrimary = Color(0xFFFFF8F3),
    primaryContainer = Color(0xFFF7DCCB),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF655A50),
    background = Color(0xFFFFF9F4),
    onBackground = Color(0xFF211A16),
    surface = Color(0xFFFFF9F4),
    onSurface = Color(0xFF211A16),
    surfaceVariant = Color(0xFFF0E7DF),
    onSurfaceVariant = Color(0xFF51463E),
    outline = Color(0xFF81736A),
    error = Color(0xFFAE2F26),
)

private val DarkInk = darkColorScheme(
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF542000),
    primaryContainer = Color(0xFF733000),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFFD5C3B6),
    background = Color(0xFF18120F),
    onBackground = Color(0xFFECE1DA),
    surface = Color(0xFF18120F),
    onSurface = Color(0xFFECE1DA),
    surfaceVariant = Color(0xFF302720),
    onSurfaceVariant = Color(0xFFD9C7BA),
    outline = Color(0xFFA39489),
    error = Color(0xFFFFB4AB),
)

private val JustDumpTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun JustDumpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkInk else LightInk,
        typography = JustDumpTypography,
        content = content,
    )
}
