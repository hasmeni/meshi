package com.meshi.code.game

enum class TttPiece { EMPTY, X, O }

data class TttState(
    val cells: List<TttPiece> = List(9) { TttPiece.EMPTY },
    val turn: TttPiece = TttPiece.X,   // X goes first (challenger)
) {
    val winner: TttPiece?
        get() {
            val lines = listOf(
                listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // rows
                listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // cols
                listOf(0, 4, 8), listOf(2, 4, 6),                   // diagonals
            )
            for (line in lines) {
                val (a, b, c) = line
                if (cells[a] != TttPiece.EMPTY && cells[a] == cells[b] && cells[b] == cells[c]) {
                    return cells[a]
                }
            }
            return null
        }

    val isDraw: Boolean
        get() = winner == null && cells.none { it == TttPiece.EMPTY }

    val isGameOver: Boolean
        get() = winner != null || isDraw
}

/** Returns new state with [idx] claimed, or null if the move is illegal. */
fun applyTttMove(state: TttState, idx: Int): TttState? {
    if (idx !in 0..8) return null
    if (state.cells[idx] != TttPiece.EMPTY) return null
    if (state.isGameOver) return null
    val newCells = state.cells.toMutableList().also { it[idx] = state.turn }
    val next = if (state.turn == TttPiece.X) TttPiece.O else TttPiece.X
    return TttState(cells = newCells, turn = next)
}
