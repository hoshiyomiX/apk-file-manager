package com.hoshiyomi.filemanager.ui.editor

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.ActivityTextEditorBinding
import com.hoshiyomi.filemanager.databinding.DialogInputBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset

class TextEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextEditorBinding
    private var file: File? = null
    private var originalContent: String = ""
    private var wordWrapEnabled = true
    private var encoding = "UTF-8"

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_text_editor)
        binding.toolbar.setNavigationOnClickListener { handleBackPress() }

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
        setupMenu()
        setupEditor()
        loadFile()
    }

    private fun setupMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.menu_text_editor, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_save -> {
                        saveFile()
                        true
                    }
                    R.id.action_undo -> {
                        binding.editTextContent.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_Z, android.view.META_CTRL_MASK))
                        true
                    }
                    R.id.action_redo -> {
                        binding.editTextContent.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_Z, android.view.META_CTRL_MASK or android.view.META_SHIFT_MASK))
                        true
                    }
                    R.id.action_find_replace -> {
                        showFindReplaceDialog()
                        true
                    }
                    R.id.action_word_wrap -> {
                        wordWrapEnabled = !wordWrapEnabled
                        menuItem.isChecked = wordWrapEnabled
                        binding.editTextContent.setHorizontallyScrolling(!wordWrapEnabled)
                        true
                    }
                    R.id.action_encoding -> {
                        showEncodingDialog()
                        true
                    }
                    R.id.action_go_to_line -> {
                        showGoToLineDialog()
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun setupEditor() {
        binding.editTextContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                updateStatusBar()
            }
        })

        binding.editTextContent.setOnKeyListener { _, _, _ ->
            updateStatusBar()
            false
        }

        binding.editTextContent.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: Menu?): Boolean = true
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: Menu?): Boolean = true
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
    }

    private fun loadFile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val f = file ?: return@launch
                val bytes = f.readBytes()
                val content = detectAndDecode(bytes)
                val lineCount = content.count { it == '\n' } + if (content.isNotEmpty() && content.last() != '\n') 1 else 0

                launch(Dispatchers.Main) {
                    originalContent = content
                    binding.editTextContent.setText(content)
                    binding.editTextContent.post {
                        updateStatusBar()
                    }
                    updateLineCount(lineCount)
                    binding.tvEncoding.text = encoding
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun detectAndDecode(bytes: ByteArray): String {
        return try {
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                encoding = "UTF-8 (BOM)"
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            } else {
                val text = String(bytes, Charsets.UTF_8)
                if (text.contains("\uFFFD")) {
                    encoding = "ISO-8859-1"
                    String(bytes, Charsets.ISO_8859_1)
                } else {
                    encoding = "UTF-8"
                    text
                }
            }
        } catch (e: Exception) {
            encoding = "UTF-8"
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun saveFile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val f = file ?: return@launch
                val content = binding.editTextContent.text.toString()
                val charset = if (encoding.startsWith("ISO")) Charsets.ISO_8859_1 else Charsets.UTF_8
                f.writeText(content, charset)
                originalContent = content

                launch(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, R.string.file_saved, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFindReplaceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val findEdit = dialogView.findViewById<EditText>(R.id.editTextInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_find_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val query = findEdit.text.toString()
                if (query.isNotBlank()) {
                    findInText(query)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun findInText(query: String) {
        val text = binding.editTextContent.text.toString()
        val startIndex = binding.editTextContent.selectionEnd
        val index = text.indexOf(query, startIndex, ignoreCase = true)

        if (index >= 0) {
            binding.editTextContent.requestFocus()
            binding.editTextContent.setSelection(index, index + query.length)
            val layout = binding.editTextContent.layout
            if (layout != null) {
                val line = layout.getLineForOffset(index)
                binding.editTextContent.scrollTo(0, layout.getLineTop(line) - binding.editTextContent.height / 2)
            }
        } else {
            Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEncodingDialog() {
        val encodings = arrayOf("UTF-8", "ISO-8859-1", "US-ASCII", "UTF-16", "Windows-1252")
        val current = encoding

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_encoding)
            .setSingleChoiceItems(encodings, encodings.indexOfFirst { current.contains(it) }) { dialog, which ->
                if (which != encodings.indexOfFirst { current.contains(it) }) {
                    Toast.makeText(this, "Reopen file to apply encoding", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showGoToLineDialog() {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_go_to_line_title)
        dialogBinding.editTextInput.inputType = EditorInfo.TYPE_CLASS_NUMBER

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_go_to_line_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val lineNum = dialogBinding.editTextInput.text.toString().toIntOrNull()
                if (lineNum != null && lineNum > 0) {
                    goToLine(lineNum)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun goToLine(lineNumber: Int) {
        val layout = binding.editTextContent.layout ?: return
        val lineCount = layout.lineCount
        if (lineNumber > lineCount) {
            Toast.makeText(this, "Line $lineNumber exceeds total lines ($lineCount)", Toast.LENGTH_SHORT).show()
            return
        }
        val lineStart = layout.getLineStart(lineNumber - 1)
        binding.editTextContent.requestFocus()
        binding.editTextContent.setSelection(lineStart)
        binding.editTextContent.scrollTo(0, layout.getLineTop(lineNumber - 1))
    }

    private fun handleBackPress() {
        if (isModified()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.file_not_saved)
                .setMessage("Do you want to save changes before closing?")
                .setPositiveButton(R.string.action_save) { _, _ ->
                    saveFile()
                    finish()
                }
                .setNegativeButton("Discard") { _, _ ->
                    finish()
                }
                .setNeutralButton(R.string.dialog_cancel, null)
                .show()
        } else {
            finish()
        }
    }

    private fun isModified(): Boolean {
        return binding.editTextContent.text.toString() != originalContent
    }

    private fun updateStatusBar() {
        val editable = binding.editTextContent.text ?: return
        val cursorPos = binding.editTextContent.selectionStart
        val lineCount = editable.count { it == '\n' } + if (editable.isNotEmpty()) 1 else 0
        val currentLine = editable.substring(0, cursorPos).count { it == '\n' } + 1
        val currentCol = cursorPos - editable.substring(0, cursorPos).lastIndexOf('\n').coerceAtLeast(-1)

        binding.tvLineCount.text = getString(R.string.line_count, lineCount)
        binding.tvColumnPosition.text = getString(R.string.column_position, currentCol)
    }

    private fun updateLineCount(count: Int) {
        binding.tvLineCount.text = getString(R.string.line_count, count)
    }

    override fun onPause() {
        super.onPause()
        if (isModified()) {
            saveFile()
        }
    }
}
