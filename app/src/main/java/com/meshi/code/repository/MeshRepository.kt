package com.meshi.code.repository

import com.meshi.code.MeshiConfig
import com.meshi.code.ble.MeshiBleManager
import com.meshi.code.ble.ScanDevice
import com.meshi.code.ble.ConnectionState
import com.meshi.code.ble.withBleRetry
import com.meshi.code.db.ChatMessageDao
import com.meshi.code.db.ContactNicknameDao
import com.meshi.code.db.ContactNicknameEntity
import com.meshi.code.db.GameRecordDao
import com.meshi.code.db.SnrSampleDao
import com.meshi.code.db.toDomain
import com.meshi.code.db.toEntity
import com.meshi.code.protocol.InputValidator
import com.meshi.code.protocol.MeshChannel
import com.meshi.code.protocol.MeshContact
import com.meshi.code.protocol.NodeFrame
import com.meshi.code.protocol.PacketFramer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class DeliveryState { NONE, SENT, DELIVERED }

data class ChatMessage(
    val senderName: String,
    val senderDisplayId: String,
    val text: String,
    val timestampMs: Long,
    val isSentByMe: Boolean,
    val channelIdx: Int = 0,
    val snrDb: Float = 0f,
    val ackTag: Long = 0L,
    val deliveryState: DeliveryState = DeliveryState.NONE,
    val pathLen: Int = 0,
    val rttMs: Int = 0,
)

data class AdvertEvent(
    val senderPubKeyPrefix: ByteArray,
    val name: String,
    val lat: Double,
    val lon: Double,
    val snrDb: Float,
    val isNew: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Single source of truth for all MeshCore state.
 * Observes [MeshiBleManager.frames] and delegates frame routing to [FrameRouter].
 */
class MeshRepository(
    val ble: MeshiBleManager,
    private val scope: CoroutineScope,
    private val dao: ChatMessageDao,
    val nicknameDao: ContactNicknameDao,
    val gameRecordDao: GameRecordDao,
    val snrSampleDao: SnrSampleDao,
    private val isBlocked: (senderHex: String) -> Boolean = { false },
    private val isDeleted: (pubKeyHex: String) -> Boolean = { false },
) {
    val scanResults: StateFlow<List<ScanDevice>> = ble.scanResults
    val connectionState: StateFlow<ConnectionState> = ble.connectionState
    val selfInfo: StateFlow<NodeFrame.SelfInfo?> = ble.selfInfo

    // Mutable state owned by FrameRouter but exposed here for the rest of the app
    private val _contacts = MutableStateFlow<List<MeshContact>>(emptyList())
    val contacts: StateFlow<List<MeshContact>> = _contacts

    private val _adverts = MutableStateFlow<List<AdvertEvent>>(emptyList())
    val adverts: StateFlow<List<AdvertEvent>> = _adverts

    private val _channels = MutableStateFlow<Map<Int, MeshChannel>>(
        mapOf(0 to MeshChannel(0, "Public"))
    )
    val channels: StateFlow<Map<Int, MeshChannel>> = _channels

    private val _deviceInfo = MutableStateFlow<NodeFrame.DeviceInfo?>(null)
    val deviceInfo: StateFlow<NodeFrame.DeviceInfo?> = _deviceInfo

    private val _nodeStats = MutableStateFlow<NodeFrame.BattAndStorage?>(null)
    val nodeStats: StateFlow<NodeFrame.BattAndStorage?> = _nodeStats

    private val _radioStats = MutableStateFlow<NodeFrame.RadioStats?>(null)
    val radioStats: StateFlow<NodeFrame.RadioStats?> = _radioStats

    /** All messages persisted in the local Room database, ordered by time. */
    val messages: StateFlow<List<ChatMessage>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Local contact nicknames, keyed by pubKeyHex. */
    val nicknames: StateFlow<Map<String, String>> = nicknameDao.observeAll()
        .map { list -> list.associate { it.pubKeyHex to it.nickname } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Set of pubKeyHex strings for contacts the user has manually verified. */
    val verifiedContacts: StateFlow<Set<String>> = nicknameDao.observeAll()
        .map { list -> list.filter { it.isVerified }.map { it.pubKeyHex }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Contact tags, keyed by pubKeyHex. Only non-blank tags are included. */
    val contactTags: StateFlow<Map<String, String>> = nicknameDao.observeAll()
        .map { list -> list.filter { it.tag.isNotBlank() }.associate { it.pubKeyHex to it.tag } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    // ---- Frame Router ----
    private val frameRouter = FrameRouter(
        scope = scope,
        dao = dao,
        nicknameDao = nicknameDao,
        snrSampleDao = snrSampleDao,
        isBlocked = isBlocked,
        isDeleted = isDeleted,
        nicknames = nicknames,
        contacts = _contacts,
        adverts = _adverts,
        channels = _channels,
        deviceInfo = _deviceInfo,
        nodeStats = _nodeStats,
        radioStats = _radioStats,
    )

    val gameMessages: SharedFlow<Pair<ByteArray, String>> = frameRouter.gameMessages
    val typingEvents: SharedFlow<String> = frameRouter.typingEvents
    val reactionEvents: SharedFlow<Triple<String, Long, String>> = frameRouter.reactionEvents
    val pingResults: SharedFlow<Pair<String, Int>> = frameRouter.pingResults
    val loginResult: SharedFlow<Pair<Boolean, String>> = frameRouter.loginResult

    init {
        scope.launch {
            ble.frames.collect { frame -> frameRouter.handleFrame(frame) }
        }

        // When a PUSH_MSG_WAITING arrives, drain the queue
        scope.launch {
            ble.frames
                .filterIsInstance<NodeFrame.PushMsgWaiting>()
                .collect { ble.pollQueuedMessages() }
        }

        // When a new unknown node advertises, refresh contacts so we get the full record
        scope.launch {
            ble.frames
                .filterIsInstance<NodeFrame.PushNewAdvert>()
                .collect { refreshContacts() }
        }
    }

    // ---- Scan ----
    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()
    fun connect(device: ScanDevice) = ble.connect(device)
    fun disconnect() = ble.disconnect()

    /** Re-fetch the full contact list from the node. */
    suspend fun refreshContacts() = ble.sendRaw(PacketFramer.getContactsFrame())

    /** Persist a contact to the node's flash storage. */
    suspend fun saveContact(contact: MeshContact): Boolean =
        ble.sendRaw(PacketFramer.addUpdateContactFrame(contact))

    /** Remove a contact from the in-memory list and erase their DB record. */
    suspend fun deleteContact(pubKeyHex: String) {
        _contacts.value = _contacts.value.filter { it.displayId != pubKeyHex }
        nicknameDao.deleteNickname(pubKeyHex)
    }

    // ---- Sending (with retry) ----

    suspend fun sendDirectMessage(contact: MeshContact, text: String): Boolean {
        val self = selfInfo.value ?: return false
        val sentAtMs = System.currentTimeMillis()
        val ok = withBleRetry { ble.sendDirectMessage(contact.pubKeyPrefix, text) }
        if (ok) {
            val sent = withTimeoutOrNull(MeshiConfig.SEND_ACK_TIMEOUT_MS) {
                ble.frames.filterIsInstance<NodeFrame.Sent>().first()
            }
            if (sent != null && sent.ackTag > 0L) {
                frameRouter.trackAck(sent.ackTag, sentAtMs)
            }
            val msg = ChatMessage(
                senderName = self.nodeName.ifBlank { "Me" },
                senderDisplayId = self.displayId,
                text = text,
                timestampMs = sentAtMs,
                isSentByMe = true,
                channelIdx = -1,
                ackTag = sent?.ackTag ?: 0L,
                deliveryState = if (sent != null) DeliveryState.SENT else DeliveryState.NONE,
            )
            dao.insert(msg.toEntity())
        }
        return ok
    }

    /** Send a game protocol DM — does not persist to message history. */
    suspend fun sendGameDirect(contact: MeshContact, text: String): Boolean =
        withBleRetry { ble.sendDirectMessage(contact.pubKeyPrefix, text) }

    /** Send a channel message without persisting to DB (used for reactions, game protocol). */
    suspend fun sendGameChannel(text: String, channelIdx: Int = 0): Boolean =
        withBleRetry { ble.sendChannelMessage(text, channelIdx) }

    /** Send a ping DM and track its ack tag for RTT measurement. */
    suspend fun sendPing(contact: MeshContact): Boolean {
        val sentAt = System.currentTimeMillis()
        val ok = withBleRetry { ble.sendDirectMessage(contact.pubKeyPrefix, "[PING]") }
        if (ok) {
            val sent = withTimeoutOrNull(MeshiConfig.SEND_ACK_TIMEOUT_MS) {
                ble.frames.filterIsInstance<NodeFrame.Sent>().first()
            }
            if (sent != null && sent.ackTag > 0L) {
                frameRouter.trackAck(sent.ackTag, sentAt)
                frameRouter.trackPing(sent.ackTag, contact.displayId)
            }
        }
        return ok
    }

    suspend fun sendAdvert(flood: Boolean = false, lat: Double? = null, lon: Double? = null) {
        if (lat != null && lon != null) {
            ble.sendRaw(PacketFramer.setAdvertLatLonFrame(lat, lon))
            kotlinx.coroutines.delay(MeshiConfig.BLE_WRITE_SPACING_MS)
        }
        ble.sendAdvert(flood)
    }

    /** Fetch config for a single channel slot. */
    suspend fun fetchChannel(idx: Int) = ble.sendRaw(PacketFramer.getChannelFrame(idx))

    /** Fetch all 8 channel slots. */
    suspend fun fetchAllChannels() {
        for (i in 0..7) {
            ble.sendRaw(PacketFramer.getChannelFrame(i))
            kotlinx.coroutines.delay(MeshiConfig.BLE_WRITE_SPACING_MS)
        }
    }

    /** Request battery/storage stats from the node. */
    suspend fun fetchNodeStats() {
        ble.sendRaw(PacketFramer.getBatteryStorageFrame())
    }

    /** Request radio packet statistics. */
    suspend fun fetchRadioStats() {
        ble.sendRaw(PacketFramer.getStatsFrame())
    }

    /** Send login credentials to a repeater / room server. */
    suspend fun sendLogin(password: String): Boolean =
        withBleRetry { ble.sendRaw(PacketFramer.sendLoginFrame(password)) }

    /** Set or clear a local nickname for a contact. */
    suspend fun setNickname(pubKeyHex: String, nickname: String) {
        if (nickname.isBlank()) {
            nicknameDao.deleteNickname(pubKeyHex)
        } else {
            val existing = nicknameDao.get(pubKeyHex)
            nicknameDao.setNickname(ContactNicknameEntity(pubKeyHex, nickname.trim(),
                existing?.isVerified ?: false, existing?.tag ?: ""))
        }
    }

    /** Toggle verification status for a contact. Creates a row if none exists yet. */
    suspend fun setVerified(pubKeyHex: String, verified: Boolean) {
        val existing = nicknameDao.get(pubKeyHex)
        if (existing != null) {
            nicknameDao.setVerified(pubKeyHex, verified)
        } else {
            nicknameDao.setNickname(ContactNicknameEntity(pubKeyHex, "", verified))
        }
    }

    /** Set or clear a tag for a contact. Creates a row if none exists yet. */
    suspend fun setTag(pubKeyHex: String, tag: String) {
        val existing = nicknameDao.get(pubKeyHex)
        if (existing != null) {
            nicknameDao.setTag(pubKeyHex, tag.trim())
        } else {
            nicknameDao.setNickname(ContactNicknameEntity(pubKeyHex, "", false, tag.trim()))
        }
    }

    /** Send a typing indicator DM (not persisted). */
    suspend fun sendTypingIndicator(contact: MeshContact) {
        ble.sendDirectMessage(contact.pubKeyPrefix, "[TYPING]")
    }

    /** Search messages by text content. */
    suspend fun searchMessages(query: String): List<ChatMessage> =
        dao.searchMessages(query).map { it.toDomain() }

    /** Create or update a channel on the node. */
    suspend fun setChannel(idx: Int, name: String, secret: ByteArray): Boolean {
        val ok = withBleRetry { ble.sendRaw(PacketFramer.setChannelFrame(idx, name, secret)) }
        if (ok) {
            _channels.value = _channels.value + (idx to MeshChannel(idx, name, secret))
        }
        return ok
    }

    /** Remove a private channel by clearing its slot on the node. */
    suspend fun deleteChannel(idx: Int): Boolean {
        if (idx == 0) return false
        val ok = withBleRetry { ble.sendRaw(PacketFramer.setChannelFrame(idx, "", ByteArray(16))) }
        if (ok) {
            _channels.value = _channels.value - idx
        }
        return ok
    }

    /** Send a message to a specific channel by index. */
    suspend fun sendChannelMessage(text: String, channelIdx: Int = 0): Boolean {
        val self = selfInfo.value ?: return false
        val ok = withBleRetry { ble.sendChannelMessage(text, channelIdx) }
        if (ok) {
            dao.insert(
                ChatMessage(
                    senderName = self.nodeName.ifBlank { "Me" },
                    senderDisplayId = self.displayId,
                    text = text,
                    timestampMs = System.currentTimeMillis(),
                    isSentByMe = true,
                    channelIdx = channelIdx,
                ).toEntity()
            )
        }
        return ok
    }
}
