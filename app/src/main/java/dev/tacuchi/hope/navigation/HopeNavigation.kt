package dev.tacuchi.hope.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.tacuchi.hope.ui.assets.AssetsScreen
import dev.tacuchi.hope.ui.editor.NewHopeScreen
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

/** Pila guardable: sobrevive a la rotación, al cambio de tema y a la muerte del proceso. */
@Composable
fun HopeNavigation() {
    val backStack = rememberNavBackStack(LibraryRoute)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }

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
            entry<AssetsRoute> { AssetsScreen(onBack = dropUnlessResumed(block = goBack)) }
            entry<NewHopeRoute> { NewHopeScreen(onClose = dropUnlessResumed(block = goBack)) }
        },
    )
}
