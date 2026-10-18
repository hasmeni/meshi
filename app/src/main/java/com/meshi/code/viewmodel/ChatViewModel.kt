package com.meshi.code.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshi.code.MeshiConfig
import com.meshi.code.prefs.AppPreferences
import com.meshi.code.protocol.MeshChannel
import com.meshi.code.protocol.MeshContact
import com.meshi.code.protocol.NodeFrame
import com.meshi.code.repository.AdvertEvent
import com.meshi.code.repository.ChatMessage
import com.meshi.code.repository.MeshRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class ChatViewModel(val repository: MeshRepository, private val prefs: AppPreferences) : ViewModel() {

    val selfInfo: StateFlow<NodeFrame.SelfInfo?> = repository.selfInfo
    val contacts: StateFlow<List<MeshContact>> = repository.contacts
    val adverts: StateFlow<List<AdvertEvent>> = repository.adverts
    val channels: StateFlow<Map<Int, MeshChannel>> = repository.channels
    val connectionState = repository.connectionState
    val deviceInfo: StateFlow<NodeFrame.DeviceInfo?> = repository.deviceInfo
    val nodeStats: StateFlow<NodeFrame.BattAndStorage?> = repository.nodeStats
    val radioStats: StateFlow<NodeFrame.RadioStats?> = repository.radioStats
    val nicknames: StateFlow<Map<String, String>> = repository.nicknames
    val verifiedContacts: StateFlow<Set<String>> = repository.verifiedContacts
    val contactTags: StateFlow<Map<String, String>> = repository.contactTags

    // ---- Game invite management (delegated) ----
    private val gameInviteManager = GameInviteManager(
        repository = repository,
        scope = viewModelScope,
        contactsProvider = { contacts.value },
    )
    val pendingInvite: StateFlow<MeshContact?> = gameInviteManager.pendingInvite
    val pendingChessInvite: StateFlow<MeshContact?> = gameInviteManager.pendingChessInvite
    val pendingTttInvite: StateFlow<MeshContact?> = gameInviteManager.pendingTttInvite
    val pendingBshInvite: StateFlow<MeshContact?> = gameInviteManager.pendingBshInvite

    fun clearPendingInvite() = gameInviteManager.clearPendingInvite()
    fun clearPendingChessInvite() = gameInviteManager.clearPendingChessInvite()
    fun clearPendingTttInvite() = gameInviteManager.clearPendingTttInvite()
    fun clearPendingBshInvite() = gameInviteManager.clearPendingBshInvite()

    // ---- SOS management (delegated) ----
    private val sosManager = SosManager(
        repository = repository,
        prefs = prefs,
        scope = viewModelScope,
        isStealthMode = { _isStealthMode.value },
    )
    val isSosActive: StateFlow<Boolean> = sosManager.isSosActive

    /** Emitted when a send operation fails — collect in a LaunchedEffect to show a Snackbar. */
    private val _sendError = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val sendError: SharedFlow<String> = _sendError

    /** Login result — collect in a LaunchedEffect to show result. */
    val loginResult: SharedFlow<Pair<Boolean, String>> = repository.loginResult

    /** Contacts currently typing — set of pubKeyHex, auto-cleared after timeout. */
    private val _typingContacts = MutableStateFlow<Set<String>>(emptySet())
    val typingContacts: StateFlow<Set<String>> = _typingContacts

    /** Search results from [searchMessages]. */
    private val _searchResults = MutableStateFlow<List<ChatMessage>>(emptyList())
    val searchResults: StateFlow<List<ChatMessage>> = _searchResults

    /** Blocked contacts — set of pubKeyHex; messages from these nodes are silently dropped. */
    private val _blockedContacts = MutableStateFlow(prefs.blockedContacts)
    val blockedContacts: StateFlow<Set<String>> = _blockedContacts

    // Thread-safe typing debounce map
    private val typingJobs = ConcurrentHashMap<String, Job>()

    /** Stealth mode — suppresses outgoing adverts so the node is invisible to others. */
    private val _isStealthMode = MutableStateFlow(prefs.stealthMode)
    val isStealthMode: StateFlow<Boolean> = _isStealthMode

    /** Persistent GPS location sharing — includes stored lat/lon in all outgoing adverts. */
    private val _isLocationSharing = MutableStateFlow(prefs.shareLocation)
    val isLocationSharing: StateFlow<Boolean> = _isLocationSharing
    private var sharedLat: Double? = null
    private var sharedLon: Double? = null

    /** Emoji reactions — Map<originalTimestampMs, Map<senderKey, emoji>>. */
    private val _reactions = MutableStateFlow<Map<Long, Map<String, String>>>(emptyMap())
    val reactions: StateFlow<Map<Long, Map<String, String>>> = _reactions

    /** Ping RTT results from the mesh — collect to show in UI. */
    val pingResults: SharedFlow<Pair<String, Int>> = repository.pingResults

    init {
        viewModelScope.launch {
            repository.typingEvents.collect { senderHex ->
                _typingContacts.value = _typingContacts.value + senderHex
                delay(MeshiConfig.TYPING_INDICATOR_TTL_MS)
                _typingContacts.value = _typingContacts.value - senderHex
            }
        }
        viewModelScope.launch {
            repository.reactionEvents.collect { (senderKey, originalTs, emoji) ->
                val current = _reactions.value.toMutableMap()
                val forMsg = (current[originalTs] ?: emptyMap()).toMutableMap()
                forMsg[senderKey] = emoji
                current[originalTs] = forMsg
                _reactions.value = current
            }
        }
    }

    /** All messages for the public channel (idx 0). */
    val publicMessages: StateFlow<List<ChatMessage>> = repository.messages
        .map { list -> list.filter { it.channelIdx == 0 } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** All DM messages (channelIdx == -1). */
    val directMessages: StateFlow<List<ChatMessage>> = repository.messages
        .map { list -> list.filter { it.channelIdx == -1 } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Messages for a given channel index as a hot Flow. */
    fun messagesForChannel(idx: Int): Flow<List<ChatMessage>> =
        repository.messages.map { list -> list.filter { it.channelIdx == idx } }

    // ---- Sending ----

    fun sendPublicMessage(text: String) {
        viewModelScope.launch {
            if (!repository.sendChannelMessage(text, channelIdx = 0))
                _sendError.emit("Failed to send message")
        }
    }

    fun sendChannelMessage(text: String, channelIdx: Int) {
        viewModelScope.launch {
            if (!repository.sendChannelMessage(text, channelIdx))
                _sendError.emit("Failed to send message to channel")
        }
    }

    fun sendDirect(contact: MeshContact, text: String) {
        viewModelScope.launch {
            if (!repository.sendDirectMessage(contact, text))
                _sendError.emit("Failed to send message to ${contact.name.ifBlank { contact.displayId }}")
        }
    }

    fun sendAdvert(flood: Boolean = false, lat: Double? = null, lon: Double? = null) {
        if (_isStealthMode.value) return
        val effectiveLat = lat ?: if (_isLocationSharing.value) sharedLat else null
        val effectiveLon = lon ?: if (_isLocationSharing.value) sharedLon else null
        viewModelScope.launch { repository.sendAdvert(flood, effectiveLat, effectiveLon) }
    }

    fun setLocationSharing(enabled: Boolean, lat: Double? = null, lon: Double? = null) {
        _isLocationSharing.value = enabled
        prefs.shareLocation = enabled
        sharedLat = if (enabled) lat else null
        sharedLon = if (enabled) lon else null
    }

    fun sendReaction(msg: ChatMessage, emoji: String) {
        if (msg.isSentByMe) return
        viewModelScope.launch {
            val text = "[REACT]${msg.timestampMs}:$emoji"
            val success = if (msg.channelIdx == -1) {
                val contact = contacts.value.find { it.displayId == msg.senderDisplayId }
                    ?: return@launch
                repository.sendGameDirect(contact, text)
            } else {
                repository.sendGameChannel(text, msg.channelIdx)
            }
            if (success) {
                val selfId = selfInfo.value?.displayId ?: "me"
                val current = _reactions.value.toMutableMap()
                val forMsg = (current[msg.timestampMs] ?: emptyMap()).toMutableMap()
                forMsg[selfId] = emoji
                current[msg.timestampMs] = forMsg
                _reactions.value = current
            }
        }
    }

    fun pingContact(contact: MeshContact) {
        viewModelScope.launch {
            if (!repository.sendPing(contact))
                _sendError.emit("Ping failed")
        }
    }

    fun toggleStealth() {
        val active = !_isStealthMode.value
        _isStealthMode.value = active
        prefs.stealthMode = active
    }

    fun toggleSos() = sosManager.toggle()

    /** Notify remote that we are typing (debounced per-contact). */
    fun notifyTyping(contact: MeshContact) {
        val key = contact.displayId
        val existing = typingJobs[key]
        if (existing?.isActive == true) return
        typingJobs[key] = viewModelScope.launch {
            repository.sendTypingIndicator(contact)
            delay(MeshiConfig.TYPING_SEND_DEBOUNCE_MS)
            typingJobs.remove(key)
        }
    }

    // ---- Contacts ----

    fun saveContact(contact: MeshContact) {
        viewModelScope.launch { repository.saveContact(contact) }
    }

    fun refreshContacts() {
        viewModelScope.launch { repository.refreshContacts() }
    }

    fun setNickname(pubKeyHex: String, nickname: String) {
        viewModelScope.launch { repository.setNickname(pubKeyHex, nickname) }
    }

    fun setVerified(pubKeyHex: String, verified: Boolean) {
        viewModelScope.launch { repository.setVerified(pubKeyHex, verified) }
    }

    fun setTag(pubKeyHex: String, tag: String) {
        viewModelScope.launch { repository.setTag(pubKeyHex, tag) }
    }

    // ---- Channels ----

    fun fetchAllChannels() {
        viewModelScope.launch { repository.fetchAllChannels() }
    }

    fun createOrUpdateChannel(idx: Int, name: String, secret: ByteArray? = null): ByteArray {
        val actualSecret = secret ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        viewModelScope.launch { repository.setChannel(idx, name, actualSecret) }
        return actualSecret
    }

    fun deleteChannel(idx: Int) {
        viewModelScope.launch { repository.deleteChannel(idx) }
    }

    // ---- Node stats ----

    fun fetchNodeStats() {
        viewModelScope.launch { repository.fetchNodeStats() }
    }

    fun fetchRadioStats() {
        viewModelScope.launch { repository.fetchRadioStats() }
    }

    fun sendLogin(password: String) {
        viewModelScope.launch {
            if (!repository.sendLogin(password))
                _sendError.emit("Failed to send login request")
        }
    }

    // ---- Block list ----

    /** Permanent — contact is suppressed even if they rebroadcast. */
    fun deleteContact(pubKeyHex: String) {
        prefs.deletedContacts = prefs.deletedContacts + pubKeyHex
        viewModelScope.launch { repository.deleteContact(pubKeyHex) }
    }

    /** Soft remove — hides contact now, but they reappear if they broadcast again. */
    fun softDeleteContact(pubKeyHex: String) {
        viewModelScope.launch { repository.deleteContact(pubKeyHex) }
    }

    fun blockContact(pubKeyHex: String) {
        val updated = _blockedContacts.value + pubKeyHex
        _blockedContacts.value = updated
        prefs.blockedContacts = updated
    }

    fun unblockContact(pubKeyHex: String) {
        val updated = _blockedContacts.value - pubKeyHex
        _blockedContacts.value = updated
        prefs.blockedContacts = updated
    }

    // ---- Message search ----

    fun searchMessages(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            val raw = repository.searchMessages(query)
            _searchResults.value = raw
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    // ---- Export ----

    /** Returns a formatted text export of all messages in [channelIdx] (or -1 for DMs). */
    fun exportMessages(channelIdx: Int): String {
        val msgs = repository.messages.value.filter { it.channelIdx == channelIdx }
        return msgs.joinToString("\n") { msg ->
            val from = if (msg.isSentByMe) "Me" else msg.senderName.ifBlank { msg.senderDisplayId }
            "[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestampMs))}] $from: ${msg.text}"
        }
    }

    /** Live SNR samples for a contact, newest-first, up to 30 entries. */
    fun snrSamplesForContact(contactId: String): Flow<List<Float>> =
        repository.snrSampleDao.observeForContact(contactId, limit = 30)
            .map { list -> list.map { it.snrDb } }

    fun disconnect() = repository.disconnect()
}
