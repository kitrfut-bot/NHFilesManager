package com.nhdeveloper.nhfiles.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.nhfiles.data.AppSettings
import com.nhdeveloper.nhfiles.data.Conflict
import com.nhdeveloper.nhfiles.data.FileItem
import com.nhdeveloper.nhfiles.data.FileKind
import com.nhdeveloper.nhfiles.data.FileOperations
import com.nhdeveloper.nhfiles.data.FileRepository
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.HtmlCrypto
import com.nhdeveloper.nhfiles.data.Intents
import com.nhdeveloper.nhfiles.data.K
import com.nhdeveloper.nhfiles.data.OpProgress
import com.nhdeveloper.nhfiles.data.ScanResult
import com.nhdeveloper.nhfiles.data.SettingsRepository
import com.nhdeveloper.nhfiles.data.SortBy
import com.nhdeveloper.nhfiles.data.StorageInfo
import com.nhdeveloper.nhfiles.data.StorageScanner
import com.nhdeveloper.nhfiles.data.TextFileIO
import com.nhdeveloper.nhfiles.data.ZipEntryInfo
import com.nhdeveloper.nhfiles.data.ZipManager
import com.nhdeveloper.nhfiles.vpo.data.VideoItem
import com.nhdeveloper.nhfiles.vpo.player.PlayerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Tabs : Screen
    data object Browser : Screen
    data object Search : Screen
    data object Analysis : Screen
    data class Zip(val file: File) : Screen
    data class Apk(val file: File) : Screen
    data class Editor(val file: File) : Screen
    data class Image(val file: File) : Screen
    data class Video(val file: File) : Screen
    data class Audio(val file: File) : Screen
    data class Pdf(val file: File) : Screen
}

data class PaneState(
    val path: String,
    val items: List<FileItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class QuickLink(val name: String, val path: String, val kind: String)
data class ClipboardState(val files: List<File>, val move: Boolean)
data class ConflictRequest(val files: List<File>, val dest: File, val move: Boolean, val names: List<String>)

data class SearchState(
    val query: String = "",
    val scope: Int = 0, // 0 all, 1 files, 2 folders
    val kind: FileKind? = null,
    val sortBy: SortBy = SortBy.NAME,
    val asc: Boolean = true,
    val rootPath: String = "",
    val title: String = "Search",
    val results: List<FileItem> = emptyList(),
    val searching: Boolean = false,
)

data class ZipState(
    val file: File,
    val all: List<ZipEntryInfo> = emptyList(),
    val prefix: String = "",
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class AnalysisState(
    val scanning: Boolean = false,
    val result: ScanResult? = null,
    val selected: Set<String> = emptySet(),
    val tab: Int = 0,
)

private class LimitReached : RuntimeException()

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = FileRepository()
    val ops = FileOperations()
    val zipManager = ZipManager(ops)
    private val scanner = StorageScanner(repo)
    private val settingsRepo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val root: File get() = repo.root

    // ---- navigation -------------------------------------------------------------------------
    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Tabs))
        private set
    var tab by mutableIntStateOf(0)

    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    fun onBack() {
        when (stack.last()) {
            Screen.Browser -> if (!browserBack()) pop()
            Screen.Tabs -> if (tab != 0) tab = 0
            else -> pop()
        }
    }

    // ---- messages / progress ----------------------------------------------------------------
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages
    fun message(s: String) { _messages.tryEmit(s) }

    val progress = MutableStateFlow<OpProgress?>(null)
    private var opJob: Job? = null

    private fun runOp(title: String, block: suspend () -> String) {
        if (opJob?.isActive == true) {
            message("Another operation is still running")
            return
        }
        opJob = viewModelScope.launch {
            progress.value = OpProgress(title, 0, 0)
            try {
                message(block())
            } catch (e: CancellationException) {
                message("$title cancelled")
                throw e
            } catch (e: Exception) {
                message(e.message ?: "$title failed")
            } finally {
                progress.value = null
                refreshAll()
                refreshStorage()
            }
        }
    }

    fun cancelOp() { opJob?.cancel() }

    // ---- home data --------------------------------------------------------------------------
    var quickLinks by mutableStateOf<List<QuickLink>>(emptyList())
        private set
    var storage by mutableStateOf(StorageInfo(0, 0))
        private set

    fun onAccessGranted() {
        viewModelScope.launch {
            quickLinks = withContext(Dispatchers.IO) { buildLinks() }
            storage = withContext(Dispatchers.IO) { repo.storage() }
        }
    }

    fun refreshStorage() {
        viewModelScope.launch { storage = withContext(Dispatchers.IO) { repo.storage() } }
    }

    private fun buildLinks(): List<QuickLink> {
        val r = root
        val l = ArrayList<QuickLink>()
        l.add(QuickLink("Internal Storage", r.absolutePath, "internal"))
        val std = listOf(
            Triple("Downloads", "Download", "downloads"),
            Triple("Documents", "Documents", "documents"),
            Triple("Pictures", "Pictures", "pictures"),
            Triple("Movies", "Movies", "movies"),
            Triple("Music", "Music", "music"),
            Triple("Camera (DCIM)", "DCIM", "camera"),
        )
        for ((name, dir, kind) in std) {
            val f = File(r, dir)
            if (f.isDirectory) l.add(QuickLink(name, f.absolutePath, kind))
        }
        val android = File(r, "Android")
        if (android.isDirectory) l.add(QuickLink("Android", android.absolutePath, "android"))
        try {
            getApplication<Application>().getExternalFilesDirs(null).drop(1).filterNotNull().forEach {
                val p = it.absolutePath.substringBefore("/Android/data")
                val f = File(p)
                if (f.isDirectory && f.canRead()) l.add(QuickLink("SD card / USB", p, "sd"))
            }
        } catch (e: Exception) {
            // ignore: external volumes are optional
        }
        return l
    }

    // ---- browser ----------------------------------------------------------------------------
    val panes = mutableStateListOf(PaneState(""), PaneState(""))
    var activePane by mutableIntStateOf(0)
    var dual by mutableStateOf(false)
        private set
    var filterKind by mutableStateOf<FileKind?>(null)
    var clipboard by mutableStateOf<ClipboardState?>(null)
        private set
    var conflict by mutableStateOf<ConflictRequest?>(null)
        private set
    private var browserLoaded = false
    private val loadJobs = arrayOfNulls<Job>(2)

    private fun isVolumeRoot(path: String): Boolean =
        path == root.absolutePath || quickLinks.any { it.kind == "sd" && it.path == path }

    fun openBrowser(path: String, replaceTop: Boolean = false) {
        browserLoaded = true
        activePane = 0
        if (panes[1].path.isBlank()) panes[1] = PaneState(root.absolutePath)
        panes[0] = PaneState(path)
        load(0)
        if (dual) load(1)
        if (stack.contains(Screen.Browser)) {
            stack = stack.takeWhile { it != Screen.Browser } + Screen.Browser
        } else if (replaceTop && stack.size > 1) {
            stack = stack.dropLast(1) + Screen.Browser
        } else {
            push(Screen.Browser)
        }
    }

    fun openStart() {
        val s = settings.value.startDir
        val path = if (s.isNotBlank() && File(s).isDirectory) s else root.absolutePath
        openBrowser(path)
    }

    fun openFolder(i: Int, path: String) {
        panes[i] = panes[i].copy(path = path, selected = emptySet())
        activePane = i
        load(i)
    }

    fun refresh(i: Int) = load(i, keepSelection = true)

    fun refreshAll() {
        if (!browserLoaded) return
        load(0, true)
        if (dual) load(1, true)
    }

    private fun load(i: Int, keepSelection: Boolean = false) {
        loadJobs[i]?.cancel()
        val path = panes[i].path
        if (path.isBlank()) return
        panes[i] = panes[i].copy(loading = true, error = null)
        loadJobs[i] = viewModelScope.launch {
            try {
                val s = settingsRepo.settings.first()
                val items = withContext(Dispatchers.IO) {
                    repo.sorted(repo.list(File(path), s.showHidden), s.sortBy, s.sortAsc)
                }
                val cur = panes[i]
                val keep = if (keepSelection) cur.selected.filter { sel -> items.any { it.path == sel } }.toSet() else emptySet()
                panes[i] = cur.copy(items = items, loading = false, error = null, selected = keep)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                panes[i] = panes[i].copy(items = emptyList(), loading = false, error = e.message ?: "Cannot open this folder")
            }
        }
    }

    fun goUp(i: Int): Boolean {
        val cur = File(panes[i].path)
        val parent = cur.parentFile
        if (isVolumeRoot(cur.absolutePath) || parent == null) return false
        openFolder(i, parent.absolutePath)
        return true
    }

    fun browserBack(): Boolean {
        val p = panes[activePane]
        if (p.selected.isNotEmpty()) {
            clearSelection(activePane)
            return true
        }
        return goUp(activePane)
    }

    fun toggleDual() {
        dual = !dual
        if (dual) {
            if (panes[1].path.isBlank()) panes[1] = PaneState(root.absolutePath)
            load(1)
        } else {
            activePane = 0
        }
    }

    // selection
    fun toggleSelect(i: Int, path: String) {
        val p = panes[i]
        panes[i] = p.copy(selected = if (path in p.selected) p.selected - path else p.selected + path)
        activePane = i
    }

    fun selectAll(i: Int) {
        val p = panes[i]
        val visible = p.items.filter { filterKind == null || it.isDir || it.kind == filterKind }
        panes[i] = p.copy(selected = visible.map { it.path }.toSet())
    }

    fun clearSelection(i: Int) { panes[i] = panes[i].copy(selected = emptySet()) }

    fun selectedFiles(i: Int): List<File> = panes[i].selected.map { File(it) }

    // open
    fun open(item: FileItem, pane: Int) {
        activePane = pane
        if (item.isDir) openFolder(pane, item.path) else openFile(item.file)
    }

    fun openFile(f: File) {
        addRecent(f)
        when (FileUtils.kindOf(f.name, false)) {
            FileKind.IMAGE -> push(Screen.Image(f))
            FileKind.VIDEO -> playVideo(f)
            FileKind.AUDIO -> push(Screen.Audio(f))
            FileKind.PDF -> push(Screen.Pdf(f))
            FileKind.APK -> push(Screen.Apk(f))
            FileKind.ARCHIVE -> if (FileUtils.isZip(f.name)) push(Screen.Zip(f)) else openExternal(f)
            FileKind.TEXT -> push(Screen.Editor(f))
            else -> openExternal(f)
        }
    }

    /** Opens the built-in video player with every video of the same folder as the playlist. */
    fun playVideo(f: File) {
        viewModelScope.launch {
            try {
                val (items, index) = withContext(Dispatchers.IO) { buildVideoQueue(f) }
                PlayerLauncher.play(getApplication(), items, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.message ?: "Cannot start the video player")
            }
        }
    }

    private fun buildVideoQueue(f: File): Pair<List<VideoItem>, Int> {
        val dir = f.parentFile
        val videos = (dir?.listFiles() ?: arrayOf(f))
            .filter { it.isFile && FileUtils.kindOf(it.name, false) == FileKind.VIDEO }
            .sortedBy { it.name.lowercase() }
            .ifEmpty { listOf(f) }
        val items = videos.map { v ->
            val base = FileUtils.baseName(v.name)
            val sub = listOf("srt", "vtt").map { File(v.parentFile, "$base.$it") }.firstOrNull { it.isFile }
            VideoItem(
                uri = Uri.fromFile(v).toString(),
                name = v.name,
                durationMs = if (v == f) durationOf(v) else 0L,
                width = 0, height = 0,
                size = v.length(),
                folderKey = dir?.absolutePath ?: "",
                folderName = dir?.name ?: "",
                folderPath = dir?.absolutePath ?: "",
                dateAdded = v.lastModified() / 1000,
                subtitleUri = sub?.let { Uri.fromFile(it).toString() },
            )
        }
        val idx = videos.indexOfFirst { it == f }.coerceAtLeast(0)
        return items to idx
    }

    private fun durationOf(f: File): Long = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        0L
    }

    fun openAsText(f: File) { addRecent(f); push(Screen.Editor(f)) }
    fun openExternal(f: File) { Intents.open(getApplication(), f)?.let { message(it) } }
    fun share(files: List<File>) { Intents.share(getApplication(), files)?.let { message(it) } }

    // file operations
    fun createFolder(i: Int, name: String) = quick { ops.createFolder(File(panes[i].path), name); "Folder created" }
    fun createFile(i: Int, name: String) = quick { ops.createFile(File(panes[i].path), name); "File created" }
    fun rename(f: File, name: String) = quick { ops.rename(f, name); "Renamed" }

    private fun quick(block: suspend () -> String) {
        viewModelScope.launch {
            try {
                val msg = withContext(Dispatchers.IO) { block() }
                message(msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.message ?: "Operation failed")
            }
            refreshAll()
        }
    }

    fun copyToClipboard(files: List<File>, move: Boolean) {
        clipboard = ClipboardState(files, move)
        panes.indices.forEach { clearSelection(it) }
        message(if (move) "${files.size} item(s) ready to move" else "${files.size} item(s) copied")
    }

    fun clearClipboard() { clipboard = null }

    fun paste(i: Int) {
        val cb = clipboard ?: return
        transfer(cb.files, File(panes[i].path), cb.move)
    }

    fun transferToOther(move: Boolean) {
        val other = 1 - activePane
        val files = selectedFiles(activePane)
        if (files.isEmpty()) return
        transfer(files, File(panes[other].path), move)
    }

    private fun transfer(files: List<File>, dest: File, move: Boolean) {
        val clash = files.filter { f ->
            val t = File(dest, f.name)
            FileUtils.present(t) && !FileUtils.sameFile(f, t)
        }
        if (clash.isNotEmpty()) {
            conflict = ConflictRequest(files, dest, move, clash.map { it.name })
            return
        }
        startTransfer(files, dest, move, Conflict.RENAME)
    }

    fun resolveConflict(choice: Conflict?) {
        val c = conflict ?: return
        conflict = null
        if (choice != null) startTransfer(c.files, c.dest, c.move, choice)
    }

    private fun startTransfer(files: List<File>, dest: File, move: Boolean, policy: Conflict) {
        runOp(if (move) "Moving" else "Copying") {
            val n = ops.transfer(files, dest, move, policy) { progress.value = it }
            if (move) clipboard = null
            panes.indices.forEach { clearSelection(it) }
            "${if (move) "Moved" else "Copied"} $n item(s)"
        }
    }

    fun delete(files: List<File>) {
        runOp("Deleting") {
            val n = ops.delete(files) { progress.value = it }
            panes.indices.forEach { clearSelection(it) }
            "Deleted $n item(s)"
        }
    }

    /** Writes a new, self-decrypting HTML file next to [file]; the original is left untouched. */
    fun encryptHtml(file: File) {
        quick {
            val out = FileUtils.uniqueFile(file.parentFile ?: root, FileUtils.baseName(file.name) + "_locked.html")
            val bytes = file.readBytes()
            out.writeText(HtmlCrypto.encrypt(bytes))
            "Encrypted copy saved as ${out.name}"
        }
    }

    /** Reverses [encryptHtml]; fails with a message if [file] isn't in our encrypted format. */
    fun decryptHtml(file: File) {
        quick {
            val html = TextFileIO.load(file).text
            val bytes = HtmlCrypto.decrypt(html)
                ?: throw java.io.IOException("This isn't an NH-encrypted HTML file")
            val out = FileUtils.uniqueFile(file.parentFile ?: root, FileUtils.baseName(file.name) + "_decrypted.html")
            out.writeBytes(bytes)
            "Decrypted copy saved as ${out.name}"
        }
    }

    fun createZip(files: List<File>, name: String) {
        val dir = File(panes[activePane].path)
        runOp("Compressing") {
            val clean = if (name.endsWith(".zip", ignoreCase = true)) name else "$name.zip"
            ops.validateName(clean)
            val out = FileUtils.uniqueFile(dir, clean)
            zipManager.create(files, out) { progress.value = it }
            panes.indices.forEach { clearSelection(it) }
            "Created ${out.name}"
        }
    }

    // ---- settings ---------------------------------------------------------------------------
    private fun edit(refresh: Boolean = false, block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch {
            settingsRepo.edit(block)
            if (refresh) refreshAll()
        }
    }

    fun setTheme(v: Int) = edit { it[K.THEME] = v }
    fun setStartDir(v: String) = edit { it[K.START] = v }
    fun setShowExt(v: Boolean) = edit { it[K.EXT] = v }
    fun setShowHidden(v: Boolean) = edit(true) { it[K.HIDDEN] = v }
    fun setSort(v: SortBy) = edit(true) { it[K.SORT] = v.ordinal }
    fun setAsc(v: Boolean) = edit(true) { it[K.ASC] = v }
    fun setGrid(v: Boolean) = edit { it[K.GRID] = v }
    fun setConfirmDelete(v: Boolean) = edit { it[K.CONFIRM] = v }
    fun setKeepRecents(v: Boolean) = edit { it[K.KEEP] = v }
    fun clearRecents() = edit { it[K.RECENTS] = "" }
    fun clearBookmarks() = edit { it[K.BOOKMARKS] = "" }

    fun addRecent(f: File) {
        if (!settings.value.keepRecents) return
        val path = f.absolutePath
        edit { p ->
            val cur = SettingsRepository.splitList(p[K.RECENTS])
            p[K.RECENTS] = (listOf(path) + cur.filter { it != path }).take(50).joinToString("\n")
        }
    }

    fun toggleBookmark(path: String) {
        edit { p ->
            val cur = SettingsRepository.splitList(p[K.BOOKMARKS])
            p[K.BOOKMARKS] = (if (path in cur) cur - path else cur + path).joinToString("\n")
        }
    }

    // ---- search -----------------------------------------------------------------------------
    var search by mutableStateOf(SearchState())
        private set
    private var searchJob: Job? = null

    fun openSearch(rootPath: String, kind: FileKind? = null, title: String = "Search") {
        search = SearchState(rootPath = rootPath, kind = kind, title = title)
        push(Screen.Search)
        if (kind != null) startSearch()
    }

    fun updateSearch(block: (SearchState) -> SearchState) {
        search = block(search)
        startSearch()
    }

    fun updateSearchSortOnly(block: (SearchState) -> SearchState) {
        search = block(search)
        resortSearch()
    }

    fun resortSearch() {
        val st = search
        search = st.copy(results = repo.sorted(st.results, st.sortBy, st.asc))
    }

    private fun startSearch() {
        searchJob?.cancel()
        val st = search
        if (st.query.isBlank() && st.kind == null) {
            search = st.copy(results = emptyList(), searching = false)
            return
        }
        search = st.copy(results = emptyList(), searching = true)
        searchJob = viewModelScope.launch {
            delay(250)
            val s = settingsRepo.settings.first()
            val q = st.query.trim()
            val acc = ArrayList<FileItem>()
            try {
                withContext(Dispatchers.IO) {
                    var lastPush = 0L
                    repo.walk(File(st.rootPath), s.showHidden) { f ->
                        if (q.isEmpty() || f.name.contains(q, ignoreCase = true)) {
                            val isDir = f.isDirectory
                            val kind = FileUtils.kindOf(f.name, isDir)
                            val okScope = when (st.scope) {
                                1 -> !isDir
                                2 -> isDir
                                else -> true
                            }
                            val okKind = st.kind == null || kind == st.kind
                            if (okScope && okKind) {
                                acc.add(repo.toItem(f, false))
                                if (acc.size >= 1000) throw LimitReached()
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastPush > 250) {
                            lastPush = now
                            val snap = ArrayList(acc)
                            withContext(Dispatchers.Main) { search = search.copy(results = snap) }
                        }
                    }
                }
            } catch (e: LimitReached) {
                // enough results; stop walking
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.message ?: "Search failed")
            }
            search = search.copy(results = repo.sorted(acc, search.sortBy, search.asc), searching = false)
        }
    }

    // ---- zip --------------------------------------------------------------------------------
    var zip by mutableStateOf<ZipState?>(null)
        private set

    fun loadZip(file: File) {
        zip = ZipState(file)
        viewModelScope.launch {
            try {
                val all = zipManager.list(file)
                zip = zip?.copy(all = all, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                zip = zip?.copy(loading = false, error = e.message ?: "Cannot read archive")
            }
        }
    }

    fun zipOpenDir(prefix: String) { zip = zip?.copy(prefix = prefix, selected = emptySet()) }

    fun zipUp(): Boolean {
        val z = zip ?: return false
        if (z.selected.isNotEmpty()) { zip = z.copy(selected = emptySet()); return true }
        if (z.prefix.isEmpty()) return false
        val trimmed = z.prefix.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        zip = z.copy(prefix = if (idx < 0) "" else trimmed.substring(0, idx + 1), selected = emptySet())
        return true
    }

    fun zipToggle(name: String) {
        val z = zip ?: return
        zip = z.copy(selected = if (name in z.selected) z.selected - name else z.selected + name)
    }

    fun zipSelectAll() {
        val z = zip ?: return
        zip = z.copy(selected = ZipManager.children(z.all, z.prefix).map { it.name }.toSet())
    }

    fun zipExtract(file: File, dest: File?, only: Set<String>?) {
        runOp("Extracting") {
            val target = dest ?: FileUtils.uniqueFile(file.parentFile ?: root, FileUtils.baseName(file.name))
            val n = zipManager.extract(file, target, only) { progress.value = it }
            "Extracted $n file(s) to ${target.name}"
        }
    }

    fun zipDelete(file: File, names: Set<String>) {
        runOp("Updating archive") {
            zipManager.deleteEntries(file, names) { progress.value = it }
            reloadZip(file)
            "Removed ${names.size} entr${if (names.size == 1) "y" else "ies"}"
        }
    }

    fun zipRename(file: File, from: String, newLeaf: String) {
        runOp("Updating archive") {
            ops.validateName(newLeaf)
            val isDir = from.endsWith("/")
            val trimmed = from.trimEnd('/')
            val parent = trimmed.substringBeforeLast('/', "")
            val to = (if (parent.isEmpty()) "" else "$parent/") + newLeaf + (if (isDir) "/" else "")
            zipManager.renameEntry(file, from, to) { progress.value = it }
            reloadZip(file)
            "Renamed"
        }
    }

    fun zipAddClipboard(file: File) {
        val cb = clipboard
        if (cb == null || cb.files.isEmpty()) {
            message("Copy files in the browser first, then add them here")
            return
        }
        val prefix = zip?.prefix ?: ""
        runOp("Updating archive") {
            zipManager.addFiles(file, cb.files, prefix) { progress.value = it }
            reloadZip(file)
            "Added ${cb.files.size} item(s)"
        }
    }

    fun zipTest(file: File) {
        runOp("Testing archive") { zipManager.test(file) }
    }

    private suspend fun reloadZip(file: File) {
        val all = zipManager.list(file)
        zip = zip?.copy(all = all, selected = emptySet(), error = null)
    }

    // ---- analysis ---------------------------------------------------------------------------
    var analysis by mutableStateOf(AnalysisState())
        private set
    val scanStatus = MutableStateFlow("")
    private var scanJob: Job? = null

    fun openAnalysis(tabIndex: Int = 0) {
        analysis = analysis.copy(tab = tabIndex)
        push(Screen.Analysis)
        if (analysis.result == null && !analysis.scanning) startScan()
    }

    fun setAnalysisTab(t: Int) { analysis = analysis.copy(tab = t) }

    fun startScan() {
        if (scanJob?.isActive == true) return
        analysis = analysis.copy(scanning = true, result = null, selected = emptySet())
        scanStatus.value = "Scanning…"
        refreshStorage()
        scanJob = viewModelScope.launch {
            try {
                val r = scanner.scan(root) { scanStatus.value = it }
                analysis = analysis.copy(scanning = false, result = r)
            } catch (e: CancellationException) {
                analysis = analysis.copy(scanning = false)
                throw e
            } catch (e: Exception) {
                analysis = analysis.copy(scanning = false)
                message(e.message ?: "Scan failed")
            }
        }
    }

    fun cancelScan() { scanJob?.cancel() }

    fun toggleAnalysisSelect(path: String) {
        val a = analysis
        analysis = a.copy(selected = if (path in a.selected) a.selected - path else a.selected + path)
    }

    fun deleteAnalysisSelection() {
        val paths = analysis.selected.toList()
        if (paths.isEmpty()) return
        runOp("Deleting") {
            val n = ops.delete(paths.map { File(it) }) { progress.value = it }
            val gone = paths.toSet()
            val a = analysis
            val r = a.result
            analysis = a.copy(
                selected = emptySet(),
                result = r?.copy(
                    largest = r.largest.filter { it.path !in gone },
                    duplicates = r.duplicates.map { g -> g.filter { it.path !in gone } }.filter { it.size > 1 },
                ),
            )
            "Deleted $n item(s)"
        }
    }
}
