package com.nhdeveloper.nhfiles.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhdeveloper.nhfiles.data.ApkInfo
import com.nhdeveloper.nhfiles.data.ApkReader
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.Intents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ApkResult {
    data class Ok(val info: ApkInfo) : ApkResult
    data class Fail(val message: String) : ApkResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkScreen(vm: MainViewModel, file: File) {
    val ctx = LocalContext.current
    val result by produceState<ApkResult?>(null, file) {
        value = withContext(Dispatchers.IO) {
            try {
                ApkResult.Ok(ApkReader.read(ctx, file))
            } catch (e: Exception) {
                ApkResult.Fail(e.message ?: "Cannot read this APK")
            }
        }
    }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text("APK Info") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            GlassBar {
                Button(
                    onClick = { confirm = true },
                    enabled = result is ApkResult.Ok,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                ) { Text("Install") }
            }
        },
    ) { pad ->
        when (val r = result) {
            null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ApkResult.Fail -> Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(r.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            is ApkResult.Ok -> ApkDetails(r.info, Modifier.padding(pad))
        }
    }

    val ok = result as? ApkResult.Ok
    if (confirm && ok != null) {
        ConfirmDialog(
            "Install ${ok.info.label}?",
            "Package ${ok.info.packageName}\nVersion ${ok.info.versionName} (${ok.info.versionCode})\n\nAndroid will show its own installer to confirm.",
            "Continue",
            onConfirm = { confirm = false; Intents.install(ctx, file)?.let { vm.message(it) } },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun ApkDetails(i: ApkInfo, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard(shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val bmp = i.icon
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                    } else {
                        Icon(Icons.Filled.Android, null, tint = kindColor(com.nhdeveloper.nhfiles.data.FileKind.APK), modifier = Modifier.size(56.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(i.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(i.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(FileUtils.formatSize(i.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            InfoCard(
                listOf(
                    "Package name" to i.packageName,
                    "Version name" to i.versionName,
                    "Version code" to i.versionCode.toString(),
                    "Min SDK" to "${i.minSdk} (${ApkReader.androidName(i.minSdk)})",
                    "Target SDK" to "${i.targetSdk} (${ApkReader.androidName(i.targetSdk)})",
                )
            )
        }
        item { Text("Signing certificate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        if (i.certs.isEmpty()) {
            item { Text("Not signed or certificate unavailable", style = MaterialTheme.typography.bodySmall) }
        }
        items(i.certs) { c ->
            InfoCard(
                listOf(
                    "Subject" to c.subject, "Issuer" to c.issuer, "SHA-256" to c.sha256,
                    "Valid from" to c.validFrom, "Valid until" to c.validTo,
                )
            )
        }
        item { Text("Permissions (${i.permissions.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        items(i.permissions) { p ->
            Text(p, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        item { Text("Package entries (${i.entries.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        items(i.entries.take(500)) { e ->
            Row(Modifier.fillMaxWidth()) {
                Text(e.name, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!e.isDir) Text(FileUtils.formatSize(e.size.coerceAtLeast(0)), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (i.entries.size > 500) item { Text("…and ${i.entries.size - 500} more entries", style = MaterialTheme.typography.labelSmall) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    GlassCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (k, v) ->
                Column {
                    Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
