package com.hoshiyomi.filemanager.ui.apkviewer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.core.apk.ApkAnalyzer
import com.hoshiyomi.filemanager.core.apk.ZipEntryInfo
import com.hoshiyomi.filemanager.databinding.FragmentFilesBinding
import com.hoshiyomi.filemanager.databinding.ItemFileBinding
import com.hoshiyomi.filemanager.ui.editor.TextEditorActivity
import com.hoshiyomi.filemanager.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ApkFilesAdapter
    private var apkFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ApkFilesAdapter(
            onFileClick = { entry -> onEntryClick(entry) }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(context)
        binding.rvFiles.adapter = adapter
        apkFile?.let { loadApkFiles(it) }
    }

    fun loadApkFiles(file: File) {
        apkFile = file
        if (_binding != null) {
            doLoadFiles(file)
        }
    }

    private fun doLoadFiles(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.rvFiles.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val entries = ApkAnalyzer.listApkContents(file)
            launch(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (entries.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvFiles.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvFiles.visibility = View.VISIBLE
                    adapter.submitList(entries)
                }
            }
        }
    }

    private fun onEntryClick(entry: ZipEntryInfo) {
        val apk = apkFile ?: return
        if (entry.isDirectory) return

        val isTextFile = entry.name.endsWith(".txt", true) ||
            entry.name.endsWith(".xml", true) ||
            entry.name.endsWith(".json", true) ||
            entry.name.endsWith(".smali", true) ||
            entry.name.endsWith(".properties", true) ||
            entry.name.endsWith(".yml", true) ||
            entry.name.endsWith(".yaml", true) ||
            entry.name.endsWith(".cfg", true) ||
            entry.name.endsWith(".pro", true)

        if (isTextFile) {
            lifecycleScope.launch {
                val cacheDir = requireContext().cacheDir
                val extractDir = File(cacheDir, "apk_extract")
                extractDir.mkdirs()
                val result = ApkAnalyzer.extractFile(apk, entry.name, extractDir)
                result.onSuccess { extractedFile ->
                    val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                        putExtra(TextEditorActivity.EXTRA_FILE_PATH, extractedFile.absolutePath)
                    }
                    startActivity(intent)
                }.onFailure { e ->
                    Toast.makeText(context, "Extract failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Click 'Extract All' to extract this file", Toast.LENGTH_SHORT).show()
        }
    }

    inner class ApkFilesAdapter(
        private val onFileClick: (ZipEntryInfo) -> Unit
    ) : RecyclerView.Adapter<ApkFilesAdapter.ViewHolder>() {

        private val items = mutableListOf<ZipEntryInfo>()

        inner class ViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            val name = entry.name.substringAfterLast('/')

            holder.binding.tvFileName.text = name
            holder.binding.cbSelected.visibility = View.GONE

            if (entry.isDirectory) {
                holder.binding.ivFileIcon.setImageResource(R.drawable.ic_folder)
                holder.binding.tvFileDetails.text = "Directory"
            } else {
                val ext = name.substringAfterLast('.', "").lowercase()
                holder.binding.ivFileIcon.setImageResource(FileUtils.getFileExtensionIcon(ext))
                holder.binding.tvFileDetails.text = com.hoshiyomi.filemanager.model.FileItem.formatFileSize(entry.size)
            }

            holder.binding.root.setOnClickListener {
                if (!entry.isDirectory) {
                    onFileClick(entry)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        fun submitList(entries: List<ZipEntryInfo>) {
            items.clear()
            items.addAll(entries)
            notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
