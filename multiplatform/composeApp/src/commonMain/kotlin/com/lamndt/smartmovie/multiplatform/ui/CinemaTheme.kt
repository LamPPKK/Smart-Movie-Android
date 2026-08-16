package com.lamndt.smartmovie.multiplatform.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lamndt.smartmovie.multiplatform.generated.resources.Res
import com.lamndt.smartmovie.multiplatform.generated.resources.manrope_variable
import com.lamndt.smartmovie.multiplatform.generated.resources.newsreader_variable
import org.jetbrains.compose.resources.Font

object CinemaColors {
    val Background = Color(0xFF050508)
    val Elevated = Color(0xFF0E0E12)
    val Surface = Color(0xFF17171E)
    val SurfaceHover = Color(0xFF20202A)
    val Accent = Color(0xFFE01C47)
    val Gold = Color(0xFFF5B533)
    val Foreground = Color(0xFFF5F7FC)
    val Muted = Color(0xFF9499A8)
    val Divider = Color(0x1FFFFFFF)
}

@Composable
private fun newsreader(): FontFamily = FontFamily(
    Font(Res.font.newsreader_variable, FontWeight.Normal),
    Font(Res.font.newsreader_variable, FontWeight.Bold),
)

@Composable
private fun manrope(): FontFamily = FontFamily(
    Font(Res.font.manrope_variable, FontWeight.Normal),
    Font(Res.font.manrope_variable, FontWeight.SemiBold),
    Font(Res.font.manrope_variable, FontWeight.Bold),
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
    outline = CinemaColors.Divider,
    error = Color(0xFFFF7185),
)

@Composable
fun SmartMovieTheme(content: @Composable () -> Unit) {
    val display = newsreader()
    val body = manrope()
    MaterialTheme(
        colorScheme = CinemaScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 66.sp),
            displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 46.sp, lineHeight = 49.sp),
            headlineLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 33.sp),
            titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 30.sp),
            titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
            labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
        ),
        content = content,
    )
}

@Composable
fun CinemaBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaColors.Background)
            .background(
                Brush.radialGradient(
                    colors = listOf(CinemaColors.Accent.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(1350f, -100f),
                    radius = 1350f,
                ),
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0x99070A17)),
                    start = Offset.Zero,
                    end = Offset(900f, 1800f),
                ),
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides CinemaColors.Foreground) {
            content()
        }
    }
}
