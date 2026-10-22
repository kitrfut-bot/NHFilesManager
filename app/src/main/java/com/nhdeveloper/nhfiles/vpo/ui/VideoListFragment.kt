package com.nhdeveloper.nhfiles.vpo.ui

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.vpo.data.Keys
import com.nhdeveloper.nhfiles.vpo.data.VideoItem
import com.nhdeveloper.nhfiles.databinding.FragmentListBinding
import com.nhdeveloper.nhfiles.vpo.player.PlayerLauncher
import com.nhdeveloper.nhfiles.vpo.util.hasVideoPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VideoListFragment : Fragment(R.layout.fragment_list) {
    companion object {
        const val MODE_ALL = "all"
        const val MODE_FOLDER = "folder"
        const val MODE_FAVORITES = "favorites"
        const val MODE_HISTORY = "history"
        const val MODE_PLAYLIST = "playlist"

        fun newInstance(mode: String, key: String = "", title: String = "") = VideoListFragment().apply {
            arguments = Bundle().apply {
                putString("mode", mode); putString("key", key); putString("title", title)
            }
        }
    }

    private val vm: LibraryViewModel by activityViewModels()
    private val mode get() = requireArguments().getString("mode") ?: MODE_ALL
    private val key get() = requireArguments().getString("key") ?: ""
    private val query = MutableStateFlow("")
    private var favorites: Set<String> = emptySet()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentListBinding.bind(view)
        val adapter = VideoAdapter(
            onClick = { list, index -> PlayerLauncher.play(requireContext(), list, index) },
            onMore = { anchor, v ->
                val cur = (b.recycler.adapter as VideoAdapter).currentList
                VideoMenu.show(this, vm, anchor, v, favorites, cur, mode, key)
            }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.setHasFixedSize(true)
        b.recycler.setItemViewCacheSize(20)
        b.recycler.adapter = adapter
        var animated = false
        b.searchInput.doAfterTextChanged { query.value = it?.toString().orEmpty() }
        b.btnSort.isVisible = mode == MODE_ALL || mode == MODE_FOLDER
        b.btnClear.isVisible = mode == MODE_HISTORY
        b.btnSort.setOnClickListener {
            val current = vm.settings.value.sort
            MaterialAlertDialogBuilder(requireContext()).setTitle("Sort by")
                .setSingleChoiceItems(LibraryViewModel.SORT_LABELS.toTypedArray(),
                    LibraryViewModel.SORT_KEYS.indexOf(current)) { d, i ->
                    vm.setSetting(Keys.SORT, LibraryViewModel.SORT_KEYS[i]); d.dismiss()
                }.show()
        }
        b.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Clear history?")
                .setMessage("Resume positions will be forgotten.")
                .setPositiveButton("Clear") { _, _ -> vm.clearHistory() }
                .setNegativeButton("Cancel", null).show()
        }
        b.btnAllow.setOnClickListener { (activity as MainActivity).requestVideoPermission() }
        b.btnFolder.setOnClickListener { (activity as MainActivity).pickFolder() }

        val source: Flow<List<VideoItem>> = when (mode) {
            MODE_FOLDER -> vm.videos.map { l -> l.filter { it.folderKey == key } }
            MODE_FAVORITES -> vm.favorites
            MODE_HISTORY -> vm.history.map { l -> l.map { it.first } }
            MODE_PLAYLIST -> vm.playlistVideos(key.toLong())
            else -> vm.videos
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(source, query, vm.scanned) { l, q, scanned ->
                        Triple(if (q.isBlank()) l else l.filter { it.name.contains(q.trim(), true) }, q, scanned)
                    }.flowOn(Dispatchers.Default).collect { (list, q, scanned) ->
                        adapter.submitList(list) {
                            if (!animated && list.isNotEmpty()) { animated = true; b.recycler.scheduleLayoutAnimation() }
                        }
                        val empty = list.isEmpty()
                        b.emptyState.fadeVisible(empty && (scanned || mode != MODE_ALL))
                        b.recycler.isVisible = !empty
                        val noAccess = mode == MODE_ALL && !hasVideoPermission(requireContext()) &&
                            vm.settings.value.trees.isEmpty()
                        b.btnAllow.isVisible = noAccess
                        b.btnFolder.isVisible = mode == MODE_ALL
                        b.emptyTitle.text = when {
                            q.isNotBlank() -> "No results"
                            noAccess -> "Allow access to your videos"
                            mode == MODE_FAVORITES -> "No favorites yet"
                            mode == MODE_HISTORY -> "No history yet"
                            mode == MODE_PLAYLIST -> "This playlist is empty"
                            else -> "No videos found"
                        }
                        b.emptyMessage.text = when {
                            q.isNotBlank() -> "Try a different search."
                            noAccess -> "Video Player Offline reads videos stored on this device. Nothing is uploaded."
                            mode == MODE_ALL -> "Pull in videos by choosing a folder, or tap refresh."
                            else -> "Use the ⋮ menu on a video to add it here."
                        }
                    }
                }
                launch { vm.favoriteSet.collect { favorites = it; adapter.favorites = it } }
                launch { vm.loading.collect { b.loading.isVisible = it } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val t = requireArguments().getString("title").orEmpty()
        if (t.isNotEmpty()) setScreenTitle(t)
    }
}
