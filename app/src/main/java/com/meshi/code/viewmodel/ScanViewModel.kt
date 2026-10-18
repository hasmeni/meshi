package com.meshi.code.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshi.code.MeshiApplication
import com.meshi.code.ble.ConnectionState
import com.meshi.code.ble.MeshiBleManager
import com.meshi.code.ble.ScanDevice
import com.meshi.code.db.MeshiDatabase
import com.meshi.code.repository.MeshRepository
import com.meshi.code.service.MeshiForegroundService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val PREFS_NAME = "meshi_prefs"
private const val KEY_LAST_ADDRESS = "last_ble_address"

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = MeshiBleManager(app.applicationContext)
    private val db = MeshiDatabase.getInstance(app.applicationContext)
    val repository = MeshRepository(
        ble,
        viewModelScope,
        db.chatMessageDao(),
        db.contactNicknameDao(),
        db.gameRecordDao(),
        db.snrSampleDao(),
        isBlocked = { hex -> (app as MeshiApplication).prefs.blockedContacts.contains(hex) },
        isDeleted = { hex -> (app as MeshiApplication).prefs.deletedContacts.contains(hex) },
    )

    // Expose repository in application singleton so the foreground service can observe it
    init {
        (app as MeshiApplication).repository = repository
        // Start/stop foreground service based on connection state
        repository.connectionState.onEach { state ->
            when (state) {
                ConnectionState.CONNECTED -> {
                    val nodeName = repository.selfInfo.value?.nodeName?.ifBlank { "Unknown Node" } ?: "Unknown Node"
                    app.applicationContext.startForegroundService(
                        MeshiForegroundService.startIntent(app.applicationContext, nodeName)
                    )
                }
                ConnectionState.DISCONNECTED -> {
                    app.applicationContext.stopService(
                        MeshiForegroundService.stopIntent(app.applicationContext)
                    )
                }
                else -> {}
            }
        }.launchIn(viewModelScope)
    }

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val scanResults: StateFlow<List<ScanDevice>> = repository.scanResults
    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private var isScanning = false

    init {
        // Auto-reconnect to last connected device
        val savedAddress = prefs.getString(KEY_LAST_ADDRESS, null)
        if (savedAddress != null) {
            ble.connectByAddress(savedAddress)
        }
    }

    fun toggleScan() {
        if (isScanning) {
            repository.stopScan()
        } else {
            repository.startScan()
        }
        isScanning = !isScanning
    }

    fun connect(result: ScanDevice) {
        repository.stopScan()
        isScanning = false
        prefs.edit().putString(KEY_LAST_ADDRESS, result.address).apply()
        repository.connect(result)
    }

    fun clearSavedDevice() {
        prefs.edit().remove(KEY_LAST_ADDRESS).apply()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopScan()
    }
}
