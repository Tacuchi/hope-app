package dev.tacuchi.hope.ui.importing

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.ui.common.AssetImage
import dev.tacuchi.hope.ui.common.formatDuration
import dev.tacuchi.hope.ui.R as UiR

/** Las pantallas de una importación en curso: preparación, avisos, extracción y portada. */
@Composable
fun ImportScreen(
    state: ImportState,
    onCancel: () -> Unit,
    onContinueLong: () -> Unit,
    onChooseCoverOnly: () -> Unit,
    onSelectPosition: (Long) -> Unit,
    onPickGallery: (Uri) -> Unit,
    onUse: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    when (state) {
        is ImportState.ChoosingCover -> ChooseCoverScreen(state, onCancel, onSelectPosition, onPickGallery, onUse)
        is ImportState.Extracting -> ExtractingScreen(state.progress, onCancel)
        else -> {
            WaitingScreen(
                message = stringResource(if (state == ImportState.Saving) R.string.import_saving else R.string.import_preparing),
                onCancel = onCancel,
            )
            ImportDialog(state, onCancel, onContinueLong, onChooseCoverOnly)
        }
    }
}

@Composable
private fun ImportDialog(
    state: ImportState,
    onCancel: () -> Unit,
    onContinueLong: () -> Unit,
    onChooseCoverOnly: () -> Unit,
) {
    when (state) {
        is ImportState.Unsupported -> MessageDialog(
            title = stringResource(R.string.import_unsupported_title),
            body = stringResource(
                R.string.import_unsupported_body,
                state.received ?: stringResource(R.string.import_unsupported_unknown),
            ),
            onDismiss = onCancel,
        )
        ImportState.Failed -> MessageDialog(
            title = stringResource(R.string.import_failed_title),
            body = stringResource(R.string.import_failed_body),
            onDismiss = onCancel,
        )
        is ImportState.LongWarning -> ChoiceDialog(
            icon = UiR.drawable.ic_warning,
            title = stringResource(R.string.import_long_title),
            body = stringResource(R.string.import_long_body, formatDuration(state.durationMs)),
            confirm = stringResource(UiR.string.action_continue),
            onConfirm = onContinueLong,
            onCancel = onCancel,
        )
        ImportState.NoAudio -> ChoiceDialog(
            icon = UiR.drawable.ic_info,
            title = stringResource(R.string.import_no_audio_title),
            body = stringResource(R.string.import_no_audio_body),
            confirm = stringResource(R.string.import_no_audio_cover),
            onConfirm = onChooseCoverOnly,
            onCancel = onCancel,
        )
        else -> Unit
    }
}

@Composable
private fun MessageDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(UiR.drawable.ic_info), contentDescription = null) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_accept)) } },
    )
}

@Composable
private fun ChoiceDialog(
    icon: Int,
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(painterResource(icon), contentDescription = null) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportTopBar(title: String, onCancel: () -> Unit, action: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(painterResource(UiR.drawable.ic_close), contentDescription = stringResource(UiR.string.action_cancel))
            }
        },
        actions = { action() },
    )
}

@Composable
private fun WaitingScreen(message: String, onCancel: () -> Unit) {
    Scaffold(topBar = { ImportTopBar(stringResource(UiR.string.assets_import), onCancel) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LinearProgressIndicator(Modifier.widthIn(max = 360.dp).fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExtractingScreen(progress: Int?, onCancel: () -> Unit) {
    Scaffold(topBar = { ImportTopBar(stringResource(R.string.import_extracting_title), onCancel) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(UiR.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                if (progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.import_progress, progress), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.import_extracting_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onCancel) { Text(stringResource(UiR.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun ChooseCoverScreen(
    state: ImportState.ChoosingCover,
    onCancel: () -> Unit,
    onSelectPosition: (Long) -> Unit,
    onPickGallery: (Uri) -> Unit,
    onUse: () -> Unit,
) {
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickGallery(uri)
    }
    val openGallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Scaffold(
        topBar = {
            ImportTopBar(stringResource(R.string.cover_title), onCancel) {
                TextButton(onClick = onUse, enabled = state.preview != null || state.galleryImage != null) {
                    Text(stringResource(R.string.cover_use), fontWeight = FontWeight.Medium)
                }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (maxWidth > maxHeight) {
                // Horizontal: la vista a la izquierda y los controles a la derecha, sin taparse.
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    CoverPreview(state, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        CoverControls(state, onSelectPosition, openGallery)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    CoverPreview(state, Modifier.fillMaxWidth().aspectRatio(1.1f))
                    Spacer(Modifier.height(16.dp))
                    CoverControls(state, onSelectPosition, openGallery)
                }
            }
        }
    }
}

@Composable
private fun CoverPreview(state: ImportState.ChoosingCover, modifier: Modifier) {
    val image = state.galleryImage ?: state.preview
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AssetImage(image, Modifier.fillMaxSize(), maxSidePx = PREVIEW_SIDE_PX, contentDescription = previewDescription(state))
        }
    }
}

@Composable
private fun previewDescription(state: ImportState.ChoosingCover): String =
    if (state.galleryImage != null) {
        stringResource(R.string.cover_gallery_selected)
    } else {
        stringResource(R.string.cover_frame_at, formatDuration(state.positionMs))
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverControls(
    state: ImportState.ChoosingCover,
    onSelectPosition: (Long) -> Unit,
    onOpenGallery: () -> Unit,
) {
    val usingGallery = state.galleryImage != null
    Text(
        previewDescription(state),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    val positionLabel = stringResource(R.string.cover_position)
    val sliderState = remember(state.durationMs) {
        SliderState(state.positionMs.toFloat(), 0, 0f..state.durationMs.coerceAtLeast(1).toFloat())
    }
    // Un fotograma sugerido también mueve el deslizador.
    LaunchedEffect(sliderState, state.positionMs) { sliderState.value = state.positionMs.toFloat() }
    Slider(
        state = sliderState,
        onValueChange = {
            sliderState.value = it
            onSelectPosition(it.toLong())
        },
        modifier = Modifier.semantics { contentDescription = positionLabel },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(0), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(state.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(R.string.cover_suggested),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(state.suggestions, key = { it.positionMs }) { frame ->
            SuggestedFrameItem(
                frame = frame,
                selected = !usingGallery && frame.positionMs == state.positionMs,
                onClick = { onSelectPosition(frame.positionMs) },
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onOpenGallery, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(UiR.drawable.ic_image), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.cover_use_gallery))
    }
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(
            painterResource(UiR.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(if (usingGallery) R.string.cover_gallery_hint else R.string.cover_saved_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SuggestedFrameItem(frame: SuggestedFrame, selected: Boolean, onClick: () -> Unit) {
    val time = formatDuration(frame.positionMs)
    val label = stringResource(R.string.cover_suggested_item, time)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .then(
                    if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer), RoundedCornerShape(12.dp)) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            frame.thumbnail?.let { thumb -> AssetImage(thumb, Modifier.fillMaxSize(), maxSidePx = THUMBNAIL_SIDE_PX) }
            if (selected) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(UiR.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            time,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Lado con que se leen la vista previa grande y las miniaturas sugeridas. */
private const val PREVIEW_SIDE_PX = 720
private const val THUMBNAIL_SIDE_PX = 192
