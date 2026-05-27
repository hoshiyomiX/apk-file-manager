package com.hoshiyomi.filemanager.ui.filemanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hoshiyomi.filemanager.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(
    val path: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HistoryAdapter(
    private val onHistoryClick: (String) -> Unit,
    private val onHistoryLongClick: (String) -> Unit
) : ListAdapter<HistoryEntry, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    inner class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val entry = getItem(position)
        holder.binding.tvHistoryPath.text = entry.path
        holder.binding.tvHistoryTime.text = dateFormat.format(Date(entry.timestamp))
        holder.binding.root.setOnClickListener { onHistoryClick(entry.path) }
        holder.binding.root.setOnLongClickListener {
            onHistoryLongClick(entry.path)
            true
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryEntry>() {
        override fun areItemsTheSame(old: HistoryEntry, new: HistoryEntry): Boolean = old.path == new.path
        override fun areContentsTheSame(old: HistoryEntry, new: HistoryEntry): Boolean = old == new
    }
}
