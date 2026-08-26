package com.lamndt.smartmovie

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.lamndt.smartmovie.designsystem.SmartMovieTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as SmartMovieApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleAuthIntent(intent)
        setContent { SmartMovieTheme { SmartMovieApp(container) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.watchRemote.setPhoneActive(true)
    }

    override fun onStop() {
        container.watchRemote.setPhoneActive(false)
        super.onStop()
    }

    private fun handleAuthIntent(intent: Intent?) {
        val attempt = intent?.data?.getQueryParameter("auth_attempt") ?: return
        val locale = resources.configuration.locales[0]
        container.handleAuthCallback(
            attempt,
            com.lamndt.smartmovie.model.CatalogLocale.from(locale.language, locale.country),
        )
    }
}
