package com.meshi.code.ui.navigation

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meshi.code.MeshiApplication
import com.meshi.code.ble.ConnectionState
import com.meshi.code.game.BattleshipViewModel
import com.meshi.code.game.ChessViewModel
import com.meshi.code.game.ReversiViewModel
import com.meshi.code.game.TicTacToeViewModel
import com.meshi.code.protocol.MeshContact
import com.meshi.code.ui.screens.*
import com.meshi.code.viewmodel.ChatViewModel
import com.meshi.code.viewmodel.ScanViewModel

private object Routes {
    const val SCAN = "scan"
    const val MAIN = "main"
}

/** Parse a meshichan:// deep link URI into (channelName, secretHex) or null. */
private fun parseDeepLink(intent: Intent?): Pair<String, String>? {
    val uri = intent?.data ?: return null
    if (uri.scheme != "meshichan") return null
    val name = uri.host ?: return null
    val secret = uri.path?.trimStart('/') ?: return null
    return name to secret
}

@Composable
fun NavGraph(deepLinkIntent: Intent? = null) {
    val navController = rememberNavController()
    val scanVm: ScanViewModel = viewModel()
    var pendingChannelJoin by remember { mutableStateOf(parseDeepLink(deepLinkIntent)) }

    LaunchedEffect(Unit) {
        scanVm.connectionState.collect { state ->
            val current = navController.currentDestination?.route
            if (state == ConnectionState.CONNECTED && current == Routes.SCAN) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.SCAN) { inclusive = false }
                }
            } else if (state == ConnectionState.DISCONNECTED && current == Routes.MAIN) {
                navController.navigate(Routes.SCAN) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SCAN) {
        composable(Routes.SCAN) {
            ScanScreen(viewModel = scanVm, onConnected = {})
        }
        composable(Routes.MAIN) {
            val context = LocalContext.current
            val chatVm = remember {
                ChatViewModel(
                    scanVm.repository,
                    (context.applicationContext as MeshiApplication).prefs,
                )
            }

            // Show channel join dialog if app was opened via meshichan:// link
            if (pendingChannelJoin != null) {
                val (chanName, secretHex) = pendingChannelJoin!!
                val secret = runCatching {
                    (secretHex.chunked(2).map { it.toInt(16).toByte() }).toByteArray()
                }.getOrNull()
                if (secret != null && secret.size == 16) {
                    AlertDialog(
                        onDismissRequest = { pendingChannelJoin = null },
                        title = { Text("Join Channel?") },
                        text = { Text("Join private channel \"$chanName\" shared via QR code?") },
                        confirmButton = {
                            TextButton(onClick = {
                                // Find a free slot (1-7)
                                val freeSlot = (1..7).firstOrNull { it !in chatVm.channels.value }
                                if (freeSlot != null) {
                                    chatVm.createOrUpdateChannel(freeSlot, chanName, secret)
                                }
                                pendingChannelJoin = null
                            }) { Text("Join") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingChannelJoin = null }) { Text("Cancel") }
                        },
                    )
                } else {
                    pendingChannelJoin = null
                }
            }

            val navigateToScan = {
                navController.navigate(Routes.SCAN) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
            }
            MainScreen(
                chatVm = chatVm,
                onDisconnect = {
                    chatVm.disconnect()
                    navigateToScan()
                },
                onPanicWipe = {
                    chatVm.disconnect()
                    navigateToScan()
                },
            )
        }
    }
}

private enum class Tab(val label: String) {
    Channel("Channel"),
    Nodes("Nodes"),
    Channels("Channels"),
    Map("Map"),
}

private sealed class GameSession {
    data class Reversi(val contact: MeshContact, val isChallenging: Boolean) : GameSession()
    data class Chess(val contact: MeshContact, val isChallenging: Boolean) : GameSession()
    data class TicTacToe(val contact: MeshContact, val isChallenging: Boolean) : GameSession()
    data class Battleship(val contact: MeshContact, val isChallenging: Boolean) : GameSession()
}

@Composable
private fun MainScreen(chatVm: ChatViewModel, onDisconnect: () -> Unit, onPanicWipe: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(Tab.Channel) }

    var dmContact by remember { mutableStateOf<MeshContact?>(null) }
    var openChannelIdx by remember { mutableStateOf<Int?>(null) }
    var gameSession by remember { mutableStateOf<GameSession?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }

    // ---- Unread badge tracking ----
    val publicMsgCount = chatVm.publicMessages.collectAsState().value.size
    val dmMsgCount = chatVm.directMessages.collectAsState().value.size

    var channelVisited by remember { mutableStateOf(false) }
    var dmVisited by remember { mutableStateOf(false) }
    var baselineChannel by remember { mutableIntStateOf(0) }
    var baselineDm by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            Tab.Channel -> { channelVisited = true; baselineChannel = publicMsgCount }
            Tab.Nodes   -> { dmVisited = true;      baselineDm = dmMsgCount }
            else -> {}
        }
    }

    val unreadChannel = if (!channelVisited || selectedTab == Tab.Channel) 0
                        else (publicMsgCount - baselineChannel).coerceAtLeast(0)
    val unreadDm      = if (!dmVisited || selectedTab == Tab.Nodes) 0
                        else (dmMsgCount - baselineDm).coerceAtLeast(0)

    // Open the game screen when we receive a Reversi invite
    val pendingInvite by chatVm.pendingInvite.collectAsState()
    LaunchedEffect(pendingInvite) {
        val invite = pendingInvite
        if (invite != null && gameSession == null) {
            gameSession = GameSession.Reversi(invite, isChallenging = false)
            chatVm.clearPendingInvite()
        }
    }

    // Open the game screen when we receive a Chess invite (same pattern as Reversi)
    val pendingChessInvite by chatVm.pendingChessInvite.collectAsState()
    LaunchedEffect(pendingChessInvite) {
        val invite = pendingChessInvite
        if (invite != null && gameSession == null) {
            gameSession = GameSession.Chess(invite, isChallenging = false)
            chatVm.clearPendingChessInvite()
        }
    }

    // Open the game screen when we receive a Tic-tac-toe invite
    val pendingTttInvite by chatVm.pendingTttInvite.collectAsState()
    LaunchedEffect(pendingTttInvite) {
        val invite = pendingTttInvite
        if (invite != null && gameSession == null) {
            gameSession = GameSession.TicTacToe(invite, isChallenging = false)
            chatVm.clearPendingTttInvite()
        }
    }

    // Open the game screen when we receive a Battleship invite
    val pendingBshInvite by chatVm.pendingBshInvite.collectAsState()
    LaunchedEffect(pendingBshInvite) {
        val invite = pendingBshInvite
        if (invite != null && gameSession == null) {
            gameSession = GameSession.Battleship(invite, isChallenging = false)
            chatVm.clearPendingBshInvite()
        }
    }

    // --- Overlays in priority order ---

    if (showCrashLog) {
        CrashLogScreen(onBack = { showCrashLog = false })
        return
    }

    if (showLeaderboard) {
        LeaderboardScreen(viewModel = chatVm, onBack = { showLeaderboard = false })
        return
    }

    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            onCrashLog = { showSettings = false; showCrashLog = true },
            onLeaderboard = { showSettings = false; showLeaderboard = true },
            onPanicWipe = onPanicWipe,
        )
        return
    }

    when (val session = gameSession) {
        is GameSession.Reversi -> {
            val reversiVm = remember(session) {
                ReversiViewModel(chatVm.repository, session.contact, session.isChallenging)
            }
            DisposableEffect(session) { onDispose { reversiVm.close() } }
            ReversiScreen(
                viewModel = reversiVm,
                onBack = { gameSession = null },
                onRematch = {
                    gameSession = null
                    gameSession = GameSession.Reversi(session.contact, isChallenging = true)
                },
            )
            return
        }
        is GameSession.Chess -> {
            val chessVm = remember(session) {
                ChessViewModel(chatVm.repository, session.contact, session.isChallenging)
            }
            DisposableEffect(session) { onDispose { chessVm.close() } }
            ChessScreen(
                viewModel = chessVm,
                onBack = { gameSession = null },
            )
            return
        }
        is GameSession.TicTacToe -> {
            val tttVm = remember(session) {
                TicTacToeViewModel(chatVm.repository, session.contact, session.isChallenging)
            }
            DisposableEffect(session) { onDispose { tttVm.close() } }
            TicTacToeScreen(
                viewModel = tttVm,
                onBack = { gameSession = null },
            )
            return
        }
        is GameSession.Battleship -> {
            val bshVm = remember(session) {
                BattleshipViewModel(chatVm.repository, session.contact, session.isChallenging)
            }
            DisposableEffect(session) { onDispose { bshVm.close() } }
            BattleshipScreen(
                viewModel = bshVm,
                onBack = { gameSession = null },
            )
            return
        }
        null -> {}
    }

    if (dmContact != null) {
        DirectChatScreen(contact = dmContact!!, viewModel = chatVm, onBack = { dmContact = null })
        return
    }

    if (openChannelIdx != null) {
        ChatScreen(channelIdx = openChannelIdx!!, viewModel = chatVm, onBack = { openChannelIdx = null })
        return
    }

    // --- Normal tab layout ---
    Scaffold(
        topBar = {
            // Channel and Nodes tabs manage their own TopAppBar inside their screens
            if (selectedTab != Tab.Channel && selectedTab != Tab.Nodes) {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = {
                        Text(
                            when (selectedTab) {
                                Tab.Channels -> "Channels"
                                Tab.Map      -> "Map"
                                else         -> ""
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            val badgeCount = when (tab) {
                                Tab.Channel -> unreadChannel
                                Tab.Nodes   -> unreadDm
                                else        -> 0
                            }
                            BadgedBox(badge = {
                                if (badgeCount > 0) Badge { Text("$badgeCount") }
                            }) {
                                Icon(
                                    imageVector = when (tab) {
                                        Tab.Channel  -> Icons.Default.Chat
                                        Tab.Nodes    -> Icons.Default.People
                                        Tab.Channels -> Icons.Default.Tag
                                        Tab.Map      -> Icons.Default.Map
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            Tab.Channel -> ChatScreen(
                channelIdx = 0,
                viewModel = chatVm,
                onDisconnect = onDisconnect,
                onSettings = { showSettings = true },
                contentPadding = padding,
            )
            Tab.Nodes -> NodesScreen(
                viewModel = chatVm,
                onMessageContact = { dmContact = it },
                onChallengeContact = { contact ->
                    gameSession = GameSession.Reversi(contact, isChallenging = true)
                },
                onChessContact = { contact ->
                    gameSession = GameSession.Chess(contact, isChallenging = true)
                },
                onTttContact = { contact ->
                    gameSession = GameSession.TicTacToe(contact, isChallenging = true)
                },
                onBshContact = { contact ->
                    gameSession = GameSession.Battleship(contact, isChallenging = true)
                },
                onSettings = { showSettings = true },
                contentPadding = padding,
            )
            Tab.Channels -> ChannelsScreen(
                viewModel = chatVm,
                onOpenChannel = { idx ->
                    if (idx == 0) selectedTab = Tab.Channel
                    else openChannelIdx = idx
                },
                contentPadding = padding,
            )
            Tab.Map -> MapScreen(
                viewModel = chatVm,
                contentPadding = padding,
            )
        }
    }
}
