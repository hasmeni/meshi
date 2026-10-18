package com.meshi.code.game

const val BSH_PREFIX = "[BSH]"
const val BSH_SIZE = 10

data class BshShipTemplate(val name: String, val size: Int)

val BSH_SHIP_TEMPLATES = listOf(
    BshShipTemplate("Carrier", 5),
    BshShipTemplate("Battleship", 4),
    BshShipTemplate("Cruiser", 3),
    BshShipTemplate("Submarine", 3),
    BshShipTemplate("Destroyer", 2),
)

data class BshShip(
    val name: String,
    val size: Int,
    val row: Int,
    val col: Int,
    val horizontal: Boolean,
    val hits: Set<Int> = emptySet(),
) {
    val cells: List<Pair<Int, Int>> get() = List(size) { i ->
        if (horizontal) row to col + i else row + i to col
    }
    val isSunk: Boolean get() = hits.size == size
}

enum class BshCell { EMPTY, SHIP, HIT, MISS, SUNK }

fun canPlaceShip(
    placed: List<BshShip>,
    row: Int, col: Int,
    size: Int, horizontal: Boolean,
): Boolean {
    val newCells = List(size) { i ->
        if (horizontal) row to col + i else row + i to col
    }
    if (newCells.any { (r, c) -> r !in 0 until BSH_SIZE || c !in 0 until BSH_SIZE }) return false
    val existing = placed.flatMap { it.cells }.toSet()
    return newCells.none { it in existing }
}

/** Build a grid showing my ships and which cells have been hit/missed by the opponent. */
fun buildMyBoard(ships: List<BshShip>, opponentShots: Set<Pair<Int, Int>>): Array<Array<BshCell>> {
    val board = Array(BSH_SIZE) { Array(BSH_SIZE) { BshCell.EMPTY } }
    // Mark ship cells
    for (ship in ships) {
        for ((r, c) in ship.cells) {
            board[r][c] = BshCell.SHIP
        }
    }
    // Apply hits to ships
    for (ship in ships) {
        val shipCells = ship.cells
        for (hitIdx in ship.hits) {
            val (r, c) = shipCells[hitIdx]
            board[r][c] = if (ship.isSunk) BshCell.SUNK else BshCell.HIT
        }
    }
    // Mark missed shots on empty water
    for ((r, c) in opponentShots) {
        if (board[r][c] == BshCell.EMPTY) board[r][c] = BshCell.MISS
    }
    return board
}

/** Build a grid showing what I know about the opponent's board (from shots I've fired). */
fun buildOpponentBoard(
    shots: Map<Pair<Int, Int>, Boolean>,   // true = hit, false = miss
    sunkCells: Set<Pair<Int, Int>>,
): Array<Array<BshCell>> {
    val board = Array(BSH_SIZE) { Array(BSH_SIZE) { BshCell.EMPTY } }
    for ((pos, isHit) in shots) {
        val (r, c) = pos
        board[r][c] = when {
            pos in sunkCells -> BshCell.SUNK
            isHit            -> BshCell.HIT
            else             -> BshCell.MISS
        }
    }
    return board
}
