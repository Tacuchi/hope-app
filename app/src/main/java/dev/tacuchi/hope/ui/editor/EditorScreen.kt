package dev.tacuchi.hope.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tacuchi.hope.R
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.AssetType
import dev.tacuchi.hope.ui.assets.assetName
import dev.tacuchi.hope.ui.common.AssetImage
import dev.tacuchi.hope.ui.common.formatDuration
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Assets recién importados que el editor debe poner en su lugar. */
data class EditorImport(val slot: EditorSlot?, val assets: List<Asset>)

/** Ancho máximo del formulario: en horizontal no se estira de borde a borde. */
private val FormMaxWidth = 640.dp

@Composable
fun EditorScreen(
    args: EditorArgs,
    imported: EditorImport?,
    onImportedConsumed: () -> Unit,
    onImport: (uri: Uri, origin: AssetOrigin, slot: EditorSlot) -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(args)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    EditorContent(
        state = state,
        onClose = onClose,
        onTitleChange = viewModel::setTitle,
        onPickCover = { picking = EditorSlot.Cover },
        onPickBackground = { picking = EditorSlot.Background },
        onToggleBackground = viewModel::toggleBackgroundPreview,
        onBackgroundVolume = viewModel::setBackgroundVolume,
        onTrim = viewModel::setTrim,
        onRecord = onRecord,
        onOpenSettings = openAppSettings,
        onVoiceVolume = viewModel::setVoiceVolume,
        onShiftOffset = viewModel::shiftVoiceOffset,
        onListen = viewModel::toggleListen,
        onSave = viewModel::save,
    )

    if (pickingSlot != null) {
        AssetPickerSheet(
            slot = pickingSlot,
            assets = catalog?.filter { it.asset.type == pickingSlot.assetType() },
            onPick = { asset ->
                picking = null
                viewModel.choose(pickingSlot, asset)
            },
            onImportVisual = {
                picking = null
                pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onImportVideo = {
                picking = null
                pickVideoForAudio.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onImportAudio = {
                picking = null
                pickAudio.launch(arrayOf("audio/*"))
            },
            onDismiss = { picking = null },
        )
    }

    state.recording?.let { recording ->
        RecordingSheet(
            recording = recording,
            backgroundVolume = state.draft.backgroundVolume,
            backgroundSpanMs = state.draft.backgroundSpanMs(),
            onStop = viewModel::stopRecording,
            onFinish = viewModel::finishRecording,
            onDiscard = viewModel::discardRecording,
        )
    }
}

/** Lo que dura el fondo con su recorte; nulo sin fondo. */
internal fun EditorDraft.backgroundSpanMs(): Long? {
    val length = background?.durationMs ?: return null
    return (trimEndMs ?: length) - trimStartMs
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorContent(
    state: EditorUiState,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onPickCover: () -> Unit,
    onPickBackground: () -> Unit,
    onToggleBackground: () -> Unit,
    onBackgroundVolume: (Int) -> Unit,
    onTrim: (Long, Long) -> Unit,
    onRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onVoiceVolume: (Int) -> Unit,
    onShiftOffset: (Long) -> Unit,
    onListen: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = state.draft
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.editor_title else R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
        bottomBar = {
            EditorActions(
                canListen = !draft.tracksMissing,
                listening = state.playback == EditorPlayback.Mix,
                onListen = onListen,
                onSave = onSave,
            )
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = FormMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoverAndTitle(state, onTitleChange, onPickCover)
                BackgroundTrackCard(state, onPickBackground, onToggleBackground, onBackgroundVolume, onTrim)
                VoiceTrackCard(state, onRecord, onOpenSettings, onVoiceVolume, onShiftOffset)
                if (state.showErrors && draft.tracksMissing) {
                    Text(
                        stringResource(R.string.editor_error_tracks),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Los enlaces de origen llegan en una fase posterior: el campo se ve, deshabilitado.
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.editor_link)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_link), contentDescription = null) },
                    supportingText = { Text(stringResource(R.string.editor_link_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CoverAndTitle(state: EditorUiState, onTitleChange: (String) -> Unit, onPickCover: () -> Unit) {
    val draft = state.draft
    val titleError = state.showErrors && draft.titleMissing
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val pickLabel = stringResource(if (draft.cover == null) R.string.editor_pick_cover else R.string.editor_change_cover)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = pickLabel, role = Role.Button, onClick = onPickCover),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val file = state.coverFile
                    if (file != null) {
                        AssetImage(file, Modifier.fillMaxSize())
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_image),
                            contentDescription = stringResource(R.string.editor_cover),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
            TextButton(onClick = onPickCover) { Text(pickLabel) }
        }
        OutlinedTextField(
            value = draft.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.editor_field_title)) },
            isError = titleError,
            supportingText = if (titleError) {
                { Text(stringResource(R.string.editor_error_title)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BackgroundTrackCard(
    state: EditorUiState,
    onPick: () -> Unit,
    onToggle: () -> Unit,
    onVolume: (Int) -> Unit,
    onTrim: (Long, Long) -> Unit,
) {
    val draft = state.draft
    val background = draft.background
    val subtitle = background?.let { trackSubtitle(it) } ?: stringResource(R.string.editor_background_empty)
    TrackCard(
        icon = R.drawable.ic_music_note,
        title = R.string.editor_background_track,
        subtitle = subtitle,
        action = {
            TextButton(onClick = onPick) {
                Text(stringResource(if (background == null) R.string.editor_pick_audio else R.string.editor_change_audio))
            }
        },
    ) {
        val length = background?.durationMs ?: 0L
        val enabled = background != null && length > 0
        val previewing = state.playback == EditorPlayback.Background
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onToggle, enabled = enabled) {
                Icon(
                    painterResource(if (previewing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    stringResource(if (previewing) R.string.editor_pause_background else R.string.editor_play_background),
                )
            }
            Spacer(Modifier.width(12.dp))
            val span = draft.backgroundSpanMs() ?: 0L
            val progress = if (previewing && span > 0) state.positionMs.toFloat() / span else 0f
            Slider(value = progress.coerceIn(0f, 1f), onValueChange = {}, enabled = false, modifier = Modifier.weight(1f))
        }
        VolumeRow(percent = draft.backgroundVolume, enabled = enabled, label = R.string.editor_volume_background, onChange = onVolume)
        HorizontalDivider()
        Text(stringResource(R.string.editor_trim), style = MaterialTheme.typography.titleSmall)
        val end = draft.trimEndMs ?: length
        RangeSlider(
            value = if (enabled) draft.trimStartMs.toFloat()..end.toFloat() else 0f..1f,
            onValueChange = { range ->
                onTrim(range.start.roundTo(TRIM_STEP_MS), range.endInclusive.roundTo(TRIM_STEP_MS))
            },
            valueRange = if (enabled) 0f..length.toFloat() else 0f..1f,
            enabled = enabled,
        )
        Row {
            Text(stringResource(R.string.editor_trim_start, formatDuration(draft.trimStartMs)), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.editor_trim_end, formatDuration(end)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val TRIM_STEP_MS = 100L

private fun Float.roundTo(step: Long): Long = (this / step).roundToLong() * step

@Composable
private fun VoiceTrackCard(
    state: EditorUiState,
    onRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onVolume: (Int) -> Unit,
    onShiftOffset: (Long) -> Unit,
) {
    val draft = state.draft
    val voice = draft.voice
    val subtitle = voice?.let { stringResource(R.string.editor_voice_take, formatDuration(it.durationMs ?: 0)) }
        ?: stringResource(R.string.editor_voice_empty)
    TrackCard(R.drawable.ic_mic, R.string.editor_voice_track, subtitle) {
        FilledTonalButton(onClick = onRecord) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(if (voice == null) R.string.editor_record else R.string.editor_record_again))
        }
        if (state.micDenied) {
            Column {
                Text(
                    stringResource(R.string.editor_mic_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.editor_open_settings)) }
            }
        }
        if (state.recordingFailed) {
            Text(
                stringResource(R.string.editor_recording_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        VolumeRow(percent = draft.voiceVolume, enabled = voice != null, label = R.string.editor_volume_voice, onChange = onVolume)
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.editor_voice_offset),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onShiftOffset(-EditorViewModel.OFFSET_STEP_MS) }, enabled = draft.voiceOffsetMs > 0) {
                        Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.editor_offset_less))
                    }
                    Text(formatDuration(draft.voiceOffsetMs), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onShiftOffset(EditorViewModel.OFFSET_STEP_MS) }) {
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.editor_offset_more))
                    }
                }
            }
        }
    }
}

/** «Audio de video 1 · 0:52». */
@Composable
private fun trackSubtitle(asset: Asset): String {
    val name = assetName(asset)
    val duration = asset.durationMs ?: return name
    return name + stringResource(R.string.assets_subtitle_separator) + formatDuration(duration)
}

@Composable
private fun TrackCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun VolumeRow(percent: Int, enabled: Boolean, @StringRes label: Int, onChange: (Int) -> Unit) {
    val description = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.editor_volume), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Slider(
            value = percent / 100f,
            onValueChange = { onChange((it * 100).roundToInt()) },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .semanticsLabel(description),
        )
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.editor_percent, percent), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EditorActions(canListen: Boolean, listening: Boolean, onListen: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.navigationBarsPadding().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider()
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onListen, enabled = canListen, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(painterResource(if (listening) R.drawable.ic_pause else R.drawable.ic_headphones), contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(if (listening) R.string.editor_pause else R.string.editor_listen))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.editor_save))
            }
        }
    }
}

private fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }

/** Solo imágenes para la portada y solo audios para el fondo. */
internal fun EditorSlot.assetType(): AssetType = when (this) {
    EditorSlot.Cover -> AssetType.Image
    EditorSlot.Background -> AssetType.Audio
}
