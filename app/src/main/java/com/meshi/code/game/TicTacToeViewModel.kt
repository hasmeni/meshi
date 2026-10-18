package com.meshi.code.game

import com.meshi.code.db.GameRecordEntity
import com.meshi.code.protocol.MeshContact
import com.meshi.code.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

const val TTT_PREFIX = "[TTT]"

enum class TttGamePhase {
    AWAITING_ACCEPT, INVITED, PLAYING, GAME_OVER,
}

data class TttUiState(
    val gameState: TttState = TttState(),
    val phase: TttGamePhase = TttGamePhase.AWAITING_ACCEPT,
    val myPiece: TttPiece = TttPiece.X,  // challenger = X
    val statusMsg: String = "",
)

/**
 * Manages one Tic-tac-toe session over the mesh.
 * [isChallenging] = true → we sent the invite (we are X).
 */
class TicTacToeViewModel(
    private val repository: MeshRepository,
    val opponent: MeshContact,
    isChallenging: Boolean,
) : java.io.Closeable {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow(
        TttUiState(
            phase = if (isChallenging) TttGamePhase.AWAITING_ACCEPT else TttGamePhase.INVITED,
            myPiece = if (isChallenging) TttPiece.X else TttPiece.O,
            statusMsg = if (isChallenging)
                "Invite sent. Waiting for ${opponentLabel()}…"
            else
                "${opponentLabel()} challenges you to Tic-tac-toe!",
        )
    )
    val uiState: StateFlow<TttUiState> = _uiState

    init {
        if (isChallenging) scope.launch { sendMsg("INVITE") }

        scope.launch {
            repository.gameMessages
                .filter { (senderPrefix, _) -> senderPrefix.contentEquals(opponent.pubKeyPrefix) }
                .collect { (_, text) ->
                    if (text.startsWith(TTT_PREFIX)) handleIncoming(text.removePrefix(TTT_PREFIX))
                }
        }
    }

    private fun handleIncoming(cmd: String) {
        val s = _uiState.value
        when {
            cmd == "INVITE" -> { /* ignore extra invites */ }
            cmd == "ACCEPT" && s.phase == TttGamePhase.AWAITING_ACCEPT -> {
                _uiState.value = s.copy(
                    phase = TttGamePhase.PLAYING,
                    statusMsg = "Game on! Your turn (X)",
                )
            }
            cmd == "DECLINE" && s.phase == TttGamePhase.AWAITING_ACCEPT -> {
                _uiState.value = s.copy(
                    phase = TttGamePhase.GAME_OVER,
                    statusMsg = "${opponentLabel()} declined.",
                )
            }
            cmd.startsWith("MOVE:") && s.phase == TttGamePhase.PLAYING -> {
                val idx = cmd.removePrefix("MOVE:").toIntOrNull() ?: return
                val newGame = applyTttMove(s.gameState, idx) ?: return
                val updated = s.copy(gameState = newGame)
                _uiState.value = updated.copy(statusMsg = buildStatusMsg(updated))
                if (newGame.isGameOver) saveGameRecord(newGame)
            }
            cmd == "RESIGN" && s.phase == TttGamePhase.PLAYING -> {
                _uiState.value = s.copy(
                    phase = TttGamePhase.GAME_OVER,
                    statusMsg = "${opponentLabel()} resigned — You win!",
                )
                scope.launch {
                    repository.gameRecordDao.insert(
                        GameRecordEntity(
                            gameType = "TTT", opponentId = opponent.displayId,
                            opponentName = opponentLabel(), result = "WIN",
                            timestampMs = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
    }

    fun acceptInvite() {
        if (_uiState.value.phase != TttGamePhase.INVITED) return
        scope.launch { sendMsg("ACCEPT") }
        _uiState.value = _uiState.value.copy(
            phase = TttGamePhase.PLAYING,
            statusMsg = "${opponentLabel()} (X) goes first",
        )
    }

    fun declineInvite() {
        if (_uiState.value.phase != TttGamePhase.INVITED) return
        scope.launch { sendMsg("DECLINE") }
        _uiState.value = _uiState.value.copy(
            phase = TttGamePhase.GAME_OVER,
            statusMsg = "Challenge declined.",
        )
    }

    fun onCellTapped(idx: Int) {
        val s = _uiState.value
        if (s.phase != TttGamePhase.PLAYING) return
        if (s.gameState.turn != s.myPiece) return
        val newGame = applyTttMove(s.gameState, idx) ?: return
        scope.launch { sendMsg("MOVE:$idx") }
        val updated = s.copy(gameState = newGame)
        _uiState.value = updated.copy(statusMsg = buildStatusMsg(updated))
        if (newGame.isGameOver) saveGameRecord(newGame)
    }

    fun resign() {
        if (_uiState.value.phase != TttGamePhase.PLAYING) return
        scope.launch { sendMsg("RESIGN") }
        _uiState.value = _uiState.value.copy(
            phase = TttGamePhase.GAME_OVER,
            statusMsg = "You resigned.",
        )
        scope.launch {
            repository.gameRecordDao.insert(
                GameRecordEntity(
                    gameType = "TTT", opponentId = opponent.displayId,
                    opponentName = opponentLabel(), result = "LOSS",
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun saveGameRecord(game: TttState) {
        val s = _uiState.value
        val result = when (game.winner) {
            s.myPiece -> "WIN"
            null      -> "DRAW"
            else      -> "LOSS"
        }
        val msg = when (result) {
            "WIN"  -> "You win!"
            "DRAW" -> "It's a draw!"
            else   -> "You lose."
        }
        _uiState.value = s.copy(
            phase = TttGamePhase.GAME_OVER,
            statusMsg = "Game over — $msg",
        )
        scope.launch {
            repository.gameRecordDao.insert(
                GameRecordEntity(
                    gameType = "TTT", opponentId = opponent.displayId,
                    opponentName = opponentLabel(), result = result,
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun buildStatusMsg(s: TttUiState): String {
        if (s.gameState.isGameOver) return "Game over"
        return if (s.gameState.turn == s.myPiece) "Your turn (${s.myPiece.name})"
               else "${opponentLabel()}'s turn (${s.gameState.turn.name})"
    }

    private fun opponentLabel() = opponent.name.ifBlank { opponent.displayId }
    private suspend fun sendMsg(cmd: String) = repository.sendGameDirect(opponent, "$TTT_PREFIX$cmd")

    override fun close() = scope.cancel()
}
