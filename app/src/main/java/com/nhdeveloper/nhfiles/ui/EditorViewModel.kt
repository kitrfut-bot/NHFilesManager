package com.nhdeveloper.nhfiles.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.TextFileIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    var file by mutableStateOf<File?>(null)
        private set
    var text by mutableStateOf(TextFieldValue(""))
        private set
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var readOnly by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    var charset by mutableStateOf("UTF-8")
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var canSaveAs by mutableStateOf(false)
        private set
    var lang by mutableStateOf(Lang.NONE)
        private set
    /** When true, only the text inside quoted strings (shown in green) can be typed into or deleted. */
    var restrictToStrings by mutableStateOf(false)
        private set
    var hasEditableRanges by mutableStateOf(false)
        private set
    private var stringRanges: List<IntRange> = emptyList()

    private var savedText = ""
    private var bom = false
    private var crlf = false
    private var lastPush = 0L
    private val undo = ArrayList<TextFieldValue>()
    private val redo = ArrayList<TextFieldValue>()

    val undoEnabled: Boolean get() = text.text.length < 500_000

    fun load(f: File, charsetOverride: String? = null) {
        file = f
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { TextFileIO.load(f, charsetOverride) }
                text = TextFieldValue(r.text)
                savedText = r.text
                charset = r.charset
                bom = r.hasBom
                crlf = r.crlf
                readOnly = r.truncated || r.binary || !f.canWrite()
                canSaveAs = !r.truncated && !r.binary
                notice = when {
                    r.truncated -> "Large file: showing the first 1 MB, read-only"
                    r.binary -> "Binary content detected: opened read-only"
                    !f.canWrite() -> "This file is not writable: read-only mode"
                    else -> null
                }
                lang = langFor(FileUtils.extension(f.name))
                stringRanges = findStringRanges(r.text)
                hasEditableRanges = stringRanges.isNotEmpty()
                restrictToStrings = lang != Lang.NONE && hasEditableRanges && !readOnly
                undo.clear(); redo.clear()
                dirty = false
                updateFlags()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Cannot open file: ${e.message}"
            }
            loading = false
        }
    }

    private fun updateFlags() {
        canUndo = undo.isNotEmpty()
        canRedo = redo.isNotEmpty()
    }

    fun toggleRestrict() {
        if (!hasEditableRanges) return
        restrictToStrings = !restrictToStrings
        message = if (restrictToStrings) "Only highlighted text is editable" else "Full editing unlocked"
    }

    /** True when the text span from start to end falls entirely inside one editable (string) range. */
    private fun spanIsEditable(start: Int, end: Int): Boolean =
        stringRanges.any { r -> start >= r.first && end <= r.last + 1 }

    fun onChange(v: TextFieldValue) {
        if (readOnly) return
        val old = text
        if (restrictToStrings && v.text != old.text) {
            val oldT = old.text
            val newT = v.text
            val minLen = minOf(oldT.length, newT.length)
            var start = 0
            while (start < minLen && oldT[start] == newT[start]) start++
            var oldEnd = oldT.length
            var newEnd = newT.length
            while (oldEnd > start && newEnd > start && oldT[oldEnd - 1] == newT[newEnd - 1]) {
                oldEnd--; newEnd--
            }
            if (!spanIsEditable(start, oldEnd)) {
                // Edit touches locked (non-string) text: keep the text, just move the cursor/selection.
                text = old.copy(selection = v.selection)
                return
            }
        }
        if (v.text != old.text) {
            if (undoEnabled) {
                val now = SystemClock.elapsedRealtime()
                if (undo.isEmpty() || now - lastPush > 700) {
                    undo.add(old)
                    if (undo.size > 200) undo.removeAt(0)
                }
                lastPush = now
            }
            redo.clear()
            dirty = v.text != savedText
        }
        text = v
        if (v.text != old.text) refreshRanges()
        updateFlags()
    }

    private fun refreshRanges() {
        if (text.text.length > 200_000) return // skip on very large files, same cap as syntax highlighting
        stringRanges = findStringRanges(text.text)
        hasEditableRanges = stringRanges.isNotEmpty()
        if (!hasEditableRanges) restrictToStrings = false
    }

    fun undo() {
        if (undo.isEmpty()) return
        redo.add(text)
        text = undo.removeAt(undo.lastIndex)
        dirty = text.text != savedText
        refreshRanges()
        updateFlags()
    }

    fun redo() {
        if (redo.isEmpty()) return
        undo.add(text)
        text = redo.removeAt(redo.lastIndex)
        dirty = text.text != savedText
        refreshRanges()
        updateFlags()
    }

    fun selectAll() { text = text.copy(selection = TextRange(0, text.text.length)) }

    /** Selects the next match; returns the match start or -1. */
    fun findNext(query: String, forward: Boolean): Int {
        if (query.isEmpty()) return -1
        val t = text.text
        var idx = if (forward) {
            t.indexOf(query, text.selection.max, ignoreCase = true)
        } else {
            if (text.selection.min - 1 < 0) -1 else t.lastIndexOf(query, text.selection.min - 1, ignoreCase = true)
        }
        if (idx < 0) idx = if (forward) t.indexOf(query, 0, ignoreCase = true) else t.lastIndexOf(query, t.length, ignoreCase = true)
        if (idx < 0) return -1
        text = text.copy(selection = TextRange(idx, idx + query.length))
        return idx
    }

    fun replaceCurrent(query: String, replacement: String): Int {
        if (readOnly || query.isEmpty()) return -1
        val sel = text.selection
        val t = text.text
        if (!sel.collapsed && t.substring(sel.min, sel.max).equals(query, ignoreCase = true)) {
            val n = t.substring(0, sel.min) + replacement + t.substring(sel.max)
            val cursor = sel.min + replacement.length
            onChange(TextFieldValue(n, TextRange(cursor)))
        }
        return findNext(query, true)
    }

    fun replaceAll(query: String, replacement: String): Int {
        if (readOnly || query.isEmpty()) return 0
        val re = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        val count = re.findAll(text.text).count()
        if (count > 0) {
            onChange(TextFieldValue(re.replace(text.text, Regex.escapeReplacement(replacement))))
        }
        return count
    }

    fun reopenWith(cs: String) {
        val f = file ?: return
        if (dirty) {
            charset = cs
            message = "Encoding for saving set to $cs"
        } else {
            load(f, cs)
        }
    }

    fun save() {
        val f = file ?: return
        if (readOnly) return
        doSave(f)
    }

    fun saveAs(name: String) {
        val dir = file?.parentFile ?: return
        if (!canSaveAs) return
        try {
            if (name.isBlank() || name.contains('/')) throw IllegalArgumentException("Invalid file name")
        } catch (e: IllegalArgumentException) {
            error = e.message
            return
        }
        val target = File(dir, name)
        if (FileUtils.present(target)) {
            error = "A file named \"$name\" already exists. Choose another name."
            return
        }
        doSave(target)
    }

    private fun doSave(target: File) {
        if (saving) return
        saving = true
        val content = text.text
        val cs = charset
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { TextFileIO.save(target, content, cs, bom, crlf) }
                file = target
                savedText = content
                dirty = false
                readOnly = false
                message = "Saved ${target.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Save failed: ${e.message}. The original file was not modified."
            }
            saving = false
        }
    }
}
