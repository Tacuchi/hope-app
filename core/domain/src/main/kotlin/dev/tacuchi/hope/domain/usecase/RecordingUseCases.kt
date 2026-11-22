package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.media.AudioOutputs
import dev.tacuchi.hope.domain.media.MediaInspector
import dev.tacuchi.hope.domain.media.VoiceRecorder
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.model.backgroundPlaysWhileRecording
import dev.tacuchi.hope.domain.repository.AssetRepository
import java.io.File
import javax.inject.Inject

/** Si el fondo debe sonar mientras se graba: solo con audífonos y si hay fondo. */
class CheckBackgroundWhileRecordingUseCase @Inject constructor(private val outputs: AudioOutputs) {
    operator fun invoke(hasBackground: Boolean): Boolean =
        backgroundPlaysWhileRecording(outputs.headphonesConnected(), hasBackground)
}

/** Empieza una toma nueva. Falla si el micrófono no se puede abrir. */
class StartRecordingUseCase @Inject constructor(private val recorder: VoiceRecorder) {
    operator fun invoke() = recorder.start()
}

/** Detiene la toma y la entrega; nula si quedó vacía. */
class StopRecordingUseCase @Inject constructor(private val recorder: VoiceRecorder) {
    operator fun invoke(): File? = recorder.finish()
}

/** La toma pasa a ser el asset «Grabación N». Si no se puede guardar, la toma se borra. */
class SaveRecordingUseCase @Inject constructor(
    private val recorder: VoiceRecorder,
    private val inspector: MediaInspector,
    private val assets: AssetRepository,
) {
    suspend operator fun invoke(take: File): Asset = try {
        val duration = inspector.inspect(take).durationMs
        assets.add(
            listOf(NewAsset(take, AssetNameKind.Recording, AssetOrigin.Recording, "audio/mp4", durationMs = duration)),
        ).single()
    } catch (error: Exception) {
        recorder.discardTake(take)
        throw error
    }
}

/** Descarta la toma en curso o ya detenida: no deja nada. */
class DiscardRecordingUseCase @Inject constructor(private val recorder: VoiceRecorder) {
    operator fun invoke(take: File?) {
        recorder.discard()
        take?.let(recorder::discardTake)
    }
}

/** Borra los restos de una toma que el sistema cortó. */
class DiscardRecordingLeftoversUseCase @Inject constructor(private val recorder: VoiceRecorder) {
    operator fun invoke() = recorder.discardLeftovers()
}
