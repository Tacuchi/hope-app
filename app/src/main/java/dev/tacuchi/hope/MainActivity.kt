package dev.tacuchi.hope

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.tacuchi.hope.importing.OriginResolver
import dev.tacuchi.hope.links.Links
import dev.tacuchi.hope.navigation.HopeNavigation
import dev.tacuchi.hope.ui.importing.ImportPurpose
import dev.tacuchi.hope.ui.importing.ImportViewModel
import dev.tacuchi.hope.ui.theme.HopeTheme

class MainActivity : ComponentActivity() {

    private val importViewModel: ImportViewModel by viewModels { ImportViewModel.Factory }

    /** Un enlace recibido por Compartir, hasta que la navegación abre Nueva hope con él. */
    private var sharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Al recrear la actividad el intent es el mismo: solo se importa la primera vez.
        if (savedInstanceState == null) importShared(intent)
        setContent {
            HopeTheme {
                HopeNavigation(importViewModel, sharedLink, onSharedLinkConsumed = { sharedLink = null })
            }
        }
    }

    /** La hope solo suena con la app a la vista: salir o bloquear la pantalla la pausa. */
    override fun onStop() {
        super.onStop()
        appContainer.mixPlayer.pause()
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
