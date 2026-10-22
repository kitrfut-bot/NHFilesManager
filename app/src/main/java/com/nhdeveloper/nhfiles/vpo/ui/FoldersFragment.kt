package com.nhdeveloper.nhfiles.vpo.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.databinding.FragmentListBinding
import kotlinx.coroutines.launch

class FoldersFragment : Fragment(R.layout.fragment_list) {
    private val vm: LibraryViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentListBinding.bind(view)
        b.searchInput.isVisible = false
        b.btnSort.isVisible = false
        (b.searchInput.parent as View).isVisible = false
        val adapter = FolderAdapter { f ->
            (activity as MainActivity).push(
                VideoListFragment.newInstance(VideoListFragment.MODE_FOLDER, f.key, f.name))
        }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.setHasFixedSize(true)
        b.recycler.adapter = adapter
        var animated = false
        b.btnFolder.setOnClickListener { (activity as MainActivity).pickFolder() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.folders.collect {
                        adapter.submitList(it) {
                            if (!animated && it.isNotEmpty()) { animated = true; b.recycler.scheduleLayoutAnimation() }
                        }
                        b.emptyState.fadeVisible(it.isEmpty())
                        b.emptyTitle.text = "No folders"
                        b.emptyMessage.text = "Folders appear once videos are found."
                        b.btnFolder.isVisible = it.isEmpty()
                    }
                }
                launch { vm.loading.collect { b.loading.isVisible = it } }
            }
        }
    }

    override fun onResume() { super.onResume(); if (parentFragment == null) setScreenTitle("Folders") }
}
