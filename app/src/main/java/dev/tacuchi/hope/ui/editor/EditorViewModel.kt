package dev.tacuchi.hope.ui.editor

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.tacuchi.hope.appContainer
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.AssetType
import dev.tacuchi.hope.data.Hope
import dev.tacuchi.hope.data.HopeRepository
import dev.tacuchi.hope.data.NewAsset
import dev.tacuchi.hope.importing.MediaInspector
import dev.tacuchi.hope.links.Link
import dev.tacuchi.hope.links.LinkPreview
import dev.tacuchi.hope.links.LinkPreviews
import dev.tacuchi.hope.links.Links
import dev.tacuchi.hope.playback.MixPlayback
import dev.tacuchi.hope.playback.MixPlayer
import dev.tacuchi.hope.playback.MixSpec
import dev.tacuchi.hope.recording.AudioOutputs
import dev.tacuchi.hope.recording.VoiceRecorder
import dev.tacuchi.hope.recording.backgroundPlaysWhileRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    /** El enlace reconocido, o nulo si el campo está vacío o no es un enlace. */
    val link: Link? get() = Links.recognize(linkText)
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
)

enum class EditorPlayback { Idle, Mix, Background }

/**
 * El editor de una hope nueva o guardada. Escuchar usa el mismo [MixPlayer] que la biblioteca
 * con los mismos ajustes, así lo que se oye aquí es lo que suena allá.
 */
class EditorViewModel(
    private val args: EditorArgs,
    private val hopes: HopeRepository,
    private val assets: AssetRepository,
    private val player: MixPlayer,
    private val recorder: VoiceRecorder,
    private val inspector: MediaInspector,
    private val headphonesConnected: () -> Boolean,
    private val previews: LinkPreviews,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    private var createdAt = 0L
    private var previewJob: Job? = null
    private var saving = false
    private var recordingJob: Job? = null
    private var pendingTake: File? = null

    val state: StateFlow<EditorUiState> = combine(_state, player.state) { editor, playback ->
        val mode = playback.editorMode()
        editor.copy(playback = mode, positionMs = if (mode == EditorPlayback.Idle) 0 else playback.positionMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    /** El catálogo para la hoja de elegir; nulo mientras se lee. */
    val catalog: StateFlow<List<PickableAsset>?> = assets.observe(null)
        .map { list -> list.map { PickableAsset(it, assets.fileOf(it)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        recorder.discardLeftovers()
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val saved = args.hopeId?.let { hopes.get(it) }
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
        args.coverAssetId?.let { id -> assets.get(id)?.let { draft = draft.copy(cover = it) } }
        args.backgroundAssetId?.let { id -> assets.get(id)?.let { draft = draft.withBackground(it) } }
        args.linkUrl?.let { draft = draft.copy(linkText = it) }
        _state.update { it.copy(loading = false, isNew = saved == null, draft = draft, coverFile = draft.cover?.let(assets::fileOf)) }
        requestPreview()
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setLink(text: String) {
        edit { it.copy(linkText = text) }
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
            val preview = previews.of(link) ?: return@launch
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
        player.toggle(MIX_KEY, draft.toMixSpec())
    }

    /** El botón de la pista de fondo: solo el fondo, con su volumen y su recorte. */
    fun toggleBackgroundPreview() {
        val draft = _state.value.draft
        if (draft.background == null) return
        player.toggle(BACKGROUND_KEY, draft.toMixSpec().copy(voice = null))
    }

    fun onMicDenied() = _state.update { it.copy(micDenied = true) }

    /** Abre «Grabando voz» y empieza la toma. Requiere el permiso ya concedido. */
    fun startRecording() {
        val draft = _state.value.draft
        stopEditorPlayback()
        val backgroundPlays = backgroundPlaysWhileRecording(headphonesConnected(), draft.background != null)
        try {
            recorder.start()
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo empezar a grabar", error)
            _state.update { it.copy(recordingFailed = true, recording = null) }
            return
        }
        // La toma empieza donde entra la voz: el fondo arranca en ese punto para que calcen.
        if (backgroundPlays) {
            player.play(RECORDING_KEY, draft.toMixSpec().copy(voice = null), startMs = draft.voiceOffsetMs)
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
        player.stop(RECORDING_KEY)
        pendingTake = recorder.finish()
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
                val duration = inspector.inspect(take).durationMs
                val asset = assets.add(
                    listOf(NewAsset(take, AssetNameKind.Recording, AssetOrigin.Recording, "audio/mp4", durationMs = duration)),
                ).single()
                _state.update { state -> state.copy(recording = null, draft = state.draft.copy(voice = asset)) }
                syncPlayback()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo guardar la toma", error)
                take.delete()
                _state.update { it.copy(recording = null, recordingFailed = true) }
            }
        }
    }

    /** «Descartar» o cerrar la hoja: la toma no deja nada. */
    fun discardRecording() {
        recordingJob?.cancel()
        player.stop(RECORDING_KEY)
        recorder.discard()
        pendingTake?.delete()
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
            val preview = draft.currentPreview ?: link?.let { previews.of(it) }
            hopes.save(
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
            state.copy(draft = draft, coverFile = draft.cover?.let(assets::fileOf))
        }
        syncPlayback()
    }

    /** Si algo suena desde el editor, sigue sonando con los ajustes nuevos. */
    private fun syncPlayback() {
        val draft = _state.value.draft
        player.update(MIX_KEY, draft.toMixSpec())
        player.update(BACKGROUND_KEY, draft.toMixSpec().copy(voice = null))
    }

    private fun stopEditorPlayback() {
        player.stop(MIX_KEY)
        player.stop(BACKGROUND_KEY)
        player.stop(RECORDING_KEY)
    }

    private fun EditorDraft.withBackground(asset: Asset) = copy(background = asset, trimStartMs = 0, trimEndMs = null)

    private fun EditorDraft.toMixSpec() = MixSpec(
        background = background?.let(assets::fileOf),
        backgroundLengthMs = background?.durationMs,
        backgroundVolume = backgroundVolume,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        voice = voice?.let(assets::fileOf),
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

        fun factory(args: EditorArgs): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                EditorViewModel(
                    args = args,
                    hopes = container.hopeRepository,
                    assets = container.assetRepository,
                    player = container.mixPlayer,
                    recorder = container.voiceRecorder,
                    inspector = container.inspector,
                    headphonesConnected = container.audioOutputs::headphonesConnected,
                    previews = container.linkPreviews,
                )
            }
        }
    }
}
