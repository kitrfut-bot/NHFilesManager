package com.nhdeveloper.nhfiles.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps audio playing (with a media-style notification and lock-screen controls) after the
 * Audio screen is closed or the app is backgrounded. The player itself lives in [AudioPlayerHolder].
 */
class AudioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val p = AudioPlayerHolder.player ?: return
        val open = Intent(this, com.nhdeveloper.nhfiles.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, 1001, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, p).setSessionActivity(pi).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = AudioPlayerHolder.player
        if (p == null || !p.isPlaying) {
            AudioPlayerHolder.stopAndRelease(this)
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
