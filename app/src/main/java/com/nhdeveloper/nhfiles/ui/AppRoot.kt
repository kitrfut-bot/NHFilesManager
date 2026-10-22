package com.nhdeveloper.nhfiles.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhfiles.data.Conflict
import com.nhdeveloper.nhfiles.data.PermissionManager
import com.nhdeveloper.nhfiles.ui.theme.GlassLevel
import com.nhdeveloper.nhfiles.ui.theme.glass
import com.nhdeveloper.nhfiles.ui.theme.glassTone

@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    var hasAccess by remember { mutableStateOf(PermissionManager.hasAccess(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) hasAccess = PermissionManager.hasAccess(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(hasAccess) { if (hasAccess) vm.onAccessGranted() }

    if (!hasAccess) {
        PermissionScreen(onResult = { hasAccess = PermissionManager.hasAccess(ctx) })
        return
    }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    val progress by vm.progress.collectAsStateWithLifecycle()

    BackHandler(enabled = vm.stack.size > 1 || vm.tab != 0) { vm.onBack() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = vm.stack.last(),
            transitionSpec = {
                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }) togetherWith fadeOut(tween(120))
            },
            label = "screen",
        ) { top ->
          when (top) {
            Screen.Tabs -> TabsScreen(vm)
            Screen.Browser -> BrowserScreen(vm)
            Screen.Search -> SearchScreen(vm)
            Screen.Analysis -> AnalysisScreen(vm)
            is Screen.Zip -> ZipScreen(vm, top.file)
            is Screen.Apk -> ApkScreen(vm, top.file)
            is Screen.Editor -> EditorScreen(vm, top.file)
            is Screen.Image -> ImageScreen(vm, top.file)
            is Screen.Video -> VideoScreen(vm, top.file)
            is Screen.Audio -> AudioScreen(vm, top.file)
            is Screen.Pdf -> PdfScreen(vm, top.file)
          }
        }
        SnackbarHost(
            snack,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 100.dp),
        ) { data -> GlassSnackbar(data) }
    }

    progress?.let { ProgressDialog(it) { vm.cancelOp() } }
    vm.conflict?.let { c -> ConflictDialog(c.names, onChoose = { vm.resolveConflict(it) }) }
}

@Composable
private fun ConflictDialog(names: List<String>, onChoose: (Conflict?) -> Unit) {
    Dialog(onDismissRequest = { onChoose(null) }) {
        Box(Modifier.glass(RoundedCornerShape(28.dp), GlassLevel.Strong)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Items already exist", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                val shown = names.take(4).joinToString("\n") { "• $it" }
                val more = if (names.size > 4) "\n…and ${names.size - 4} more" else ""
                Text(shown + more, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onChoose(Conflict.RENAME) }, modifier = Modifier.fillMaxWidth()) { Text("Keep both (rename)") }
                TextButton(onClick = { onChoose(Conflict.REPLACE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Replace existing", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onChoose(Conflict.SKIP) }, modifier = Modifier.fillMaxWidth()) { Text("Skip existing") }
                TextButton(onClick = { onChoose(null) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onResult: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassTile(MaterialTheme.colorScheme.primary, 96.dp) {
            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary.glassTone(), modifier = Modifier.size(54.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Storage access needed", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            if (Build.VERSION.SDK_INT >= 30) {
                "NH Files is a file manager, so it needs \"All files access\" to browse, copy, move and edit files in shared storage. " +
                    "Nothing leaves your device. Tap the button, then enable access for NH Files and come back."
            } else {
                "NH Files needs permission to read and write shared storage so it can browse and manage your files. Nothing leaves your device."
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        ctx.startActivity(PermissionManager.allFilesIntent(ctx))
                    } catch (e: Exception) {
                        try {
                            ctx.startActivity(PermissionManager.allFilesFallbackIntent())
                        } catch (e2: Exception) {
                            ctx.startActivity(PermissionManager.appDetailsIntent(ctx))
                        }
                    }
                } else {
                    launcher.launch(PermissionManager.legacyPermissions())
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Grant access") }
        TextButton(onClick = { ctx.startActivity(PermissionManager.appDetailsIntent(ctx)) }) { Text("Open app settings") }
    }
}
