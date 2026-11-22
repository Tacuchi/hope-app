package dev.tacuchi.hope.ui.library

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Procesa el hilo principal hasta que se cumpla [condition]: ahí vuelven las corrutinas de la pantalla. */
fun waitUntil(condition: () -> Boolean) {
    repeat(500) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(5)
    }
    error("No se cumplió a tiempo")
}
