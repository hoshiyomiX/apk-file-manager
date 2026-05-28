package com.hoshiyomi.filemanager.ui.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.DialogConfirmBinding
import com.hoshiyomi.filemanager.databinding.DialogInputBinding
import com.hoshiyomi.filemanager.databinding.DialogSortBinding
import com.hoshiyomi.filemanager.databinding.FragmentFileManagerBinding
import com.hoshiyomi.filemanager.model.FileItem
import com.hoshiyomi.filemanager.model.SortMode
import com.hoshiyomi.filemanager.model.SortOrder
import com.hoshiyomi.filemanager.ui.apkviewer.ApkViewerActivity
import com.hoshiyomi.filemanager.ui.editor.TextEditorActivity
import com.hoshiyomi.filemanager.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File

class FileManagerFragment : Fragment() {

    private var _binding: FragmentFileManagerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FileManagerViewModel by activityViewModels()

    private lateinit var leftAdapter: FileListAdapter
    private lateinit var rightAdapter: FileListAdapter

    private var leftPanelActive = true
    private var dualPanelMode = true
    private var showHiddenFiles = false

    private var leftCurrentPath = File("/")
    private var rightCurrentPath = File("/")
    private var leftHistory = mutableListOf<File>()
    private var leftHistoryIndex = -1
    private var rightHistory = mutableListOf<File>()
    private var rightHistoryIndex = -1

    private var contextMenuPanelIsLeft = true
    private var contextMenuFileItem: FileItem? = null

    private var searchQuery: String? = null

    companion object {
        private const val ARG_START_PATH = "start_path"

        fun newInstance(startPath: String? = null): FileManagerFragment {
            val fragment = FileManagerFragment()
            startPath?.let {
                fragment.arguments = Bundle().apply {
                    putString(ARG_START_PATH, it)
                }
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupAdapters()
        setupRecyclerViews()
        setupSwipeRefresh()
        setupPanelButtons()
        observeViewModel()
        updateActivePanelHighlight()
        updateUnifiedPathBar()

        val startPath = arguments?.getString(ARG_START_PATH)
        val homeDir = if (!startPath.isNullOrBlank()) File(startPath) else FileUtils.getStorageDirectories(requireContext()).firstOrNull() ?: File("/")
        leftCurrentPath = homeDir
        rightCurrentPath = homeDir
        pushHistory(true, leftCurrentPath)
        pushHistory(false, rightCurrentPath)
        loadFilesForPanel(true)
        loadFilesForPanel(false)
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_file_manager, menu)
                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem?.actionView as? SearchView
                searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        searchQuery = query
                        performSearch(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        if (newText.isNullOrBlank()) {
                            searchQuery = null
                            loadFilesForPanel(leftPanelActive)
                        }
                        return false
                    }
                })
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_select_all -> {
                        getActiveAdapter().selectAll()
                        true
                    }
                    R.id.action_bookmark -> {
                        val currentPath = if (leftPanelActive) leftCurrentPath else rightCurrentPath
                        bookmarkPath(currentPath.absolutePath)
                        true
                    }
                    R.id.action_compress -> {
                        showCompressDialog()
                        true
                    }
                    R.id.action_properties -> {
                        showPropertiesDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupAdapters() {
        leftAdapter = FileListAdapter(
            onFileClick = { fileItem -> onFileClick(fileItem, true) },
            onFileLongClick = { fileItem -> onFileLongClick(fileItem, true) },
            onSelectionChanged = { _ -> updateSelectionCount() }
        )
        rightAdapter = FileListAdapter(
            onFileClick = { fileItem -> onFileClick(fileItem, false) },
            onFileLongClick = { fileItem -> onFileLongClick(fileItem, false) },
            onSelectionChanged = { _ -> updateSelectionCount() }
        )
    }

    private fun setupRecyclerViews() {
        binding.rvLeftFiles.layoutManager = LinearLayoutManager(context)
        binding.rvLeftFiles.adapter = leftAdapter

        binding.rvRightFiles.layoutManager = LinearLayoutManager(context)
        binding.rvRightFiles.adapter = rightAdapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeLeftRefresh.setOnRefreshListener {
            loadFilesForPanel(true)
            binding.swipeLeftRefresh.isRefreshing = false
        }
        binding.swipeRightRefresh.setOnRefreshListener {
            loadFilesForPanel(false)
            binding.swipeRightRefresh.isRefreshing = false
        }
    }

    private fun setupUnifiedPathBar() {
        binding.btnUnifiedUp.setOnClickListener { navigateUp(leftPanelActive) }
        binding.btnPathCreateNew.setOnClickListener { showCreateNewDialog() }
        binding.btnPathSort.setOnClickListener { showSortDialog() }
        binding.btnPathFilter.setOnClickListener { showFilterDialog() }
    }

    private fun showCreateNewDialog() {
        val options = arrayOf(
            getString(R.string.action_new_folder),
            getString(R.string.action_new_file)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.path_bar_create_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showNewFolderDialog()
                    1 -> showNewFileDialog()
                }
            }
            .show()
    }

    private fun showFilterDialog() {
        val options = arrayOf(
            getString(R.string.path_bar_filter_all),
            getString(R.string.path_bar_filter_hidden),
            getString(R.string.path_bar_filter_apk),
            getString(R.string.path_bar_filter_image),
            getString(R.string.path_bar_filter_video),
            getString(R.string.path_bar_filter_audio),
            getString(R.string.path_bar_filter_document),
            getString(R.string.path_bar_filter_archive)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.path_bar_filter_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        showHiddenFiles = false
                        viewModel.setShowHidden(false)
                        viewModel.clearFileFilter()
                        loadFilesForPanel(true)
                        loadFilesForPanel(false)
                    }
                    1 -> {
                        showHiddenFiles = !showHiddenFiles
                        viewModel.setShowHidden(showHiddenFiles)
                        loadFilesForPanel(true)
                        loadFilesForPanel(false)
                    }
                    else -> {
                        val filterType = when (which) {
                            2 -> "apk"
                            3 -> "image"
                            4 -> "video"
                            5 -> "audio"
                            6 -> "document"
                            7 -> "archive"
                            else -> null
                        }
                        filterType?.let { viewModel.setFileFilter(it) }
                        loadFilesForPanel(true)
                        loadFilesForPanel(false)
                    }
                }
            }
            .show()
    }

    private fun updateActivePanelHighlight() {
        if (leftPanelActive) {
            binding.leftPanelIndicator.visibility = View.VISIBLE
            binding.rightPanelIndicator.visibility = View.INVISIBLE
            binding.leftPanel.setBackgroundColor(resources.getColor(R.color.panel_active_bg, null))
            binding.rightPanel.setBackgroundColor(resources.getColor(R.color.md_theme_background, null))
        } else {
            binding.leftPanelIndicator.visibility = View.INVISIBLE
            binding.rightPanelIndicator.visibility = View.VISIBLE
            binding.leftPanel.setBackgroundColor(resources.getColor(R.color.md_theme_background, null))
            binding.rightPanel.setBackgroundColor(resources.getColor(R.color.panel_active_bg, null))
        }
    }

    private fun setupPanelButtons() {
        setupUnifiedPathBar()

        // Intercept touch on RecyclerViews to switch active panel on first touch (ACTION_DOWN)
        val panelTouchListener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val isLeft = (rv.id == R.id.rvLeftFiles)
                    if (isLeft != leftPanelActive) {
                        leftPanelActive = isLeft
                        updateSelectionCount()
                        updateUnifiedPathBar()
                        updateActivePanelHighlight()
                    }
                }
                return false // do not consume the event
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        }
        binding.rvLeftFiles.addOnItemTouchListener(panelTouchListener)
        binding.rvRightFiles.addOnItemTouchListener(panelTouchListener)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.leftPanelState.collect { state ->
                        leftAdapter.submitList(state.files)
                        updateEmptyState(true, state.files.isEmpty())
                        if (state.isLoading) {
                            binding.swipeLeftRefresh.isRefreshing = true
                        } else {
                            binding.swipeLeftRefresh.isRefreshing = false
                        }
                        if (state.error != null) {
                            Toast.makeText(context, state.error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.rightPanelState.collect { state ->
                        rightAdapter.submitList(state.files)
                        updateEmptyState(false, state.files.isEmpty())
                        if (state.isLoading) {
                            binding.swipeRightRefresh.isRefreshing = true
                        } else {
                            binding.swipeRightRefresh.isRefreshing = false
                        }
                        if (state.error != null) {
                            Toast.makeText(context, state.error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.clipboard.collect { }
                }
            }
        }
    }

    private fun loadFilesForPanel(isLeft: Boolean) {
        val path = if (isLeft) leftCurrentPath else rightCurrentPath
        updatePathDisplay(isLeft, path)
        viewModel.loadFiles(path, isLeft)
    }

    private fun updatePathDisplay(isLeft: Boolean, path: File) {
        // Update unified path bar if the changed panel is active
        if (isLeft == leftPanelActive) {
            val pathText = if (path.absolutePath == "/") "/" else path.absolutePath
            binding.tvUnifiedPath.text = pathText
        }
    }

    private fun updateUnifiedPathBar() {
        val path = if (leftPanelActive) leftCurrentPath else rightCurrentPath
        val pathText = if (path.absolutePath == "/") "/" else path.absolutePath
        binding.tvUnifiedPath.text = pathText
    }

    private fun updateEmptyState(isLeft: Boolean, isEmpty: Boolean) {
        if (isLeft) {
            binding.tvLeftEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvLeftFiles.visibility = if (isEmpty) View.GONE else View.VISIBLE
        } else {
            binding.tvRightEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvRightFiles.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun onFileClick(fileItem: FileItem, isLeft: Boolean) {
        if (getAdapter(isLeft).isMultiSelectMode()) {
            getAdapter(isLeft).toggleSelection(fileItem)
            return
        }

        // Switch active panel on click
        if (isLeft != leftPanelActive) {
            leftPanelActive = isLeft
            updateUnifiedPathBar()
            updateActivePanelHighlight()
        }

        if (fileItem.isDirectory) {
            navigateTo(fileItem.file, isLeft)
        } else {
            handleFileOpen(fileItem)
        }
    }

    private fun onFileLongClick(fileItem: FileItem, isLeft: Boolean) {
        contextMenuPanelIsLeft = isLeft
        contextMenuFileItem = fileItem

        // Switch active panel on long click
        if (isLeft != leftPanelActive) {
            leftPanelActive = isLeft
            updateUnifiedPathBar()
            updateActivePanelHighlight()
        }

        val adapter = getAdapter(isLeft)
        if (!adapter.isMultiSelectMode()) {
            showContextMenu(fileItem, isLeft)
        } else {
            adapter.toggleSelection(fileItem)
        }
    }

    private fun navigateTo(file: File, isLeft: Boolean) {
        if (isLeft) {
            leftCurrentPath = file
            pushHistory(true, file)
        } else {
            rightCurrentPath = file
            pushHistory(false, file)
        }
        searchQuery = null
        getAdapter(isLeft).clearSelection()
        getAdapter(isLeft).setMultiSelectMode(false)
        loadFilesForPanel(isLeft)
        viewModel.addToHistory(file.absolutePath)
    }

    private fun navigateUp(isLeft: Boolean) {
        val currentPath = if (isLeft) leftCurrentPath else rightCurrentPath
        val parent = currentPath.parentFile
        if (parent != null && parent.canRead()) {
            navigateTo(parent, isLeft)
        }
    }

    private fun navigateBack(isLeft: Boolean) {
        val history = if (isLeft) leftHistory else rightHistory
        val historyIndex = if (isLeft) leftHistoryIndex else rightHistoryIndex
        if (historyIndex > 0) {
            val newIndex = historyIndex - 1
            val path = history[newIndex]
            if (isLeft) {
                leftHistoryIndex = newIndex
                leftCurrentPath = path
            } else {
                rightHistoryIndex = newIndex
                rightCurrentPath = path
            }
            loadFilesForPanel(isLeft)
        }
    }

    private fun pushHistory(isLeft: Boolean, path: File) {
        if (isLeft) {
            leftHistory.add(path)
            leftHistoryIndex = leftHistory.size - 1
        } else {
            rightHistory.add(path)
            rightHistoryIndex = rightHistory.size - 1
        }
    }

    private fun handleFileOpen(fileItem: FileItem) {
        when {
            fileItem.isApk -> {
                val intent = Intent(requireContext(), ApkViewerActivity::class.java).apply {
                    putExtra(ApkViewerActivity.EXTRA_FILE_PATH, fileItem.file.absolutePath)
                }
                startActivity(intent)
            }
            fileItem.isText || fileItem.isCode -> {
                val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                    putExtra(TextEditorActivity.EXTRA_FILE_PATH, fileItem.file.absolutePath)
                }
                startActivity(intent)
            }
            fileItem.isArchive -> {
                val intent = Intent(requireContext(), ApkViewerActivity::class.java).apply {
                    putExtra(ApkViewerActivity.EXTRA_FILE_PATH, fileItem.file.absolutePath)
                }
                startActivity(intent)
            }
            else -> {
                FileUtils.openFile(requireContext(), fileItem.file)
            }
        }
    }

    private fun showContextMenu(fileItem: FileItem, isLeft: Boolean) {
        contextMenuPanelIsLeft = isLeft
        contextMenuFileItem = fileItem
        val otherPanelLabel = if (isLeft) getString(R.string.context_to_right) else getString(R.string.context_to_left)

        val menuItems = mutableListOf<String>()
        menuItems.add(getString(R.string.action_open))
        if (fileItem.isText || fileItem.isCode) {
            menuItems.add(getString(R.string.action_edit))
        }
        menuItems.add(getString(R.string.action_copy))
        menuItems.add(getString(R.string.action_cut))
        menuItems.add(getString(R.string.action_move_to_panel, otherPanelLabel))
        if (viewModel.hasClipboard()) {
            menuItems.add(getString(R.string.action_paste_here))
        }
        menuItems.add(getString(R.string.action_rename))
        menuItems.add(getString(R.string.action_delete))
        menuItems.add(getString(R.string.action_bookmark))
        menuItems.add(getString(R.string.action_info))
        if (fileItem.isArchive) {
            menuItems.add(getString(R.string.action_extract))
        }

        val subtitle = fileItem.file.absolutePath
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(fileItem.name)
            .setMessage(subtitle)
            .setItems(menuItems.toTypedArray()) { _, which ->
                when {
                    menuItems[which] == getString(R.string.action_open) -> {
                        if (fileItem.isDirectory) {
                            navigateTo(fileItem.file, isLeft)
                        } else {
                            handleFileOpen(fileItem)
                        }
                    }
                    menuItems[which] == getString(R.string.action_edit) -> {
                        val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                            putExtra(TextEditorActivity.EXTRA_FILE_PATH, fileItem.file.absolutePath)
                        }
                        startActivity(intent)
                    }
                    menuItems[which] == getString(R.string.action_copy) -> {
                        viewModel.copyFilesToClipboard(listOf(fileItem.file))
                        Toast.makeText(context, getString(R.string.copied, 1), Toast.LENGTH_SHORT).show()
                    }
                    menuItems[which] == getString(R.string.action_cut) -> {
                        viewModel.cutFilesToClipboard(listOf(fileItem.file))
                        Toast.makeText(context, getString(R.string.cut, 1), Toast.LENGTH_SHORT).show()
                    }
                    menuItems[which] == getString(R.string.action_move_to_panel, otherPanelLabel) -> {
                        moveFileToOtherPanel(fileItem)
                    }
                    menuItems[which] == getString(R.string.action_paste_here) -> {
                        val destDir = if (isLeft) leftCurrentPath else rightCurrentPath
                        viewModel.pasteFiles(destDir, isLeft) { result ->
                            result.onSuccess {
                                Toast.makeText(context, getString(R.string.pasted, 1), Toast.LENGTH_SHORT).show()
                            }.onFailure { e ->
                                Toast.makeText(context, e.message ?: "Failed to paste", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    menuItems[which] == getString(R.string.action_rename) -> {
                        showRenameDialog(fileItem)
                    }
                    menuItems[which] == getString(R.string.action_delete) -> {
                        showDeleteDialog(listOf(fileItem))
                    }
                    menuItems[which] == getString(R.string.action_bookmark) -> {
                        bookmarkPath(fileItem.file.absolutePath)
                    }
                    menuItems[which] == getString(R.string.action_info) -> {
                        showFileProperties(fileItem)
                    }
                    menuItems[which] == getString(R.string.action_extract) -> {
                        showExtractDialog(fileItem)
                    }
                }
            }
            .show()
    }

    private fun moveFileToOtherPanel(fileItem: FileItem) {
        val isLeft = contextMenuPanelIsLeft
        val destDir = if (isLeft) rightCurrentPath else leftCurrentPath
        val destPanel = !isLeft
        val files = listOf(fileItem.file)
        viewModel.pasteFiles(destDir, destPanel) { result ->
            result.onSuccess {
                Toast.makeText(context, getString(R.string.moved_to_panel), Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, e.message ?: "Failed to move", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showNewFolderDialog() {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_new_folder_title)
        dialogBinding.editTextInput.hint = getString(R.string.dialog_new_folder_name)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_new_folder_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = dialogBinding.editTextInput.text.toString().trim()
                if (name.isNotBlank()) {
                    val parentDir = if (leftPanelActive) leftCurrentPath else rightCurrentPath
                    viewModel.createDirectory(parentDir, name, leftPanelActive) { result ->
                        handleResult(result, R.string.created, name)
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showNewFileDialog() {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_new_file_title)
        dialogBinding.editTextInput.hint = getString(R.string.dialog_file_name)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_new_file_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = dialogBinding.editTextInput.text.toString().trim()
                if (name.isNotBlank()) {
                    val parentDir = if (leftPanelActive) leftCurrentPath else rightCurrentPath
                    viewModel.createFile(parentDir, name, leftPanelActive) { result ->
                        handleResult(result, R.string.created, name)
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showRenameDialog(fileItem: FileItem) {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_rename_title)
        dialogBinding.editTextInput.setText(fileItem.name)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_rename_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val newName = dialogBinding.editTextInput.text.toString().trim()
                if (newName.isNotBlank() && newName != fileItem.name) {
                    val isLeft = contextMenuPanelIsLeft
                    val parentDir = if (isLeft) leftCurrentPath else rightCurrentPath
                    viewModel.renameFile(fileItem.file, newName, parentDir, isLeft) { result ->
                        result.onSuccess {
                            Toast.makeText(context, getString(R.string.renamed, newName), Toast.LENGTH_SHORT).show()
                        }.onFailure { e ->
                            Toast.makeText(context, e.message ?: getString(R.string.error_failed_to_rename), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showDeleteDialog(fileItems: List<FileItem>) {
        val names = if (fileItems.size == 1) fileItems[0].name else "${fileItems.size} items"
        val dialogBinding = DialogConfirmBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_delete_title)
        dialogBinding.tvDialogMessage.text = getString(R.string.dialog_delete_message, names)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val files = fileItems.map { it.file }
                val isLeft = contextMenuPanelIsLeft
                val parentDir = if (isLeft) leftCurrentPath else rightCurrentPath
                viewModel.deleteFiles(files, parentDir, isLeft) { result ->
                    result.onSuccess {
                        Toast.makeText(context, getString(R.string.deleted, names), Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(context, e.message ?: getString(R.string.error_failed_to_delete), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showSortDialog() {
        val dialogBinding = DialogSortBinding.inflate(layoutInflater)
        val currentMode = viewModel.sortMode.value
        val currentOrder = viewModel.sortOrder.value

        when (currentMode) {
            SortMode.NAME -> dialogBinding.rbSortName.isChecked = true
            SortMode.SIZE -> dialogBinding.rbSortSize.isChecked = true
            SortMode.DATE -> dialogBinding.rbSortDate.isChecked = true
            SortMode.TYPE -> dialogBinding.rbSortType.isChecked = true
        }
        dialogBinding.cbAscending.isChecked = currentOrder == SortOrder.ASCENDING

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_sort_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val mode = when {
                    dialogBinding.rbSortName.isChecked -> SortMode.NAME
                    dialogBinding.rbSortSize.isChecked -> SortMode.SIZE
                    dialogBinding.rbSortDate.isChecked -> SortMode.DATE
                    dialogBinding.rbSortType.isChecked -> SortMode.TYPE
                    else -> SortMode.NAME
                }
                val order = if (dialogBinding.cbAscending.isChecked) SortOrder.ASCENDING else SortOrder.DESCENDING
                viewModel.setSortMode(mode)
                viewModel.setSortOrder(order)
                loadFilesForPanel(true)
                loadFilesForPanel(false)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showExtractDialog(fileItem: FileItem) {
        val destDir = if (leftPanelActive) rightCurrentPath else leftCurrentPath
        val dialogBinding = DialogConfirmBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_extract_title)
        dialogBinding.tvDialogMessage.text = getString(R.string.dialog_extract_message) + "\n${destDir.absolutePath}"

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val isLeft = contextMenuPanelIsLeft
                viewModel.extractArchive(fileItem.file, destDir, !isLeft) { result ->
                    result.onSuccess {
                        Toast.makeText(context, getString(R.string.extracted, destDir.name), Toast.LENGTH_SHORT).show()
                        loadFilesForPanel(!isLeft)
                    }.onFailure { e ->
                        Toast.makeText(context, e.message ?: getString(R.string.error_failed_to_extract), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showCompressDialog() {
        val adapter = getActiveAdapter()
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) {
            Toast.makeText(context, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_compress_title)
        dialogBinding.editTextInput.hint = "archive.zip"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_compress_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = dialogBinding.editTextInput.text.toString().trim()
                if (name.isNotBlank()) {
                    val parentDir = if (leftPanelActive) leftCurrentPath else rightCurrentPath
                    val archiveFile = File(parentDir, if (name.endsWith(".zip", true)) name else "$name.zip")
                    val files = selected.map { it.file }
                    viewModel.compressFiles(files, archiveFile, leftPanelActive) { result ->
                        result.onSuccess {
                            Toast.makeText(context, getString(R.string.compressed, archiveFile.name), Toast.LENGTH_SHORT).show()
                        }.onFailure { e ->
                            Toast.makeText(context, e.message ?: getString(R.string.error_failed_to_compress), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showPropertiesDialog() {
        val fileItem = contextMenuFileItem
        if (fileItem == null) {
            val currentDir = if (leftPanelActive) leftCurrentPath else rightCurrentPath
            val tempItem = FileItem(currentDir)
            showFileProperties(tempItem)
        } else {
            showFileProperties(fileItem)
        }
    }

    private fun showFileProperties(fileItem: FileItem) {
        val sb = StringBuilder()
        sb.append("Name: ${fileItem.name}\n")
        sb.append("Path: ${fileItem.file.absolutePath}\n")
        sb.append("Type: ${if (fileItem.isDirectory) "Directory" else fileItem.extension.uppercase().ifEmpty { "Unknown" }}\n")
        sb.append("Size: ${fileItem.readableSize}\n")
        sb.append("Modified: ${fileItem.readableDate}\n")
        sb.append("Readable: ${if (fileItem.file.canRead()) "Yes" else "No"}\n")
        sb.append("Writable: ${if (fileItem.file.canWrite()) "Yes" else "No"}\n")
        sb.append("Hidden: ${if (fileItem.isHidden) "Yes" else "No"}")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_properties_title)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun doShowBookmarksDialog() {
        val bookmarks = viewModel.getBookmarks()
        if (bookmarks.isEmpty()) {
            Toast.makeText(context, getString(R.string.no_bookmarks), Toast.LENGTH_SHORT).show()
            return
        }
        val paths = bookmarks.map { it.absolutePath }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bookmarks)
            .setItems(paths) { _, which ->
                val dir = bookmarks[which]
                navigateTo(dir, leftPanelActive)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun bookmarkPath(path: String) {
        if (viewModel.isBookmarked(path)) {
            viewModel.removeBookmark(path)
            Toast.makeText(context, getString(R.string.bookmark_removed), Toast.LENGTH_SHORT).show()
        } else {
            viewModel.saveBookmark(path)
            Toast.makeText(context, getString(R.string.bookmark_added), Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteFiles() {
        val clip = viewModel.clipboard.value
        if (clip == null) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val destDir = if (leftPanelActive) leftCurrentPath else rightCurrentPath
        viewModel.pasteFiles(destDir, leftPanelActive) { result ->
            result.onSuccess {
                Toast.makeText(context, getString(R.string.pasted, clip.files.size), Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, e.message ?: "Failed to paste", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSearch(query: String?) {
        if (query.isNullOrBlank()) return
        val currentPath = if (leftPanelActive) leftCurrentPath else rightCurrentPath
        viewModel.searchFiles(currentPath, query, leftPanelActive)
    }

    private fun updateSelectionCount() {
        val adapter = getActiveAdapter()
        val selected = adapter.getSelectedItems()
        if (selected.isNotEmpty()) {
            val activity = requireActivity()
            if (activity is AppCompatActivity) {
                activity.supportActionBar?.title = getString(R.string.selected, selected.size)
            }
        } else {
            val activity = requireActivity()
            if (activity is AppCompatActivity) {
                activity.supportActionBar?.title = getString(R.string.app_name)
            }
        }
    }

    private fun getActiveAdapter(): FileListAdapter = if (leftPanelActive) leftAdapter else rightAdapter
    private fun getAdapter(isLeft: Boolean): FileListAdapter = if (isLeft) leftAdapter else rightAdapter

    fun toggleDualPanel() {
        dualPanelMode = !dualPanelMode
        if (dualPanelMode) {
            binding.rightPanel.visibility = View.VISIBLE
            binding.panelDivider.visibility = View.VISIBLE
        } else {
            binding.rightPanel.visibility = View.GONE
            binding.panelDivider.visibility = View.GONE
        }
    }

    fun refreshCurrentPanel() {
        loadFilesForPanel(leftPanelActive)
        loadFilesForPanel(!leftPanelActive)
    }

    fun showBookmarksDialog() {
        doShowBookmarksDialog()
    }

    private fun handleResult(result: Result<*>, successStringRes: Int, name: String) {
        result.onSuccess {
            Toast.makeText(context, getString(successStringRes, name), Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, e.message ?: "Operation failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
