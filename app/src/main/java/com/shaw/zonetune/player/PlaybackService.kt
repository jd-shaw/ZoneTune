package com.shaw.zonetune.player

import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.shaw.zonetune.ZoneTuneApp

/**
 * Media3 session service: notification + lock-screen / headset controls.
 * Queue next/prev are routed through [PlayerController] (single ExoPlayer item).
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        val controller = ZoneTuneApp.instance.playerController
        val player = QueueAwarePlayer(controller.player, controller)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

/** Exposes next/prev even with a single MediaItem so notification buttons work. */
private class QueueAwarePlayer(
    player: ExoPlayer,
    private val controller: PlayerController,
) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands()
            .buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: @Player.Command Int): Boolean =
        when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> true
            else -> super.isCommandAvailable(command)
        }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        controller.playNext()
    }

    override fun seekToNextMediaItem() {
        controller.playNext()
    }

    override fun seekToPrevious() {
        controller.playPrev()
    }

    override fun seekToPreviousMediaItem() {
        controller.playPrev()
    }
}
