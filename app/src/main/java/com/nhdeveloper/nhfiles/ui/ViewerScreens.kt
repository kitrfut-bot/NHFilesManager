package com.nhdeveloper.nhfiles.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhfiles.audio.AudioPlayerHolder
import com.nhdeveloper.nhfiles.audio.AudioQueueBuilder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nhdeveloper.nhfiles.data.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.nhdeveloper.nhfiles.ui.theme.GlassLevel
import com.nhdeveloper.nhfiles.ui.theme.glass
import com.nhdeveloper.nhfiles.ui.theme.glassTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScaffold(
    vm: MainViewModel,
    file: File,
    actions: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (actions) {
                GlassBar {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceAround) {
                        IconButton(onClick = { vm.share(listOf(file)) }) { Icon(Icons.Filled.Share, "Share", tint = Color(0xFF43A047).glassTone()) }
                        IconButton(onClick = { vm.openExternal(file) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open with", tint = Color(0xFF42A5F5).glassTone()) }
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
    ) { pad -> content(Modifier.padding(pad)) }
    if (confirm) {
        ConfirmDialog("Delete", "Delete \"${file.name}\"? This cannot be undone.", "Delete", true,
            onConfirm = { confirm = false; vm.pop(); vm.delete(listOf(file)) }, onDismiss = { confirm = false })
    }
}

@Composable
private fun Unsupported(vm: MainViewModel, file: File, msg: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.openExternal(file) }) { Text("Open with another app") }
    }
}

// ---------------------------------------------------------------------------------------------
// Image
// ---------------------------------------------------------------------------------------------

private fun decodeImage(file: File, maxDim: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = try {
        when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    if (degrees == 0f) return bmp
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

@Composable
fun ImageScreen(vm: MainViewModel, file: File) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            try { decodeImage(file) } catch (e: Throwable) { null }
        }
    }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(bmp) { if (bmp == null) { delay(1500); failed = true } }
    ViewerScaffold(vm, file) { mod ->
        Box(mod.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                Image(
                    b.asImageBitmap(), file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2.5f
                                offset = Offset.Zero
                            })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                val o = offset + pan * scale
                                offset = Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            } else if (failed) {
                Unsupported(vm, file, "This image cannot be previewed.", Modifier)
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Video
// ---------------------------------------------------------------------------------------------

@Composable
fun VideoScreen(vm: MainViewModel, file: File) {
    var error by remember { mutableStateOf(false) }
    ViewerScaffold(vm, file) { mod ->
        if (error) {
            Unsupported(vm, file, "This video format cannot be played inside the app.", mod)
        } else {
            Box(mod.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val mc = MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { it.isLooping = false; start() }
                            setOnErrorListener { _, _, _ -> error = true; true }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Audio
// ---------------------------------------------------------------------------------------------

private data class AudioMeta(val title: String, val artist: String, val art: Bitmap?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(vm: MainViewModel, file: File) {
    val ctx = LocalContext.current
    LaunchedEffect(file) {
        val (tracks, idx) = AudioQueueBuilder.buildFor(file)
        AudioPlayerHolder.play(ctx, tracks, idx)
    }
    val ui by AudioPlayerHolder.state.collectAsStateWithLifecycle()
    var dragPos by remember { mutableStateOf<Long?>(null) }

    val current = ui.queue.getOrNull(ui.index)
    val meta by produceState(AudioMeta(current?.title ?: file.name, current?.artist ?: "", null), current?.path) {
        val t = current
        value = if (t == null) {
            AudioMeta(file.name, "", null)
        } else {
            withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(t.path)
                    val art = r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    AudioMeta(
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: t.title,
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: t.artist,
                        art,
                    )
                } catch (e: Exception) {
                    AudioMeta(t.title, t.artist, null)
                } finally {
                    try { r.release() } catch (e: Exception) { /* ignore */ }
                }
            }
        }
    }

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.playing) {
        while (ui.playing) {
            delay(250)
            tick++
        }
    }
    val pos = dragPos ?: run { tick; AudioPlayerHolder.currentPosition() }
    val dur = ui.duration

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text("Now Playing · Background", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(180.dp).glass(RoundedCornerShape(32.dp), GlassLevel.Strong),
                contentAlignment = Alignment.Center,
            ) {
                val art = meta.art
                if (art != null) {
                    Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.MusicNote, null, tint = kindColor(com.nhdeveloper.nhfiles.data.FileKind.AUDIO), modifier = Modifier.size(84.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, textAlign = TextAlign.Center)
            if (meta.artist.isNotEmpty()) Text(meta.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ui.queue.size > 1) {
                Text(
                    "Track ${ui.index + 1} of ${ui.queue.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Keeps playing when you leave this screen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Slider(
                value = if (dur > 0) pos.toFloat() / dur else 0f,
                onValueChange = { dragPos = (it * dur).toLong() },
                onValueChangeFinished = {
                    dragPos?.let { AudioPlayerHolder.seekTo(it) }
                    dragPos = null
                },
                enabled = ui.ready,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(pos.toInt()), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(dur.toInt()), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { AudioPlayerHolder.previous() }, enabled = ui.queue.size > 1) {
                    Icon(Icons.Filled.SkipPrevious, "Previous track")
                }
                IconButton(onClick = { AudioPlayerHolder.seekRelative(-10_000) }, enabled = ui.ready) {
                    Icon(Icons.Filled.Replay10, "Back 10 seconds")
                }
                IconButton(
                    onClick = { AudioPlayerHolder.togglePlay() },
                    enabled = ui.ready,
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play or pause",
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { AudioPlayerHolder.seekRelative(10_000) }, enabled = ui.ready) {
                    Icon(Icons.Filled.Forward10, "Forward 10 seconds")
                }
                IconButton(onClick = { AudioPlayerHolder.next() }, enabled = ui.queue.size > 1) {
                    Icon(Icons.Filled.SkipNext, "Next track")
                }
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { AudioPlayerHolder.stopAndRelease(ctx); vm.pop() }) { Text("Stop playback") }
        }
    }
}

private fun formatTime(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------------------------------------
// PDF
// ---------------------------------------------------------------------------------------------

private class PdfHolder {
    var renderer: PdfRenderer? = null
    val mutex = Mutex()
}

@Composable
fun PdfScreen(vm: MainViewModel, file: File) {
    val holder = remember { PdfHolder() }
    var pages by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        try {
            val r = withContext(Dispatchers.IO) {
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            }
            holder.renderer = r
            pages = r.pageCount
        } catch (e: Exception) {
            error = "This PDF cannot be previewed (it may be protected or damaged)."
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { holder.renderer?.close() } catch (e: Exception) { /* ignore */ }
            holder.renderer = null
        }
    }
    ViewerScaffold(vm, file) { mod ->
        val err = error
        if (err != null) {
            Unsupported(vm, file, err, mod)
        } else if (pages == 0) {
            Box(mod.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            BoxWithConstraints(mod.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                val widthPx = constraints.maxWidth.coerceIn(200, 1440)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                    items(pages) { index -> PdfPage(holder, index, widthPx) }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(holder: PdfHolder, index: Int, widthPx: Int) {
    val bmp by produceState<Bitmap?>(null, index) {
        value = withContext(Dispatchers.IO) {
            holder.mutex.withLock {
                val r = holder.renderer ?: return@withLock null
                try {
                    r.openPage(index).use { page ->
                        val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(AColor.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    } else {
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
