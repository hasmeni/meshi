package com.meshi.code.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshi.code.game.*

private val BoardGreen = Color(0xFF2E7D32)
private val DarkGreen  = Color(0xFF1B5E20)
private val CellGreen  = Color(0xFF388E3C)
private val LineColor  = Color(0xFF1B5E20)
private val PieceBlack = Color(0xFF111111)
private val PieceWhite = Color(0xFFF0F0F0)
private val ValidHint  = Color(0x5000E676)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReversiScreen(
    viewModel: ReversiViewModel,
    onBack: () -> Unit,
    onRematch: () -> Unit = onBack,
) {
    val state by viewModel.uiState.collectAsState()
    var showResignDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.phase == GamePhase.PLAYING) { showResignDialog = true }

    if (showResignDialog) {
        AlertDialog(
            onDismissRequest = { showResignDialog = false },
            title = { Text("Resign?") },
            text = { Text("Are you sure you want to resign this game?") },
            confirmButton = {
                TextButton(onClick = { showResignDialog = false; viewModel.resign() }) {
                    Text("Resign")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Reversi — ${viewModel.opponent.name.ifBlank { viewModel.opponent.displayId }}",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.phase == GamePhase.PLAYING) showResignDialog = true else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == GamePhase.PLAYING) {
                        IconButton(onClick = { showResignDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Resign")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        containerColor = DarkGreen,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            ScoreBar(state)
            Spacer(Modifier.height(8.dp))
            Text(
                state.statusMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))

            when (state.phase) {
                GamePhase.INVITED -> InvitePanel(
                    opponentName = viewModel.opponent.name.ifBlank { viewModel.opponent.displayId },
                    onAccept = viewModel::acceptInvite,
                    onDecline = { viewModel.declineInvite(); onBack() },
                )
                else -> {
                    GameBoard(
                        state = state,
                        onCellClick = { r, c -> viewModel.makeMove(r, c) },
                    )
                    if (state.phase == GamePhase.GAME_OVER) {
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = onBack) { Text("Back") }
                            Button(
                                onClick = onRematch,
                                colors = ButtonDefaults.buttonColors(containerColor = BoardGreen),
                            ) { Text("Rematch") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(state: ReversiUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(BoardGreen, MaterialTheme.shapes.medium)
            .padding(horizontal = 28.dp, vertical = 10.dp),
    ) {
        val blackActive = state.phase == GamePhase.PLAYING &&
                state.gameState.currentTurn == Piece.BLACK
        val whiteActive = state.phase == GamePhase.PLAYING &&
                state.gameState.currentTurn == Piece.WHITE
        PieceCount(
            diskColor = PieceBlack,
            label = "BLACK",
            count = state.gameState.blackCount,
            isActive = blackActive,
        )
        Text("vs", color = Color.White, fontWeight = FontWeight.Bold)
        PieceCount(
            diskColor = PieceWhite,
            label = "WHITE",
            count = state.gameState.whiteCount,
            isActive = whiteActive,
        )
    }
}

@Composable
private fun PieceCount(diskColor: Color, label: String, count: Int, isActive: Boolean) {
    val scale by animateFloatAsState(if (isActive) 1.15f else 1f, label = "pieceScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(diskColor, CircleShape)
                .border(1.5.dp, Color.Gray, CircleShape),
        )
        Text(
            "$count",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun GameBoard(state: ReversiUiState, onCellClick: (Int, Int) -> Unit) {
    val canMove = state.phase == GamePhase.PLAYING &&
            state.gameState.currentTurn == state.myColor
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        val cellSize: Dp = (maxWidth - 8.dp) / 8
        Column(
            modifier = Modifier
                .border(2.dp, LineColor)
                .background(CellGreen),
        ) {
            for (row in 0..7) {
                Row {
                    for (col in 0..7) {
                        val piece = state.gameState.board[row][col]
                        val isValid = canMove && (row to col) in state.gameState.validMoves
                        BoardCell(
                            piece = piece,
                            isValidMove = isValid,
                            cellSize = cellSize,
                            onClick = { onCellClick(row, col) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(
    piece: Piece,
    isValidMove: Boolean,
    cellSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(cellSize)
            .background(CellGreen)
            .border(0.5.dp, LineColor)
            .then(if (isValidMove) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        when (piece) {
            Piece.BLACK -> Box(
                Modifier
                    .size(cellSize * 0.80f)
                    .background(PieceBlack, CircleShape)
                    .border(1.dp, Color(0xFF444444), CircleShape),
            )
            Piece.WHITE -> Box(
                Modifier
                    .size(cellSize * 0.80f)
                    .background(PieceWhite, CircleShape)
                    .border(1.dp, Color(0xFFBBBBBB), CircleShape),
            )
            Piece.EMPTY -> if (isValidMove) {
                Box(
                    Modifier
                        .size(cellSize * 0.30f)
                        .background(ValidHint, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun InvitePanel(
    opponentName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            "$opponentName challenges you to Reversi!",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            "You will play as WHITE. They go first as BLACK.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = BoardGreen),
            ) { Text("Accept") }
            OutlinedButton(
                onClick = onDecline,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("Decline") }
        }
    }
}
