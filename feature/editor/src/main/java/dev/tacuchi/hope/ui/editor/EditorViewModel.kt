package dev.tacuchi.hope.ui.editor

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.domain.media.PreviewPlayback
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkPreview
import dev.tacuchi.hope.domain.model.MixPlayback
import dev.tacuchi.hope.domain.model.MixPresentation
import dev.tacuchi.hope.domain.model.MixSpec
import dev.tacuchi.hope.domain.model.VoiceEffects
import dev.tacuchi.hope.domain.model.WaveState
import dev.tacuchi.hope.domain.model.trimmedLengthMs
import dev.tacuchi.hope.domain.usecase.CheckBackgroundWhileRecordingUseCase
import dev.tacuchi.hope.domain.usecase.DiscardRecordingLeftoversUseCase
import dev.tacuchi.hope.domain.usecase.DiscardRecordingUseCase
import dev.tacuchi.hope.domain.usecase.GetAssetUseCase
import dev.tacuchi.hope.domain.usecase.GetHopeUseCase
import dev.tacuchi.hope.domain.usecase.GetLinkPreviewUseCase
import dev.tacuchi.hope.domain.usecase.LoadWaveformUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetsUseCase
import dev.tacuchi.hope.domain.usecase.ObservePlaybackUseCase
import dev.tacuchi.hope.domain.usecase.PlayMixUseCase
import dev.tacuchi.hope.domain.usecase.RecognizeLinkUseCase
import dev.tacuchi.hope.domain.usecase.ReleaseAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.SaveHopeUseCase
import dev.tacuchi.hope.domain.usecase.SaveRecordingUseCase
import dev.tacuchi.hope.domain.usecase.StartRecordingUseCase
import dev.tacuchi.hope.domain.usecase.StopAllAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.StopMixUseCase
import dev.tacuchi.hope.domain.usecase.StopRecordingUseCase
import dev.tacuchi.hope.domain.usecase.ToggleAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ToggleMixUseCase
import dev.tacuchi.hope.domain.usecase.UpdateMixUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Con qué abre el editor: una hope guardada, o assets y un enlace que ya vienen puestos. [openStep]
 * elige el paso; sin él, una hope guardada abre en Revisar y una nueva en Portada.
 */
data class EditorArgs(
    val hopeId: Long? = null,
    val coverAssetId: Long? = null,
    val backgroundAssetId: Long? = null,
    val linkUrl: String? = null,
    val openStep: EditorStep? = null,
)

/** Los pasos, en orden. Revisar es la previsualización: desde ahí se guarda. */
enum class EditorStep { Cover, Background, Voice, Mix, Review }

/** Un asset de la hoja de elegir, con su archivo para la miniatura. */
data class PickableAsset(val asset: Asset, val file: File)

/** Qué hueco del editor pidió un asset. */
enum class EditorSlot { Cover, Background }

/** Qué parte ajusta una hoja «Efectos». */
enum class EffectsPart { Cover, Background, Voice, Hope }

/**
 * Una hoja «Efectos» abierta: los efectos que se están probando. «Aceptar» los pasa al borrador;
 * «Cancelar» deja todo como estaba.
 */
data class EffectsSheet(
    val part: EffectsPart,
    val background: BackgroundEffects,
    val voice: VoiceEffects,
    val hope: HopeEffects,
    val cover: CoverEffects,
)

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
    val voiceTrimStartMs: Long = 0,
    val voiceTrimEndMs: Long? = null,
    val backgroundEffects: BackgroundEffects = BackgroundEffects(),
    val voiceEffects: VoiceEffects = VoiceEffects(),
    val hopeEffects: HopeEffects = HopeEffects(),
    val coverEffects: CoverEffects = CoverEffects(),
) {
    val linkInvalid: Boolean get() = linkText.isNotBlank() && link == null

    /** La vista previa solo vale para el enlace que se ve ahora. */
    val currentPreview: LinkPreview? get() = linkPreview?.takeIf { it.url == link?.url }
    val titleMissing: Boolean get() = title.isBlank()
    val tracksMissing: Boolean get() = background == null && voice == null
    val canSave: Boolean get() = !titleMissing && !tracksMissing && !linkInvalid

    val backgroundLengthMs: Long get() = background?.durationMs ?: 0
    val voiceLengthMs: Long get() = voice?.durationMs ?: 0
    val backgroundEndMs: Long get() = trimEndMs ?: backgroundLengthMs
    val voiceEndMs: Long get() = voiceTrimEndMs ?: voiceLengthMs

    /** Cómo suena el borrador: lo mismo que sonará la hope guardada en la biblioteca. */
    fun toMixSpec(): MixSpec = MixSpec(
        presentation = MixPresentation(title, cover?.file, coverEffects),
        background = background?.file,
        backgroundLengthMs = background?.durationMs,
        backgroundVolume = backgroundVolume,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        voice = voice?.file,
        voiceLengthMs = voice?.durationMs,
        voiceVolume = voiceVolume,
        voiceOffsetMs = voiceOffsetMs,
        voiceTrimStartMs = voiceTrimStartMs,
        voiceTrimEndMs = voiceTrimEndMs,
        backgroundEffects = backgroundEffects,
        voiceEffects = voiceEffects,
        hopeEffects = hopeEffects,
    )

    /** El borrador con los efectos de [sheet], para escucharlos antes de aceptarlos. */
    fun trying(sheet: EffectsSheet?): EditorDraft = if (sheet == null) this else copy(
        backgroundEffects = sheet.background,
        voiceEffects = sheet.voice,
        hopeEffects = sheet.hope,
        coverEffects = sheet.cover,
    )
}

/** Qué suena desde el editor. */
enum class EditorPlayback { Idle, Mix, Background, Voice }

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val step: EditorStep = EditorStep.Cover,
    /** El paso más avanzado al que se llegó: los anteriores figuran hechos y se pueden tocar. */
    val reached: EditorStep = EditorStep.Cover,
    val draft: EditorDraft = EditorDraft(),
    val coverFile: File? = null,
    /** Lo que suena desde el editor: la mezcla, solo el fondo o solo la voz. */
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
    val backgroundWave: WaveState? = null,
    val voiceWave: WaveState? = null,
    /** La escucha de un candidato en el selector; no cambia el borrador. */
    val preview: PreviewPlayback = PreviewPlayback(),
    val effects: EffectsSheet? = null,
)

/**
 * El editor de una hope nueva o guardada, por pasos: Portada, Fondo, Voz, Mezcla y Revisar.
 * Todos los pasos y las hojas «Efectos» trabajan sobre el mismo borrador. Escuchar usa el mismo
 * reproductor que la biblioteca con los mismos ajustes, así lo que se oye aquí es lo que suena
 * allá. [args] llega de la ruta restaurable del destino.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel @AssistedInject constructor(
    @Assisted private val args: EditorArgs,
    observeAssets: ObserveAssetsUseCase,
    observePlayback: ObservePlaybackUseCase,
    observePreview: ObserveAssetPreviewUseCase,
    discardRecordingLeftovers: DiscardRecordingLeftoversUseCase,
    private val loadWaveform: LoadWaveformUseCase,
    private val getHope: GetHopeUseCase,
    private val getAsset: GetAssetUseCase,
    private val saveHope: SaveHopeUseCase,
    private val recognizeLink: RecognizeLinkUseCase,
    private val getLinkPreview: GetLinkPreviewUseCase,
    private val toggleMix: ToggleMixUseCase,
    private val playMix: PlayMixUseCase,
    private val updateMix: UpdateMixUseCase,
    private val stopMix: StopMixUseCase,
    private val togglePreview: ToggleAssetPreviewUseCase,
    private val stopPreview: StopAllAssetPreviewUseCase,
    private val releasePreview: ReleaseAssetPreviewUseCase,
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

    /** Sube con «Reintentar» para volver a pedir una onda que falló. */
    private val waveRetry = MutableStateFlow(0)

    private val catalog = observeAssets().map { list -> list.map { PickableAsset(it, it.file) } }

    /** La onda de una pista: se calcula al cambiar de audio, no al recortar ni al cambiar el volumen. */
    private fun waveOf(asset: (EditorDraft) -> Asset?): Flow<WaveState?> =
        combine(_state.map { asset(it.draft) }.distinctUntilChanged(), waveRetry) { current, _ -> current }
            .flatMapLatest { current -> current?.let(loadWaveform::invoke) ?: flowOf(null) }

    private val waves = combine(waveOf { it.background }, waveOf { it.voice }) { background, voice -> background to voice }

    /** Un solo estado para la pantalla: el borrador, lo que suena, el catálogo, las ondas y la preescucha. */
    val state: StateFlow<EditorUiState> = combine(_state, observePlayback(), catalog, waves, observePreview()) {
            editor, playback, pickable, (backgroundWave, voiceWave), preview ->
        val mode = playback.editorMode()
        editor.copy(
            playback = mode,
            positionMs = if (mode == EditorPlayback.Idle) 0 else playback.positionMs,
            catalog = pickable,
            backgroundWave = backgroundWave,
            voiceWave = voiceWave,
            preview = preview,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    init {
        discardRecordingLeftovers()
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val saved = args.hopeId?.let { getHope(it) }
        var draft = if (saved != null) {
            createdAt = saved.hope.createdAt
            val hope = saved.hope
            EditorDraft(
                title = hope.title,
                cover = saved.cover,
                background = saved.background,
                voice = saved.voice,
                backgroundVolume = hope.backgroundVolume,
                voiceVolume = hope.voiceVolume,
                trimStartMs = hope.trimStartMs,
                trimEndMs = hope.trimEndMs,
                voiceOffsetMs = hope.voiceOffsetMs,
                linkText = hope.linkUrl.orEmpty(),
                linkPreview = hope.linkUrl?.let { url -> hope.linkTitle?.let { title -> LinkPreview(url, title, hope.linkThumbnailUrl) } },
                voiceTrimStartMs = hope.voiceTrimStartMs,
                voiceTrimEndMs = hope.voiceTrimEndMs,
                backgroundEffects = hope.backgroundEffects,
                voiceEffects = hope.voiceEffects,
                hopeEffects = hope.hopeEffects,
                coverEffects = hope.coverEffects,
            )
        } else {
            EditorDraft()
        }
        args.coverAssetId?.let { id -> getAsset(id)?.let { draft = draft.copy(cover = it) } }
        args.backgroundAssetId?.let { id -> getAsset(id)?.let { draft = draft.withBackground(it) } }
        args.linkUrl?.let { draft = draft.copy(linkText = it) }
        draft = draft.copy(link = recognizeLink(draft.linkText))
        // «Editar» abre la previsualización; una hope guardada tiene todos sus pasos hechos.
        val step = args.openStep ?: if (saved != null) EditorStep.Review else EditorStep.Cover
        val reached = if (saved != null) EditorStep.Review else step
        _state.update {
            it.copy(loading = false, isNew = saved == null, draft = draft, coverFile = draft.cover?.file, step = step, reached = reached)
        }
        requestPreview()
    }

    // Pasos.

    /** Va a [step]: a uno ya hecho, o al siguiente del actual. */
    fun goTo(step: EditorStep) {
        val current = _state.value
        if (step.ordinal > maxOf(current.reached.ordinal, current.step.ordinal + 1)) return
        stopTrackPreviews()
        stopPreview()
        _state.update { it.copy(step = step, reached = maxOf(it.reached, step)) }
    }

    fun next() {
        val step = _state.value.step
        if (step != EditorStep.Review) goTo(EditorStep.entries[step.ordinal + 1])
    }

    /** Atrás: al paso anterior sin perder nada. Devuelve falso en el primero, para cerrar el editor. */
    fun back(): Boolean {
        val current = _state.value
        if (current.effects != null) {
            cancelEffects()
            return true
        }
        if (current.step == EditorStep.Cover) return false
        goTo(EditorStep.entries[current.step.ordinal - 1])
        return true
    }

    // Portada, título y enlace.

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

    /** Elegir confirma: la pista o la portada cambian. Escuchar un candidato no las toca. */
    fun choose(slot: EditorSlot, asset: Asset) {
        stopPreview()
        edit { draft ->
            when (slot) {
                EditorSlot.Cover -> if (asset.type == AssetType.Image) draft.copy(cover = asset) else draft
                EditorSlot.Background -> if (asset.type == AssetType.Audio && asset.id != draft.background?.id) draft.withBackground(asset) else draft
            }
        }
    }

    fun removeBackground() = edit { it.copy(background = null, trimStartMs = 0, trimEndMs = null) }

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

    // Preescucha del selector.

    /** Escucha un candidato del selector, o lo pausa. Pausa lo que suene desde el editor. */
    fun togglePickerPreview(asset: Asset) {
        stopTrackPreviews()
        stopMix(MIX_KEY)
        togglePreview(asset)
    }

    /** Al cerrar el selector deja de sonar el candidato. */
    fun closePicker() = stopPreview()

    // Fondo y voz.

    fun setBackgroundVolume(percent: Int) = edit { it.copy(backgroundVolume = percent.coerceIn(0, 100)) }

    fun setVoiceVolume(percent: Int) = edit { it.copy(voiceVolume = percent.coerceIn(0, 100)) }

    /** Recorte del fondo, en tiempos del archivo; siempre deja un tramo de [MIN_SPAN_MS] al menos. */
    fun setTrim(startMs: Long, endMs: Long) = edit { draft ->
        val length = draft.background?.durationMs ?: return@edit draft
        val (start, end) = clampTrim(startMs, endMs, length)
        draft.copy(trimStartMs = start, trimEndMs = end.takeIf { it < length })
    }

    /** Recorte de la voz, en tiempos del archivo. */
    fun setVoiceTrim(startMs: Long, endMs: Long) = edit { draft ->
        val length = draft.voice?.durationMs ?: return@edit draft
        val (start, end) = clampTrim(startMs, endMs, length)
        draft.copy(voiceTrimStartMs = start, voiceTrimEndMs = end.takeIf { it < length })
    }

    fun shiftVoiceOffset(deltaMs: Long) = edit { it.copy(voiceOffsetMs = (it.voiceOffsetMs + deltaMs).coerceIn(0, MAX_OFFSET_MS)) }

    /** «Reintentar» en una onda que no se pudo calcular. */
    fun retryWaves() {
        waveRetry.value++
    }

    // Escuchar.

    /** Escuchar la mezcla completa con los ajustes de ahora (y los de la hoja abierta). */
    fun toggleListen() {
        val draft = effectiveDraft()
        if (draft.tracksMissing) return
        stopPreview()
        toggleMix(MIX_KEY, draft.toMixSpec())
    }

    /** Escuchar el tramo del fondo con sus efectos, solo. */
    fun toggleBackgroundPreview() {
        val draft = effectiveDraft()
        if (draft.background == null) return
        stopPreview()
        toggleMix(BACKGROUND_KEY, draft.backgroundOnly())
    }

    /** Escuchar el tramo de la voz con sus efectos, sola y desde el principio. */
    fun toggleVoicePreview() {
        val draft = effectiveDraft()
        if (draft.voice == null) return
        stopPreview()
        toggleMix(VOICE_KEY, draft.voiceOnly())
    }

    // Hojas «Efectos».

    fun openEffects(part: EffectsPart) {
        val draft = _state.value.draft
        _state.update {
            it.copy(effects = EffectsSheet(part, draft.backgroundEffects, draft.voiceEffects, draft.hopeEffects, draft.coverEffects))
        }
    }

    fun editBackgroundEffects(change: (BackgroundEffects) -> BackgroundEffects) = editSheet { it.copy(background = change(it.background)) }

    fun editVoiceEffects(change: (VoiceEffects) -> VoiceEffects) = editSheet { it.copy(voice = change(it.voice)) }

    fun editHopeEffects(change: (HopeEffects) -> HopeEffects) = editSheet { sheet ->
        val next = change(sheet.hope)
        // El fondo solo baja bajo la voz si hay voz.
        sheet.copy(hope = if (_state.value.draft.voice == null) next.copy(duck = null) else next)
    }

    fun editCoverEffects(change: (CoverEffects) -> CoverEffects) = editSheet { it.copy(cover = change(it.cover)) }

    /** Escuchar desde la hoja, antes de cerrarla: su parte con los efectos que se están probando. */
    fun toggleSheetListen() = when (_state.value.effects?.part) {
        EffectsPart.Background -> toggleBackgroundPreview()
        EffectsPart.Voice -> toggleVoicePreview()
        EffectsPart.Hope -> toggleListen()
        EffectsPart.Cover, null -> Unit
    }

    /** «Aceptar»: los efectos probados pasan al borrador. */
    fun acceptEffects() {
        val sheet = _state.value.effects ?: return
        stopSheetListening(sheet.part)
        _state.update { it.copy(effects = null) }
        edit { it.trying(sheet) }
    }

    /** «Cancelar» o cerrar: el borrador queda como estaba. */
    fun cancelEffects() {
        val sheet = _state.value.effects ?: return
        stopSheetListening(sheet.part)
        _state.update { it.copy(effects = null) }
    }

    private fun stopSheetListening(part: EffectsPart) = when (part) {
        EffectsPart.Background -> stopMix(BACKGROUND_KEY)
        EffectsPart.Voice -> stopMix(VOICE_KEY)
        EffectsPart.Hope -> stopMix(MIX_KEY)
        EffectsPart.Cover -> Unit
    }

    private fun editSheet(change: (EffectsSheet) -> EffectsSheet) {
        _state.update { state -> state.effects?.let { state.copy(effects = change(it)) } ?: state }
        syncPlayback()
    }

    // Grabación.

    fun onMicDenied() = _state.update { it.copy(micDenied = true) }

    /** Abre «Grabando voz» y empieza la toma. Requiere el permiso ya concedido. */
    fun startRecording() {
        val draft = _state.value.draft
        stopEditorPlayback()
        stopPreview()
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

    /** «Listo»: la toma pasa a ser el asset «Grabación N» y queda como pista de voz, entera. */
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
                _state.update { state ->
                    state.copy(recording = null, draft = state.draft.copy(voice = asset, voiceTrimStartMs = 0, voiceTrimEndMs = null))
                }
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

    // Guardar.

    fun save() {
        val draft = _state.value.draft
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
                    voiceTrimStartMs = draft.voiceTrimStartMs,
                    voiceTrimEndMs = draft.voiceTrimEndMs,
                    backgroundEffects = draft.backgroundEffects,
                    voiceEffects = draft.voiceEffects,
                    // Sin voz el fondo no baja: no se guarda una bajada que no suena.
                    hopeEffects = if (draft.voice == null) draft.hopeEffects.copy(duck = null) else draft.hopeEffects,
                    coverEffects = draft.coverEffects,
                ),
            )
            stopEditorPlayback()
            _state.update { it.copy(saved = true) }
        }
    }

    override fun onCleared() {
        stopEditorPlayback()
        releasePreview()
        if (_state.value.recording != null) discardRecording()
    }

    private fun edit(change: (EditorDraft) -> EditorDraft) {
        _state.update { state ->
            val draft = change(state.draft)
            state.copy(draft = draft, coverFile = draft.cover?.file)
        }
        syncPlayback()
    }

    /** El borrador con los efectos de la hoja abierta: es lo que se escucha. */
    private fun effectiveDraft(): EditorDraft = _state.value.let { it.draft.trying(it.effects) }

    /** Si algo suena desde el editor, sigue sonando con los ajustes nuevos, desde el mismo punto. */
    private fun syncPlayback() {
        val draft = effectiveDraft()
        updateMix(MIX_KEY, draft.toMixSpec())
        updateMix(BACKGROUND_KEY, draft.backgroundOnly())
        updateMix(VOICE_KEY, draft.voiceOnly())
    }

    private fun stopTrackPreviews() {
        stopMix(BACKGROUND_KEY)
        stopMix(VOICE_KEY)
    }

    private fun stopEditorPlayback() {
        stopMix(MIX_KEY)
        stopTrackPreviews()
        stopMix(RECORDING_KEY)
    }

    private fun EditorDraft.withBackground(asset: Asset) = copy(background = asset, trimStartMs = 0, trimEndMs = null)

    /** Solo el fondo, recortado y con sus efectos, sin los de la hope completa. */
    private fun EditorDraft.backgroundOnly(): MixSpec = toMixSpec().copy(voice = null, hopeEffects = HopeEffects())

    /** Solo la voz, recortada y con sus efectos, desde el principio. */
    private fun EditorDraft.voiceOnly(): MixSpec = toMixSpec().copy(background = null, voiceOffsetMs = 0, hopeEffects = HopeEffects())

    private fun MixPlayback.editorMode(): EditorPlayback = when {
        !isPlaying -> EditorPlayback.Idle
        key == MIX_KEY -> EditorPlayback.Mix
        key == BACKGROUND_KEY -> EditorPlayback.Background
        key == VOICE_KEY -> EditorPlayback.Voice
        else -> EditorPlayback.Idle
    }

    companion object {
        private const val TAG = "HopeEditor"
        private const val MIX_KEY = "editor"
        private const val BACKGROUND_KEY = "editor-background"
        private const val VOICE_KEY = "editor-voice"
        private const val RECORDING_KEY = "editor-recording"
        private const val TIMER_TICK_MS = 200L

        /** Mientras se escribe el enlace no se consulta la red a cada letra. */
        private const val PREVIEW_DEBOUNCE_MS = 600L

        /** Paso de − y + en la entrada de la voz y en los recortes. */
        const val OFFSET_STEP_MS = 500L
        const val TRIM_STEP_MS = 500L

        /** Lo más corto que puede quedar un recorte. */
        const val MIN_SPAN_MS = 500L
        private const val MAX_OFFSET_MS = 10 * 60 * 1000L

        /** Acota un recorte al archivo y deja siempre un tramo que se puede reproducir. */
        internal fun clampTrim(startMs: Long, endMs: Long, lengthMs: Long): Pair<Long, Long> {
            val minSpan = minOf(MIN_SPAN_MS, lengthMs)
            val start = startMs.coerceIn(0, lengthMs - minSpan)
            val end = endMs.coerceIn(start + minSpan, lengthMs)
            return start to end
        }

        /** Lo que dura un recorte. */
        internal fun span(lengthMs: Long, startMs: Long, endMs: Long?) = trimmedLengthMs(lengthMs, startMs, endMs)
    }
}
