package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.media.AudioExtractor
import dev.tacuchi.hope.domain.media.FrameGrabber
import dev.tacuchi.hope.domain.media.ImportStaging
import dev.tacuchi.hope.domain.media.MediaInspector
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.CoverFrames
import dev.tacuchi.hope.domain.model.ExtractedAudio
import dev.tacuchi.hope.domain.model.ImportDecision
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.model.StagedFile
import dev.tacuchi.hope.domain.model.audioNameKind
import dev.tacuchi.hope.domain.repository.AssetRepository
import java.io.File
import javax.inject.Inject

/** Una importación copiada e inspeccionada, lista para decidir qué sigue. */
data class PreparedImport(val session: File, val staged: StagedFile, val decision: ImportDecision)

/** La portada elegida en «Elegir portada»: una imagen de la galería o un fotograma del video. */
sealed interface CoverChoice {
    data class Gallery(val image: StagedFile) : CoverChoice
    data class Frame(val frames: CoverFrames, val positionMs: Long) : CoverChoice
}

/** Borra lo que haya quedado de una importación que el sistema cortó. */
class DiscardImportLeftoversUseCase @Inject constructor(private val staging: ImportStaging) {
    operator fun invoke() = staging.discardAll()
}

/**
 * Copia lo recibido a una sesión temporal nueva y mira qué es. [onSession] recibe la sesión en
 * cuanto existe, para poder borrarla si se cancela a mitad de la copia.
 */
class PrepareImportUseCase @Inject constructor(
    private val staging: ImportStaging,
    private val inspector: MediaInspector,
) {
    suspend operator fun invoke(uri: String, declaredMime: String?, onSession: (File) -> Unit = {}): PreparedImport {
        val session = staging.newSession().also(onSession)
        val staged = staging.copy(uri, session, declaredMime)
        return PreparedImport(session, staged, ImportDecision.from(inspector.inspect(staged.file)))
    }
}

/** Saca el audio del video de la sesión. */
class ExtractAudioUseCase @Inject constructor(private val extractor: AudioExtractor) {
    suspend operator fun invoke(session: File, video: File, onProgress: (Int?) -> Unit): ExtractedAudio {
        val output = File(session, "audio.m4a")
        return ExtractedAudio(output, extractor.extract(video, output, onProgress))
    }
}

/** Abre los fotogramas del video de la sesión, para elegir portada. */
class OpenFramesUseCase @Inject constructor(private val grabber: FrameGrabber, private val staging: ImportStaging) {
    operator fun invoke(session: File, video: File): CoverFrames =
        CoverFrames(grabber.open(video, staging.newSubfolder(session, "frames")))
}

/** El fotograma en [positionMs], reducido a [maxSide]; nulo si el video no lo da. */
class GetFramePreviewUseCase @Inject constructor() {
    suspend operator fun invoke(frames: CoverFrames, positionMs: Long, maxSide: Int): File? = frames.reader.preview(positionMs, maxSide)
}

class CloseFramesUseCase @Inject constructor() {
    operator fun invoke(frames: CoverFrames) = frames.reader.close()
}

/** Copia una imagen de la galería a la sesión; nula si no es una imagen que se pueda leer. */
class StageGalleryImageUseCase @Inject constructor(
    private val staging: ImportStaging,
    private val inspector: MediaInspector,
) {
    suspend operator fun invoke(session: File, uri: String): StagedFile? {
        val image = staging.copy(uri, staging.newSubfolder(session, "gallery"))
        return image.takeIf { inspector.inspect(it.file).isImage }
    }
}

/** Una imagen o un audio que entra tal cual al catálogo. */
class ConfirmImportedFileUseCase @Inject constructor(private val assets: AssetRepository) {
    suspend operator fun invoke(staged: StagedFile, decision: ImportDecision, origin: AssetOrigin): List<Asset> {
        val item = when (decision) {
            ImportDecision.Image -> NewAsset(staged.file, AssetNameKind.Image, origin, staged.mimeType)
            is ImportDecision.Audio -> NewAsset(
                staged.file,
                audioNameKind(staged.file.extension, staged.declaredMime),
                origin,
                staged.mimeType,
                durationMs = decision.durationMs,
            )
            else -> error("Solo una imagen o un audio entran tal cual: $decision")
        }
        return assets.add(listOf(item))
    }
}

/**
 * Lo que sale de un video: el audio extraído, si lo hay, y la portada elegida, si la hay. Sin
 * portada (cancelar en «Elegir portada») se guarda solo el audio.
 */
class ConfirmVideoImportUseCase @Inject constructor(private val assets: AssetRepository) {
    suspend operator fun invoke(session: File, origin: AssetOrigin, audio: ExtractedAudio?, cover: CoverChoice?): List<Asset> {
        val items = buildList {
            if (audio != null) {
                add(NewAsset(audio.file, AssetNameKind.AudioFromVideo, origin, "audio/mp4", durationMs = audio.durationMs))
            }
            when (cover) {
                is CoverChoice.Gallery ->
                    add(NewAsset(cover.image.file, AssetNameKind.Image, AssetOrigin.Gallery, cover.image.mimeType))
                is CoverChoice.Frame -> {
                    val frame = File(session, "frame.jpg")
                    check(cover.frames.reader.save(cover.positionMs, frame)) { "Sin fotograma en ${cover.positionMs} ms" }
                    add(NewAsset(frame, AssetNameKind.Frame, origin, "image/jpeg", frameMs = cover.positionMs))
                }
                null -> Unit
            }
        }
        return assets.add(items)
    }
}

/** Cancela: borra la sesión temporal y lo que tenga. */
class CancelImportUseCase @Inject constructor(private val staging: ImportStaging) {
    operator fun invoke(session: File) = staging.discard(session)
}
