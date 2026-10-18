package com.meshi.code.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversiLogicTest {

    @Test
    fun `initial board has four centre pieces and black to move`() {
        val s = ReversiState.initial()
        assertEquals(2, s.blackCount)
        assertEquals(2, s.whiteCount)
        assertEquals(Piece.BLACK, s.currentTurn)
        assertFalse(s.isGameOver)
    }

    @Test
    fun `opponent helper flips colours`() {
        assertEquals(Piece.WHITE, Piece.BLACK.opponent())
        assertEquals(Piece.BLACK, Piece.WHITE.opponent())
        assertEquals(Piece.EMPTY, Piece.EMPTY.opponent())
    }

    @Test
    fun `black has exactly four opening moves`() {
        val s = ReversiState.initial()
        // Standard Othello opening: (2,3), (3,2), (4,5), (5,4)
        val expected = setOf(2 to 3, 3 to 2, 4 to 5, 5 to 4)
        assertEquals(expected, s.validMoves)
    }

    @Test
    fun `a legal opening move flips one disc and passes turn to white`() {
        val s = ReversiState.initial()
        val next = applyMove(s, 2, 3)
        assertNotNull(next)
        next!!
        // Black placed at (2,3) and flipped (3,3): black now has 4, white 1
        assertEquals(4, next.blackCount)
        assertEquals(1, next.whiteCount)
        assertEquals(Piece.WHITE, next.currentTurn)
    }

    @Test
    fun `move onto an occupied square is rejected`() {
        val s = ReversiState.initial()
        assertNull(applyMove(s, 3, 3)) // centre is occupied
    }

    @Test
    fun `move that flips nothing is rejected`() {
        val s = ReversiState.initial()
        assertNull(applyMove(s, 0, 0)) // corner flips nothing at opening
    }

    @Test
    fun `out of range coordinates are rejected`() {
        val s = ReversiState.initial()
        assertNull(applyMove(s, -1, 4))
        assertNull(applyMove(s, 8, 4))
        assertNull(applyMove(s, 4, 8))
    }

    @Test
    fun `fully black board is game over with black winner`() {
        val allBlack = ReversiState(
            board = List(8) { List(8) { Piece.BLACK } },
            currentTurn = Piece.WHITE,
        )
        assertTrue(allBlack.isGameOver)
        assertEquals(Piece.BLACK, allBlack.winner)
    }

    @Test
    fun `equal full board is a draw with null winner`() {
        val half = List(8) { r ->
            List(8) { if (r < 4) Piece.BLACK else Piece.WHITE }
        }
        val s = ReversiState(board = half, currentTurn = Piece.BLACK)
        assertTrue(s.isGameOver)
        assertNull(s.winner)
    }
}
