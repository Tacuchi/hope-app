package dev.tacuchi.hope.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.domain.media.PreviewPlayback
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.ui.common.PlayRingButton
import dev.tacuchi.hope.ui.common.ProgressLine
import dev.tacuchi.hope.ui.common.progressText
import dev.tacuchi.hope.ui.theme.TimeStyle
import dev.tacuchi.hope.ui.common.assetName
import dev.tacuchi.hope.ui.common.assetSubtitle
import dev.tacuchi.hope.ui.common.AssetImage
import dev.tacuchi.hope.ui.common.formatDuration
import dev.tacuchi.hope.ui.R as UiR

/**
 * Elegir la portada o el fondo de Assets, o importarlos en el momento. En los audios, ▶ escucha y
 * el círculo elige: escuchar no cambia la pista. Solo «Usar» confirma la elección. Lo que suena se
 * detiene al cerrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPickerSheet(
    slot: EditorSlot,
    assets: List<PickableAsset>?,
    inUse: Asset?,
    preview: PreviewPlayback,
    onListen: (Asset) -> Unit,
    onConfirm: (Asset) -> Unit,
    onImportVisual: () -> Unit,
    onImportVideo: () -> Unit,
    onImportAudio: () -> Unit,
    onDismiss: () -> Unit,
) {
    var chosenId by rememberSaveable { mutableStateOf(inUse?.id) }
    val chosen = assets?.firstOrNull { it.asset.id == chosenId }?.asset
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (slot == EditorSlot.Cover) R.string.picker_cover_title else R.string.picker_audio_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(UiR.drawable.ic_close, stringResource(UiR.string.action_close), onDismiss)
            }
            if (slot == EditorSlot.Background) {
                Text(
                    stringResource(R.string.picker_audio_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false).selectableGroup()) {
                if (assets != null && assets.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (slot == EditorSlot.Cover) R.string.picker_no_images else R.string.picker_no_audios),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
                items(assets.orEmpty(), key = { it.asset.id }) { item ->
                    CandidateRow(
                        item = item,
                        chosen = item.asset.id == chosenId,
                        inUse = item.asset.id == inUse?.id,
                        progress = preview.takeIf { it.assetId == item.asset.id },
                        onListen = { onListen(item.asset) },
                        onChoose = { chosenId = item.asset.id },
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (slot == EditorSlot.Cover) {
                            ImportButton(UiR.drawable.ic_upload, R.string.picker_import_visual, onImportVisual)
                        } else {
                            ImportButton(UiR.drawable.ic_smart_display, R.string.picker_import_video, onImportVideo)
                            ImportButton(UiR.drawable.ic_upload, R.string.picker_import_audio, onImportAudio)
                        }
                    }
                }
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(UiR.string.action_cancel), style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = { chosen?.let(onConfirm) },
                    enabled = chosen != null,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Text(
                        chosen?.let { stringResource(R.string.picker_use, assetName(it)) } ?: stringResource(UiR.string.action_accept),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Una fila candidata: reproducir a la izquierda y elegir a la derecha, dos objetivos separados. */
@Composable
private fun CandidateRow(
    item: PickableAsset,
    chosen: Boolean,
    inUse: Boolean,
    progress: PreviewPlayback?,
    onListen: () -> Unit,
    onChoose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val name = assetName(item.asset)
    val playing = progress?.isPlaying == true
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when {
            chosen -> colors.secondaryContainer
            progress != null -> colors.surfaceContainerHigh
            else -> colors.surfaceContainerLow.copy(alpha = 0f)
        },
        contentColor = if (chosen) colors.onSecondaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.asset.type == AssetType.Audio) {
                PlayRingButton(
                    playing = playing,
                    positionMs = progress?.positionMs ?: 0,
                    durationMs = progress?.durationMs?.takeIf { it > 0 } ?: item.asset.durationMs ?: 0,
                    contentDescription = stringResource(if (playing) R.string.picker_pause else R.string.picker_listen, name),
                    onClick = onListen,
                    size = 56.dp,
                )
            } else {
                PickerTile(item)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = onChoose),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (inUse) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.outline), color = colors.surface.copy(alpha = 0f)) {
                            Text(stringResource(R.string.picker_in_use), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                if (progress != null) {
                    val duration = progress.durationMs.takeIf { it > 0 } ?: item.asset.durationMs ?: 0
                    Text(
                        stringResource(R.string.picker_playing) + stringResource(UiR.string.assets_subtitle_separator) + progressText(progress.positionMs, duration),
                        style = TimeStyle.small,
                    )
                    ProgressLine(progress.positionMs, duration, Modifier.fillMaxWidth().padding(top = 4.dp))
                } else {
                    val subtitle = assetSubtitle(item.asset) + if (chosen) stringResource(UiR.string.assets_subtitle_separator) + stringResource(R.string.picker_chosen) else ""
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (chosen) colors.onSecondaryContainer else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RadioButton(
                selected = chosen,
                onClick = onChoose,
                modifier = Modifier.size(48.dp),
                colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.outline),
            )
        }
    }
}

@Composable
private fun ImportButton(icon: Int, label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(label))
    }
}

@Composable
private fun PickerTile(item: PickableAsset) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AssetImage(item.file, Modifier.fillMaxSize(), maxSidePx = 192)
    }
}

/**
 * Hoja «Grabando voz»: el tiempo que lleva, detener, terminar o descartar. Cerrarla descarta.
 * Con audífonos el fondo suena; por el altavoz se graba en silencio y lo dice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSheet(
    recording: RecordingUi,
    backgroundVolume: Int,
    backgroundSpanMs: Long?,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDiscard,
        // Sin confirmValueChange: una lambda nueva en cada tic del cronómetro recrearía el estado
        // y la hoja volvería a animarse sin terminar de abrir.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // La hoja ya deja libre la barra de navegación; si no cabe (horizontal), se desplaza.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.recording_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            val hint = when {
                recording.backgroundPlays -> stringResource(R.string.recording_background_on, backgroundVolume)
                recording.hasBackground -> stringResource(R.string.recording_background_off)
                else -> stringResource(R.string.recording_no_background)
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!recording.stopped) {
                    Box(Modifier.size(10.dp).background(colors.error, CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(if (recording.stopped) R.string.recording_ready else R.string.recording_on),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (recording.stopped) colors.primary else colors.error,
                )
            }
            Text(
                formatDuration(recording.elapsedMs),
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(16.dp))
            if (backgroundSpanMs != null && backgroundSpanMs > 0) {
                LinearProgressIndicator(
                    progress = { (recording.elapsedMs.toFloat() / backgroundSpanMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onDiscard, enabled = !recording.saving, modifier = Modifier.height(48.dp)) {
                    Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.recording_discard))
                }
                FilledIconButton(
                    onClick = onStop,
                    enabled = !recording.stopped,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    Icon(painterResource(UiR.drawable.ic_stop), contentDescription = stringResource(R.string.recording_stop), Modifier.size(32.dp))
                }
                FilledTonalButton(
                    onClick = onFinish,
                    enabled = !recording.saving,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer,
                    ),
                ) {
                    Icon(painterResource(UiR.drawable.ic_check), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.recording_done))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.recording_discard_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
