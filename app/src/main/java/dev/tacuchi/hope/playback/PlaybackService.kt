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
import dev.tacuchi.hope.MainActivity
import dev.tacuchi.hope.R
import dev.tacuchi.hope.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var attachedSource: Player? = null
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val mix get() = appContainer.mixPlayer

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
            mix.state.map { it.key to it.presentation }.distinctUntilChanged().collect {
                val current = mix.state.value
                if (current.key == null) endSession()
                else (session?.player as? SessionPlayer)?.show(current.presentation)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = mix.sessionSource
        if (source != null && source !== attachedSource) {
            clearSession()
            val wrapper = SessionPlayer(source, getString(R.string.library_new_hope), mix::resume, mix::pause) {
                mix.state.value.key?.let(mix::stop)
            }
            wrapper.show(mix.state.value.presentation)
            val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_close)
                .setSessionCommand(close)
                .setDisplayName(getString(R.string.action_close))
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
