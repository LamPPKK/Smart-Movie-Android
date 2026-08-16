package com.lamndt.smartmovie.multiplatform

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SmartMovie",
        state = rememberWindowState(
            size = DpSize(1360.dp, 860.dp),
            position = WindowPosition.PlatformDefault,
        ),
    ) {
        SmartMovieApp()
    }
}
