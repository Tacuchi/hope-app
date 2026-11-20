package dev.tacuchi.hope.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetType
import dev.tacuchi.hope.ui.assets.AssetsScreen
import dev.tacuchi.hope.ui.editor.EditorArgs
import dev.tacuchi.hope.ui.editor.EditorImport
import dev.tacuchi.hope.ui.editor.EditorScreen
import dev.tacuchi.hope.ui.editor.EditorSlot
import dev.tacuchi.hope.ui.importing.ImportPurpose
import dev.tacuchi.hope.ui.importing.ImportScreen
import dev.tacuchi.hope.ui.importing.ImportState
import dev.tacuchi.hope.ui.importing.ImportViewModel
import dev.tacuchi.hope.ui.library.LibraryScreen
import dev.tacuchi.hope.ui.search.SearchScreen
import dev.tacuchi.hope.ui.settings.LicensesScreen
import dev.tacuchi.hope.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object LicensesRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data object AssetsRoute : NavKey

/** El editor: una hope guardada ([hopeId]) o una nueva, con assets o un enlace que pueden venir puestos. */
@Serializable
data class EditorRoute(
    val hopeId: Long? = null,
    val coverAssetId: Long? = null,
    val backgroundAssetId: Long? = null,
    val linkUrl: String? = null,
) : NavKey

/** Una importación en curso; la muestra el estado de [ImportViewModel], no la pila. */
@Serializable
data object ImportRoute : NavKey

/** Nueva hope con lo que trajo una importación: el audio de fondo y la imagen de portada. */
private fun editorWith(assets: List<Asset>) = EditorRoute(
    coverAssetId = assets.firstOrNull { it.type == AssetType.Image }?.id,
    backgroundAssetId = assets.firstOrNull { it.type == AssetType.Audio }?.id,
)

private fun EditorSlot.purpose() = when (this) {
    EditorSlot.Cover -> ImportPurpose.EditorCover
    EditorSlot.Background -> ImportPurpose.EditorBackground
}

/**
 * Pila guardable: sobrevive a la rotación, al cambio de tema y a la muerte del proceso. Cada
 * destino tiene sus propios ViewModels, que se limpian al salir de la pila.
 */
@Composable
fun HopeNavigation(
    importViewModel: ImportViewModel,
    sharedLink: String? = null,
    onSharedLinkConsumed: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(LibraryRoute)

    // Un enlace compartido abre Nueva hope con el enlace puesto.
    LaunchedEffect(sharedLink) {
        if (sharedLink != null) {
            backStack.add(EditorRoute(linkUrl = sharedLink))
            onSharedLinkConsumed()
        }
    }
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    var editorImport by remember { mutableStateOf<EditorImport?>(null) }

    // Una importación activa se muestra encima; al terminar, lo creado va adonde la pidieron.
    LaunchedEffect(importState) {
        when (val state = importState) {
            ImportState.Idle -> backStack.removeAll { it == ImportRoute }
            is ImportState.Finished -> {
                backStack.removeAll { it == ImportRoute }
                when (state.purpose) {
                    ImportPurpose.Catalog -> if (backStack.lastOrNull() != AssetsRoute) backStack.add(AssetsRoute)
                    ImportPurpose.Share -> backStack.add(editorWith(state.assets))
                    ImportPurpose.EditorCover -> editorImport = EditorImport(EditorSlot.Cover, state.assets)
                    ImportPurpose.EditorBackground -> editorImport = EditorImport(EditorSlot.Background, state.assets)
                }
                importViewModel.acknowledgeFinished()
            }
            else -> if (backStack.lastOrNull() != ImportRoute) {
                backStack.removeAll { it == ImportRoute }
                backStack.add(ImportRoute)
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<LibraryRoute> {
                LibraryScreen(
                    onOpenSearch = dropUnlessResumed { backStack.add(SearchRoute) },
                    onOpenSettings = dropUnlessResumed { backStack.add(SettingsRoute) },
                    onOpenAssets = dropUnlessResumed { backStack.add(AssetsRoute) },
                    onNewHope = dropUnlessResumed { backStack.add(EditorRoute()) },
                    onEditHope = { id -> backStack.add(EditorRoute(hopeId = id)) },
                )
            }
            entry<SettingsRoute> { SettingsScreen(dropUnlessResumed(block = goBack), dropUnlessResumed { backStack.add(LicensesRoute) }) }
            entry<LicensesRoute> { LicensesScreen(dropUnlessResumed(block = goBack)) }
            entry<SearchRoute> { SearchScreen(onBack = dropUnlessResumed(block = goBack)) }
            entry<AssetsRoute> {
                AssetsScreen(
                    onBack = dropUnlessResumed(block = goBack),
                    onImport = { uri, origin -> importViewModel.start(uri, declaredMime = null, origin = origin) },
                    onUseInNewHope = { asset -> backStack.add(editorWith(listOf(asset))) },
                    onOpenHope = { id -> backStack.add(EditorRoute(hopeId = id)) },
                )
            }
            entry<EditorRoute> { route ->
                EditorScreen(
                    args = EditorArgs(route.hopeId, route.coverAssetId, route.backgroundAssetId, route.linkUrl),
                    imported = editorImport,
                    onImportedConsumed = { editorImport = null },
                    onImport = { uri, origin, slot ->
                        importViewModel.start(uri, declaredMime = null, origin = origin, purpose = slot.purpose())
                    },
                    onClose = dropUnlessResumed(block = goBack),
                    // Guardada, la hope se ve en la biblioteca.
                    onSaved = { while (backStack.size > 1) backStack.removeLastOrNull() },
                )
            }
            entry<ImportRoute> {
                ImportScreen(
                    state = importState,
                    onCancel = importViewModel::cancel,
                    onContinueLong = importViewModel::continueLong,
                    onChooseCoverOnly = importViewModel::chooseCoverOnly,
                    onSelectPosition = importViewModel::selectPosition,
                    onPickGallery = importViewModel::useGalleryImage,
                    onUse = importViewModel::confirmCover,
                )
            }
        },
    )
}
