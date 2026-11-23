package dev.tacuchi.hope.playback

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import dev.tacuchi.hope.importing.testMedia
import org.junit.Assert.assertTrue
import java.io.File

/** Abre reproductores de prueba en el hilo principal, los hace sonar enteros y los libera al final. */
class ProbeSession {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "mix-probe")
    private val players = mutableListOf<ProbePlayer>()

    fun media(name: String): File = testMedia(name, dir)

    fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Una reproducción en curso: se puede pausar, saltar o cambiar el volumen mientras suena. */
    inner class Playing(val run: ProbeRun, private val player: ProbePlayer) {
        fun pause() = onMain { player.pause() }
        fun resume() = onMain { player.play() }
        fun seekTo(ms: Long) = onMain { player.seekTo(ms) }
        val positionMs: Long get() = onMain { player.positionMs }
        val durationMs: Long get() = onMain { player.durationMs }
        val isPlaying: Boolean get() = onMain { player.isPlaying }

        fun waitMs(ms: Long) = Thread.sleep(ms)

        /** Espera a que suene [ms] de la hope; falla si no llega en un tiempo razonable. */
        fun waitForPosition(ms: Long) {
            val deadline = System.currentTimeMillis() + ms + 5_000
            while (System.currentTimeMillis() < deadline && !(isPlaying && positionMs >= ms)) Thread.sleep(10)
            assertTrue("No llegó a los $ms ms", positionMs >= ms)
        }

        internal fun waitUntilEnded() {
            val deadline = System.currentTimeMillis() + (run.mix.durationMs - positionMs).toLong().coerceAtLeast(0) + 5_000
            while (System.currentTimeMillis() < deadline && !onMain { player.ended }) Thread.sleep(50)
            assertTrue("La reproducción no terminó", onMain { player.ended })
        }
    }

    /**
     * Suena [mix] desde el inicio hasta el final y devuelve lo armado, con sus capturas (las de cada
     * pista solo con [captureTracks]). [script] corre mientras suena.
     */
    fun play(mix: ProbeMix, captureTracks: Boolean = true, script: Playing.() -> Unit = {}): ProbeRun {
        val run = ProbeRun(mix, captureTracks)
        val player = onMain { ProbePlayer(context).also { it.start(run) } }
        players += player
        val playing = Playing(run, player)
        playing.script()
        playing.waitUntilEnded()
        return run
    }

    fun close() {
        onMain { players.forEach { it.release() } }
        players.clear()
        dir.deleteRecursively()
    }
}

/**
 * Junta las mediciones de una prueba con lo esperado y su tolerancia, las anota todas en el
 * registro (etiqueta [TAG]) y recién después falla si alguna no entra. Así una medición fuera de
 * rango no esconde las demás.
 */
class Checks(private val title: String) {
    private val lines = mutableListOf<String>()
    private val failures = mutableListOf<String>()

    private fun record(label: String, detail: String, ok: Boolean) {
        val line = "${if (ok) "ok   " else "FALLA"} $label: $detail"
        lines += line
        if (!ok) failures += line
    }

    fun near(label: String, measured: Double, expected: Double, tolerance: Double, unit: String = "") =
        record(label, "medido ${fmt(measured)}$unit, esperado ${fmt(expected)}$unit ± ${fmt(tolerance)}$unit", kotlin.math.abs(measured - expected) <= tolerance)

    fun relative(label: String, measured: Double, expected: Double, fraction: Double, unit: String = "") =
        record(label, "medido ${fmt(measured)}$unit, esperado ${fmt(expected)}$unit ± ${fmt(fraction * 100)} %", relativeError(measured, expected) <= fraction)

    fun atMost(label: String, measured: Double, limit: Double, unit: String = "") =
        record(label, "medido ${fmt(measured)}$unit, máximo ${fmt(limit)}$unit", measured <= limit)

    fun atLeast(label: String, measured: Double, limit: Double, unit: String = "") =
        record(label, "medido ${fmt(measured)}$unit, mínimo ${fmt(limit)}$unit", measured >= limit)

    fun note(label: String, value: String) {
        lines += "nota  $label: $value"
    }

    fun assertAll() {
        android.util.Log.i(TAG, "== $title")
        lines.forEach { android.util.Log.i(TAG, it) }
        assertTrue("$title\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun fmt(value: Double) = when {
        value.isNaN() || value.isInfinite() -> value.toString()
        kotlin.math.abs(value) >= 100 -> "%.1f".format(value)
        else -> "%.4f".format(value)
    }

    companion object {
        const val TAG = "HopeMixProbe"
    }
}

/**
 * Tolerancias de la prueba de audio. Salen de la medición de referencia de la mezcla actual
 * (`MixPlayerTest.theReferenceMixKeepsItsTrimOffsetAndLevels`): el fondo al 30 % midió 0,2992
 * (sesgo de 0,3 %) con un desvío de 0,98 % entre ventanas de 20 ms; cada marca de tiempo llegó
 * entre 18 y 27 ms tarde (el relleno inicial del AAC, que la mezcla no descuenta) y la captura
 * terminó 37 ms después del final nominal.
 */
object Tolerance {
    /** Ganancia medida en una ventana de 20 ms (de 0 a 1): tres desvíos de la referencia, redondeado. */
    const val GAIN = 0.03

    /** Nivel medido en una ventana de 200 ms o más: el desvío baja con la raíz del largo. */
    const val PLATEAU_DB = 0.5

    /** Entrada de la voz y marcas de sincronía: el mismo margen que ya usa la prueba de la mezcla actual. */
    const val ENTRY_MS = 100.0

    /** Final de la hope: la referencia termina 37 ms después del nominal. */
    const val END_MS = 60.0

    /** Tono medido en 0,8 s o más. */
    const val PITCH = 0.003

    /** Lapso entre el primero y el último de un tren de pulsos, medido en ventanas de 1 ms. */
    const val PULSE_SPAN_MS = 10.0

    /** Lo mismo medido dos veces (con y sin salto): solo cambia la ventana de 1 ms y el redondeo. */
    const val SAME_MARK_MS = 5.0

    /** Un tramo vecino no debe sonar: la referencia lo deja en -57,9 dB. */
    const val NEIGHBOR_DB = -40.0

    /** Cola de la sala: la medición con un pulso AAC contra la simulación con ruido blanco. */
    const val RT60 = 0.2

    /** Nivel de la cola 100 ms después del pulso. */
    const val TAIL_DB = 3.0

    /** Respuesta de «voz clara» en un tono. */
    const val RESPONSE_DB = 1.0

    /**
     * Demora del volumen en vivo. La mezcla actual tardó entre 1845 y 2015 ms en cinco corridas de la
     * referencia (`MixPlayerTest.theLiveVolumeReference`): Media3 procesa la mezcla unos 2 s por
     * delante de lo que suena. Margen de 500 ms por la ventana de 100 ms y el reparto de búferes.
     */
    const val LIVE_VOLUME_MS = 2_500.0

    /** Una trama AAC de 1024 muestras a 44,1 kHz (23,2 ms) más 5 ms por la ventana de medición. */
    const val AAC_FRAME_MS = 28.0

    /** Silencio de una pista tras un salto, hasta llegar a la mitad de su nivel: dos tramas AAC de 1024 muestras. */
    const val SEEK_GAP_MS = 50.0

    /** Amplitud de los tonos de los archivos de prueba (la de `sine` de ffmpeg). */
    const val TONE = 0.125

    /** Nivel eficaz de esos tonos. */
    val TONE_RMS = TONE / kotlin.math.sqrt(2.0)
}
