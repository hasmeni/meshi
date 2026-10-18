package com.meshi.code.viewmodel

import com.meshi.code.MeshiConfig
import com.meshi.code.prefs.AppPreferences
import com.meshi.code.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the SOS beacon — a repeating flood advert that signals distress.
 *
 * State is persisted to [AppPreferences] so the beacon resumes after app restart.
 */
class SosManager(
    private val repository: MeshRepository,
    private val prefs: AppPreferences,
    private val scope: CoroutineScope,
    private val isStealthMode: () -> Boolean,
) {
    private val _isSosActive = MutableStateFlow(prefs.sosActive)
    val isSosActive: StateFlow<Boolean> = _isSosActive
    private var sosJob: Job? = null

    init {
        if (prefs.sosActive) startLoop()
    }

    fun toggle() {
        val active = !_isSosActive.value
        _isSosActive.value = active
        prefs.sosActive = active
        if (active) startLoop() else stopLoop()
    }

    private fun startLoop() {
        sosJob?.cancel()
        sosJob = scope.launch {
            while (isActive) {
                if (!isStealthMode()) repository.sendAdvert(flood = true)
                delay(MeshiConfig.SOS_ADVERT_INTERVAL_MS)
            }
        }
    }

    private fun stopLoop() {
        sosJob?.cancel()
        sosJob = null
    }
}
