package com.meshi.code.game

enum class Piece { EMPTY, BLACK, WHITE }

fun Piece.opponent(): Piece = when (this) {
    Piece.BLACK -> Piece.WHITE
    Piece.WHITE -> Piece.BLACK
    Piece.EMPTY -> Piece.EMPTY
}

data class ReversiState(
    val board: List<List<Piece>>,
    val currentTurn: Piece,
) {
    val blackCount: Int = board.sumOf { row -> row.count { it == Piece.BLACK } }
    val whiteCount: Int = board.sumOf { row -> row.count { it == Piece.WHITE } }
    val validMoves: Set<Pair<Int, Int>> = computeValidMoves(board, currentTurn)
    val isGameOver: Boolean
        get() = validMoves.isEmpty() && computeValidMoves(board, currentTurn.opponent()).isEmpty()
    val winner: Piece?
        get() = if (!isGameOver) null else when {
            blackCount > whiteCount -> Piece.BLACK
            whiteCount > blackCount -> Piece.WHITE
            else -> null
        }

    companion object {
        fun initial() = ReversiState(
            board = List(8) { r ->
                List(8) { c ->
                    when {
                        r == 3 && c == 3 -> Piece.WHITE
                        r == 3 && c == 4 -> Piece.BLACK
                        r == 4 && c == 3 -> Piece.BLACK
                        r == 4 && c == 4 -> Piece.WHITE
                        else -> Piece.EMPTY
                    }
                }
            },
            currentTurn = Piece.BLACK,
        )
    }
}

private val DIRECTIONS = listOf(
    -1 to -1, -1 to 0, -1 to 1,
     0 to -1,            0 to 1,
     1 to -1,  1 to 0,  1 to 1,
)

fun computeValidMoves(board: List<List<Piece>>, turn: Piece): Set<Pair<Int, Int>> {
    if (turn == Piece.EMPTY) return emptySet()
    return buildSet {
        for (r in 0..7) for (c in 0..7) {
            if (board[r][c] == Piece.EMPTY && wouldFlip(board, r, c, turn)) add(r to c)
        }
    }
}

private fun wouldFlip(board: List<List<Piece>>, row: Int, col: Int, turn: Piece): Boolean {
    val opp = turn.opponent()
    for ((dr, dc) in DIRECTIONS) {
        var r = row + dr; var c = col + dc
        var hasOpp = false
        while (r in 0..7 && c in 0..7 && board[r][c] == opp) { r += dr; c += dc; hasOpp = true }
        if (hasOpp && r in 0..7 && c in 0..7 && board[r][c] == turn) return true
    }
    return false
}

/**
 * Returns the new state after applying [row],[col], or null if the move is invalid.
 * Automatically passes the turn if the next player has no valid moves (standard Reversi rule).
 */
fun applyMove(state: ReversiState, row: Int, col: Int): ReversiState? {
    if (row !in 0..7 || col !in 0..7) return null
    if (state.board[row][col] != Piece.EMPTY) return null
    val turn = state.currentTurn
    val opp = turn.opponent()

    val newBoard = state.board.map { it.toMutableList() }.toMutableList()
    newBoard[row][col] = turn

    var anyFlip = false
    for ((dr, dc) in DIRECTIONS) {
        val toFlip = mutableListOf<Pair<Int, Int>>()
        var r = row + dr; var c = col + dc
        while (r in 0..7 && c in 0..7 && newBoard[r][c] == opp) {
            toFlip.add(r to c); r += dr; c += dc
        }
        if (toFlip.isNotEmpty() && r in 0..7 && c in 0..7 && newBoard[r][c] == turn) {
            toFlip.forEach { (fr, fc) -> newBoard[fr][fc] = turn }
            anyFlip = true
        }
    }
    if (!anyFlip) return null

    val immutable = newBoard.map { it.toList() }
    val nextTurn = if (computeValidMoves(immutable, opp).isNotEmpty()) opp else turn
    return ReversiState(immutable, nextTurn)
}
