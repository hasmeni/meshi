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

enum class BshPhase {
    INVITE_SENT, INVITE_RECEIVED,
    PLACEMENT, WAITING_READY,
    MY_TURN, OPPONENT_TURN,
    GAME_OVER,
}

data class BshUiState(
    val phase: BshPhase,
    val placedShips: List<BshShip> = emptyList(),
    val opponentShots: Set<Pair<Int, Int>> = emptySet(),
    val shotsFired: Map<Pair<Int, Int>, Boolean> = emptyMap(),    // pos → isHit
    val sunkCells: Set<Pair<Int, Int>> = emptySet(),              // cells confirmed sunk on opponent board
    val opponentSunkCount: Int = 0,
    val selectedTemplateIdx: Int = 0,
    val horizontal: Boolean = true,
    val statusMsg: String = "",
    val gameResult: String? = null,   // "WIN" or "LOSE"
)

class BattleshipViewModel(
    private val repository: MeshRepository,
    val opponent: MeshContact,
    private val isChallenging: Boolean,
) : java.io.Closeable {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val opponentLabel get() = opponent.name.ifBlank { opponent.displayId }
    private var opponentReady = false
    private var iReady = false

    private val _state = MutableStateFlow(
        BshUiState(
            phase = if (isChallenging) BshPhase.INVITE_SENT else BshPhase.INVITE_RECEIVED,
            statusMsg = if (isChallenging)
                "Waiting for $opponentLabel to accept…"
            else
                "$opponentLabel challenges you to Battleship!",
        )
    )
    val state: StateFlow<BshUiState> = _state

    init {
        if (isChallenging) scope.launch { send("INVITE") }

        scope.launch {
            repository.gameMessages
                .filter { (prefix, _) -> prefix.contentEquals(opponent.pubKeyPrefix) }
                .collect { (_, text) ->
                    if (text.startsWith(BSH_PREFIX)) handle(text.removePrefix(BSH_PREFIX))
                }
        }
    }

    private fun handle(cmd: String) {
        val s = _state.value
        when {
            cmd == "ACCEPT" && s.phase == BshPhase.INVITE_SENT -> {
                _state.value = s.copy(
                    phase = BshPhase.PLACEMENT,
                    statusMsg = "Place your ships!",
                )
            }
            cmd == "DECLINE" && s.phase == BshPhase.INVITE_SENT -> {
                _state.value = s.copy(
                    phase = BshPhase.GAME_OVER,
                    statusMsg = "$opponentLabel declined.",
                )
            }
            cmd == "READY" -> {
                opponentReady = true
                checkBothReady()
            }
            cmd.startsWith("SHOT:") -> {
                val parts = cmd.removePrefix("SHOT:").split(",")
                val r = parts.getOrNull(0)?.toIntOrNull() ?: return
                val c = parts.getOrNull(1)?.toIntOrNull() ?: return
                processIncomingShot(r, c)
            }
            cmd.startsWith("RESULT:") -> {
                processResult(cmd.removePrefix("RESULT:"))
            }
            cmd == "RESIGN" && s.phase != BshPhase.GAME_OVER -> {
                _state.value = s.copy(
                    phase = BshPhase.GAME_OVER,
                    gameResult = "WIN",
                    statusMsg = "$opponentLabel resigned — You win!",
                )
                saveRecord("WIN")
            }
        }
    }

    // ---- Invite phase ----

    fun acceptInvite() {
        val s = _state.value
        if (s.phase != BshPhase.INVITE_RECEIVED) return
        scope.launch { send("ACCEPT") }
        _state.value = s.copy(phase = BshPhase.PLACEMENT, statusMsg = "Place your ships!")
    }

    fun declineInvite() {
        if (_state.value.phase != BshPhase.INVITE_RECEIVED) return
        scope.launch { send("DECLINE") }
        _state.value = _state.value.copy(phase = BshPhase.GAME_OVER, statusMsg = "Challenge declined.")
    }

    // ---- Placement phase ----

    fun selectTemplate(idx: Int) {
        _state.value = _state.value.copy(selectedTemplateIdx = idx)
    }

    fun toggleOrientation() {
        _state.value = _state.value.copy(horizontal = !_state.value.horizontal)
    }

    fun tapCell(row: Int, col: Int) {
        val s = _state.value
        if (s.phase != BshPhase.PLACEMENT) return
        val remaining = BSH_SHIP_TEMPLATES.filterIndexed { i, _ ->
            s.placedShips.none { it.name == BSH_SHIP_TEMPLATES[i].name }
        }
        val template = remaining.getOrNull(s.selectedTemplateIdx) ?: remaining.firstOrNull() ?: return
        if (!canPlaceShip(s.placedShips, row, col, template.size, s.horizontal)) return
        val ship = BshShip(template.name, template.size, row, col, s.horizontal)
        val newPlaced = s.placedShips + ship
        // Advance selected index to next unplaced
        val newRemaining = BSH_SHIP_TEMPLATES.filterIndexed { i, _ ->
            newPlaced.none { it.name == BSH_SHIP_TEMPLATES[i].name }
        }
        _state.value = s.copy(
            placedShips = newPlaced,
            selectedTemplateIdx = 0,
            statusMsg = if (newRemaining.isEmpty()) "All ships placed! Tap Ready."
                        else "Place your ${newRemaining.first().name} (${newRemaining.first().size})",
        )
    }

    fun removeLastShip() {
        val s = _state.value
        if (s.phase != BshPhase.PLACEMENT || s.placedShips.isEmpty()) return
        _state.value = s.copy(placedShips = s.placedShips.dropLast(1))
    }

    fun confirmReady() {
        val s = _state.value
        if (s.placedShips.size < BSH_SHIP_TEMPLATES.size) return
        scope.launch { send("READY") }
        iReady = true
        _state.value = s.copy(
            phase = BshPhase.WAITING_READY,
            statusMsg = "Waiting for $opponentLabel to finish placing…",
        )
        checkBothReady()
    }

    private fun checkBothReady() {
        if (!iReady || !opponentReady) return
        val s = _state.value
        if (isChallenging) {
            _state.value = s.copy(phase = BshPhase.MY_TURN, statusMsg = "Your turn — fire!")
        } else {
            _state.value = s.copy(phase = BshPhase.OPPONENT_TURN, statusMsg = "Waiting for $opponentLabel…")
        }
    }

    // ---- Game phase ----

    fun fireShot(row: Int, col: Int) {
        val s = _state.value
        if (s.phase != BshPhase.MY_TURN) return
        val pos = row to col
        if (pos in s.shotsFired) return
        scope.launch { send("SHOT:$row,$col") }
        _state.value = s.copy(
            phase = BshPhase.OPPONENT_TURN,
            statusMsg = "Fired at (${row + 1}, ${col + 1}) — waiting for result…",
        )
    }

    fun resign() {
        val s = _state.value
        if (s.phase != BshPhase.MY_TURN && s.phase != BshPhase.OPPONENT_TURN) return
        scope.launch { send("RESIGN") }
        _state.value = s.copy(
            phase = BshPhase.GAME_OVER,
            gameResult = "LOSE",
            statusMsg = "You resigned.",
        )
        saveRecord("LOSS")
    }

    private fun processIncomingShot(row: Int, col: Int) {
        val s = _state.value
        val ships = s.placedShips.toMutableList()
        var resultStr = "MISS"

        for (i in ships.indices) {
            val ship = ships[i]
            val cellIdx = ship.cells.indexOfFirst { (r, c) -> r == row && c == col }
            if (cellIdx >= 0) {
                val updated = ship.copy(hits = ship.hits + cellIdx)
                ships[i] = updated
                resultStr = if (updated.isSunk) "SUNK:${ship.name}" else "HIT"
                break
            }
        }

        val newOpponentShots = s.opponentShots + (row to col)
        val allSunk = ships.all { it.isSunk }

        scope.launch { send("RESULT:$row,$col,$resultStr") }

        if (allSunk) {
            _state.value = s.copy(
                placedShips = ships,
                opponentShots = newOpponentShots,
                phase = BshPhase.GAME_OVER,
                gameResult = "LOSE",
                statusMsg = "All your ships have been sunk. You lose!",
            )
            saveRecord("LOSS")
        } else {
            _state.value = s.copy(
                placedShips = ships,
                opponentShots = newOpponentShots,
                phase = BshPhase.MY_TURN,
                statusMsg = "Your turn — fire!",
            )
        }
    }

    private fun processResult(payload: String) {
        // "row,col,MISS" | "row,col,HIT" | "row,col,SUNK:ShipName"
        val parts = payload.split(",", limit = 3)
        if (parts.size < 3) return
        val r = parts[0].toIntOrNull() ?: return
        val c = parts[1].toIntOrNull() ?: return
        val resultStr = parts[2]
        val isHit = !resultStr.startsWith("MISS")
        val pos = r to c

        val s = _state.value
        var newSunkCells = s.sunkCells
        var newSunkCount = s.opponentSunkCount
        var statusMsg: String

        when {
            resultStr.startsWith("SUNK:") -> {
                val shipName = resultStr.removePrefix("SUNK:")
                newSunkCount++
                statusMsg = "You sunk their $shipName!"
                // Mark all hit cells for that ship as sunk (we only know this one for certain)
                newSunkCells = newSunkCells + pos
                // Find any previously marked HIT cells that belong to this ship
                // We can't know which without the ship layout, so just mark this cell sunk
            }
            isHit -> statusMsg = "Hit at row ${r + 1}, col ${c + 1}!"
            else  -> statusMsg = "Miss at row ${r + 1}, col ${c + 1}."
        }

        val newShots = s.shotsFired + (pos to isHit)

        if (newSunkCount >= BSH_SHIP_TEMPLATES.size) {
            _state.value = s.copy(
                shotsFired = newShots,
                sunkCells = newSunkCells,
                opponentSunkCount = newSunkCount,
                phase = BshPhase.GAME_OVER,
                gameResult = "WIN",
                statusMsg = "All enemy ships sunk — You win!",
            )
            saveRecord("WIN")
            return
        }

        _state.value = s.copy(
            shotsFired = newShots,
            sunkCells = newSunkCells,
            opponentSunkCount = newSunkCount,
            phase = BshPhase.OPPONENT_TURN,
            statusMsg = "$statusMsg  Waiting for $opponentLabel…",
        )
    }

    private fun saveRecord(result: String) {
        scope.launch {
            repository.gameRecordDao.insert(
                GameRecordEntity(
                    gameType = "BATTLESHIP",
                    opponentId = opponent.displayId,
                    opponentName = opponentLabel,
                    result = result,
                    timestampMs = System.currentTimeMillis(),
                )
            )
        }
    }

    private suspend fun send(cmd: String) = repository.sendGameDirect(opponent, "$BSH_PREFIX$cmd")

    override fun close() = scope.cancel()
}
