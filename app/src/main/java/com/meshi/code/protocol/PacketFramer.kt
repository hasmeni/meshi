package com.meshi.code.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds command frames (app → node) and decodes response frames (node → app).
 *
 * Over BLE there is NO extra framing — each GATT write/notification is exactly one frame.
 * Byte 0 of every frame is the command or response code.
 *
 * Source: MeshCore Companion Protocol docs + meshcore-open (meshcore_protocol.dart).
 */
object PacketFramer {

    // ---- Frame builders ----

    /** 0x16 — First thing to send after BLE connect. Returns DEVICE_INFO (0x0D). */
    fun deviceQueryFrame(): ByteArray = byteArrayOf(Cmd.DEVICE_QUERY, 0x03)

    /**
     * 0x01 — Must follow DEVICE_QUERY. Returns SELF_INFO (0x05).
     * [appName] is shown to other nodes as the connected app.
     */
    fun appStartFrame(appName: String = "Meshi"): ByteArray {
        val nameBytes = appName.toByteArray(Charsets.UTF_8)
        return byteArrayOf(Cmd.APP_START, 0x03, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20) + nameBytes
    }

    /** 0x06 — Sync node clock. Send after APP_START. */
    fun setDeviceTimeFrame(epochSeconds: Long = System.currentTimeMillis() / 1000): ByteArray =
        buf(5) { put(Cmd.SET_DEVICE_TIME); putInt(epochSeconds.toInt()) }

    /** 0x04 — Fetch all contacts (since=0). Triggers CONTACTS_START → CONTACT×N → END_OF_CONTACTS. */
    fun getContactsFrame(): ByteArray =
        buf(5) { put(Cmd.GET_CONTACTS); putInt(0) }

    /** 0x0A — Fetch next queued message. Repeat until NoMoreMessages (0x0A response). */
    fun syncNextMessageFrame(): ByteArray = byteArrayOf(Cmd.SYNC_NEXT_MESSAGE)

    /**
     * 0x07 — Broadcast this node's advertisement.
     * [flood] = false → zero-hop local beacon only.
     * [flood] = true  → flood across the network.
     */
    fun sendSelfAdvertFrame(flood: Boolean = false): ByteArray =
        byteArrayOf(Cmd.SEND_SELF_ADVERT, if (flood) 0x01 else 0x00, 0, 0, 0, 0, 0, 0, 0)

    /**
     * 0x03 — Send a message on a channel (public = index 0).
     * Response: SENT (0x06).
     */
    fun sendChannelMessageFrame(channelIdx: Int = 0, text: String): ByteArray {
        val ts = (System.currentTimeMillis() / 1000).toInt()
        val textBytes = text.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        return buf(7 + textBytes.size) {
            put(Cmd.SEND_CHANNEL_MSG)
            put(0x00)                    // txt_type = plain
            put(channelIdx.toByte())
            putInt(ts)
            put(textBytes)
        }
    }

    /**
     * 0x02 — Send a direct (encrypted) message to a contact.
     * [recipientPubKeyPrefix] = first 6 bytes of recipient's Ed25519 public key.
     */
    fun sendDirectMessageFrame(
        recipientPubKeyPrefix: ByteArray,
        text: String,
        attempt: Int = 0,
    ): ByteArray {
        require(recipientPubKeyPrefix.size >= 6) { "Need at least 6 bytes of public key" }
        val ts = (System.currentTimeMillis() / 1000).toInt()
        val textBytes = text.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        return buf(13 + textBytes.size) {
            put(Cmd.SEND_TXT_MSG)
            put(0x00)                    // txt_type = plain
            put(attempt.toByte())
            putInt(ts)
            put(recipientPubKeyPrefix, 0, 6)
            put(textBytes)
        }
    }

    /** 0x14 — Query battery voltage and storage usage. */
    fun getBatteryStorageFrame(): ByteArray = byteArrayOf(Cmd.GET_BATT_STORAGE)

    /** 0x38 — Request radio / packet statistics. */
    fun getStatsFrame(): ByteArray = byteArrayOf(Cmd.GET_STATS)

    /**
     * 0x1A — Send login credentials to a repeater / room server.
     * [password] is sent as null-terminated UTF-8 after the command byte.
     */
    fun sendLoginFrame(password: String): ByteArray {
        val passBytes = password.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        return byteArrayOf(Cmd.SEND_LOGIN) + passBytes
    }

    /**
     * 0x0E — Set the node's advertised GPS position before sending an advert.
     * Coordinates in decimal degrees; node stores them as microdegrees (int32 LE).
     */
    fun setAdvertLatLonFrame(lat: Double, lon: Double): ByteArray =
        buf(9) {
            put(Cmd.SET_ADVERT_LATLON)
            putInt((lat * 1_000_000).toInt())
            putInt((lon * 1_000_000).toInt())
        }

    /** 0x1F — Get channel config by index (0-7). Response: CHANNEL_INFO (0x12). */
    fun getChannelFrame(channelIdx: Int): ByteArray =
        byteArrayOf(Cmd.GET_CHANNEL, channelIdx.toByte())

    /**
     * 0x20 — Create or update a channel.
     * [secret] must be 16 bytes (AES-128). Generate randomly for new channels.
     * Channel 0 with the public secret is the public broadcast channel.
     * Total frame: 50 bytes.
     */
    fun setChannelFrame(channelIdx: Int, name: String, secret: ByteArray): ByteArray {
        require(secret.size == 16) { "Channel secret must be exactly 16 bytes" }
        return buf(50) {
            put(Cmd.SET_CHANNEL)
            put(channelIdx.toByte())
            put(name.toByteArray(Charsets.UTF_8).copyOf(32))  // null-padded to 32 bytes
            put(secret)                                         // 16-byte AES-128 key
        }
    }

    /**
     * 0x09 — Persist a contact to the node's flash storage.
     * Must use the full 32-byte public key (get it from RESP_CONTACT after GET_CONTACTS).
     * Total frame: 144 bytes.
     */
    fun addUpdateContactFrame(contact: MeshContact): ByteArray {
        return buf(144) {
            put(0x09)                                          // command
            put(contact.pubKey)                                // [1-32]  32-byte Ed25519 public key
            put(1)                                             // [33]    type = chat node
            put(0)                                             // [34]    flags (0 = not favourite)
            put(0xFF.toByte())                                 // [35]    out_path_len = flood routing
            put(ByteArray(64))                                 // [36-99] out_path (zeroed = unknown)
            val nameBytes = contact.name.toByteArray(Charsets.UTF_8).copyOf(32)
            put(nameBytes)                                     // [100-131] adv_name null-padded to 32
            putInt((contact.lastAdvertTs / 1000).toInt())      // [132-135] last_advert_ts (seconds)
            putInt((contact.lat * 1_000_000).toInt())          // [136-139] adv_lat microdegrees
            putInt((contact.lon * 1_000_000).toInt())          // [140-143] adv_lon microdegrees
        }
    }

    // ---- Frame decoder ----

    /** Decode a raw notification byte array from the node into a [NodeFrame]. */
    fun decode(raw: ByteArray): NodeFrame {
        if (raw.isEmpty()) return NodeFrame.Unknown(0, raw)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val code = buf.getUByte()

        return try {
            when (code) {
                Resp.OK                  -> NodeFrame.Ok
                Resp.ERROR               -> NodeFrame.Err(buf.getOrDefault { 0 })
                Resp.SELF_INFO           -> parseSelfInfo(buf)
                Resp.DEVICE_INFO         -> parseDeviceInfo(buf)
                Resp.SENT                -> parseSent(buf)
                Resp.NO_MORE_MESSAGES    -> NodeFrame.NoMoreMessages
                Resp.CONTACTS_START      -> NodeFrame.ContactsStart
                Resp.CONTACT             -> parseContact(buf)
                Resp.END_OF_CONTACTS     -> NodeFrame.EndOfContacts
                Resp.DIRECT_MSG_V3       -> parseDirectMsgV3(buf)
                Resp.CHANNEL_MSG_V3      -> parseChannelMsgV3(buf)
                Resp.CHANNEL_INFO        -> parseChannelInfo(buf)
                Resp.PUSH_ADVERT         -> parseAdvertPush(buf, Resp.PUSH_ADVERT)
                Resp.PUSH_NEW_ADVERT     -> parseAdvertPush(buf, Resp.PUSH_NEW_ADVERT)
                Resp.PUSH_ACK_CONFIRMED  -> parseAckConfirmed(buf)
                Resp.PUSH_MSG_WAITING    -> NodeFrame.PushMsgWaiting
                Resp.PUSH_PATH_UPDATED   -> NodeFrame.PushPathUpdated
                Resp.BATT_AND_STORAGE    -> parseBattAndStorage(buf)
                Resp.RADIO_STATS         -> parseRadioStats(buf)
                Resp.PUSH_LOGIN_OK       -> NodeFrame.LoginOk
                Resp.PUSH_LOGIN_FAIL     -> NodeFrame.LoginFail(
                    buf.getRemainingBytes().decodeUtf8NullTerminated()
                )
                else                     -> NodeFrame.Unknown(code, raw)
            }
        } catch (e: Exception) {
            NodeFrame.Unknown(code, raw)
        }
    }

    // ---- Private parsers ----

    private fun parseSelfInfo(buf: ByteBuffer): NodeFrame.SelfInfo {
        val nodeType = buf.getOrDefault { 0 }
        val txPower = buf.getOrDefault { 0 }
        val maxPower = buf.getOrDefault { 0 }
        val pubKey = buf.getBytes(32)
        val lat = buf.getOrDefault { 0 }
        val lon = buf.getOrDefault { 0 }
        val multiAcks = buf.getOrDefault { 0 }
        val locPolicy = buf.getOrDefault { 0 }
        val telemetry = buf.getOrDefault { 0 }
        val manualAdd = buf.getOrDefault { 0 }
        val freqHz = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val bwHz = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val sf = buf.getOrDefault { 0 }
        val cr = buf.getOrDefault { 0 }
        val nameBytes = buf.getRemainingBytes()
        val name = nameBytes.decodeUtf8NullTerminated()
        return NodeFrame.SelfInfo(
            pubKey = pubKey,
            nodeName = name,
            nodeType = nodeType.and(0xFF),
            radioFreqHz = freqHz,
            radioSf = sf.and(0xFF),
            radioBwHz = bwHz,
        )
    }

    private fun parseDeviceInfo(buf: ByteBuffer): NodeFrame.DeviceInfo {
        val fwVer = buf.getOrDefault { 0 }.and(0xFF)
        // Skip static fields, read strings
        if (buf.remaining() < 2) return NodeFrame.DeviceInfo(fwVer, "", "")
        buf.getBytes(2)  // max_contacts_raw, max_channels
        buf.getBytes(4)  // ble_pin
        val fwBuild = buf.getBytes(12).decodeUtf8NullTerminated()
        val model = buf.getBytes(40).decodeUtf8NullTerminated()
        val fwVersionStr = buf.getBytes(20).decodeUtf8NullTerminated()
        return NodeFrame.DeviceInfo(fwVer, model, fwVersionStr)
    }

    private fun parseSent(buf: ByteBuffer): NodeFrame.Sent {
        val routeFlag = buf.getOrDefault { 0 }.and(0xFF)
        val ackTag = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val timeout = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        return NodeFrame.Sent(routeFlag, ackTag, timeout)
    }

    private fun parseContact(buf: ByteBuffer): NodeFrame.ContactRecord {
        val pubKey = buf.getBytes(32)
        val type = buf.getOrDefault { 0 }
        val flags = buf.getOrDefault { 0 }
        val pathLen = buf.getOrDefault { 0 }
        buf.getBytes(64)   // out_path
        val rawName = buf.getBytes(32).decodeUtf8NullTerminated()
        val lastAdvert = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val lat = if (buf.remaining() >= 4) buf.int / 1_000_000.0 else 0.0
        val lon = if (buf.remaining() >= 4) buf.int / 1_000_000.0 else 0.0
        return NodeFrame.ContactRecord(
            MeshContact(
                pubKey = pubKey,
                pubKeyPrefix = pubKey.take(6).toByteArray(),
                name = rawName,
                lastAdvertTs = lastAdvert * 1000L,
                lat = lat,
                lon = lon,
            )
        )
    }

    private fun parseDirectMsgV3(buf: ByteBuffer): NodeFrame.DirectMsgV3 {
        val snrRaw = buf.getOrDefault { 0 }
        buf.getBytes(2)  // reserved
        val senderPrefix = buf.getBytes(6)
        val pathLen = buf.getOrDefault { 0 }
        val txtType = buf.getOrDefault { 0 }
        val ts = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        if (txtType.and(0xFF) == 2 && buf.remaining() >= 4) buf.getBytes(4) // signature
        val text = buf.getRemainingBytes().decodeUtf8NullTerminated()
        return NodeFrame.DirectMsgV3(
            senderPubKeyPrefix = senderPrefix,
            text = text,
            timestampMs = ts * 1000L,
            snrDb = snrRaw / 4f,
            pathLen = pathLen.and(0xFF),
        )
    }

    private fun parseChannelMsgV3(buf: ByteBuffer): NodeFrame.ChannelMsgV3 {
        val snrRaw = buf.getOrDefault { 0 }
        buf.getBytes(2)  // reserved
        val channelIdx = buf.getOrDefault { 0 }.and(0xFF)
        val pathLen = buf.getOrDefault { 0 }
        val txtType = buf.getOrDefault { 0 }
        val ts = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        // Text arrives as "SenderName: MessageBody"
        val full = buf.getRemainingBytes().decodeUtf8NullTerminated()
        val colonIdx = full.indexOf(": ")
        val sender = if (colonIdx > 0) full.substring(0, colonIdx) else ""
        val text = if (colonIdx > 0) full.substring(colonIdx + 2) else full
        return NodeFrame.ChannelMsgV3(
            channelIdx = channelIdx,
            senderName = sender,
            text = text,
            timestampMs = ts * 1000L,
            snrDb = snrRaw / 4f,
        )
    }

    private fun parseChannelInfo(buf: ByteBuffer): NodeFrame.ChannelInfo {
        val idx = buf.getOrDefault { 0 }.and(0xFF)
        val name = buf.getBytes(32).decodeUtf8NullTerminated()
        val secret = buf.getBytes(16)
        return NodeFrame.ChannelInfo(idx, name, secret)
    }

    private fun parseAdvertPush(buf: ByteBuffer, code: Int): NodeFrame {
        val snrRaw = buf.getOrDefault { 0 }
        buf.getBytes(2)   // reserved
        val prefix = buf.getBytes(6)
        val pathLen = buf.getOrDefault { 0 }
        buf.getBytes(64)  // out_path
        val name = buf.getBytes(32).decodeUtf8NullTerminated()
        val lastAdvert = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val lat = if (buf.remaining() >= 4) buf.int / 1_000_000.0 else 0.0
        val lon = if (buf.remaining() >= 4) buf.int / 1_000_000.0 else 0.0
        return if (code == Resp.PUSH_NEW_ADVERT) {
            NodeFrame.PushNewAdvert(prefix, name, lat, lon, snrRaw / 4f)
        } else {
            NodeFrame.PushAdvert(prefix, name, lat, lon, snrRaw / 4f)
        }
    }

    private fun parseBattAndStorage(buf: ByteBuffer): NodeFrame.BattAndStorage {
        val battMv = if (buf.remaining() >= 2) buf.short.toInt() and 0xFFFF else 0
        val battPct = buf.getOrDefault { 0 }.and(0xFF)
        val used = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val free = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        return NodeFrame.BattAndStorage(battMv, battPct, used, free)
    }

    private fun parseRadioStats(buf: ByteBuffer): NodeFrame.RadioStats {
        val txQueueLen  = if (buf.remaining() >= 2) buf.short.toInt() and 0xFFFF else 0
        val txQueueFree = if (buf.remaining() >= 2) buf.short.toInt() and 0xFFFF else 0
        val numSent     = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val numRecv     = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val numDirect   = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val numChannel  = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        return NodeFrame.RadioStats(txQueueLen, txQueueFree, numSent, numRecv, numDirect, numChannel)
    }

    private fun parseAckConfirmed(buf: ByteBuffer): NodeFrame.PushAckConfirmed {
        val ackTag = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        val rtt = if (buf.remaining() >= 4) buf.int.toLong() and 0xFFFFFFFFL else 0L
        return NodeFrame.PushAckConfirmed(ackTag, rtt)
    }

    // ---- ByteBuffer helpers ----

    private fun buf(size: Int, block: ByteBuffer.() -> Unit): ByteArray {
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.block()
        return b.array()
    }

    private fun ByteBuffer.getUByte(): Int = (get().toInt() and 0xFF)
    private fun ByteBuffer.getOrDefault(default: () -> Int): Int =
        if (hasRemaining()) get().toInt() else default()

    private fun ByteBuffer.getBytes(n: Int): ByteArray {
        val avail = minOf(n, remaining())
        val out = ByteArray(n)
        get(out, 0, avail)
        return out
    }

    private fun ByteBuffer.getRemainingBytes(): ByteArray {
        val out = ByteArray(remaining())
        get(out)
        return out
    }

    /**
     * Decode bytes as UTF-8, stopping at the first null byte (C-style null termination).
     * This prevents garbage bytes that follow the null terminator in fixed-width fields
     * (e.g. 32-byte channel names) from appearing as "??%>>" in the UI.
     */
    private fun ByteArray.decodeUtf8NullTerminated(): String {
        val end = indexOfFirst { it == 0.toByte() }.let { if (it < 0) size else it }
        return String(this, 0, end, Charsets.UTF_8)
    }
}
