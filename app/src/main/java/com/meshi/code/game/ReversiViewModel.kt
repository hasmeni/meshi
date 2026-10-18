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

/** Prefix that distinguishes Reversi protocol messages from normal DMs. */
const val GAME_PREFIX = "[RV]"

enum class GamePhase {
    /** We sent the invite and are waiting for the opponent to accept. */
    AWAITING_ACCEPT,
    /** We received an invite and must respond. */
    INVITED,
    /** Game is in progress. */
    PLAYING,
    /** Game ended (win / lose / draw / resign / decline). */
    GAME_OVER,
}

data class ReversiUiState(
    val gameState: ReversiState = ReversiState.initial(),
    val phase: GamePhase = GamePhase.AWAITING_ACCEPT,
    val myColor: Piece = Piece.BLACK,
    val statusMsg: String = "",
)

/**
 * Manages one Reversi session against [opponent].
 *
 * Not a Jetpack ViewModel — create with [remember] and clean up with [DisposableEffect].
 * [isChallenging] = true  → we sent the invite (we are BLACK).
 * [isChallenging] = false → we received the invite (we are WHITE).
 */
class ReversiViewModel(
    private val repository: MeshRepository,
    val opponent: MeshContact,
    isChallenging: Boolean,
) : java.io.Closeable {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow(
        ReversiUiState(
            phase = if (isChallenging) GamePhase.AWAITING_ACCEPT else GamePhase.INVITED,
            myColor = if (isChallenging) Piece.BLACK else Piece.WHITE,
            statusMsg = if (isChallenging)
                "Invite sent. Waiting for ${opponentLabel()}…"
            else
                "${opponentLabel()} challenges you to Reversi!",
        )
    )
    val uiState: StateFlow<ReversiUiState> = _uiState

    init {
        if (isChallenging) scope.launch { sendMsg("INVITE") }

        scope.launch {
            repository.gameMessages
                .filter { (senderPrefix, _) -> senderPrefix.contentEquals(opponent.pubKeyPrefix) }
                .collect { (_, text) -> handleIncoming(text) }
        }
    }

    private fun handleIncoming(text: String) {
        val cmd = text.removePrefix(GAME_PREFIX)
        val s = _uiState.value
        when {
            cmd == "ACCEPT" && s.phase == GamePhase.AWAITING_ACCEPT -> {
                _uiState.value = s.copy(
                    phase = GamePhase.PLAYING,
                    statusMsg = "Game on! Your turn (BLACK)",
                )
            }
            cmd == "DECLINE" && s.phase == GamePhase.AWAITING_ACCEPT -> {
                _uiState.value = s.copy(
                    phase = GamePhase.GAME_OVER,
                    statusMsg = "${opponentLabel()} declined.",
                )
            }
            cmd.startsWith("MOVE:") && s.phase == GamePhase.PLAYING -> {
                val parts = cmd.removePrefix("MOVE:").split(",")
                val r = parts.getOrNull(0)?.toIntOrNull() ?: return
                val c = parts.getOrNull(1)?.toIntOrNull() ?: return
                val newGame = applyMove(s.gameState, r, c) ?: return
                val updated = s.copy(gameState = newGame)
                _uiState.value = updated.copy(statusMsg = statusMsg(updated))
                if (newGame.isGameOver) finishGame()
            }
            cmd == "RESIGN" && s.phase == GamePhase.PLAYING -> {
                _uiState.value = s.copy(
                    phase = GamePhase.GAME_OVER,
                    statusMsg = "${opponentLabel()} resigned — You win!",
                )
            }
        }
    }

    fun acceptInvite() {
        if (_uiState.value.phase != GamePhase.INVITED) return
        scope.launch { sendMsg("ACCEPT") }
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.PLAYING,
            statusMsg = "${opponentLabel()} goes first (BLACK)",
        )
    }

    fun declineInvite() {
        if (_uiState.value.phase != GamePhase.INVITED) return
        scope.launch { sendMsg("DECLINE") }
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.GAME_OVER,
            statusMsg = "Challenge declined.",
        )
    }

    fun makeMove(row: Int, col: Int) {
        val s = _uiState.value
        if (s.phase != GamePhase.PLAYING) return
        if (s.gameState.currentTurn != s.myColor) return
        val newGame = applyMove(s.gameState, row, col) ?: return
        scope.launch { sendMsg("MOVE:$row,$col") }
        val updated = s.copy(gameState = newGame)
        _uiState.value = updated.copy(statusMsg = statusMsg(updated))
        if (newGame.isGameOver) finishGame()
    }

    fun resign() {
        if (_uiState.value.phase != GamePhase.PLAYING) return
        scope.launch { sendMsg("RESIGN") }
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.GAME_OVER,
            statusMsg = "You resigned.",
        )
    }

    private fun finishGame() {
        val s = _uiState.value
        val scoreStr = "${s.gameState.blackCount}-${s.gameState.whiteCount}"
        val result = when (s.gameState.winner) {
            s.myColor -> "WIN"
            null      -> "DRAW"
            else      -> "LOSS"
        }
        val msg = when (result) {
            "WIN"  -> "You win! $scoreStr"
            "DRAW" -> "Draw! $scoreStr"
            else   -> "You lose. $scoreStr"
        }
        _uiState.value = s.copy(phase = GamePhase.GAME_OVER, statusMsg = "Game over — $msg")
        scope.launch {
            repository.gameRecordDao.insert(
                GameRecordEntity(
                    gameType = "REVERSI",
                    opponentId = opponent.displayId,
                    opponentName = opponentLabel(),
                    result = result,
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun statusMsg(s: ReversiUiState): String {
        val gs = s.gameState
        if (gs.isGameOver) return "Game over"
        return if (gs.currentTurn == s.myColor)
            "Your turn (${colorName(s.myColor)})"
        else
            "${opponentLabel()}'s turn (${colorName(gs.currentTurn)})"
    }

    private fun colorName(p: Piece) = p.name.lowercase().replaceFirstChar { it.uppercase() }
    private fun opponentLabel() = opponent.name.ifBlank { opponent.displayId }

    private suspend fun sendMsg(cmd: String) {
        repository.sendGameDirect(opponent, "$GAME_PREFIX$cmd")
    }

    override fun close() = scope.cancel()
}
