package dev.tacuchi.hope.testing

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** El grafo real trabaja en sus hilos y vuelve al principal: se procesa el principal hasta que se cumpla. */
fun waitUntil(condition: () -> Boolean) {
    repeat(500) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(5)
    }
    error("No se cumplió a tiempo")
}
