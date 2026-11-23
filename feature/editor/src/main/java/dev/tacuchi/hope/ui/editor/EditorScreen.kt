package dev.tacuchi.hope.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.VoiceEffects
import dev.tacuchi.hope.ui.common.openLink
import dev.tacuchi.hope.ui.R as UiR

/** Assets recién importados que el editor debe poner en su lugar. */
data class EditorImport(val slot: EditorSlot?, val assets: List<Asset>)

/** Ancho máximo del contenido: en horizontal no se estira de borde a borde. */
private val ContentMaxWidth = 640.dp

/** Todo lo que la pantalla puede pedir; [EditorContent] no conoce el ViewModel. */
class EditorActions(
    val onClose: () -> Unit = {},
    val onStep: (EditorStep) -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onLink: (String) -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onPick: (EditorSlot) -> Unit = {},
    val onImportCover: () -> Unit = {},
    val onRemoveBackground: () -> Unit = {},
    val onTrim: (Long, Long) -> Unit = { _, _ -> },
    val onVoiceTrim: (Long, Long) -> Unit = { _, _ -> },
    val onRetryWaves: () -> Unit = {},
    val onToggleBackground: () -> Unit = {},
    val onToggleVoice: () -> Unit = {},
    val onListen: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onBackgroundVolume: (Int) -> Unit = {},
    val onVoiceVolume: (Int) -> Unit = {},
    val onShiftOffset: (Long) -> Unit = {},
    val onOpenEffects: (EffectsPart) -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
fun EditorScreen(
    args: EditorArgs,
    imported: EditorImport?,
    onImportedConsumed: () -> Unit,
    onImport: (uri: Uri, origin: AssetOrigin, slot: EditorSlot) -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(args) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Una importación pedida desde un paso se aplica una sola vez y deja el resto del borrador.
    LaunchedEffect(imported) {
        if (imported != null) {
            viewModel.applyImported(imported.slot, imported.assets)
            onImportedConsumed()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording() else viewModel.onMicDenied()
    }
    val onRecord = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    var picking by rememberSaveable { mutableStateOf<EditorSlot?>(null) }
    val pickingSlot = picking
    val pickVisual = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImport(uri, AssetOrigin.Gallery, EditorSlot.Cover)
    }
    val pickVideoForAudio = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImport(uri, AssetOrigin.Gallery, EditorSlot.Background)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri, AssetOrigin.Files, EditorSlot.Background)
    }
    val importVisual = { pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }

    // Atrás va al paso anterior sin perder nada; en el primero cierra.
    BackHandler(enabled = !state.loading && (state.step != EditorStep.Cover || state.effects != null)) {
        viewModel.back()
    }

    EditorContent(
        state = state,
        actions = EditorActions(
            onClose = onClose,
            onStep = viewModel::goTo,
            onNext = viewModel::next,
            onBack = { if (!viewModel.back()) onClose() },
            onTitle = viewModel::setTitle,
            onLink = viewModel::setLink,
            onOpenLink = { url -> openLink(context, url) },
            onPick = { picking = it },
            onImportCover = importVisual,
            onRemoveBackground = viewModel::removeBackground,
            onTrim = viewModel::setTrim,
            onVoiceTrim = viewModel::setVoiceTrim,
            onRetryWaves = viewModel::retryWaves,
            onToggleBackground = viewModel::toggleBackgroundPreview,
            onToggleVoice = viewModel::toggleVoicePreview,
            onListen = viewModel::toggleListen,
            onRecord = onRecord,
            onOpenSettings = openAppSettings,
            onBackgroundVolume = viewModel::setBackgroundVolume,
            onVoiceVolume = viewModel::setVoiceVolume,
            onShiftOffset = viewModel::shiftVoiceOffset,
            onOpenEffects = viewModel::openEffects,
            onSave = viewModel::save,
        ),
    )

    if (pickingSlot != null) {
        val close = {
            picking = null
            viewModel.closePicker()
        }
        AssetPickerSheet(
            slot = pickingSlot,
            assets = state.catalog?.filter { it.asset.type == pickingSlot.assetType() },
            inUse = if (pickingSlot == EditorSlot.Cover) state.draft.cover else state.draft.background,
            preview = state.preview,
            onListen = viewModel::togglePickerPreview,
            onConfirm = { asset ->
                picking = null
                viewModel.choose(pickingSlot, asset)
            },
            onImportVisual = {
                close()
                importVisual()
            },
            onImportVideo = {
                close()
                pickVideoForAudio.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onImportAudio = {
                close()
                pickAudio.launch(arrayOf("audio/*"))
            },
            onDismiss = close,
        )
    }

    EffectsSheetHost(state, viewModel)

    state.recording?.let { recording ->
        RecordingSheet(
            recording = recording,
            backgroundVolume = state.draft.backgroundVolume,
            backgroundSpanMs = state.draft.background?.let { EditorViewModel.span(state.draft.backgroundLengthMs, state.draft.trimStartMs, state.draft.trimEndMs) },
            onStop = viewModel::stopRecording,
            onFinish = viewModel::finishRecording,
            onDiscard = viewModel::discardRecording,
        )
    }
}

/** La hoja «Efectos» abierta, si hay una. */
@Composable
private fun EffectsSheetHost(state: EditorUiState, viewModel: EditorViewModel) {
    val sheet = state.effects ?: return
    if (sheet.part == EffectsPart.Cover) {
        CoverEffectsSheet(
            cover = state.coverFile,
            effects = sheet.cover,
            onChange = { change: (CoverEffects) -> CoverEffects -> viewModel.editCoverEffects(change) },
            onCancel = viewModel::cancelEffects,
            onAccept = viewModel::acceptEffects,
        )
    } else {
        AudioEffectsSheet(
            state = state,
            sheet = sheet,
            onBackground = { change: (BackgroundEffects) -> BackgroundEffects -> viewModel.editBackgroundEffects(change) },
            onVoice = { change: (VoiceEffects) -> VoiceEffects -> viewModel.editVoiceEffects(change) },
            onHope = { change: (HopeEffects) -> HopeEffects -> viewModel.editHopeEffects(change) },
            onListen = viewModel::toggleSheetListen,
            onCancel = viewModel::cancelEffects,
            onAccept = viewModel::acceptEffects,
            onGoToVoice = {
                viewModel.cancelEffects()
                viewModel.goTo(EditorStep.Voice)
            },
        )
    }
}

/**
 * El editor por pasos sin el ViewModel: el título con el paso, el indicador «hilván», el paso
 * actual y los botones Atrás y Siguiente (o Guardar en Revisar).
 */
@Composable
fun EditorContent(state: EditorUiState, actions: EditorActions) {
    val step = state.step
    Scaffold(
        topBar = { EditorTopBar(state, actions) },
        bottomBar = { EditorBottomBar(state, actions) },
    ) { innerPadding ->
        if (state.loading) return@Scaffold
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    EditorStep.Cover -> CoverStep(state, actions)
                    EditorStep.Background -> BackgroundStep(state, actions)
                    EditorStep.Voice -> VoiceStep(state, actions)
                    EditorStep.Mix -> MixStep(state, actions)
                    EditorStep.Review -> ReviewStep(state, actions)
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(state: EditorUiState, actions: EditorActions) {
    Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // En el primer paso se cierra; en los demás se vuelve al anterior.
            val first = state.step == EditorStep.Cover
            RoundIconButtonTransparent(
                if (first) UiR.drawable.ic_close else UiR.drawable.ic_arrow_back,
                stringResource(if (first) UiR.string.action_close else UiR.string.action_back),
                if (first) actions.onClose else actions.onBack,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    stringResource(if (state.isNew) R.string.editor_title else R.string.editor_title_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.editor_step_progress, state.step.ordinal + 1, EditorStep.entries.size, stringResource(state.step.label())),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StepIndicator(state.step, state.reached, actions.onStep, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun RoundIconButtonTransparent(icon: Int, description: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

@Composable
private fun EditorBottomBar(state: EditorUiState, actions: EditorActions) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.step != EditorStep.Cover) {
                OutlinedButton(onClick = actions.onBack, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(R.string.editor_back), style = MaterialTheme.typography.titleMedium)
                }
            }
            val primary = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
            if (state.step == EditorStep.Review) {
                Button(onClick = actions.onSave, modifier = Modifier.weight(1.4f).height(56.dp), colors = primary) {
                    Icon(painterResource(UiR.drawable.ic_check), null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                val next = EditorStep.entries[state.step.ordinal + 1]
                val label = if (state.step == EditorStep.Voice && state.draft.voice == null) {
                    stringResource(R.string.editor_skip_voice)
                } else {
                    stringResource(R.string.editor_next, stringResource(next.label()))
                }
                Button(onClick = actions.onNext, modifier = Modifier.weight(1.4f).height(56.dp), colors = primary) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(UiR.drawable.ic_arrow_forward), null)
                }
            }
        }
    }
}

/** Solo imágenes para la portada y solo audios para el fondo. */
internal fun EditorSlot.assetType(): AssetType = when (this) {
    EditorSlot.Cover -> AssetType.Image
    EditorSlot.Background -> AssetType.Audio
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}
