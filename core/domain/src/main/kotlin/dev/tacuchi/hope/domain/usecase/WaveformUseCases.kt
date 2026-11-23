package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.media.WaveformSource
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.WaveState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * La onda de un audio: primero «calculando» y después la onda o el error. Al dejar de recoger el
 * flujo (por ejemplo, al cambiar de audio) el cálculo se cancela.
 */
class LoadWaveformUseCase @Inject constructor(private val source: WaveformSource) {
    operator fun invoke(asset: Asset): Flow<WaveState> = flow {
        emit(WaveState.Loading)
        val result = try {
            WaveState.Ready(source.waveform(asset))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            WaveState.Failed
        }
        emit(result)
    }
}
