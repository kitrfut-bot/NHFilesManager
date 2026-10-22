package com.nhdeveloper.nhfiles.vpo.ui

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhdeveloper.nhfiles.R
import com.nhdeveloper.nhfiles.vpo.data.AppSettings
import com.nhdeveloper.nhfiles.vpo.data.Keys
import com.nhdeveloper.nhfiles.databinding.FragmentRowsBinding
import com.nhdeveloper.nhfiles.vpo.util.ThemePrefs
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : Fragment(R.layout.fragment_rows) {
    private val vm: LibraryViewModel by activityViewModels()
    private lateinit var container: android.widget.LinearLayout

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    companion object {
        val SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val ASPECTS = arrayOf("Fit", "Fill", "Crop", "16:9", "4:3")
        val COLORS = intArrayOf(Color.WHITE, Color.YELLOW, Color.CYAN, 0xFFFF6A00.toInt())
        val COLOR_NAMES = arrayOf("White", "Yellow", "Cyan", "Orange")
        val SCALES = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val LANG_CODES = arrayOf("", "en", "hi", "bn", "es", "fr", "de", "ar", "pt", "ru", "ja", "ko")
        val LANG_NAMES = arrayOf("Default", "English", "Hindi", "Bengali", "Spanish", "French",
            "German", "Arabic", "Portuguese", "Russian", "Japanese", "Korean")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        container = FragmentRowsBinding.bind(view).rows
        container.enterAnim()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.collect { build(it) }
            }
        }
    }

    private fun speedLabel(s: Float) = if (s == 1f) "1.0x (Normal)" else "${s}x"

    private fun choose(title: String, items: Array<String>, checked: Int, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(title)
            .setSingleChoiceItems(items, checked) { d, i -> onPick(i); d.dismiss() }.show()
    }

    private fun build(s: AppSettings) {
        container.removeAllViews()
        container.addHeader("APPEARANCE")
        container.addRow(R.drawable.ic_brightness, "Theme", ThemePrefs.LABELS[ThemePrefs.get(requireContext())]) {
            choose("Theme", ThemePrefs.LABELS, ThemePrefs.get(requireContext())) {
                ThemePrefs.set(requireContext(), it)   // recreates the screen with the new theme
            }
        }
        container.addHeader("PLAYBACK")
        container.addSwitchRow(R.drawable.ic_history, "Resume playback", "Continue from where you stopped", s.resume) {
            vm.setSetting(Keys.RESUME, it)
        }
        container.addSwitchRow(R.drawable.ic_next, "Auto-play next", "Play the next video in the list", s.autoPlayNext) {
            vm.setSetting(Keys.AUTONEXT, it)
        }
        container.addSwitchRow(R.drawable.ic_audio, "Background audio",
            "Keep playing sound when the screen is off or app is in background", s.bgAudio) {
            vm.setSetting(Keys.BGAUDIO, it)
            if (it && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        container.addSwitchRow(R.drawable.ic_pip, "Auto Picture-in-Picture",
            "Enter PiP when you press Home during playback", s.autoPip) { vm.setSetting(Keys.AUTOPIP, it) }
        container.addRow(R.drawable.ic_speed, "Default playback speed", speedLabel(s.defaultSpeed)) {
            choose("Playback speed", SPEEDS.map { speedLabel(it) }.toTypedArray(),
                SPEEDS.indexOfFirst { it == s.defaultSpeed }) { vm.setSetting(Keys.SPEED, SPEEDS[it]) }
        }
        container.addRow(R.drawable.ic_aspect, "Default aspect ratio", ASPECTS[s.defaultAspect.coerceIn(0, 4)]) {
            choose("Aspect ratio", ASPECTS, s.defaultAspect) { vm.setSetting(Keys.ASPECT, it) }
        }
        container.addRow(R.drawable.ic_audio, "Preferred audio language",
            LANG_NAMES[LANG_CODES.indexOf(s.audioLang).coerceAtLeast(0)]) {
            choose("Audio language", LANG_NAMES, LANG_CODES.indexOf(s.audioLang)) { vm.setSetting(Keys.AUDIOLANG, LANG_CODES[it]) }
        }

        container.addHeader("SUBTITLES")
        container.addRow(R.drawable.ic_subtitle, "Subtitle size", "${(s.subtitleScale * 100).toInt()}%") {
            choose("Subtitle size", SCALES.map { "${(it * 100).toInt()}%" }.toTypedArray(),
                SCALES.indexOfFirst { it == s.subtitleScale }) { vm.setSetting(Keys.SUBSCALE, SCALES[it]) }
        }
        container.addRow(R.drawable.ic_subtitle, "Subtitle color",
            COLOR_NAMES[COLORS.indexOf(s.subtitleColor).coerceAtLeast(0)]) {
            choose("Subtitle color", COLOR_NAMES, COLORS.indexOf(s.subtitleColor)) { vm.setSetting(Keys.SUBCOLOR, COLORS[it]) }
        }
        container.addRow(R.drawable.ic_subtitle, "Preferred subtitle language",
            LANG_NAMES[LANG_CODES.indexOf(s.subLang).coerceAtLeast(0)]) {
            choose("Subtitle language", LANG_NAMES, LANG_CODES.indexOf(s.subLang)) { vm.setSetting(Keys.SUBLANG, LANG_CODES[it]) }
        }

        container.addHeader("LIBRARY")
        container.addRow(R.drawable.ic_sort, "Sort order",
            LibraryViewModel.SORT_LABELS[LibraryViewModel.SORT_KEYS.indexOf(s.sort).coerceAtLeast(0)]) {
            choose("Sort by", LibraryViewModel.SORT_LABELS.toTypedArray(),
                LibraryViewModel.SORT_KEYS.indexOf(s.sort)) { vm.setSetting(Keys.SORT, LibraryViewModel.SORT_KEYS[it]) }
        }
        container.addRow(R.drawable.ic_folder_add, "Add folder", "Choose a folder through the system picker") {
            (activity as MainActivity).pickFolder()
        }
        if (s.trees.isNotEmpty()) {
            container.addRow(R.drawable.ic_delete, "Remove added folders", "${s.trees.size} folder(s) added") {
                val list = s.trees.toList()
                val names = list.map { android.net.Uri.parse(it).lastPathSegment?.substringAfterLast(':')?.ifEmpty { it } ?: it }
                    .toTypedArray()
                MaterialAlertDialogBuilder(requireContext()).setTitle("Remove folder")
                    .setItems(names) { _, i -> vm.removeTree(list[i]) }.show()
            }
        }
        container.addRow(R.drawable.ic_delete, "Clear history") {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Clear history?")
                .setPositiveButton("Clear") { _, _ -> vm.clearHistory() }
                .setNegativeButton("Cancel", null).show()
        }
    }

    override fun onResume() { super.onResume(); setScreenTitle("Settings") }
}
