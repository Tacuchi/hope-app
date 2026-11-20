package dev.tacuchi.hope.importing

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Copia un archivo de prueba de `assets/media` a una carpeta del dispositivo. */
fun testMedia(name: String, into: File): File {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    into.mkdirs()
    val target = File(into, name)
    instrumentation.context.assets.open("media/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
    return target
}
