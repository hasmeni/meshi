package com.meshi.code.protocol

import com.meshi.code.MeshiConfig

/**
 * Sanitises and validates data received from the mesh network.
 *
 * All text arriving over BLE is untrusted — it could contain control characters,
 * excessively long strings, or other payloads that would break the UI or database.
 */
object InputValidator {

    /**
     * Sanitise a contact or sender name received from the mesh.
     * Strips control characters, trims whitespace, and enforces a max length.
     */
    fun sanitizeName(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it.isWhitespace() || it in "-_." }
            .trim()
            .take(MeshiConfig.CONTACT_NAME_MAX_LENGTH)

    /**
     * Sanitise a channel name received from the mesh.
     */
    fun sanitizeChannelName(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it.isWhitespace() || it in "-_." }
            .trim()
            .take(MeshiConfig.CHANNEL_NAME_MAX_LENGTH)

    /**
     * Sanitise incoming message text. Strips control characters (except newline)
     * but does not truncate — messages are already bounded by BLE MTU.
     */
    fun sanitizeMessageText(raw: String): String =
        raw.map { ch ->
            when {
                ch == '\n' || ch == '\r' -> ch
                ch.isISOControl() -> ' '
                else -> ch
            }
        }.joinToString("")
            .trim()

    /**
     * Validate that a public key prefix has the expected length.
     */
    fun isValidPubKeyPrefix(prefix: ByteArray): Boolean =
        prefix.size >= MeshiConfig.PUB_KEY_PREFIX_LENGTH &&
            prefix.any { it != 0.toByte() }  // not all zeros

    /**
     * Validate GPS coordinates are within sane bounds.
     */
    fun isValidGps(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0

    /**
     * Validate a channel index is within the allowed range (0-7).
     */
    fun isValidChannelIdx(idx: Int): Boolean = idx in 0..7
}
