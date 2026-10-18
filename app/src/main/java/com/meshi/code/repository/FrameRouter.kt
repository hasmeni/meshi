package com.meshi.code.repository

import com.meshi.code.MeshiConfig
import com.meshi.code.db.ChatMessageDao
import com.meshi.code.db.ContactNicknameDao
import com.meshi.code.db.ContactNicknameEntity
import com.meshi.code.db.SnrSampleDao
import com.meshi.code.db.SnrSampleEntity
import com.meshi.code.db.toEntity
import com.meshi.code.game.BSH_PREFIX
import com.meshi.code.game.CHESS_PREFIX
import com.meshi.code.game.GAME_PREFIX
import com.meshi.code.game.TTT_PREFIX
import com.meshi.code.protocol.InputValidator
import com.meshi.code.protocol.MeshChannel
import com.meshi.code.protocol.MeshContact
import com.meshi.code.protocol.NodeFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Routes decoded [NodeFrame]s from the BLE layer into the appropriate state holders.
 *
 * Extracted from [MeshRepository] to keep frame-dispatch logic separate from
 * send/command logic.
 */
class FrameRouter(
    private val scope: CoroutineScope,
    private val dao: ChatMessageDao,
    private val nicknameDao: ContactNicknameDao,
    private val snrSampleDao: SnrSampleDao,
    private val isBlocked: (senderHex: String) -> Boolean,
    private val isDeleted: (pubKeyHex: String) -> Boolean,
    private val nicknames: StateFlow<Map<String, String>>,
    val contacts: MutableStateFlow<List<MeshContact>>,
    val adverts: MutableStateFlow<List<AdvertEvent>>,
    val channels: MutableStateFlow<Map<Int, MeshChannel>>,
    val deviceInfo: MutableStateFlow<NodeFrame.DeviceInfo?>,
    val nodeStats: MutableStateFlow<NodeFrame.BattAndStorage?>,
    val radioStats: MutableStateFlow<NodeFrame.RadioStats?>,
) {
    /** Game protocol messages keyed by sender pubkey prefix. */
    private val _gameMessages = MutableSharedFlow<Pair<ByteArray, String>>(extraBufferCapacity = 64)
    val gameMessages: SharedFlow<Pair<ByteArray, String>> = _gameMessages

    /** Typing indicator events — emits the pubKeyHex of the contact who is typing. */
    private val _typingEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val typingEvents: SharedFlow<String> = _typingEvents

    /** Reaction events — Triple(senderKey, originalTimestampMs, emoji). */
    private val _reactionEvents = MutableSharedFlow<Triple<String, Long, String>>(extraBufferCapacity = 64)
    val reactionEvents: SharedFlow<Triple<String, Long, String>> = _reactionEvents

    /** Ping results — Pair(contactDisplayId, rttMs). */
    private val _pingResults = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 8)
    val pingResults: SharedFlow<Pair<String, Int>> = _pingResults

    /** Login result — true = success, false = failure (with reason string). */
    private val _loginResult = MutableSharedFlow<Pair<Boolean, String>>(extraBufferCapacity = 4)
    val loginResult: SharedFlow<Pair<Boolean, String>> = _loginResult

    /** ackTag → sentAtMs for pending DMs, used to compute RTT on delivery. */
    private val pendingAcks = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    /** ackTag → contactDisplayId for pending pings. */
    private val pendingPings = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private val contactsAccumulator = mutableListOf<MeshContact>()

    fun trackAck(ackTag: Long, sentAtMs: Long) {
        pendingAcks[ackTag] = sentAtMs
    }

    fun trackPing(ackTag: Long, contactDisplayId: String) {
        pendingPings[ackTag] = contactDisplayId
    }

    fun handleFrame(frame: NodeFrame) {
        when (frame) {
            is NodeFrame.DeviceInfo -> deviceInfo.value = frame
            is NodeFrame.BattAndStorage -> nodeStats.value = frame
            is NodeFrame.RadioStats -> radioStats.value = frame

            is NodeFrame.LoginOk -> scope.launch { _loginResult.emit(true to "Logged in successfully") }
            is NodeFrame.LoginFail -> scope.launch {
                _loginResult.emit(false to InputValidator.sanitizeMessageText(frame.reason).ifBlank { "Login failed" })
            }

            is NodeFrame.ContactsStart -> synchronized(contactsAccumulator) { contactsAccumulator.clear() }
            is NodeFrame.ContactRecord -> {
                val sanitized = frame.contact.copy(
                    name = InputValidator.sanitizeName(frame.contact.name)
                )
                if (InputValidator.isValidPubKeyPrefix(sanitized.pubKeyPrefix)) {
                    synchronized(contactsAccumulator) { contactsAccumulator.add(sanitized) }
                }
            }
            is NodeFrame.EndOfContacts -> {
                val accumulated = synchronized(contactsAccumulator) { contactsAccumulator.toList() }
                contacts.value = accumulated.filter { !isDeleted(it.displayId) }
                scope.launch {
                    val savedGps = nicknameDao.observeAll().first()
                        .filter { it.lat != 0.0 || it.lon != 0.0 }
                        .associateBy { it.pubKeyHex }
                    if (savedGps.isNotEmpty()) {
                        contacts.value = contacts.value.map { c ->
                            if (c.lat == 0.0 && c.lon == 0.0) {
                                val s = savedGps[c.displayId]
                                if (s != null) c.copy(lat = s.lat, lon = s.lon) else c
                            } else c
                        }
                    }
                }
            }

            is NodeFrame.DirectMsgV3 -> handleDirectMsg(frame)
            is NodeFrame.ChannelMsgV3 -> handleChannelMsg(frame)

            is NodeFrame.ChannelInfo -> {
                if (InputValidator.isValidChannelIdx(frame.channelIdx) &&
                    (frame.channelIdx == 0 || frame.name.isNotBlank())
                ) {
                    val sanitizedName = InputValidator.sanitizeChannelName(frame.name)
                    channels.value = channels.value +
                        (frame.channelIdx to MeshChannel(frame.channelIdx, sanitizedName, frame.secret))
                }
            }

            is NodeFrame.PushAckConfirmed -> {
                if (frame.ackTag > 0) {
                    val sentAt = pendingAcks.remove(frame.ackTag)
                    val rttMs = if (sentAt != null) (System.currentTimeMillis() - sentAt).toInt() else 0
                    scope.launch {
                        dao.updateDeliveryStateAndRtt(frame.ackTag, DeliveryState.DELIVERED.name, rttMs)
                    }
                    val pingContactId = pendingPings.remove(frame.ackTag)
                    if (pingContactId != null) {
                        scope.launch { _pingResults.emit(pingContactId to rttMs) }
                    }
                }
            }

            is NodeFrame.PushAdvert ->
                addAdvert(frame.senderPubKeyPrefix, frame.name, frame.lat, frame.lon, frame.snrDb, false)
            is NodeFrame.PushNewAdvert ->
                addAdvert(frame.senderPubKeyPrefix, frame.name, frame.lat, frame.lon, frame.snrDb, true)

            else -> Unit
        }
    }

    private fun handleDirectMsg(frame: NodeFrame.DirectMsgV3) {
        if (!InputValidator.isValidPubKeyPrefix(frame.senderPubKeyPrefix)) return
        val senderHex = frame.senderPubKeyPrefix.toHexString()
        if (isBlocked(senderHex)) return
        val sanitizedText = InputValidator.sanitizeMessageText(frame.text)

        when {
            sanitizedText.startsWith(GAME_PREFIX) || sanitizedText.startsWith(CHESS_PREFIX) ||
            sanitizedText.startsWith(TTT_PREFIX) || sanitizedText.startsWith(BSH_PREFIX) -> {
                scope.launch { _gameMessages.emit(frame.senderPubKeyPrefix to sanitizedText) }
            }
            sanitizedText.startsWith("[REACT]") -> {
                val payload = sanitizedText.removePrefix("[REACT]")
                val ci = payload.indexOf(':')
                if (ci > 0) {
                    val ts = payload.substring(0, ci).toLongOrNull()
                    val emoji = payload.substring(ci + 1).take(8) // limit emoji length
                    if (ts != null && emoji.isNotEmpty()) {
                        scope.launch { _reactionEvents.emit(Triple(senderHex, ts, emoji)) }
                    }
                }
            }
            sanitizedText == "[TYPING]" -> {
                scope.launch { _typingEvents.emit(senderHex) }
            }
            sanitizedText == "[PING]" -> {
                // Incoming ping — no action needed, the ack is handled by the node
            }
            else -> {
                val contact = contacts.value.find {
                    it.pubKeyPrefix.contentEquals(frame.senderPubKeyPrefix)
                }
                val displayName = nicknames.value[senderHex]
                    ?: contact?.name
                    ?: senderHex
                val msg = ChatMessage(
                    senderName = InputValidator.sanitizeName(displayName),
                    senderDisplayId = senderHex,
                    text = sanitizedText,
                    timestampMs = frame.timestampMs,
                    isSentByMe = false,
                    channelIdx = -1,
                    snrDb = frame.snrDb,
                    pathLen = frame.pathLen,
                )
                scope.launch {
                    dao.insert(msg.toEntity())
                    if (frame.snrDb != 0f) {
                        snrSampleDao.insert(SnrSampleEntity(
                            contactId = senderHex,
                            snrDb = frame.snrDb,
                            timestampMs = frame.timestampMs,
                        ))
                        snrSampleDao.trimForContact(senderHex)
                    }
                }
            }
        }
    }

    private fun handleChannelMsg(frame: NodeFrame.ChannelMsgV3) {
        if (!InputValidator.isValidChannelIdx(frame.channelIdx)) return
        val sanitizedText = InputValidator.sanitizeMessageText(frame.text)
        val sanitizedSender = InputValidator.sanitizeName(frame.senderName)

        // Route reactions without persisting to DB
        if (sanitizedText.startsWith("[REACT]")) {
            val payload = sanitizedText.removePrefix("[REACT]")
            val ci = payload.indexOf(':')
            if (ci > 0) {
                val ts = payload.substring(0, ci).toLongOrNull()
                val emoji = payload.substring(ci + 1).take(8)
                if (ts != null && emoji.isNotEmpty()) {
                    scope.launch { _reactionEvents.emit(Triple(sanitizedSender, ts, emoji)) }
                }
            }
            return
        }
        val msg = ChatMessage(
            senderName = sanitizedSender,
            senderDisplayId = sanitizedSender,
            text = sanitizedText,
            timestampMs = frame.timestampMs,
            isSentByMe = false,
            channelIdx = frame.channelIdx,
            snrDb = frame.snrDb,
        )
        scope.launch { dao.insert(msg.toEntity()) }
    }

    private fun addAdvert(prefix: ByteArray, name: String, lat: Double, lon: Double, snr: Float, isNew: Boolean) {
        if (!InputValidator.isValidPubKeyPrefix(prefix)) return
        val prefixHex = prefix.joinToString("") { "%02x".format(it) }
        if (isDeleted(prefixHex)) return
        val sanitizedName = InputValidator.sanitizeName(name)
        val validGps = InputValidator.isValidGps(lat, lon)
        val safeLat = if (validGps) lat else 0.0
        val safeLon = if (validGps) lon else 0.0

        val event = AdvertEvent(prefix, sanitizedName, safeLat, safeLon, snr, isNew)
        // Trim old adverts and enforce max size
        val now = System.currentTimeMillis()
        adverts.value = (adverts.value.filter {
            now - it.timestampMs < MeshiConfig.ADVERTS_TTL_MS
        } + event).takeLast(MeshiConfig.ADVERTS_MAX_SIZE)

        // If this advert carries GPS, update the matching contact immediately
        if (safeLat != 0.0 || safeLon != 0.0) {
            val idx = contacts.value.indexOfFirst { it.displayId == prefixHex }
            if (idx >= 0 && contacts.value[idx].lat == 0.0 && contacts.value[idx].lon == 0.0) {
                val updated = contacts.value.toMutableList()
                updated[idx] = updated[idx].copy(lat = safeLat, lon = safeLon)
                contacts.value = updated
            }
            scope.launch {
                val existing = nicknameDao.get(prefixHex)
                nicknameDao.setNickname(
                    (existing ?: ContactNicknameEntity(pubKeyHex = prefixHex, nickname = ""))
                        .copy(lat = safeLat, lon = safeLon)
                )
            }
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
