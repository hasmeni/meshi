package com.meshi.code.game

/** Prefix that distinguishes Chess protocol messages from normal DMs. */
const val CHESS_PREFIX = "[CH]"

enum class ChessPiece {
    EMPTY,
    WP, WN, WB, WR, WQ, WK,   // white
    BP, BN, BB, BR, BQ, BK,   // black
}

val ChessPiece.isWhite get() = this in WHITE_PIECES
val ChessPiece.isBlack get() = this in BLACK_PIECES
val ChessPiece.isEmpty  get() = this == ChessPiece.EMPTY
val ChessPiece.isKing   get() = this == ChessPiece.WK || this == ChessPiece.BK
val ChessPiece.symbol get() = when (this) {
    ChessPiece.EMPTY -> "·"
    ChessPiece.WP -> "♙"; ChessPiece.WN -> "♘"; ChessPiece.WB -> "♗"
    ChessPiece.WR -> "♖"; ChessPiece.WQ -> "♕"; ChessPiece.WK -> "♔"
    ChessPiece.BP -> "♟"; ChessPiece.BN -> "♞"; ChessPiece.BB -> "♝"
    ChessPiece.BR -> "♜"; ChessPiece.BQ -> "♛"; ChessPiece.BK -> "♚"
}

private val WHITE_PIECES = setOf(ChessPiece.WP, ChessPiece.WN, ChessPiece.WB, ChessPiece.WR, ChessPiece.WQ, ChessPiece.WK)
private val BLACK_PIECES = setOf(ChessPiece.BP, ChessPiece.BN, ChessPiece.BB, ChessPiece.BR, ChessPiece.BQ, ChessPiece.BK)

data class ChessMove(val from: Int, val to: Int, val promoteTo: ChessPiece = ChessPiece.EMPTY)

// Move notation for mesh transmission: e.g. "e2e4" or "e7e8q" for promotion
fun ChessMove.toNotation(): String {
    val fc = 'a' + (from % 8); val fr = '1' + (from / 8)
    val tc = 'a' + (to % 8);   val tr = '1' + (to / 8)
    val promo = when (promoteTo) {
        ChessPiece.WQ, ChessPiece.BQ -> "q"
        ChessPiece.WR, ChessPiece.BR -> "r"
        ChessPiece.WB, ChessPiece.BB -> "b"
        ChessPiece.WN, ChessPiece.BN -> "n"
        else -> ""
    }
    return "$fc$fr$tc$tr$promo"
}

fun parseChessMove(notation: String, board: Array<ChessPiece>, isWhiteTurn: Boolean): ChessMove? {
    if (notation.length < 4) return null
    val fc = notation[0] - 'a'; val fr = notation[1] - '1'
    val tc = notation[2] - 'a'; val tr = notation[3] - '1'
    if (fc !in 0..7 || fr !in 0..7 || tc !in 0..7 || tr !in 0..7) return null
    val from = fr * 8 + fc
    val to   = tr * 8 + tc
    val promoChar = notation.getOrNull(4)
    val promoteTo = if (promoChar != null) {
        if (isWhiteTurn) when (promoChar) { 'q' -> ChessPiece.WQ; 'r' -> ChessPiece.WR; 'b' -> ChessPiece.WB; 'n' -> ChessPiece.WN; else -> ChessPiece.WQ }
        else             when (promoChar) { 'q' -> ChessPiece.BQ; 'r' -> ChessPiece.BR; 'b' -> ChessPiece.BB; 'n' -> ChessPiece.BN; else -> ChessPiece.BQ }
    } else ChessPiece.EMPTY
    return ChessMove(from, to, promoteTo)
}

data class ChessState(
    val board: Array<ChessPiece>,       // index = rank*8 + file, rank 0 = rank 1 (white home)
    val isWhiteTurn: Boolean,
    val isGameOver: Boolean = false,
    val winner: Boolean? = null,        // true=white, false=black, null=draw/stalemate
    val lastMove: ChessMove? = null,
) {
    val legalMoves: List<ChessMove> get() = generateLegalMoves(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChessState) return false
        return board.contentEquals(other.board) && isWhiteTurn == other.isWhiteTurn
    }
    override fun hashCode(): Int = 31 * board.contentHashCode() + isWhiteTurn.hashCode()
}

fun initialChessState(): ChessState {
    val b = Array(64) { ChessPiece.EMPTY }
    // White pieces — rank 1 = index 0..7
    b[0]=ChessPiece.WR; b[1]=ChessPiece.WN; b[2]=ChessPiece.WB; b[3]=ChessPiece.WQ
    b[4]=ChessPiece.WK; b[5]=ChessPiece.WB; b[6]=ChessPiece.WN; b[7]=ChessPiece.WR
    for (f in 0..7) b[8 + f] = ChessPiece.WP
    // Black pieces — rank 8 = index 56..63
    b[56]=ChessPiece.BR; b[57]=ChessPiece.BN; b[58]=ChessPiece.BB; b[59]=ChessPiece.BQ
    b[60]=ChessPiece.BK; b[61]=ChessPiece.BB; b[62]=ChessPiece.BN; b[63]=ChessPiece.BR
    for (f in 0..7) b[48 + f] = ChessPiece.BP
    return ChessState(b, isWhiteTurn = true)
}

/** Apply a move without legality check — used internally by legal move generation. */
private fun applyMoveRaw(state: ChessState, move: ChessMove): ChessState {
    val b = state.board.copyOf()
    val piece = b[move.from]
    b[move.from] = ChessPiece.EMPTY
    // Promotion
    b[move.to] = when {
        move.promoteTo != ChessPiece.EMPTY -> move.promoteTo
        // Auto-promote pawn reaching back rank
        piece == ChessPiece.WP && move.to / 8 == 7 -> ChessPiece.WQ
        piece == ChessPiece.BP && move.to / 8 == 0 -> ChessPiece.BQ
        else -> piece
    }
    return state.copy(board = b, isWhiteTurn = !state.isWhiteTurn, lastMove = move)
}

/** Apply a move and check game-over conditions. */
fun applyChessMove(state: ChessState, move: ChessMove): ChessState {
    val next = applyMoveRaw(state, move)
    val opponentLegal = generateLegalMoves(next)
    return if (opponentLegal.isEmpty()) {
        val inCheck = isKingInCheck(next.board, next.isWhiteTurn)
        next.copy(
            isGameOver = true,
            winner = if (inCheck) state.isWhiteTurn /* mover wins */ else null /* stalemate */,
        )
    } else {
        next
    }
}

fun isKingInCheck(board: Array<ChessPiece>, whiteKing: Boolean): Boolean {
    val king = if (whiteKing) ChessPiece.WK else ChessPiece.BK
    val kingIdx = board.indexOfFirst { it == king }
    if (kingIdx < 0) return false
    // Check if any opponent piece attacks kingIdx
    val opponentColor = !whiteKing
    return board.indices.any { from ->
        val p = board[from]
        val isOpponent = if (opponentColor) p.isWhite else p.isBlack
        if (!isOpponent) false
        else canAttack(board, from, kingIdx, opponentColor)
    }
}

private fun canAttack(board: Array<ChessPiece>, from: Int, to: Int, isWhiteAttacker: Boolean): Boolean {
    val p = board[from]
    val fr = from / 8; val ff = from % 8
    val tr = to / 8;   val tf = to % 8
    val dr = tr - fr;  val df = tf - ff
    return when (p) {
        ChessPiece.WP -> isWhiteAttacker && dr == 1 && kotlin.math.abs(df) == 1
        ChessPiece.BP -> !isWhiteAttacker && dr == -1 && kotlin.math.abs(df) == 1
        ChessPiece.WN, ChessPiece.BN ->
            (kotlin.math.abs(dr) == 2 && kotlin.math.abs(df) == 1) ||
            (kotlin.math.abs(dr) == 1 && kotlin.math.abs(df) == 2)
        ChessPiece.WB, ChessPiece.BB ->
            kotlin.math.abs(dr) == kotlin.math.abs(df) && kotlin.math.abs(dr) > 0 &&
            isClearDiagonal(board, from, to)
        ChessPiece.WR, ChessPiece.BR ->
            (dr == 0 || df == 0) && !(dr == 0 && df == 0) &&
            isClearLine(board, from, to)
        ChessPiece.WQ, ChessPiece.BQ ->
            ((kotlin.math.abs(dr) == kotlin.math.abs(df) && kotlin.math.abs(dr) > 0 && isClearDiagonal(board, from, to)) ||
             ((dr == 0 || df == 0) && !(dr == 0 && df == 0) && isClearLine(board, from, to)))
        ChessPiece.WK, ChessPiece.BK ->
            kotlin.math.abs(dr) <= 1 && kotlin.math.abs(df) <= 1 && (dr != 0 || df != 0)
        else -> false
    }
}

private fun isClearLine(board: Array<ChessPiece>, from: Int, to: Int): Boolean {
    val fr = from / 8; val ff = from % 8
    val tr = to / 8;   val tf = to % 8
    val dr = Integer.signum(tr - fr); val df = Integer.signum(tf - ff)
    var r = fr + dr; var f = ff + df
    while (r != tr || f != tf) {
        if (!board[r * 8 + f].isEmpty) return false
        r += dr; f += df
    }
    return true
}

private fun isClearDiagonal(board: Array<ChessPiece>, from: Int, to: Int) = isClearLine(board, from, to)

private fun generateLegalMoves(state: ChessState): List<ChessMove> {
    val result = mutableListOf<ChessMove>()
    val board = state.board
    val isWhite = state.isWhiteTurn
    val myPieces = if (isWhite) WHITE_PIECES else BLACK_PIECES
    val oppPieces = if (isWhite) BLACK_PIECES else WHITE_PIECES

    for (from in 0..63) {
        val piece = board[from]
        if (piece !in myPieces) continue
        val fr = from / 8; val ff = from % 8

        val rawMoves: List<Int> = when (piece) {
            ChessPiece.WP -> whitePawnMoves(board, fr, ff)
            ChessPiece.BP -> blackPawnMoves(board, fr, ff)
            ChessPiece.WN, ChessPiece.BN -> knightMoves(fr, ff)
            ChessPiece.WB, ChessPiece.BB -> slidingMoves(board, fr, ff, listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1))
            ChessPiece.WR, ChessPiece.BR -> slidingMoves(board, fr, ff, listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1))
            ChessPiece.WQ, ChessPiece.BQ -> slidingMoves(board, fr, ff, listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1, 1 to 0, -1 to 0, 0 to 1, 0 to -1))
            ChessPiece.WK, ChessPiece.BK -> kingMoves(fr, ff)
            else -> emptyList()
        }.filter { to ->
            val target = board[to]
            target.isEmpty || target in oppPieces
        }

        for (to in rawMoves) {
            val candidateMove = ChessMove(from, to)
            val nextState = applyMoveRaw(state, candidateMove)
            if (!isKingInCheck(nextState.board, isWhite)) {
                // Pawn promotion — always queen for auto
                result.add(candidateMove)
            }
        }
    }
    return result
}

private fun whitePawnMoves(board: Array<ChessPiece>, r: Int, f: Int): List<Int> {
    val moves = mutableListOf<Int>()
    if (r + 1 <= 7 && board[(r + 1) * 8 + f].isEmpty) {
        moves.add((r + 1) * 8 + f)
        if (r == 1 && board[(r + 2) * 8 + f].isEmpty) moves.add((r + 2) * 8 + f)
    }
    for (df in listOf(-1, 1)) {
        if (f + df in 0..7) {
            val target = board[(r + 1) * 8 + (f + df)]
            if (target.isBlack) moves.add((r + 1) * 8 + (f + df))
        }
    }
    return moves.filter { it / 8 in 0..7 }
}

private fun blackPawnMoves(board: Array<ChessPiece>, r: Int, f: Int): List<Int> {
    val moves = mutableListOf<Int>()
    if (r - 1 >= 0 && board[(r - 1) * 8 + f].isEmpty) {
        moves.add((r - 1) * 8 + f)
        if (r == 6 && board[(r - 2) * 8 + f].isEmpty) moves.add((r - 2) * 8 + f)
    }
    for (df in listOf(-1, 1)) {
        if (f + df in 0..7 && r - 1 >= 0) {
            val target = board[(r - 1) * 8 + (f + df)]
            if (target.isWhite) moves.add((r - 1) * 8 + (f + df))
        }
    }
    return moves.filter { it / 8 in 0..7 }
}

private fun knightMoves(r: Int, f: Int): List<Int> {
    val deltas = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
    return deltas.mapNotNull { (dr, df) ->
        val nr = r + dr; val nf = f + df
        if (nr in 0..7 && nf in 0..7) nr * 8 + nf else null
    }
}

private fun slidingMoves(board: Array<ChessPiece>, r: Int, f: Int, dirs: List<Pair<Int, Int>>): List<Int> {
    val moves = mutableListOf<Int>()
    for ((dr, df) in dirs) {
        var nr = r + dr; var nf = f + df
        while (nr in 0..7 && nf in 0..7) {
            val target = board[nr * 8 + nf]
            moves.add(nr * 8 + nf)
            if (!target.isEmpty) break
            nr += dr; nf += df
        }
    }
    return moves
}

private fun kingMoves(r: Int, f: Int): List<Int> {
    val deltas = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
    return deltas.mapNotNull { (dr, df) ->
        val nr = r + dr; val nf = f + df
        if (nr in 0..7 && nf in 0..7) nr * 8 + nf else null
    }
}
