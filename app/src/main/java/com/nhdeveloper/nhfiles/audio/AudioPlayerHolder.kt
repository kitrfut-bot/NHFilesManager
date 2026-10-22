package com.nhdeveloper.nhfiles.audio

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nhdeveloper.nhfiles.data.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class AudioTrack(val path: String, val name: String, val title: String, val artist: String)

data class AudioUiState(
    val queue: List<AudioTrack> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val ready: Boolean = false,
)

/**
 * Owns a single ExoPlayer for audio, independent of the screen that started it, so playback
 * (and the notification from [AudioPlaybackService]) survives the Audio screen being closed.
 */
object AudioPlayerHolder {
    var player: ExoPlayer? = null
        private set

    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state

    private var listener: Player.Listener? = null

    private fun getOrCreate(context: Context): ExoPlayer {
        player?.let { return it }
        val app = context.applicationContext
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(playing = isPlaying)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = p.currentMediaItemIndex
                _state.value = _state.value.copy(index = idx, duration = p.duration.coerceAtLeast(0))
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(ready = true, duration = p.duration.coerceAtLeast(0))
                }
            }
        }
        p.addListener(l)
        listener = l
        player = p
        return p
    }

    /** Starts (or replaces) the queue and begins foreground playback via [AudioPlaybackService]. */
    fun play(context: Context, tracks: List<AudioTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val app = context.applicationContext
        val p = getOrCreate(app)
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setUri(File(t.path).toUri())
                .setMediaId(t.path)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build()
                )
                .build()
        }
        _state.value = AudioUiState(queue = tracks, index = startIndex, ready = false)
        p.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        p.prepare()
        p.playWhenReady = true
        app.startService(Intent(app, AudioPlaybackService::class.java))
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekTo(ms: Long) { player?.seekTo(ms) }
    fun seekRelative(deltaMs: Long) {
        val p = player ?: return
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, p.duration.coerceAtLeast(0)))
    }
    fun next() { player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun previous() { player?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }

    fun currentPosition(): Long = player?.currentPosition ?: 0L

    /** Stops playback entirely and releases the player (does not just pause). */
    fun stopAndRelease(context: Context) {
        context.applicationContext.stopService(Intent(context.applicationContext, AudioPlaybackService::class.java))
        listener?.let { player?.removeListener(it) }
        player?.release()
        player = null
        listener = null
        _state.value = AudioUiState()
    }

    private fun File.toUri() = android.net.Uri.fromFile(this)
}

object AudioQueueBuilder {
    /** All playable audio files in the same folder as [f], sorted by name, with [f]'s index. */
    fun buildFor(f: File): Pair<List<AudioTrack>, Int> {
        val dir = f.parentFile
        val files = (dir?.listFiles() ?: arrayOf(f))
            .filter { it.isFile && FileUtils.kindOf(it.name, false) == com.nhdeveloper.nhfiles.data.FileKind.AUDIO }
            .sortedBy { it.name.lowercase() }
            .ifEmpty { listOf(f) }
        val tracks = files.map { t ->
            AudioTrack(t.absolutePath, t.name, FileUtils.baseName(t.name), dir?.name ?: "")
        }
        val idx = files.indexOfFirst { it.absolutePath == f.absolutePath }.coerceAtLeast(0)
        return tracks to idx
    }
}
