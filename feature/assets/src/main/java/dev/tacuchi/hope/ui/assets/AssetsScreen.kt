package dev.tacuchi.hope.ui.assets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.ui.R as UiR
import dev.tacuchi.hope.ui.common.assetName
import dev.tacuchi.hope.ui.common.assetSubtitle

/** Ancho máximo del contenido: en horizontal deja libre la esquina del botón «Importar». */
private val ContentMaxWidth = 600.dp

/** Alto de la franja del botón «Importar»: el botón (56) más sus márgenes. */
private val FabClearance = 88.dp

/** Margen lateral que necesita el botón «Importar» para quedar al costado de la lista. */
private val FabSideSpace = 176.dp

@Composable
fun AssetsScreen(
    onBack: () -> Unit,
    onImport: (uri: Uri, origin: AssetOrigin) -> Unit,
    onUseInNewHope: (Asset) -> Unit,
    onOpenHope: (Long) -> Unit,
    viewModel: AssetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickVisual = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImport(uri, AssetOrigin.Gallery)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri, AssetOrigin.Files)
    }
    AssetsContent(
        state = state,
        onBack = onBack,
        onSelectFilter = viewModel::selectFilter,
        onTogglePlay = viewModel::togglePlay,
        onDelete = viewModel::delete,
        onUseInNewHope = onUseInNewHope,
        onUseInHope = { asset, hopeId -> viewModel.useInHope(asset, hopeId, onOpenHope) },
        onImportVisual = {
            pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        },
        onImportAudio = { pickAudio.launch(arrayOf("audio/*")) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsContent(
    state: AssetsUiState,
    onBack: () -> Unit,
    onSelectFilter: (AssetFilter) -> Unit,
    onTogglePlay: (Asset) -> Unit,
    onDelete: (Asset) -> Unit,
    onUseInNewHope: (Asset) -> Unit,
    onUseInHope: (asset: Asset, hopeId: Long) -> Unit,
    onImportVisual: () -> Unit,
    onImportAudio: () -> Unit,
) {
    var toDelete by remember { mutableStateOf<Asset?>(null) }
    var toPlace by remember { mutableStateOf<Asset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(UiR.drawable.ic_arrow_back),
                            contentDescription = stringResource(UiR.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = { ImportButton(onImportVisual, onImportAudio) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilterRow(state.filter, onSelectFilter)
            val assets = state.assets
            when {
                assets == null -> Unit
                assets.isEmpty() -> EmptyState(filtered = state.filter != AssetFilter.All)
                else -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    // Con margen lateral libre (horizontal) el botón queda al costado de la lista. Si no
                    // (vertical), la lista termina sobre la franja del botón para que nunca tape una fila.
                    val sideSpace = (maxWidth - ContentMaxWidth) / 2
                    val fabHasItsOwnColumn = sideSpace >= FabSideSpace
                    AssetList(
                        assets = assets,
                        playingId = state.playingId,
                        uses = state.uses,
                        canUseInExisting = state.hopes.isNotEmpty(),
                        onTogglePlay = onTogglePlay,
                        onAskDelete = { toDelete = it },
                        onUseInNewHope = onUseInNewHope,
                        onAskUseInHope = { toPlace = it },
                        modifier = Modifier
                            .widthIn(max = ContentMaxWidth)
                            .fillMaxSize()
                            .padding(bottom = if (fabHasItsOwnColumn) 0.dp else FabClearance),
                    )
                }
            }
        }
    }

    toPlace?.let { asset ->
        AlertDialog(
            onDismissRequest = { toPlace = null },
            title = { Text(stringResource(R.string.assets_choose_hope_title, assetName(asset))) },
            text = {
                LazyColumn {
                    items(state.hopes, key = { it.id }) { hope ->
                        ListItem(
                            headlineContent = { Text(hope.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                toPlace = null
                                onUseInHope(asset, hope.id)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { toPlace = null }) { Text(stringResource(UiR.string.action_cancel)) }
            },
        )
    }

    toDelete?.let { asset ->
        val name = assetName(asset)
        val usedBy = state.uses[asset.id].orEmpty()
        if (usedBy.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { toDelete = null },
                icon = { Icon(painterResource(UiR.drawable.ic_info), contentDescription = null) },
                title = { Text(stringResource(R.string.assets_in_use_title, name)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pluralStringResource(R.plurals.assets_in_use_body, usedBy.size, usedBy.size))
                        usedBy.forEach { use ->
                            Text(
                                stringResource(R.string.assets_in_use_item, use.title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { toDelete = null }) { Text(stringResource(UiR.string.action_accept)) }
                },
            )
            return@let
        }
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.assets_delete_title, name)) },
            text = { Text(stringResource(R.string.assets_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    onDelete(asset)
                }) { Text(stringResource(R.string.assets_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(UiR.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun AssetList(
    assets: List<Asset>,
    playingId: Long?,
    uses: Map<Long, List<AssetUse>>,
    canUseInExisting: Boolean,
    onTogglePlay: (Asset) -> Unit,
    onAskDelete: (Asset) -> Unit,
    onUseInNewHope: (Asset) -> Unit,
    onAskUseInHope: (Asset) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    // Un asset nuevo entra arriba: la lista vuelve al inicio para mostrarlo.
    // Solo cuando cambia de verdad: girar la pantalla conserva la posición.
    val newestId = assets.firstOrNull()?.id
    var shownNewestId by rememberSaveable { mutableStateOf(newestId) }
    LaunchedEffect(newestId) {
        if (newestId != shownNewestId) {
            listState.scrollToItem(0)
            shownNewestId = newestId
        }
    }
    LazyColumn(modifier = modifier, state = listState) {
        items(assets, key = { it.id }) { asset ->
            AssetRow(
                asset = asset,
                playing = asset.id == playingId,
                useCount = uses[asset.id]?.size ?: 0,
                canUseInExisting = canUseInExisting,
                onTogglePlay = { onTogglePlay(asset) },
                onDelete = { onAskDelete(asset) },
                onUseInNewHope = { onUseInNewHope(asset) },
                onUseInHope = { onAskUseInHope(asset) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun FilterRow(selectedFilter: AssetFilter, onSelect: (AssetFilter) -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssetFilter.entries.forEach { option ->
            val selected = option == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.label)) },
                leadingIcon = {
                    val icon = if (selected) UiR.drawable.ic_check else option.icon
                    Icon(painterResource(icon), contentDescription = null, Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    iconColor = MaterialTheme.colorScheme.primary,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 48.dp, bottom = FabClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (filtered) {
                Text(
                    text = stringResource(R.string.assets_empty_filtered),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    painterResource(UiR.drawable.ic_perm_media),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.assets_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.assets_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AssetRow(
    asset: Asset,
    playing: Boolean,
    useCount: Int,
    canUseInExisting: Boolean,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
    onUseInNewHope: () -> Unit,
    onUseInHope: () -> Unit,
) {
    val name = assetName(asset)
    val clickModifier = if (asset.type == AssetType.Audio) {
        val label = stringResource(if (playing) R.string.assets_pause else R.string.assets_play, name)
        Modifier.clickable(onClickLabel = label, onClick = onTogglePlay)
    } else {
        Modifier
    }
    ListItem(
        modifier = clickModifier,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        leadingContent = { AssetTile(asset, playing) },
        supportingContent = {
            Column {
                Text(
                    assetSubtitle(asset),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (useCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.assets_in_use, useCount, useCount),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        trailingContent = { AssetMenu(name, canUseInExisting, onDelete, onUseInNewHope, onUseInHope) },
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AssetTile(asset: Asset, playing: Boolean) {
    val icon = when {
        playing -> UiR.drawable.ic_pause
        asset.nameKind == AssetNameKind.Recording -> UiR.drawable.ic_mic
        asset.type == AssetType.Audio -> UiR.drawable.ic_music_note
        else -> UiR.drawable.ic_image
    }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                if (playing) colors.primaryContainer else colors.surfaceContainerHigh,
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (playing) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssetMenu(
    name: String,
    canUseInExisting: Boolean,
    onDelete: () -> Unit,
    onUseInNewHope: () -> Unit,
    onUseInHope: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.assets_more, name),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.assets_use_new_hope)) },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_add), contentDescription = null) },
                onClick = {
                    expanded = false
                    onUseInNewHope()
                },
            )
            // Sin hopes guardadas no hay dónde ponerlo.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.assets_use_existing_hope)) },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_bookmark_add), contentDescription = null) },
                onClick = {
                    expanded = false
                    onUseInHope()
                },
                enabled = canUseInExisting,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.assets_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        painterResource(UiR.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
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
private fun ImportButton(onImportVisual: () -> Unit, onImportAudio: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            onClick = { expanded = true },
            icon = { Icon(painterResource(UiR.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(UiR.string.assets_import)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.assets_import_visual)) },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_image), contentDescription = null) },
                onClick = {
                    expanded = false
                    onImportVisual()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.assets_import_audio)) },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_music_note), contentDescription = null) },
                onClick = {
                    expanded = false
                    onImportAudio()
                },
            )
        }
    }
}
