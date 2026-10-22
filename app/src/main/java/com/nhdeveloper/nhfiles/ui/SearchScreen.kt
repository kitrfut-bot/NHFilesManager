package com.nhdeveloper.nhfiles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.nhfiles.data.FileKind
import com.nhdeveloper.nhfiles.data.SortBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: MainViewModel) {
    val st = vm.search
    val s by vm.settings.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (st.kind == null) focus.requestFocus() }

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text(st.title) },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = st.query,
                onValueChange = { q -> vm.updateSearch { it.copy(query = q) } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = glassFieldColors(),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search by file or folder name") },
                trailingIcon = {
                    if (st.query.isNotEmpty()) {
                        IconButton(onClick = { vm.updateSearch { it.copy(query = "") } }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).focusRequester(focus),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("All", "Files", "Folders").forEachIndexed { i, label ->
                    FilterChip(selected = st.scope == i, onClick = { vm.updateSearch { it.copy(scope = i) } }, label = { Text(label) })
                }
                Box {
                    FilterChip(selected = st.kind != null, onClick = { typeMenu = true }, label = { Text("Type: " + kindLabel(st.kind)) })
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        listOf<FileKind?>(null, FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.ARCHIVE, FileKind.APK, FileKind.TEXT, FileKind.PDF, FileKind.DOCUMENT)
                            .forEach { k ->
                                DropdownMenuItem(text = { Text(kindLabel(k)) }, onClick = { typeMenu = false; vm.updateSearch { it.copy(kind = k) } })
                            }
                    }
                }
                Box {
                    FilterChip(selected = false, onClick = { sortMenu = true }, label = { Text("Sort: ${st.sortBy.label} ${if (st.asc) "▲" else "▼"}") })
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortBy.values().forEach { by ->
                            DropdownMenuItem(text = { Text(by.label) }, onClick = {
                                sortMenu = false
                                vm.updateSearchSortOnly { it.copy(sortBy = by) }
                            })
                        }
                        DropdownMenuItem(text = { Text(if (st.asc) "Descending" else "Ascending") }, onClick = {
                            sortMenu = false
                            vm.updateSearchSortOnly { it.copy(asc = !it.asc) }
                        })
                    }
                }
            }
            if (st.searching) GlassLinearProgress(null, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Text(
                if (st.query.isBlank() && st.kind == null) "Type to search inside ${st.rootPath.substringAfterLast('/')}"
                else "${st.results.size} result(s)${if (st.results.size >= 1000) " (first 1000)" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            val listState = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(st.results, key = { it.path }) { item ->
                    FileRow(
                        item, false, false, s.showExtensions,
                        modifier = Modifier.scrollZoom(listState, item.path),
                        subtitle = item.file.parent ?: "",
                        onClick = {
                            if (item.isDir) vm.openBrowser(item.path, replaceTop = true) else vm.openFile(item.file)
                        },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

private fun kindLabel(k: FileKind?): String = when (k) {
    null -> "All"
    FileKind.IMAGE -> "Images"
    FileKind.VIDEO -> "Videos"
    FileKind.AUDIO -> "Audio"
    FileKind.ARCHIVE -> "Archives"
    FileKind.APK -> "APK"
    FileKind.TEXT -> "Text & code"
    FileKind.PDF -> "PDF"
    FileKind.DOCUMENT -> "Documents"
    else -> "Other"
}
