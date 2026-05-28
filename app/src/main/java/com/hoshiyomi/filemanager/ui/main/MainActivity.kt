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
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.ActivityMainBinding
import com.hoshiyomi.filemanager.ui.apkviewer.ApkViewerActivity
import com.hoshiyomi.filemanager.ui.filemanager.FileManagerFragment
import com.hoshiyomi.filemanager.ui.filemanager.HistoryFragment
import com.hoshiyomi.filemanager.ui.logs.LogViewerActivity
import com.hoshiyomi.filemanager.ui.settings.SettingsActivity
import com.hoshiyomi.filemanager.util.FileUtils
import com.hoshiyomi.filemanager.util.ThemeManager
import com.hoshiyomi.filemanager.util.ThemeMode
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawer()
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
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setupDrawer() {
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.drawer_open_drawer,
            R.string.drawer_close_drawer
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            handleDrawerItemClick(menuItem)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        setupThemeSelector()
    }

    private fun setupThemeSelector() {
        val headerView = binding.navigationView.getHeaderView(0)
        val radioGroup = headerView.findViewById<RadioGroup>(R.id.radioGroupTheme)
        val currentMode = ThemeManager.getThemeMode(this)

        when (currentMode) {
            ThemeMode.SYSTEM -> radioGroup.check(R.id.radioThemeSystem)
            ThemeMode.LIGHT -> radioGroup.check(R.id.radioThemeLight)
            ThemeMode.DARK -> radioGroup.check(R.id.radioThemeDark)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioThemeLight -> ThemeMode.LIGHT
                R.id.radioThemeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            ThemeManager.applyTheme(this, mode)
        }
    }

    private fun handleDrawerItemClick(item: MenuItem) {
        when (item.itemId) {
            R.id.drawer_home -> {
                navigateDrawerTo(null)
            }
            R.id.drawer_downloads -> {
                navigateDrawerTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            }
            R.id.drawer_images -> {
                navigateDrawerTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
            }
            R.id.drawer_videos -> {
                navigateDrawerTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
            }
            R.id.drawer_music -> {
                navigateDrawerTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
            }
            R.id.drawer_documents -> {
                navigateDrawerTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            }
            R.id.drawer_bookmarks -> {
                val fragment = currentFragment
                if (fragment is FileManagerFragment) {
                    fragment.showBookmarksDialog()
                }
            }
            R.id.drawer_apk_tools -> {
                Toast.makeText(this, "Open an APK file to use APK Tools", Toast.LENGTH_SHORT).show()
            }
            R.id.drawer_logs -> {
                startActivity(Intent(this, LogViewerActivity::class.java))
            }
            R.id.drawer_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    private fun navigateDrawerTo(targetDir: File?) {
        val startPath = targetDir?.absolutePath
        val fragment = FileManagerFragment.newInstance(startPath)
        replaceFragment(fragment, "file_manager")
        binding.bottomNavigation.menu.findItem(R.id.nav_files)?.isChecked = true
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_files -> {
                    loadFileManagerFragment()
                    true
                }
                R.id.nav_history -> {
                    loadHistoryFragment()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
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

    private fun loadHistoryFragment() {
        val historySheet = HistoryFragment.newInstance()
        historySheet.show(supportFragmentManager, HistoryFragment.TAG)
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val transaction = supportFragmentManager.beginTransaction()
        currentFragment?.let {
            if (tag != "file_manager" && tag != "history") {
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
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }
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
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        return when (item.itemId) {
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
