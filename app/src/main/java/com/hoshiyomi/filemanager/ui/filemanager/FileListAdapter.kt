package com.hoshiyomi.filemanager.ui.filemanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.ItemFileBinding
import com.hoshiyomi.filemanager.model.FileItem
import com.hoshiyomi.filemanager.util.FileUtils

class FileListAdapter(
    private val onFileClick: (FileItem) -> Unit,
    private val onFileLongClick: (FileItem) -> Unit,
    private val onSelectionChanged: (Set<FileItem>) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    private val items = mutableListOf<FileItem>()
    private val selectedItems = mutableSetOf<FileItem>()
    private var multiSelectMode = false

    inner class FileViewHolder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val fileItem = items[position]
        val binding = holder.binding

        binding.ivFileIcon.setImageResource(FileUtils.getFileIcon(fileItem))
        binding.tvFileName.text = fileItem.name

        if (fileItem.isDirectory) {
            binding.tvFileDetails.text = fileItem.readableDate
        } else {
            binding.tvFileDetails.text = "${fileItem.readableSize}  \u2022  ${fileItem.readableDate}"
        }

        if (multiSelectMode) {
            binding.cbSelected.visibility = View.VISIBLE
            binding.cbSelected.isChecked = selectedItems.contains(fileItem)
            binding.cbSelected.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItems.add(fileItem)
                } else {
                    selectedItems.remove(fileItem)
                }
                onSelectionChanged(selectedItems.toSet())
            }
            binding.root.setOnClickListener {
                binding.cbSelected.isChecked = !binding.cbSelected.isChecked
            }
            val isSelected = selectedItems.contains(fileItem)
            binding.root.setBackgroundColor(
                if (isSelected) {
                    ContextCompat.getColor(binding.root.context, R.color.md_theme_primary_container)
                } else {
                    ContextCompat.getColor(binding.root.context, android.R.color.transparent)
                }
            )
        } else {
            binding.cbSelected.visibility = View.GONE
            binding.root.setOnClickListener { onFileClick(fileItem) }
            binding.root.setOnLongClickListener {
                onFileLongClick(fileItem)
                true
            }
            binding.root.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, android.R.color.transparent)
            )
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<FileItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setMultiSelectMode(enabled: Boolean) {
        if (multiSelectMode == enabled) return
        multiSelectMode = enabled
        if (!enabled) {
            selectedItems.clear()
            onSelectionChanged(emptySet())
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedItems.clear()
        onSelectionChanged(emptySet())
        notifyDataSetChanged()
    }

    fun getSelectedItems(): Set<FileItem> = selectedItems.toSet()

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(items)
        onSelectionChanged(selectedItems.toSet())
        notifyDataSetChanged()
    }

    fun isMultiSelectMode(): Boolean = multiSelectMode

    fun toggleSelection(fileItem: FileItem) {
        if (selectedItems.contains(fileItem)) {
            selectedItems.remove(fileItem)
        } else {
            selectedItems.add(fileItem)
        }
        onSelectionChanged(selectedItems.toSet())
        notifyDataSetChanged()
    }
}
