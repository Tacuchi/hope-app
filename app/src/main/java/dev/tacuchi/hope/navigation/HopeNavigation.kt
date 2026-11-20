package dev.tacuchi.hope.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.tacuchi.hope.ui.assets.AssetsScreen
import dev.tacuchi.hope.ui.editor.NewHopeScreen
import dev.tacuchi.hope.ui.importing.ImportScreen
import dev.tacuchi.hope.ui.importing.ImportState
import dev.tacuchi.hope.ui.importing.ImportViewModel
import dev.tacuchi.hope.ui.library.LibraryScreen
import dev.tacuchi.hope.ui.search.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data object AssetsRoute : NavKey

@Serializable
data object NewHopeRoute : NavKey

/** Una importación en curso; la muestra el estado de [ImportViewModel], no la pila. */
@Serializable
data object ImportRoute : NavKey

/** Pila guardable: sobrevive a la rotación, al cambio de tema y a la muerte del proceso. */
@Composable
fun HopeNavigation(importViewModel: ImportViewModel) {
    val backStack = rememberNavBackStack(LibraryRoute)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    // Una importación activa se muestra encima; al terminar se ve Assets con lo nuevo arriba.
    LaunchedEffect(importState) {
        when (importState) {
            ImportState.Idle -> backStack.removeAll { it == ImportRoute }
            ImportState.Finished -> {
                backStack.removeAll { it == ImportRoute }
                if (backStack.lastOrNull() != AssetsRoute) backStack.add(AssetsRoute)
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
        entryProvider = entryProvider {
            entry<LibraryRoute> {
                LibraryScreen(
                    onOpenSearch = dropUnlessResumed { backStack.add(SearchRoute) },
                    onOpenAssets = dropUnlessResumed { backStack.add(AssetsRoute) },
                    onNewHope = dropUnlessResumed { backStack.add(NewHopeRoute) },
                )
            }
            entry<SearchRoute> { SearchScreen(onBack = dropUnlessResumed(block = goBack)) }
            entry<AssetsRoute> {
                AssetsScreen(
                    onBack = dropUnlessResumed(block = goBack),
                    onImport = { uri, origin -> importViewModel.start(uri, declaredMime = null, origin = origin) },
                )
            }
            entry<NewHopeRoute> { NewHopeScreen(onClose = dropUnlessResumed(block = goBack)) }
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
