package com.hoshiyomi.filemanager.ui.apkviewer

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.core.apk.ApkAnalyzer
import com.hoshiyomi.filemanager.databinding.ActivityApkViewerBinding
import com.hoshiyomi.filemanager.model.ApkInfo
import com.hoshiyomi.filemanager.ui.logs.LogViewerActivity
import com.hoshiyomi.filemanager.util.DiagnosticLogger
import kotlinx.coroutines.launch
import java.io.File

class ApkViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApkViewerBinding
    private var apkInfo: ApkInfo? = null
    private var apkFile: File? = null

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        private val TAB_TITLES = arrayOf(
            R.string.apk_info,
            R.string.apk_manifest,
            R.string.apk_permissions,
            R.string.apk_certificates,
            R.string.apk_files,
            R.string.log_title
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApkViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath == null) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        apkFile = File(filePath)
        if (!apkFile!!.exists()) {
            Toast.makeText(this, R.string.error_file_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.title = apkFile!!.name

        // Start a fresh diagnostic logging session for this APK
        DiagnosticLogger.newSession("Analyzing: ${apkFile!!.name}")

        setupViewPager()
        setupMenu()
        loadApkInfo()
    }

    private fun setupViewPager() {
        val pagerAdapter = ApkViewerPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.attach()
    }

    private fun setupMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.menu_apk_viewer, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_share -> {
                        shareApk()
                        true
                    }
                    R.id.action_copy_logs -> {
                        copyDiagnosticLogs()
                        true
                    }
                    R.id.action_view_logs -> {
                        openLogViewer()
                        true
                    }
                    R.id.action_sign_apk -> {
                        Toast.makeText(this@ApkViewerActivity, "Sign APK - coming soon", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.action_extract_all -> {
                        extractAll()
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun loadApkInfo() {
        lifecycleScope.launch {
            val file = apkFile ?: return@launch
            val result = ApkAnalyzer.analyzeApk(this@ApkViewerActivity, file)
            result.onSuccess { info ->
                apkInfo = info
                supportFragmentManager.fragments.forEach { fragment ->
                    when (fragment) {
                        is ApkInfoFragment -> fragment.updateApkInfo(info)
                        is ManifestFragment -> fragment.updateManifest(info.manifestXml)
                        is PermissionsFragment -> fragment.updatePermissions(info.permissions)
                        is CertificatesFragment -> fragment.updateCertificate(info.certificateInfo)
                        is FilesFragment -> fragment.loadApkFiles(file)
                    }
                }
                binding.viewPager.adapter?.notifyDataSetChanged()
            }.onFailure { e ->
                Toast.makeText(this@ApkViewerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareApk() {
        val file = apkFile ?: return
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share APK"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAll() {
        val file = apkFile ?: return
        val destDir = File(file.parentFile, file.nameWithoutExtension)
        val dialogBinding = com.hoshiyomi.filemanager.databinding.DialogConfirmBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.apk_extract_all)
        dialogBinding.tvDialogMessage.text = "Extract to:\n${destDir.absolutePath}"

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                lifecycleScope.launch {
                    val result = com.hoshiyomi.filemanager.core.archive.ArchiveManager.extractArchive(file, destDir)
                    result.onSuccess {
                        Toast.makeText(this@ApkViewerActivity, getString(R.string.extracted, destDir.name), Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(this@ApkViewerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    inner class ApkViewerPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = TAB_TITLES.size

        override fun createFragment(position: Int): androidx.fragment.app.Fragment {
            return when (position) {
                0 -> ApkInfoFragment()
                1 -> ManifestFragment()
                2 -> PermissionsFragment()
                3 -> CertificatesFragment()
                4 -> FilesFragment()
                5 -> LogsFragment()
                else -> ApkInfoFragment()
            }
        }
    }

    private fun copyDiagnosticLogs() {
        val summary = DiagnosticLogger.buildApkDiagnosticSummary()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("APK Analysis", summary)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.log_copied, "Analysis Summary"), Toast.LENGTH_SHORT).show()
    }

    private fun openLogViewer() {
        startActivity(Intent(this, LogViewerActivity::class.java))
    }
}
