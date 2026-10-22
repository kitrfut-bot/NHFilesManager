package com.nhdeveloper.nhfiles.vpo.ui

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment(R.layout.fragment_playlists) {
    private val vm: LibraryViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentPlaylistsBinding.bind(view)
        val adapter = PlaylistAdapter(
            onClick = { p ->
                (activity as MainActivity).push(
                    VideoListFragment.newInstance(VideoListFragment.MODE_PLAYLIST, p.id.toString(), p.name))
            },
            onMore = { anchor, p ->
                val pm = PopupMenu(requireContext(), anchor)
                pm.menu.add(0, 1, 0, "Rename")
                pm.menu.add(0, 2, 1, "Delete")
                pm.setOnMenuItemClickListener {
                    if (it.itemId == 1) promptText(requireContext(), "Rename playlist", p.name, "Save") { n -> vm.renamePlaylist(p.id, n) }
                    else MaterialAlertDialogBuilder(requireContext()).setTitle("Delete \"${p.name}\"?")
                        .setMessage("Videos stay on your device.")
                        .setPositiveButton("Delete") { _, _ -> vm.deletePlaylist(p.id) }
                        .setNegativeButton("Cancel", null).show()
                    true
                }
                pm.show()
            }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        var animated = false
        b.fabAdd.setOnClickListener {
            promptText(requireContext(), "New playlist", "", "Create") { vm.createPlaylist(it, null) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.playlists.collect {
                    adapter.submitList(it) {
                        if (!animated && it.isNotEmpty()) { animated = true; b.recycler.scheduleLayoutAnimation() }
                    }
                    b.empty.fadeVisible(it.isEmpty())
                }
            }
        }
    }

    override fun onResume() { super.onResume(); if (parentFragment == null) setScreenTitle("Playlists") }
}
