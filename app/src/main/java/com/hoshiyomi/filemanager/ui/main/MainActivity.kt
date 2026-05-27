package com.hoshiyomi.filemanager.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.ActivityMainBinding
import com.hoshiyomi.filemanager.ui.apkviewer.ApkViewerActivity
import com.hoshiyomi.filemanager.ui.filemanager.FileManagerFragment
import com.hoshiyomi.filemanager.ui.logs.LogViewerActivity
import com.hoshiyomi.filemanager.ui.settings.SettingsActivity
import com.hoshiyomi.filemanager.util.FileUtils
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNavigation()
        handleIncomingIntent(intent)
        requestStoragePermission()
        setupBackPressed()

        if (savedInstanceState == null) {
            loadFileManagerFragment()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            onNavigationClicked()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_files -> {
                    loadFileManagerFragment()
                    true
                }
                R.id.nav_tools -> {
                    Toast.makeText(this, "Tools coming soon", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.nav_apk_viewer -> {
                    Toast.makeText(this, "Open an APK file to view", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }
    }

    private fun loadFileManagerFragment() {
        val fragment = FileManagerFragment()
        replaceFragment(fragment, "file_manager")
        binding.bottomNavigation.menu.findItem(R.id.nav_files)?.isChecked = true
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val transaction = supportFragmentManager.beginTransaction()
        currentFragment?.let {
            if (it is FileManagerFragment && tag != "file_manager") {
                transaction.remove(it)
            }
        }
        transaction.replace(R.id.fragmentContainer, fragment, tag)
        transaction.addToBackStack(tag)
        transaction.commitAllowingStateLoss()
        currentFragment = fragment
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 1) {
                    supportFragmentManager.popBackStack()
                } else {
                    if (isTaskRoot) {
                        finish()
                    } else {
                        supportFragmentManager.popBackStack()
                    }
                }
            }
        })
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri: Uri? = intent.data
                if (uri != null) {
                    val file = uriToFile(uri)
                    if (file != null && file.exists()) {
                        when {
                            file.name.endsWith(".apk", true) -> {
                                openApkViewer(file)
                            }
                            else -> {
                                val fragment = FileManagerFragment.newInstance(file.parentFile?.absolutePath)
                                replaceFragment(fragment, "file_manager")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            when (uri.scheme) {
                "file" -> File(uri.path ?: return null)
                "content" -> {
                    var filePath: String? = null
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val dataIndex = cursor.getColumnIndexOrThrow("_data")
                        if (cursor.moveToFirst()) {
                            filePath = cursor.getString(dataIndex)
                        }
                    }
                    filePath?.let { File(it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun openApkViewer(file: File) {
        val intent = Intent(this, ApkViewerActivity::class.java).apply {
            putExtra(ApkViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
        }
        startActivity(intent)
    }

    private fun requestStoragePermission() {
        if (FileUtils.isStoragePermissionGranted(this)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_permission_rationale_title)
                    .setMessage(R.string.dialog_permission_rationale_message)
                    .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                        FileUtils.requestManageStoragePermission(this)
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    FileUtils.REQUEST_CODE_STORAGE_PERMISSION
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_bookmarks -> {
                val fragment = currentFragment
                if (fragment is FileManagerFragment) {
                    fragment.showBookmarksDialog()
                }
                true
            }
            R.id.action_logs -> {
                startActivity(Intent(this, LogViewerActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun onNavigationClicked() {
        val fragment = currentFragment
        if (fragment is FileManagerFragment) {
            fragment.toggleDualPanel()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == FileUtils.REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val fragment = currentFragment
                if (fragment is FileManagerFragment) {
                    fragment.refreshCurrentPanel()
                }
            }
        }
    }
}
