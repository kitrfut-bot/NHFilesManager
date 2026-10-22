package com.nhdeveloper.nhfiles.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhfiles.data.FileItem
import com.nhdeveloper.nhfiles.data.FileKind
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.PermissionManager
import com.nhdeveloper.nhfiles.data.SortBy
import com.nhdeveloper.nhfiles.vpo.ui.SplashActivity
import com.nhdeveloper.nhfiles.data.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import com.nhdeveloper.nhfiles.ui.theme.GlassLevel
import com.nhdeveloper.nhfiles.ui.theme.glass
import com.nhdeveloper.nhfiles.ui.theme.glassTone
import com.nhdeveloper.nhfiles.ui.theme.pressScale

private val tabTitles = listOf("NH Files", "Recent", "Bookmarks", "Tools", "Settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(vm: MainViewModel) {
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.tab == 0) {
                            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(tabTitles[vm.tab], fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.openSearch(vm.root.absolutePath) }) { Icon(Icons.Filled.Search, "Search") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; vm.onAccessGranted() })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; vm.tab = 4 })
                            DropdownMenuItem(text = { Text("About") }, onClick = { menu = false; about = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Floating glass pill; the selection indicator animates between items.
            Box(Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                NavigationBar(
                    modifier = Modifier.glass(RoundedCornerShape(30.dp), GlassLevel.Strong, shadowElevation = 14.dp),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                ) {
                    val items = listOf(
                        Triple(Icons.Filled.Folder, "Files", Color(0xFF6EA8FF)),
                        Triple(Icons.Filled.History, "Recent", Color(0xFF5CE1F5)),
                        Triple(Icons.Filled.Bookmark, "Bookmarks", Color(0xFFB197FC)),
                        Triple(Icons.Filled.Build, "Tools", Color(0xFF6EE7B7)),
                        Triple(Icons.Filled.Settings, "Settings", Color(0xFF9DB4FF)),
                    )
                    items.forEachIndexed { i, (icon, label, color) ->
                        NavigationBarItem(
                            selected = vm.tab == i,
                            onClick = { vm.tab = i },
                            icon = { Icon(icon, label, Modifier.size(26.dp)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = color.glassTone(),
                                unselectedIconColor = color.glassTone().copy(alpha = 0.7f),
                                selectedTextColor = color.glassTone(),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = color.copy(alpha = 0.24f),
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Crossfade(targetState = vm.tab, animationSpec = tween(180), label = "tabs") { t ->
            when (t) {
                0 -> HomeTab(vm, pad)
                1 -> RecentTab(vm, pad)
                2 -> BookmarksTab(vm, pad)
                3 -> ToolsTab(vm, pad)
                else -> SettingsTab(vm, pad)
            }
        }
    }
    if (about) AboutDialog { about = false }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
    MessageDialog(
        "NH Files",
        "Version $version\nA fast, offline file manager. No account, no ads, no internet connection required.\n\nBy NH Developer.",
        onDismiss,
    )
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

private fun linkIcon(kind: String): ImageVector = when (kind) {
    "downloads" -> Icons.Filled.Download
    "documents" -> Icons.Filled.Description
    "pictures", "camera" -> Icons.Filled.PhotoLibrary
    "movies" -> Icons.Filled.Movie
    "music" -> Icons.Filled.LibraryMusic
    "android" -> Icons.Filled.Android
    else -> Icons.Filled.Storage
}

private fun linkColor(kind: String): Color = when (kind) {
    "downloads" -> Color(0xFF43A047)
    "documents" -> Color(0xFFFB8C00)
    "pictures", "camera" -> Color(0xFF42A5F5)
    "movies" -> Color(0xFFEF5350)
    "music" -> Color(0xFFAB47BC)
    "android" -> Color(0xFF66BB6A)
    else -> Color(0xFF5C9DFF)
}

@Composable
private fun HomeTab(vm: MainViewModel, pad: PaddingValues) {
    val listState = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StorageCard(vm.storage) }
        item { QuickGrid(vm) }
        item { Text("Quick Access", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        items(vm.quickLinks, key = { it.path }) { q ->
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.scrollZoom(listState, q.path).fillMaxWidth(),
                onClick = { vm.openBrowser(q.path) },
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassTile(linkColor(q.kind), 40.dp) {
                        Icon(linkIcon(q.kind), null, tint = linkColor(q.kind).glassTone(), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            q.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StorageCard(st: StorageInfo) {
    val frac by animateFloatAsState(st.fraction, animationSpec = tween(700), label = "storage")
    GlassCard(shape = RoundedCornerShape(28.dp), sweep = true) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                GlassRing(frac, Modifier.size(80.dp), 8.dp)
                Text("${(st.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Internal Storage", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${FileUtils.formatSize(st.used)} used of ${FileUtils.formatSize(st.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${FileUtils.formatSize(st.free)} available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                GlassLinearProgress(frac, Modifier.fillMaxWidth())
            }
        }
    }
}

private data class Tile(val label: String, val icon: ImageVector, val color: Color, val action: () -> Unit)

@Composable
private fun QuickGrid(vm: MainViewModel) {
    val r = vm.root
    fun dir(name: String) = File(r, name).absolutePath
    val tiles = listOf(
        Tile("Internal", Icons.Filled.Storage, Color(0xFF5C9DFF)) { vm.openBrowser(r.absolutePath) },
        Tile("Downloads", Icons.Filled.Download, Color(0xFF43A047)) { vm.openBrowser(dir("Download")) },
        Tile("Documents", Icons.Filled.Description, Color(0xFFFB8C00)) { vm.openBrowser(dir("Documents")) },
        Tile("Pictures", Icons.Filled.PhotoLibrary, Color(0xFF42A5F5)) { vm.openBrowser(dir("Pictures")) },
        Tile("Movies", Icons.Filled.Movie, Color(0xFFEF5350)) { vm.openBrowser(dir("Movies")) },
        Tile("Music", Icons.Filled.LibraryMusic, Color(0xFFAB47BC)) { vm.openBrowser(dir("Music")) },
        Tile("APK", Icons.Filled.Android, Color(0xFF66BB6A)) { vm.openSearch(r.absolutePath, FileKind.APK, "APK files") },
        Tile("More", Icons.Filled.MoreVert, Color(0xFF9AA0A6)) { vm.tab = 3 },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val src = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .pressScale(src)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(interactionSource = src, indication = LocalIndication.current) { t.action() }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GlassTile(t.color, 58.dp) { Icon(t.icon, t.label, tint = t.color.glassTone(), modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.height(4.dp))
                        Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Recent / Bookmarks
// ---------------------------------------------------------------------------------------------

@Composable
private fun loadItems(vm: MainViewModel, paths: List<String>): List<FileItem> {
    val state = produceState(emptyList<FileItem>(), paths) {
        value = withContext(Dispatchers.IO) {
            paths.map { File(it) }.filter { it.exists() }.map { vm.repo.toItem(it, true) }
        }
    }
    return state.value
}

@Composable
private fun RecentTab(vm: MainViewModel, pad: PaddingValues) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val list = loadItems(vm, s.recents)
    Column(Modifier.fillMaxSize().padding(pad)) {
        if (list.isEmpty()) {
            EmptyState(if (s.keepRecents) "No recent files yet" else "Recent history is turned off in Settings")
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${list.size} recent", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearRecents() }) { Text("Clear") }
            }
            val listState = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(list, key = { it.path }) { item ->
                    FileRow(
                        item, selected = false, selecting = false, showExt = s.showExtensions,
                        modifier = Modifier.scrollZoom(listState, item.path),
                        subtitle = item.file.parent ?: "",
                        onClick = { if (item.isDir) vm.openBrowser(item.path) else vm.openFile(item.file) },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarksTab(vm: MainViewModel, pad: PaddingValues) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val list = loadItems(vm, s.bookmarks)
    Column(Modifier.fillMaxSize().padding(pad)) {
        if (list.isEmpty()) {
            EmptyState("No bookmarks yet. Open a folder and use More ▸ Bookmark this folder.")
        } else {
            val listState = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(list, key = { it.path }) { item ->
                    Row(Modifier.scrollZoom(listState, item.path), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            FileRow(
                                item, selected = false, selecting = false, showExt = s.showExtensions,
                                subtitle = item.path,
                                onClick = { if (item.isDir) vm.openBrowser(item.path) else vm.openFile(item.file) },
                                onLongClick = {},
                            )
                        }
                        IconButton(onClick = { vm.toggleBookmark(item.path) }) { Icon(Icons.Filled.Close, "Remove bookmark") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------------------------

@Composable
private fun ToolsTab(vm: MainViewModel, pad: PaddingValues) {
    val root = vm.root.absolutePath
    val ctx = LocalContext.current
    val tools = listOf(
        Tile("Video Player", Icons.Filled.Movie, Color(0xFFFF6A00)) {
            ctx.startActivity(Intent(ctx, SplashActivity::class.java))
        },
        Tile("All Videos", Icons.Filled.Movie, Color(0xFFEF5350)) { vm.openSearch(root, FileKind.VIDEO, "Videos") },
        Tile("ZIP Manager", Icons.Filled.FolderZip, Color(0xFFFFCA28)) { vm.openSearch(root, FileKind.ARCHIVE, "Choose an archive") },
        Tile("APK Info", Icons.Filled.Android, Color(0xFF66BB6A)) { vm.openSearch(root, FileKind.APK, "Choose an APK") },
        Tile("Text Editor", Icons.Filled.Code, Color(0xFF42A5F5)) { vm.openSearch(root, FileKind.TEXT, "Choose a text file") },
        Tile("Storage Analysis", Icons.Filled.Storage, Color(0xFFFFA726)) { vm.openAnalysis(0) },
        Tile("Large Files", Icons.Filled.DeleteSweep, Color(0xFFEF5350)) { vm.openAnalysis(1) },
        Tile("Duplicates", Icons.Filled.ContentCopy, Color(0xFFAB47BC)) { vm.openAnalysis(2) },
        Tile("Search", Icons.Filled.Search, Color(0xFF5C9DFF)) { vm.openSearch(root) },
        Tile("Home", Icons.Filled.Home, Color(0xFF9AA0A6)) { vm.tab = 0 },
    )
    Column(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t ->
                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { t.action() },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassTile(t.color, 40.dp) { Icon(t.icon, null, tint = t.color.glassTone()) }
                            Spacer(Modifier.width(10.dp))
                            Text(t.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsTab(vm: MainViewModel, pad: PaddingValues) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    var startDialog by remember { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }

    val startOptions = vm.quickLinks.filter { it.kind != "android" }
    val themeNames = listOf("System default", "Light", "Dark")

    LazyColumn(Modifier.fillMaxSize().padding(pad)) {
        item { SectionTitle("Appearance") }
        item { SettingRow("Theme", themeNames[s.theme.coerceIn(0, 2)]) { themeDialog = true } }
        item { SettingRow("Grid view by default", null, s.grid) { vm.setGrid(it) } }
        item { SectionTitle("Browsing") }
        item {
            val label = startOptions.firstOrNull { it.path == s.startDir }?.name ?: "Internal Storage"
            SettingRow("Default start folder", label) { startDialog = true }
        }
        item { SettingRow("Show file extensions", null, s.showExtensions) { vm.setShowExt(it) } }
        item { SettingRow("Show hidden files", null, s.showHidden) { vm.setShowHidden(it) } }
        item { SettingRow("Default sorting", s.sortBy.label + if (s.sortAsc) " (A→Z)" else " (Z→A)") { sortDialog = true } }
        item { SectionTitle("Safety") }
        item { SettingRow("Confirm before deleting", null, s.confirmDelete) { vm.setConfirmDelete(it) } }
        item { SectionTitle("History") }
        item { SettingRow("Keep recent files", null, s.keepRecents) { vm.setKeepRecents(it) } }
        item { SettingRow("Clear recent files", "${s.recents.size} entries") { vm.clearRecents(); vm.message("Recent files cleared") } }
        item { SettingRow("Clear bookmarks", "${s.bookmarks.size} entries") { vm.clearBookmarks(); vm.message("Bookmarks cleared") } }
        item { SectionTitle("Storage permission") }
        item {
            Text(
                "NH Files uses \"All files access\" (Android 11+) or the classic storage permission (Android 8–10) to work with real file paths. " +
                    "If you revoke it, the app asks again. Android/data and Android/obb are protected by Android and may not be listable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            SettingRow("Manage permission", "Open system settings") {
                try {
                    ctx.startActivity(
                        if (android.os.Build.VERSION.SDK_INT >= 30) PermissionManager.allFilesIntent(ctx)
                        else PermissionManager.appDetailsIntent(ctx)
                    )
                } catch (e: Exception) {
                    ctx.startActivity(PermissionManager.appDetailsIntent(ctx))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (themeDialog) {
        ChoiceDialog("Theme", themeNames, s.theme.coerceIn(0, 2), { vm.setTheme(it); themeDialog = false }, { themeDialog = false })
    }
    if (startDialog) {
        val names = startOptions.map { it.name }
        val sel = startOptions.indexOfFirst { it.path == s.startDir }.let { if (it < 0) 0 else it }
        ChoiceDialog("Default start folder", names, sel, {
            vm.setStartDir(if (it == 0) "" else startOptions[it].path)
            startDialog = false
        }, { startDialog = false })
    }
    if (sortDialog) {
        val names = SortBy.values().map { it.label } + listOf("Ascending (A→Z)", "Descending (Z→A)")
        ChoiceDialog("Default sorting", names, s.sortBy.ordinal, { i ->
            if (i < SortBy.values().size) vm.setSort(SortBy.values()[i])
            else vm.setAsc(i == SortBy.values().size)
        }, { sortDialog = false })
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        level = GlassLevel.Subtle,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        level = GlassLevel.Subtle,
        onClick = { onChange(!checked) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
