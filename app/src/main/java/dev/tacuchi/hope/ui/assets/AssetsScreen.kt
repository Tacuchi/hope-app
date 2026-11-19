package dev.tacuchi.hope.ui.assets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.R

private enum class AssetFilter(@StringRes val label: Int, @DrawableRes val icon: Int) {
    All(R.string.assets_filter_all, R.drawable.ic_check),
    Audio(R.string.assets_filter_audio, R.drawable.ic_graphic_eq),
    Image(R.string.assets_filter_image, R.drawable.ic_image),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(AssetFilter.All) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = { DisabledImportButton() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssetFilter.entries.forEach { option ->
                    val selected = option == filter
                    FilterChip(
                        selected = selected,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.label)) },
                        leadingIcon = {
                            val icon = if (selected) R.drawable.ic_check else option.icon
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_perm_media),
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

/**
 * Importar llega en una fase posterior. El FAB de M3 no tiene estado deshabilitado, así que se pinta
 * con los colores de deshabilitado de M3 y se anuncia deshabilitado y sin acción.
 */
@Composable
private fun DisabledImportButton() {
    val label = stringResource(R.string.assets_import)
    val disabledContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ExtendedFloatingActionButton(
        onClick = {},
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = disabledContent,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(label) },
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = label
            disabled()
        },
    )
}
