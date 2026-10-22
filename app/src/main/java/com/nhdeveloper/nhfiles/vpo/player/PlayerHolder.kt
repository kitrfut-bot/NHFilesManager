package com.nhdeveloper.nhfiles.vpo.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.nhdeveloper.nhfiles.vpo.data.VideoItem
import com.nhdeveloper.nhfiles.vpo.ui.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide scope for tiny DB writes that must survive an activity closing. */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Owns the single ExoPlayer so playback can continue in [PlaybackService]
 * (background audio) after the activity goes away.
 */
object PlayerHolder {
    var player: ExoPlayer? = null
        private set
    val audioDelay = AudioDelayProcessor()
    var queue: List<VideoItem> = emptyList()
    var pending: Pair<List<VideoItem>, Int>? = null

    fun getOrCreate(context: Context): ExoPlayer {
        player?.let { return it }
        val app = context.applicationContext
        val factory = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(audioDelay))
                .build()
        }
            // ON = use the phone's own decoders first; fall back to the FFmpeg extension
            // (AC-3 / E-AC-3 / DTS / TrueHD ... which many phones cannot decode).
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(app, factory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        (p.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector)?.let { ts ->
            ts.setParameters(ts.buildUponParameters().setConstrainAudioChannelCountToDeviceCapabilities(false))
        }
        player = p
        return p
    }

    fun release(context: Context) {
        context.applicationContext.stopService(Intent(context.applicationContext, PlaybackService::class.java))
        player?.release()
        player = null
        queue = emptyList()
        audioDelay.delayMs = 0
    }
}

object PlayerLauncher {
    fun play(ctx: Context, items: List<VideoItem>, index: Int) {
        if (items.isEmpty()) return
        PlayerHolder.pending = items to index.coerceIn(0, items.lastIndex)
        val opts = android.app.ActivityOptions.makeCustomAnimation(ctx, com.nhdeveloper.nhfiles.R.anim.fade_in, com.nhdeveloper.nhfiles.R.anim.fade_out)
        val intent = Intent(ctx, PlayerActivity::class.java)
        if (ctx !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent, opts.toBundle())
    }
}
