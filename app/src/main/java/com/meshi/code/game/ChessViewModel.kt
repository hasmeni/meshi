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

enum class ChessGamePhase {
    AWAITING_ACCEPT, INVITED, PLAYING, GAME_OVER,
}

data class ChessUiState(
    val gameState: ChessState = initialChessState(),
    val phase: ChessGamePhase = ChessGamePhase.AWAITING_ACCEPT,
    val amWhite: Boolean = true,    // challenger = white
    val selectedSquare: Int? = null,
    val legalDestinations: Set<Int> = emptySet(),
    val statusMsg: String = "",
)

/**
 * Manages one Chess session over the mesh.
 * [isChallenging] = true → we sent the invite (we are WHITE).
 */
class ChessViewModel(
    private val repository: MeshRepository,
    val opponent: MeshContact,
    isChallenging: Boolean,
) : java.io.Closeable {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow(
        ChessUiState(
            phase = if (isChallenging) ChessGamePhase.AWAITING_ACCEPT else ChessGamePhase.INVITED,
            amWhite = isChallenging,
            statusMsg = if (isChallenging)
                "Invite sent. Waiting for ${opponentLabel()}…"
            else
                "${opponentLabel()} challenges you to Chess!",
        )
    )
    val uiState: StateFlow<ChessUiState> = _uiState

    init {
        if (isChallenging) scope.launch { sendMsg("INVITE") }

        scope.launch {
            repository.gameMessages
                .filter { (senderPrefix, _) -> senderPrefix.contentEquals(opponent.pubKeyPrefix) }
                .collect { (_, text) ->
                    if (text.startsWith(CHESS_PREFIX)) handleIncoming(text.removePrefix(CHESS_PREFIX))
                }
        }
    }

    private fun handleIncoming(cmd: String) {
        val s = _uiState.value
        when {
            cmd == "INVITE" -> { /* ignore extra invites */ }
            cmd == "ACCEPT" && s.phase == ChessGamePhase.AWAITING_ACCEPT -> {
                _uiState.value = s.copy(
                    phase = ChessGamePhase.PLAYING,
                    statusMsg = "Game on! White goes first",
                )
            }
            cmd == "DECLINE" && s.phase == ChessGamePhase.AWAITING_ACCEPT -> {
                _uiState.value = s.copy(
                    phase = ChessGamePhase.GAME_OVER,
                    statusMsg = "${opponentLabel()} declined.",
                )
            }
            cmd.startsWith("MOVE:") && s.phase == ChessGamePhase.PLAYING -> {
                val notation = cmd.removePrefix("MOVE:")
                val move = parseChessMove(notation, s.gameState.board, s.gameState.isWhiteTurn)
                    ?: return
                val newGame = applyChessMove(s.gameState, move)
                val updated = s.copy(gameState = newGame, selectedSquare = null, legalDestinations = emptySet())
                _uiState.value = updated.copy(statusMsg = buildStatusMsg(updated))
                if (newGame.isGameOver) saveGameRecord(newGame)
            }
            cmd == "RESIGN" && s.phase == ChessGamePhase.PLAYING -> {
                _uiState.value = s.copy(
                    phase = ChessGamePhase.GAME_OVER,
                    statusMsg = "${opponentLabel()} resigned — You win!",
                )
                scope.launch {
                    repository.gameRecordDao.insert(
                        GameRecordEntity(
                            gameType = "CHESS", opponentId = opponent.displayId,
                            opponentName = opponentLabel(), result = "WIN",
                            timestampMs = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
    }

    fun acceptInvite() {
        if (_uiState.value.phase != ChessGamePhase.INVITED) return
        scope.launch { sendMsg("ACCEPT") }
        _uiState.value = _uiState.value.copy(
            phase = ChessGamePhase.PLAYING,
            statusMsg = "${opponentLabel()} (White) goes first",
        )
    }

    fun declineInvite() {
        if (_uiState.value.phase != ChessGamePhase.INVITED) return
        scope.launch { sendMsg("DECLINE") }
        _uiState.value = _uiState.value.copy(
            phase = ChessGamePhase.GAME_OVER,
            statusMsg = "Challenge declined.",
        )
    }

    fun onSquareTapped(idx: Int) {
        val s = _uiState.value
        if (s.phase != ChessGamePhase.PLAYING) return
        val myTurn = (s.amWhite && s.gameState.isWhiteTurn) || (!s.amWhite && !s.gameState.isWhiteTurn)
        if (!myTurn) return

        val piece = s.gameState.board[idx]
        val isMyPiece = if (s.amWhite) piece.isWhite else piece.isBlack

        when {
            // Deselect
            s.selectedSquare == idx -> {
                _uiState.value = s.copy(selectedSquare = null, legalDestinations = emptySet())
            }
            // Select my piece
            isMyPiece -> {
                val dests = s.gameState.legalMoves.filter { it.from == idx }.map { it.to }.toSet()
                _uiState.value = s.copy(selectedSquare = idx, legalDestinations = dests)
            }
            // Execute move if destination is valid
            s.selectedSquare != null && idx in s.legalDestinations -> {
                val move = s.gameState.legalMoves.firstOrNull { it.from == s.selectedSquare && it.to == idx }
                    ?: return
                val newGame = applyChessMove(s.gameState, move)
                scope.launch { sendMsg("MOVE:${move.toNotation()}") }
                val updated = s.copy(
                    gameState = newGame, selectedSquare = null, legalDestinations = emptySet()
                )
                _uiState.value = updated.copy(statusMsg = buildStatusMsg(updated))
                if (newGame.isGameOver) saveGameRecord(newGame)
            }
        }
    }

    fun resign() {
        if (_uiState.value.phase != ChessGamePhase.PLAYING) return
        scope.launch { sendMsg("RESIGN") }
        _uiState.value = _uiState.value.copy(
            phase = ChessGamePhase.GAME_OVER,
            statusMsg = "You resigned.",
        )
        scope.launch {
            repository.gameRecordDao.insert(
                GameRecordEntity(
                    gameType = "CHESS", opponentId = opponent.displayId,
                    opponentName = opponentLabel(), result = "LOSS",
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun saveGameRecord(game: ChessState) {
        val s = _uiState.value
        val result = when (game.winner) {
            true  -> if (s.amWhite) "WIN" else "LOSS"
            false -> if (!s.amWhite) "WIN" else "LOSS"
            null  -> "DRAW"
        }
        _uiState.value = s.copy(
            phase = ChessGamePhase.GAME_OVER,
            statusMsg = "Game over — ${when (result) { "WIN" -> "You win!"; "LOSS" -> "You lose."; else -> "Draw!" }}",
        )
        scope.launch {
            repository.gameRecordDao.insert(
                GameRecordEntity(
                    gameType = "CHESS", opponentId = opponent.displayId,
                    opponentName = opponentLabel(), result = result,
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun buildStatusMsg(s: ChessUiState): String {
        if (s.gameState.isGameOver) return "Game over"
        val myTurn = (s.amWhite && s.gameState.isWhiteTurn) || (!s.amWhite && !s.gameState.isWhiteTurn)
        return if (myTurn) "Your turn (${if (s.amWhite) "White" else "Black"})"
               else "${opponentLabel()}'s turn (${if (s.gameState.isWhiteTurn) "White" else "Black"})"
    }

    private fun opponentLabel() = opponent.name.ifBlank { opponent.displayId }
    private suspend fun sendMsg(cmd: String) = repository.sendGameDirect(opponent, "$CHESS_PREFIX$cmd")

    override fun close() = scope.cancel()
}
