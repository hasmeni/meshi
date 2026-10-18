package com.meshi.code

/**
 * Centralised constants for timeouts, limits, and thresholds used across the app.
 * Keeps magic numbers out of business logic and makes them easy to tune.
 */
object MeshiConfig {

    // ---- BLE / Protocol ----
    /** Timeout for the initial DEVICE_QUERY handshake step (ms). */
    const val HANDSHAKE_DEVICE_QUERY_TIMEOUT_MS = 6_000L
    /** Timeout for APP_START → SELF_INFO during handshake (ms). */
    const val HANDSHAKE_APP_START_TIMEOUT_MS = 6_000L
    /** Timeout for SET_DEVICE_TIME ack during handshake (ms). */
    const val HANDSHAKE_CLOCK_SYNC_TIMEOUT_MS = 3_000L
    /** Timeout waiting for a RESP_SENT frame after sending a message (ms). */
    const val SEND_ACK_TIMEOUT_MS = 2_000L
    /** Delay between sequential BLE writes to avoid overwhelming the radio (ms). */
    const val BLE_WRITE_SPACING_MS = 150L
    /** MTU size requested during BLE connection. */
    const val BLE_MTU_SIZE = 185

    // ---- Retry ----
    /** Maximum number of retry attempts for failed BLE sends. */
    const val SEND_MAX_RETRIES = 3
    /** Initial delay before the first retry (ms). Doubles on each subsequent attempt. */
    const val SEND_RETRY_BASE_DELAY_MS = 500L

    // ---- Messages ----
    /** Maximum character length for a single mesh message. */
    const val MESSAGE_MAX_LENGTH = 160
    /** Maximum number of advert events kept in memory. */
    const val ADVERTS_MAX_SIZE = 50
    /** Time-to-live for advert events before they are considered stale (ms). */
    const val ADVERTS_TTL_MS = 30 * 60 * 1000L  // 30 minutes

    // ---- Typing indicators ----
    /** How long a typing indicator stays visible before auto-clearing (ms). */
    const val TYPING_INDICATOR_TTL_MS = 5_000L
    /** Debounce interval for outgoing typing indicator sends (ms). */
    const val TYPING_SEND_DEBOUNCE_MS = 3_000L

    // ---- SOS ----
    /** Interval between SOS flood adverts (ms). */
    const val SOS_ADVERT_INTERVAL_MS = 30_000L

    // ---- Battery ----
    /** Default battery alert threshold (percent). */
    const val DEFAULT_BATTERY_ALERT_PCT = 20
    /** Interval between battery stat polls in the foreground service (ms). */
    const val BATTERY_POLL_INTERVAL_MS = 5 * 60 * 1000L

    // ---- Database ----
    /** Maximum SNR samples kept per contact. */
    const val SNR_SAMPLES_MAX_PER_CONTACT = 100
    /** Maximum search results returned from message search. */
    const val SEARCH_RESULTS_LIMIT = 200

    // ---- Validation ----
    /** Expected length of a public key prefix in bytes. */
    const val PUB_KEY_PREFIX_LENGTH = 6
    /** Maximum allowed length for a contact name from the mesh. */
    const val CONTACT_NAME_MAX_LENGTH = 32
    /** Maximum allowed length for a channel name. */
    const val CHANNEL_NAME_MAX_LENGTH = 32
}
