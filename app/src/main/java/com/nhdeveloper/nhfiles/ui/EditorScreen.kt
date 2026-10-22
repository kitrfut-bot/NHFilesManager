package com.nhdeveloper.nhfiles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhdeveloper.nhfiles.data.FileUtils
import com.nhdeveloper.nhfiles.data.TextFileIO
import java.io.File
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nhdeveloper.nhfiles.ui.theme.glassTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(main: MainViewModel, file: File) {
    val vm: EditorViewModel = viewModel()
    LaunchedEffect(file) { vm.load(file) }

    val dark = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.5f }
    val lang = remember(file) { langFor(FileUtils.extension(file.name)) }
    val syntax = remember(lang, dark) { SyntaxTransformation(lang, dark) }
    val clipboard = LocalClipboardManager.current
    val snack = remember { SnackbarHostState() }

    var menu by remember { mutableStateOf(false) }
    var encMenu by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var askDiscard by remember { mutableStateOf(false) }
    var saveAs by remember { mutableStateOf(false) }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }

    fun close() { main.pop() }
    fun tryClose() { if (vm.dirty) askDiscard = true else close() }
    BackHandler { tryClose() }

    suspend fun scrollToSelection() {
        val l = layout ?: return
        val start = vm.text.selection.start.coerceIn(0, vm.text.text.length)
        val line = l.getLineForOffset(start)
        vScroll.animateScrollTo((l.getLineTop(line) - 120f).toInt().coerceAtLeast(0))
    }
    var closeAfterSave by remember { mutableStateOf(false) }
    LaunchedEffect(vm.dirty, vm.saving, closeAfterSave) {
        if (closeAfterSave && !vm.dirty && !vm.saving) close()
    }
    LaunchedEffect(vm.error) { if (vm.error != null) closeAfterSave = false }
    var findTick by remember { mutableStateOf(0) }
    LaunchedEffect(findTick) { if (findTick > 0) scrollToSelection() }

    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val currentFile = vm.file ?: file

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            GlassTopAppBar(
                title = {
                    Text(
                        currentFile.name + if (vm.dirty) " •" else "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { IconButton(onClick = { tryClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.save() }, enabled = !vm.readOnly && vm.dirty && !vm.saving) { Icon(Icons.Filled.Save, "Save") }
                    if (vm.hasEditableRanges && !vm.readOnly) {
                        IconButton(onClick = { vm.toggleRestrict() }) {
                            Icon(
                                if (vm.restrictToStrings) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                if (vm.restrictToStrings) "Only highlighted text is editable — tap to edit everything" else "Full editing — tap to lock to highlighted text only",
                                tint = if (vm.restrictToStrings) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                    }
                    IconButton(onClick = { showFind = !showFind }) { Icon(Icons.Filled.Search, "Find") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Save as…") }, enabled = vm.canSaveAs, onClick = { menu = false; saveAs = true })
                            DropdownMenuItem(text = { Text("Select all") }, onClick = { menu = false; vm.selectAll() })
                            DropdownMenuItem(text = { Text("Copy") }, onClick = {
                                menu = false
                                val sel = vm.text.selection
                                if (!sel.collapsed) clipboard.setText(AnnotatedString(vm.text.text.substring(sel.min, sel.max)))
                            })
                            DropdownMenuItem(text = { Text("Cut") }, enabled = !vm.readOnly, onClick = {
                                menu = false
                                val v = vm.text
                                val sel = v.selection
                                if (!sel.collapsed) {
                                    clipboard.setText(AnnotatedString(v.text.substring(sel.min, sel.max)))
                                    vm.onChange(TextFieldValue(v.text.removeRange(sel.min, sel.max), TextRange(sel.min)))
                                }
                            })
                            DropdownMenuItem(text = { Text("Paste") }, enabled = !vm.readOnly, onClick = {
                                menu = false
                                val clip = clipboard.getText()?.text
                                if (clip != null) {
                                    val v = vm.text
                                    val sel = v.selection
                                    val n = v.text.substring(0, sel.min) + clip + v.text.substring(sel.max)
                                    vm.onChange(TextFieldValue(n, TextRange(sel.min + clip.length)))
                                }
                            })
                            DropdownMenuItem(text = { Text("Encoding: ${vm.charset}") }, onClick = { menu = false; encMenu = true })
                        }
                        DropdownMenu(expanded = encMenu, onDismissRequest = { encMenu = false }) {
                            TextFileIO.ENCODINGS.forEach { e ->
                                DropdownMenuItem(text = { Text(e) }, onClick = { encMenu = false; vm.reopenWith(e) })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            GlassBar {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    AnimatedVisibility(visible = showFind) {
                      Column {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                label = { Text("Find") }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp), colors = glassFieldColors(),
                            )
                            if (!vm.readOnly) {
                                OutlinedTextField(
                                    value = replacement, onValueChange = { replacement = it }, singleLine = true,
                                    label = { Text("Replace with") }, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp), colors = glassFieldColors(),
                                )
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { if (vm.findNext(query, false) < 0) vm.message = "No matches" else findTick++ }) { Text("Previous") }
                                TextButton(onClick = { if (vm.findNext(query, true) < 0) vm.message = "No matches" else findTick++ }) { Text("Next") }
                                if (!vm.readOnly) {
                                    TextButton(onClick = {
                                        if (vm.replaceCurrent(query, replacement) < 0) vm.message = "No more matches" else findTick++
                                    }) { Text("Replace") }
                                    TextButton(onClick = {
                                        val n = vm.replaceAll(query, replacement)
                                        vm.message = "Replaced $n occurrence(s)"
                                    }) { Text("Replace all") }
                                }
                            }
                        }
                        HorizontalDivider()
                      }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.undo() }, enabled = vm.canUndo && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                        IconButton(onClick = { vm.redo() }, enabled = vm.canRedo && !vm.readOnly) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                        IconButton(onClick = { showFind = !showFind }) { Icon(Icons.Filled.FindReplace, "Find and replace") }
                        val lines = vm.text.text.count { it == '\n' } + 1
                        Text(
                            "${vm.charset} · $lines lines" + if (vm.readOnly) " · read-only" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            vm.notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp),
                )
            }
            if (vm.restrictToStrings) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Only the highlighted text (in quotes) is editable — tap the lock icon to edit everything",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (vm.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val lineCount = vm.text.text.count { it == '\n' } + 1
                Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
                    if (lineCount <= 50_000) {
                        Text(
                            remember(lineCount) { (1..lineCount).joinToString("\n") },
                            style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier
                                .background(glassTint())
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .defaultMinSize(minWidth = 28.dp),
                        )
                    }
                    Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        BasicTextField(
                            value = vm.text,
                            onValueChange = { vm.onChange(it) },
                            readOnly = vm.readOnly,
                            textStyle = textStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = syntax,
                            onTextLayout = { layout = it },
                            modifier = Modifier
                                .widthIn(min = 320.dp)
                                .defaultMinSize(minHeight = 400.dp)
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    vm.error?.let { MessageDialog("Editor", it) { vm.error = null } }
    if (saveAs) {
        NameDialog("Save as", "File name", currentFile.name, "Save", { saveAs = false; vm.saveAs(it) }, { saveAs = false })
    }
    if (askDiscard) {
        GlassAlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes to ${currentFile.name} before closing?") },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    closeAfterSave = true
                    vm.save()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askDiscard = false }) { Text("Cancel") }
                    TextButton(onClick = { askDiscard = false; close() }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}
