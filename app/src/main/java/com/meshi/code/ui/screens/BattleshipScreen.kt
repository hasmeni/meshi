package com.meshi.code.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshi.code.game.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleshipScreen(
    viewModel: BattleshipViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Battleship — ${viewModel.opponent.name.ifBlank { viewModel.opponent.displayId }}",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == BshPhase.MY_TURN || state.phase == BshPhase.OPPONENT_TURN) {
                        IconButton(onClick = { viewModel.resign() }) {
                            Icon(Icons.Default.Flag, contentDescription = "Resign",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.phase) {
                BshPhase.INVITE_SENT -> InviteSentContent(state.statusMsg, onBack)
                BshPhase.INVITE_RECEIVED -> InviteReceivedContent(state.statusMsg, viewModel)
                BshPhase.PLACEMENT, BshPhase.WAITING_READY -> PlacementContent(state, viewModel)
                BshPhase.MY_TURN, BshPhase.OPPONENT_TURN -> GameContent(state, viewModel)
                BshPhase.GAME_OVER -> GameOverContent(state, onBack)
            }
        }
    }
}

// ---- Invite screens ----

@Composable
private fun InviteSentContent(statusMsg: String, onBack: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Icon(Icons.Default.HourglassTop, contentDescription = null,
        modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(statusMsg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onBack) { Text("Cancel") }
}

@Composable
private fun InviteReceivedContent(statusMsg: String, viewModel: BattleshipViewModel) {
    Spacer(Modifier.height(48.dp))
    Icon(Icons.Default.SportsScore, contentDescription = null,
        modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(statusMsg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { viewModel.acceptInvite() }) { Text("Accept") }
        OutlinedButton(onClick = { viewModel.declineInvite() }) { Text("Decline") }
    }
}

// ---- Placement screen ----

@Composable
private fun PlacementContent(state: BshUiState, viewModel: BattleshipViewModel) {
    val waiting = state.phase == BshPhase.WAITING_READY
    val placedNames = state.placedShips.map { it.name }.toSet()
    val remaining = BSH_SHIP_TEMPLATES.filter { it.name !in placedNames }

    Text("Place Your Fleet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    // Status
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            state.statusMsg,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
            textAlign = TextAlign.Center,
        )
    }

    // Orientation toggle
    if (!waiting) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Orientation:", style = MaterialTheme.typography.bodyMedium)
            FilterChip(
                selected = state.horizontal,
                onClick = { viewModel.toggleOrientation() },
                label = { Text(if (state.horizontal) "Horizontal →" else "Vertical ↓") },
            )
        }
    }

    // Placement grid
    PlacementGrid(
        placedShips = state.placedShips,
        selectedTemplate = if (!waiting) remaining.getOrNull(state.selectedTemplateIdx) else null,
        horizontal = state.horizontal,
        onTap = { r, c -> if (!waiting) viewModel.tapCell(r, c) },
    )

    // Ship selector chips
    if (!waiting && remaining.isNotEmpty()) {
        Text("Select ship to place:", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            remaining.forEachIndexed { idx, tmpl ->
                FilterChip(
                    selected = idx == state.selectedTemplateIdx,
                    onClick = { viewModel.selectTemplate(idx) },
                    label = { Text("${tmpl.name}\n(${tmpl.size})", textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }

    // Already placed ships
    if (state.placedShips.isNotEmpty()) {
        Text("Placed:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            state.placedShips.forEach { ship ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        ship.name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    // Action buttons
    if (!waiting) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.placedShips.isNotEmpty()) {
                OutlinedButton(onClick = { viewModel.removeLastShip() }) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo")
                }
            }
            if (remaining.isEmpty()) {
                Button(onClick = { viewModel.confirmReady() }) { Text("Ready!") }
            }
        }
    }
}

@Composable
private fun PlacementGrid(
    placedShips: List<BshShip>,
    selectedTemplate: BshShipTemplate?,
    horizontal: Boolean,
    onTap: (Int, Int) -> Unit,
) {
    val shipCells = placedShips.flatMap { it.cells }.toSet()
    val sunkCells = placedShips.filter { it.isSunk }.flatMap { it.cells }.toSet()
    val hitCells = placedShips.flatMap { ship ->
        ship.hits.map { ship.cells[it] }
    }.toSet()

    BshGrid(
        label = "Your Board",
        cellContent = { r, c ->
            val pos = r to c
            val color = when {
                pos in sunkCells -> Color(0xFF8B0000)
                pos in hitCells  -> Color(0xFFCC3333)
                pos in shipCells -> MaterialTheme.colorScheme.primary
                else             -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .clickable { onTap(r, c) }
            )
        },
    )
}

// ---- Game screen ----

@Composable
private fun GameContent(state: BshUiState, viewModel: BattleshipViewModel) {
    val myBoard = remember(state.placedShips, state.opponentShots) {
        buildMyBoard(state.placedShips, state.opponentShots)
    }
    val opponentBoard = remember(state.shotsFired, state.sunkCells) {
        buildOpponentBoard(state.shotsFired, state.sunkCells)
    }
    val isMyTurn = state.phase == BshPhase.MY_TURN

    // Enemy waters
    Text("Enemy Waters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error)
    Text(
        if (isMyTurn) "Tap to fire!" else "Waiting…",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    BshGrid(
        label = null,
        cellContent = { r, c ->
            val cell = opponentBoard[r][c]
            val color = when (cell) {
                BshCell.SUNK  -> Color(0xFF8B0000)
                BshCell.HIT   -> Color(0xFFCC3333)
                BshCell.MISS  -> Color(0xFF336699)
                else          -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .then(
                        if (isMyTurn && cell == BshCell.EMPTY)
                            Modifier.clickable { viewModel.fireShot(r, c) }
                        else
                            Modifier
                    )
            ) {
                if (cell == BshCell.HIT || cell == BshCell.SUNK) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.Center))
                } else if (cell == BshCell.MISS) {
                    Text("·", color = Color.White, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center))
                }
            }
        },
    )

    // Stats
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Sunk: ${state.opponentSunkCount}/${BSH_SHIP_TEMPLATES.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            state.statusMsg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        val myHits = state.opponentShots.count { pos ->
            state.placedShips.any { ship -> pos in ship.cells }
        }
        Text(
            "Hits on me: $myHits",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    // My fleet
    Text("Your Fleet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)

    BshGrid(
        label = null,
        cellContent = { r, c ->
            val cell = myBoard[r][c]
            val color = when (cell) {
                BshCell.SUNK  -> Color(0xFF8B0000)
                BshCell.HIT   -> Color(0xFFCC3333)
                BshCell.SHIP  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                BshCell.MISS  -> Color(0xFF336699)
                else          -> Color.Transparent
            }
            Box(modifier = Modifier.fillMaxSize().background(color)) {
                if (cell == BshCell.HIT || cell == BshCell.SUNK) {
                    Text("✕", color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.Center))
                } else if (cell == BshCell.MISS) {
                    Text("·", color = Color.White, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center))
                }
            }
        },
    )
}

// ---- Game over screen ----

@Composable
private fun GameOverContent(state: BshUiState, onBack: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    val isWin = state.gameResult == "WIN"
    Icon(
        if (isWin) Icons.Default.EmojiEvents else Icons.Default.SentimentDissatisfied,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = if (isWin) Color(0xFFFFD700) else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        state.statusMsg,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onBack) { Text("Back") }
}

// ---- Grid composable ----

@Composable
private fun BshGrid(
    label: String?,
    cellContent: @Composable BoxScope.(row: Int, col: Int) -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val labelWidth = 20.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Fit all 10 cells + row-label column within available width, clamped to a sensible range
        val cellSize = ((maxWidth - labelWidth) / BSH_SIZE).coerceIn(22.dp, 36.dp)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
            }
            // Column headers A-J
            Row {
                Spacer(Modifier.width(labelWidth))
                for (c in 0 until BSH_SIZE) {
                    Box(modifier = Modifier.size(cellSize, 16.dp), contentAlignment = Alignment.Center) {
                        Text(('A' + c).toString(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                }
            }
            for (r in 0 until BSH_SIZE) {
                Row {
                    // Row number
                    Box(modifier = Modifier.size(labelWidth, cellSize), contentAlignment = Alignment.Center) {
                        Text("${r + 1}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                    for (c in 0 until BSH_SIZE) {
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .border(0.5.dp, gridColor)
                                .clip(RoundedCornerShape(1.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        ) {
                            cellContent(r, c)
                        }
                    }
                }
            }
        }
    }
}
