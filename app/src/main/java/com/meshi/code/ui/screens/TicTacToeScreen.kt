package com.meshi.code.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshi.code.game.TicTacToeViewModel
import com.meshi.code.game.TttGamePhase
import com.meshi.code.game.TttPiece

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen(
    viewModel: TicTacToeViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val s = uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tic-tac-toe vs ${viewModel.opponent.name.ifBlank { viewModel.opponent.displayId }}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Status message
            Text(
                s.statusMsg,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(8.dp))

            // 3x3 Grid
            val cellSize = 100.dp
            val xColor = MaterialTheme.colorScheme.error
            val oColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.outline
            val canInteract = s.phase == TttGamePhase.PLAYING && s.gameState.turn == s.myPiece

            Column {
                for (row in 0..2) {
                    Row {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            val piece = s.gameState.cells[idx]
                            val isClickable = canInteract && piece == TttPiece.EMPTY
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .border(1.dp, gridColor)
                                    .then(
                                        if (isClickable) Modifier.clickable { viewModel.onCellTapped(idx) }
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                when (piece) {
                                    TttPiece.X -> XMark(color = xColor, modifier = Modifier.size(60.dp))
                                    TttPiece.O -> OMark(color = oColor, modifier = Modifier.size(60.dp))
                                    TttPiece.EMPTY -> {
                                        if (isClickable) {
                                            Text(
                                                "·",
                                                fontSize = 32.sp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            when (s.phase) {
                TttGamePhase.INVITED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.acceptInvite() }) { Text("Accept") }
                        OutlinedButton(onClick = { viewModel.declineInvite() }) { Text("Decline") }
                    }
                }
                TttGamePhase.PLAYING -> {
                    OutlinedButton(
                        onClick = { viewModel.resign() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Resign")
                    }
                }
                TttGamePhase.GAME_OVER -> {
                    Button(onClick = onBack) { Text("Back") }
                }
                TttGamePhase.AWAITING_ACCEPT -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun XMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pad = size.minDimension * 0.15f
        val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        drawLine(color, Offset(pad, pad), Offset(size.width - pad, size.height - pad), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width - pad, pad), Offset(pad, size.height - pad), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun OMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pad = size.minDimension * 0.1f
        drawCircle(
            color = color,
            radius = (size.minDimension / 2f) - pad,
            style = Stroke(width = size.minDimension * 0.12f),
        )
    }
}
