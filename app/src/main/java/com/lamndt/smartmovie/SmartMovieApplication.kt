package com.lamndt.smartmovie

import android.app.Application

class SmartMovieApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this, BuildConfig.CATALOG_BASE_URL) }
}
