package dev.tacuchi.hope.ui.importing

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.CoverFrames
import dev.tacuchi.hope.domain.model.ExtractedAudio
import dev.tacuchi.hope.domain.model.ImportDecision
import dev.tacuchi.hope.domain.model.StagedFile
import dev.tacuchi.hope.domain.model.durationMs
import dev.tacuchi.hope.domain.model.isLong
import dev.tacuchi.hope.domain.model.receivedFormatLabel
import dev.tacuchi.hope.domain.usecase.CancelImportUseCase
import dev.tacuchi.hope.domain.usecase.CloseFramesUseCase
import dev.tacuchi.hope.domain.usecase.ConfirmImportedFileUseCase
import dev.tacuchi.hope.domain.usecase.ConfirmVideoImportUseCase
import dev.tacuchi.hope.domain.usecase.CoverChoice
import dev.tacuchi.hope.domain.usecase.DiscardImportLeftoversUseCase
import dev.tacuchi.hope.domain.usecase.ExtractAudioUseCase
import dev.tacuchi.hope.domain.usecase.GetFramePreviewUseCase
import dev.tacuchi.hope.domain.usecase.OpenFramesUseCase
import dev.tacuchi.hope.domain.usecase.PrepareImportUseCase
import dev.tacuchi.hope.domain.usecase.StageGalleryImageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Un fotograma sugerido en «Elegir portada»; [thumbnail] es su imagen temporal, nula mientras se lee. */
data class SuggestedFrame(val positionMs: Long, val thumbnail: File?)

/** Quién pidió la importación: decide adónde lleva al terminar. */
enum class ImportPurpose {
    /** Desde Assets: al terminar se ve Assets con lo nuevo arriba. */
    Catalog,

    /** Compartido desde otra app: al terminar abre Nueva hope con lo creado. */
    Share,

    /** Desde el editor: lo creado vuelve al editor, a la portada o al fondo. */
    EditorCover,
    EditorBackground,
}

/** Por dónde va una importación. Las imágenes son archivos temporales que dibuja la pantalla. */
sealed interface ImportState {
    data object Idle : ImportState
    data object Preparing : ImportState
    data class Unsupported(val received: String?) : ImportState
    data class LongWarning(val durationMs: Long) : ImportState
    data object NoAudio : ImportState
    data class Extracting(val progress: Int?) : ImportState
    data class ChoosingCover(
        val durationMs: Long,
        val positionMs: Long,
        val preview: File?,
        val suggestions: List<SuggestedFrame>,
        val galleryImage: File?,
        val coverOnly: Boolean,
    ) : ImportState
    data object Saving : ImportState
    data object Failed : ImportState

    /** Terminó y creó [assets]; la navegación los lleva según [purpose] y vuelve a [Idle]. */
    data class Finished(val purpose: ImportPurpose, val assets: List<Asset>) : ImportState
}

/**
 * Una importación de punta a punta: copia, inspección, avisos, extracción y portada. Los assets
 * se escriben solo al final; cancelar en cualquier paso borra la carpeta temporal de la sesión.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    discardLeftovers: DiscardImportLeftoversUseCase,
    private val prepareImport: PrepareImportUseCase,
    private val extractAudio: ExtractAudioUseCase,
    private val openFrames: OpenFramesUseCase,
    private val getFramePreview: GetFramePreviewUseCase,
    private val closeFrames: CloseFramesUseCase,
    private val stageGalleryImage: StageGalleryImageUseCase,
    private val confirmImportedFile: ConfirmImportedFileUseCase,
    private val confirmVideoImport: ConfirmVideoImportUseCase,
    private val cancelImport: CancelImportUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var job: Job? = null
    private var previewJob: Job? = null
    private var session: File? = null
    private var staged: StagedFile? = null
    private var origin: AssetOrigin = AssetOrigin.Shared
    private var purpose: ImportPurpose = ImportPurpose.Catalog
    private var pending: ImportDecision? = null
    private var extracted: ExtractedAudio? = null
    private var galleryFile: StagedFile? = null
    private var frames: CoverFrames? = null

    init {
        // Lo que haya quedado de una importación que el sistema cortó.
        viewModelScope.launch(Dispatchers.IO) { discardLeftovers() }
    }

    fun start(uri: Uri, declaredMime: String?, origin: AssetOrigin, purpose: ImportPurpose = ImportPurpose.Catalog) {
        cleanup()
        this.origin = origin
        this.purpose = purpose
        _state.value = ImportState.Preparing
        job = viewModelScope.launch {
            try {
                val prepared = prepareImport(uri.toString(), declaredMime) { session = it }
                staged = prepared.staged
                val decision = prepared.decision
                when {
                    decision is ImportDecision.Unsupported ->
                        _state.value = ImportState.Unsupported(receivedFormatLabel(prepared.staged.displayName, prepared.staged.declaredMime))
                    decision.isLong -> {
                        pending = decision
                        _state.value = ImportState.LongWarning(decision.durationMs ?: 0L)
                    }
                    else -> proceed(decision)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    /** Sigue después del aviso de más de 10 minutos. */
    fun continueLong() {
        val decision = pending ?: return
        pending = null
        job = viewModelScope.launch { proceed(decision) }
    }

    /** Después del aviso de video sin audio: elegir solo la portada. */
    fun chooseCoverOnly() {
        job = viewModelScope.launch { openCover(coverOnly = true) }
    }

    fun selectPosition(positionMs: Long) {
        val current = _state.value as? ImportState.ChoosingCover ?: return
        _state.value = current.copy(positionMs = positionMs, galleryImage = null)
        galleryFile = null
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val reader = frames ?: return@launch
            val preview = getFramePreview(reader, positionMs, PREVIEW_SIDE) ?: return@launch
            _state.update { state ->
                if (state is ImportState.ChoosingCover && state.positionMs == positionMs) state.copy(preview = preview) else state
            }
        }
    }

    fun useGalleryImage(uri: Uri) {
        val currentSession = session ?: return
        viewModelScope.launch {
            try {
                // Si no es una imagen legible, se queda el fotograma elegido.
                val image = stageGalleryImage(currentSession, uri.toString()) ?: return@launch
                galleryFile = image
                _state.update { state ->
                    if (state is ImportState.ChoosingCover) state.copy(galleryImage = image.file) else state
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // La imagen no se pudo leer: se queda el fotograma elegido.
            }
        }
    }

    /** «Usar»: escribe el audio extraído (si lo hay) y la portada como assets. */
    fun confirmCover() {
        val current = _state.value as? ImportState.ChoosingCover ?: return
        val currentSession = session ?: return
        val reader = frames
        val gallery = galleryFile
        val cover = when {
            gallery != null -> CoverChoice.Gallery(gallery)
            reader != null -> CoverChoice.Frame(reader, current.positionMs)
            else -> return
        }
        val audio = extracted.takeUnless { current.coverOnly }
        _state.value = ImportState.Saving
        job = viewModelScope.launch {
            try {
                finish(confirmVideoImport(currentSession, origin, audio, cover))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    /**
     * Cancela. En «Elegir portada» el audio ya extraído se guarda igual, sin portada; en cualquier
     * otro paso no crea assets y borra los temporales.
     */
    fun cancel() {
        val current = _state.value
        val audio = extracted
        val currentSession = session
        if (current is ImportState.ChoosingCover && !current.coverOnly && audio != null && currentSession != null) {
            _state.value = ImportState.Saving
            job = viewModelScope.launch {
                try {
                    finish(confirmVideoImport(currentSession, origin, audio, cover = null))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    fail(error)
                }
            }
            return
        }
        cleanup()
        _state.value = ImportState.Idle
    }

    /** La navegación ya llevó el resultado a su lugar. */
    fun acknowledgeFinished() {
        if (_state.value is ImportState.Finished) _state.value = ImportState.Idle
    }

    private suspend fun proceed(decision: ImportDecision) {
        val file = staged ?: return
        when (decision) {
            is ImportDecision.Image, is ImportDecision.Audio -> {
                _state.value = ImportState.Saving
                finish(confirmImportedFile(file, decision, origin))
            }
            is ImportDecision.VideoWithoutAudio -> _state.value = ImportState.NoAudio
            is ImportDecision.VideoWithAudio -> extract(file.file)
            ImportDecision.Unsupported -> Unit
        }
    }

    private suspend fun extract(video: File) {
        val currentSession = session ?: return
        _state.value = ImportState.Extracting(progress = null)
        try {
            extracted = extractAudio(currentSession, video) { progress ->
                if (_state.value is ImportState.Extracting) _state.value = ImportState.Extracting(progress)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fail(error)
            return
        }
        openCover(coverOnly = false)
    }

    private suspend fun openCover(coverOnly: Boolean) {
        val video = staged?.file ?: return
        val currentSession = session ?: return
        try {
            val reader = withContext(Dispatchers.IO) { openFrames(currentSession, video) }.also { frames = it }
            val duration = reader.durationMs
            val positions = (0 until SUGGESTION_COUNT).map { index ->
                duration * (2 * index + 1) / (2 * SUGGESTION_COUNT)
            }
            val first = positions.first()
            _state.value = ImportState.ChoosingCover(
                durationMs = duration,
                positionMs = first,
                preview = getFramePreview(reader, first, PREVIEW_SIDE),
                suggestions = positions.map { SuggestedFrame(it, null) },
                galleryImage = null,
                coverOnly = coverOnly,
            )
            val thumbnails = positions.map { SuggestedFrame(it, getFramePreview(reader, it, THUMBNAIL_SIDE)) }
            _state.update { state ->
                if (state is ImportState.ChoosingCover) state.copy(suggestions = thumbnails) else state
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun finish(created: List<Asset>) {
        cleanup(keepState = true)
        _state.value = ImportState.Finished(purpose, created)
    }

    private fun fail(error: Exception) {
        Log.w(TAG, "La importación falló", error)
        cleanup(keepState = true)
        _state.value = ImportState.Failed
    }

    /** Cierra lo abierto y borra la sesión temporal. */
    private fun cleanup(keepState: Boolean = false) {
        val running = job
        job = null
        previewJob?.cancel()
        frames?.let(closeFrames::invoke)
        frames = null
        val dir = session
        session = null
        if (running != null && !keepState && running.isActive) {
            // Se borra cuando la extracción ya se detuvo, para que no escriba en una carpeta borrada.
            running.invokeOnCompletion { dir?.let(cancelImport::invoke) }
            running.cancel()
        } else {
            dir?.let(cancelImport::invoke)
        }
        staged = null
        pending = null
        extracted = null
        galleryFile = null
    }

    override fun onCleared() {
        cleanup()
    }

    companion object {
        private const val TAG = "HopeImport"
        private const val SUGGESTION_COUNT = 5
        private const val PREVIEW_SIDE = 720
        private const val THUMBNAIL_SIDE = 192
        private const val PREVIEW_DEBOUNCE_MS = 120L
    }
}
