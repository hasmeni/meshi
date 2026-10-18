package com.meshi.code.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleRetryTest {

    @Test
    fun `succeeds on first attempt without retrying`() = runTest {
        var calls = 0
        val result = withBleRetry(maxRetries = 3, baseDelayMs = 1) {
            calls++
            true
        }
        assertTrue(result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries then succeeds`() = runTest {
        var calls = 0
        val result = withBleRetry(maxRetries = 3, baseDelayMs = 1) {
            calls++
            calls == 3 // fail twice, succeed on third call
        }
        assertTrue(result)
        assertEquals(3, calls)
    }

    @Test
    fun `gives up after exhausting all attempts`() = runTest {
        var calls = 0
        val result = withBleRetry(maxRetries = 3, baseDelayMs = 1) {
            calls++
            false
        }
        assertFalse(result)
        // 1 initial attempt + 3 retries
        assertEquals(4, calls)
    }
}
