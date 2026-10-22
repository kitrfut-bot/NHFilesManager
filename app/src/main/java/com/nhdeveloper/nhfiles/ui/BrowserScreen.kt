package com.nhdeveloper.nhfiles.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhfiles.data.AppSettings
import com.nhdeveloper.nhfiles.data.FileItem
import com.nhdeveloper.nhfiles.data.FileKind
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.SortBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.nhdeveloper.nhfiles.ui.theme.GlassLevel
import com.nhdeveloper.nhfiles.ui.theme.glass
import com.nhdeveloper.nhfiles.ui.theme.glassTint
import com.nhdeveloper.nhfiles.ui.theme.glassTone
import com.nhdeveloper.nhfiles.ui.theme.pressScale

private sealed interface BDialog {
    data object NewFolder : BDialog
    data object NewFile : BDialog
    data class Rename(val file: File) : BDialog
    data class Delete(val files: List<File>) : BDialog
    data class Props(val files: List<File>) : BDialog
    data class Compress(val files: List<File>) : BDialog
    data class ExtractTo(val zip: File) : BDialog
}

private data class Act(val icon: ImageVector, val label: String, val danger: Boolean = false, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ap = vm.activePane
    val active = vm.panes[ap]
    val selected = vm.selectedFiles(ap)
    val selecting = selected.isNotEmpty()
    var dialog by remember { mutableStateOf<BDialog?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    val title = when {
        selecting -> "${selected.size} selected"
        active.path == vm.root.absolutePath -> "Internal storage"
        else -> File(active.path).name.ifEmpty { "/" }
    }

    fun requestDelete(files: List<File>) {
        if (s.confirmDelete) dialog = BDialog.Delete(files) else vm.delete(files)
    }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (selecting) vm.clearSelection(ap) else vm.onBack() }) {
                        Icon(if (selecting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { vm.selectAll(ap) }) { Icon(Icons.Filled.SelectAll, "Select all") }
                    } else {
                        IconButton(onClick = { vm.openSearch(active.path) }) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { vm.toggleDual() }) {
                            Icon(
                                Icons.Filled.VerticalSplit, "Dual pane",
                                tint = if (vm.dual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { vm.setGrid(!s.grid) }) {
                            Icon(if (s.grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, "Toggle grid or list")
                        }
                    }
                },
            )
        },
        bottomBar = {
            GlassBar {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    if (selecting) {
                        BarButton(Icons.Filled.ContentCopy, "Copy", Modifier.weight(1f), tint = Color(0xFF42A5F5)) { vm.copyToClipboard(selected, false) }
                        BarButton(Icons.Filled.ContentCut, "Cut", Modifier.weight(1f), tint = Color(0xFFFFA726)) { vm.copyToClipboard(selected, true) }
                        BarButton(Icons.Filled.Delete, "Delete", Modifier.weight(1f), tint = Color(0xFFEF5350)) { requestDelete(selected) }
                        BarButton(Icons.Filled.Share, "Share", Modifier.weight(1f), tint = Color(0xFF43A047)) { vm.share(selected) }
                        BarButton(Icons.Filled.MoreVert, "More", Modifier.weight(1f), tint = Color(0xFF9AA0A6)) { sheet = true }
                    } else {
                        Box(Modifier.weight(1f)) {
                            BarButton(Icons.Filled.Add, "New", tint = Color(0xFF43A047)) { showNew = true }
                            DropdownMenu(expanded = showNew, onDismissRequest = { showNew = false }) {
                                DropdownMenuItem(
                                    text = { Text("New folder") },
                                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                    onClick = { showNew = false; dialog = BDialog.NewFolder },
                                )
                                DropdownMenuItem(
                                    text = { Text("New file") },
                                    leadingIcon = { Icon(Icons.Filled.NoteAdd, null) },
                                    onClick = { showNew = false; dialog = BDialog.NewFile },
                                )
                            }
                        }
                        BarButton(
                            Icons.Filled.ContentPaste, "Paste", Modifier.weight(1f),
                            tint = Color(0xFF42A5F5), enabled = vm.clipboard != null, badge = vm.clipboard?.files?.size ?: 0,
                        ) { vm.paste(ap) }
                        BarButton(Icons.Filled.SelectAll, "Select", Modifier.weight(1f), tint = Color(0xFFFFA726)) { vm.selectAll(ap) }
                        Box(Modifier.weight(1f)) {
                            BarButton(Icons.AutoMirrored.Filled.Sort, "Sort", tint = Color(0xFFAB47BC)) { showSort = true }
                            SortMenu(vm, s, showSort) { showSort = false }
                        }
                        Box(Modifier.weight(1f)) {
                            BarButton(Icons.Filled.MoreVert, "More", tint = Color(0xFF9AA0A6)) { showMore = true }
                            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                    onClick = { showMore = false; vm.refresh(ap) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Show hidden files") },
                                    trailingIcon = { Checkbox(checked = s.showHidden, onCheckedChange = null) },
                                    onClick = { showMore = false; vm.setShowHidden(!s.showHidden) },
                                )
                                val bookmarked = s.bookmarks.contains(active.path)
                                DropdownMenuItem(
                                    text = { Text(if (bookmarked) "Remove bookmark" else "Bookmark this folder") },
                                    leadingIcon = { Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null) },
                                    onClick = { showMore = false; vm.toggleBookmark(active.path) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Folder properties") },
                                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                                    onClick = { showMore = false; dialog = BDialog.Props(listOf(File(active.path))) },
                                )
                                if (vm.clipboard != null) {
                                    DropdownMenuItem(
                                        text = { Text("Clear clipboard") },
                                        onClick = { showMore = false; vm.clearClipboard() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (!vm.dual) {
                BrowserPane(vm, 0, s, Modifier.fillMaxSize(), false)
            } else {
                // Dual pane: always left | right, on phones and tablets.
                Row(Modifier.fillMaxSize()) {
                    BrowserPane(vm, 0, s, Modifier.weight(1f).fillMaxHeight(), true)
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    BrowserPane(vm, 1, s, Modifier.weight(1f).fillMaxHeight(), true)
                }
            }
        }
    }

    if (sheet) {
        val one = selected.singleOrNull()
        val bookmarked = one != null && s.bookmarks.contains(one.absolutePath)
        val acts = buildList {
            if (one != null) {
                if (one.isFile) {
                    add(Act(Icons.AutoMirrored.Filled.OpenInNew, "Open") { vm.clearSelection(ap); vm.openFile(one) })
                    add(Act(Icons.AutoMirrored.Filled.OpenInNew, "Open with…") { vm.openExternal(one) })
                    add(Act(Icons.Filled.Edit, "Edit as text") { vm.clearSelection(ap); vm.openAsText(one) })
                } else {
                    add(Act(Icons.Filled.Folder, "Open") { vm.openFolder(ap, one.absolutePath) })
                }
            }
            if (selected.any { it.isFile }) add(Act(Icons.Filled.Share, "Share") { vm.share(selected) })
            add(Act(Icons.Filled.ContentCopy, "Copy") { vm.copyToClipboard(selected, false) })
            add(Act(Icons.Filled.ContentCut, "Move (cut)") { vm.copyToClipboard(selected, true) })
            if (vm.dual) {
                add(Act(Icons.Filled.ContentCopy, "Copy to other pane") { vm.transferToOther(false) })
                add(Act(Icons.Filled.ContentCut, "Move to other pane") { vm.transferToOther(true) })
            }
            if (one != null) add(Act(Icons.Filled.Edit, "Rename") { dialog = BDialog.Rename(one) })
            add(Act(Icons.Filled.FolderZip, "Compress to ZIP") { dialog = BDialog.Compress(selected) })
            if (one != null && one.isFile && FileUtils.isZip(one.name)) {
                add(Act(Icons.Filled.Unarchive, "Extract here") { vm.clearSelection(ap); vm.zipExtract(one, null, null) })
                add(Act(Icons.Filled.Unarchive, "Extract to…") { dialog = BDialog.ExtractTo(one) })
            }
            if (one != null && one.isFile && FileUtils.extension(one.name) in setOf("html", "htm")) {
                add(Act(Icons.Filled.Lock, "Encrypt HTML (stays runnable)") { vm.clearSelection(ap); vm.encryptHtml(one) })
                add(Act(Icons.Filled.LockOpen, "Decrypt HTML") { vm.clearSelection(ap); vm.decryptHtml(one) })
            }
            if (one != null) {
                add(Act(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Add bookmark") {
                    vm.toggleBookmark(one.absolutePath)
                })
            }
            add(Act(Icons.Filled.Info, "Properties") { dialog = BDialog.Props(selected) })
            add(Act(Icons.Filled.Delete, "Delete", danger = true) { requestDelete(selected) })
        }
        ActionSheet(
            title = if (one != null) one.name else "${selected.size} items",
            actions = acts,
            onDismiss = { sheet = false },
        )
    }

    when (val d = dialog) {
        null -> {}
        BDialog.NewFolder -> NameDialog("New folder", "Folder name", "", "Create", { vm.createFolder(ap, it); dialog = null }, { dialog = null })
        BDialog.NewFile -> NameDialog("New file", "File name", "untitled.txt", "Create", { vm.createFile(ap, it); dialog = null }, { dialog = null })
        is BDialog.Rename -> NameDialog("Rename", "New name", d.file.name, "Rename", {
            vm.rename(d.file, it); vm.clearSelection(ap); dialog = null
        }, { dialog = null })
        is BDialog.Delete -> {
            val what = if (d.files.size == 1) d.files[0].name else d.files.size.toString() + " items"
            ConfirmDialog(
                "Delete",
                "Delete " + what + "? This cannot be undone.",
                "Delete", danger = true,
                onConfirm = { vm.delete(d.files); dialog = null },
                onDismiss = { dialog = null },
            )
        }
        is BDialog.Props -> PropertiesDialog(vm, d.files) { dialog = null }
        is BDialog.Compress -> NameDialog(
            "Compress to ZIP", "Archive name",
            if (d.files.size == 1) FileUtils.baseName(d.files[0].name) else "Archive", "Compress",
            { vm.createZip(d.files, it); dialog = null }, { dialog = null },
        )
        is BDialog.ExtractTo -> FolderPickerDialog(
            startPath = d.zip.parent ?: vm.root.absolutePath,
            title = "Extract to…",
            onPick = { vm.clearSelection(ap); vm.zipExtract(d.zip, it, null); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: Int = 0,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val iconColor = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint.glassTone()
    val src = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(src)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (badge > 0) {
            BadgedBox(badge = { Badge { Text(badge.toString()) } }) { Icon(icon, label, Modifier.size(24.dp), tint = iconColor) }
        } else {
            Icon(icon, label, Modifier.size(24.dp), tint = iconColor)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SortMenu(vm: MainViewModel, s: AppSettings, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text("Sort by", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        SortBy.values().forEach { by ->
            DropdownMenuItem(
                text = { Text(by.label) },
                leadingIcon = { RadioButton(selected = s.sortBy == by, onClick = null) },
                onClick = { vm.setSort(by) },
            )
        }
        DropdownMenuItem(
            text = { Text("Ascending") },
            leadingIcon = { Checkbox(checked = s.sortAsc, onCheckedChange = null) },
            onClick = { vm.setAsc(!s.sortAsc) },
        )
        HorizontalDivider()
        Text("Show", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val filters = listOf<Pair<String, FileKind?>>(
            "All files" to null, "Images" to FileKind.IMAGE, "Videos" to FileKind.VIDEO, "Audio" to FileKind.AUDIO,
            "Archives" to FileKind.ARCHIVE, "APK" to FileKind.APK, "Text & code" to FileKind.TEXT, "PDF" to FileKind.PDF,
        )
        filters.forEach { (label, kind) ->
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { RadioButton(selected = vm.filterKind == kind, onClick = null) },
                onClick = { vm.filterKind = kind; onDismiss() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(title: String, actions: List<Act>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            actions.forEach { a ->
                val c = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ListItem(
                    headlineContent = { Text(a.label, color = c) },
                    leadingContent = { Icon(a.icon, null, tint = c) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onDismiss(); a.run() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BrowserPane(vm: MainViewModel, index: Int, s: AppSettings, modifier: Modifier, dualMode: Boolean) {
    val pane = vm.panes[index]
    val selecting = pane.selected.isNotEmpty()
    val kindFilter = vm.filterKind
    val visible = remember(pane.items, kindFilter) {
        pane.items.filter { kindFilter == null || it.isDir || it.kind == kindFilter }
    }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(pane.path) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(pane.loading) { if (!pane.loading) refreshing = false }
    val isActive = vm.activePane == index
    val borderMod = if (dualMode && isActive) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(16.dp)) else Modifier

    Column(modifier.then(borderMod)) {
        Breadcrumb(vm, index)
        if (dualMode) {
            val folders = pane.items.count { it.isDir }
            Text(
                "Folders: $folders  Files: ${pane.items.size - folders}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().background(glassTint()).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; vm.refresh(index) },
            modifier = Modifier.fillMaxSize(),
        ) {
            val err = pane.error
            when {
                err != null -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(err, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { vm.refresh(index) }) { Text("Retry") }
                        }
                    }
                }
                pane.loading && pane.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                visible.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item { EmptyState(if (pane.items.isEmpty()) "This folder is empty" else "No files match the current filter") }
                }
                s.grid -> LazyVerticalGrid(columns = GridCells.Adaptive(92.dp), state = gridState, modifier = Modifier.fillMaxSize()) {
                    gridItems(visible, key = { it.path }) { item ->
                        FileCell(
                            item, item.path in pane.selected, s.showExtensions,
                            modifier = Modifier.scrollZoom(gridState, item.path),
                            onClick = { if (selecting) vm.toggleSelect(index, item.path) else vm.open(item, index) },
                            onLong = { vm.toggleSelect(index, item.path) },
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(visible, key = { it.path }) { item ->
                        FileRow(
                            item, item.path in pane.selected, selecting, s.showExtensions,
                            modifier = Modifier.animateItem().scrollZoom(listState, item.path),
                            compact = dualMode,
                            onClick = { if (selecting) vm.toggleSelect(index, item.path) else vm.open(item, index) },
                            onLongClick = { vm.toggleSelect(index, item.path) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCell(item: FileItem, selected: Boolean, showExt: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Column(
        modifier
            .padding(4.dp)
            .pressScale(src)
            .glassItem(selected, 20.dp)
            .combinedClickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick, onLongClick = onLong)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileIcon(item.kind, 52.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            displayName(item, showExt),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Breadcrumb(vm: MainViewModel, index: Int) {
    val path = vm.panes[index].path
    val rootPath = vm.root.absolutePath
    val segs = remember(path) {
        val out = ArrayList<Pair<String, String>>()
        if (path == rootPath || path.startsWith("$rootPath/")) {
            out.add("Internal" to rootPath)
            var cur = rootPath
            path.removePrefix(rootPath).split('/').filter { it.isNotEmpty() }.forEach {
                cur += "/$it"
                out.add(it to cur)
            }
        } else {
            out.add("/" to "/")
            var cur = ""
            path.split('/').filter { it.isNotEmpty() }.forEach {
                cur += "/$it"
                out.add(it to cur)
            }
        }
        out
    }
    val scroll = rememberScrollState()
    LaunchedEffect(path) {
        delay(60)
        scroll.animateScrollTo(scroll.maxValue)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(18.dp), GlassLevel.Subtle)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segs.forEachIndexed { i, (label, p) ->
            val last = i == segs.lastIndex
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.openFolder(index, p) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            if (!last) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class PropInfo(val size: Long, val files: Int, val folders: Int, val created: Long, val accessed: Long)

@Composable
fun PropertiesDialog(vm: MainViewModel, files: List<File>, onDismiss: () -> Unit) {
    val info by produceState<PropInfo?>(null, files) {
        value = withContext(Dispatchers.IO) {
            var size = 0L
            var nf = 0
            var nd = 0
            for (f in files) {
                size += vm.ops.sizeOf(f)
                if (f.isDirectory) nd++ else nf++
            }
            var created = 0L
            var accessed = 0L
            if (files.size == 1) {
                try {
                    val a = Files.readAttributes(files[0].toPath(), BasicFileAttributes::class.java)
                    created = a.creationTime().toMillis()
                    accessed = a.lastAccessTime().toMillis()
                } catch (e: Exception) {
                    // timestamps are optional
                }
            }
            PropInfo(size, nf, nd, created, accessed)
        }
    }
    val one = files.singleOrNull()
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (one != null) {
                    PropRow("Name", one.name.ifEmpty { one.path })
                    PropRow("Path", one.absolutePath)
                    PropRow("Type", if (one.isDirectory) "Folder" else FileUtils.mime(one))
                } else {
                    PropRow("Selection", "${files.size} items")
                }
                val i = info
                PropRow("Size", if (i == null) "Calculating…" else "${FileUtils.formatSize(i.size)} (${i.size} bytes)")
                if (one == null && i != null) PropRow("Contains", "${i.files} files, ${i.folders} folders")
                if (one != null) {
                    PropRow("Modified", FileUtils.formatDateTime(one.lastModified()))
                    if (i != null) {
                        PropRow("Created", FileUtils.formatDateTime(i.created))
                        PropRow("Accessed", FileUtils.formatDateTime(i.accessed))
                    }
                    PropRow("Permissions", (if (one.canRead()) "Read " else "") + (if (one.canWrite()) "Write" else "(read-only)"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FolderPickerDialog(startPath: String, title: String, onPick: (File) -> Unit, onDismiss: () -> Unit) {
    var path by remember { mutableStateOf(startPath) }
    val dirs by produceState(emptyList<File>(), path) {
        value = withContext(Dispatchers.IO) {
            File(path).listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
        }
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.height(300.dp)) {
                    val parent = File(path).parentFile
                    if (parent != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable { path = parent.absolutePath }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Folder, null, tint = kindColor(FileKind.FOLDER))
                                Spacer(Modifier.width(12.dp))
                                Text("..")
                            }
                        }
                    }
                    items(dirs, key = { it.path }) { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { path = d.absolutePath }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = kindColor(FileKind.FOLDER))
                            Spacer(Modifier.width(12.dp))
                            Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(File(path)) }) { Text("Select this folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
