package dev.tacuchi.hope.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.tacuchi.hope.ui.common.CoverImage
import dev.tacuchi.hope.ui.common.formatDuration
import dev.tacuchi.hope.ui.common.openLink
import dev.tacuchi.hope.ui.R as UiR

/** Ancho máximo de la cuadrícula: en horizontal deja libre la esquina del botón «Nueva». */
private val GridMaxWidth = 840.dp

/** Alto de la franja del botón «Nueva»: el botón (56) más sus márgenes. */
private val FabClearance = 88.dp

@Composable
fun LibraryScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssets: () -> Unit,
    onNewHope: () -> Unit,
    onEditHope: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LibraryContent(
        state = state,
        onOpenSearch = onOpenSearch,
        onOpenSettings = onOpenSettings,
        onOpenAssets = onOpenAssets,
        onNewHope = onNewHope,
        onTogglePlay = viewModel::togglePlay,
        onEdit = { onEditHope(it.id) },
        onDelete = viewModel::delete,
        onOpenOriginal = { item -> item.linkUrl?.let { openLink(context, it) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssets: () -> Unit,
    onNewHope: () -> Unit,
    onTogglePlay: (LibraryItem) -> Unit,
    onEdit: (LibraryItem) -> Unit,
    onDelete: (LibraryItem) -> Unit,
    onOpenOriginal: (LibraryItem) -> Unit,
) {
    var toDelete by remember { mutableStateOf<LibraryItem?>(null) }
    val items = state.items
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            painterResource(UiR.drawable.ic_travel_explore),
                            contentDescription = stringResource(R.string.library_search),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(UiR.drawable.ic_settings), stringResource(UiR.string.settings))
                    }
                    IconButton(onClick = onOpenAssets) {
                        Icon(
                            painterResource(UiR.drawable.ic_perm_media),
                            contentDescription = stringResource(R.string.library_assets),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!items.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNewHope,
                    icon = { Icon(painterResource(UiR.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.library_new)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                items == null -> Unit
                items.isEmpty() -> EmptyLibrary(onNewHope, onOpenSearch)
                else -> HopeGrid(items, state.playingId, onTogglePlay, onEdit, onOpenOriginal, onAskDelete = { toDelete = it })
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.library_delete_title, item.title)) },
            text = { Text(stringResource(R.string.library_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    onDelete(item)
                }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(UiR.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HopeGrid(
    items: List<LibraryItem>,
    playingId: Long?,
    onTogglePlay: (LibraryItem) -> Unit,
    onEdit: (LibraryItem) -> Unit,
    onOpenOriginal: (LibraryItem) -> Unit,
    onAskDelete: (LibraryItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.widthIn(max = GridMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = FabClearance),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.library_your_hopes),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    pluralStringResource(R.plurals.library_count, items.size, items.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items, key = { it.id }) { item ->
            HopeCard(
                item = item,
                playing = item.id == playingId,
                onTogglePlay = { onTogglePlay(item) },
                onEdit = { onEdit(item) },
                onOpenOriginal = { onOpenOriginal(item) },
                onDelete = { onAskDelete(item) },
            )
        }
    }
}

@Composable
private fun HopeCard(
    item: LibraryItem,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onEdit: () -> Unit,
    onOpenOriginal: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val playLabel = stringResource(if (playing) R.string.library_pause else R.string.library_play, item.title)
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        modifier = Modifier.clickable(onClickLabel = playLabel, role = Role.Button, onClick = onTogglePlay),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            // La portada con sus efectos, dibujada sobre la original sin cambiarla.
            item.coverFile?.let { CoverImage(it, item.coverEffects, Modifier.fillMaxSize(), maxSidePx = 480) }
            if (playing) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(UiR.drawable.ic_pause), contentDescription = null, tint = colors.onPrimaryContainer)
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) {
                        Box(Modifier.size(6.dp).background(colors.primary, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        formatDuration(item.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (playing) colors.primary else colors.onSurfaceVariant,
                    )
                    if (item.linkUrl != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painterResource(UiR.drawable.ic_link),
                            contentDescription = stringResource(R.string.library_has_link),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            HopeMenu(item.title, hasLink = item.linkUrl != null, onEdit, onOpenOriginal, onDelete)
        }
    }
}

@Composable
private fun HopeMenu(title: String, hasLink: Boolean, onEdit: () -> Unit, onOpenOriginal: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(UiR.drawable.ic_more_vert), contentDescription = stringResource(R.string.library_more, title))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_edit)) },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_edit), contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            if (hasLink) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_open_original)) },
                    leadingIcon = { Icon(painterResource(UiR.drawable.ic_open_in_new), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onOpenOriginal()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onNewHope: () -> Unit, onOpenSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(UiR.drawable.ic_graphic_eq),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNewHope,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(painterResource(UiR.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.library_new_hope), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenSearch,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Icon(painterResource(UiR.drawable.ic_travel_explore), contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.library_find_inspiration), style = MaterialTheme.typography.titleMedium)
        }
    }
}
