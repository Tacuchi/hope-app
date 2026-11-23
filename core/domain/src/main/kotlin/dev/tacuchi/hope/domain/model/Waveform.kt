package dev.tacuchi.hope.domain.model

/**
 * La forma de onda de un audio: el pico de cada intervalo, de 0 a 1, de principio a fin del
 * archivo. Tiene siempre pocos valores, sea cual sea el largo del audio.
 */
class Waveform(val peaks: FloatArray, val durationMs: Long) {
    /** El pico del intervalo que contiene [ms]. */
    fun peakAt(ms: Long): Float {
        if (peaks.isEmpty() || durationMs <= 0) return 0f
        val index = (ms.coerceIn(0, durationMs - 1) * peaks.size / durationMs).toInt()
        return peaks[index.coerceIn(0, peaks.lastIndex)]
    }

    override fun equals(other: Any?): Boolean =
        other is Waveform && durationMs == other.durationMs && peaks.contentEquals(other.peaks)

    override fun hashCode(): Int = 31 * peaks.contentHashCode() + durationMs.hashCode()

    companion object {
        /** Cuántos intervalos tiene toda onda: alcanza para dibujarla a lo ancho de una pantalla. */
        const val BINS = 600
    }
}

/** La onda de un asset mientras se calcula, cuando está y si no se pudo calcular. */
sealed interface WaveState {
    data object Loading : WaveState

    data class Ready(val wave: Waveform) : WaveState

    data object Failed : WaveState
}
