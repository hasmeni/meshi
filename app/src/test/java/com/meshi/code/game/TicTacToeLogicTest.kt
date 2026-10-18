package com.meshi.code.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicTacToeLogicTest {

    @Test
    fun `fresh board has no winner and is not a draw`() {
        val s = TttState()
        assertNull(s.winner)
        assertFalse(s.isDraw)
        assertFalse(s.isGameOver)
        assertEquals(TttPiece.X, s.turn)
    }

    @Test
    fun `applying a move claims the cell and flips the turn`() {
        val s = applyTttMove(TttState(), 0)!!
        assertEquals(TttPiece.X, s.cells[0])
        assertEquals(TttPiece.O, s.turn)
    }

    @Test
    fun `cannot play an occupied cell`() {
        val s = applyTttMove(TttState(), 4)!!
        assertNull(applyTttMove(s, 4))
    }

    @Test
    fun `out of range move is rejected`() {
        assertNull(applyTttMove(TttState(), -1))
        assertNull(applyTttMove(TttState(), 9))
    }

    @Test
    fun `top row of X wins`() {
        // X:0, O:3, X:1, O:4, X:2 -> X completes top row
        var s = TttState()
        s = applyTttMove(s, 0)!!
        s = applyTttMove(s, 3)!!
        s = applyTttMove(s, 1)!!
        s = applyTttMove(s, 4)!!
        s = applyTttMove(s, 2)!!
        assertEquals(TttPiece.X, s.winner)
        assertTrue(s.isGameOver)
    }

    @Test
    fun `diagonal win is detected`() {
        var s = TttState()
        s = applyTttMove(s, 0)!! // X
        s = applyTttMove(s, 1)!! // O
        s = applyTttMove(s, 4)!! // X
        s = applyTttMove(s, 2)!! // O
        s = applyTttMove(s, 8)!! // X -> 0,4,8 diagonal
        assertEquals(TttPiece.X, s.winner)
    }

    @Test
    fun `cannot move after game is over`() {
        var s = TttState()
        s = applyTttMove(s, 0)!!
        s = applyTttMove(s, 3)!!
        s = applyTttMove(s, 1)!!
        s = applyTttMove(s, 4)!!
        s = applyTttMove(s, 2)!! // X wins
        assertNull(applyTttMove(s, 5))
    }

    @Test
    fun `full board with no line is a draw`() {
        // X O X / X O O / O X X  -> no three in a row
        var s = TttState()
        for (idx in listOf(0, 1, 2, 4, 3, 5, 7, 6, 8)) {
            s = applyTttMove(s, idx)!!
        }
        assertTrue(s.isDraw)
        assertTrue(s.isGameOver)
        assertNull(s.winner)
    }
}
