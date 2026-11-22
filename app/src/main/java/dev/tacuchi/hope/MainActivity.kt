package dev.tacuchi.hope

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.tacuchi.hope.domain.model.OriginResolver
import dev.tacuchi.hope.domain.model.Links
import dev.tacuchi.hope.navigation.HopeNavigation
import dev.tacuchi.hope.ui.importing.ImportPurpose
import dev.tacuchi.hope.ui.importing.ImportViewModel
import dev.tacuchi.hope.ui.settings.SettingsViewModel
import dev.tacuchi.hope.ui.theme.HopeTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Una importación por actividad: la navegación y lo compartido usan la misma instancia. */
    private val importViewModel: ImportViewModel by viewModels()

    /** La apariencia: la actividad pinta con ella y Ajustes la cambia, desde la misma instancia. */
    private val settingsViewModel: SettingsViewModel by viewModels()

    /** Un enlace recibido por Compartir, hasta que la navegación abre Nueva hope con él. */
    private var sharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Al recrear la actividad el intent es el mismo: solo se importa la primera vez.
        if (savedInstanceState == null) importShared(intent)
        setContent {
            val appearance by settingsViewModel.appearance.collectAsStateWithLifecycle()
            HopeTheme(appearance) {
                val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                HopeNavigation(
                    importViewModel = importViewModel,
                    settingsViewModel = settingsViewModel,
                    sharedLink = sharedLink,
                    onSharedLinkConsumed = { sharedLink = null },
                )
            }
        }
    }

    /** Hope ya estaba abierta (singleTask): lo compartido llega a la misma instancia. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importShared(intent)
    }

    private fun importShared(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        if (intent.type == "text/plain") {
            receiveLink(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            return
        }
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        val origin = OriginResolver.fromReferrer(referrer?.toString())
        importViewModel.start(uri, intent.type, origin, ImportPurpose.Share)
    }

    /** Del texto compartido («Mira esto https://…») se toma el primer enlace; nada se descarga. */
    private fun receiveLink(text: String) {
        val link = Links.firstIn(text)
        if (link == null) {
            Toast.makeText(this, R.string.link_shared_without_url, Toast.LENGTH_SHORT).show()
            return
        }
        sharedLink = link.url
    }
}
