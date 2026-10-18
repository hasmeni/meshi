package com.meshi.code.protocol

import com.meshi.code.MeshiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test
    fun `sanitizeName strips control characters`() {
        val result = InputValidator.sanitizeName("Al\u0000ice\u0007")
        assertEquals("Alice", result)
    }

    @Test
    fun `sanitizeName keeps allowed punctuation and trims`() {
        assertEquals("node-1_a.b", InputValidator.sanitizeName("  node-1_a.b  "))
    }

    @Test
    fun `sanitizeName enforces max length`() {
        val long = "x".repeat(100)
        assertEquals(MeshiConfig.CONTACT_NAME_MAX_LENGTH, InputValidator.sanitizeName(long).length)
    }

    @Test
    fun `sanitizeMessageText preserves newlines but strips other control chars`() {
        val result = InputValidator.sanitizeMessageText("line1\nline2\tend\u0001")
        // tab and \u0001 become spaces; newline preserved; trailing trimmed
        assertTrue(result.contains("\n"))
        assertFalse(result.contains("\t"))
        assertFalse(result.contains("\u0001"))
    }

    @Test
    fun `isValidPubKeyPrefix rejects short prefixes`() {
        assertFalse(InputValidator.isValidPubKeyPrefix(ByteArray(MeshiConfig.PUB_KEY_PREFIX_LENGTH - 1) { 1 }))
    }

    @Test
    fun `isValidPubKeyPrefix rejects all-zero prefixes`() {
        assertFalse(InputValidator.isValidPubKeyPrefix(ByteArray(MeshiConfig.PUB_KEY_PREFIX_LENGTH)))
    }

    @Test
    fun `isValidPubKeyPrefix accepts a valid prefix`() {
        val valid = ByteArray(MeshiConfig.PUB_KEY_PREFIX_LENGTH) { (it + 1).toByte() }
        assertTrue(InputValidator.isValidPubKeyPrefix(valid))
    }

    @Test
    fun `isValidGps bounds are enforced`() {
        assertTrue(InputValidator.isValidGps(0.0, 0.0))
        assertTrue(InputValidator.isValidGps(-90.0, 180.0))
        assertFalse(InputValidator.isValidGps(90.1, 0.0))
        assertFalse(InputValidator.isValidGps(0.0, -180.1))
    }

    @Test
    fun `isValidChannelIdx accepts 0 through 7 only`() {
        for (i in 0..7) assertTrue(InputValidator.isValidChannelIdx(i))
        assertFalse(InputValidator.isValidChannelIdx(-1))
        assertFalse(InputValidator.isValidChannelIdx(8))
    }
}
