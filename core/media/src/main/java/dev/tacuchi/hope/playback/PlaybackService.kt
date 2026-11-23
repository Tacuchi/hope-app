package dev.tacuchi.hope.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import dev.tacuchi.hope.cover.CoverComposer
import dev.tacuchi.hope.domain.model.MixPresentation
import dev.tacuchi.hope.media.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    /** La misma mezcla que suena desde la UI. Hilt la inyecta en `super.onCreate()`. */
    @Inject
    lateinit var mix: MixPlayer

    /** Compone la portada con sus efectos para la notificación y la pantalla de bloqueo. */
    @Inject
    lateinit var covers: CoverComposer

    private var session: MediaSession? = null
    private var attachedSource: Player? = null
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val close = SessionCommand("dev.tacuchi.hope.CLOSE", Bundle.EMPTY)
    private val controls = object : MediaSession.Callback {
        override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo):
            ListenableFuture<MediaSession.ConnectionResult> {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            if (controller.isTrusted || session.isMediaNotificationController(controller)) {
                result.setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon().add(close).build())
            }
            return Futures.immediateFuture(result.build())
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand != close) return super.onCustomCommand(session, controller, customCommand, args)
            session.player.stop()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        setForegroundServiceTimeoutMs(0)
        scope.launch {
            mix.state.map { it.key to it.presentation }.distinctUntilChanged().collectLatest {
                val current = mix.state.value
                if (current.key == null) {
                    endSession()
                } else {
                    showWithArtwork(current.presentation)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = mix.sessionSource
        if (source != null && source !== attachedSource) {
            clearSession()
            val wrapper = SessionPlayer(source, getString(R.string.playback_untitled), mix::resume, mix::pause) {
                mix.state.value.key?.let(mix::stop)
            }
            wrapper.show(mix.state.value.presentation)
            val shown = mix.state.value.presentation
            scope.launch { showWithArtwork(shown) }
            // La actividad de lanzamiento del paquete: el servicio no conoce la actividad de la app.
            val launcher = packageManager.getLaunchIntentForPackage(packageName)?.component
            val openApp = PendingIntent.getActivity(this, 0, Intent().setComponent(launcher),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_playback_close)
                .setSessionCommand(close)
                .setDisplayName(getString(R.string.playback_close))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build()
            session = MediaSession.Builder(this, wrapper)
                .setCallback(controls)
                .setSessionActivity(openApp)
                .setMediaButtonPreferences(listOf(closeButton))
                .build().also(::addSession)
            attachedSource = source
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Primero la portada original, enseguida; después la compuesta con sus efectos, que puede tardar. */
    private suspend fun showWithArtwork(presentation: MixPresentation) {
        (session?.player as? SessionPlayer)?.show(presentation)
        val artwork = withContext(Dispatchers.IO) { covers.artwork(presentation.cover, presentation.coverEffects) }
        if (artwork != presentation.cover && mix.state.value.presentation == presentation) {
            (session?.player as? SessionPlayer)?.show(presentation, artwork)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (mix.sessionSource?.playWhenReady != true) {
            mix.state.value.key?.let(mix::stop)
            endSession()
        }
    }

    private fun clearSession() {
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        attachedSource = null
    }

    private fun endSession() {
        clearSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        clearSession()
        super.onDestroy()
    }
}
