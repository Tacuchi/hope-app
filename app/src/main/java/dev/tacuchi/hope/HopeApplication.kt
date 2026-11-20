package dev.tacuchi.hope

import android.app.Application
import android.content.Context

class HopeApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as HopeApplication).container
