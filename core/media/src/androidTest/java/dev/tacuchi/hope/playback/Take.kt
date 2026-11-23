package dev.tacuchi.hope.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un tramo continuo de audio capturado, en mono. Los instantes se dan en milisegundos de la hope
 * (o de la pista, si se capturó una pista). Las mediciones de tono usan una ventana de Hann: la
 * amplitud que devuelven es la de un seno de esa frecuencia, en la escala de la muestra (1 = 0 dBFS).
 */
class Take(val startUs: Long, val sampleRate: Int, val samples: FloatArray) {
    val startMs: Double get() = startUs / 1000.0
    val endMs: Double get() = startMs + samples.size * 1000.0 / sampleRate

    private fun index(ms: Double): Int = ((ms - startMs) * sampleRate / 1000.0).roundToInt().coerceIn(0, samples.size)

    fun rms(fromMs: Double, toMs: Double): Double {
        val from = index(fromMs)
        val to = index(toMs)
        if (to <= from) return 0.0
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i].toDouble()
        return sqrt(sum / (to - from))
    }

    /** Amplitud del seno de [hz] entre [fromMs] y [toMs]. */
    fun tone(hz: Double, fromMs: Double, toMs: Double): Double {
        val from = index(fromMs)
        val to = index(toMs)
        val n = to - from
        if (n < 8) return 0.0
        val step = 2 * PI * hz / sampleRate
        var re = 0.0
        var im = 0.0
        var weights = 0.0
        for (k in 0 until n) {
            val w = 0.5 - 0.5 * cos(2 * PI * k / (n - 1))
            val x = samples[from + k] * w
            val phase = step * (from + k)
            re += x * cos(phase)
            im -= x * sin(phase)
            weights += w
        }
        return 2 * sqrt(re * re + im * im) / weights
    }

    /** Amplitud del seno de [hz] en una ventana de [windowMs] centrada en [atMs]. */
    fun toneAt(hz: Double, atMs: Double, windowMs: Double = 20.0): Double =
        tone(hz, atMs - windowMs / 2, atMs + windowMs / 2)

    /**
     * Primer instante en que el seno de [hz] llega a [threshold]. Con una ventana centrada, un tono
     * que empieza de golpe mide la mitad de su amplitud justo en su comienzo: con un umbral de la
     * mitad del nivel estable, el resultado es su primer instante audible.
     */
    fun toneOnset(hz: Double, threshold: Double, fromMs: Double, toMs: Double, windowMs: Double = 20.0): Double? {
        var t = maxOf(fromMs, startMs + windowMs / 2)
        val last = minOf(toMs, endMs - windowMs / 2)
        while (t <= last) {
            if (toneAt(hz, t, windowMs) >= threshold) return t
            t += 1.0
        }
        return null
    }

    /**
     * Último instante en que el seno de [hz] llega a [threshold]: con la mitad del nivel, donde
     * termina. Solo mira ventanas completas: si suena hasta el final del tramo, devuelve el final
     * menos media ventana.
     */
    fun toneEnd(hz: Double, threshold: Double, fromMs: Double, toMs: Double, windowMs: Double = 20.0): Double? {
        var t = minOf(toMs, endMs - windowMs / 2)
        val first = maxOf(fromMs, startMs + windowMs / 2)
        while (t >= first) {
            if (toneAt(hz, t, windowMs) >= threshold) return t
            t -= 1.0
        }
        return null
    }

    /** Frecuencia del seno más fuerte entre [loHz] y [hiHz], con un barrido fino y ajuste parabólico. */
    fun peakHz(fromMs: Double, toMs: Double, loHz: Double, hiHz: Double, stepHz: Double = 0.25): Double {
        val freqs = generateSequence(loHz) { it + stepHz }.takeWhile { it <= hiHz }.toList()
        val levels = freqs.map { tone(it, fromMs, toMs) }
        val best = levels.indices.maxBy { levels[it] }
        if (best == 0 || best == levels.lastIndex) return freqs[best]
        val (a, b, c) = Triple(levels[best - 1], levels[best], levels[best + 1])
        val shift = 0.5 * (a - c) / (a - 2 * b + c)
        return freqs[best] + shift * stepHz
    }

    /** Frecuencia de un tono solo, por sus cruces por cero ascendentes. */
    fun pitchHz(fromMs: Double, toMs: Double): Double {
        val from = index(fromMs)
        val to = index(toMs)
        var first = -1.0
        var last = -1.0
        var count = 0
        for (i in from + 1 until to) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a < 0 && b >= 0) {
                val crossing = i - 1 + a / (a - b).toDouble()
                if (first < 0) first = crossing
                last = crossing
                count++
            }
        }
        return if (count < 2) 0.0 else (count - 1) * sampleRate / (last - first)
    }

    /** Nivel en ventanas de [frameMs], para seguir pulsos. */
    private fun frames(fromMs: Double, toMs: Double, frameMs: Double): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var t = fromMs
        while (t + frameMs <= toMs) {
            result += t to rms(t, t + frameMs)
            t += frameMs
        }
        return result
    }

    /**
     * Comienzo del pulso que se espera cerca de [expectedMs]: el primer milisegundo en que el nivel
     * llega a la mitad del máximo de esa zona. No depende del nivel absoluto del pulso, así sirve
     * con fundidos, bajadas y salas.
     */
    fun onsetNear(expectedMs: Double, searchMs: Double = 150.0): Double {
        val frames = frames(expectedMs - searchMs, expectedMs + searchMs, 1.0)
        val peak = frames.maxOf { it.second }
        return frames.first { it.second >= peak / 2 }.first
    }

    /** Comienzos de los pulsos que pasan [threshold], con al menos [gapMs] entre uno y otro. */
    fun pulseOnsets(threshold: Double, fromMs: Double, toMs: Double, gapMs: Double = 200.0): List<Double> {
        val onsets = mutableListOf<Double>()
        var last = Double.NEGATIVE_INFINITY
        var above = false
        for ((t, level) in frames(fromMs, toMs, 1.0)) {
            if (!above && level >= threshold && t - last >= gapMs) {
                onsets += t
                last = t
            }
            above = level >= threshold
        }
        return onsets
    }

    /**
     * Tiempo de reverberación (RT60) de la cola entre [fromMs] y [toMs], con la integración de
     * Schroeder: lo que tarda la energía restante en bajar de -5 a -25 dB, por tres. NaN si no baja
     * lo suficiente en ese tramo.
     */
    fun rt60(fromMs: Double, toMs: Double): Double {
        val from = index(fromMs)
        val to = index(toMs)
        val energy = DoubleArray(to - from)
        var acc = 0.0
        for (i in to - 1 downTo from) {
            acc += samples[i] * samples[i].toDouble()
            energy[i - from] = acc
        }
        val total = energy.firstOrNull() ?: return Double.NaN
        val t5 = energy.indexOfFirst { 10 * log10(it / total) <= -5 }
        val t25 = energy.indexOfFirst { 10 * log10(it / total) <= -25 }
        if (t5 < 0 || t25 < 0) return Double.NaN
        return 3.0 * (t25 - t5) / sampleRate
    }
}

/** Decibelios de una proporción de amplitudes. */
fun Double.db(): Double = 20 * log10(this)

/** La proporción de amplitudes que corresponde a [this] decibelios. */
fun Double.fromDb(): Double = Math.pow(10.0, this / 20)

/** Desvío relativo entre dos valores, para las tolerancias en porcentaje. */
fun relativeError(measured: Double, expected: Double): Double = abs(measured - expected) / expected
