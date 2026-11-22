package dev.tacuchi.hope

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Raíz del grafo de Hilt: las dependencias de la app se declaran en `di/`. */
@HiltAndroidApp
class HopeApplication : Application()
