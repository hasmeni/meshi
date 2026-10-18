package com.meshi.code

import android.app.Application
import com.meshi.code.crash.CrashLogManager
import com.meshi.code.prefs.AppPreferences
import com.meshi.code.repository.MeshRepository

class MeshiApplication : Application() {
    /** Set by ScanViewModel after repository is created; read by MeshiForegroundService. */
    var repository: MeshRepository? = null

    lateinit var prefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        CrashLogManager.install(this)
    }
}
