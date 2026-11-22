package dev.tacuchi.hope.ui.editor

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkPreview
import dev.tacuchi.hope.domain.model.MixPlayback
import dev.tacuchi.hope.domain.model.MixPresentation
import dev.tacuchi.hope.domain.model.MixSpec
import dev.tacuchi.hope.domain.usecase.CheckBackgroundWhileRecordingUseCase
import dev.tacuchi.hope.domain.usecase.DiscardRecordingLeftoversUseCase
import dev.tacuchi.hope.domain.usecase.DiscardRecordingUseCase
import dev.tacuchi.hope.domain.usecase.GetAssetUseCase
import dev.tacuchi.hope.domain.usecase.GetHopeUseCase
import dev.tacuchi.hope.domain.usecase.GetLinkPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetsUseCase
import dev.tacuchi.hope.domain.usecase.ObservePlaybackUseCase
import dev.tacuchi.hope.domain.usecase.PlayMixUseCase
import dev.tacuchi.hope.domain.usecase.RecognizeLinkUseCase
import dev.tacuchi.hope.domain.usecase.SaveHopeUseCase
import dev.tacuchi.hope.domain.usecase.SaveRecordingUseCase
import dev.tacuchi.hope.domain.usecase.StartRecordingUseCase
import dev.tacuchi.hope.domain.usecase.StopMixUseCase
import dev.tacuchi.hope.domain.usecase.StopRecordingUseCase
import dev.tacuchi.hope.domain.usecase.ToggleMixUseCase
import dev.tacuchi.hope.domain.usecase.UpdateMixUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Con qué abre el editor: una hope guardada, o assets y un enlace que ya vienen puestos. */
data class EditorArgs(
    val hopeId: Long? = null,
    val coverAssetId: Long? = null,
    val backgroundAssetId: Long? = null,
    val linkUrl: String? = null,
)

/** Un asset de la hoja de elegir, con su archivo para la miniatura. */
data class PickableAsset(val asset: Asset, val file: File)

/** Qué hueco del editor pidió un asset. */
enum class EditorSlot { Cover, Background }

/** La toma en curso de la hoja «Grabando voz». */
data class RecordingUi(
    val elapsedMs: Long = 0,
    /** Con audífonos el fondo suena; por el altavoz se graba en silencio. */
    val backgroundPlays: Boolean,
    val hasBackground: Boolean,
    /** Ya se detuvo y espera «Listo» o «Descartar». */
    val stopped: Boolean = false,
    val saving: Boolean = false,
)

/** Lo que se edita: se guarda tal cual en la hope. */
data class EditorDraft(
    val title: String = "",
    val cover: Asset? = null,
    val background: Asset? = null,
    val voice: Asset? = null,
    val backgroundVolume: Int = 100,
    val voiceVolume: Int = 100,
    val trimStartMs: Long = 0,
    val trimEndMs: Long? = null,
    val voiceOffsetMs: Long = 0,
    /** Lo escrito en «Enlace de origen»; vacío es sin enlace. */
    val linkText: String = "",
    val linkPreview: LinkPreview? = null,
    /** El enlace reconocido en [linkText], o nulo si el campo está vacío o no es un enlace. */
    val link: Link? = null,
) {
    val linkInvalid: Boolean get() = linkText.isNotBlank() && link == null

    /** La vista previa solo vale para el enlace que se ve ahora. */
    val currentPreview: LinkPreview? get() = linkPreview?.takeIf { it.url == link?.url }
    val titleMissing: Boolean get() = title.isBlank()
    val tracksMissing: Boolean get() = background == null && voice == null
    val canSave: Boolean get() = !titleMissing && !tracksMissing && !linkInvalid
}

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: EditorDraft = EditorDraft(),
    val coverFile: File? = null,
    /** Lo que suena desde el editor: la mezcla o solo el fondo. */
    val playback: EditorPlayback = EditorPlayback.Idle,
    val positionMs: Long = 0,
    /** Se intentó guardar sin título o sin pistas: se marcan los faltantes. */
    val showErrors: Boolean = false,
    val recording: RecordingUi? = null,
    val micDenied: Boolean = false,
    val recordingFailed: Boolean = false,
    val saved: Boolean = false,
    /** El catálogo para la hoja de elegir; nulo mientras se lee, vacío si no hay assets. */
    val catalog: List<PickableAsset>? = null,
)

enum class EditorPlayback { Idle, Mix, Background }

/**
 * El editor de una hope nueva o guardada. Escuchar usa el mismo reproductor que la biblioteca
 * con los mismos ajustes, así lo que se oye aquí es lo que suena allá. [args] llega de la ruta
 * restaurable del destino.
 */
@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel @AssistedInject constructor(
    @Assisted private val args: EditorArgs,
    observeAssets: ObserveAssetsUseCase,
    observePlayback: ObservePlaybackUseCase,
    discardRecordingLeftovers: DiscardRecordingLeftoversUseCase,
    private val getHope: GetHopeUseCase,
    private val getAsset: GetAssetUseCase,
    private val saveHope: SaveHopeUseCase,
    private val recognizeLink: RecognizeLinkUseCase,
    private val getLinkPreview: GetLinkPreviewUseCase,
    private val toggleMix: ToggleMixUseCase,
    private val playMix: PlayMixUseCase,
    private val updateMix: UpdateMixUseCase,
    private val stopMix: StopMixUseCase,
    private val checkBackgroundWhileRecording: CheckBackgroundWhileRecordingUseCase,
    private val startVoiceTake: StartRecordingUseCase,
    private val stopVoiceTake: StopRecordingUseCase,
    private val saveVoiceTake: SaveRecordingUseCase,
    private val discardVoiceTake: DiscardRecordingUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: EditorArgs): EditorViewModel
    }

    private val _state = MutableStateFlow(EditorUiState())
    private var createdAt = 0L
    private var previewJob: Job? = null
    private var saving = false
    private var recordingJob: Job? = null
    private var pendingTake: File? = null

    private val catalog = observeAssets().map { list -> list.map { PickableAsset(it, it.file) } }

    /** Un solo estado para la pantalla: el borrador, lo que suena y el catálogo. */
    val state: StateFlow<EditorUiState> = combine(_state, observePlayback(), catalog) { editor, playback, pickable ->
        val mode = playback.editorMode()
        editor.copy(playback = mode, positionMs = if (mode == EditorPlayback.Idle) 0 else playback.positionMs, catalog = pickable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    init {
        discardRecordingLeftovers()
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val saved = args.hopeId?.let { getHope(it) }
        var draft = if (saved != null) {
            createdAt = saved.hope.createdAt
            EditorDraft(
                title = saved.hope.title,
                cover = saved.cover,
                background = saved.background,
                voice = saved.voice,
                backgroundVolume = saved.hope.backgroundVolume,
                voiceVolume = saved.hope.voiceVolume,
                trimStartMs = saved.hope.trimStartMs,
                trimEndMs = saved.hope.trimEndMs,
                voiceOffsetMs = saved.hope.voiceOffsetMs,
                linkText = saved.hope.linkUrl.orEmpty(),
                linkPreview = saved.hope.linkUrl?.let { url ->
                    saved.hope.linkTitle?.let { title -> LinkPreview(url, title, saved.hope.linkThumbnailUrl) }
                },
            )
        } else {
            EditorDraft()
        }
        args.coverAssetId?.let { id -> getAsset(id)?.let { draft = draft.copy(cover = it) } }
        args.backgroundAssetId?.let { id -> getAsset(id)?.let { draft = draft.withBackground(it) } }
        args.linkUrl?.let { draft = draft.copy(linkText = it) }
        draft = draft.copy(link = recognizeLink(draft.linkText))
        _state.update { it.copy(loading = false, isNew = saved == null, draft = draft, coverFile = draft.cover?.file) }
        requestPreview()
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setLink(text: String) {
        edit { it.copy(linkText = text, link = recognizeLink(text)) }
        requestPreview(debounceMs = PREVIEW_DEBOUNCE_MS)
    }

    /**
     * Pide la vista previa del enlace de ahora, una sola vez: se guarda con la hope y la
     * biblioteca no vuelve a consultar la red. Sin respuesta queda la ficha con la red.
     */
    private fun requestPreview(debounceMs: Long = 0) {
        previewJob?.cancel()
        val draft = _state.value.draft
        val link = draft.link ?: return
        if (draft.currentPreview != null) return
        previewJob = viewModelScope.launch {
            delay(debounceMs)
            val preview = getLinkPreview(link) ?: return@launch
            _state.update { state ->
                if (state.draft.link?.url == preview.url) state.copy(draft = state.draft.copy(linkPreview = preview)) else state
            }
        }
    }

    fun choose(slot: EditorSlot, asset: Asset) = edit { draft ->
        when (slot) {
            EditorSlot.Cover -> if (asset.type == AssetType.Image) draft.copy(cover = asset) else draft
            EditorSlot.Background -> if (asset.type == AssetType.Audio) draft.withBackground(asset) else draft
        }
    }

    /**
     * Lo que creó una importación pedida desde el editor: el hueco que la pidió toma lo de su
     * tipo, y el otro solo se llena si estaba vacío (un video trae audio y portada).
     */
    fun applyImported(slot: EditorSlot?, created: List<Asset>) = edit { draft ->
        val image = created.firstOrNull { it.type == AssetType.Image }
        val audio = created.firstOrNull { it.type == AssetType.Audio }
        var next = draft
        if (image != null && (slot == EditorSlot.Cover || next.cover == null)) next = next.copy(cover = image)
        if (audio != null && (slot == EditorSlot.Background || next.background == null)) next = next.withBackground(audio)
        next
    }

    fun setBackgroundVolume(percent: Int) = edit { it.copy(backgroundVolume = percent.coerceIn(0, 100)) }

    fun setVoiceVolume(percent: Int) = edit { it.copy(voiceVolume = percent.coerceIn(0, 100)) }

    fun setTrim(startMs: Long, endMs: Long) = edit { draft ->
        val length = draft.background?.durationMs ?: return@edit draft
        val start = startMs.coerceIn(0, length)
        val end = endMs.coerceIn(start, length)
        draft.copy(trimStartMs = start, trimEndMs = end.takeIf { it < length })
    }

    fun shiftVoiceOffset(deltaMs: Long) = edit { it.copy(voiceOffsetMs = (it.voiceOffsetMs + deltaMs).coerceIn(0, MAX_OFFSET_MS)) }

    /** Escuchar: la mezcla completa con los ajustes de ahora. */
    fun toggleListen() {
        val draft = _state.value.draft
        if (draft.tracksMissing) return
        toggleMix(MIX_KEY, draft.toMixSpec())
    }

    /** El botón de la pista de fondo: solo el fondo, con su volumen y su recorte. */
    fun toggleBackgroundPreview() {
        val draft = _state.value.draft
        if (draft.background == null) return
        toggleMix(BACKGROUND_KEY, draft.toMixSpec().copy(voice = null))
    }

    fun onMicDenied() = _state.update { it.copy(micDenied = true) }

    /** Abre «Grabando voz» y empieza la toma. Requiere el permiso ya concedido. */
    fun startRecording() {
        val draft = _state.value.draft
        stopEditorPlayback()
        val backgroundPlays = checkBackgroundWhileRecording(hasBackground = draft.background != null)
        try {
            startVoiceTake()
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo empezar a grabar", error)
            _state.update { it.copy(recordingFailed = true, recording = null) }
            return
        }
        // La toma empieza donde entra la voz: el fondo arranca en ese punto para que calcen.
        if (backgroundPlays) {
            playMix(RECORDING_KEY, draft.toMixSpec().copy(voice = null), startMs = draft.voiceOffsetMs)
        }
        _state.update {
            it.copy(
                micDenied = false,
                recordingFailed = false,
                recording = RecordingUi(backgroundPlays = backgroundPlays, hasBackground = draft.background != null),
            )
        }
        val startedAt = SystemClock.elapsedRealtime()
        recordingJob = viewModelScope.launch {
            while (true) {
                _state.update { state ->
                    val rec = state.recording ?: return@update state
                    if (rec.stopped) state else state.copy(recording = rec.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt))
                }
                delay(TIMER_TICK_MS)
            }
        }
    }

    /** El botón central: detiene la toma y espera «Listo» o «Descartar». */
    fun stopRecording() {
        if (_state.value.recording?.stopped != false) return
        recordingJob?.cancel()
        stopMix(RECORDING_KEY)
        pendingTake = stopVoiceTake()
        _state.update { state -> state.copy(recording = state.recording?.copy(stopped = true)) }
    }

    /** «Listo»: la toma pasa a ser el asset «Grabación N» y queda como pista de voz. */
    fun finishRecording() {
        val current = _state.value.recording ?: return
        if (current.saving) return
        if (!current.stopped) stopRecording()
        val take = pendingTake
        pendingTake = null
        if (take == null) {
            _state.update { it.copy(recording = null, recordingFailed = true) }
            return
        }
        _state.update { state -> state.copy(recording = state.recording?.copy(saving = true)) }
        viewModelScope.launch {
            try {
                val asset = saveVoiceTake(take)
                _state.update { state -> state.copy(recording = null, draft = state.draft.copy(voice = asset)) }
                syncPlayback()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo guardar la toma", error)
                _state.update { it.copy(recording = null, recordingFailed = true) }
            }
        }
    }

    /** «Descartar» o cerrar la hoja: la toma no deja nada. */
    fun discardRecording() {
        recordingJob?.cancel()
        stopMix(RECORDING_KEY)
        discardVoiceTake(pendingTake)
        pendingTake = null
        _state.update { it.copy(recording = null) }
    }

    fun save() {
        val current = _state.value
        val draft = current.draft
        if (!draft.canSave) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        // Pedir la vista previa abre una ventana: un segundo toque no crea otra hope.
        if (saving) return
        saving = true
        viewModelScope.launch {
            // Sin vista previa todavía, se pide ahora; si no llega, la hope se guarda con la ficha.
            previewJob?.cancel()
            val link = draft.link
            val preview = draft.currentPreview ?: link?.let { getLinkPreview(it) }
            saveHope(
                Hope(
                    id = args.hopeId ?: 0,
                    title = draft.title.trim(),
                    coverAssetId = draft.cover?.id,
                    backgroundAssetId = draft.background?.id,
                    voiceAssetId = draft.voice?.id,
                    backgroundVolume = draft.backgroundVolume,
                    voiceVolume = draft.voiceVolume,
                    trimStartMs = draft.trimStartMs,
                    trimEndMs = draft.trimEndMs,
                    voiceOffsetMs = draft.voiceOffsetMs,
                    createdAt = createdAt,
                    linkUrl = link?.url,
                    linkNetwork = link?.network,
                    linkTitle = preview?.title,
                    linkThumbnailUrl = preview?.thumbnailUrl,
                ),
            )
            stopEditorPlayback()
            _state.update { it.copy(saved = true) }
        }
    }

    override fun onCleared() {
        stopEditorPlayback()
        if (_state.value.recording != null) discardRecording()
    }

    private fun edit(change: (EditorDraft) -> EditorDraft) {
        _state.update { state ->
            val draft = change(state.draft)
            state.copy(draft = draft, coverFile = draft.cover?.file)
        }
        syncPlayback()
    }

    /** Si algo suena desde el editor, sigue sonando con los ajustes nuevos. */
    private fun syncPlayback() {
        val draft = _state.value.draft
        updateMix(MIX_KEY, draft.toMixSpec())
        updateMix(BACKGROUND_KEY, draft.toMixSpec().copy(voice = null))
    }

    private fun stopEditorPlayback() {
        stopMix(MIX_KEY)
        stopMix(BACKGROUND_KEY)
        stopMix(RECORDING_KEY)
    }

    private fun EditorDraft.withBackground(asset: Asset) = copy(background = asset, trimStartMs = 0, trimEndMs = null)

    private fun EditorDraft.toMixSpec() = MixSpec(
        presentation = MixPresentation(title, cover?.file),
        background = background?.file,
        backgroundLengthMs = background?.durationMs,
        backgroundVolume = backgroundVolume,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        voice = voice?.file,
        voiceLengthMs = voice?.durationMs,
        voiceVolume = voiceVolume,
        voiceOffsetMs = voiceOffsetMs,
    )

    private fun MixPlayback.editorMode(): EditorPlayback = when {
        !isPlaying -> EditorPlayback.Idle
        key == MIX_KEY -> EditorPlayback.Mix
        key == BACKGROUND_KEY -> EditorPlayback.Background
        else -> EditorPlayback.Idle
    }

    companion object {
        private const val TAG = "HopeEditor"
        private const val MIX_KEY = "editor"
        private const val BACKGROUND_KEY = "editor-background"
        private const val RECORDING_KEY = "editor-recording"
        private const val TIMER_TICK_MS = 200L

        /** Mientras se escribe el enlace no se consulta la red a cada letra. */
        private const val PREVIEW_DEBOUNCE_MS = 600L

        /** Paso de «Entra a los». */
        const val OFFSET_STEP_MS = 1_000L
        private const val MAX_OFFSET_MS = 10 * 60 * 1000L
    }
}
