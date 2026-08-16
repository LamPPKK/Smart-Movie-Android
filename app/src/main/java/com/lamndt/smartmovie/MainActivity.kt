package com.lamndt.smartmovie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.lamndt.smartmovie.designsystem.SmartMovieTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as SmartMovieApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { SmartMovieTheme { SmartMovieApp(container) } }
    }

    override fun onStart() {
        super.onStart()
        container.watchRemote.setPhoneActive(true)
    }

    override fun onStop() {
        container.watchRemote.setPhoneActive(false)
        super.onStop()
    }
}
