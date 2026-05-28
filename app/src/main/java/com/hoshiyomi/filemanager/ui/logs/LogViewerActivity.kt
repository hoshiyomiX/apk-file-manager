package com.hoshiyomi.filemanager.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.ActivityLogViewerBinding
import com.hoshiyomi.filemanager.util.DiagnosticLogger

class LogViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogViewerBinding

    private val TAB_TITLES = arrayOf(
        R.string.log_tab_summary,
        R.string.log_tab_full,
        R.string.log_tab_compact
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.log_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupViewPager()
        setupMenu()
    }

    private fun setupViewPager() {
        val adapter = LogPagerAdapter(this, TAB_TITLES.size)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.attach()

        val errorCount = DiagnosticLogger.getErrorCount()
        val warnCount = DiagnosticLogger.getWarnCount()
        supportActionBar?.subtitle = "${DiagnosticLogger.getEntries().size} entries · $errorCount errors · $warnCount warnings"
    }

    private fun setupMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.menu_log_viewer, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_copy_logs -> {
                        copyFullLog()
                        true
                    }
                    R.id.action_copy_summary -> {
                        copyDiagnosticSummary()
                        true
                    }
                    R.id.action_share_logs -> {
                        shareLogs()
                        true
                    }
                    R.id.action_clear_logs -> {
                        confirmClearLogs()
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun copyFullLog() {
        val logText = DiagnosticLogger.exportAsText()
        copyToClipboard("MT File Manager Diagnostic Log", logText)
    }

    private fun copyDiagnosticSummary() {
        val summary = DiagnosticLogger.buildAppDiagnosticSummary()
        copyToClipboard("MT File Manager Diagnostic Summary", summary)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.log_copied, label), Toast.LENGTH_SHORT).show()
    }

    private fun shareLogs() {
        val logText = DiagnosticLogger.exportAsText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MT File Manager Diagnostic Log")
            putExtra(Intent.EXTRA_TEXT, logText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.log_share)))
    }

    private fun confirmClearLogs() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.log_clear_title)
            .setMessage(R.string.log_clear_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                DiagnosticLogger.clear()
                Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
