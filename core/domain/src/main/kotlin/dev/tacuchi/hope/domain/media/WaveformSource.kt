package dev.tacuchi.hope.domain.media

import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.Waveform

/** Calcula la onda de un audio del catálogo, o la toma de la caché si ya se calculó. */
interface WaveformSource {
    /** La onda de [asset], fuera del hilo principal. Falla si el audio no se puede leer. */
    suspend fun waveform(asset: Asset): Waveform
}
