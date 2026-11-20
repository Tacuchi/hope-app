package dev.tacuchi.hope.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.R
import dev.tacuchi.hope.data.AssetType
import dev.tacuchi.hope.ui.assets.assetName
import dev.tacuchi.hope.ui.assets.assetSubtitle
import dev.tacuchi.hope.ui.common.AssetImage
import dev.tacuchi.hope.ui.common.formatDuration

/** Elegir la portada o el fondo de Assets, o importarlos en el momento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPickerSheet(
    slot: EditorSlot,
    assets: List<PickableAsset>?,
    onPick: (dev.tacuchi.hope.data.Asset) -> Unit,
    onImportVisual: () -> Unit,
    onImportVideo: () -> Unit,
    onImportAudio: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(if (slot == EditorSlot.Cover) R.string.editor_pick_cover_title else R.string.editor_pick_audio_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            if (slot == EditorSlot.Cover) {
                item { ImportItem(R.drawable.ic_image, R.string.editor_import_visual, onImportVisual) }
            } else {
                item { ImportItem(R.drawable.ic_smart_display, R.string.editor_import_video, onImportVideo) }
                item { ImportItem(R.drawable.ic_music_note, R.string.editor_import_audio, onImportAudio) }
            }
            item { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            if (assets != null && assets.isEmpty()) {
                item {
                    Text(
                        stringResource(if (slot == EditorSlot.Cover) R.string.editor_no_images else R.string.editor_no_audios),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            items(assets.orEmpty(), key = { it.asset.id }) { item ->
                ListItem(
                    modifier = Modifier.clickable { onPick(item.asset) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { PickerTile(item) },
                    supportingContent = {
                        Text(assetSubtitle(item.asset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                ) {
                    Text(assetName(item.asset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ImportItem(icon: Int, label: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
    ) {
        Text(stringResource(label))
    }
}

@Composable
private fun PickerTile(item: PickableAsset) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (item.asset.type == AssetType.Image) {
            AssetImage(item.file, Modifier.fillMaxSize(), maxSidePx = 192)
        } else {
            Icon(
                painterResource(if (item.asset.nameKind == dev.tacuchi.hope.data.AssetNameKind.Recording) R.drawable.ic_mic else R.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, Modifier.size(18.dp))
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
                    Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.recording_stop), Modifier.size(32.dp))
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
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp))
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
