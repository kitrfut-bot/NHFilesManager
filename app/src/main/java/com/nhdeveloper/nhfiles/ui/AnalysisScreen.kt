package com.nhdeveloper.nhfiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhfiles.data.FileItem
import com.nhdeveloper.nhfiles.data.FileKind
import com.nhdeveloper.nhfiles.data.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(vm: MainViewModel) {
    val a = vm.analysis
    val status by vm.scanStatus.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text("Storage Analysis") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.startScan() }, enabled = !a.scanning) { Icon(Icons.Filled.Refresh, "Rescan") }
                },
            )
        },
        bottomBar = {
            if (a.selected.isNotEmpty()) {
                GlassBar {
                    Button(
                        onClick = { confirm = true },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    ) { Text("Review & delete ${a.selected.size} selected") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = a.tab) {
                listOf("Overview", "Large files", "Duplicates").forEachIndexed { i, t ->
                    Tab(selected = a.tab == i, onClick = { vm.setAnalysisTab(i) }, text = { Text(t) })
                }
            }
            val r = a.result
            when {
                a.scanning -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(status)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { vm.cancelScan() }) { Text("Cancel") }
                }
                r == null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No scan results yet")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.startScan() }) { Text("Scan storage") }
                }
                a.tab == 0 -> Overview(vm, r.byCategory, r.fileCount)
                a.tab == 1 -> FileList(vm, r.largest, a.selected, "Largest files. Tick items to review them for deletion.")
                else -> Duplicates(vm, r.duplicates, a.selected)
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            "Delete selected files?",
            "${a.selected.size} item(s) will be permanently deleted. Nothing is deleted without this confirmation.",
            "Delete", danger = true,
            onConfirm = { confirm = false; vm.deleteAnalysisSelection() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun Overview(vm: MainViewModel, cats: Map<FileKind, Long>, count: Int) {
    val st = vm.storage
    val total = cats.values.sum().coerceAtLeast(1L)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "${FileUtils.formatSize(st.used)} used of ${FileUtils.formatSize(st.total)} · ${FileUtils.formatSize(st.free)} free",
                style = MaterialTheme.typography.titleSmall,
            )
            Text("$count files scanned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val names = mapOf(
            FileKind.IMAGE to "Images", FileKind.VIDEO to "Videos", FileKind.AUDIO to "Audio", FileKind.ARCHIVE to "Archives",
            FileKind.APK to "APK files", FileKind.TEXT to "Text & code", FileKind.PDF to "PDF", FileKind.DOCUMENT to "Documents", FileKind.OTHER to "Other",
        )
        items(cats.entries.sortedByDescending { it.value }.filter { it.key != FileKind.FOLDER }, key = { it.key }) { (k, v) ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(kindColor(k)))
                    Spacer(Modifier.width(8.dp))
                    Text(names[k] ?: k.name, Modifier.weight(1f))
                    Text(FileUtils.formatSize(v), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { v.toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = kindColor(k), trackColor = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun FileList(vm: MainViewModel, items: List<FileItem>, selected: Set<String>, hint: String) {
    if (items.isEmpty()) {
        EmptyState("Nothing to show")
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item { Text(hint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp, 8.dp)) }
        items(items, key = { it.path }) { item -> SelectableFile(vm, item, item.path in selected, Modifier.scrollZoom(listState, item.path)) }
    }
}

@Composable
private fun Duplicates(vm: MainViewModel, groups: List<List<FileItem>>, selected: Set<String>) {
    if (groups.isEmpty()) {
        EmptyState("No duplicate files of 32 KB or more were found")
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item {
            Text(
                "Identical files (same SHA-256). Tick the copies you want to remove; keep at least one of each group.",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp, 8.dp),
            )
        }
        groups.forEachIndexed { gi, g ->
            item(key = "h$gi") {
                Text(
                    "${g.size} copies · ${FileUtils.formatSize(g[0].size)} each · ${FileUtils.formatSize(g[0].size * (g.size - 1))} reclaimable",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 10.dp, 16.dp, 2.dp),
                )
            }
            items(g, key = { "d$gi:${it.path}" }) { item -> SelectableFile(vm, item, item.path in selected, Modifier.scrollZoom(listState, "d$gi:${item.path}")) }
        }
    }
}

@Composable
private fun SelectableFile(vm: MainViewModel, item: FileItem, checked: Boolean, modifier: Modifier = Modifier) {
    GlassSelectableRow(
        checked, modifier,
        onClick = { vm.toggleAnalysisSelect(item.path) }, onLongClick = {},
        horizontal = 12.dp, vertical = 6.dp,
    ) {
        Checkbox(checked = checked, onCheckedChange = { vm.toggleAnalysisSelect(item.path) })
        FileIcon(item.kind, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.file.parent ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(FileUtils.formatSize(item.size), style = MaterialTheme.typography.labelMedium)
    }
}
