package me.Gabix.zene

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Alapértelmezetten bekapcsoljuk az összes szám ismétlését (REPEAT_MODE_ALL)
        player?.repeatMode = Player.REPEAT_MODE_ALL

        player?.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateNotificationLayout()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateNotificationLayout()
            }
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(COMMAND_CYCLE_MODE, Bundle.EMPTY))
                        .add(SessionCommand(COMMAND_CLOSE_APP, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        COMMAND_CYCLE_MODE -> {
                            cyclePlaybackMode(session.player)
                            return Futures.immediateFuture(
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            )
                        }
                        COMMAND_CLOSE_APP -> {
                            // Leállítjuk a lejátszást
                            session.player.stop()
                            // Eltávolítjuk a MediaSession-t, ami megszünteti a Foreground értesítést
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            session.release()
                            // Bezárjuk a zenelejátszó Service-t
                            stopSelf()
                            // Teljesen leállítjuk az alkalmazás folyamatát (processzét)
                            Process.killProcess(Process.myPid())
                            return Futures.immediateFuture(
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            )
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        updateNotificationLayout()
    }

    private fun updateNotificationLayout() {
        val p = player ?: return
        val iconResId = when {
            p.shuffleModeEnabled -> R.drawable.ic_shuffle
            p.repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            p.repeatMode == Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
            else -> R.drawable.ic_repeat_off
        }
        val displayName = when {
            p.shuffleModeEnabled -> "Keverés"
            p.repeatMode == Player.REPEAT_MODE_ONE -> "Egy szám ismétlése"
            p.repeatMode == Player.REPEAT_MODE_ALL -> "Összes ismétlése"
            else -> "Nincs ismétlés"
        }

        val modeButton = CommandButton.Builder()
            .setDisplayName(displayName)
            .setIconResId(iconResId)
            .setSessionCommand(SessionCommand(COMMAND_CYCLE_MODE, Bundle.EMPTY))
            .build()

        val closeButton = CommandButton.Builder()
            .setDisplayName("Bezárás")
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(SessionCommand(COMMAND_CLOSE_APP, Bundle.EMPTY))
            .build()

        mediaSession?.setCustomLayout(listOf(modeButton, closeButton))
    }

    private fun cyclePlaybackMode(player: Player) {
        if (player.shuffleModeEnabled) {
            player.shuffleModeEnabled = false
            player.repeatMode = Player.REPEAT_MODE_ALL
            return
        }
        when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> player.repeatMode = Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = true
            }
            else -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val COMMAND_CYCLE_MODE = "me.Gabix.zene.CYCLE_MODE"
        const val COMMAND_CLOSE_APP = "me.Gabix.zene.CLOSE_APP"
    }
}