package com.meshi.code.ble

import com.meshi.code.MeshiConfig
import kotlinx.coroutines.delay

/**
 * Retries a BLE send operation with exponential backoff.
 *
 * @param maxRetries Maximum number of retry attempts (default from [MeshiConfig]).
 * @param baseDelayMs Initial delay before the first retry; doubles each attempt.
 * @param operation The suspend function that returns true on success, false on failure.
 * @return true if the operation eventually succeeded, false if all attempts failed.
 */
suspend fun withBleRetry(
    maxRetries: Int = MeshiConfig.SEND_MAX_RETRIES,
    baseDelayMs: Long = MeshiConfig.SEND_RETRY_BASE_DELAY_MS,
    operation: suspend () -> Boolean,
): Boolean {
    // First attempt (not a retry)
    if (operation()) return true

    // Retry with exponential backoff
    var delayMs = baseDelayMs
    repeat(maxRetries) {
        delay(delayMs)
        if (operation()) return true
        delayMs *= 2
    }
    return false
}
