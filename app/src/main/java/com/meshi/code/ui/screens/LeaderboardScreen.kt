package com.meshi.code.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshi.code.db.GameRecordEntity
import com.meshi.code.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GAME_LABELS = mapOf(
    "REVERSI"     to "Reversi",
    "CHESS"       to "Chess",
    "TTT"         to "Tic-tac-toe",
    "BATTLESHIP"  to "Battleship",
)

private data class GameSummary(
    val gameType: String,
    val wins: Int,
    val losses: Int,
    val draws: Int,
) {
    val total = wins + losses + draws
}

private data class OpponentRow(
    val opponentId: String,
    val opponentName: String,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val total: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val records by viewModel.repository.gameRecordDao.observeAllUnlimited()
        .collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    // Compute per-game-type summary
    val gameSummaries = remember(records) {
        GAME_LABELS.keys.map { type ->
            val subset = records.filter { it.gameType == type }
            GameSummary(
                gameType = type,
                wins     = subset.count { it.result == "WIN" },
                losses   = subset.count { it.result == "LOSS" },
                draws    = subset.count { it.result == "DRAW" },
            )
        }.filter { it.total > 0 }
    }

    // Compute per-opponent summary (all game types combined)
    val opponentRows = remember(records) {
        records
            .groupBy { it.opponentId }
            .map { (id, recs) ->
                OpponentRow(
                    opponentId   = id,
                    opponentName = recs.first().opponentName.ifBlank { id },
                    wins         = recs.count { it.result == "WIN" },
                    losses       = recs.count { it.result == "LOSS" },
                    draws        = recs.count { it.result == "DRAW" },
                    total        = recs.size,
                )
            }
            .sortedByDescending { it.total }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear history",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No games played yet.\nChallenge a contact to get started!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Overall stats per game type ──────────────────────────────
                if (gameSummaries.isNotEmpty()) {
                    item {
                        LeaderboardSectionHeader("Overall Stats")
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gameSummaries.forEach { summary ->
                                GameSummaryCard(summary, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ── Per-opponent breakdown ───────────────────────────────────
                if (opponentRows.isNotEmpty()) {
                    item {
                        LeaderboardSectionHeader("Opponents")
                    }
                    items(opponentRows, key = { it.opponentId }) { row ->
                        OpponentCard(row)
                    }
                }

                // ── Recent game log ──────────────────────────────────────────
                item {
                    LeaderboardSectionHeader("Recent Games")
                }
                items(records.take(50), key = { it.id }) { record ->
                    RecentGameRow(record)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Game History?") },
            text = { Text("All game records will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.repository.gameRecordDao.deleteAll() }
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LeaderboardSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun GameSummaryCard(summary: GameSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                GAME_LABELS[summary.gameType] ?: summary.gameType,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            HorizontalDivider()
            StatRow("W", summary.wins.toString(),   MaterialTheme.colorScheme.primary)
            StatRow("L", summary.losses.toString(), MaterialTheme.colorScheme.error)
            StatRow("D", summary.draws.toString(),  MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(
                "${summary.total} games",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun OpponentCard(row: OpponentRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.opponentName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${row.total} game${if (row.total != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WldChip(row.wins, row.losses, row.draws)
        }
    }
}

@Composable
private fun WldChip(wins: Int, losses: Int, draws: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ResultBadge(wins.toString(),   MaterialTheme.colorScheme.primary)
        ResultBadge(losses.toString(), MaterialTheme.colorScheme.error)
        if (draws > 0) ResultBadge(draws.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RecentGameRow(record: GameRecordEntity) {
    val resultColor = when (record.result) {
        "WIN"  -> MaterialTheme.colorScheme.primary
        "LOSS" -> MaterialTheme.colorScheme.error
        else   -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val resultLabel = when (record.result) {
        "WIN"  -> "Win"
        "LOSS" -> "Loss"
        else   -> "Draw"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Result pill
        Surface(
            color = resultColor.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.width(44.dp),
        ) {
            Text(
                resultLabel,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = resultColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        // Game type
        Text(
            GAME_LABELS[record.gameType] ?: record.gameType,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Opponent
        Text(
            "vs ${record.opponentName.ifBlank { record.opponentId }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // Time
        Text(
            remember(record.timestampMs) {
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(record.timestampMs))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}
