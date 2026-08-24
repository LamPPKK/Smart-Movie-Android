package com.lamndt.smartmovie.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

object CinemaColors {
    val Background = Color(0xFF050508)
    val Elevated = Color(0xFF0E0E12)
    val Surface = Color(0x12FFFFFF)
    val Accent = Color(0xFFE01C47)
    val Gold = Color(0xFFF5B533)
    val Foreground = Color(0xFFF5F7FC)
    val Muted = Color(0xFF9499A8)
    val Success = Color(0xFF58D68D)
}

val Newsreader = FontFamily(
    Font(R.font.newsreader_variable, FontWeight.Normal),
    Font(R.font.newsreader_variable, FontWeight.Bold),
)

val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal),
    Font(R.font.manrope_variable, FontWeight.SemiBold),
    Font(R.font.manrope_variable, FontWeight.Bold),
)

private val CinemaScheme = darkColorScheme(
    primary = CinemaColors.Accent,
    onPrimary = Color.White,
    secondary = CinemaColors.Gold,
    background = CinemaColors.Background,
    onBackground = CinemaColors.Foreground,
    surface = CinemaColors.Elevated,
    onSurface = CinemaColors.Foreground,
    surfaceVariant = CinemaColors.Surface,
    onSurfaceVariant = CinemaColors.Muted,
    error = Color(0xFFFF6B7C),
)

@Composable
fun SmartMovieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CinemaScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 58.sp),
            displayMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Black, fontSize = 42.sp, lineHeight = 46.sp),
            headlineLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = Manrope, fontSize = 17.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontFamily = Manrope, fontSize = 15.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
            labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        ),
        content = content,
    )
}

@Composable
fun isWindowWidthAtLeast(minimumWidthDp: Int): Boolean {
    val width = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { width.toDp() >= minimumWidthDp.dp }
}

@Composable
fun CinemaBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaColors.Background)
            .background(
                Brush.radialGradient(
                    colors = listOf(CinemaColors.Accent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(1100f, 0f),
                    radius = 1200f,
                ),
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0x73070A17)),
                    start = Offset.Zero,
                    end = Offset(900f, 1800f),
                ),
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides CinemaColors.Foreground) { content() }
    }
}
