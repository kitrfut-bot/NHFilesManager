package com.nhdeveloper.nhfiles.vpo.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Rational
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.vpo.data.*
import com.nhdeveloper.nhfiles.databinding.ActivityPlayerBinding
import com.nhdeveloper.nhfiles.databinding.DialogStepperBinding
import com.nhdeveloper.nhfiles.databinding.DialogSubtitleStyleBinding
import com.nhdeveloper.nhfiles.vpo.player.*
import com.nhdeveloper.nhfiles.vpo.util.formatDuration
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerActivity : AppCompatActivity() {
    companion object { const val EXTRA_RESUME = "resume" }

    private lateinit var b: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var dao: AppDao
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var settings = AppSettings()

    private var locked = false
    private var controlsVisible = true
    private var seeking = false
    private var aspectMode = 0
    private var videoSize = VideoSize.UNKNOWN
    private var inPip = false

    // subtitles loaded from a file (parsed so delay can be applied)
    private var loadedCues: List<Cue>? = null
    private var loadedSubName = "Subtitle"
    private var subtitleDelayMs = 0L
    private var subtitleCounter = 0

    // gestures
    private enum class G { NONE, SEEK, BRIGHT, VOLUME }
    private var gesture = G.NONE
    private var gStartPos = 0L
    private var gTarget = 0L
    private var gStartBright = 0.5f
    private var gStartVol = 0f

    private val hideRunnable = Runnable { if (player.isPlaying && !seeking) setControls(false) }
    private val tick = object : Runnable {
        override fun run() { updateProgress(); handler.postDelayed(this, 500) }
    }

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSubtitleFile(uri)
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            b.btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            b.btnPlayPause.contentDescription = if (isPlaying) "Pause" else "Play"
            if (isPlaying) scheduleHide() else handler.removeCallbacks(hideRunnable)
        }

        override fun onPlaybackStateChanged(state: Int) {
            b.buffering.isVisible = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) b.errorBox.isVisible = false
            if (state == Player.STATE_ENDED && !locked) setControls(true, persist = true)
            updateNavButtons()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            b.tvTitle.text = mediaItem?.mediaMetadata?.title
            loadedCues = null
            updateNavButtons()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                val old = oldPosition.mediaItem ?: return
                record(old.mediaId, old.mediaMetadata.title?.toString() ?: "", 0L, durationOf(old.mediaId))
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            b.buffering.isVisible = false
            b.errorBox.isVisible = true
            b.btnSkip.isVisible = player.hasNextMediaItem()
            b.tvError.text = "Can't play this video (${error.errorCodeName}).\n" +
                "The file may be damaged or use a codec this device doesn't support."
            setControls(true, persist = true)
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            videoSize = size
            applyAspect()
            updatePipParams()
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge, including behind the camera notch / cutout (true full screen)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        // keep buttons out of the notch area, but let the video fill everything
        ViewCompat.setOnApplyWindowInsetsListener(b.controlsLayer) { v, insets ->
            val c = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(c.left, c.top, c.right, c.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        dao = AppDatabase.get(this).dao()
        settingsRepo = SettingsRepository(applicationContext)
        player = PlayerHolder.getOrCreate(applicationContext)
        player.addListener(listener)

        setupControls()
        setupGestures()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) Toast.makeText(this@PlayerActivity, "Screen is locked", Toast.LENGTH_SHORT).show()
                else finish()
            }
        })
        b.videoContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (aspectMode >= 3) applyAspect() }

        lifecycleScope.launch {
            settings = settingsRepo.current()
            aspectMode = settings.defaultAspect
            applySubtitleStyle()
            applyAspect()
            startFromIntent(intent)
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.fade_in, R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { startFromIntent(intent) }
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, PlaybackService::class.java))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        b.playerView.player = player
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tick)
        savePosition()
        if (isFinishing) return
        if (settings.bgAudio && player.isPlaying) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
            b.playerView.player = null
            ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        } else {
            player.pause()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        if (isFinishing) {
            b.playerView.player = null
            PlayerHolder.release(this)
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // ---------------------------------------------------------------- queue / start

    private suspend fun startFromIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_RESUME, false) && player.mediaItemCount > 0) {
            b.tvTitle.text = player.currentMediaItem?.mediaMetadata?.title
            return
        }
        val pending = PlayerHolder.pending
        PlayerHolder.pending = null
        val (items, index) = pending ?: externalItem(intent)?.let { listOf(it) to 0 } ?: run {
            if (player.mediaItemCount == 0) finish()
            return
        }
        PlayerHolder.queue = items
        val mediaItems = items.map { toMediaItem(it) }
        val h = if (settings.resume) dao.historyFor(items[index].uri) else null
        val start = if (h != null && h.position > 3_000 && h.position < h.duration - 3_000) h.position else 0L
        player.setMediaItems(mediaItems, index, start)
        player.setPlaybackSpeed(settings.defaultSpeed)
        player.pauseAtEndOfMediaItems = !settings.autoPlayNext
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(settings.audioLang.ifEmpty { null })
            .setPreferredTextLanguage(settings.subLang.ifEmpty { null })
            .build()
        player.prepare()
        player.playWhenReady = true
        b.tvTitle.text = items[index].title
    }

    private fun externalItem(intent: Intent): VideoItem? {
        val uri = intent.data ?: return null
        var name = uri.lastPathSegment ?: "Video"
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0) ?: name
                }
            } catch (_: Exception) { }
        }
        return VideoItem(uri.toString(), name, 0, 0, 0, 0, "ext", "", "", 0)
    }

    private fun toMediaItem(v: VideoItem): MediaItem {
        val b = MediaItem.Builder().setUri(v.parsedUri).setMediaId(v.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(v.title).build())
        v.subtitleUri?.let { s ->
            val mime = if (s.lowercase().endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            b.setSubtitleConfigurations(listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(s)).setMimeType(mime)
                    .setLabel("External").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        }
        return b.build()
    }

    private fun durationOf(uri: String): Long = PlayerHolder.queue.firstOrNull { it.uri == uri }?.durationMs ?: 0L

    private fun record(uri: String, name: String, pos: Long, dur: Long) {
        if (dur <= 0) return
        AppScope.launch { dao.upsertHistory(HistoryEntity(uri, name, pos, dur, System.currentTimeMillis())) }
    }

    private fun savePosition() {
        val item = player.currentMediaItem ?: return
        val dur = player.duration
        if (dur <= 0 || dur == C.TIME_UNSET) return
        val pos = player.currentPosition
        val finished = pos >= dur - 3_000
        record(item.mediaId, item.mediaMetadata.title?.toString() ?: "", if (finished) 0 else pos, dur)
    }

    // ---------------------------------------------------------------- controls

    private fun setupControls() {
        b.btnBack.setOnClickListener { finish() }
        b.btnPlayPause.setOnClickListener {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.isPlaying) player.pause() else player.play()
            scheduleHide()
        }
        b.btnPrev.setOnClickListener { player.seekToPreviousMediaItem(); scheduleHide() }
        b.btnNext.setOnClickListener { player.seekToNextMediaItem(); scheduleHide() }
        b.btnBack10.setOnClickListener { seekBy(-10_000) }
        b.btnFwd10.setOnClickListener { seekBy(10_000) }
        b.btnBack30.setOnClickListener { seekBy(-30_000) }
        b.btnFwd30.setOnClickListener { seekBy(30_000) }
        b.toolLock.setOnClickListener { setLocked(true) }
        b.btnUnlock.setOnClickListener { setLocked(false) }
        b.toolSubtitle.setOnClickListener { showSubtitleDialog() }
        b.btnSubtitleTop.setOnClickListener { showSubtitleDialog() }
        b.toolAudio.setOnClickListener { showAudioDialog() }
        b.toolSpeed.setOnClickListener { showSpeedDialog() }
        b.toolAspect.setOnClickListener { showAspectDialog() }
        b.toolPip.setOnClickListener { enterPip() }
        b.btnMore.setOnClickListener { showMoreMenu(it) }
        b.btnRetry.setOnClickListener { b.errorBox.isVisible = false; player.prepare(); player.play() }
        b.btnSkip.setOnClickListener { b.errorBox.isVisible = false; player.seekToNextMediaItem(); player.prepare(); player.play() }
        b.btnCloseError.setOnClickListener { finish() }

        b.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) b.tvPosition.text = formatDuration(p.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar) { seeking = true; handler.removeCallbacks(hideRunnable) }
            override fun onStopTrackingTouch(sb: SeekBar) {
                player.seekTo(sb.progress.toLong()); seeking = false; scheduleHide()
            }
        })
    }

    private fun seekBy(deltaMs: Long) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, dur))
        scheduleHide()
    }

    private fun updateProgress() {
        if (seeking) return
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        b.seekBar.max = dur.toInt().coerceAtLeast(1)
        b.seekBar.progress = player.currentPosition.coerceAtMost(dur).toInt()
        b.seekBar.secondaryProgress = player.bufferedPosition.coerceAtMost(dur).toInt()
        b.tvPosition.text = formatDuration(player.currentPosition)
        b.tvDuration.text = formatDuration(dur)
    }

    private fun updateNavButtons() {
        b.btnPrev.alpha = if (player.hasPreviousMediaItem()) 1f else 0.35f
        b.btnNext.alpha = if (player.hasNextMediaItem()) 1f else 0.35f
    }

    private fun setControls(show: Boolean, persist: Boolean = false) {
        if (locked && show) return
        controlsVisible = show
        val v = b.controlsLayer
        v.animate().cancel()
        if (show) {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(150).start()
            if (!persist) scheduleHide()
        } else {
            v.animate().alpha(0f).setDuration(150).withEndAction { v.visibility = View.GONE }.start()
        }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        if (controlsVisible) handler.postDelayed(hideRunnable, 4000)
    }

    private fun setLocked(lock: Boolean) {
        locked = lock
        if (lock) {
            setControls(false)
            b.btnUnlock.isVisible = true
            handler.postDelayed({ if (locked) b.btnUnlock.isVisible = false }, 3000)
            Toast.makeText(this, "Controls locked", Toast.LENGTH_SHORT).show()
        } else {
            b.btnUnlock.isVisible = false
            setControls(true)
        }
    }

    // ---------------------------------------------------------------- gestures

    private fun setupGestures() {
        var scaling = false
        var scaleAcc = 1f
        var scaleDone = false
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean { scaling = true; scaleAcc = 1f; scaleDone = false; return true }
            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (locked || scaleDone) return true
                scaleAcc *= d.scaleFactor
                if (scaleAcc > 1.15f) { aspectMode = 2; applyAspect(); flashInfo("Full screen (Crop)"); hideInfoLater(); scaleDone = true }
                else if (scaleAcc < 0.87f) { aspectMode = 0; applyAspect(); flashInfo("Fit"); hideInfoLater(); scaleDone = true }
                return true
            }
            override fun onScaleEnd(d: ScaleGestureDetector) { scaling = false }
        })
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                gesture = G.NONE
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) {
                    b.btnUnlock.isVisible = true
                    handler.postDelayed({ if (locked) b.btnUnlock.isVisible = false }, 3000)
                } else setControls(!controlsVisible)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val left = e.x < b.playerRoot.width / 2f
                seekBy(if (left) -10_000 else 10_000)
                flashInfo(if (left) "⏪ 10s" else "10s ⏩")
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (locked || e1 == null) return false
                val totalX = e2.x - e1.x
                val totalY = e2.y - e1.y
                if (gesture == G.NONE) {
                    if (abs(totalX) < 40 && abs(totalY) < 40) return true
                    gesture = if (abs(totalX) > abs(totalY)) G.SEEK
                    else if (e1.x < b.playerRoot.width / 2f) G.BRIGHT else G.VOLUME
                    gStartPos = player.currentPosition
                    gStartBright = currentBrightness()
                    gStartVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                }
                when (gesture) {
                    G.SEEK -> {
                        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return true
                        gTarget = (gStartPos + (totalX / b.playerRoot.width * 120_000f).toLong()).coerceIn(0, dur)
                        val diff = (gTarget - gStartPos) / 1000
                        flashInfo("${formatDuration(gTarget)}  (${if (diff >= 0) "+" else "−"}${abs(diff)}s)", sticky = true)
                    }
                    G.BRIGHT -> {
                        val v = (gStartBright - totalY / b.playerRoot.height * 1.2f).coerceIn(0.02f, 1f)
                        val lp = window.attributes; lp.screenBrightness = v; window.attributes = lp
                        flashInfo("Brightness ${(v * 100).roundToInt()}%", sticky = true)
                    }
                    G.VOLUME -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val v = (gStartVol - totalY / b.playerRoot.height * 1.2f).coerceIn(0f, 1f)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt(), 0)
                        flashInfo("Volume ${(v * 100).roundToInt()}%", sticky = true)
                    }
                    else -> {}
                }
                return true
            }
        })
        b.gestureLayer.setOnTouchListener { _, ev ->
            scaleDetector.onTouchEvent(ev)
            if (scaling || ev.pointerCount > 1) { gesture = G.NONE; return@setOnTouchListener true }
            val handled = detector.onTouchEvent(ev)
            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
                if (gesture == G.SEEK) player.seekTo(gTarget)
                if (gesture != G.NONE) hideInfoLater()
                gesture = G.NONE
            }
            handled
        }
    }

    private fun currentBrightness(): Float {
        val w = window.attributes.screenBrightness
        if (w >= 0) return w
        return try {
            android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) { 0.5f }
    }

    private val hideInfo = Runnable { b.gestureInfo.isVisible = false }
    private fun flashInfo(text: String, sticky: Boolean = false) {
        b.gestureInfo.text = text
        b.gestureInfo.isVisible = true
        handler.removeCallbacks(hideInfo)
        if (!sticky) handler.postDelayed(hideInfo, 700)
    }
    private fun hideInfoLater() { handler.removeCallbacks(hideInfo); handler.postDelayed(hideInfo, 500) }

    // ---------------------------------------------------------------- aspect ratio

    private fun applyAspect() {
        val pv = b.playerView
        val lp = pv.layoutParams as FrameLayout.LayoutParams
        when (aspectMode) {
            0, 1, 2 -> {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                pv.resizeMode = when (aspectMode) {
                    0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            }
            else -> {
                val cw = b.videoContainer.width
                val ch = b.videoContainer.height
                if (cw == 0 || ch == 0) return
                val ratio = if (aspectMode == 3) 16f / 9f else 4f / 3f
                var w = cw; var h = (w / ratio).toInt()
                if (h > ch) { h = ch; w = (h * ratio).toInt() }
                if (lp.width == w && lp.height == h) return
                lp.width = w; lp.height = h; lp.gravity = android.view.Gravity.CENTER
                pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        }
        pv.layoutParams = lp
    }

    private fun showAspectDialog() {
        MaterialAlertDialogBuilder(this).setTitle("Aspect ratio")
            .setSingleChoiceItems(SettingsFragment.ASPECTS, aspectMode) { d, i ->
                aspectMode = i; applyAspect(); d.dismiss()
            }.show()
    }

    // ---------------------------------------------------------------- speed

    private fun showSpeedDialog() {
        val speeds = SettingsFragment.SPEEDS
        val labels = arrayOf("0.25x", "0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
        val cur = speeds.indexOfFirst { it == player.playbackParameters.speed }
        MaterialAlertDialogBuilder(this).setTitle("Playback speed")
            .setSingleChoiceItems(labels, cur) { d, i -> player.setPlaybackSpeed(speeds[i]); d.dismiss() }.show()
    }

    // ---------------------------------------------------------------- tracks

    private fun trackLabel(f: Format, n: Int, kind: String): String {
        val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
        val base = f.label ?: lang ?: "$kind $n"
        val extra = if (f.channelCount > 0) when (f.channelCount) {
            1 -> " • Mono"; 2 -> " • Stereo"; 6 -> " • 5.1"; 8 -> " • 7.1"; else -> " • ${f.channelCount}ch"
        } else ""
        return base + extra
    }

    private fun codecName(mime: String?): String = when (mime) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC-3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        null -> ""
        else -> mime.substringAfter('/').uppercase()
    }

    private fun showAudioDialog() {
        // list ALL audio tracks, even ones this phone can't decode (they are marked), so nothing "disappears"
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        val builder = MaterialAlertDialogBuilder(this).setTitle("Audio track")
            .setNeutralButton("Audio delay…") { _, _ -> showAudioDelayDialog() }
        if (groups.isEmpty()) {
            builder.setMessage("No audio tracks found yet. The video may have no sound, or it is still loading.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = groups.mapIndexed { i, g ->
            val f = g.getTrackFormat(0)
            val codec = codecName(f.sampleMimeType)
            val label = trackLabel(f, i + 1, "Audio")
            label + (if (codec.isNotEmpty()) " • $codec" else "") + (if (!g.isSupported) "  ⚠ can't be decoded" else "")
        }.toTypedArray()
        builder.setSingleChoiceItems(labels, groups.indexOfFirst { it.isSelected }) { d, i ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(groups[i].mediaTrackGroup, 0)).build()
            if (!groups[i].isSupported)
                Toast.makeText(this, "This audio format can't be decoded on this device, so it may be silent.", Toast.LENGTH_LONG).show()
            d.dismiss()
        }.show()
    }

    private fun showAudioDelayDialog() {
        val sb = DialogStepperBinding.inflate(LayoutInflater.from(this))
        var v = PlayerHolder.audioDelay.delayMs
        fun refresh() { sb.stepperValue.text = "$v ms"; PlayerHolder.audioDelay.delayMs = v }
        sb.stepperHint.text = "Delays the sound so it plays later than the picture (0 to 2000 ms). " +
            "Use it when sound is ahead of video. Works for 16-bit PCM output."
        sb.btnMinus.setOnClickListener { v = (v - 50).coerceAtLeast(0); refresh() }
        sb.btnPlus.setOnClickListener { v = (v + 50).coerceAtMost(2000); refresh() }
        sb.btnReset.setOnClickListener { v = 0; refresh() }
        refresh()
        MaterialAlertDialogBuilder(this).setTitle("Audio delay").setView(sb.root)
            .setPositiveButton("Done", null).show()
    }

    private fun showSubtitleDialog() {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        val labels = (listOf("Off") + groups.mapIndexed { i, g -> trackLabel(g.getTrackFormat(0), i + 1, "Subtitle") }).toTypedArray()
        val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val selected = if (textDisabled) 0 else (groups.indexOfFirst { it.isSelected } + 1)
        MaterialAlertDialogBuilder(this).setTitle("Subtitles")
            .setSingleChoiceItems(labels, selected) { d, i ->
                val p = player.trackSelectionParameters.buildUpon()
                if (i == 0) p.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                else p.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(groups[i - 1].mediaTrackGroup, 0))
                player.trackSelectionParameters = p.build()
                d.dismiss()
            }
            .setNeutralButton("Load file…") { _, _ -> subtitlePicker.launch(arrayOf("*/*")) }
            .setPositiveButton("Style & delay") { _, _ -> showSubtitleStyleDialog() }
            .show()
    }

    private fun loadSubtitleFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val cues = SubtitleTools.parse(SubtitleTools.decode(bytes))
                if (cues.isEmpty()) { Toast.makeText(this@PlayerActivity, "Not a valid SRT/VTT file", Toast.LENGTH_LONG).show(); return@launch }
                loadedCues = cues
                loadedSubName = uri.lastPathSegment?.substringAfterLast('/') ?: "Subtitle"
                applyLoadedSubtitle()
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, "Couldn't read subtitle file", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyLoadedSubtitle() {
        val cues = loadedCues ?: return
        val current = player.currentMediaItem ?: return
        val file = SubtitleTools.writeShiftedVtt(java.io.File(cacheDir, "subs"), cues, subtitleDelayMs, ++subtitleCounter)
        val item = current.buildUpon().setSubtitleConfigurations(listOf(
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file)).setMimeType(MimeTypes.TEXT_VTT)
                .setLabel(loadedSubName).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build())).build()
        val idx = player.currentMediaItemIndex
        val pos = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
        player.replaceMediaItem(idx, item)
        player.seekTo(idx, pos)
        player.playWhenReady = wasPlaying
    }

    private fun showSubtitleStyleDialog() {
        val d = DialogSubtitleStyleBinding.inflate(LayoutInflater.from(this))
        var scale = settings.subtitleScale
        var color = settings.subtitleColor
        var delay = subtitleDelayMs
        d.sizeSlider.value = scale.coerceIn(0.5f, 2f)
        fun sizeText() { d.sizeLabel.text = "Size: ${(scale * 100).toInt()}%" }
        fun delayText() { d.delayValue.text = "${if (delay > 0) "+" else ""}$delay ms" }
        d.sizeSlider.addOnChangeListener { _, v, _ -> scale = v; sizeText() }
        val ids = intArrayOf(R.id.cWhite, R.id.cYellow, R.id.cCyan, R.id.cOrange)
        d.colorGroup.check(ids[SettingsFragment.COLORS.indexOf(color).coerceAtLeast(0)])
        d.colorGroup.setOnCheckedChangeListener { _, id -> color = SettingsFragment.COLORS[ids.indexOf(id).coerceAtLeast(0)] }
        d.delayLabel.text = "Subtitle delay"
        d.delayNote.text = "Delay applies to subtitle files you load. Embedded subtitle tracks can't be shifted."
        d.btnMinus.setOnClickListener { delay = (delay - 100).coerceAtLeast(-10_000); delayText() }
        d.btnPlus.setOnClickListener { delay = (delay + 100).coerceAtMost(10_000); delayText() }
        sizeText(); delayText()
        MaterialAlertDialogBuilder(this).setTitle("Subtitle style").setView(d.root)
            .setPositiveButton("Apply") { _, _ ->
                lifecycleScope.launch {
                    settingsRepo.set(Keys.SUBSCALE, scale); settingsRepo.set(Keys.SUBCOLOR, color)
                    settings = settingsRepo.current()
                    applySubtitleStyle()
                }
                if (delay != subtitleDelayMs) {
                    subtitleDelayMs = delay
                    if (loadedCues != null) applyLoadedSubtitle()
                    else Toast.makeText(this, "Load a subtitle file to use delay", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun applySubtitleStyle() {
        b.playerView.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * settings.subtitleScale)
            setStyle(CaptionStyleCompat(settings.subtitleColor, Color.TRANSPARENT, Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null))
        }
    }

    // ---------------------------------------------------------------- more / pip

    private fun showMoreMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, if (player.repeatMode == Player.REPEAT_MODE_ONE) "Loop: on" else "Loop: off")
        pm.menu.add(0, 2, 1, "Share")
        pm.menu.add(0, 3, 2, "Open with…")
        pm.menu.add(0, 4, 3, "Video info")
        pm.setOnMenuItemClickListener {
            val v = currentVideo()
            when (it.itemId) {
                1 -> player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                2 -> v?.let { x -> VideoMenu.share(this, x) }
                3 -> v?.let { x -> VideoMenu.openWith(this, x) }
                4 -> v?.let { x -> VideoMenu.info(this, x) }
            }
            true
        }
        pm.show()
    }

    private fun currentVideo(): VideoItem? {
        val id = player.currentMediaItem?.mediaId ?: return null
        return PlayerHolder.queue.firstOrNull { it.uri == id }
    }

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < 26) return null
        val w = if (videoSize.width > 0) videoSize.width else 16
        val h = if (videoSize.height > 0) videoSize.height else 9
        val r = (w.toFloat() / h).coerceIn(0.42f, 2.38f)
        return PictureInPictureParams.Builder().setAspectRatio(Rational((r * 1000).toInt(), 1000)).build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= 26) pipParams()?.let { try { setPictureInPictureParams(it) } catch (_: Exception) { } }
    }

    private fun enterPip() {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Picture-in-Picture isn't supported on this device", Toast.LENGTH_SHORT).show(); return
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                enterPictureInPictureMode(pipParams()!!)
            } else {
                @Suppress("DEPRECATION")
                enterPictureInPictureMode()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't enter Picture-in-Picture", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (settings.autoPip && player.isPlaying && !locked) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (inPip) {
            b.controlsLayer.visibility = View.GONE
            b.btnUnlock.isVisible = false
        } else if (!locked) {
            b.controlsLayer.alpha = 1f
            b.controlsLayer.visibility = View.VISIBLE
            controlsVisible = true
            scheduleHide()
        }
    }
}
