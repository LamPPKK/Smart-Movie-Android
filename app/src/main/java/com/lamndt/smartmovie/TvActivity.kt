package com.lamndt.smartmovie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.lamndt.smartmovie.designsystem.SmartMovieTheme

class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = (application as SmartMovieApplication).container
        setContent { SmartMovieTheme { TvApp(container) } }
    }
}
