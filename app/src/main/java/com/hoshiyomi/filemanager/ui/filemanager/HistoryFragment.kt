package com.hoshiyomi.filemanager.ui.filemanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.FragmentHistoryBinding
import java.io.File

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FileManagerViewModel by activityViewModels()

    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onHistoryClick = { path ->
                navigateToPath(path)
            },
            onHistoryLongClick = { path ->
                showHistoryOptions(path)
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(context)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun setupListeners() {
        binding.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_clear_title)
                .setMessage(R.string.history_clear_message)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    viewModel.clearHistory()
                    loadHistory()
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun loadHistory() {
        val history = viewModel.getNavigationHistory()
        if (history.isEmpty()) {
            binding.tvHistoryEmpty.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
            binding.btnClearHistory.visibility = View.GONE
        } else {
            binding.tvHistoryEmpty.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
            binding.btnClearHistory.visibility = View.VISIBLE
            historyAdapter.submitList(history)
        }
    }

    private fun navigateToPath(path: String) {
        val file = File(path)
        if (file.exists() && file.isDirectory && file.canRead()) {
            val fragment = FileManagerFragment.newInstance(path)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment, "file_manager")
                .addToBackStack("file_manager")
                .commitAllowingStateLoss()
        } else {
            Toast.makeText(context, getString(R.string.history_path_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHistoryOptions(path: String) {
        val options = arrayOf(
            getString(R.string.history_open),
            getString(R.string.action_copy),
            getString(R.string.history_remove)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(path)
            .setItems(options) { _, which ->
                when (options[which]) {
                    getString(R.string.history_open) -> navigateToPath(path)
                    getString(R.string.action_copy) -> {
                        android.content.ClipData.newPlainText("path", path).let {
                            requireActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                ?.let { cm -> (cm as android.content.ClipboardManager).setPrimaryClip(it) }
                        }
                        Toast.makeText(context, R.string.history_path_copied, Toast.LENGTH_SHORT).show()
                    }
                    getString(R.string.history_remove) -> {
                        viewModel.removeHistoryEntry(path)
                        loadHistory()
                    }
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
