package com.meshi.code.protocol

import java.util.UUID

/**
 * MeshCore BLE transport UUIDs — Nordic UART Service (NUS).
 * NUS naming convention (from the node's perspective):
 *   RX = node receives from app (app writes here)
 *   TX = node transmits to app (app subscribes to notifications here)
 */
object MeshCoreBle {
    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    /** App writes commands here (NUS RX characteristic). */
    val RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    /** App subscribes to notifications here (NUS TX characteristic). */
    val TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    /** Client Characteristic Configuration Descriptor */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/** Command bytes sent from app → node. */
object Cmd {
    const val APP_START: Byte          = 0x01
    const val SEND_TXT_MSG: Byte       = 0x02
    const val SEND_CHANNEL_MSG: Byte   = 0x03
    const val GET_CONTACTS: Byte       = 0x04
    const val GET_DEVICE_TIME: Byte    = 0x05
    const val SET_DEVICE_TIME: Byte    = 0x06
    const val SEND_SELF_ADVERT: Byte   = 0x07
    const val SYNC_NEXT_MESSAGE: Byte  = 0x0A.toByte()
    const val SEND_LOGIN: Byte         = 0x1A
    const val DEVICE_QUERY: Byte       = 0x16
    const val GET_CHANNEL: Byte        = 0x1F
    const val SET_CHANNEL: Byte        = 0x20.toByte()
    const val GET_BATT_STORAGE: Byte   = 0x14
    const val GET_STATS: Byte          = 0x38.toByte()
    const val SET_ADVERT_LATLON: Byte  = 0x0E
}

/** Response/push bytes received from node → app. */
object Resp {
    const val OK: Int                  = 0x00
    const val ERROR: Int               = 0x01
    const val CONTACTS_START: Int      = 0x02
    const val CONTACT: Int             = 0x03
    const val END_OF_CONTACTS: Int     = 0x04
    const val SELF_INFO: Int           = 0x05
    const val SENT: Int                = 0x06
    const val DIRECT_MSG_V1: Int       = 0x07
    const val CHANNEL_MSG_V1: Int      = 0x08
    const val NO_MORE_MESSAGES: Int    = 0x0A
    const val BATT_AND_STORAGE: Int    = 0x0C
    const val DEVICE_INFO: Int         = 0x0D
    const val DIRECT_MSG_V3: Int       = 0x10
    const val CHANNEL_MSG_V3: Int      = 0x11
    const val CHANNEL_INFO: Int        = 0x12
    // Async push events (>= 0x80)
    const val PUSH_ADVERT: Int         = 0x80
    const val PUSH_PATH_UPDATED: Int   = 0x81
    const val PUSH_ACK_CONFIRMED: Int  = 0x82
    const val PUSH_MSG_WAITING: Int    = 0x83
    const val PUSH_LOGIN_OK: Int       = 0x85
    const val PUSH_LOGIN_FAIL: Int     = 0x86
    const val PUSH_NEW_ADVERT: Int     = 0x8A
    const val RADIO_STATS: Int         = 0x38
}

/**
 * Public channel (index 0) shared secret — 16-byte AES-128 key.
 * Source: MeshCore firmware / meshcore-open reference.
 */
val PUBLIC_CHANNEL_SECRET = byteArrayOf(
    0x8b.toByte(), 0x33.toByte(), 0x87.toByte(), 0xe9.toByte(),
    0xc5.toByte(), 0xcd.toByte(), 0xea.toByte(), 0x6a.toByte(),
    0xc9.toByte(), 0xe5.toByte(), 0xed.toByte(), 0xba.toByte(),
    0xa1.toByte(), 0x15.toByte(), 0xcd.toByte(), 0x72.toByte(),
)

// ---- Decoded frame model ----

data class MeshContact(
    val pubKey: ByteArray,
    val pubKeyPrefix: ByteArray,   // first 6 bytes of pubKey
    val name: String,
    val lastAdvertTs: Long,
    val lat: Double,
    val lon: Double,
) {
    val displayId: String get() = pubKeyPrefix.toHexString()
}

sealed class NodeFrame {
    val isAsyncPush: Boolean get() = this is PushAdvert || this is PushNewAdvert ||
        this is PushAckConfirmed || this is PushMsgWaiting || this is PushPathUpdated

    // ---- Sync responses ----
    object Ok : NodeFrame()
    data class Err(val code: Int) : NodeFrame()
    data class DeviceInfo(val fwVerCode: Int, val model: String, val fwVersionStr: String) : NodeFrame()
    data class SelfInfo(
        val pubKey: ByteArray,
        val nodeName: String,
        val nodeType: Int,
        val radioFreqHz: Long,
        val radioSf: Int,
        val radioBwHz: Long,
    ) : NodeFrame() {
        val pubKeyPrefix: ByteArray get() = pubKey.take(6).toByteArray()
        val displayId: String get() = pubKeyPrefix.toHexString()
    }
    data class Sent(val routeFlag: Int, val ackTag: Long, val timeoutMs: Long) : NodeFrame()
    object NoMoreMessages : NodeFrame()
    object ContactsStart : NodeFrame()
    data class ContactRecord(val contact: MeshContact) : NodeFrame()
    object EndOfContacts : NodeFrame()
    data class DirectMsgV3(
        val senderPubKeyPrefix: ByteArray,
        val text: String,
        val timestampMs: Long,
        val snrDb: Float,
        val pathLen: Int,
    ) : NodeFrame()
    data class ChannelMsgV3(
        val channelIdx: Int,
        val senderName: String,
        val text: String,
        val timestampMs: Long,
        val snrDb: Float,
    ) : NodeFrame()
    data class ChannelInfo(
        val channelIdx: Int,
        val name: String,
        val secret: ByteArray = ByteArray(16),
    ) : NodeFrame()
    // ---- Async push ----
    data class PushAdvert(
        val senderPubKeyPrefix: ByteArray,
        val name: String,
        val lat: Double,
        val lon: Double,
        val snrDb: Float,
    ) : NodeFrame()
    data class PushNewAdvert(
        val senderPubKeyPrefix: ByteArray,
        val name: String,
        val lat: Double,
        val lon: Double,
        val snrDb: Float,
    ) : NodeFrame()
    data class PushAckConfirmed(val ackTag: Long, val rttMs: Long) : NodeFrame()
    object PushMsgWaiting : NodeFrame()
    object PushPathUpdated : NodeFrame()
    data class BattAndStorage(
        val battMv: Int,
        val battPct: Int,
        val usedStorageBytes: Long,
        val freeStorageBytes: Long,
    ) : NodeFrame()
    data class RadioStats(
        val txQueueLen: Int,
        val txQueueFree: Int,
        val numSent: Long,
        val numReceived: Long,
        val numDirectSent: Long,
        val numChannelSent: Long,
    ) : NodeFrame()
    object LoginOk : NodeFrame()
    data class LoginFail(val reason: String) : NodeFrame()
    data class Unknown(val code: Int, val raw: ByteArray) : NodeFrame()
}

data class MeshChannel(
    val idx: Int,
    val name: String,
    /** AES-128 secret — managed by the node, not used directly in the app. */
    val secret: ByteArray = ByteArray(16),
) {
    val isPublic: Boolean get() = idx == 0
    val displayName: String get() = name.ifBlank { if (isPublic) "Public" else "Channel $idx" }
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
