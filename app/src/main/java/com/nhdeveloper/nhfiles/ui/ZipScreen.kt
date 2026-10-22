package com.nhdeveloper.nhfiles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhdeveloper.nhfiles.data.FileKind
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.ZipEntryInfo
import com.nhdeveloper.nhfiles.data.ZipManager
import java.io.File
import com.nhdeveloper.nhfiles.ui.theme.glassTint

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ZipScreen(vm: MainViewModel, file: File) {
    LaunchedEffect(file) { vm.loadZip(file) }
    val z = vm.zip?.takeIf { it.file == file }
    var menu by remember { mutableStateOf(false) }
    var pickDest by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler { if (!vm.zipUp()) vm.pop() }

    val entries = remember(z?.all, z?.prefix) {
        if (z == null) emptyList() else ZipManager.children(z.all, z.prefix)
    }
    val selected = z?.selected ?: emptySet()

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text(if (selected.isNotEmpty()) "${selected.size} selected" else file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (!vm.zipUp()) vm.pop() }) {
                        Icon(if (selected.isNotEmpty()) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.zipSelectAll() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Test archive") }, onClick = { menu = false; vm.zipTest(file) })
                            DropdownMenuItem(
                                text = { Text("Add copied files here") },
                                onClick = { menu = false; vm.zipAddClipboard(file) },
                            )
                            if (selected.size == 1) {
                                DropdownMenuItem(text = { Text("Rename entry") }, onClick = { menu = false; rename = selected.first() })
                            }
                            if (selected.isNotEmpty()) {
                                DropdownMenuItem(text = { Text("Delete from archive") }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            GlassBar {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selected.isNotEmpty()) {
                        Button(onClick = { vm.zipExtract(file, null, selected) }, modifier = Modifier.weight(1f)) { Text("Extract selected", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        Button(onClick = { vm.zipExtract(file, null, null) }, modifier = Modifier.weight(1f)) { Text("Extract here", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    OutlinedButton(onClick = { pickDest = true }, modifier = Modifier.weight(1f)) { Text("Extract to…", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when {
                z == null || z.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                z.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(z.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth().background(glassTint()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileIcon(FileKind.ARCHIVE, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "${z.all.count { !it.isDir }} files · ${FileUtils.formatSize(file.length())} · ${FileUtils.formatDate(file.lastModified())}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "/" + z.prefix,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (entries.isEmpty()) {
                        EmptyState("This folder is empty")
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            items(entries, key = { it.name }) { e ->
                                ZipRow(
                                    e, e.name in selected, selected.isNotEmpty(), z.prefix,
                                    modifier = Modifier.scrollZoom(listState, e.name),
                                    onClick = {
                                        if (selected.isNotEmpty()) vm.zipToggle(e.name)
                                        else if (e.isDir) vm.zipOpenDir(e.name)
                                        else vm.message("Extract the file to open it (${FileUtils.formatSize(e.size)})")
                                    },
                                    onLong = { vm.zipToggle(e.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickDest) {
        FolderPickerDialog(
            startPath = file.parent ?: vm.root.absolutePath,
            title = "Extract to…",
            onPick = { pickDest = false; vm.zipExtract(file, it, if (selected.isEmpty()) null else selected) },
            onDismiss = { pickDest = false },
        )
    }
    rename?.let { old ->
        NameDialog("Rename entry", "New name", old.trimEnd('/').substringAfterLast('/'), "Rename", {
            rename = null
            vm.zipRename(file, old, it)
        }, { rename = null })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete from archive",
            "Remove ${selected.size} entr${if (selected.size == 1) "y" else "ies"} from the archive? The archive file is rewritten.",
            "Delete", danger = true,
            onConfirm = { confirmDelete = false; vm.zipDelete(file, selected) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZipRow(e: ZipEntryInfo, selected: Boolean, selecting: Boolean, prefix: String, modifier: Modifier = Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val leaf = e.name.removePrefix(prefix).trimEnd('/')
    GlassSelectableRow(selected, modifier, onClick, onLong, horizontal = 12.dp, vertical = 6.dp) {
        FileIcon(FileUtils.kindOf(leaf, e.isDir), 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(leaf, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.isDir) "Folder" else "${FileUtils.formatSize(e.size.coerceAtLeast(0))} (packed ${FileUtils.formatSize(e.compressed.coerceAtLeast(0))})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(FileUtils.formatDate(e.modified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selecting) {
            Spacer(Modifier.width(8.dp))
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}
