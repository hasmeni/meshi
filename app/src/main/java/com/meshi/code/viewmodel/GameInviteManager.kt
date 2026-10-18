package com.meshi.code.viewmodel

import com.meshi.code.game.BSH_PREFIX
import com.meshi.code.game.CHESS_PREFIX
import com.meshi.code.game.GAME_PREFIX
import com.meshi.code.game.TTT_PREFIX
import com.meshi.code.protocol.MeshContact
import com.meshi.code.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages incoming game invites from the mesh.
 *
 * Listens to [MeshRepository.gameMessages] and routes invite messages
 * to the appropriate pending-invite StateFlow so the UI can react.
 */
class GameInviteManager(
    private val repository: MeshRepository,
    private val scope: CoroutineScope,
    private val contactsProvider: () -> List<MeshContact>,
) {
    private val _pendingInvite = MutableStateFlow<MeshContact?>(null)
    val pendingInvite: StateFlow<MeshContact?> = _pendingInvite

    private val _pendingChessInvite = MutableStateFlow<MeshContact?>(null)
    val pendingChessInvite: StateFlow<MeshContact?> = _pendingChessInvite

    private val _pendingTttInvite = MutableStateFlow<MeshContact?>(null)
    val pendingTttInvite: StateFlow<MeshContact?> = _pendingTttInvite

    private val _pendingBshInvite = MutableStateFlow<MeshContact?>(null)
    val pendingBshInvite: StateFlow<MeshContact?> = _pendingBshInvite

    init {
        scope.launch {
            repository.gameMessages.collect { (senderPrefix, text) ->
                val contact = contactsProvider().find {
                    it.pubKeyPrefix.contentEquals(senderPrefix)
                } ?: return@collect
                when (text) {
                    "${GAME_PREFIX}INVITE"  -> _pendingInvite.value = contact
                    "${CHESS_PREFIX}INVITE" -> _pendingChessInvite.value = contact
                    "${TTT_PREFIX}INVITE"   -> _pendingTttInvite.value = contact
                    "${BSH_PREFIX}INVITE"   -> _pendingBshInvite.value = contact
                }
            }
        }
    }

    fun clearPendingInvite() { _pendingInvite.value = null }
    fun clearPendingChessInvite() { _pendingChessInvite.value = null }
    fun clearPendingTttInvite() { _pendingTttInvite.value = null }
    fun clearPendingBshInvite() { _pendingBshInvite.value = null }
}
