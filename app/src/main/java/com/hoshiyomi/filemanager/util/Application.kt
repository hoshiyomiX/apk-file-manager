package com.hoshiyomi.filemanager.util

import android.app.Application

class Application : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemeManager.initTheme(this)
        BookmarkManager.init(this)
    }

    companion object {
        lateinit var instance: Application
            private set
    }
}
