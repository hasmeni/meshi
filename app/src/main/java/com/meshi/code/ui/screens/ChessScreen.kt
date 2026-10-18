package com.meshi.code.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshi.code.game.*

private val LightSquare  = Color(0xFFF0D9B5)
private val DarkSquare   = Color(0xFFB58863)
private val HighlightSel = Color(0xAA6EF442)
private val HighlightDst = Color(0x8820B2AA)
private val BoardBorder  = Color(0xFF8B6914)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessScreen(
    viewModel: ChessViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showResignDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.phase == ChessGamePhase.PLAYING) { showResignDialog = true }

    if (showResignDialog) {
        AlertDialog(
            onDismissRequest = { showResignDialog = false },
            title = { Text("Resign?") },
            text = { Text("Are you sure you want to resign?") },
            confirmButton = {
                TextButton(onClick = { showResignDialog = false; viewModel.resign() }) { Text("Resign") }
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
                        "Chess — ${viewModel.opponent.name.ifBlank { viewModel.opponent.displayId }}",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.phase == ChessGamePhase.PLAYING) showResignDialog = true else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == ChessGamePhase.PLAYING) {
                        IconButton(onClick = { showResignDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Resign")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BoardBorder,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF2D2D2D),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.statusMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            when (state.phase) {
                ChessGamePhase.INVITED -> ChessInvitePanel(
                    opponentName = viewModel.opponent.name.ifBlank { viewModel.opponent.displayId },
                    onAccept = viewModel::acceptInvite,
                    onDecline = { viewModel.declineInvite(); onBack() },
                )
                else -> {
                    ChessBoard(state = state, onSquareTap = viewModel::onSquareTapped)
                    if (state.phase == ChessGamePhase.GAME_OVER) {
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(onClick = onBack) { Text("Back") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChessBoard(state: ChessUiState, onSquareTap: (Int) -> Unit) {
    // Display from perspective of the local player: white at bottom if amWhite
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .border(3.dp, BoardBorder),
    ) {
        val cellSize: Dp = maxWidth / 8
        Column {
            val ranks = if (state.amWhite) (7 downTo 0) else (0..7)
            for (rank in ranks) {
                Row {
                    val files = if (state.amWhite) (0..7) else (7 downTo 0)
                    for (file in files) {
                        val idx = rank * 8 + file
                        val piece = state.gameState.board[idx]
                        val isLight = (rank + file) % 2 == 0
                        val isSelected = state.selectedSquare == idx
                        val isDest = idx in state.legalDestinations
                        val lastFrom = state.gameState.lastMove?.from == idx
                        val lastTo = state.gameState.lastMove?.to == idx

                        val bg = when {
                            isSelected -> HighlightSel
                            isDest     -> HighlightDst
                            lastFrom || lastTo -> (if (isLight) LightSquare else DarkSquare).copy(alpha = 0.6f).compositeOver(Color(0xFFBFB200))
                            isLight    -> LightSquare
                            else       -> DarkSquare
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(cellSize)
                                .background(bg)
                                .clickable { onSquareTap(idx) },
                        ) {
                            if (!piece.isEmpty) {
                                Text(
                                    piece.symbol,
                                    fontSize = (cellSize.value * 0.7f).sp,
                                    color = if (piece.isWhite) Color.White else Color(0xFF1A1A1A),
                                    style = LocalTextStyle.current.copy(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = if (piece.isWhite) Color(0xFF333333) else Color(0xFFAAAAAA),
                                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                        )
                                    )
                                )
                            }
                            if (isDest && piece.isEmpty) {
                                Box(
                                    Modifier
                                        .size(cellSize * 0.3f)
                                        .background(Color(0x6020B2AA), CircleShape),
                                )
                            }
                            if (isDest && !piece.isEmpty) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .border(3.dp, Color(0xAA20B2AA)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChessInvitePanel(opponentName: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            "$opponentName challenges you to Chess!",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            "You will play as Black. White (challenger) moves first.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = BoardBorder)) { Text("Accept") }
            OutlinedButton(onClick = onDecline,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Decline") }
        }
    }
}

// Blend two colors (simple version)
private fun Color.compositeOver(other: Color): Color =
    Color(
        red = red * 0.5f + other.red * 0.5f,
        green = green * 0.5f + other.green * 0.5f,
        blue = blue * 0.5f + other.blue * 0.5f,
        alpha = 1f,
    )
