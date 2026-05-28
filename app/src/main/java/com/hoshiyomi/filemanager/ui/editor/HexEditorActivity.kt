package com.hoshiyomi.filemanager.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.ActivityHexEditorBinding
import com.hoshiyomi.filemanager.databinding.DialogInputBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class HexEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHexEditorBinding
    private var file: File? = null
    private var fileBytes: ByteArray = ByteArray(0)
    private var isModified = false
    private lateinit var adapter: HexAdapter

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val BYTES_PER_ROW = 16
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10MB warning threshold
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHexEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_hex_editor)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath == null) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        file = File(filePath)
        if (!file!!.exists()) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.title = file!!.name
        setupRecyclerView()
        loadFile()
    }

    private fun setupRecyclerView() {
        adapter = HexAdapter()
        binding.rvHexContent.layoutManager = LinearLayoutManager(this)
        binding.rvHexContent.adapter = adapter
    }

    private fun loadFile() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val f = file ?: return@launch
                if (f.length() > MAX_FILE_SIZE) {
                    launch(Dispatchers.Main) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@HexEditorActivity)
                            .setTitle("Large File")
                            .setMessage("This file is ${com.hoshiyomi.filemanager.model.FileItem.formatFileSize(f.length())}. Loading large files may be slow. Continue?")
                            .setPositiveButton("Load") { _, _ ->
                                doLoadFile(f)
                            }
                            .setNegativeButton("Cancel") { _, _ ->
                                finish()
                            }
                            .show()
                    }
                    return@launch
                }
                doLoadFile(f)
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@HexEditorActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun doLoadFile(f: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                fileBytes = f.readBytes()
                val rows = createHexRows(fileBytes)

                launch(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    adapter.submitList(rows)
                    updateStatus()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@HexEditorActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun createHexRows(bytes: ByteArray): List<HexRow> {
        val rows = mutableListOf<HexRow>()
        var offset = 0
        while (offset < bytes.size) {
            val end = (offset + BYTES_PER_ROW).coerceAtMost(bytes.size)
            val rowBytes = bytes.sliceArray(offset until end)
            rows.add(
                HexRow(
                    offset = offset,
                    bytes = rowBytes,
                    ascii = buildAscii(rowBytes)
                )
            )
            offset += BYTES_PER_ROW
        }
        return rows
    }

    private fun buildAscii(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().and(0xFF)
            sb.append(if (c >= 32 && c <= 126) c.toChar() else '.')
        }
        return sb.toString()
    }

    private fun updateStatus() {
        binding.tvBytesCount.text = getString(R.string.bytes_count, fileBytes.size)
        binding.tvOffset.text = getString(R.string.current_offset, 0)
    }

    private fun showGoToOffsetDialog() {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_go_to_address_title)
        dialogBinding.editTextInput.inputType = EditorInfo.TYPE_CLASS_NUMBER
        dialogBinding.editTextInput.hint = "0x00000000"

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_go_to_address_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val input = dialogBinding.editTextInput.text.toString().trim()
                try {
                    val offset = if (input.startsWith("0x", true) || input.startsWith("0X")) {
                        input.substring(2).toInt(16)
                    } else {
                        input.toLong().toInt()
                    }
                    scrollToOffset(offset)
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid offset", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun scrollToOffset(offset: Int) {
        if (offset < 0 || offset >= fileBytes.size) {
            Toast.makeText(this, "Offset out of range (0 - ${fileBytes.size - 1})", Toast.LENGTH_SHORT).show()
            return
        }
        val rowIndex = offset / BYTES_PER_ROW
        (binding.rvHexContent.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(rowIndex, 0)
        binding.tvOffset.text = getString(R.string.current_offset, offset)
    }

    data class HexRow(
        val offset: Int,
        val bytes: ByteArray,
        val ascii: String
    ) {
        val hexString: String
            get() = bytes.joinToString(" ") { "%02X".format(it) }
        val offsetHex: String
            get() = "%08X".format(offset)
    }

    inner class HexAdapter : RecyclerView.Adapter<HexAdapter.ViewHolder>() {

        private val rows = mutableListOf<HexRow>()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvOffset: android.widget.TextView = itemView.findViewById(R.id.tvHexOffset)
            val tvHex: android.widget.TextView = itemView.findViewById(R.id.tvHexBytes)
            val tvAscii: android.widget.TextView = itemView.findViewById(R.id.tvAscii)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.item_hex_row,
                parent,
                false
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = rows[position]
            holder.tvOffset.text = row.offsetHex
            holder.tvHex.text = row.hexString.padEnd(BYTES_PER_ROW * 3 - 1, ' ')
            holder.tvAscii.text = row.ascii
            holder.itemView.setOnClickListener {
                binding.tvOffset.text = getString(R.string.current_offset, row.offset)
            }
        }

        override fun getItemCount(): Int = rows.size

        fun submitList(newRows: List<HexRow>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }
    }
}
