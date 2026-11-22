package dev.tacuchi.hope.ui.importing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.NewAsset
import dev.tacuchi.hope.importing.AudioExtractor
import dev.tacuchi.hope.importing.FrameGrabber
import dev.tacuchi.hope.importing.ImportDecision
import dev.tacuchi.hope.importing.ImportStaging
import dev.tacuchi.hope.importing.MediaInspector
import dev.tacuchi.hope.importing.StagedFile
import dev.tacuchi.hope.importing.audioNameKind
import dev.tacuchi.hope.importing.isLong
import dev.tacuchi.hope.importing.receivedFormatLabel
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

/** Un fotograma sugerido en «Elegir portada». */
data class SuggestedFrame(val positionMs: Long, val thumbnail: Bitmap?)

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

/** Por dónde va una importación. */
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
        val preview: Bitmap?,
        val suggestions: List<SuggestedFrame>,
        val galleryImage: Bitmap?,
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
    private val staging: ImportStaging,
    private val inspector: MediaInspector,
    private val extractor: AudioExtractor,
    private val repository: AssetRepository,
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
    private var extractedAudio: File? = null
    private var extractedDurationMs: Long? = null
    private var galleryFile: StagedFile? = null
    private var grabber: FrameGrabber? = null

    init {
        // Lo que haya quedado de una importación que el sistema cortó.
        viewModelScope.launch(Dispatchers.IO) { staging.discardAll() }
    }

    fun start(uri: Uri, declaredMime: String?, origin: AssetOrigin, purpose: ImportPurpose = ImportPurpose.Catalog) {
        cleanup()
        this.origin = origin
        this.purpose = purpose
        _state.value = ImportState.Preparing
        job = viewModelScope.launch {
            try {
                val newSession = staging.newSession().also { session = it }
                val file = staging.copy(uri, newSession, declaredMime).also { staged = it }
                val decision = ImportDecision.from(inspector.inspect(file.file))
                when {
                    decision is ImportDecision.Unsupported ->
                        _state.value = ImportState.Unsupported(receivedFormatLabel(file.displayName, file.declaredMime))
                    decision.isLong -> {
                        pending = decision
                        _state.value = ImportState.LongWarning(durationOf(decision) ?: 0L)
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
            val bitmap = grabber?.preview(positionMs, PREVIEW_SIDE) ?: return@launch
            _state.update { state ->
                if (state is ImportState.ChoosingCover && state.positionMs == positionMs) state.copy(preview = bitmap) else state
            }
        }
    }

    fun useGalleryImage(uri: Uri) {
        val currentSession = session ?: return
        viewModelScope.launch {
            try {
                val image = staging.copy(uri, staging.newSubfolder(currentSession, "gallery"))
                val bitmap = withContext(Dispatchers.IO) { decodeSampled(image.file, PREVIEW_SIDE) } ?: return@launch
                galleryFile = image
                _state.update { state ->
                    if (state is ImportState.ChoosingCover) state.copy(galleryImage = bitmap) else state
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
        _state.value = ImportState.Saving
        job = viewModelScope.launch {
            try {
                val items = buildList {
                    val audio = extractedAudio
                    if (!current.coverOnly && audio != null) add(extractedAudioAsset(audio))
                    val gallery = galleryFile
                    if (gallery != null) {
                        add(NewAsset(gallery.file, AssetNameKind.Image, AssetOrigin.Gallery, mimeOf(gallery)))
                    } else {
                        val frame = File(currentSession, "frame.jpg")
                        saveFrame(current.positionMs, frame)
                        add(NewAsset(frame, AssetNameKind.Frame, origin, "image/jpeg", frameMs = current.positionMs))
                    }
                }
                finish(repository.add(items))
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
        val audio = extractedAudio
        if (current is ImportState.ChoosingCover && !current.coverOnly && audio != null) {
            _state.value = ImportState.Saving
            job = viewModelScope.launch {
                try {
                    finish(repository.add(listOf(extractedAudioAsset(audio))))
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
            is ImportDecision.Image -> {
                _state.value = ImportState.Saving
                finish(repository.add(listOf(NewAsset(file.file, AssetNameKind.Image, origin, mimeOf(file)))))
            }
            is ImportDecision.Audio -> {
                _state.value = ImportState.Saving
                val kind = audioNameKind(file.file.extension, file.declaredMime)
                finish(repository.add(listOf(NewAsset(file.file, kind, origin, mimeOf(file), durationMs = decision.durationMs))))
            }
            is ImportDecision.VideoWithoutAudio -> _state.value = ImportState.NoAudio
            is ImportDecision.VideoWithAudio -> extract(file.file)
            ImportDecision.Unsupported -> Unit
        }
    }

    private suspend fun extract(video: File) {
        val currentSession = session ?: return
        _state.value = ImportState.Extracting(progress = null)
        val output = File(currentSession, "audio.m4a")
        try {
            extractedDurationMs = extractor.extract(video, output) { progress ->
                if (_state.value is ImportState.Extracting) _state.value = ImportState.Extracting(progress)
            }
            extractedAudio = output
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
        try {
            val frames = withContext(Dispatchers.IO) { FrameGrabber(video) }.also { grabber = it }
            val duration = frames.durationMs
            val positions = (0 until SUGGESTION_COUNT).map { index ->
                duration * (2 * index + 1) / (2 * SUGGESTION_COUNT)
            }
            val first = positions.first()
            _state.value = ImportState.ChoosingCover(
                durationMs = duration,
                positionMs = first,
                preview = frames.preview(first, PREVIEW_SIDE),
                suggestions = positions.map { SuggestedFrame(it, null) },
                galleryImage = null,
                coverOnly = coverOnly,
            )
            val thumbnails = positions.map { SuggestedFrame(it, frames.preview(it, THUMBNAIL_SIDE)) }
            _state.update { state ->
                if (state is ImportState.ChoosingCover) state.copy(suggestions = thumbnails) else state
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fail(error)
        }
    }

    private suspend fun saveFrame(positionMs: Long, target: File) {
        val bitmap = grabber?.full(positionMs) ?: error("Sin fotograma en $positionMs ms")
        withContext(Dispatchers.IO) {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        }
    }

    private fun extractedAudioAsset(audio: File) =
        NewAsset(audio, AssetNameKind.AudioFromVideo, origin, "audio/mp4", durationMs = extractedDurationMs)

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
        grabber?.close()
        grabber = null
        val dir = session
        session = null
        if (running != null && !keepState && running.isActive) {
            // Se borra cuando la extracción ya se detuvo, para que no escriba en una carpeta borrada.
            running.invokeOnCompletion { dir?.let(staging::discard) }
            running.cancel()
        } else {
            dir?.let(staging::discard)
        }
        staged = null
        pending = null
        extractedAudio = null
        extractedDurationMs = null
        galleryFile = null
    }

    override fun onCleared() {
        cleanup()
    }

    private fun durationOf(decision: ImportDecision): Long? = when (decision) {
        is ImportDecision.Audio -> decision.durationMs
        is ImportDecision.VideoWithAudio -> decision.durationMs
        is ImportDecision.VideoWithoutAudio -> decision.durationMs
        else -> null
    }

    private fun mimeOf(file: StagedFile): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.file.extension)
            ?: file.declaredMime
            ?: "application/octet-stream"

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val TAG = "HopeImport"
        private const val SUGGESTION_COUNT = 5
        private const val PREVIEW_SIDE = 720
        private const val THUMBNAIL_SIDE = 192
        private const val PREVIEW_DEBOUNCE_MS = 120L
        private const val JPEG_QUALITY = 90
    }
}
